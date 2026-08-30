package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

/** Renames the selected session. */
public final class RenameSessionAction extends BaseAction {
    private static final String TITLE = "Rename session";

    public RenameSessionAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "rename-session";
    }

    @Override
    public String label() {
        return TITLE;
    }

    @Override
    public boolean enabled() {
        return actionContext.appState().currentSession() != null;
    }

    @Override
    public void execute() {
        final AppState state = actionContext.appState();
        final Session session = state.currentSession();
        if (session == null) {
            return;
        }
        final JTextField name = new JTextField(session.name(), 30);
        if (JOptionPane.showConfirmDialog(
                        actionContext.window(), name, TITLE, JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        final String updatedName = name.getText().trim();
        if (updatedName.isBlank()) {
            JOptionPane.showMessageDialog(
                    actionContext.window(),
                    "Session name is required.",
                    "Rename session",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (updatedName.equalsIgnoreCase(session.name())) {
            return;
        }
        final Project project = state.projects().get(session.projectId());
        if (project != null
                && project.sessionIds().stream()
                        .map(state.sessions()::get)
                        .anyMatch(
                                other ->
                                        other != null
                                                && other.name().equalsIgnoreCase(updatedName))) {
            JOptionPane.showMessageDialog(
                    actionContext.window(),
                    "A session with that name already exists in this project.",
                    TITLE,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        final var sessionId = state.currentSessionId();
        if (sessionId == null) {
            return;
        }
        state.updateSession(sessionId, session.withName(updatedName));
        actionContext
                .viewCoordinator()
                .updateView(ViewId.SESSION, ViewState.session(session.projectId(), sessionId));
    }
}
