package com.jagent.desktop.models;

public record PullRequestFilter(String name, String query) {
    public PullRequestFilter {
        name = name == null ? "" : name.trim();
        query = query == null ? "" : query.trim();
    }

    @Override
    public String toString() {
        return name;
    }
}
