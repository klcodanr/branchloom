package com.jagent.desktop.models.github;

import org.jetbrains.annotations.NotNull;

public record CliCredential(@NotNull String host, @NotNull String name, @NotNull String user)
        implements Credential {
    @Override
    public String id() {
        return name;
    }
}
