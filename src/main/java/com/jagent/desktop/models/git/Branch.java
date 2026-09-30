package com.jagent.desktop.models.git;

public record Branch(String name) {
    private static final String REMOTES_PREFIX = "refs/remotes/";

    public boolean remote() {
        return name.startsWith(REMOTES_PREFIX);
    }

    public String localName() {
        if (!remote()) {
            return name;
        }
        return name.substring(REMOTES_PREFIX.length());
    }
}
