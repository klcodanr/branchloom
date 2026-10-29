package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.ViewController.ViewState;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.OptionPicker;
import java.util.ArrayList;
import java.util.List;

public final class FindAction implements Action {
    private final ViewController viewController;
    private final OptionPicker commandPalette;

    @Inject
    public FindAction(final ViewController viewController, final DialogService dialogService) {
        this.viewController = viewController;
        this.commandPalette = new OptionPicker(dialogService);
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
        final var state = viewController.appState();

        final List<OptionPicker.Choice> choices = new ArrayList<>();
        state.projects()
                .forEach(
                        (projectId, project) -> {
                            choices.add(
                                    new OptionPicker.Choice(
                                            "Project: " + project.name(),
                                            () ->
                                                    viewController.updateView(
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
                                                        new OptionPicker.Choice(
                                                                "Session: "
                                                                        + project.name()
                                                                        + " / "
                                                                        + session.name(),
                                                                () ->
                                                                        viewController.updateView(
                                                                                ViewId.SESSION,
                                                                                ViewState.session(
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
                                                                            new OptionPicker.Choice(
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
                                                                                        viewController
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
        commandPalette.open("Find project, session, or terminal", choices);
    }
}
