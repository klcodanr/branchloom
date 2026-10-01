package com.jagent.desktop.services.github;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestCheck;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.PullRequestReview;
import com.jagent.desktop.models.PullRequestReviews;
import com.jagent.desktop.models.github.Auth;
import com.jagent.desktop.models.github.Issue;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.git.GitRepository;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHubAbuseLimitHandler;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.RateLimitChecker;
import org.kohsuke.github.RateLimitTarget;
import org.kohsuke.github.connector.GitHubConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GitHub integration service for pull request, issue, review, and check data.
 *
 * <p>Use this class when application services need data from GitHub for a {@link Project}. It
 * resolves auth from configured connections or GitHub CLI, builds API clients with rate-limit
 * handling, and caches repository lookups.
 *
 * <p>This class resolves authentication state from {@link AppState} and can be reused across calls.
 *
 * <pre>{@code
 * GitHub gitHub = new GitHub(appState);
 * List<PullRequest> requests = gitHub.loadForProject(projectId, project, searchText);
 * PullRequestDetails details = gitHub.getPullRequestDetails(request);
 * }</pre>
 *
 * <p>Use {@link com.jagent.desktop.services.git.GitRepository} for local git state and this class
 * for remote GitHub API state.
 */
@SuppressWarnings("PMD.GodClass")
public final class GitHub {
    private static final Logger LOG = LoggerFactory.getLogger(GitHub.class);
    private static final int CORE_RATE_LIMIT_BUFFER = 1;
    private static final int SEARCH_RATE_LIMIT_BUFFER = 1;

    private static final Map<ProjectId, GitHub> INSTANCES = new ConcurrentHashMap<>();
    private final GitHubAuth gitHubAuth;
    private final ProjectId projectId;
    private final Project project;

    private volatile String login;
    private volatile Auth auth;
    private volatile org.kohsuke.github.GitHub client;
    private volatile GHRepository repository;

    public static GitHub forProject(final AppState appState, final ProjectId projectId) {
        return INSTANCES.computeIfAbsent(projectId, id -> new GitHub(appState, id));
    }

    private GitHub(final AppState appState, final ProjectId projectId) {
        this.gitHubAuth = new GitHubAuth();
        this.projectId = Objects.requireNonNull(projectId);
        this.project = Objects.requireNonNull(appState.projects().get(projectId));
    }

    private org.kohsuke.github.GitHub client() throws IOException {
        final Auth refreshedAuth = gitHubAuth.getAuth(project);
        if (auth != null && auth.equals(refreshedAuth) && client != null) {
            return client;
        }
        auth = refreshedAuth;
        client =
                new GitHubBuilder()
                        .withEndpoint(auth.host())
                        .withOAuthToken(auth.token())
                        .withRateLimitHandler(GitHubRateLimitHandler.WAIT)
                        .withAbuseLimitHandler(GitHubAbuseLimitHandler.WAIT)
                        .withRateLimitChecker(
                                new RateLimitChecker.LiteralValue(CORE_RATE_LIMIT_BUFFER),
                                RateLimitTarget.CORE)
                        .withRateLimitChecker(
                                new RateLimitChecker.LiteralValue(SEARCH_RATE_LIMIT_BUFFER),
                                RateLimitTarget.SEARCH)
                        .withConnector(new LoggingGitHubConnector(GitHubConnector.DEFAULT))
                        .build();

        login = client.getMyself().getLogin();
        return client;
    }

    private List<PullRequestCheck> mergeChecks(
            final List<PullRequestCheck> checksFromRuns,
            final List<PullRequestCheck> checksFromStatuses) {
        final Map<String, PullRequestCheck> mergedByName = new LinkedHashMap<>();
        for (final PullRequestCheck check : checksFromRuns) {
            mergedByName.put(check.name(), check);
        }
        final Map<String, PullRequestCheck> latestStatusesByName = new HashMap<>();
        for (final PullRequestCheck status : checksFromStatuses) {
            final PullRequestCheck existing = latestStatusesByName.get(status.name());
            if (existing == null || checkTimestamp(status) >= checkTimestamp(existing)) {
                latestStatusesByName.put(status.name(), status);
            }
        }
        latestStatusesByName.entrySet().stream()
                .sorted(
                        (left, right) ->
                                Long.compare(
                                        checkTimestamp(right.getValue()),
                                        checkTimestamp(left.getValue())))
                .forEach(entry -> mergedByName.putIfAbsent(entry.getKey(), entry.getValue()));
        return List.copyOf(mergedByName.values());
    }

    private long checkTimestamp(final PullRequestCheck check) {
        return check.completedAt() == null ? Long.MIN_VALUE : check.completedAt().getTime();
    }

    //
    private GHRepository repository() throws IOException {
        if (repository != null) {
            return repository;
        }
        final String name;
        try (GitRepository gitRepo = GitRepository.open(Path.of(project.path()))) {
            name = gitRepo.repositoryOrgAndName();
        }
        if (name == null) {
            throw new IOException("No GitHub remote found for this project");
        }

        repository = client().getRepository(name);
        if (repository == null) {
            throw new IOException("Failed to get GitHub repository for name: " + name);
        }
        return repository;
    }

    private PullRequest toPullRequest(@NotNull final GHPullRequest request) {
        try {
            return new PullRequest(
                    projectId,
                    project,
                    request.getNumber(),
                    request.getState() == null
                            ? PullRequest.State.OPEN
                            : PullRequest.State.valueOf(request.getState().toString()),
                    request.getTitle() == null ? "" : request.getTitle(),
                    request.getBody() == null ? "" : request.getBody(),
                    request.getHtmlUrl(),
                    request.getCreatedAt(),
                    request.getUpdatedAt(),
                    GitHubUser.from(request.getUser()),
                    request.getHead() == null ? "" : request.getHead().getRef(),
                    request.getHead() == null ? "" : request.getHead().getSha(),
                    request.getBase() == null ? "" : request.getBase().getRef());
        } catch (IOException e) {
            LOG.error("Failed to create PullRequest from GHPullRequest", e);
        }
        return null;
    }

    public void approve(final PullRequest request) throws IOException, InterruptedException {
        final GHPullRequest ghPullRequest = repository().getPullRequest(request.number());
        ghPullRequest.createReview().event(GHPullRequestReviewEvent.APPROVE).create();
    }

    public void convertToDraft(final PullRequest request) throws IOException {
        updateDraft(request.number(), true);
    }

    public void close(final PullRequest request) throws IOException, InterruptedException {
        repository().getPullRequest(request.number()).close();
    }

    public String getLogin() throws IOException {
        if (this.login == null) {
            login = client().getMyself().getLogin();
        }
        return login;
    }

    public PullRequest getPullRequest(final int number) throws IOException {
        return toPullRequest(repository().getPullRequest(number));
    }

    public PullRequest getPullRequest(final Path worktree) throws IOException {
        if (!Files.isDirectory(worktree)) {
            throw new IOException("The worktree directory does not exist: " + worktree);
        }
        final String branch;
        try (GitRepository gitRepo = GitRepository.open(worktree)) {
            branch = gitRepo.currentBranch();
        } catch (IOException exception) {
            throw new IOException(
                    "Failed to determine current branch for worktree: " + worktree, exception);
        }

        final GHRepository repository = repository();
        final GHPullRequest request =
                StreamSupport.stream(
                                repository
                                        .queryPullRequests()
                                        .state(GHIssueState.ALL)
                                        .head(repository.getOwner().getName() + ":" + branch)
                                        .list()
                                        .spliterator(),
                                false)
                        .findFirst()
                        .orElseThrow(() -> new IOException("No pull request found"));
        return toPullRequest(request);
    }

    public PullRequestDetails getPullRequestDetails(final PullRequest request)
            throws IOException, InterruptedException {
        final GHRepository repository = repository();
        final GHPullRequest ghPullRequest = repository.getPullRequest(request.number());
        try {
            PullRequestChecks checks;
            try {
                String headSha = request.headSha();
                LOG.debug(
                        "PR checks lookup started: project={}, number={}, summaryHeadSha={}",
                        request.project().name(),
                        request.number(),
                        request.abbreviatedHeadSha());
                if (headSha.isBlank()) {
                    LOG.debug(
                            "PR checks lookup requires refresh for missing SHA: project={}, number={}",
                            request.project().name(),
                            request.number());
                    final GHPullRequest refreshed = repository.getPullRequest(request.number());
                    headSha =
                            refreshed.getHead() == null || refreshed.getHead().getSha() == null
                                    ? ""
                                    : refreshed.getHead().getSha();
                    LOG.debug(
                            "PR checks refresh resolved SHA: project={}, number={}, refreshedHeadSha={}",
                            request.project().name(),
                            request.number(),
                            request.abbreviatedHeadSha());
                }
                if (headSha.isBlank()) {
                    LOG.debug(
                            "PR checks lookup has no SHA after refresh: project={}, number={}",
                            request.project().name(),
                            request.number());
                    checks = new PullRequestChecks(List.of());
                } else {
                    final List<PullRequestCheck> checksFromRuns =
                            repository.getCheckRuns(headSha).toList().stream()
                                    .map(PullRequestCheck::from)
                                    .toList();
                    final List<PullRequestCheck> checksFromStatuses =
                            repository.getCommit(headSha).listStatuses().toList().stream()
                                    .map(PullRequestCheck::from)
                                    .toList();
                    final List<PullRequestCheck> mergedChecks =
                            mergeChecks(checksFromRuns, checksFromStatuses);
                    LOG.debug(
                            "PR checks lookup finished: project={}, number={}, headSha={}, checks={}",
                            request.project().name(),
                            request.number(),
                            request.abbreviatedHeadSha(),
                            mergedChecks.size());
                    checks = new PullRequestChecks(mergedChecks);
                }
            } catch (IOException exception) {
                LOG.warn("Failed to load checks for pull request {}", request.number(), exception);
                checks = new PullRequestChecks(List.of());
            }
            return new PullRequestDetails(
                    projectId,
                    project,
                    ghPullRequest.getNumber(),
                    ghPullRequest.isDraft(),
                    Boolean.TRUE.equals(ghPullRequest.getMergeable()),
                    ghPullRequest.getMergeableState() == null
                            ? ""
                            : ghPullRequest.getMergeableState(),
                    ghPullRequest.getAdditions(),
                    ghPullRequest.getDeletions(),
                    ghPullRequest.getChangedFiles(),
                    checks);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load pull request details " + request.number(), exception);
        }
    }

    public void markReady(final PullRequest request) throws IOException, InterruptedException {
        updateDraft(request.number(), false);
    }

    public void merge(final PullRequest request) throws IOException, InterruptedException {
        repository()
                .getPullRequest(request.number())
                .merge(null, null, GHPullRequest.MergeMethod.SQUASH);
    }

    private void updateDraft(final int number, final boolean draft) throws IOException {

        final var repository = repository();
        final var localAuth = this.auth;
        final URL url =
                URI.create(
                                localAuth.host()
                                        + "/repos/"
                                        + repository.getFullName()
                                        + "/pulls/"
                                        + number)
                        .toURL();
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PATCH");
        connection.setRequestProperty("Authorization", "Bearer " + auth.token());
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        try {
            connection
                    .getOutputStream()
                    .write(("{\"draft\":" + draft + "}").getBytes(StandardCharsets.UTF_8));
            if (connection.getResponseCode() / 100 != 2) {
                throw new IOException("GitHub returned HTTP " + connection.getResponseCode());
            }
        } finally {
            connection.disconnect();
        }
    }

    public List<PullRequest> listPullRequests(final String search) throws IOException {
        final long started = System.currentTimeMillis();

        LOG.debug("PR API auth selection: project={}", project.name());
        final GHRepository repository = repository();
        final List<PullRequest> requests =
                StreamSupport.stream(
                                client().searchPullRequests()
                                        .q(
                                                "repo:"
                                                        + repository.getFullName()
                                                        + " is:pr is:open "
                                                        + search)
                                        .list()
                                        .spliterator(),
                                false)
                        .map(this::toPullRequest)
                        .toList();

        LOG.info(
                "PR API load finished: project={}, search={}, count={}, elapsedMs={}",
                project.name(),
                search,
                requests.size(),
                System.currentTimeMillis() - started);
        return requests;
    }

    public List<Issue> listIssues() throws IOException, InterruptedException {

        return StreamSupport.stream(
                        client().searchIssues()
                                .q("repo:" + repository.getFullName() + " is:issue is:open")
                                .list()
                                .spliterator(),
                        false)
                .map(
                        issue ->
                                new Issue(
                                        issue.getNumber(),
                                        issue.getTitle(),
                                        issue.getBody(),
                                        issue.getHtmlUrl()))
                .toList();
    }

    public PullRequestReviews getReviews(final PullRequest request)
            throws IOException, InterruptedException {
        final List<PullRequestReview> reviews =
                repository().getPullRequest(request.number()).listReviews().toList().stream()
                        .map(
                                review -> {
                                    try {
                                        return PullRequestReview.from(review);
                                    } catch (IOException e) {
                                        LOG.warn(
                                                "Failed to convert GitHub review to PullRequestReview",
                                                e);
                                    }
                                    return null;
                                })
                        .filter(Objects::nonNull)
                        .toList();
        return new PullRequestReviews(reviews);
    }
}
