package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Starts the selected session removal workflow. */
public class RemoveSessionAction extends BaseAction {
    private static final String TITLE = "Remove session";
    private static final Logger LOG = Logger.getLogger(RemoveSessionAction.class.getName());
    private final Git git = new Git();

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
            removeWorktree(state, sessionId, projectId, project, session);
            return;
        }

        removeSession(state, sessionId, projectId);
    }

    protected int removalChoice(final Session session) {
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

    private void removeWorktree(
            final AppState state,
            final SessionId sessionId,
            final ProjectId projectId,
            final Project project,
            final Session session) {
        final var job = actionContext.viewCoordinator().backgroundJobs().start(TITLE);
        job.update("Checking worktree...");
        BackgroundTasks.submit("Operations", TITLE, () -> checkWorktree(project, session))
                .whenCompleteAsync(
                        (check, failure) ->
                                handleWorktreeCheck(
                                        state, sessionId, projectId, project, session, job, check,
                                        failure),
                        SwingUtilities::invokeLater);
    }

    private WorktreeCheck checkWorktree(final Project project, final Session session) {
        try {
            final Path worktree = git.validateWorktreeDeletion(project, session);
            return new WorktreeCheck(worktree, !Git.status(worktree).isBlank());
        } catch (IOException exception) {
            throw new CompletionException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        }
    }

    private void handleWorktreeCheck(
            final AppState state,
            final SessionId sessionId,
            final ProjectId projectId,
            final Project project,
            final Session session,
            final Handle job,
            final WorktreeCheck check,
            final Throwable failure) {
        if (failure != null) {
            failRemoval(job, failure);
            return;
        }
        if (check.hasChanges() && !confirmWorktreeDeletion(session)) {
            job.complete();
            return;
        }
        job.update("Removing worktree...");
        BackgroundTasks.submit("Operations", TITLE, () -> deleteWorktree(project, check.path()))
                .whenCompleteAsync(
                        (ignored, removalFailure) ->
                                finishWorktreeRemoval(
                                        state, sessionId, projectId, job, removalFailure),
                        SwingUtilities::invokeLater);
    }

    private void deleteWorktree(final Project project, final Path worktree) {
        try {
            git.deleteWorktree(project, worktree);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private void finishWorktreeRemoval(
            final AppState state,
            final SessionId sessionId,
            final ProjectId projectId,
            final Handle job,
            final Throwable failure) {
        if (failure == null) {
            job.complete();
            removeSession(state, sessionId, projectId);
        } else {
            failRemoval(job, failure);
        }
    }

    protected void failRemoval(final Handle job, final Throwable failure) {
        final Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
        final String message =
                cause.getMessage() == null ? "Could not remove worktree." : cause.getMessage();
        job.fail(message);
        LOG.log(Level.SEVERE, "Could not remove worktree", cause);
        JOptionPane.showMessageDialog(
                actionContext.window(), message, TITLE, JOptionPane.ERROR_MESSAGE);
    }

    private void removeSession(
            final AppState state, final SessionId sessionId, final ProjectId projectId) {
        state.removeSession(sessionId);
        actionContext.viewCoordinator().updateView(ViewId.PROJECT, ViewState.project(projectId));
    }
}
