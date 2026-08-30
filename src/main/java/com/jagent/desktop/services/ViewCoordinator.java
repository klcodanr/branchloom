package com.jagent.desktop.services;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.TerminalId;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Owns application selection, cached views, and central navigation state. */
public final class ViewCoordinator {
    private final AppState appState;
    private final Consumer<ViewId> viewChanged;
    private ViewId currentViewId;

    public record ViewState(
            ProjectId newProjectId, SessionId newSessionId, TerminalId newTerminalId) {
        public static ViewState reset() {
            return new ViewState(null, null, null);
        }

        public static ViewState project(final ProjectId projectId) {
            return new ViewState(projectId, null, null);
        }

        public static ViewState projectTerminal(
                final ProjectId projectId, final TerminalId terminalId) {
            return new ViewState(projectId, null, terminalId);
        }

        public static ViewState sessionTerminal(
                final ProjectId projectId, final SessionId sessionId, final TerminalId terminalId) {
            return new ViewState(projectId, sessionId, terminalId);
        }

        public static ViewState session(final ProjectId projectId, final SessionId sessionId) {
            return new ViewState(projectId, sessionId, null);
        }
    }

    public ViewCoordinator(final AppState appState) {
        this(appState, ignored -> {});
    }

    public ViewCoordinator(final AppState appState, final Consumer<ViewId> viewChanged) {
        this.appState = appState;
        this.viewChanged = viewChanged;
    }

    public void updateView(final ViewId newViewId, final @Nullable ViewState viewState) {
        if (viewState != null) {
            appState.updateCurrentProject(viewState.newProjectId());
            appState.updateCurrentSession(viewState.newSessionId());
            appState.updateCurrentTerminal(viewState.newTerminalId());
        }
        this.currentViewId = newViewId;
        this.viewChanged.accept(newViewId);
    }

    public ViewId currentViewId() {
        return this.currentViewId;
    }
}
