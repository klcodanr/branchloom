package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.github.Issue;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BulkSessionCreator;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.dialogs.BulkSessionDialog;
import com.jagent.desktop.ui.utils.ErrorMessages;
import com.jagent.desktop.ui.utils.GitUtils;
import java.util.List;

public class BulkCreateSessionsAction implements Action {
    private final ViewController viewController;
    private final AppState appState;

    private final DialogService dialogService;
    private static final String TITLE = "Bulk agent sessions";
    private final BulkSessionCreator sessionCreator;

    @Inject
    public BulkCreateSessionsAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final BulkSessionCreator sessionCreator) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
        this.sessionCreator = sessionCreator;
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
        return viewController.currentProjectId() != null;
    }

    @Override
    public void execute() {
        final var state = appState;
        final ProjectId projectId = viewController.currentProjectId();
        if (projectId == null || state.projects().get(projectId) == null) {
            return;
        }
        if (state.appSettings().agents().isEmpty()) {
            dialogService.errorMessage(
                    "Configure an agent in Settings before starting a session.", TITLE);
            return;
        }
        final Project project = state.projects().get(projectId);
        final GitHub gitHub = GitHub.forProject(state, projectId);
        BackgroundOperations.submit("GitHub", "issues", () -> gitHub.listIssues(null))
                .thenAcceptAsync(
                        issues -> {
                            if (issues.isEmpty()) {
                                dialogService.message(
                                        "No open GitHub issues were found.",
                                        TITLE,
                                        DialogService.MessageType.INFORMATION);
                                return;
                            }
                            dialogService.show(
                                    TITLE,
                                    new BulkSessionDialog(
                                            appState,
                                            dialogService,
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
                                            }),
                                    "Create sessions");
                        })
                .exceptionally(
                        failure -> {
                            dialogService.errorMessage(
                                    ErrorMessages.deepestCause(
                                            failure, "Could not load GitHub issues."),
                                    "Bulk agent sessions");
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
