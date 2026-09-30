package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.git.Worktree;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.components.UiText;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Offers recovery when an imported session points to a missing worktree. */
public final class MissingWorktreeRecovery {
    private static final Logger LOG = LoggerFactory.getLogger(MissingWorktreeRecovery.class);

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
        BackgroundOperations.submit(
                        "Git",
                        "check-missing-worktree",
                        () -> {
                            try (GitRepository repository =
                                    GitRepository.open(Path.of(project.path()))) {
                                final Path normalizedWorktree =
                                        worktree.toAbsolutePath().normalize();
                                return repository.listWorktrees().stream()
                                        .filter(Worktree::prunable)
                                        .filter(
                                                candidate ->
                                                        candidate
                                                                .path()
                                                                .toAbsolutePath()
                                                                .normalize()
                                                                .equals(normalizedWorktree))
                                        .findFirst();
                            }
                        })
                .thenAccept(prunable -> showRecovery(actionContext, project, session, prunable))
                .exceptionally(
                        failure -> {
                            LOG.warn("Check missing worktree", failure);
                            return null;
                        });
    }

    private static void showRecovery(
            final ActionContext actionContext,
            final Project project,
            final Session session,
            final Optional<Worktree> prunable) {
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
            restore(actionContext, project, prunable.get());
        } else {
            remove(actionContext, project, session);
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
            final ActionContext actionContext, final Project project, final Worktree worktree) {
        BackgroundOperations.submit(
                        "Git",
                        "restore-missing-worktree",
                        () -> {
                            try (GitRepository repository =
                                    GitRepository.open(Path.of(project.path()))) {
                                repository.restoreWorktree(worktree);
                                return null;
                            }
                        })
                .thenAccept(
                        ignored -> {
                            actionContext
                                    .viewCoordinator()
                                    .updateView(
                                            com.jagent.desktop.api.ViewId.SESSION,
                                            ViewState.session(
                                                    actionContext.appState().currentProjectId(),
                                                    actionContext.appState().currentSessionId()));
                        })
                .exceptionally(
                        failure -> {
                            showError(actionContext.window(), "Fix worktree", failure);
                            return null;
                        });
    }

    private static void remove(
            final ActionContext actionContext, final Project project, final Session session) {
        BackgroundOperations.submit(
                        "Git",
                        "prune-worktrees",
                        () -> {
                            try (GitRepository repository =
                                    GitRepository.open(Path.of(project.path()))) {
                                repository.pruneWorktrees(Path.of(project.path()));
                                return null;
                            }
                        })
                .thenAccept(
                        ignored -> {
                            final SessionId sessionId = actionContext.appState().currentSessionId();
                            if (sessionId == null) {
                                return;
                            }
                            final var currentProjectId =
                                    actionContext.appState().currentProjectId();
                            final Session currentSession =
                                    actionContext.appState().currentSession();
                            if (currentProjectId == null
                                    || currentSession == null
                                    || !currentProjectId.equals(session.projectId())
                                    || !Objects.equals(
                                            currentSession.worktreePath(),
                                            session.worktreePath())) {
                                return;
                            }
                            actionContext.appState().removeSession(sessionId);
                            actionContext
                                    .viewCoordinator()
                                    .updateView(
                                            com.jagent.desktop.api.ViewId.PROJECT,
                                            ViewState.project(session.projectId()));
                        })
                .exceptionally(
                        failure -> {
                            showError(actionContext.window(), "Remove session", failure);
                            return null;
                        });
    }

    private static void showError(final Window owner, final String title, final Throwable failure) {
        final Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
        JOptionPane.showMessageDialog(
                owner,
                UiText.valueOrDefault(cause.getMessage(), "Git operation failed."),
                title,
                JOptionPane.ERROR_MESSAGE);
    }
}
