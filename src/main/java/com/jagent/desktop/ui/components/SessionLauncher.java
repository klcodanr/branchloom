package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.SessionCreationService.CreatedSession;
import com.jagent.desktop.services.ViewController.ViewState;
import com.jagent.desktop.ui.components.terminal.TerminalPanel;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Opens a newly created session and runs its configured setup commands. */
public final class SessionLauncher {
    private final ActionContext actionContext;
    private final TerminalPanelFactory terminalPanelFactory;

    public SessionLauncher(final ActionContext actionContext) {
        this(actionContext, TerminalPanel::retained);
    }

    /* package */ SessionLauncher(
            final ActionContext actionContext, final TerminalPanelFactory terminalPanelFactory) {
        this.actionContext = actionContext;
        this.terminalPanelFactory =
                terminalPanelFactory == null ? TerminalPanel::retained : terminalPanelFactory;
    }

    public void launch(final Project project, final CreatedSession created) {
        final var terminal = actionContext.appState().terminals().get(created.terminalId());
        final TerminalPanel terminalPanel =
                terminalPanelFactory.create(
                        created.terminalId(),
                        terminal,
                        Path.of(created.worktreePath()).toAbsolutePath().normalize(),
                        project.name()
                                + " > "
                                + created.session().name()
                                + " > "
                                + terminal.title());
        actionContext
                .viewController()
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
                    actionContext
                            .viewController()
                            .backgroundJobs()
                            .start("Session setup", project.name(), created.session().name());
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
        BackgroundOperations.runCommand(
                        "Commands",
                        "session-startup",
                        project.startupCommands().get(index),
                        Path.of(worktreePath),
                        job::output)
                .thenRun(
                        () ->
                                runStartupCommand(
                                        project,
                                        worktreePath,
                                        job,
                                        index + 1,
                                        onComplete,
                                        terminalPanel))
                .exceptionally(
                        exception -> {
                            final String message =
                                    exception.getCause() == null
                                            ? exception.getMessage()
                                            : exception.getCause().getMessage();
                            failStartup(
                                    project,
                                    worktreePath,
                                    job,
                                    index,
                                    onComplete,
                                    terminalPanel,
                                    UiText.valueOrDefault(message, ""),
                                    project.startupCommands().get(index));
                            return null;
                        });
    }

    private void failStartup(
            final Project project,
            final String worktreePath,
            final Handle job,
            final int index,
            final Runnable onComplete,
            final TerminalPanel terminalPanel,
            final String output,
            final String command) {
        final String message = UiText.valueOrDefault(output, "Setup command failed.");
        job.fail(message);
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(
                () -> {
                    final Object[] options = {"Open terminal", "Retry setup"};
                    final JTextArea details =
                            new JTextArea(failureDetails(index, command, message));
                    details.setEditable(false);
                    details.setLineWrap(true);
                    details.setWrapStyleWord(true);
                    details.setRows(10);
                    details.setColumns(60);
                    final int choice =
                            JOptionPane.showOptionDialog(
                                    actionContext.window(),
                                    new JScrollPane(details),
                                    "Session setup failed",
                                    JOptionPane.DEFAULT_OPTION,
                                    JOptionPane.WARNING_MESSAGE,
                                    null,
                                    options,
                                    options[0]);
                    if (choice == 1) {
                        runStartupCommand(
                                project,
                                worktreePath,
                                actionContext
                                        .viewController()
                                        .backgroundJobs()
                                        .start("Session setup", project.name(), ""),
                                index,
                                onComplete,
                                terminalPanel);
                    } else if (choice == 0) {
                        openExternalTerminal(worktreePath);
                    }
                });
    }

    private String failureDetails(final int index, final String command, final String output) {
        return "Startup command "
                + (index + 1)
                + " failed:\n\n"
                + command
                + "\n\nOutput:\n"
                + output;
    }

    private void openExternalTerminal(final String worktreePath) {
        BackgroundOperations.submit(
                        "Terminals",
                        "open-session-terminal",
                        () -> PlatformCommands.openTerminal(Path.of(worktreePath)))
                .exceptionally(
                        exception -> {
                            final Throwable cause =
                                    exception.getCause() == null ? exception : exception.getCause();
                            SwingUtilities.invokeLater(
                                    () ->
                                            JOptionPane.showMessageDialog(
                                                    actionContext.window(),
                                                    "Could not open terminal: "
                                                            + UiText.valueOrDefault(
                                                                    cause.getMessage(),
                                                                    "Unknown error."),
                                                    "Open terminal failed",
                                                    JOptionPane.ERROR_MESSAGE));
                            return null;
                        });
    }

    /* package */
    @FunctionalInterface
    interface TerminalPanelFactory {
        TerminalPanel create(
                TerminalId id, Terminal definition, Path directory, String resourceName);
    }
}
