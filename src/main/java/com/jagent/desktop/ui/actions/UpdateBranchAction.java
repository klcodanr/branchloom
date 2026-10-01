package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.utils.ErrorDialogs;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.nio.file.Path;
import javax.swing.JOptionPane;

/** Updates the selected branch without overwriting local commits. */
public final class UpdateBranchAction extends BaseAction {
    public UpdateBranchAction(final ActionContext actionContext) {
        super(actionContext);
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
        return actionContext.appState().currentProjectId() != null
                || actionContext.appState().currentSessionId() != null;
    }

    @Override
    public void execute() {
        final Session session = actionContext.appState().currentSession();
        if (session != null) {
            chooseSessionUpdate(
                    session.worktreePath() == null ? null : Path.of(session.worktreePath()),
                    actionContext.appState().currentProject());
            return;
        }
        final ProjectId projectId = actionContext.appState().currentProjectId();
        final Project project =
                projectId == null ? null : actionContext.appState().projects().get(projectId);
        if (project != null) {
            updatePrimary(project);
        }
    }

    private void chooseSessionUpdate(final Path worktree, final Project project) {
        if (worktree == null) {
            return;
        }
        final String[] options =
                project == null
                        ? new String[] {"Update from upstream"}
                        : new String[] {"Update from upstream", "Rebase onto primary"};
        final int choice =
                JOptionPane.showOptionDialog(
                        actionContext.window(),
                        "How should the current branch be updated?",
                        label(),
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]);
        if (choice < 0) {
            return;
        }
        if (choice == 0 || project == null) {
            updateCurrent(worktree);
            return;
        }
        rebaseOntoPrimary(worktree);
    }

    private void updateCurrent(final Path worktree) {
        ProgressOperation.run(
                        actionContext,
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
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        "The current branch is up to date.",
                                        label(),
                                        JOptionPane.INFORMATION_MESSAGE))
                .exceptionally(
                        failure -> {
                            ErrorDialogs.show(
                                    actionContext.window(),
                                    label(),
                                    ErrorMessages.deepestCause(
                                            failure, "Could not update the current branch."));
                            return null;
                        });
    }

    private void rebaseOntoPrimary(final Path worktree) {
        ProgressOperation.run(
                        actionContext,
                        label(),
                        "Rebasing onto the primary branch...",
                        () -> {
                            try (GitRepository repository = GitRepository.open(worktree)) {
                                repository.rebasePrimaryBranch();
                                return null;
                            }
                        })
                .thenRun(
                        () ->
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        "The current branch includes the latest primary branch.",
                                        label(),
                                        JOptionPane.INFORMATION_MESSAGE))
                .exceptionally(
                        failure -> {
                            ErrorDialogs.show(
                                    actionContext.window(),
                                    label(),
                                    ErrorMessages.deepestCause(
                                            failure, "Could not rebase onto the primary branch."));
                            return null;
                        });
    }

    private void updatePrimary(final Project project) {
        ProgressOperation.run(
                        actionContext,
                        label(),
                        "Rebasing the primary branch...",
                        () -> {
                            try (GitRepository repository =
                                    GitRepository.open(Path.of(project.path()))) {
                                repository.updatePrimaryBranch();
                                return null;
                            }
                        })
                .thenRun(
                        () ->
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        "The primary branch is up to date.",
                                        label(),
                                        JOptionPane.INFORMATION_MESSAGE))
                .exceptionally(
                        failure -> {
                            ErrorDialogs.show(
                                    actionContext.window(),
                                    label(),
                                    ErrorMessages.deepestCause(
                                            failure, "Could not update the primary branch."));
                            return null;
                        });
    }
}
