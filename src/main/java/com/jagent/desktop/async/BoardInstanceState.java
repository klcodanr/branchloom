package com.jagent.desktop.async;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.board.AgentSessionBoardItem;
import com.jagent.desktop.models.board.BoardItem;
import com.jagent.desktop.models.board.BoardSource;
import com.jagent.desktop.models.board.GithubIssueBoardItem;
import com.jagent.desktop.models.board.JiraIssueBoardItem;
import com.jagent.desktop.models.board.sources.JiraConfig;
import com.jagent.desktop.models.board.sources.SourceConfigs;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.components.UiText;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads and normalizes all supported board sources into one item model. */
public final class BoardInstanceState
        extends BatchedAsyncLoadingState<String, SourceConfigs, BoardItem> {
    private static final String JIRA_TOKEN = "JIRA_TOKEN";
    private static final DateTimeFormatter JIRA_DATE_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                    .optionalEnd()
                    .appendPattern("Z")
                    .toFormatter();
    private static final Logger LOG = LoggerFactory.getLogger(BoardInstanceState.class);
    private final AppState appState;

    public BoardInstanceState(final AppState appState, final BackgroundJobs backgroundJobs) {
        super("Boards", backgroundJobs);
        this.appState = appState;
    }

    private record LoadTask(String name, Callable<List<BoardItem>> load) {}

    @Override
    protected CompletableFuture<Void> loadBatches(
            final SourceConfigs sources, final BatchListener<BoardItem> listener) {

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            final List<LoadTask> tasks = new ArrayList<>();
            if (sources.AGENT_SESSION().enabled()) {
                tasks.add(new LoadTask("Agent Sessions", this::loadSessions));
            }
            if (sources.JIRA().enabled()) {
                tasks.add(new LoadTask("Jira Issues", () -> loadJiraIssues(sources.JIRA())));
            }
            for (final Map.Entry<ProjectId, Project> pe : this.appState.projects().entrySet()) {
                if (sources.GITHUB_ISSUES().enabled()) {
                    tasks.add(
                            new LoadTask(
                                    pe.getValue().name() + ": GitHub Issues",
                                    () ->
                                            loadProjectGithubIssues(
                                                    pe.getKey(), sources.GITHUB_ISSUES().query())));
                }

                if (sources.PULL_REQUEST().enabled()) {
                    tasks.add(
                            new LoadTask(
                                    pe.getValue().name() + ": Pull Requests",
                                    () ->
                                            loadPullRequests(
                                                    pe.getKey(), sources.PULL_REQUEST().query())));
                }
            }
            listener.onStart(tasks.size());
            final List<BoardItem> allValues = Collections.synchronizedList(new ArrayList<>());
            return CompletableFuture.allOf(
                            tasks.stream()
                                    .map(
                                            t ->
                                                    CompletableFuture.supplyAsync(
                                                            () -> {
                                                                try {
                                                                    listener.onMessage(
                                                                            "Loading " + t.name);
                                                                    final List<BoardItem> result =
                                                                            t.load.call();
                                                                    allValues.addAll(result);
                                                                    listener.onProgress(1, result);
                                                                    listener.onMessage(
                                                                            "SUCCESS: " + t.name);
                                                                } catch (Exception e) {
                                                                    LOG.warn(
                                                                            "Error loading task: "
                                                                                    + t.name,
                                                                            e);
                                                                    listener.onMessage(
                                                                            "FAILURE: " + t.name);
                                                                    listener.onProgress(1, null);
                                                                }
                                                                return null;
                                                            },
                                                            executor))
                                    .collect(Collectors.toList())
                                    .toArray(new CompletableFuture<?>[0]))
                    .thenRun(
                            () -> {
                                listener.onMessage("All tasks completed.");
                                listener.onComplete(allValues);
                            });
        }
    }

    private List<BoardItem> loadProjectGithubIssues(final ProjectId projectId, final String query)
            throws IOException, InterruptedException {
        final Project project = appState.projects().get(projectId);
        if (project == null) {
            return List.of();
        }
        final List<BoardItem> items = new ArrayList<>();
        for (final var issue : GitHub.forProject(appState, projectId).listIssues(query)) {
            items.add(
                    new GithubIssueBoardItem(
                            issue.number(),
                            "open",
                            project.name(),
                            BoardSource.GITHUB_ISSUES,
                            projectId + ":" + issue.number(),
                            issue.title(),
                            issue.body(),
                            issue.createdAt(),
                            issue.updatedAt(),
                            issue.url(),
                            projectId.value().toString()));
        }
        return items;
    }

    private List<BoardItem> loadPullRequests(final ProjectId projectId, final String query)
            throws IOException, InterruptedException {
        final Project project = appState.projects().get(projectId);
        if (project == null) {
            return List.of();
        }
        final List<BoardItem> items = new ArrayList<>();
        final GitHub gitHub = GitHub.forProject(appState, projectId);
        for (final PullRequest request : gitHub.listPullRequests(query)) {

            final PullRequestDetails details = gitHub.getPullRequestDetails(request);
            items.add(details);
        }
        return items;
    }

    private List<BoardItem> loadSessions() {
        final List<BoardItem> items = new ArrayList<>();
        for (final var entry : appState.sessions().entrySet()) {
            final Session session = entry.getValue();
            final Project project = appState.projects().get(session.projectId());
            items.add(
                    new AgentSessionBoardItem(
                            session.terminalIds().isEmpty() ? "idle" : "active",
                            UiText.valueOrDefault(session.agent(), ""),
                            project == null ? "" : project.name(),
                            UiText.valueOrDefault(session.worktreePath(), ""),
                            session.projectId(),
                            entry.getKey(),
                            BoardSource.AGENT_SESSION,
                            entry.getKey().value().toString(),
                            session.name(),
                            UiText.valueOrDefault(session.prompt(), ""),
                            Date.from(session.created()),
                            Date.from(session.created()),
                            null));
        }
        return List.copyOf(items);
    }

    private List<BoardItem> loadJiraIssues(final JiraConfig source) throws IOException {
        final String configuredEndpoint = appState.appSettings().jiraEndpoint();
        if (configuredEndpoint == null || configuredEndpoint.isBlank()) {
            return List.of();
        }
        final String endpoint = configuredEndpoint.replaceAll("/$", "");
        final HttpURLConnection connection = jiraConnection(endpoint, source);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() / 100 != 2) {
                throw new IOException("Jira returned HTTP " + connection.getResponseCode());
            }
            final JsonObject root =
                    JsonParser.parseString(
                                    new String(
                                            connection.getInputStream().readAllBytes(),
                                            StandardCharsets.UTF_8))
                            .getAsJsonObject();
            final JsonArray issues = root.getAsJsonArray("issues");
            final List<BoardItem> result = new ArrayList<>();
            for (final JsonElement element : issues) {
                final JsonObject issue = element.getAsJsonObject();
                final JsonObject fields = issue.getAsJsonObject("fields");
                final String key = text(issue, "key");
                final String project = nestedText(fields, "project", "key");
                result.add(
                        new JiraIssueBoardItem(
                                BoardSource.JIRA,
                                key,
                                text(fields, "summary"),
                                text(fields, "description"),
                                jiraDate(fields, "created"),
                                jiraDate(fields, "updated"),
                                URI.create(endpoint + "/browse/" + key).toURL(),
                                project,
                                nestedText(fields, "status", "name"),
                                nestedText(fields, "creator", "emailAddress"),
                                nestedText(fields, "assignee", "emailAddress")));
            }
            return List.copyOf(result);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection jiraConnection(final String endpoint, final JiraConfig source)
            throws IOException {
        final String token = System.getenv(JIRA_TOKEN);
        if (token == null || token.isBlank()) {
            throw new IOException("Jira token is not configured in " + JIRA_TOKEN);
        }
        final String query =
                source.query().isBlank()
                        ? ""
                        : "&jql=" + URLEncoder.encode(source.query(), StandardCharsets.UTF_8);
        final HttpURLConnection connection =
                (HttpURLConnection)
                        URI.create(endpoint + "/rest/api/2/search?maxResults=100" + query)
                                .toURL()
                                .openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }

    private static String text(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String nestedText(
            final JsonObject object, final String parent, final String child) {
        final JsonElement value = object.get(parent);
        return value == null || !value.isJsonObject() ? "" : text(value.getAsJsonObject(), child);
    }

    private static Date jiraDate(final JsonObject object, final String name) {
        final String value = text(object, name);
        if (value.isBlank()) {
            return new Date(0);
        }
        try {
            return Date.from(OffsetDateTime.parse(value, JIRA_DATE_FORMATTER).toInstant());
        } catch (RuntimeException exception) {
            try {
                return Date.from(
                        LocalDateTime.parse(value, JIRA_DATE_FORMATTER).toInstant(ZoneOffset.UTC));
            } catch (RuntimeException ignored) {
                LOG.warn("Unable to parse Jira {} date: {}", name, value);
                return new Date(0);
            }
        }
    }
}
