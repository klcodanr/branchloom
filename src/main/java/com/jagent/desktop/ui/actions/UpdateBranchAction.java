package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.Git;
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
            update(session.worktreePath() == null ? null : Path.of(session.worktreePath()));
            return;
        }
        final ProjectId projectId = actionContext.appState().currentProjectId();
        final Project project =
                projectId == null ? null : actionContext.appState().projects().get(projectId);
        if (project != null) {
            update(Path.of(project.path()));
        }
    }

    private void update(final Path worktree) {
        if (worktree == null) {
            return;
        }
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
                                        "The branch is up to date.",
                                        label(),
                                        JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        failure.getMessage(),
                                        label(),
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        },
                        SwingUtilities::invokeLater);
    }
}
