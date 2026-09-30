package com.jagent.desktop.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jagent.desktop.models.GitHubConnection;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestCheck;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.PullRequestReview;
import com.jagent.desktop.models.PullRequestReviews;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.components.UiText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHubAbuseLimitHandler;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.RateLimitChecker;
import org.kohsuke.github.RateLimitTarget;
import org.kohsuke.github.connector.GitHubConnector;
import org.kohsuke.github.connector.GitHubConnectorRequest;
import org.kohsuke.github.connector.GitHubConnectorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"PMD.GodClass", "PMD.CommentDefaultAccessModifier"})
public final class GitHub {
    private static final String DEFAULT_HOST = "github.com";
    private static final String CLI_CONNECTION_ID = "github-cli";
    private static final Logger LOG = LoggerFactory.getLogger(GitHub.class);
    private static final GitHubTokenFactory TOKEN_FACTORY = createTokenFactory();
    private static final Duration CACHE_EXPIRATION = Duration.ofHours(1);
    private static final long CACHE_MAXIMUM_SIZE = 512;
    private static final int CORE_RATE_LIMIT_BUFFER = 1;
    private static final int SEARCH_RATE_LIMIT_BUFFER = 1;
    private static final Cache<RepositoryCacheKey, GHRepository> REPOSITORIES =
            Caffeine.newBuilder()
                    .maximumSize(CACHE_MAXIMUM_SIZE)
                    .expireAfterWrite(CACHE_EXPIRATION)
                    .build();

    public record Auth(String host, String user, String connectionId, String displayName) {
        public Auth(final String host, final String user) {
            this(host, user, CLI_CONNECTION_ID, null);
        }

        public Auth(final String host, final String user, final String connectionId) {
            this(host, user, connectionId, null);
        }

        public GitHubConnection connection() {
            return isCli()
                    ? new GitHubConnection(
                            connectionId == null ? CLI_CONNECTION_ID : connectionId,
                            "GitHub CLI",
                            host,
                            user,
                            false,
                            null)
                    : new GitHubConnection(
                            connectionId, label(), host, user, true, "github:" + connectionId);
        }

        @Override
        public String toString() {
            return label();
        }

        private String label() {
            return isCli()
                    ? UiText.valueOrDefault(user, "GitHub CLI") + " (" + host + ")"
                    : (displayName == null || displayName.isBlank()
                                    ? "Personal access token"
                                    : displayName)
                            + " ("
                            + host
                            + ")";
        }

        public boolean isCli() {
            return connectionId == null
                    || CLI_CONNECTION_ID.equals(connectionId)
                    || connectionId.startsWith(CLI_CONNECTION_ID + ":");
        }
    }

    private GitHub() {}

    private static GitHubTokenFactory createTokenFactory() {
        CredentialStore credentialStore;
        try {
            credentialStore = new KeyringCredentialStore();
        } catch (RuntimeException exception) {
            LOG.warn(
                    "OS keyring unavailable; using in-memory credential store for this run",
                    exception);
            credentialStore = new InMemoryCredentialStore();
        }
        return new GitHubTokenFactory(
                new PersonalAccessTokenProvider(credentialStore, GitHubTokenExpiration.http()),
                new CliTokenProvider(),
                Clock.systemUTC());
    }

    public record Issue(int number, String title, String body, String url) {}

    private static final class InMemoryCredentialStore implements CredentialStore {
        private final Map<String, String> secrets = new HashMap<>();

        @Override
        public void put(final String key, final String secret) {
            secrets.put(key, secret);
        }

        @Override
        public Optional<String> get(final String key) {
            return Optional.ofNullable(secrets.get(key));
        }

        @Override
        public void delete(final String key) {
            secrets.remove(key);
        }
    }

    public static List<Auth> configuredAuths(final AppState state) {
        final List<Auth> auths = new ArrayList<>(cliAuths());
        auths.addAll(state.githubConnections().values().stream().map(GitHub::auth).toList());
        return List.copyOf(auths);
    }

    private static List<Auth> cliAuths() {
        try {
            final Process process =
                    new ProcessBuilder("gh", "auth", "status", "--json", "hosts")
                            .redirectErrorStream(true)
                            .start();
            final String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                return List.of(new Auth(DEFAULT_HOST, null, CLI_CONNECTION_ID));
            }
            try {
                return parseCliAuths(output);
            } catch (RuntimeException ignored) {
                return List.of(new Auth(DEFAULT_HOST, null, CLI_CONNECTION_ID));
            }
        } catch (IOException exception) {
            return List.of(new Auth(DEFAULT_HOST, null, CLI_CONNECTION_ID));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of(new Auth(DEFAULT_HOST, null, CLI_CONNECTION_ID));
        }
    }

    private static List<Auth> parseCliAuths(final String output) {
        final JsonObject hosts =
                JsonParser.parseString(output).getAsJsonObject().getAsJsonObject("hosts");
        if (hosts == null || hosts.isEmpty()) {
            return List.of(new Auth(DEFAULT_HOST, null, CLI_CONNECTION_ID));
        }
        final List<Auth> auths = new ArrayList<>();
        for (final String host : hosts.keySet()) {
            final JsonElement entries = hosts.get(host);
            if (entries.isJsonArray()) {
                entries.getAsJsonArray()
                        .forEach(
                                entry -> {
                                    final JsonObject account = entry.getAsJsonObject();
                                    final JsonElement login = account.get("login");
                                    if (login != null && !login.isJsonNull()) {
                                        final String user = login.getAsString();
                                        auths.add(
                                                new Auth(
                                                        host,
                                                        user,
                                                        CLI_CONNECTION_ID
                                                                + ":"
                                                                + host
                                                                + ":"
                                                                + user));
                                    }
                                });
            }
        }
        return auths.isEmpty()
                ? List.of(new Auth(DEFAULT_HOST, null, CLI_CONNECTION_ID))
                : List.copyOf(auths);
    }

    private static Auth auth(final GitHubConnection connection) {
        return new Auth(connection.host(), connection.user(), connection.id(), connection.name());
    }

    public static List<PullRequest> loadForProject(
            final ProjectId projectId, final Project project, final String search)
            throws IOException, InterruptedException {
        return loadForProject(projectId, project, search, Map.of());
    }

    public static List<PullRequest> loadForProject(
            final ProjectId projectId,
            final Project project,
            final String search,
            final Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        return load(
                projectId,
                project,
                Objects.requireNonNullElse(search, "").trim(),
                configuredConnections);
    }

    public static List<Issue> loadIssuesForProject(final Project project)
            throws IOException, InterruptedException {
        final GHRepository repository = repository(project, Map.of());
        return StreamSupport.stream(
                        projectClient(project, Map.of())
                                .searchIssues()
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
                                        url(issue)))
                .toList();
    }

    public static PullRequest pullRequest(
            final ProjectId projectId, final Project project, final Path worktree)
            throws IOException, InterruptedException {
        return pullRequest(projectId, project, worktree, Map.of());
    }

    public static PullRequest pullRequest(
            final ProjectId projectId,
            final Project project,
            final Path worktree,
            final Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
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

        final GHRepository repository = repository(project, configuredConnections);
        final String fullName = repository.getFullName();
        final int separator = fullName.indexOf('/');
        final String owner = fullName.substring(0, separator);
        final GHPullRequest request =
                StreamSupport.stream(
                                repository
                                        .queryPullRequests()
                                        .state(GHIssueState.OPEN)
                                        .head(owner + ":" + branch)
                                        .list()
                                        .spliterator(),
                                false)
                        .findFirst()
                        .orElseThrow(() -> new IOException("No pull request found"));

        JsonLogging.info(
                GitHub.class,
                "PR status lookup finished",
                Map.of(
                        "project",
                        project.name(),
                        "worktree",
                        worktree.toString(),
                        "result",
                        "found",
                        "number",
                        request.getNumber()));
        return PullRequest.from(projectId, project, request);
    }

    public static PullRequestDetails pullRequestDetails(
            final ProjectId projectId, final Project project, final int number)
            throws IOException, InterruptedException {
        return pullRequestDetails(projectId, project, number, Map.of());
    }

    public static PullRequestDetails pullRequestDetails(
            final ProjectId projectId,
            final Project project,
            final int number,
            final Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        return PullRequestDetails.fromPullRequest(
                projectId,
                project,
                repository(project, configuredConnections).getPullRequest(number));
    }

    public static GHPullRequest nativePullRequest(final Project project, final int number)
            throws IOException, InterruptedException {
        return nativePullRequest(project, number, Map.of());
    }

    public static GHPullRequest nativePullRequest(
            final Project project,
            final int number,
            final Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        return repository(project, configuredConnections).getPullRequest(number);
    }

    static String apiEndpoint(final Project project) {
        final String host = fallbackConnection(project).host();
        return apiEndpoint(host);
    }

    static String apiEndpoint(
            final Project project, final Map<String, GitHubConnection> configuredConnections)
            throws IOException {
        final String host = connection(project, configuredConnections).host();
        return apiEndpoint(host);
    }

    static String apiEndpoint(final String host) {
        return DEFAULT_HOST.equalsIgnoreCase(host)
                ? "https://api.github.com"
                : "https://" + host + "/api/v3";
    }

    static String token(final Project project) throws IOException, InterruptedException {
        return token(project, Map.of());
    }

    static String token(
            final Project project, final Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        return TOKEN_FACTORY.token(connection(project, configuredConnections));
    }

    private static GitHubConnection fallbackConnection(final Project project) {
        final String configuredHost = project.githubHost();
        final String host = UiText.valueOrDefault(configuredHost, DEFAULT_HOST);
        final String id = project.githubConnectionId();
        return new GitHubConnection(
                id == null ? CLI_CONNECTION_ID : id,
                id == null ? "GitHub CLI" : "GitHub connection",
                host,
                project.githubUser(),
                id != null && !isCliConnection(id, CLI_CONNECTION_ID),
                id == null || isCliConnection(id, CLI_CONNECTION_ID) ? null : "github:" + id);
    }

    private static GitHubConnection connection(
            final Project project, final Map<String, GitHubConnection> configuredConnections)
            throws IOException {
        final GitHubConnection fallback = fallbackConnection(project);
        if (!fallback.usePersonalAccessToken()) {
            return fallback;
        }
        final GitHubConnection configured = configuredConnections.get(fallback.id());
        if (configured == null) {
            throw new IOException("Configured GitHub connection not found: " + fallback.id());
        }
        return configured;
    }

    private static List<PullRequest> load(
            final ProjectId projectId,
            final Project project,
            final String search,
            final Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        final long started = System.currentTimeMillis();
        final GitHubConnection auth = connection(project, configuredConnections);
        JsonLogging.info(
                GitHub.class,
                "PR API load started",
                Map.of("project", project.name(), "search", search));
        LOG.debug(
                "PR API auth selection: project={}, host={}, user={}, connectionId={}, usingPAT={}",
                project.name(),
                auth.host(),
                auth.user(),
                auth.id(),
                auth.usePersonalAccessToken());
        final GHRepository repository = repository(project, configuredConnections);
        final List<PullRequest> requests =
                StreamSupport.stream(
                                projectClient(project, configuredConnections)
                                        .searchPullRequests()
                                        .q(
                                                "repo:"
                                                        + repository.getFullName()
                                                        + " is:pr is:open "
                                                        + search)
                                        .list()
                                        .spliterator(),
                                false)
                        .map(request -> PullRequest.from(projectId, project, request))
                        .toList();

        LOG.info(
                "PR API load finished: project={}, search={}, count={}, elapsedMs={}",
                project.name(),
                search,
                requests.size(),
                System.currentTimeMillis() - started);
        return requests;
    }

    private static boolean isCliConnection(final String id, final String cliConnectionId) {
        return cliConnectionId.equals(id) || id.startsWith(cliConnectionId + ":");
    }

    public static PullRequestChecks getChecks(
            final PullRequest request, final Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        final GHRepository repository = repository(request.project(), configuredConnections);
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
            return new PullRequestChecks(List.of());
        }
        final List<PullRequestCheck> checksFromRuns =
                latestCheckRunsByName(repository.getCheckRuns(headSha).toList()).stream()
                        .map(PullRequestCheck::from)
                        .toList();
        final List<PullRequestCheck> checksFromStatuses =
                repository.getCommit(headSha).listStatuses().toList().stream()
                        .map(PullRequestCheck::from)
                        .toList();
        final List<PullRequestCheck> checks = mergeChecks(checksFromRuns, checksFromStatuses);
        LOG.debug(
                "PR checks lookup finished: project={}, number={}, headSha={}, checks={}",
                request.project().name(),
                request.number(),
                request.abbreviatedHeadSha(),
                checks.size());
        return new PullRequestChecks(checks);
    }

    static List<PullRequestCheck> mergeChecks(
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

    private static long checkTimestamp(final PullRequestCheck check) {
        return check.completedAt() == null ? Long.MIN_VALUE : check.completedAt().getTime();
    }

    private static List<GHCheckRun> latestCheckRunsByName(final List<GHCheckRun> runs) {
        final Map<String, GHCheckRun> latestByName = new HashMap<>();
        for (final GHCheckRun run : runs) {
            final String name = run.getName() == null ? "" : run.getName().trim();
            final GHCheckRun existing = latestByName.get(name);
            if (existing == null || checkRunTimestamp(run) >= checkRunTimestamp(existing)) {
                latestByName.put(name, run);
            }
        }
        return latestByName.values().stream()
                .sorted(
                        (left, right) ->
                                Long.compare(checkRunTimestamp(right), checkRunTimestamp(left)))
                .toList();
    }

    private static long checkRunTimestamp(final GHCheckRun run) {
        final Date completed = run.getCompletedAt();
        if (completed != null) {
            return completed.getTime();
        }
        final Date started = run.getStartedAt();
        if (started != null) {
            return started.getTime();
        }
        return Long.MIN_VALUE;
    }

    public static PullRequestReviews getReviews(final PullRequest request)
            throws IOException, InterruptedException {
        return getReviews(request, Map.of());
    }

    public static PullRequestReviews getReviews(
            final PullRequest request, final Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        final List<PullRequestReview> reviews =
                repository(request.project(), configuredConnections)
                        .getPullRequest(request.number())
                        .listReviews()
                        .toList()
                        .stream()
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

    private static String url(final GHObject object) {
        try {
            return object.getHtmlUrl().toString();
        } catch (IOException exception) {
            return "";
        }
    }

    private static GHRepository repository(
            final Project project, final Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        final String name;
        try (GitRepository gitRepo = GitRepository.open(Path.of(project.path()))) {
            name = gitRepo.repositoryOrgAndName();
        }
        if (name == null) {
            throw new IOException("No GitHub remote found for this project");
        }
        final String token = token(project, configuredConnections);
        final String apiEndpoint = apiEndpoint(project, configuredConnections);

        final RepositoryCacheKey key = new RepositoryCacheKey(apiEndpoint, token, name);
        final GHRepository repository =
                REPOSITORIES.get(
                        key,
                        (n) -> {
                            final org.kohsuke.github.GitHub client;
                            try {
                                client = client(apiEndpoint, token);
                                return client.getRepository(name);
                            } catch (IOException | InterruptedException e) {
                                LOG.error("Failed to get GitHub repository for name: {}", name, e);
                            }
                            return null;
                        });
        if (repository == null) {
            throw new IOException("Failed to get GitHub repository for name: " + name);
        }
        return repository;
    }

    private static org.kohsuke.github.GitHub projectClient(
            final Project project, final Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        return client(
                apiEndpoint(project, configuredConnections), token(project, configuredConnections));
    }

    private static org.kohsuke.github.GitHub client(final String apiEndpoint, final String token)
            throws IOException, InterruptedException {
        return new GitHubBuilder()
                .withEndpoint(apiEndpoint)
                .withOAuthToken(token)
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
    }

    private static final class LoggingGitHubConnector implements GitHubConnector {
        private final GitHubConnector delegate;

        private LoggingGitHubConnector(final GitHubConnector delegate) {
            this.delegate = delegate;
        }

        @Override
        public GitHubConnectorResponse send(final GitHubConnectorRequest request)
                throws IOException {
            final long started = System.nanoTime();
            try {
                final GitHubConnectorResponse response = delegate.send(request);
                LOG.debug(
                        "GitHub API {} {} -> {} ({}ms, remaining={}, reset={})",
                        request.method(),
                        request.url().toExternalForm(),
                        response.statusCode(),
                        (System.nanoTime() - started) / 1_000_000,
                        UiText.valueOrDefault(response.header("X-RateLimit-Remaining"), "unknown"),
                        UiText.valueOrDefault(response.header("X-RateLimit-Reset"), "unknown"));
                return response;
            } catch (IOException exception) {
                LOG.debug(
                        "GitHub API {} {} failed after {}ms",
                        request.method(),
                        request.url(),
                        (System.nanoTime() - started) / 1_000_000,
                        exception);
                throw exception;
            }
        }
    }

    private record RepositoryCacheKey(String endpoint, String token, String repositoryName) {}
}
