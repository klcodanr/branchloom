package com.jagent.desktop.async;

import com.jagent.desktop.services.BackgroundJobs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BatchedAsyncLoadingState<I, C, V>
        extends AsyncLoadingState<I, C, List<V>, BatchListener<V>> {
    private static final Logger LOG = LoggerFactory.getLogger(BatchedAsyncLoadingState.class);

    public BatchedAsyncLoadingState(final String name, final BackgroundJobs backgroundJobs) {
        super(name, backgroundJobs);
    }

    protected abstract CompletableFuture<Void> loadBatches(C config, BatchListener<V> listener);

    @Override
    protected final List<V> load(
            final I instance, final C config, final BackgroundJobs.Handle jobHandler) {
        final List<V> allValues = Collections.synchronizedList(new ArrayList<>());
        try {
            loadBatches(
                            config,
                            new BatchListener<>() {

                                /** Total batches reported by the loader. */
                                private volatile int total;

                                @Override
                                public void onComplete(final List<V> data) {
                                    final BatchListener<V> listener = getListener(instance);
                                    if (listener != null) {
                                        listener.onComplete(allValues);
                                    }
                                    jobHandler.complete();
                                }

                                @Override
                                public void onMessage(final String message) {
                                    final BatchListener<V> listener = getListener(instance);
                                    if (listener != null) {
                                        listener.onMessage(message);
                                    }
                                    jobHandler.update(message);
                                    jobHandler.output(message);
                                }

                                @Override
                                public void onProgress(
                                        final int progress, @Nullable final List<V> progressData) {
                                    final BatchListener<V> listener = getListener(instance);
                                    if (listener != null) {
                                        listener.onProgress(progress, progressData);
                                    }
                                    if (progressData != null) {
                                        allValues.addAll(progressData);
                                    }
                                    jobHandler.update("Completed " + progress + " of " + total);
                                }

                                @Override
                                public void onStart(final int total) {
                                    final BatchListener<V> listener = getListener(instance);
                                    if (listener != null) {
                                        listener.onStart(total);
                                    }
                                    this.total = total;
                                }

                                @Override
                                public void onFailure(final Throwable error) {
                                    final BatchListener<V> listener = getListener(instance);
                                    if (listener != null) {
                                        listener.onFailure(error);
                                    }
                                    jobHandler.update("Failed: " + error.getMessage());
                                }
                            })
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.warn("Error loading batches", e);
        }

        return allValues;
    }
}
