package com.jagent.desktop.models;

import java.util.UUID;

public record TerminalId(UUID value) {
    public TerminalId {
        if (value == null) {
            throw new IllegalArgumentException("Terminal ID cannot be null");
        }
    }

    public static TerminalId create() {
        return new TerminalId(UUID.randomUUID());
    }
}
