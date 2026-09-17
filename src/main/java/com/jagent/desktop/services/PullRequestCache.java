package com.jagent.desktop.services;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PullRequestCache {
    private static final Logger LOG = Logger.getLogger(PullRequestCache.class.getName());
    private static final String LOAD_FAILURE = "Failed to load pull requests";
    private static final Object INSTANCE_LOCK = new Object();
    private static PullRequestCache instance;
    private final AppState appState;
    private final Map<ProjectId, CacheEntry> map = new ConcurrentHashMap<>();
    private final ScheduledExecutorService loader =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        final Thread t = new Thread(r, "pull-request-cache-loader");
                        t.setDaemon(true);
                        return t;
                    });
    private final java.util.Set<ProjectId> refreshInFlight = ConcurrentHashMap.newKeySet();

    // @SuppressFBWarnings("EI_EXPOSE_REP")
    public static PullRequestCache get(final AppState appState) {
        synchronized (INSTANCE_LOCK) {
            if (instance == null) {
                instance = new PullRequestCache(appState);
            }
            return instance;
        }
    }

    private PullRequestCache(final AppState appState) {
        this.appState = appState;
    }

    private ProjectPullRequests load(final ProjectId projectId)
            throws IOException, InterruptedException {
        final long started = System.nanoTime();
        final var project = appState.projects().get(projectId);
        if (project == null) {
            return new ProjectPullRequests(List.of(), List.of());
        }
        final ProjectPullRequests requests =
                new ProjectPullRequests(
                        GitHub.loadForProject(projectId, project, "author:@me"),
                        GitHub.loadReviewRequestedForProject(projectId, project));
        put(projectId, requests);
        LOG.info(
                () ->
                        "PR cache load finished: project="
                                + project.name()
                                + ", authored="
                                + requests.authored().size()
                                + ", review="
                                + requests.review().size()
                                + ", elapsedMs="
                                + (System.nanoTime() - started) / 1_000_000);
        return requests;
    }

    private void put(final ProjectId projectId, final ProjectPullRequests projectPullRequests) {
        map.put(projectId, new CacheEntry(projectPullRequests));
    }

    public ProjectPullRequests get(final ProjectId projectId) {
        final CacheEntry entry = map.get(projectId);
        if (entry == null) {
            try {
                return load(projectId);
            } catch (IOException | InterruptedException e) {
                LOG.log(Level.SEVERE, LOAD_FAILURE, e);
                return new ProjectPullRequests(List.of(), List.of());
            }
        }
        requestRefresh(projectId);
        return entry.projectPullRequests;
    }

    public ProjectPullRequests getCached(final ProjectId projectId) {
        final CacheEntry entry = map.get(projectId);
        return entry == null
                ? new ProjectPullRequests(List.of(), List.of())
                : entry.projectPullRequests;
    }

    public List<PullRequest> loadForFilter(final PullRequestFilter filter) {
        return appState.projects().entrySet().stream()
                .flatMap(
                        entry -> {
                            try {
                                return GitHub.loadForProject(
                                        entry.getKey(),
                                        entry.getValue(),
                                        filter == null ? "" : filter.query())
                                        .stream();
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                LOG.log(Level.SEVERE, LOAD_FAILURE, exception);
                                return java.util.stream.Stream.<PullRequest>empty();
                            } catch (IOException exception) {
                                LOG.log(Level.SEVERE, LOAD_FAILURE, exception);
                                return java.util.stream.Stream.<PullRequest>empty();
                            }
                        })
                .distinct()
                .toList();
    }

    public List<PullRequest> loadForProjectFilter(
            final ProjectId projectId, final PullRequestFilter filter) {
        final var project = appState.projects().get(projectId);
        if (project == null) {
            return List.of();
        }
        try {
            return GitHub.loadForProject(projectId, project, filter == null ? "" : filter.query());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.log(Level.SEVERE, LOAD_FAILURE, exception);
            return List.of();
        } catch (IOException exception) {
            LOG.log(Level.SEVERE, LOAD_FAILURE, exception);
            return List.of();
        }
    }

    public boolean hasCached(final ProjectId projectId) {
        return map.containsKey(projectId);
    }

    private void requestRefresh(final ProjectId projectId) {
        if (!refreshInFlight.add(projectId)) {
            return;
        }
        loader.execute(
                () -> {
                    try {
                        refresh(projectId);
                    } finally {
                        refreshInFlight.remove(projectId);
                    }
                });
    }

    private record CacheEntry(ProjectPullRequests projectPullRequests) {}

    public ProjectPullRequests refresh(final ProjectId projectId) {
        try {
            return load(projectId);
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Failed to refresh pull requests", e);
            return new ProjectPullRequests(List.of(), List.of());
        }
    }

    public List<PullRequest> refreshReview(final ProjectId projectId) {
        final long started = System.nanoTime();
        final var project = appState.projects().get(projectId);
        if (project == null) {
            return List.of();
        }
        try {
            final List<PullRequest> requests =
                    GitHub.loadReviewRequestedForProject(projectId, project);
            LOG.info(
                    () ->
                            "PR review load finished: project="
                                    + project.name()
                                    + ", count="
                                    + requests.size()
                                    + ", elapsedMs="
                                    + (System.nanoTime() - started) / 1_000_000);
            return requests;
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Failed to refresh review requests", e);
            return List.of();
        }
    }

    public record ProjectPullRequests(List<PullRequest> authored, List<PullRequest> review) {}
}
