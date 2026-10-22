package com.jagent.desktop.models;

import java.util.UUID;

public record SessionId(UUID value) implements IdType {
    public SessionId {
        if (value == null) {
            throw new IllegalArgumentException("Session ID cannot be null");
        }
    }

    public static SessionId create() {
        return new SessionId(UUID.randomUUID());
    }
}
