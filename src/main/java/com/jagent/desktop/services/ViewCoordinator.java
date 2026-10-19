package com.jagent.desktop.services;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.AsyncLoadingState;
import com.jagent.desktop.async.BoardInstanceState;
import com.jagent.desktop.async.SessionInstanceState;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.TerminalId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Owns application selection, cached views, and central navigation state. */
public final class ViewCoordinator {
    private final AppState appState;
    private final Map<Object, AsyncLoadingState<?, ?, ?, ?>> asyncLoadingStates = new HashMap<>();
    private final BackgroundJobs backgroundJobs = new BackgroundJobs();
    private final Consumer<ViewId> viewChanged;
    private final Map<ViewId, Integer> selectedTabs = new EnumMap<>(ViewId.class);
    private final Map<SessionId, Integer> selectedSessionTabs = new HashMap<>();
    private ViewId currentViewId;
    private String currentBoardName;

    public record ViewState(
            ProjectId newProjectId,
            SessionId newSessionId,
            TerminalId newTerminalId,
            String boardName) {
        public ViewState(
                final ProjectId newProjectId,
                final SessionId newSessionId,
                final TerminalId newTerminalId) {
            this(newProjectId, newSessionId, newTerminalId, null);
        }

        public static ViewState reset() {
            return new ViewState(null, null, null);
        }

        public static ViewState board(final String boardName) {
            return new ViewState(null, null, null, boardName);
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
        this.asyncLoadingStates.put(
                ViewId.BOARD, new BoardInstanceState(appState, this.backgroundJobs()));
        this.asyncLoadingStates.put(
                ViewId.SESSION, new SessionInstanceState(appState, this.backgroundJobs()));
    }

    public void updateView(final ViewId newViewId, final @Nullable ViewState viewState) {
        if (viewState != null) {
            appState.updateCurrentProject(viewState.newProjectId());
            appState.updateCurrentSession(viewState.newSessionId());
            appState.updateCurrentTerminal(viewState.newTerminalId());
            if (viewState.boardName() != null) {
                currentBoardName = viewState.boardName();
            }
        }
        this.currentViewId = newViewId;
        this.viewChanged.accept(newViewId);
    }

    public ViewId currentViewId() {
        return this.currentViewId;
    }

    public String currentBoardName() {
        return currentBoardName;
    }

    public BackgroundJobs backgroundJobs() {
        return backgroundJobs;
    }

    public <V extends AsyncLoadingState<?, ?, ?, ?>> V getAsyncLoadingState(final Object key) {
        return (V) asyncLoadingStates.get(key);
    }

    public <V extends AsyncLoadingState<?, ?, ?, ?>> V getOrDefaultAsyncLoadingState(
            final Object key, final V defaultState) {
        if (!asyncLoadingStates.containsKey(key)) {
            asyncLoadingStates.put(key, defaultState);
        }
        return (V) asyncLoadingStates.get(key);
    }

    public int selectedTab(final ViewId viewId) {
        return selectedTabs.getOrDefault(viewId, 0);
    }

    public boolean hasSelectedTab(final ViewId viewId) {
        return selectedTabs.containsKey(viewId);
    }

    public void updateSelectedTab(final ViewId viewId, final int tabIndex) {
        if (tabIndex >= 0) {
            selectedTabs.put(viewId, tabIndex);
        }
    }

    public int selectedSessionTab(final SessionId sessionId) {
        cleanupRemovedSessionTabs();
        if (sessionId == null) {
            return 0;
        }
        return selectedSessionTabs.getOrDefault(sessionId, 0);
    }

    public boolean hasSelectedSessionTab(final SessionId sessionId) {
        cleanupRemovedSessionTabs();
        return sessionId != null && selectedSessionTabs.containsKey(sessionId);
    }

    public void updateSelectedSessionTab(final SessionId sessionId, final int tabIndex) {
        cleanupRemovedSessionTabs();
        if (sessionId != null && tabIndex >= 0) {
            selectedSessionTabs.put(sessionId, tabIndex);
        }
    }

    private void cleanupRemovedSessionTabs() {
        selectedSessionTabs
                .keySet()
                .removeIf(sessionId -> !appState.sessions().containsKey(sessionId));
    }
}
