package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.ViewController.ViewState;
import com.jagent.desktop.services.dialog.DialogService;
import javax.swing.JTextField;

/** Renames the selected session. */
public final class RenameSessionAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private static final String TITLE = "Rename session";

    @Inject
    public RenameSessionAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
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
        return viewController.currentSession() != null;
    }

    @Override
    public void execute() {
        final var state = appState;
        final Session session = viewController.currentSession();
        if (session == null) {
            return;
        }
        final JTextField name = new JTextField(session.name(), 30);
        if (dialogService.confirm(
                        name,
                        TITLE,
                        DialogService.OptionType.OK_CANCEL,
                        DialogService.MessageType.PLAIN)
                != DialogService.ReturnType.OK) {
            return;
        }
        final String updatedName = name.getText().trim();
        if (updatedName.isBlank()) {
            dialogService.errorMessage("Session name is required.", "Rename session");
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
            dialogService.errorMessage(
                    "A session with that name already exists in this project.", TITLE);
            return;
        }
        final var sessionId = viewController.currentSessionId();
        if (sessionId == null) {
            return;
        }
        appState.updateSession(sessionId, session.withName(updatedName));
        viewController.updateView(
                ViewId.SESSION, ViewState.session(session.projectId(), sessionId));
    }
}
