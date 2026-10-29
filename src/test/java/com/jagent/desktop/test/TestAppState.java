package com.jagent.desktop.test;

import com.google.inject.Guice;
import com.jagent.desktop.async.AsyncLoadingStateFactory;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ApplicationModule;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.BulkSessionCreator;
import com.jagent.desktop.services.PullRequestWatcher;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.dialog.HeadlessDialogService;
import com.jagent.desktop.services.github.GitHubProvider;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.components.ProjectActions;
import com.jagent.desktop.ui.components.SessionActions;
import java.util.Map;

/** Common application-state fixtures for action and view tests. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class TestAppState {
    private TestAppState() {}

    public static AppState empty() {
        return new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
    }

    public static GitHubProvider githubProvider(final AppState state) {
        return projectId -> com.jagent.desktop.services.github.GitHub.forProject(state, projectId);
    }

    public static DialogService dialogService() {
        return new HeadlessDialogService();
    }

    public static ViewController controller(final AppState state) {
        return Guice.createInjector(new ApplicationModule(null, state, null, () -> {}))
                .getInstance(ViewController.class);
    }

    public static BackgroundJobs backgroundJobs(final AppState state) {
        return Guice.createInjector(new ApplicationModule(null, state, null, () -> {}))
                .getInstance(BackgroundJobs.class);
    }

    public static AsyncLoadingStateFactory asyncLoadingStateFactory(final AppState state) {
        return Guice.createInjector(new ApplicationModule(null, state, null, () -> {}))
                .getInstance(AsyncLoadingStateFactory.class);
    }

    public static BulkSessionCreator bulkSessionCreator(final AppState state) {
        return Guice.createInjector(new ApplicationModule(null, state, null, () -> {}))
                .getInstance(BulkSessionCreator.class);
    }

    public static SessionActions sessionActions(final AppState state) {
        return Guice.createInjector(new ApplicationModule(null, state, null, () -> {}))
                .getInstance(SessionActions.class);
    }

    public static ProjectActions projectActions(final AppState state) {
        return Guice.createInjector(new ApplicationModule(null, state, null, () -> {}))
                .getInstance(ProjectActions.class);
    }

    public static PullRequestWatcher pullRequestWatcher(final AppState state) {
        return new PullRequestWatcher(state, githubProvider(state));
    }
}
