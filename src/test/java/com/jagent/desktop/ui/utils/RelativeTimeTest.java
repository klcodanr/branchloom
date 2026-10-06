package com.jagent.desktop.ui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.ui.components.UiText;
import java.util.Date;
import org.junit.jupiter.api.Test;

class RelativeTimeTest {
    @Test
    void formatsTimestampOffsets() {
        final long now = System.currentTimeMillis();
        assertEquals(
                "Just now",
                UiText.relativeTime(new Date(now - 30_000L)),
                "sub-minute offsets should render as now");
        assertEquals(
                "2 minutes ago",
                UiText.relativeTime(new Date(now - 2 * 60_000L)),
                "minute offsets should render in minutes");
        assertEquals(
                "1 hour ago",
                UiText.relativeTime(new Date(now - 60 * 60_000L)),
                "hour offsets should render in hours");
        assertEquals(
                "2 days ago",
                UiText.relativeTime(new Date(now - 2 * 24 * 60 * 60_000L)),
                "day offsets should render in days");
    }

    @Test
    void handlesMissingTimestamps() {
        assertEquals(
                "Unknown", UiText.relativeTime((Date) null), "missing dates should be unknown");
        assertNull(
                UiText.localDateTime(null), "missing dates should not produce a local timestamp");
    }

    @Test
    void formatsTooltipTimestampInLocalZoneAndLocale() {
        final String localDateTime = UiText.localDateTime(new Date(1_726_314_400_000L));
        assertNotNull(localDateTime, "known dates should produce localized timestamp text");
        assertTrue(localDateTime.length() > 5, "localized timestamp should be non-trivial");
    }
}
