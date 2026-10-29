package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.git.Branch;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.SessionCreationService;
import com.jagent.desktop.services.SessionCreationService.SessionDetails;
import com.jagent.desktop.services.SessionCreationService.WorktreeRequest;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.ViewController.ViewState;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.components.FormPanel;
import com.jagent.desktop.ui.components.SearchableList;
import com.jagent.desktop.ui.utils.ErrorMessages;
import com.jagent.desktop.ui.utils.SessionNames;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Starts importing a branch into the selected project. */
public final class ImportBranchAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private static final String TITLE = "Import branch";
    private static final String EMPTY_MESSAGE = "No branches are available to import.";

    private record BranchChoice(String displayName, String ref, boolean remote, String localName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    @Inject
    public ImportBranchAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
    }

    @Override
    public String id() {
        return "import-branch";
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
                        "Loading branches...",
                        () -> {
                            try (GitRepository repository =
                                    GitRepository.open(Path.of(project.path()))) {
                                return repository.listBranches();
                            }
                        })
                .thenAccept(
                        branches -> {
                            final List<BranchChoice> choices =
                                    branches.stream()
                                            .map(ImportBranchAction::choice)
                                            .sorted(Comparator.comparing(BranchChoice::displayName))
                                            .toList();
                            if (choices.isEmpty()) {
                                dialogService.message(
                                        EMPTY_MESSAGE,
                                        TITLE,
                                        DialogService.MessageType.INFORMATION);
                                return;
                            }

                            final SearchableList<BranchChoice> branchList =
                                    new SearchableList<>(
                                            choices, "import-branches", "Search branches");
                            branchList.setVisibleRowCount(
                                    Math.min(12, Math.max(4, choices.size())));
                            final var branchForm = new FormPanel("Existing branches", branchList);
                            if (dialogService.confirm(
                                            branchForm,
                                            TITLE,
                                            DialogService.OptionType.OK_CANCEL,
                                            DialogService.MessageType.PLAIN)
                                    != DialogService.ReturnType.OK) {
                                return;
                            }

                            final List<BranchChoice> selected = branchList.selectedValues();
                            if (selected.isEmpty()) {
                                return;
                            }
                            final Set<String> names = SessionNames.existing(appState, project);
                            for (final BranchChoice selectedBranch : selected) {
                                final String name =
                                        SessionNames.unique(selectedBranch.localName(), names);
                                names.add(name.toLowerCase(Locale.ROOT));
                                importBranch(
                                        viewController,
                                        appState,
                                        dialogService,
                                        projectId,
                                        selectedBranch,
                                        name);
                            }
                        })
                .exceptionally(
                        failure -> {
                            dialogService.message(
                                    EMPTY_MESSAGE,
                                    ErrorMessages.deepestCause(failure, "Could not load branches."),
                                    DialogService.MessageType.ERROR);
                            return null;
                        });
    }

    public static void importPullRequest(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final PullRequest request) {
        final var state = appState;
        final ProjectId projectId = request.projectId();
        final Project project = state.projects().get(projectId);
        if (project == null) {
            return;
        }
        final GitHub gitHub = GitHub.forProject(state, projectId);

        ProgressOperation.run(
                        TITLE,
                        "Loading pull request branch...",
                        () -> {
                            final String headBranch =
                                    gitHub.getPullRequest(request.number()).headBranch();
                            if (headBranch.isBlank()) {
                                throw new IOException("Could not determine pull request branch.");
                            }
                            return headBranch;
                        })
                .thenAccept(
                        headBranch -> {
                            final String sessionName =
                                    request.title().isBlank()
                                            ? "pr-" + request.number()
                                            : request.title();
                            final String branchRef = "origin/" + headBranch;
                            final BranchChoice branch =
                                    new BranchChoice(
                                            branchRef, branchRef, true, "pr-" + request.number());
                            importBranch(
                                    viewController,
                                    appState,
                                    dialogService,
                                    projectId,
                                    branch,
                                    sessionName);
                        })
                .exceptionally(
                        failure -> {
                            dialogService.message(
                                    TITLE,
                                    ErrorMessages.deepestCause(
                                            failure, "Could not load pull request branch."),
                                    DialogService.MessageType.ERROR);

                            return null;
                        });
    }

    private static BranchChoice choice(final Branch branch) {
        if (branch.remote()) {
            final String remoteRef = branch.localName();
            final int separator = remoteRef.indexOf('/');
            final String localName =
                    separator >= 0 ? remoteRef.substring(separator + 1) : remoteRef;
            return new BranchChoice(remoteRef, remoteRef, true, localName);
        }
        final String local = branch.displayName();
        return new BranchChoice(local, local, false, local);
    }

    private static void importBranch(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final ProjectId projectId,
            final BranchChoice branch,
            final String sessionName) {
        final var state = appState;
        final Project project = state.projects().get(projectId);
        if (project == null) {
            return;
        }

        final SessionCreationService sessionCreator = new SessionCreationService(appState);

        ProgressOperation.run(
                        TITLE,
                        "Importing worktree...",
                        () -> {
                            final SessionDetails sessionDetails =
                                    new SessionDetails(
                                            sessionName,
                                            "Imported branch " + branch.displayName(),
                                            "");

                            final Path worktree =
                                    Path.of(
                                            Template.resolvePath(
                                                    Template.expand(
                                                            Template.worktree(
                                                                    project,
                                                                    appState.appSettings()),
                                                            project,
                                                            Session.create(
                                                                    project.id(),
                                                                    sessionName,
                                                                    "",
                                                                    "",
                                                                    ""),
                                                            false),
                                                    project));
                            final WorktreeRequest worktreeRequest =
                                    new WorktreeRequest(worktree, branch.ref(), branch.localName());
                            if (branch.remote()) {
                                try (GitRepository repository =
                                        GitRepository.open(Path.of(project.path()))) {
                                    repository.fetchRemoteRef(worktreeRequest.sourceRef());
                                }
                            }
                            return sessionCreator.createWorktreeAndSession(
                                    projectId, project, sessionDetails, worktreeRequest);
                        })
                .thenAccept(
                        sessionId -> {
                            viewController.updateView(
                                    ViewId.SESSION, ViewState.session(projectId, sessionId));
                        })
                .exceptionally(
                        failure -> {
                            dialogService.message(
                                    TITLE,
                                    ErrorMessages.deepestCause(failure, "Could not import branch."),
                                    DialogService.MessageType.ERROR);
                            return null;
                        });
    }
}
