package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.ui.components.BulkSessionCreator;
import com.jagent.desktop.ui.dialogs.BulkSessionDialog;
import com.jagent.desktop.ui.utils.ErrorMessages;
import com.jagent.desktop.ui.utils.GitUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CompletionException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class BulkCreateSessionsAction extends BaseAction {
    private final BulkSessionCreator sessionCreator;

    public BulkCreateSessionsAction(final ActionContext actionContext) {
        super(actionContext);
        sessionCreator = new BulkSessionCreator(actionContext);
    }

    @Override
    public String id() {
        return "bulk-new-sessions";
    }

    @Override
    public String label() {
        return "Start sessions from GitHub issues";
    }

    @Override
    public boolean enabled() {
        return actionContext.appState().currentProjectId() != null;
    }

    @Override
    public void execute() {
        final AppState state = actionContext.appState();
        final ProjectId projectId = state.currentProjectId();
        if (projectId == null || state.projects().get(projectId) == null) {
            return;
        }
        if (state.appSettings().agents().isEmpty()) {
            showError("Configure an agent in Settings before starting a session.");
            return;
        }
        final Project project = state.projects().get(projectId);
        BackgroundTasks.submit(
                        "GitHub",
                        "issues",
                        () -> {
                            try {
                                return GitHub.loadIssuesForProject(project);
                            } catch (IOException exception) {
                                throw new UncheckedIOException(exception);
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new CompletionException(exception);
                            }
                        })
                .whenCompleteAsync(
                        (issues, failure) -> {
                            if (failure != null) {
                                showError(
                                        ErrorMessages.deepestCause(
                                                failure, "Could not load GitHub issues."));
                            } else if (issues.isEmpty()) {
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        "No open GitHub issues were found.",
                                        "Bulk agent sessions",
                                        JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                new BulkSessionDialog(
                                                actionContext,
                                                issues,
                                                request -> create(projectId, project, request))
                                        .setVisible(true);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    protected static List<BulkSessionCreator.Candidate> candidates(
            final List<GitHub.Issue> issues) {
        return issues.stream()
                .map(
                        issue ->
                                new BulkSessionCreator.Candidate(
                                        "issue-"
                                                + issue.number()
                                                + "-"
                                                + GitUtils.toBranchSlug(issue.title()),
                                        "#" + issue.number(),
                                        "Work on GitHub issue #"
                                                + issue.number()
                                                + ": "
                                                + issue.title()
                                                + "\n\n"
                                                + issue.body()
                                                + "\n\nIssue: "
                                                + issue.url()))
                .toList();
    }

    protected static String message(final Throwable failure, final String fallback) {
        return ErrorMessages.deepestCause(failure, fallback);
    }

    private void create(
            final ProjectId projectId,
            final Project project,
            final BulkSessionDialog.Request request) {
        final List<BulkSessionCreator.Candidate> candidates = candidates(request.issues());
        sessionCreator.create(projectId, project, request.agent(), candidates, "Bulk sessions");
    }

    private void showError(final String message) {
        JOptionPane.showMessageDialog(
                actionContext.window(), message, "Bulk agent sessions", JOptionPane.ERROR_MESSAGE);
    }
}
