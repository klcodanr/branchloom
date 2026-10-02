package com.jagent.desktop.ui.components;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Formatting helpers for values displayed by Swing components. */
public final class UiText {
    private UiText() {}

    public static String escapeHtml(final String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String colorHex(final Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static String valueOrDefault(
            @Nullable final String value, @NotNull final String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static String titleCase(final String value) {
        final String[] words = value.toLowerCase(Locale.ROOT).split("_");
        final StringBuilder result = new StringBuilder();
        for (final String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public static String relativeTime(final Instant timestamp) {
        if (timestamp == null) {
            return "Unknown";
        }
        final long seconds = Math.max(0, Duration.between(timestamp, Instant.now()).toSeconds());
        if (seconds < 60) {
            return "Just now";
        }
        final long minutes = seconds / 60;
        if (minutes < 60) {
            return elapsed(minutes, "minute");
        }
        final long hours = minutes / 60;
        if (hours < 24) {
            return elapsed(hours, "hour");
        }
        return elapsed(hours / 24, "day");
    }

    public static String relativeTime(final Date timestamp) {
        return timestamp == null ? "Unknown" : relativeTime(timestamp.toInstant());
    }

    public static String localDateTime(final Date timestamp) {
        return timestamp == null
                ? null
                : timestamp
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
    }

    private static String elapsed(final long amount, final String unit) {
        return amount + " " + unit + (amount == 1 ? "" : "s") + " ago";
    }
}
