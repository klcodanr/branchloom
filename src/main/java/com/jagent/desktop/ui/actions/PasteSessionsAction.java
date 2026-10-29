package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BulkSessionCreator;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.dialogs.PasteSessionsDialog;
import com.jagent.desktop.ui.utils.GitUtils;
import java.util.List;

/** Creates one agent session for each pasted line. */
public final class PasteSessionsAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private final BulkSessionCreator sessionCreator;

    @Inject
    public PasteSessionsAction(
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
        return "paste-sessions";
    }

    @Override
    public String label() {
        return "Start sessions from pasted lines";
    }

    @Override
    public boolean enabled() {
        return viewController.currentProjectId() != null;
    }

    @Override
    public void execute() {
        final var state = appState;
        final ProjectId projectId = viewController.currentProjectId();
        final Project project = projectId == null ? null : state.projects().get(projectId);
        if (project == null) {
            return;
        }
        if (state.appSettings().agents().isEmpty()) {
            return;
        }
        dialogService.show(
                "Start sessions from pasted lines",
                new PasteSessionsDialog(
                        appState, dialogService, request -> create(projectId, project, request)),
                "Create sessions");
    }

    private void create(
            final ProjectId projectId,
            final Project project,
            final PasteSessionsDialog.Request request) {
        final List<BulkSessionCreator.Candidate> candidates =
                request.lines().stream()
                        .map(
                                line ->
                                        new BulkSessionCreator.Candidate(
                                                GitUtils.toBranchSlug(line),
                                                line,
                                                template(request.basePrompt(), line)))
                        .toList();
        sessionCreator.create(
                projectId, project, request.agent(), candidates, "Sessions from pasted lines");
    }

    public static String template(final String basePrompt, final String line) {
        return basePrompt.isBlank() ? line : basePrompt.replace("{prompt}", line);
    }
}
