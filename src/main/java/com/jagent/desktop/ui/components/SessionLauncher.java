package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.CommandRunner;
import com.jagent.desktop.services.SessionCreationService.CreatedSession;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import java.nio.file.Path;

/** Opens a newly created session and runs its configured setup commands. */
public final class SessionLauncher {
    private final ActionContext actionContext;

    public SessionLauncher(final ActionContext actionContext) {
        this.actionContext = actionContext;
    }

    public void launch(final Project project, final CreatedSession created) {
        final var terminal = actionContext.appState().terminals().get(created.terminalId());
        final TerminalPanel terminalPanel =
                TerminalPanel.retained(
                        created.terminalId(),
                        terminal,
                        Path.of(created.worktreePath()).toAbsolutePath().normalize(),
                        project.name()
                                + " > "
                                + created.session().name()
                                + " > "
                                + terminal.title());
        actionContext
                .viewCoordinator()
                .updateView(
                        ViewId.SESSION,
                        ViewState.sessionTerminal(
                                created.session().projectId(),
                                created.sessionId(),
                                created.terminalId()));
        if (project.startupCommands().isEmpty()) {
            terminalPanel.start();
        } else {
            final Handle job =
                    actionContext.viewCoordinator().backgroundJobs().start("Session setup");
            runStartupCommand(project, created.worktreePath(), job, 0, terminalPanel::start);
        }
    }

    private void runStartupCommand(
            final Project project,
            final String worktreePath,
            final Handle job,
            final int index,
            final Runnable onComplete) {
        if (index >= project.startupCommands().size()) {
            job.complete();
            onComplete.run();
            return;
        }
        job.update(
                "Running startup command "
                        + (index + 1)
                        + " of "
                        + project.startupCommands().size());
        CommandRunner.run(
                project.startupCommands().get(index),
                Path.of(worktreePath),
                ignored -> {},
                () -> runStartupCommand(project, worktreePath, job, index + 1, onComplete),
                output ->
                        job.fail(
                                output == null || output.isBlank()
                                        ? "Setup command failed."
                                        : output));
    }
}
