package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.components.UiText;
import com.jagent.desktop.ui.utils.ErrorDialogs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletionException;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Starts the selected session removal workflow. */
public class RemoveSessionAction extends BaseAction {
    private static final String TITLE = "Remove session";

    private record WorktreeCheck(Path path, boolean hasChanges) {}

    public RemoveSessionAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "remove-session";
    }

    @Override
    public String label() {
        return TITLE;
    }

    @Override
    public boolean enabled() {
        return actionContext.viewController().currentSessionId() != null;
    }

    @Override
    public void execute() {
        final var state = actionContext.appState();
        final SessionId sessionId = actionContext.viewController().currentSessionId();
        final Session session = actionContext.viewController().currentSession();
        final ProjectId projectId = session == null ? null : session.projectId();
        final Project project = projectId == null ? null : state.projects().get(projectId);
        if (session == null || project == null) {
            return;
        }

        final int choice = removalChoice(session);
        if (choice < 0 || choice == 1) {
            return;
        }

        if (choice == 2) {
            removeWorktree(sessionId, projectId, project, session);
            return;
        }

        removeSession(sessionId, projectId);
    }

    protected int removalChoice(final Session session) {
        final JCheckBox removeWorktree = new JCheckBox("Also remove worktree", true);
        final JPanel message = new JPanel();
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        message.add(new JLabel("Remove " + session.name() + "?"));
        message.add(removeWorktree);
        final Object[] options = {TITLE, "Cancel"};
        final int choice =
                JOptionPane.showOptionDialog(
                        actionContext.window(),
                        message,
                        TITLE,
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[0]);
        if (choice == 0 && removeWorktree.isSelected()) {
            return 2;
        }
        return choice;
    }

    protected boolean confirmWorktreeDeletion(final Session session) {
        return JOptionPane.showConfirmDialog(
                        actionContext.window(),
                        "Permanently delete this worktree and all uncommitted files?\n"
                                + session.worktreePath(),
                        "Confirm worktree deletion",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    protected boolean confirmResidualFolderDeletion(final Path path) {
        return JOptionPane.showConfirmDialog(
                        actionContext.window(),
                        "Git removed the worktree, but this folder still exists:\n"
                                + path
                                + "\n\nDelete the folder and its contents?",
                        "Remove leftover worktree folder",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    private void removeWorktree(
            final SessionId sessionId,
            final ProjectId projectId,
            final Project project,
            final Session session) {
        final var job =
                actionContext
                        .viewController()
                        .backgroundJobs()
                        .start(TITLE, project.name(), session.name());
        job.update("Checking worktree...");
        job.output("Checking worktree: " + session.worktreePath());
        BackgroundOperations.submit(
                        "Operations",
                        TITLE,
                        () -> {
                            final String worktreePath = session.worktreePath();
                            if (worktreePath == null || worktreePath.isBlank()) {
                                throw new IllegalStateException(
                                        "The session has no worktree path.");
                            }
                            final Path path = Path.of(worktreePath);
                            try (GitRepository worktree = GitRepository.open(path)) {
                                final var status = worktree.statusSummary();
                                final boolean hasChanges =
                                        status.additions() > 0
                                                || status.modifications() > 0
                                                || status.deletions() > 0;
                                return new WorktreeCheck(path, hasChanges);
                            }
                        })
                .thenAccept(
                        check -> {
                            if (check.hasChanges() && !confirmWorktreeDeletion(session)) {
                                job.output("Worktree removal cancelled.");
                                job.complete();
                                return;
                            }
                            job.update("Removing worktree...");
                            job.output("Removing worktree: " + check.path());
                            BackgroundOperations.submit(
                                            "Operations",
                                            TITLE,
                                            () -> {
                                                try (GitRepository repository =
                                                        GitRepository.open(
                                                                Path.of(project.path()))) {
                                                    repository.deleteWorktree(check.path());
                                                    repository.pruneWorktrees(
                                                            Path.of(project.path()));
                                                    return Files.exists(check.path())
                                                            ? check.path()
                                                            : null;
                                                }
                                            })
                                    .thenAccept(
                                            residualPath ->
                                                    finishWorktreeRemoval(
                                                            job,
                                                            sessionId,
                                                            projectId,
                                                            residualPath))
                                    .exceptionally(
                                            failure -> {
                                                failRemoval(job, failure);
                                                return null;
                                            });
                        })
                .exceptionally(
                        failure -> {
                            failRemoval(job, failure);
                            return null;
                        });
    }

    private void finishWorktreeRemoval(
            final Handle job,
            final SessionId sessionId,
            final ProjectId projectId,
            final Path residualPath) {
        if (residualPath != null && confirmResidualFolderDeletion(residualPath)) {
            removeResidualFolder(job, sessionId, projectId, residualPath);
            return;
        }
        if (residualPath != null) {
            job.output("Worktree folder retained: " + residualPath);
        }
        job.output("Worktree removed.");
        job.complete();
        removeSession(sessionId, projectId);
    }

    private void removeResidualFolder(
            final Handle job,
            final SessionId sessionId,
            final ProjectId projectId,
            final Path path) {
        BackgroundOperations.submit(
                        "Operations",
                        TITLE,
                        () -> {
                            deleteResidualFolder(path);
                            return null;
                        })
                .thenRun(
                        () -> {
                            job.output("Leftover worktree folder removed.");
                            job.complete();
                            removeSession(sessionId, projectId);
                        })
                .exceptionally(
                        failure -> {
                            failRemoval(job, failure);
                            return null;
                        });
    }

    private static void deleteResidualFolder(final Path path) throws IOException {
        try (var paths = Files.walk(path)) {
            final var candidates = paths.sorted(Comparator.reverseOrder()).toList();
            for (final Path candidate : candidates) {
                Files.deleteIfExists(candidate);
            }
        }
    }

    protected void failRemoval(final Handle job, final Throwable failure) {
        final Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
        final String message =
                UiText.valueOrDefault(cause.getMessage(), "Could not remove worktree.");
        job.output(message);
        job.fail(message);
        ErrorDialogs.show(actionContext.window(), TITLE, message);
    }

    protected void removeSession(final SessionId sessionId, final ProjectId projectId) {
        actionContext.appState().removeSession(sessionId);
    }
}
