package com.jagent.desktop.models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public record LogEntry(
        String timestamp,
        String level,
        String source,
        String message,
        Map<String, Object> data,
        String exception)
        implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss");

    public LogEntry() {
        this(null, null, null, null, Map.of(), null);
    }

    public LogEntry(final String source, final String level, final String message) {
        this(source, level, message, Map.of());
    }

    public LogEntry(
            final String source,
            final String level,
            final String message,
            final Map<String, ?> data) {
        this(LocalDateTime.now().format(TIME), level, source, message, copyData(data), null);
    }

    public LogEntry {
        data = copyData(data);
    }

    private static Map<String, Object> copyData(final Map<String, ?> data) {
        return new LinkedHashMap<>(data == null ? Map.of() : data);
    }
}
