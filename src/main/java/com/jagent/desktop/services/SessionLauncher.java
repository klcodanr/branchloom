package com.jagent.desktop.services;

import com.google.inject.Inject;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.SessionCreationService.CreatedSession;
import com.jagent.desktop.services.ViewController.ViewState;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.terminal.TerminalPanel;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Opens a newly created session and runs its configured setup commands. */
public final class SessionLauncher {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private final BackgroundJobs backgroundJobs;

    @Inject
    public SessionLauncher(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final BackgroundJobs backgroundJobs) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
        this.backgroundJobs = backgroundJobs;
    }

    public void launch(final Project project, final CreatedSession created) {
        final var terminal = appState.terminals().get(created.terminalId());
        final List<String> startupCommands =
                project.startupCommands().isEmpty()
                        ? InstallCommandGuesser.guess(Path.of(created.session().worktreePath()))
                                .stream()
                                .toList()
                        : project.startupCommands();
        final TerminalPanel terminalPanel =
                TerminalPanel.retained(
                        created.terminalId(),
                        terminal,
                        Path.of(created.session().worktreePath()).toAbsolutePath().normalize(),
                        project.name()
                                + " > "
                                + created.session().name()
                                + " > "
                                + terminal.title(),
                        dialogService);
        viewController.updateView(
                ViewId.SESSION,
                ViewState.sessionTerminal(
                        created.session().projectId(),
                        created.session().id(),
                        created.terminalId()));
        if (startupCommands.isEmpty()) {
            terminalPanel.start();
        } else {
            final AtomicInteger current = new AtomicInteger(1);
            final Handle job =
                    backgroundJobs.start("Session setup", project.name(), created.session().name());
            BackgroundOperations.runCommands(
                            "Commands",
                            "session-startup",
                            startupCommands,
                            Path.of(created.session().worktreePath()),
                            job::output,
                            (ignored) -> {
                                job.update(
                                        "Startup command "
                                                + current.getAndIncrement()
                                                + " of "
                                                + startupCommands.size()
                                                + " completed");
                            })
                    .exceptionally(
                            error -> {
                                dialogService.errorMessage(
                                        "Startup command "
                                                + current.get()
                                                + " failed:\n\n"
                                                + startupCommands.get(current.get() - 1)
                                                + "\n\nError:\n"
                                                + error,
                                        "Session Setup Failed");
                                return null;
                            })
                    .whenComplete((ignored, error) -> terminalPanel.start());
        }
    }
}
