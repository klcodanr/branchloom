package com.jagent.desktop.async;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jagent.desktop.services.BackgroundJobs;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AsyncLoadingState<I, C, V, L extends AsyncStateListener<V>> {

    private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(5);
    private static final long CACHE_MAXIMUM_SIZE = 25;
    private final String name;

    private final BackgroundJobs backgroundJobs;
    private final Cache<I, V> cache =
            Caffeine.newBuilder()
                    .maximumSize(CACHE_MAXIMUM_SIZE)
                    .expireAfterWrite(CACHE_EXPIRATION)
                    .build();
    private final Map<I, L> viewDataListeners = new ConcurrentHashMap<>();
    private final Map<I, Boolean> viewLoadingMap = new ConcurrentHashMap<>();
    private final Map<I, C> viewLoadingQueuedMap = new ConcurrentHashMap<>();

    public AsyncLoadingState(final String name, final BackgroundJobs backgroundJobs) {
        this.name = name;
        this.backgroundJobs = backgroundJobs;
    }

    /**
     * Fetches the view data for the given instance and configuration. If a fetch is already in
     * progress for the instance, the request will be queued and executed once the current fetch
     * completes. The result is cached for subsequent calls.
     *
     * @param instance the instance for which to fetch the view data
     * @param config the configuration to use when fetching the view data
     * @return a CompletableFuture that will be completed on the EDT with the fetched view data once
     *     available
     */
    public @NotNull CompletableFuture<V> fetch(final I instance, final C config) {
        return fetch(instance, config, true);
    }

    private CompletableFuture<V> fetch(
            final I instance, final C config, final boolean notifyListener) {
        if (viewLoadingMap.putIfAbsent(instance, true) != null) {
            viewLoadingQueuedMap.put(instance, config);
            return CompletableFuture.completedFuture(null);
        }
        viewLoadingMap.put(instance, true);
        final BackgroundJobs.Handle handle = this.backgroundJobs.start("View Data: " + name);
        return loadLatest(instance, config, handle)
                .whenComplete(
                        (value, error) -> {
                            viewLoadingMap.remove(instance);
                            if (error == null) {
                                handle.complete();
                            } else {
                                handle.fail(
                                        "View Data: " + name + " failed: " + error.getMessage());
                            }
                            if (!notifyListener) {
                                return;
                            }
                            final L listener = getListener(instance);
                            if (listener == null) {
                                return;
                            }
                            SwingUtilities.invokeLater(
                                    () -> {
                                        if (error == null) {
                                            listener.onComplete(value);
                                        } else {
                                            listener.onFailure(unwrap(error));
                                        }
                                    });
                        });
    }

    private CompletableFuture<V> loadLatest(
            final I instance, final C config, final BackgroundJobs.Handle handle) {
        return BackgroundOperations.submit(
                        "View Data",
                        name,
                        () -> {
                            final V data = load(instance, config, handle);
                            cache.put(instance, data);
                            return data;
                        })
                .thenCompose(
                        value -> {
                            final C queuedConfig = viewLoadingQueuedMap.remove(instance);
                            if (queuedConfig == null) {
                                return CompletableFuture.completedFuture(value);
                            }
                            return loadLatest(instance, queuedConfig, handle);
                        });
    }

    /**
     * Returns the cached value for the given instance if it is available immediately.
     *
     * @param instance
     * @return an Optional containing the cached value if present, or an empty Optional if not.
     */
    public @Nullable Optional<V> get(final I instance) {
        return Optional.ofNullable(cache.getIfPresent(instance));
    }

    public void setListener(final I instance, final L listener) {
        viewDataListeners.put(instance, listener);
    }

    protected L getListener(final I instance) {
        return viewDataListeners.get(instance);
    }

    protected abstract V load(I instance, C config, BackgroundJobs.Handle jobHandle)
            throws IOException, InterruptedException;

    private static Throwable unwrap(final Throwable error) {
        return error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
    }
}
