package com.jagent.desktop.models.github;

import org.jetbrains.annotations.NotNull;

public record PatCredential(@NotNull String credentialId, String host, @NotNull String name)
        implements Credential {
    @Override
    public String id() {
        return credentialId;
    }
}
