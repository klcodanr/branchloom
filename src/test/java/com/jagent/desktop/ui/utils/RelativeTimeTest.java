package com.jagent.desktop.ui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RelativeTimeTest {
    @Test
    void formatsTimestampOffsets() {
        final Instant now = Instant.parse("2026-09-14T12:00:00Z");

        assertEquals(
                "now",
                RelativeTime.offsetTime("2026-09-14T11:59:30Z", now),
                "timestamps less than a minute old should be shown as now");
        assertEquals(
                "2 min",
                RelativeTime.offsetTime("2026-09-14T11:58:00Z", now),
                "timestamps under an hour should use minutes");
        assertEquals(
                "1 hr",
                RelativeTime.offsetTime("2026-09-14T11:00:00Z", now),
                "timestamps under a day should use hours");
        assertEquals(
                "2 days",
                RelativeTime.offsetTime("2026-09-12T12:00:00Z", now),
                "older timestamps should use days");
    }

    @Test
    void handlesInvalidAndMissingTimestamps() {
        final Instant now = Instant.parse("2026-09-14T12:00:00Z");

        assertEquals(
                "unknown",
                RelativeTime.offsetTime(null, now),
                "missing timestamps should be handled safely");
        assertEquals(
                "unknown",
                RelativeTime.offsetTime("not-a-timestamp", now),
                "invalid timestamps should be handled safely");
    }

    @Test
    void formatsTooltipTimestampInLocalZoneAndLocale() {
        final String localDateTime =
                RelativeTime.localDateTime(
                        "2026-09-14T12:00:00Z", ZoneId.of("America/Los_Angeles"), Locale.US);

        assertTrue(
                localDateTime.startsWith("Sep 14, 2026, 5:00"),
                "tooltip timestamp should use the user's local date and time");
    }

    @Test
    void preservesMissingOrInvalidTooltipTimestamp() {
        assertEquals(
                null,
                RelativeTime.localDateTime(null, ZoneId.of("UTC"), Locale.US),
                "missing tooltip timestamp should remain missing");
        assertEquals(
                "not-a-timestamp",
                RelativeTime.localDateTime("not-a-timestamp", ZoneId.of("UTC"), Locale.US),
                "invalid tooltip timestamp should remain available for diagnosis");
    }
}
