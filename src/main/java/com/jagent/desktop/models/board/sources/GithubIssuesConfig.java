package com.jagent.desktop.models.board.sources;

public record GithubIssuesConfig(boolean enabled, String query) implements SourceConfig {}
