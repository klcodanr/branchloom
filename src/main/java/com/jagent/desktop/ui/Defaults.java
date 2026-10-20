package com.jagent.desktop.ui;

import com.google.gson.Gson;
import com.jagent.desktop.models.AppSettings;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class Defaults {
    public static final String DEFAULT_GROUP = "Default";

    private Defaults() {}

    public static AppSettings appSettings() {
        try (Reader reader =
                new InputStreamReader(
                        Defaults.class.getResourceAsStream("/default-settings.json"),
                        StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, AppSettings.class);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load default settings", exception);
        }
    }
}
