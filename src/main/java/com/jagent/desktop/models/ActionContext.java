package com.jagent.desktop.models;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.services.github.GitHubProvider;
import com.jagent.desktop.services.persistence.AppStatePersistence;
import java.awt.Window;
import java.nio.file.Path;

public record ActionContext(
        ViewCoordinator viewCoordinator,
        AppState appState,
        Window window,
        Path dataDirectory,
        AppStatePersistence persistence,
        GitHubProvider githubProvider) {
    public ActionContext(
            final ViewCoordinator viewCoordinator,
            final AppState appState,
            final Window window,
            final Path dataDirectory,
            final AppStatePersistence persistence) {
        this(
                viewCoordinator,
                appState,
                window,
                dataDirectory,
                persistence,
                projectId -> GitHub.forProject(appState, projectId));
    }
}
