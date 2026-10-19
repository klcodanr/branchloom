package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.board.BoardDefinition;

final class TopLevelViewFactory {
    private final ActionContext actionContext;

    public TopLevelViewFactory(final ActionContext actionContext) {
        this.actionContext = actionContext;
    }

    public View create(
            final ViewId view,
            final Project project,
            final Session session,
            final String boardName) {
        final ViewId resolvedView = resolveView(view, project, session);
        if (isGlobalView(resolvedView)) {
            return createGlobalView(resolvedView, boardName);
        }
        return switch (resolvedView) {
            case PROJECT -> new ProjectView(actionContext, project);
            case SESSION -> new SessionView(actionContext);
            case PROJECT_SETTINGS -> new ProjectSettingsView(actionContext);
            case PROBLEMS -> new ProblemsView();
            case RESOURCE_USAGE -> new ResourceUsageView();
            default -> throw new IllegalStateException("Global view was not handled");
        };
    }

    public View create(final ViewId view, final Project project, final Session session) {
        return create(view, project, session, null);
    }

    private View createGlobalView(final ViewId view, final String boardName) {
        return switch (view) {
            case HOME -> new HomeView(actionContext);
            case MY_PULL_REQUESTS -> new MyPullRequestsView(actionContext);
            case BOARD -> new BoardView(actionContext, board(boardName));
            default -> throw new IllegalStateException("Unexpected non-global view");
        };
    }

    private BoardDefinition board(final String name) {
        final var boards = actionContext.appState().appSettings().boards();
        return boards.stream()
                .filter(board -> board.name().equals(name))
                .findFirst()
                .orElseGet(boards::getFirst);
    }

    private boolean isGlobalView(final ViewId view) {
        return view == ViewId.HOME || view == ViewId.MY_PULL_REQUESTS || view == ViewId.BOARD;
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
