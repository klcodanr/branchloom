package com.jagent.desktop.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.services.BackgroundJobs;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ViewInstanceStateTest {
    private static final long TIMEOUT_SECONDS = 5;
    private static final String SESSION = "session";
    private static final String FIRST = "first";
    private static final String LATEST = "latest";

    @Test
    void loadsOffEdtAndNotifiesListenerOnEdt()
            throws InterruptedException, ExecutionException, TimeoutException {
        final StateFixture state = new StateFixture();
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicBoolean listenerOnEdt = new AtomicBoolean();
        state.setListener(
                SESSION,
                new TestListener() {
                    @Override
                    public void onComplete(final String data) {
                        listenerOnEdt.set(SwingUtilities.isEventDispatchThread());
                        completed.countDown();
                    }

                    @Override
                    public void onFailure(final Throwable error) {
                        completed.countDown();
                    }
                });

        assertEquals(
                "config",
                state.fetch(SESSION, "config").get(5, TimeUnit.SECONDS),
                "loaded configuration should be returned");
        assertTrue(
                completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "completion listener should be called");
        assertFalse(state.loadOnEdt.get(), "load should run off the EDT");
        assertTrue(listenerOnEdt.get(), "listener should run on the EDT");
    }

    @Test
    void reportsLoadFailureToListenerAndFuture()
            throws InterruptedException, ExecutionException, TimeoutException {
        final StateFixture state = new StateFixture();
        state.failure = new IllegalStateException("load failed");
        final CountDownLatch failed = new CountDownLatch(1);
        final AtomicBoolean listenerOnEdt = new AtomicBoolean();
        final AtomicReference<Throwable> reported = new AtomicReference<>();
        state.setListener(
                SESSION,
                new TestListener() {
                    @Override
                    public void onComplete(final String data) {
                        failed.countDown();
                    }

                    @Override
                    public void onFailure(final Throwable error) {
                        listenerOnEdt.set(SwingUtilities.isEventDispatchThread());
                        reported.set(error);
                        failed.countDown();
                    }
                });

        final ExecutionException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        ExecutionException.class,
                        () -> state.fetch(SESSION, "config").get(5, TimeUnit.SECONDS));

        assertTrue(
                failed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "failure listener should be called");
        assertTrue(listenerOnEdt.get(), "failure listener should run on the EDT");
        assertNotNull(reported.get(), "failure should be reported");
        assertEquals("load failed", reported.get().getMessage(), "reported error should match");
        assertEquals("load failed", exception.getCause().getMessage(), "future error should match");
    }

    @Test
    void queuesRefreshWithoutBlockingEdtAndNotifiesWithLatestValue() throws InterruptedException {
        final StateFixture state = new StateFixture();
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicInteger listenerCalls = new AtomicInteger();
        final AtomicReference<String> result = new AtomicReference<>();
        state.blockFirstLoad = true;
        state.setListener(
                SESSION,
                new TestListener() {
                    @Override
                    public void onComplete(final String data) {
                        listenerCalls.incrementAndGet();
                        result.set(data);
                        completed.countDown();
                    }

                    @Override
                    public void onFailure(final Throwable error) {
                        completed.countDown();
                    }
                });

        state.fetch(SESSION, FIRST);
        assertTrue(
                state.firstLoadStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "first load should start");
        state.fetch(SESSION, LATEST);
        state.releaseFirstLoad.countDown();

        assertTrue(
                completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "latest load should complete");
        assertEquals(1, listenerCalls.get(), "listener should be called once");
        assertEquals("latest", result.get(), "latest configuration should win");
        assertEquals(List.of(FIRST, LATEST), state.configs, "loads should use both configs");
    }

    @Test
    void reservesInstanceBeforeSubmittingWorker() throws InterruptedException {
        final StateFixture state = new StateFixture();
        state.blockFirstLoad = true;

        state.fetch(SESSION, FIRST);
        state.fetch(SESSION, LATEST);
        assertTrue(
                state.firstLoadStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "first load should start");
        state.releaseFirstLoad.countDown();

        await(
                () -> state.configs.equals(List.of(FIRST, LATEST)),
                "queued refresh should be loaded exactly once after the first load");
        assertEquals(1, state.backgroundJobs.jobs().size(), "refreshes should share one job");
        assertEquals(
                BackgroundJobs.Status.SUCCEEDED,
                state.backgroundJobs.jobs().getFirst().status(),
                "shared job should complete after the latest refresh");
    }

    private static void await(
            final java.util.function.BooleanSupplier condition, final String message)
            throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    private interface TestListener extends AsyncStateListener<String> {}

    private static final class StateFixture
            extends AsyncLoadingState<String, String, String, TestListener> {
        private final AtomicBoolean loadOnEdt = new AtomicBoolean();
        private final CountDownLatch firstLoadStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstLoad = new CountDownLatch(1);
        private final List<String> configs = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile IllegalStateException failure;
        private volatile boolean blockFirstLoad;
        private final BackgroundJobs backgroundJobs;

        private StateFixture() {
            this(new BackgroundJobs());
        }

        private StateFixture(final BackgroundJobs backgroundJobs) {
            super("test", backgroundJobs);
            this.backgroundJobs = backgroundJobs;
        }

        @Override
        protected String load(
                final String instance, final String config, final BackgroundJobs.Handle jobHandle) {
            loadOnEdt.set(SwingUtilities.isEventDispatchThread());
            configs.add(config);
            if (failure != null) {
                throw failure;
            }
            if (blockFirstLoad && configs.size() == 1) {
                firstLoadStarted.countDown();
                try {
                    if (!releaseFirstLoad.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("first load timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            return config;
        }
    }
}
