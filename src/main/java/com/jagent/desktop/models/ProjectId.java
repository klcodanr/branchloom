package com.jagent.desktop.models;

import java.util.UUID;

public record ProjectId(UUID value) {
    public ProjectId {
        if (value == null) {
            throw new IllegalArgumentException("Project ID cannot be null");
        }
    }

    public static ProjectId create() {
        return new ProjectId(UUID.randomUUID());
    }
}
