package com.jagent.desktop.models.board.sources;

public record PullRequestConfig(boolean enabled, String query) implements SourceConfig {}
