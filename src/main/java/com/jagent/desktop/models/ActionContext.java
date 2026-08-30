package com.jagent.desktop.models;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import java.awt.Window;

public record ActionContext(ViewCoordinator viewCoordinator, AppState appState, Window window) {}
