package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import java.util.Locale;

/** Creates a terminal tab in the current project or session view. */
public final class CreateTerminalAction extends BaseAction {
    private final String title;
    private final String command;

    public CreateTerminalAction(final ActionContext actionContext) {
        this(actionContext, "New Terminal Tab", PlatformCommands.userShell());
    }

    public CreateTerminalAction(
            final ActionContext actionContext, final String title, final String command) {
        super(actionContext);
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
        return actionContext.appState().currentProjectId() != null;
    }

    @Override
    public void execute() {
        final AppState state = actionContext.appState();
        final ProjectId projectId = state.currentProjectId();
        if (projectId == null) {
            return;
        }
        final SessionId sessionId = state.currentSessionId();
        final var terminalId =
                sessionId == null
                        ? state.addTerminal(new Terminal(null, projectId, title, command))
                        : state.addTerminal(sessionId, new Terminal(sessionId, title, command));
        actionContext
                .viewCoordinator()
                .updateView(
                        sessionId == null
                                ? com.jagent.desktop.api.ViewId.PROJECT
                                : com.jagent.desktop.api.ViewId.SESSION,
                        sessionId == null
                                ? ViewState.projectTerminal(projectId, terminalId)
                                : ViewState.sessionTerminal(projectId, sessionId, terminalId));
    }
}
