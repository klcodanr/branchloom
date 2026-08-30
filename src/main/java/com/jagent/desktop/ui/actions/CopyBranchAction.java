package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.ui.GitUtils;

/** Copies the current session's branch name. */
public final class CopyBranchAction extends BaseAction {
    public CopyBranchAction(final ActionContext actionContext) {
        super(actionContext);
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
            CopyPathAction.copy(GitUtils.toBranchSlug(session.name()));
        }
    }

    private Session currentSession() {
        final var sessionId = actionContext.appState().currentSessionId();
        return sessionId == null ? null : actionContext.appState().sessions().get(sessionId);
    }
}
