package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.CommandRunner;
import com.jagent.desktop.services.SessionCreationService.CreatedSession;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

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
            runStartupCommand(
                    project, created.worktreePath(), job, 0, terminalPanel::start, terminalPanel);
        }
    }

    private void runStartupCommand(
            final Project project,
            final String worktreePath,
            final Handle job,
            final int index,
            final Runnable onComplete,
            final TerminalPanel terminalPanel) {
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
                () ->
                        runStartupCommand(
                                project, worktreePath, job, index + 1, onComplete, terminalPanel),
                output ->
                        failStartup(
                                project,
                                worktreePath,
                                job,
                                index,
                                onComplete,
                                terminalPanel,
                                output));
    }

    private void failStartup(
            final Project project,
            final String worktreePath,
            final Handle job,
            final int index,
            final Runnable onComplete,
            final TerminalPanel terminalPanel,
            final String output) {
        final String message =
                output == null || output.isBlank() ? "Setup command failed." : output;
        job.fail(message);
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(
                () -> {
                    final Object[] options = {"Open terminal", "Retry setup"};
                    final int choice =
                            JOptionPane.showOptionDialog(
                                    actionContext.window(),
                                    "Startup setup failed. Choose how to continue.",
                                    "Session setup failed",
                                    JOptionPane.DEFAULT_OPTION,
                                    JOptionPane.WARNING_MESSAGE,
                                    null,
                                    options,
                                    options[0]);
                    if (choice == 1) {
                        runStartupCommand(
                                project, worktreePath, job, index, onComplete, terminalPanel);
                    } else if (choice == 0) {
                        terminalPanel.start();
                    }
                });
    }
}
