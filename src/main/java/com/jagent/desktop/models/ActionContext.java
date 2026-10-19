package com.jagent.desktop.models;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.services.persistence.AppStatePersistence;
import java.awt.Window;
import java.nio.file.Path;

public record ActionContext(
        ViewCoordinator viewCoordinator,
        AppState appState,
        Window window,
        Path dataDirectory,
        AppStatePersistence persistence) {}
