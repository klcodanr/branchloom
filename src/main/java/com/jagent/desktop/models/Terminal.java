package com.jagent.desktop.models;

public record Terminal(SessionId sessionId, ProjectId projectId, String title, String command) {
    public Terminal(final SessionId sessionId, final String title, final String command) {
        this(sessionId, null, title, command);
    }

    public Terminal withTitle(final String updatedTitle) {
        return new Terminal(sessionId, projectId, updatedTitle, command);
    }
}
