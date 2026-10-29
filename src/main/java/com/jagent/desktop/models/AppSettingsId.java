package com.jagent.desktop.models;

import java.util.UUID;

public record AppSettingsId(UUID id) implements IdType {

    public static final AppSettingsId CURRENT = new AppSettingsId(UUID.randomUUID());

    @Override
    public UUID value() {
        return id;
    }
}
