package com.jagent.desktop.models;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import java.awt.Window;
import java.nio.file.Path;

public record ActionContext(
        ViewController viewController,
        AppState appState,
        Window window,
        Path dataDirectory,
        Runnable refreshSettings) {}
