package com.jagent.desktop.services;

import com.google.inject.Inject;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.git.Worktree;
import com.jagent.desktop.services.ViewController.ViewState;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Offers recovery when a session points to a missing worktree. */
public final class MissingWorktreeRecovery {
    private static final Logger LOG = LoggerFactory.getLogger(MissingWorktreeRecovery.class);
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;

    @Inject
    public MissingWorktreeRecovery(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        this.viewController = Objects.requireNonNull(viewController);
        this.appState = Objects.requireNonNull(appState);
        this.dialogService = Objects.requireNonNull(dialogService);
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    public void check(final Session session) {
        final Project project = appState.projects().get(session.projectId());
        final String worktreePath = session.worktreePath();
        if (worktreePath == null || worktreePath.isBlank() || project == null) {
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
                                final Optional<Worktree> prunable =
                                        repository.listWorktrees().stream()
                                                .filter(Worktree::prunable)
                                                .filter(
                                                        candidate ->
                                                                candidate
                                                                        .path()
                                                                        .toAbsolutePath()
                                                                        .normalize()
                                                                        .equals(normalizedWorktree))
                                                .findFirst();
                                final DialogService.ReturnType choice;
                                if (prunable.isEmpty()) {
                                    choice =
                                            dialogService.option(
                                                    message(session, prunable.isPresent()),
                                                    "Missing worktree",
                                                    DialogService.MessageType.WARNING,
                                                    new Object[] {
                                                        "Fix worktree", "Remove session", "Cancel"
                                                    },
                                                    "Fix worktree");
                                } else {

                                    choice =
                                            dialogService.option(
                                                    message(session, prunable.isPresent()),
                                                    "Missing worktree",
                                                    DialogService.MessageType.WARNING,
                                                    new Object[] {"Remove session", "Cancel"},
                                                    "Remove session");
                                }

                                try {
                                    if (prunable.isPresent()
                                            && DialogService.ReturnType.OK.equals(choice)) {
                                        repository.restoreWorktree(prunable.get());
                                    } else {
                                        appState.removeSession(session.id());
                                        if (viewController
                                                .currentSessionId()
                                                .equals(session.id())) {
                                            viewController.updateView(
                                                    ViewId.PROJECT,
                                                    ViewState.project(session.projectId()));
                                        }
                                    }
                                } catch (IOException e) {
                                    dialogService.errorMessage(
                                            "Failed to fix worktree: "
                                                    + ErrorMessages.deepestCause(
                                                            e, "Unknown issue"),
                                            worktreePath);
                                }
                                return null;
                            }
                        })
                .exceptionally(
                        failure -> {
                            LOG.warn("Failed to check missing worktree", failure);
                            return null;
                        });
    }

    private static String message(final Session session, final boolean canRestore) {
        final String prefix =
                "The worktree for '" + session.name() + "' is missing:\n" + session.worktreePath();
        return canRestore
                ? prefix + "\n\nFix it by checking out the recorded branch, or remove the session."
                : prefix + "\n\nNo recorded branch is available to repair it.";
    }
}
