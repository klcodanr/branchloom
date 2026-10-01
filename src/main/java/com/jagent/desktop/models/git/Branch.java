package com.jagent.desktop.models.git;

public record Branch(String name) {
    private static final String HEADS_PREFIX = "refs/heads/";
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

    public String displayName() {
        if (name.startsWith(HEADS_PREFIX)) {
            return name.substring(HEADS_PREFIX.length());
        }
        if (name.startsWith(REMOTES_PREFIX)) {
            return name.substring(REMOTES_PREFIX.length());
        }
        return name;
    }

    public String withoutHeadsPrefix() {
        if (name.startsWith(HEADS_PREFIX)) {
            return name.substring(HEADS_PREFIX.length());
        }
        return name;
    }
}
