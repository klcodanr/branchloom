package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.dialogs.NewProjectDialog;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.nio.file.Path;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/** Creates a fresh Git project and opens an agent terminal in it. */
public final class NewProjectAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private final String targetGroup;

    @Inject
    public NewProjectAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        this(viewController, appState, dialogService, null);
    }

    public NewProjectAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            @Nullable final String targetGroup) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
        this.targetGroup = Optional.ofNullable(targetGroup).orElse(Defaults.DEFAULT_GROUP);
    }

    @Override
    public String id() {
        return "initialize-project";
    }

    @Override
    public String label() {
        return NewProjectDialog.TITLE;
    }

    @Override
    public void execute() {
        final var agents =
                appState.appSettings().agents().stream()
                        .filter(
                                agent ->
                                        agent.newSessionCommand != null
                                                && agent.newSessionCommand.contains("{prompt}"))
                        .toList();
        if (agents.isEmpty()) {
            dialogService.errorMessage(
                    "Configure an agent with a {prompt} command in Settings first.", label());
            return;
        }
        dialogService.show(
                label(),
                new NewProjectDialog(dialogService, agents, this::createProject),
                "Create");
    }

    private void createProject(final NewProjectDialog.Request request) {
        if (appState.projects().values().stream()
                .anyMatch(
                        project ->
                                project.name().equalsIgnoreCase(request.name())
                                        || Path.of(project.path())
                                                .toAbsolutePath()
                                                .normalize()
                                                .equals(request.folder()))) {
            dialogService.errorMessage(
                    "A project with that name or folder already exists.", label());
            return;
        }
        final String command =
                request.agent()
                        .newSessionCommand
                        .replace("{prompt}", PlatformCommands.shellQuote(request.prompt()));
        ProgressOperation.run(
                        label(),
                        "Initializing Git repository...",
                        () -> GitRepository.initialize(request.folder()))
                .thenAccept(
                        folder -> {
                            final var projectId =
                                    appState.addProject(
                                            Project.create(request.name(), folder.toString(), null)
                                                    .withGroup(targetGroup));
                            final var terminalId =
                                    appState.addTerminal(
                                            Terminal.create(
                                                    projectId, request.agent().name, command));
                            viewController.updateView(
                                    ViewId.PROJECT,
                                    ViewController.ViewState.projectTerminal(
                                            projectId, terminalId));
                        })
                .exceptionally(
                        failure -> {
                            dialogService.errorMessage(
                                    "Could not create the project folder or initialize Git:\n"
                                            + ErrorMessages.deepestCause(
                                                    failure, "No further details were provided."),
                                    label());
                            return null;
                        });
    }
}
