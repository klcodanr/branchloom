package com.jagent.desktop.ui.utils;

public final class ErrorMessages {
    private ErrorMessages() {}

    public static String deepestCause(final Throwable failure, final String fallback) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? fallback
                : cause.getMessage();
    }
}
