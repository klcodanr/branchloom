package com.jagent.desktop.ui.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;

/** Formats timestamps as compact relative offsets. */
public final class RelativeTime {
    private RelativeTime() {}

    public static String offsetTime(final Date timestamp) {
        if (timestamp == null) {
            return "unknown";
        }
        try {
            final long seconds =
                    Math.max(0, Duration.between(timestamp.toInstant(), Instant.now()).toSeconds());
            if (seconds < 60) {
                return "now";
            }
            final long minutes = seconds / 60;
            if (minutes < 60) {
                return minutes + " min";
            }
            final long hours = minutes / 60;
            if (hours < 24) {
                return hours + " hr";
            }
            final long days = hours / 24;
            return days + " day" + (days == 1 ? "" : "s");
        } catch (DateTimeParseException exception) {
            return "unknown";
        }
    }

    public static String localDateTime(final Date timestamp) {
        if (timestamp == null) {
            return null;
        }
        try {
            return timestamp
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(
                            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                    .withLocale(Locale.getDefault()));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
