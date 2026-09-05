package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.components.BulkSessionCreator;
import com.jagent.desktop.ui.dialogs.PasteSessionsDialog;
import com.jagent.desktop.ui.utils.GitUtils;
import java.util.List;

/** Creates one agent session for each pasted line. */
public final class PasteSessionsAction extends BaseAction {
    private final BulkSessionCreator sessionCreator;

    public PasteSessionsAction(final ActionContext actionContext) {
        super(actionContext);
        sessionCreator = new BulkSessionCreator(actionContext);
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
        return actionContext.appState().currentProjectId() != null;
    }

    @Override
    public void execute() {
        final AppState state = actionContext.appState();
        final ProjectId projectId = state.currentProjectId();
        final Project project = projectId == null ? null : state.projects().get(projectId);
        if (project == null) {
            return;
        }
        if (state.appSettings().agents().isEmpty()) {
            return;
        }
        new PasteSessionsDialog(actionContext, request -> create(projectId, project, request))
                .setVisible(true);
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

    private String template(final String basePrompt, final String line) {
        return basePrompt.isBlank() ? line : basePrompt.replace("{prompt}", line);
    }
}
