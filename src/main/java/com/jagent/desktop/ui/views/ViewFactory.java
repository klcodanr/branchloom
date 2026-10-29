package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.board.BoardDefinition;

public final class ViewFactory {
    private final ActionContext actionContext;

    public ViewFactory(final ActionContext actionContext) {
        this.actionContext = actionContext;
    }

    public View resolveView() {
        final ViewId viewId = actionContext.viewController().currentViewId();
        final Project project = actionContext.viewController().currentProject();
        final Session session = actionContext.viewController().currentSession();
        final String boardName = actionContext.viewController().currentBoardName();
        final ViewId resolvedViewId = resolveView(viewId, project, session);
        return switch (resolvedViewId) {
            case HOME -> new HomeView(actionContext);
            case PULL_REQUESTS -> new MyPullRequestsView(actionContext);
            case BOARD -> new BoardView(actionContext, board(boardName));
            case PROJECT -> new ProjectView(actionContext, project);
            case SESSION -> new SessionView(actionContext);
            case PROJECT_SETTINGS -> new ProjectSettingsView(actionContext);
            case PROBLEMS -> new ProblemsView();
            case RESOURCE_USAGE -> new ResourceUsageView();
        };
    }

    private BoardDefinition board(final String name) {
        final var boards = actionContext.appState().appSettings().boards();
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
