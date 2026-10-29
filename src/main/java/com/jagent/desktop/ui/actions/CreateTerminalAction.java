package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.ViewController.ViewState;
import java.util.Locale;

/** Creates a terminal tab in the current project or session view. */
public final class CreateTerminalAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final String title;
    private final String command;

    @Inject
    public CreateTerminalAction(final ViewController viewController, final AppState appState) {
        this(viewController, appState, "New Terminal Tab", PlatformCommands.userShell());
    }

    public CreateTerminalAction(
            final ViewController viewController,
            final AppState appState,
            final String title,
            final String command) {
        this.viewController = viewController;
        this.appState = appState;
        this.title = title;
        this.command = command;
    }

    @Override
    public String id() {
        return "new-terminal-" + title.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    @Override
    public String label() {
        return title;
    }

    @Override
    public boolean enabled() {
        return viewController.currentProjectId() != null;
    }

    @Override
    public void execute() {
        final ProjectId projectId = viewController.currentProjectId();
        if (projectId == null) {
            return;
        }
        final SessionId sessionId = viewController.currentSessionId();
        final var terminalId =
                sessionId == null
                        ? appState.addTerminal(new Terminal(null, projectId, title, command))
                        : appState.addTerminal(
                                sessionId, Terminal.create(sessionId, title, command));
        viewController.updateView(
                sessionId == null ? ViewId.PROJECT : ViewId.SESSION,
                sessionId == null
                        ? ViewState.projectTerminal(projectId, terminalId)
                        : ViewState.sessionTerminal(projectId, sessionId, terminalId));
    }
}
