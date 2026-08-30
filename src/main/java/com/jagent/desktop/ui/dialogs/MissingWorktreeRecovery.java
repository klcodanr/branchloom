package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Offers recovery when an imported session points to a missing worktree. */
public final class MissingWorktreeRecovery {
    private static final Logger LOG = Logger.getLogger(MissingWorktreeRecovery.class.getName());

    private MissingWorktreeRecovery() {}

    public static void check(
            final ActionContext actionContext, final Project project, final Session session) {
        final String worktreePath = session.worktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            return;
        }
        final Path worktree = Path.of(worktreePath);
        if (Files.isDirectory(worktree)) {
            return;
        }
        final Git git = new Git();
        git.checkPrunableWorktree(project, worktree)
                .whenCompleteAsync(
                        (prunable, failure) -> {
                            if (failure != null) {
                                LOG.log(Level.WARNING, "Check missing worktree", failure);
                                return;
                            }
                            showRecovery(actionContext, project, session, prunable, git);
                        },
                        SwingUtilities::invokeLater);
    }

    private static void showRecovery(
            final ActionContext actionContext,
            final Project project,
            final Session session,
            final Optional<Git.Worktree> prunable,
            final Git git) {
        final Object[] options =
                prunable.isPresent()
                        ? new Object[] {"Fix worktree", "Remove session", "Cancel"}
                        : new Object[] {"Remove session", "Cancel"};
        final int choice =
                JOptionPane.showOptionDialog(
                        actionContext.window(),
                        message(session, prunable.isPresent()),
                        "Missing worktree",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[0]);
        if (choice < 0 || choice == options.length - 1) {
            return;
        }
        if (prunable.isPresent() && choice == 0) {
            restore(actionContext, project, prunable.get(), git);
        } else {
            remove(actionContext, project, session, git);
        }
    }

    private static String message(final Session session, final boolean canRestore) {
        final String prefix =
                "The worktree for '" + session.name() + "' is missing:\n" + session.worktreePath();
        return canRestore
                ? prefix + "\n\nFix it by checking out the recorded branch, or remove the session."
                : prefix + "\n\nNo recorded branch is available to repair it.";
    }

    private static void restore(
            final ActionContext actionContext,
            final Project project,
            final Git.Worktree worktree,
            final Git git) {
        git.restoreWorktree(project, worktree)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            if (failure != null) {
                                showError(actionContext.window(), "Fix worktree", failure);
                                return;
                            }
                            actionContext
                                    .viewCoordinator()
                                    .updateView(
                                            com.jagent.desktop.api.ViewId.SESSION,
                                            ViewState.session(
                                                    actionContext.appState().currentProjectId(),
                                                    actionContext.appState().currentSessionId()));
                        },
                        SwingUtilities::invokeLater);
    }

    private static void remove(
            final ActionContext actionContext,
            final Project project,
            final Session session,
            final Git git) {
        git.pruneWorktrees(project)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            if (failure != null) {
                                showError(actionContext.window(), "Remove session", failure);
                                return;
                            }
                            final SessionId sessionId = actionContext.appState().currentSessionId();
                            if (sessionId != null) {
                                actionContext.appState().removeSession(sessionId);
                                actionContext
                                        .viewCoordinator()
                                        .updateView(
                                                com.jagent.desktop.api.ViewId.PROJECT,
                                                ViewState.project(session.projectId()));
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private static void showError(final Window owner, final String title, final Throwable failure) {
        final Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
        JOptionPane.showMessageDialog(
                owner,
                cause.getMessage() == null ? "Git operation failed." : cause.getMessage(),
                title,
                JOptionPane.ERROR_MESSAGE);
    }
}
