package com.jagent.desktop.services;

import com.jagent.desktop.api.RefreshableView;
import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.AsyncLoadingState;
import com.jagent.desktop.async.BoardInstanceState;
import com.jagent.desktop.async.SessionInstanceState;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.ui.views.ViewFactory;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Owns application selection, cached views, and central navigation state. */
public final class ViewController {
    private final AppState appState;
    private final Map<Object, AsyncLoadingState<?, ?, ?, ?>> asyncLoadingStates = new HashMap<>();
    private final BackgroundJobs backgroundJobs = new BackgroundJobs();
    private final PullRequestWatcher pullRequestWatcher;
    private final List<Consumer<ViewId>> viewChanged = new ArrayList<>();
    private final ViewFactory viewFactory;
    private final Map<ViewId, Integer> selectedTabs = new EnumMap<>(ViewId.class);
    private final Map<SessionId, Integer> selectedSessionTabs = new HashMap<>();
    private ViewId currentViewId;
    private String currentBoardName;
    private ProjectId currentProject;
    private SessionId currentSession;
    private TerminalId currentTerminal;
    private View currentView;

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

    public ViewController(
            final AppState appState,
            final Window window,
            final Path dataDirectory,
            final Runnable refreshSettings) {
        this.appState = appState;
        this.asyncLoadingStates.put(
                ViewId.BOARD, new BoardInstanceState(appState, this.backgroundJobs()));
        this.asyncLoadingStates.put(
                ViewId.SESSION, new SessionInstanceState(appState, this.backgroundJobs()));
        appState.addEventListener(this::handleStateEvent);

        viewFactory =
                new ViewFactory(
                        new ActionContext(
                                this,
                                appState,
                                window,
                                dataDirectory,
                                () -> {
                                    refreshSettings.run();
                                    this.updateView(currentViewId, null);
                                }));
        pullRequestWatcher =
                new PullRequestWatcher(
                        new ActionContext(this, appState, window, dataDirectory, refreshSettings));
    }

    public void updateView(final ViewId newViewId, final @Nullable ViewState viewState) {
        if (viewState != null) {
            currentProject = viewState.newProjectId();
            currentSession = viewState.newSessionId();
            currentTerminal = viewState.newTerminalId();
            if (viewState.boardName() != null) {
                currentBoardName = viewState.boardName();
            }
        }
        this.currentViewId = newViewId;
        final View previousView = currentView;
        currentView = null;
        if (previousView != null) {
            previousView.detach();
        }
        TerminalManager.get().reconcile(appState.terminals().keySet());
        for (final Consumer<ViewId> listener : List.copyOf(viewChanged)) {
            listener.accept(newViewId);
        }
    }

    public void onViewChanged(final Consumer<ViewId> viewChanged) {
        this.viewChanged.add(viewChanged);
    }

    public ViewId currentViewId() {
        return this.currentViewId;
    }

    public ProjectId currentProjectId() {
        return currentProject;
    }

    public SessionId currentSessionId() {
        return currentSession;
    }

    public TerminalId currentTerminalId() {
        return currentTerminal;
    }

    public void selectProject(final ProjectId projectId) {
        currentProject = projectId;
    }

    public void selectSession(final ProjectId projectId, final SessionId sessionId) {
        currentProject = projectId;
        currentSession = sessionId;
    }

    public void selectTerminal(final TerminalId terminalId) {
        currentTerminal = terminalId;
    }

    public void openProject(final ProjectId projectId) {
        final boolean leavingSession = currentViewId == ViewId.SESSION;
        final TerminalId terminal =
                !leavingSession && projectId.equals(currentProject) ? currentTerminal : null;
        updateView(ViewId.PROJECT, new ViewState(projectId, null, terminal, currentBoardName));
    }

    public void openSession(final ProjectId projectId, final SessionId sessionId) {
        updateView(ViewId.SESSION, ViewState.session(projectId, sessionId));
    }

    private void handleStateEvent(final AppState.StateEvent event) {
        if (event.action() != AppState.Action.REMOVE) {
            return;
        }
        if (event.target() instanceof SessionId sessionId) {
            selectedSessionTabs.remove(sessionId);
            if (sessionId.equals(currentSession)) {
                final ProjectId projectId = currentProject;
                currentSession = null;
                if (projectId != null) {
                    updateView(ViewId.PROJECT, ViewState.project(projectId));
                }
            }
        } else if (event.target() instanceof TerminalId terminalId
                && terminalId.equals(currentTerminal)) {
            selectTerminal(null);
        } else if (event.target() instanceof ProjectId projectId
                && projectId.equals(currentProject)) {
            updateView(ViewId.HOME, ViewState.reset());
        }
    }

    public AppState appState() {
        return appState;
    }

    public @NotNull View currentView() {
        if (currentView == null) {
            currentView = viewFactory.resolveView();
        }
        return currentView;
    }

    public CompletableFuture<?> refreshCurrentView() {
        if (!(currentView() instanceof RefreshableView refreshableView)) {
            return CompletableFuture.completedFuture(null);
        }
        return refreshableView.refresh();
    }

    public @Nullable Project currentProject() {
        return currentProject == null ? null : appState.projects().get(currentProject);
    }

    public @Nullable Session currentSession() {
        return currentSession == null ? null : appState.sessions().get(currentSession);
    }

    public @Nullable String currentBoardName() {
        return currentBoardName;
    }

    public BackgroundJobs backgroundJobs() {
        return backgroundJobs;
    }

    public PullRequestWatcher pullRequestWatcher() {
        return pullRequestWatcher;
    }

    @SuppressWarnings("unchecked")
    public <V extends AsyncLoadingState<?, ?, ?, ?>> V getAsyncLoadingState(final Object key) {
        return (V) asyncLoadingStates.get(key);
    }

    @SuppressWarnings("unchecked")
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
        if (sessionId == null) {
            return 0;
        }
        return selectedSessionTabs.getOrDefault(sessionId, 0);
    }

    public boolean hasSelectedSessionTab(final SessionId sessionId) {
        return sessionId != null && selectedSessionTabs.containsKey(sessionId);
    }

    public void updateSelectedSessionTab(final SessionId sessionId, final int tabIndex) {
        if (sessionId != null && tabIndex >= 0) {
            selectedSessionTabs.put(sessionId, tabIndex);
        }
    }
}
