package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;

final class TopLevelViewFactory {
    private final ActionContext actionContext;

    public TopLevelViewFactory(final ActionContext actionContext) {
        this.actionContext = actionContext;
    }

    public View create(final ViewId view, final Project project, final Session session) {
        final ViewId resolvedView = resolveView(view, project, session);
        if (isGlobalView(resolvedView)) {
            return createGlobalView(resolvedView);
        }
        return switch (resolvedView) {
            case PROJECT -> new ProjectView(actionContext, project);
            case SESSION -> new SessionView(actionContext);
            case SETTINGS -> new GlobalSettingsView(actionContext);
            case PROJECT_SETTINGS -> new ProjectSettingsView(actionContext);
            case PROBLEMS -> new ProblemsView();
            case RESOURCE_USAGE -> new ResourceUsageView();
            default -> throw new IllegalStateException("Global view was not handled");
        };
    }

    private View createGlobalView(final ViewId view) {
        return switch (view) {
            case HOME -> new HomeView(actionContext);
            case MY_PULL_REQUESTS -> new MyPullRequestsView(actionContext);
            case REVIEW_QUEUE -> new ReviewQueueView(actionContext);
            default -> throw new IllegalStateException("Unexpected non-global view");
        };
    }

    private boolean isGlobalView(final ViewId view) {
        return view == ViewId.HOME
                || view == ViewId.MY_PULL_REQUESTS
                || view == ViewId.REVIEW_QUEUE;
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
