package com.jagent.desktop.services;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.jagent.desktop.models.LogEntry;
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
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class JsonLogging {
    private static final Logger LOGGER = Logger.getLogger("com.jagent.desktop");
    private static final Gson JSON = new Gson();
    private static final java.lang.reflect.Type DATA_TYPE =
            new TypeToken<Map<String, Object>>() {}.getType();
    private static final Object LOCK = new Object();
    private static FileHandler handler;

    private JsonLogging() {}

    private static Path logFile() {
        return Path.of(System.getProperty("user.home"), ".branchloom", "branchloom.log");
    }

    public static void configure() throws IOException {
        synchronized (LOCK) {
            if (handler != null) {
                return;
            }
            final Path logFile = logFile();
            final Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            handler = new FileHandler(logFile.toString(), true);
            handler.setFormatter(new JsonFormatter());
            LOGGER.addHandler(handler);
            LOGGER.setUseParentHandlers(false);
            LOGGER.setLevel(Level.ALL);
        }
    }

    public static void log(final LogEntry event) {
        final Level level = levelFor(event.level());
        synchronized (LOCK) {
            final LogRecord record = new LogRecord(level, event.message());
            record.setLoggerName(LOGGER.getName());
            record.setSourceClassName(event.source() == null ? "Application" : event.source());
            record.setSourceMethodName("report");
            record.setParameters(new Object[] {event.data()});
            LOGGER.log(record);
            if (handler != null) {
                handler.flush();
            }
        }
    }

    private static Level levelFor(final String severity) {
        return switch (severity == null ? "" : severity.toLowerCase(Locale.ROOT)) {
            case "warning", "warn" -> Level.WARNING;
            case "error", "severe" -> Level.SEVERE;
            default -> Level.INFO;
        };
    }

    public static List<LogEntry> load() {
        return load(Integer.MAX_VALUE);
    }

    public static List<LogEntry> load(final int maxEvents) {
        final Deque<LogEntry> events = new ArrayDeque<>();
        if (maxEvents <= 0) {
            return List.of();
        }
        synchronized (LOCK) {
            if (handler != null) {
                handler.flush();
            }
        }
        final Path logFile = logFile();
        if (!Files.exists(logFile)) {
            return List.of();
        }
        try {
            try (var lines = Files.lines(logFile)) {
                for (final var iterator = lines.iterator(); iterator.hasNext(); ) {
                    final String line = iterator.next();
                    final var entry = JsonParser.parseString(line).getAsJsonObject();
                    final LogEntry event =
                            new LogEntry(
                                    entry.get("timestamp").getAsString(),
                                    entry.get("level").getAsString(),
                                    entry.get("source").getAsString(),
                                    entry.get("message").getAsString(),
                                    entry.has("data")
                                            ? JSON.fromJson(entry.get("data"), DATA_TYPE)
                                            : Map.of(),
                                    entry.has("exception")
                                            ? entry.get("exception").getAsString()
                                            : null);
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
                Files.deleteIfExists(logFile());
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
            if (record.getParameters() != null
                    && record.getParameters().length > 0
                    && record.getParameters()[0] instanceof Map<?, ?> data
                    && !data.isEmpty()) {
                entry.put("data", data);
            }
            if (record.getThrown() != null) {
                final StringWriter stackTrace = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(stackTrace));
                entry.put("exception", stackTrace.toString());
            }
            return JSON.toJson(entry) + System.lineSeparator();
        }
    }
}
