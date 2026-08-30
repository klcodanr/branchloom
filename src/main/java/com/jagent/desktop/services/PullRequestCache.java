package com.jagent.desktop.services;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PullRequestCache {
    private static final Logger LOG = Logger.getLogger(PullRequestCache.class.getName());
    private static final Object INSTANCE_LOCK = new Object();
    private static PullRequestCache instance;
    private final AppState appState;
    private final Map<ProjectId, CacheEntry> map = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        final Thread t = new Thread(r, "cache-cleaner");
                        t.setDaemon(true);
                        return t;
                    });

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
        this.cleaner.scheduleAtFixedRate(this::clearExpired, 0, 60_000, TimeUnit.MILLISECONDS);
    }

    private ProjectPullRequests load(final ProjectId projectId)
            throws IOException, InterruptedException {
        final var project = appState.projects().get(projectId);
        if (project == null) {
            return new ProjectPullRequests(List.of(), List.of());
        }
        final ProjectPullRequests requests =
                new ProjectPullRequests(
                        GitHub.loadForProject(projectId, project),
                        GitHub.loadReviewRequestedForProject(projectId, project));
        put(projectId, requests);
        return requests;
    }

    private void put(final ProjectId projectId, final ProjectPullRequests projectPullRequests) {
        final long expiryTime = System.currentTimeMillis() + 30_000;
        map.put(projectId, new CacheEntry(projectPullRequests, expiryTime));
    }

    public ProjectPullRequests get(final ProjectId projectId) {
        final CacheEntry entry = map.get(projectId);
        if (entry == null || entry.isExpired()) {
            map.remove(projectId);
            try {
                return load(projectId);
            } catch (IOException | InterruptedException e) {
                LOG.log(Level.SEVERE, "Failed to load pull requests", e);
                return new ProjectPullRequests(List.of(), List.of());
            }
        }
        return entry.projectPullRequests;
    }

    public ProjectPullRequests getCached(final ProjectId projectId) {
        final CacheEntry entry = map.get(projectId);
        return entry == null || entry.isExpired()
                ? new ProjectPullRequests(List.of(), List.of())
                : entry.projectPullRequests;
    }

    public boolean hasCached(final ProjectId projectId) {
        final CacheEntry entry = map.get(projectId);
        return entry != null && !entry.isExpired();
    }

    private void clearExpired() {
        map.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private record CacheEntry(ProjectPullRequests projectPullRequests, long expiryTime) {
        private boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    public ProjectPullRequests refresh(final ProjectId projectId) {
        try {
            return load(projectId);
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Failed to refresh pull requests", e);
            return new ProjectPullRequests(List.of(), List.of());
        }
    }

    public record ProjectPullRequests(List<PullRequest> authored, List<PullRequest> review) {}
}
