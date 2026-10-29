package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.ui.utils.GitUtils;

/** Copies the current session's branch name. */
public final class CopyBranchAction implements Action {
    private final ViewController viewController;
    private final AppState appState;

    @Inject
    public CopyBranchAction(final ViewController viewController, final AppState appState) {
        this.viewController = viewController;
        this.appState = appState;
    }

    @Override
    public String id() {
        return "copy-branch";
    }

    @Override
    public String label() {
        return "Copy branch name";
    }

    @Override
    public boolean enabled() {
        return currentSession() != null;
    }

    @Override
    public void execute() {
        final Session session = currentSession();
        if (session != null) {
            CopyPathAction.copy(
                    session.name().contains("/")
                            ? session.name()
                            : GitUtils.toBranchSlug(session.name()));
        }
    }

    private Session currentSession() {
        final var sessionId = viewController.currentSessionId();
        return sessionId == null ? null : appState.sessions().get(sessionId);
    }
}
