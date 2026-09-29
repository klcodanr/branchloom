package com.jagent.desktop.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PullRequestCache {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestCache.class);
    private static final String LOAD_FAILURE = "Failed to load pull requests";
    private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(5);
    private static final long CACHE_MAXIMUM_SIZE = 512;
    private static final Object INSTANCE_LOCK = new Object();
    private static PullRequestCache instance;
    private final AppState appState;
    private volatile Consumer<Exception> failure = exception -> {};
    private final java.util.Set<String> reportedFailures = ConcurrentHashMap.newKeySet();
    private final Cache<CacheKey, List<PullRequest>> results =
            Caffeine.newBuilder()
                    .maximumSize(CACHE_MAXIMUM_SIZE)
                    .expireAfterWrite(CACHE_EXPIRATION)
                    .build();

    // @SuppressFBWarnings("EI_EXPOSE_REP")
    public static PullRequestCache get(final AppState appState) {
        return get(appState, exception -> {});
    }

    public static PullRequestCache get(final AppState appState, final Consumer<Exception> failure) {
        synchronized (INSTANCE_LOCK) {
            if (instance == null) {
                instance = new PullRequestCache(appState);
            }
            instance.failure = failure;
            return instance;
        }
    }

    private PullRequestCache(final AppState appState) {
        this.appState = appState;
    }

    public List<PullRequest> loadForFilter(final PullRequestFilter filter) {
        return loadForFilter(filter, false);
    }

    public List<PullRequest> refreshForFilter(final PullRequestFilter filter) {
        return loadForFilter(filter, true);
    }

    private List<PullRequest> loadForFilter(
            final PullRequestFilter filter, final boolean forceRefresh) {
        return appState.projects().entrySet().stream()
                .flatMap(
                        entry -> {
                            try {
                                return load(
                                        entry.getKey(),
                                        entry.getValue(),
                                        query(filter),
                                        forceRefresh)
                                        .stream();
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                reportFailure(entry.getKey(), exception);
                                LOG.error(LOAD_FAILURE, exception);
                                return java.util.stream.Stream.<PullRequest>empty();
                            } catch (IOException exception) {
                                reportFailure(entry.getKey(), exception);
                                LOG.error(LOAD_FAILURE, exception);
                                return java.util.stream.Stream.<PullRequest>empty();
                            }
                        })
                .distinct()
                .toList();
    }

    public List<PullRequest> loadForProjectFilter(
            final ProjectId projectId, final PullRequestFilter filter) {
        return loadForProjectFilter(projectId, filter, false);
    }

    public List<PullRequest> refreshForProjectFilter(
            final ProjectId projectId, final PullRequestFilter filter) {
        return loadForProjectFilter(projectId, filter, true);
    }

    private List<PullRequest> loadForProjectFilter(
            final ProjectId projectId, final PullRequestFilter filter, final boolean forceRefresh) {
        final var project = appState.projects().get(projectId);
        if (project == null) {
            return List.of();
        }
        try {
            return load(projectId, project, query(filter), forceRefresh);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            reportFailure(projectId, exception);
            LOG.error(LOAD_FAILURE, exception);
            throw new IllegalStateException(LOAD_FAILURE, exception);
        } catch (IOException exception) {
            reportFailure(projectId, exception);
            LOG.error(LOAD_FAILURE, exception);
            throw new IllegalStateException(LOAD_FAILURE, exception);
        }
    }

    private List<PullRequest> load(
            final ProjectId projectId,
            final com.jagent.desktop.models.Project project,
            final String query,
            final boolean forceRefresh)
            throws IOException, InterruptedException {
        final CacheKey key = new CacheKey(projectId, query);
        if (forceRefresh) {
            results.invalidate(key);
        }
        final List<PullRequest> cached = results.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        final List<PullRequest> loaded =
                GitHub.loadForProject(projectId, project, query, appState.githubConnections());
        results.put(key, loaded);
        clearFailures(projectId);
        return loaded;
    }

    private static String query(final PullRequestFilter filter) {
        return filter == null ? "" : filter.query().trim();
    }

    private void reportFailure(final ProjectId projectId, final Exception exception) {
        final String key =
                projectId.value()
                        + "|"
                        + exception.getClass().getName()
                        + "|"
                        + exception.getMessage();
        if (reportedFailures.add(key)) {
            failure.accept(exception);
        }
    }

    private void clearFailures(final ProjectId projectId) {
        final String prefix = projectId.value() + "|";
        reportedFailures.removeIf(key -> key.startsWith(prefix));
    }

    private record CacheKey(ProjectId projectId, String query) {}
}
