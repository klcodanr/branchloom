package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.components.UiText;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletionException;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Starts the selected session removal workflow. */
public class RemoveSessionAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private final BackgroundJobs backgroundJobs;
    private static final String TITLE = "Remove session";

    private record WorktreeCheck(Path path, boolean hasChanges) {}

    @Inject
    public RemoveSessionAction(
            final BackgroundJobs backgroundJobs,
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        this.backgroundJobs = backgroundJobs;
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
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
        return viewController.currentSessionId() != null;
    }

    @Override
    public void execute() {
        final var state = appState;
        final SessionId sessionId = viewController.currentSessionId();
        final Session session = viewController.currentSession();
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
        final DialogService.ReturnType choice =
                dialogService.option(
                        message, TITLE, DialogService.MessageType.WARNING, options, options[0]);
        if (choice == DialogService.ReturnType.OK && removeWorktree.isSelected()) {
            return 2;
        }
        return choice == DialogService.ReturnType.OK ? 0 : -1;
    }

    protected boolean confirmWorktreeDeletion(final Session session) {
        return dialogService.confirm(
                        "Permanently delete this worktree and all uncommitted files?\n"
                                + session.worktreePath(),
                        "Confirm worktree deletion",
                        DialogService.OptionType.YES_NO,
                        DialogService.MessageType.WARNING)
                == DialogService.ReturnType.OK;
    }

    protected boolean confirmResidualFolderDeletion(final Path path) {
        return dialogService.confirm(
                        "Git removed the worktree, but this folder still exists:\n"
                                + path
                                + "\n\nDelete the folder and its contents?",
                        "Remove leftover worktree folder",
                        DialogService.OptionType.YES_NO,
                        DialogService.MessageType.WARNING)
                == DialogService.ReturnType.OK;
    }

    private void removeWorktree(
            final SessionId sessionId,
            final ProjectId projectId,
            final Project project,
            final Session session) {
        final var job = this.backgroundJobs.start(TITLE, project.name(), session.name());
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
        dialogService.errorMessage(message, TITLE);
    }

    protected void removeSession(final SessionId sessionId, final ProjectId projectId) {
        appState.removeSession(sessionId);
    }
}
