package com.jagent.desktop.services;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.jagent.desktop.models.ProblemEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class JsonLogging {
    private static final Path LOG_FILE =
            Path.of(System.getProperty("user.home"), ".branchloom", "branchloom.log");
    private static final Logger LOGGER = Logger.getLogger("com.jagent.desktop");
    private static final Gson JSON = new Gson();
    private static final Object LOCK = new Object();
    private static FileHandler handler;

    private JsonLogging() {}

    public static void configure() throws IOException {
        synchronized (LOCK) {
            if (handler != null) {
                return;
            }
            final Path parent = LOG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            handler = new FileHandler(LOG_FILE.toString(), true);
            handler.setFormatter(new JsonFormatter());
            LOGGER.addHandler(handler);
            LOGGER.setUseParentHandlers(false);
            LOGGER.setLevel(Level.ALL);
        }
    }

    public static void log(final ProblemEvent event) {
        final Level level =
                switch (event.severity == null ? "" : event.severity.toLowerCase(Locale.ROOT)) {
                    case "warning", "warn" -> Level.WARNING;
                    case "error", "severe" -> Level.SEVERE;
                    default -> Level.INFO;
                };
        LOGGER.logp(
                level,
                event.source == null ? "Application" : event.source,
                "report",
                event.message);
    }

    public static List<ProblemEvent> load() {
        return load(Integer.MAX_VALUE);
    }

    public static List<ProblemEvent> load(final int maxEvents) {
        final Deque<ProblemEvent> events = new ArrayDeque<>();
        if (maxEvents <= 0) {
            return List.of();
        }
        if (!Files.exists(LOG_FILE)) {
            return List.of();
        }
        try {
            try (var lines = Files.lines(LOG_FILE)) {
                for (final var iterator = lines.iterator(); iterator.hasNext(); ) {
                    final String line = iterator.next();
                    final var entry = JsonParser.parseString(line).getAsJsonObject();
                    final ProblemEvent event =
                            new ProblemEvent(
                                    entry.get("source").getAsString(),
                                    entry.get("level").getAsString(),
                                    entry.get("message").getAsString());
                    event.created = entry.get("timestamp").getAsString();
                    events.addLast(event);
                    if (events.size() > maxEvents) {
                        events.removeFirst();
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // A malformed or unavailable log should not prevent the application from opening.
        }
        return List.copyOf(events);
    }

    public static void clear() {
        synchronized (LOCK) {
            if (handler == null) {
                return;
            }
            handler.flush();
            handler.close();
            try {
                Files.deleteIfExists(LOG_FILE);
                handler = null;
                configure();
            } catch (IOException ignored) {
                // Logging cleanup should not prevent the application from continuing.
            }
        }
    }

    private static final class JsonFormatter extends Formatter {
        @Override
        public String format(final LogRecord record) {
            final var entry = new LinkedHashMap<String, Object>();
            entry.put("timestamp", Instant.ofEpochMilli(record.getMillis()).toString());
            entry.put("level", record.getLevel().getName());
            entry.put("source", record.getLoggerName());
            entry.put("message", formatMessage(record));
            if (record.getThrown() != null) {
                final StringWriter stackTrace = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(stackTrace));
                entry.put("exception", stackTrace.toString());
            }
            return JSON.toJson(entry) + System.lineSeparator();
        }
    }
}
