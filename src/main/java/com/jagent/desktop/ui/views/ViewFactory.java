package com.jagent.desktop.ui.views;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.board.BoardDefinition;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;

public final class ViewFactory {
    private final ViewController viewController;
    private final AppState appState;
    private final Provider<HomeView> homeView;
    private final Provider<MyPullRequestsView> pullRequestsView;
    private final Provider<ProjectSettingsView> projectSettingsView;
    private final Provider<ProblemsView> problemsView;
    private final Provider<ResourceUsageView> resourceUsageView;
    private final BoardView.Factory boardView;
    private final ProjectView.Factory projectView;
    private final SessionView.Factory sessionView;

    @Inject
    public ViewFactory(
            final ViewController viewController,
            final AppState appState,
            final Provider<HomeView> homeView,
            final Provider<MyPullRequestsView> pullRequestsView,
            final Provider<ProjectSettingsView> projectSettingsView,
            final Provider<ProblemsView> problemsView,
            final Provider<ResourceUsageView> resourceUsageView,
            final BoardView.Factory boardView,
            final ProjectView.Factory projectView,
            final SessionView.Factory sessionView) {
        this.viewController = viewController;
        this.appState = appState;
        this.homeView = homeView;
        this.pullRequestsView = pullRequestsView;
        this.projectSettingsView = projectSettingsView;
        this.problemsView = problemsView;
        this.resourceUsageView = resourceUsageView;
        this.boardView = boardView;
        this.projectView = projectView;
        this.sessionView = sessionView;
    }

    public View resolveView() {
        final ViewId viewId = viewController.currentViewId();
        final Project project = viewController.currentProject();
        final Session session = viewController.currentSession();
        final String boardName = viewController.currentBoardName();
        final ViewId resolvedViewId = resolveView(viewId, project, session);
        return switch (resolvedViewId) {
            case HOME -> homeView.get();
            case PULL_REQUESTS -> pullRequestsView.get();
            case BOARD -> boardView.create(board(boardName));
            case PROJECT -> projectView.create(project);
            case SESSION -> sessionView.create(session);
            case PROJECT_SETTINGS -> projectSettingsView.get();
            case PROBLEMS -> problemsView.get();
            case RESOURCE_USAGE -> resourceUsageView.get();
        };
    }

    private BoardDefinition board(final String name) {
        final var boards = appState.appSettings().boards();
        return boards.stream()
                .filter(board -> board.name().equals(name))
                .findFirst()
                .orElseGet(boards::getFirst);
    }

    private ViewId resolveView(final ViewId view, final Project project, final Session session) {
        final ViewId requested = view == null ? ViewId.HOME : view;
        if (requiresProject(requested) && project == null) {
            return ViewId.HOME;
        }
        if (requested == ViewId.SESSION && session == null) {
            return ViewId.HOME;
        }
        return requested;
    }

    private boolean requiresProject(final ViewId view) {
        return view == ViewId.PROJECT || view == ViewId.PROJECT_SETTINGS || view == ViewId.SESSION;
    }
}
