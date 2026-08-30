package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.dialogs.ProgressOperation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/** Starts the selected session removal workflow. */
public final class RemoveSessionAction extends BaseAction {
    private static final String TITLE = "Remove session";
    private static final Logger LOG = Logger.getLogger(RemoveSessionAction.class.getName());
    private final Git git = new Git();

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
        return actionContext.appState().currentSessionId() != null;
    }

    @Override
    public void execute() {
        final AppState state = actionContext.appState();
        final SessionId sessionId = state.currentSessionId();
        final Session session = state.currentSession();
        final ProjectId projectId = session == null ? null : session.projectId();
        final Project project = projectId == null ? null : state.projects().get(projectId);
        if (session == null || project == null) {
            return;
        }

        final int choice = removalChoice(session);
        if (choice < 0 || choice == 2) {
            return;
        }

        if (choice == 1) {
            if (!confirmWorktreeDeletion(session)) {
                return;
            }
            removeWorktree(state, sessionId, projectId, project, session);
            return;
        }

        removeSession(state, sessionId, projectId);
    }

    private int removalChoice(final Session session) {
        final Object[] options = {TITLE, "Remove session and worktree", "Cancel"};
        return JOptionPane.showOptionDialog(
                actionContext.window(),
                "Remove "
                        + session.name()
                        + "?\nThe session record can be removed while keeping its worktree.",
                TITLE,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[2]);
    }

    private boolean confirmWorktreeDeletion(final Session session) {
        return JOptionPane.showConfirmDialog(
                        actionContext.window(),
                        "Permanently delete this worktree and all uncommitted files?\n"
                                + session.worktreePath(),
                        "Confirm worktree deletion",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    private void removeWorktree(
            final AppState state,
            final SessionId sessionId,
            final ProjectId projectId,
            final Project project,
            final Session session) {
        final Path worktree;
        try {
            worktree = git.validateWorktreeDeletion(project, session);
        } catch (IOException | RuntimeException exception) {
            LOG.log(Level.SEVERE, "Could not remove worktree", exception);
            JOptionPane.showMessageDialog(
                    actionContext.window(),
                    exception.getMessage(),
                    TITLE,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        ProgressOperation.run(
                actionContext.window(),
                TITLE,
                "Removing worktree...",
                () -> {
                    git.deleteWorktree(project, worktree);
                    return null;
                },
                () -> removeSession(state, sessionId, projectId),
                failure -> {
                    LOG.log(Level.SEVERE, "Could not remove worktree", failure);
                    JOptionPane.showMessageDialog(
                            actionContext.window(),
                            failure.getMessage(),
                            TITLE,
                            JOptionPane.ERROR_MESSAGE);
                });
    }

    private void removeSession(
            final AppState state, final SessionId sessionId, final ProjectId projectId) {
        state.removeSession(sessionId);
        actionContext.viewCoordinator().updateView(ViewId.PROJECT, ViewState.project(projectId));
    }
}
