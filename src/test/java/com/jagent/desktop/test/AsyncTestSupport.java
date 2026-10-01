package com.jagent.desktop.test;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.BooleanSupplier;

/** Deterministic polling helpers for background lifecycle tests. */
public final class AsyncTestSupport {
    private static final long DEFAULT_TIMEOUT_NANOS = 5_000_000_000L;

    private AsyncTestSupport() {}

    public static void await(final BooleanSupplier condition, final String message)
            throws InterruptedException {
        await(condition, message, DEFAULT_TIMEOUT_NANOS);
    }

    public static void await(
            final BooleanSupplier condition, final String message, final long timeoutNanos)
            throws InterruptedException {
        final long deadline = System.nanoTime() + timeoutNanos;
        boolean satisfied = condition.getAsBoolean();
        while (!satisfied && System.nanoTime() < deadline) {
            Thread.sleep(25);
            satisfied = condition.getAsBoolean();
        }
        if (!satisfied) {
            fail(message);
        }
    }
}
