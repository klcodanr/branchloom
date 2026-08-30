package com.jagent.desktop.models;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import java.awt.Window;

public record ViewContext(ViewCoordinator viewCoordinator, AppState appState, Window window) {}
