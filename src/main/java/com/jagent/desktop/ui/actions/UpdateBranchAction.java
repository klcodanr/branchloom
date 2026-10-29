package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.nio.file.Path;

/** Updates the selected branch without overwriting local commits. */
public final class UpdateBranchAction implements Action {
    private final ViewController viewController;

    private final DialogService dialogService;

    @Inject
    public UpdateBranchAction(
            final ViewController viewController, final DialogService dialogService) {
        this.viewController = viewController;
        this.dialogService = dialogService;
    }

    @Override
    public String id() {
        return "update-branch";
    }

    @Override
    public String label() {
        return "Update branch";
    }

    @Override
    public boolean enabled() {
        return viewController.currentProjectId() != null
                || viewController.currentSessionId() != null;
    }

    @Override
    public void execute() {
        final Session session = viewController.currentSession();
        final Project project = viewController.currentProject();
        final Path path;
        if (session != null && session.worktreePath() != null) {
            path = Path.of(session.worktreePath());
        } else if (project != null) {
            path = Path.of(project.path());
        } else {
            return;
        }
        updateCurrent(path);
    }

    private void updateCurrent(final Path worktree) {
        ProgressOperation.run(
                        label(),
                        "Fetching latest commits...",
                        () -> {
                            try (GitRepository repository = GitRepository.open(worktree)) {
                                repository.updateCurrentBranch();
                                return null;
                            }
                        })
                .thenRun(
                        () ->
                                dialogService.message(
                                        "The current branch is up to date.",
                                        label(),
                                        DialogService.MessageType.INFORMATION))
                .exceptionally(
                        failure -> {
                            dialogService.errorMessage(
                                    ErrorMessages.deepestCause(
                                            failure, "Could not update the current branch."),
                                    label());
                            return null;
                        });
    }
}
