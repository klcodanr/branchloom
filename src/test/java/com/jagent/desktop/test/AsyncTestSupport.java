package com.jagent.desktop.test;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.BooleanSupplier;

/** Deterministic polling helpers for background lifecycle tests. */
public final class AsyncTestSupport {
    private AsyncTestSupport() {}

    public static void await(final BooleanSupplier condition, final String message)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        if (!condition.getAsBoolean()) {
            fail(message);
        }
    }
}
