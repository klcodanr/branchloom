package com.jagent.desktop.services;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import com.jagent.desktop.services.dialog.DialogModule;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.services.github.GitHubProvider;
import com.jagent.desktop.ui.views.BoardView;
import com.jagent.desktop.ui.views.ProjectView;
import com.jagent.desktop.ui.views.SessionView;
import java.awt.Window;
import java.nio.file.Path;

/** Guice bindings for the application runtime graph. */
public final class ApplicationModule extends AbstractModule {
    private final Path dataDirectory;
    private final AppState appState;
    private final Window window;
    private final Runnable refreshSettings;

    public ApplicationModule(
            final Path dataDirectory,
            final AppState appState,
            final Window window,
            final Runnable refreshSettings) {
        super();
        this.dataDirectory = dataDirectory;
        this.appState = appState;
        this.window = window;
        this.refreshSettings = refreshSettings;
    }

    @Override
    protected void configure() {
        install(new DialogModule(this.window));
        install(new FactoryModuleBuilder().build(BoardView.Factory.class));
        install(new FactoryModuleBuilder().build(ProjectView.Factory.class));
        install(new FactoryModuleBuilder().build(SessionView.Factory.class));
        bind(AppState.class).toInstance(appState);
        bind(Window.class).toProvider(Providers.of(window));
        bind(Path.class)
                .annotatedWith(Names.named("dataDirectory"))
                .toProvider(Providers.of(dataDirectory));
        bind(Runnable.class)
                .annotatedWith(Names.named("refreshSettings"))
                .toInstance(refreshSettings);
    }

    @Provides
    @Singleton
    public GitHubProvider githubProvider(final AppState state) {
        return projectId -> GitHub.forProject(state, projectId);
    }
}
