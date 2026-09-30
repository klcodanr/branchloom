package com.jagent.desktop.ui.actions;

import static com.jagent.desktop.ui.components.UiFactory.form;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.git.Branch;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.SessionCreationService;
import com.jagent.desktop.services.SessionCreationService.SessionDetails;
import com.jagent.desktop.services.SessionCreationService.WorktreeRequest;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.components.SearchableList;
import com.jagent.desktop.ui.utils.ErrorDialogs;
import com.jagent.desktop.ui.utils.ErrorMessages;
import com.jagent.desktop.ui.utils.SessionNames;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.JOptionPane;

/** Starts importing a branch into the selected project. */
public final class ImportBranchAction extends BaseAction {
    private static final String TITLE = "Import branch";
    private static final String EMPTY_MESSAGE = "No branches are available to import.";

    private record BranchChoice(String displayName, String ref, boolean remote, String localName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    public ImportBranchAction(final ActionContext actionContext) {
        super(actionContext);
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
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        EMPTY_MESSAGE,
                                        TITLE,
                                        JOptionPane.INFORMATION_MESSAGE);
                                return;
                            }

                            final SearchableList<BranchChoice> branchList =
                                    new SearchableList<>(
                                            choices, "import-branches", "Search branches");
                            branchList.setVisibleRowCount(
                                    Math.min(12, Math.max(4, choices.size())));
                            final var branchForm = form("Existing branches", branchList);
                            if (JOptionPane.showConfirmDialog(
                                            actionContext.window(),
                                            branchForm,
                                            TITLE,
                                            JOptionPane.OK_CANCEL_OPTION)
                                    != JOptionPane.OK_OPTION) {
                                return;
                            }

                            final List<BranchChoice> selected = branchList.selectedValues();
                            if (selected.isEmpty()) {
                                return;
                            }
                            final Set<String> names =
                                    SessionNames.existing(actionContext.appState(), project);
                            for (final BranchChoice selectedBranch : selected) {
                                final String name =
                                        SessionNames.unique(selectedBranch.localName(), names);
                                names.add(name.toLowerCase(Locale.ROOT));
                                importBranch(actionContext, projectId, selectedBranch, name);
                            }
                        })
                .exceptionally(
                        failure -> {
                            ErrorDialogs.show(
                                    actionContext.window(),
                                    TITLE,
                                    ErrorMessages.deepestCause(
                                            failure, "Could not load branches."));
                            return null;
                        });
    }

    public static void importPullRequest(
            final ActionContext actionContext, final PullRequest request) {
        final AppState state = actionContext.appState();
        final ProjectId projectId = request.projectId();
        final Project project = state.projects().get(projectId);
        if (project == null) {
            return;
        }

        ProgressOperation.run(
                        actionContext,
                        TITLE,
                        "Loading pull request branch...",
                        () -> {
                            final String headBranch =
                                    GitHub.nativePullRequest(
                                                    project,
                                                    request.number(),
                                                    state.githubConnections())
                                            .getHead()
                                            .getRef();
                            if (headBranch == null || headBranch.isBlank()) {
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
                            importBranch(actionContext, projectId, branch, sessionName);
                        })
                .exceptionally(
                        failure -> {
                            ErrorDialogs.show(
                                    actionContext.window(),
                                    TITLE,
                                    ErrorMessages.deepestCause(
                                            failure, "Could not load pull request branch."));
                            return null;
                        });
    }

    private static BranchChoice choice(final Branch branch) {
        final String name = branch.name();
        if (name.startsWith("refs/remotes/")) {
            final String remoteRef = name.substring("refs/remotes/".length());
            final int separator = remoteRef.indexOf('/');
            final String localName =
                    separator >= 0 ? remoteRef.substring(separator + 1) : remoteRef;
            return new BranchChoice(remoteRef, remoteRef, true, localName);
        }
        if (name.startsWith("refs/heads/")) {
            final String local = name.substring("refs/heads/".length());
            return new BranchChoice(local, local, false, local);
        }
        return new BranchChoice(name, name, false, name);
    }

    private static void importBranch(
            final ActionContext actionContext,
            final ProjectId projectId,
            final BranchChoice branch,
            final String sessionName) {
        final AppState state = actionContext.appState();
        final Project project = state.projects().get(projectId);
        if (project == null) {
            return;
        }

        final Path worktree;
        final SessionCreationService sessionCreator = new SessionCreationService(state);
        try {
            worktree =
                    sessionCreator.prepareWorktreePath(
                            projectId,
                            project,
                            sessionName,
                            "Imported branch " + branch.displayName(),
                            "");
        } catch (IOException exception) {
            ErrorDialogs.show(
                    actionContext.window(),
                    TITLE,
                    ErrorMessages.deepestCause(exception, "Could not prepare worktree."));
            return;
        }

        ProgressOperation.run(
                        actionContext,
                        TITLE,
                        "Importing worktree...",
                        () -> {
                            final SessionDetails sessionDetails =
                                    new SessionDetails(
                                            sessionName,
                                            "Imported branch " + branch.displayName(),
                                            "");
                            final WorktreeRequest worktreeRequest =
                                    new WorktreeRequest(worktree, branch.ref(), branch.localName());
                            if (branch.remote()) {
                                sessionCreator.checkFetchBranchCreateWorktreeAndSession(
                                        project, sessionDetails, worktreeRequest);
                                return sessionCreator.fetchBranchCreateWorktreeAndSession(
                                        projectId, project, sessionDetails, worktreeRequest);
                            }
                            sessionCreator.checkCreateWorktreeAndSession(
                                    project,
                                    sessionDetails,
                                    new WorktreeRequest(worktree, branch.ref(), null));
                            return sessionCreator.createWorktreeAndSession(
                                    projectId,
                                    project,
                                    sessionDetails,
                                    new WorktreeRequest(worktree, branch.ref(), null));
                        })
                .thenAccept(
                        sessionId -> {
                            actionContext
                                    .viewCoordinator()
                                    .updateView(
                                            ViewId.SESSION,
                                            ViewState.session(projectId, sessionId));
                        })
                .exceptionally(
                        failure -> {
                            ErrorDialogs.show(
                                    actionContext.window(),
                                    TITLE,
                                    ErrorMessages.deepestCause(
                                            failure, "Could not import branch."));
                            return null;
                        });
    }
}
