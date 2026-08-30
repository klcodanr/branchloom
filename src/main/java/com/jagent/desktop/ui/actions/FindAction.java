package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.components.CommandPalette;
import java.util.ArrayList;
import java.util.List;

public final class FindAction extends BaseAction {
    public FindAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "find";
    }

    @Override
    public String label() {
        return "Find...";
    }

    @Override
    public void execute() {
        final AppState state = actionContext.appState();

        final List<CommandPalette.Choice> choices = new ArrayList<>();
        state.projects()
                .forEach(
                        (projectId, project) -> {
                            choices.add(
                                    new CommandPalette.Choice(
                                            "Project: " + project.name(),
                                            () ->
                                                    actionContext
                                                            .viewCoordinator()
                                                            .updateView(
                                                                    ViewId.PROJECT,
                                                                    ViewState.project(projectId))));
                            project.sessionIds()
                                    .forEach(
                                            sessionId -> {
                                                final Session session =
                                                        state.sessions().get(sessionId);
                                                if (session == null) {
                                                    return;
                                                }
                                                choices.add(
                                                        new CommandPalette.Choice(
                                                                "Session: "
                                                                        + project.name()
                                                                        + " / "
                                                                        + session.name(),
                                                                () ->
                                                                        actionContext
                                                                                .viewCoordinator()
                                                                                .updateView(
                                                                                        ViewId
                                                                                                .SESSION,
                                                                                        ViewState
                                                                                                .session(
                                                                                                        projectId,
                                                                                                        sessionId))));
                                                session.terminalIds()
                                                        .forEach(
                                                                terminalId -> {
                                                                    final Terminal terminal =
                                                                            state.terminals()
                                                                                    .get(
                                                                                            terminalId);
                                                                    if (terminal == null) {
                                                                        return;
                                                                    }
                                                                    choices.add(
                                                                            new CommandPalette
                                                                                    .Choice(
                                                                                    "Terminal: "
                                                                                            + project
                                                                                                    .name()
                                                                                            + " / "
                                                                                            + session
                                                                                                    .name()
                                                                                            + " / "
                                                                                            + terminal
                                                                                                    .title(),
                                                                                    () -> {
                                                                                        actionContext
                                                                                                .viewCoordinator()
                                                                                                .updateView(
                                                                                                        ViewId
                                                                                                                .SESSION,
                                                                                                        new ViewState(
                                                                                                                projectId,
                                                                                                                sessionId,
                                                                                                                terminalId));
                                                                                    }));
                                                                });
                                            });
                        });
        CommandPalette.open(actionContext.window(), "Find project, session, or terminal", choices);
    }
}
