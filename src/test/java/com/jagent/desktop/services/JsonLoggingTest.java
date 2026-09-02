package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ProblemEvent;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonLoggingTest {
    private static final String USER_HOME = "user.home";
    private static final String SOURCE = "Test";
    private static final String SECOND_MESSAGE = "second";
    private static String originalUserHome;

    @BeforeAll
    static void isolateLogFile(@TempDir final Path temporaryDirectory) {
        originalUserHome = System.getProperty(USER_HOME);
        System.setProperty(USER_HOME, temporaryDirectory.toString());
    }

    @AfterAll
    static void restoreUserHome() {
        if (originalUserHome == null) {
            System.clearProperty(USER_HOME);
        } else {
            System.setProperty(USER_HOME, originalUserHome);
        }
    }

    @Test
    void logsAtMultipleSeverities() throws IOException {
        JsonLogging.configure();
        JsonLogging.clear();
        JsonLogging.log(new ProblemEvent(SOURCE, "warning", "first"));
        JsonLogging.log(new ProblemEvent(SOURCE, "error", SECOND_MESSAGE));

        final var events = JsonLogging.load(2);
        JsonLogging.clear();

        assertEquals(2, events.size(), "both events should be persisted");
        assertEquals("first", events.get(0).message, "warning message should be persisted");
        assertEquals("second", events.get(1).message, "error message should be persisted");
    }

    @Test
    void nonPositiveLimitReturnsNoEvents() {
        assertTrue(JsonLogging.load(0).isEmpty(), "assertion condition should hold");
        assertTrue(JsonLogging.load(-1).isEmpty(), "assertion condition should hold");
    }

    @Test
    void limitsEventsFromTheOldestEntry() throws IOException {
        JsonLogging.configure();
        JsonLogging.clear();
        JsonLogging.log(new ProblemEvent("Test", "info", "first"));
        JsonLogging.log(new ProblemEvent("Test", "info", "second"));

        final var events = JsonLogging.load(1);
        JsonLogging.clear();

        assertEquals(1, events.size(), "the limit should retain one event");
        assertEquals(
                SECOND_MESSAGE, events.getFirst().message, "the newest event should be retained");
    }
}
