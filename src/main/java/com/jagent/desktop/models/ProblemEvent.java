package com.jagent.desktop.models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ProblemEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss");

    public String created;
    public String source;
    public String severity;
    public String message;

    public ProblemEvent(final String source, final String severity, final String message) {
        this.created = LocalDateTime.now().format(TIME);
        this.source = source;
        this.severity = severity;
        this.message = message;
    }
}
