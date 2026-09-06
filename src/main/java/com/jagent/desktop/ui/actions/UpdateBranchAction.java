package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.GitIntegrationStrategy;
import com.jagent.desktop.ui.dialogs.ProgressOperation;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Updates the selected branch without overwriting local commits. */
public final class UpdateBranchAction extends BaseAction {
    private final Git git = new Git();

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
                        : new String[] {
                            "Update from upstream", "Merge primary branch", "Rebase onto primary"
                        };
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
        } else {
            integratePrimary(
                    project,
                    worktree,
                    choice == 1 ? GitIntegrationStrategy.MERGE : GitIntegrationStrategy.REBASE);
        }
    }

    private void updateCurrent(final Path worktree) {
        final ProgressOperation progress =
                ProgressOperation.start(
                        actionContext.window(), label(), "Fetching latest commits...");
        git.updateBranch(worktree)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            progress.close();
                            if (failure == null) {
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        "The current branch is up to date.",
                                        label(),
                                        JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        message(failure),
                                        label(),
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private void integratePrimary(
            final Project project, final Path worktree, final GitIntegrationStrategy strategy) {
        final String operation = strategy == GitIntegrationStrategy.MERGE ? "Merging" : "Rebasing";
        final ProgressOperation progress =
                ProgressOperation.start(
                        actionContext.window(), label(), operation + " the primary branch...");
        git.integratePrimaryBranch(project, worktree, strategy)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            progress.close();
                            if (failure == null) {
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        "The current branch includes the latest primary branch.",
                                        label(),
                                        JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        message(failure),
                                        label(),
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private void updatePrimary(final Project project) {
        final ProgressOperation progress =
                ProgressOperation.start(
                        actionContext.window(), label(), "Fetching the primary branch...");
        git.updatePrimaryBranch(project)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            progress.close();
                            if (failure == null) {
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        "The primary branch is up to date.",
                                        label(),
                                        JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        message(failure),
                                        label(),
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private static String message(final Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null
                && (cause instanceof java.util.concurrent.CompletionException
                        || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
