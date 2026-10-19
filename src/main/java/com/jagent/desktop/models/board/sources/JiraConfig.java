package com.jagent.desktop.models.board.sources;

public record JiraConfig(boolean enabled, String query) implements SourceConfig {
    public JiraConfig {
        query = query == null ? "" : query;
    }
}
