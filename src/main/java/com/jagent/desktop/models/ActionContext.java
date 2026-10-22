package com.jagent.desktop.models;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.services.github.GitHubProvider;
import java.awt.Window;
import java.nio.file.Path;

public record ActionContext(
        ViewController viewController,
        AppState appState,
        Window window,
        Path dataDirectory,
        Runnable refreshSettings,
        GitHubProvider githubProvider) {
    public ActionContext(
            final ViewController viewController,
            final AppState appState,
            final Window window,
            final Path dataDirectory,
            final Runnable refreshSettings) {
        this(
                viewController,
                appState,
                window,
                dataDirectory,
                refreshSettings,
                projectId -> GitHub.forProject(appState, projectId));
    }
}
