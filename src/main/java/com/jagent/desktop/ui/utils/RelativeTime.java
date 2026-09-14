package com.jagent.desktop.ui.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Formats timestamps as compact relative offsets. */
public final class RelativeTime {
    private RelativeTime() {}

    public static String offsetTime(final String timestamp, final Instant now) {
        if (timestamp == null || timestamp.isBlank()) {
            return "unknown";
        }
        try {
            final long seconds =
                    Math.max(0, Duration.between(Instant.parse(timestamp), now).toSeconds());
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

    public static String localDateTime(
            final String timestamp, final ZoneId zone, final Locale locale) {
        if (timestamp == null || timestamp.isBlank()) {
            return timestamp;
        }
        try {
            return Instant.parse(timestamp)
                    .atZone(zone)
                    .format(
                            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                    .withLocale(locale));
        } catch (DateTimeParseException exception) {
            return timestamp;
        }
    }
}
