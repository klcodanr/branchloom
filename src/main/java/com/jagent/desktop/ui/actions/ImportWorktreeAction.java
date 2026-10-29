package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.git.Worktree;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.SessionCreationService;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.ViewController.ViewState;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.components.FormPanel;
import com.jagent.desktop.ui.components.SearchableList;
import com.jagent.desktop.ui.utils.ErrorMessages;
import com.jagent.desktop.ui.utils.SessionNames;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Starts importing a worktree into the selected project. */
public final class ImportWorktreeAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private static final String TITLE = "Import worktree";
    private static final String EMPTY_MESSAGE = "No worktrees are available to import.";

    @Inject
    public ImportWorktreeAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
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

        ProgressOperation.run(
                        TITLE,
                        "Loading worktrees...",
                        () -> {
                            try (GitRepository repository =
                                    GitRepository.open(Path.of(project.path()))) {
                                return repository.listWorktrees().stream()
                                        .map(Worktree::path)
                                        .toList();
                            }
                        })
                .thenAccept(paths -> importWorktree(projectId, project, paths))
                .exceptionally(
                        failure -> {
                            dialogService.errorMessage(
                                    ErrorMessages.deepestCause(
                                            failure, "Could not load worktrees."),
                                    TITLE);
                            return null;
                        });
    }

    private void importWorktree(
            final ProjectId projectId, final Project project, final List<Path> paths) {
        final var state = this.appState;
        final List<String> worktrees = availableWorktrees(project, paths);
        if (worktrees.isEmpty()) {
            dialogService.message(EMPTY_MESSAGE, TITLE, DialogService.MessageType.INFORMATION);
            return;
        }

        final SearchableList<String> worktree =
                new SearchableList<>(worktrees, "import-worktrees", "Search worktrees");
        worktree.setVisibleRowCount(Math.min(12, Math.max(4, worktrees.size())));
        final var worktreeForm = new FormPanel("Existing worktrees", worktree);
        if (dialogService.confirm(
                        worktreeForm,
                        TITLE,
                        DialogService.OptionType.OK_CANCEL,
                        DialogService.MessageType.PLAIN)
                != DialogService.ReturnType.OK) {
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
        final var state = this.appState;
        final Project project = state.projects().get(projectId);
        if (project == null) {
            return;
        }
        final SessionCreationService sessionCreationService = new SessionCreationService(appState);
        final Path normalizedPath = Path.of(worktreePath).toAbsolutePath().normalize();
        try {

            final var sessionId =
                    sessionCreationService.createSession(
                            projectId,
                            appState.projects().get(projectId),
                            sessionName,
                            normalizedPath);
            viewController.updateView(ViewId.SESSION, ViewState.session(projectId, sessionId));
        } catch (IOException exception) {
            dialogService.errorMessage(
                    ErrorMessages.deepestCause(exception, "Could not import worktree."), TITLE);
        }
    }
}
