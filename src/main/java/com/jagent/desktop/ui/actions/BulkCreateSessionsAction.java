package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.github.Issue;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.components.BulkSessionCreator;
import com.jagent.desktop.ui.dialogs.BulkSessionDialog;
import com.jagent.desktop.ui.utils.ErrorDialogs;
import com.jagent.desktop.ui.utils.ErrorMessages;
import com.jagent.desktop.ui.utils.GitUtils;
import java.util.List;
import javax.swing.JOptionPane;

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
            ErrorDialogs.show(
                    actionContext.window(),
                    "Bulk agent sessions",
                    "Configure an agent in Settings before starting a session.");
            return;
        }
        final Project project = state.projects().get(projectId);
        final GitHub gitHub = GitHub.forProject(state, projectId);
        BackgroundOperations.submit("GitHub", "issues", gitHub::listIssues)
                .thenAcceptAsync(
                        issues -> {
                            if (issues.isEmpty()) {
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        "No open GitHub issues were found.",
                                        "Bulk agent sessions",
                                        JOptionPane.INFORMATION_MESSAGE);
                                return;
                            }
                            new BulkSessionDialog(
                                            actionContext,
                                            issues,
                                            request -> {
                                                final List<BulkSessionCreator.Candidate>
                                                        candidates = candidates(request.issues());
                                                sessionCreator.create(
                                                        projectId,
                                                        project,
                                                        request.agent(),
                                                        candidates,
                                                        "Bulk sessions");
                                            })
                                    .setVisible(true);
                        })
                .exceptionally(
                        failure -> {
                            ErrorDialogs.show(
                                    actionContext.window(),
                                    "Bulk agent sessions",
                                    ErrorMessages.deepestCause(
                                            failure, "Could not load GitHub issues."));
                            return null;
                        });
    }

    protected static List<BulkSessionCreator.Candidate> candidates(final List<Issue> issues) {
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
}
