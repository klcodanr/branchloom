package com.jagent.desktop.ui.actions;

import static com.jagent.desktop.ui.components.UiFactory.form;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.SessionCreationService;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.components.SearchableList;
import com.jagent.desktop.ui.utils.ErrorDialogs;
import com.jagent.desktop.ui.utils.ErrorMessages;
import com.jagent.desktop.ui.utils.SessionNames;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.JOptionPane;

/** Starts importing a worktree into the selected project. */
public final class ImportWorktreeAction extends BaseAction {
    private static final String TITLE = "Import worktree";
    private static final String EMPTY_MESSAGE = "No worktrees are available to import.";

    public ImportWorktreeAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "import-worktree";
    }

    @Override
    public String label() {
        return TITLE;
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

        ProgressOperation.run(
                        actionContext,
                        TITLE,
                        "Loading worktrees...",
                        () -> {
                            try (GitRepository repository =
                                    GitRepository.open(Path.of(project.path()))) {
                                return repository.listWorktrees().stream()
                                        .map(com.jagent.desktop.models.git.Worktree::path)
                                        .toList();
                            }
                        })
                .thenAccept(paths -> importWorktree(projectId, project, paths))
                .exceptionally(
                        failure -> {
                            ErrorDialogs.show(
                                    actionContext.window(),
                                    TITLE,
                                    ErrorMessages.deepestCause(
                                            failure, "Could not load worktrees."));
                            return null;
                        });
    }

    private void importWorktree(
            final ProjectId projectId, final Project project, final List<Path> paths) {
        final var state = this.actionContext.appState();
        final List<String> worktrees = availableWorktrees(project, paths);
        if (worktrees.isEmpty()) {
            JOptionPane.showMessageDialog(
                    actionContext.window(), EMPTY_MESSAGE, TITLE, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        final SearchableList<String> worktree =
                new SearchableList<>(worktrees, "import-worktrees", "Search worktrees");
        worktree.setVisibleRowCount(Math.min(12, Math.max(4, worktrees.size())));
        final var worktreeForm = form("Existing worktrees", worktree);
        if (JOptionPane.showConfirmDialog(
                        actionContext.window(), worktreeForm, TITLE, JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        final List<String> selected = worktree.selectedValues();
        if (selected.isEmpty()) {
            return;
        }
        final Set<String> names = SessionNames.existing(state, project);
        for (final String path : selected) {
            final Path fileName = Path.of(path).getFileName();
            final String baseName = fileName == null ? path : fileName.toString();
            final String sessionName = SessionNames.unique(baseName, names);
            names.add(sessionName.toLowerCase(Locale.ROOT));
            addSession(projectId, sessionName, path);
        }
    }

    private List<String> availableWorktrees(final Project project, final List<Path> paths) {
        final Path repository = Path.of(project.path()).toAbsolutePath().normalize();
        return paths.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> !path.equals(repository))
                .map(Path::toString)
                .toList();
    }

    private void addSession(
            final ProjectId projectId, final String sessionName, final String worktreePath) {
        final AppState state = this.actionContext.appState();
        final Project project = state.projects().get(projectId);
        if (project == null) {
            return;
        }
        final SessionCreationService sessionCreationService = new SessionCreationService(state);
        final Path normalizedPath = Path.of(worktreePath).toAbsolutePath().normalize();
        try {
            sessionCreationService.checkCreateSession(project, sessionName, normalizedPath);
            final var sessionId =
                    sessionCreationService.createSession(
                            projectId,
                            project,
                            sessionName,
                            "Imported worktree",
                            "",
                            normalizedPath);
            actionContext
                    .viewCoordinator()
                    .updateView(ViewId.SESSION, ViewState.session(projectId, sessionId));
        } catch (IOException exception) {
            ErrorDialogs.show(
                    actionContext.window(),
                    TITLE,
                    ErrorMessages.deepestCause(exception, "Could not import worktree."));
        }
    }
}
