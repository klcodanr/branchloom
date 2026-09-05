package com.jagent.desktop.ui.actions;

import static com.jagent.desktop.ui.components.UiFactory.form;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.dialogs.ProgressOperation;
import com.jagent.desktop.ui.utils.GitUtils;
import com.jagent.desktop.ui.utils.SessionNames;
import java.awt.Cursor;
import java.io.InvalidObjectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/** Starts importing a branch into the selected project. */
public class ImportBranchAction extends BaseAction {
    private static final String TITLE = "Import branch";
    private static final Logger LOG = Logger.getLogger(ImportBranchAction.class.getName());

    protected record BranchChoice(String displayName, String ref, boolean remote) {
        @Override
        public String toString() {
            return displayName;
        }

        protected String localName() {
            if (!remote) {
                return ref;
            }
            final int separator = ref.indexOf('/');
            return separator < 0 ? ref : ref.substring(separator + 1);
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

        final ProgressOperation progress =
                ProgressOperation.start(actionContext.window(), TITLE, "Loading branches...");
        actionContext.window().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new Git()
                .listBranches(project)
                .whenCompleteAsync(
                        (branches, failure) -> {
                            if (failure != null) {
                                progress.close();
                                actionContext.window().setCursor(Cursor.getDefaultCursor());
                                LOG.log(Level.SEVERE, "Git query", failure);
                                return;
                            }
                            progress.close();
                            actionContext.window().setCursor(Cursor.getDefaultCursor());
                            final List<BranchChoice> choices =
                                    branches.stream()
                                            .map(
                                                    branch ->
                                                            new BranchChoice(
                                                                    branch.name(),
                                                                    branch.name(),
                                                                    branch.remote()))
                                            .distinct()
                                            .sorted(Comparator.comparing(BranchChoice::displayName))
                                            .toList();
                            if (choices.isEmpty()) {
                                LOG.severe("Import branch: No branches found.");
                                return;
                            }

                            final JList<BranchChoice> branch =
                                    new JList<>(choices.toArray(new BranchChoice[0]));
                            branch.setName("import-branches");
                            branch.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                            branch.setVisibleRowCount(Math.min(12, Math.max(4, choices.size())));
                            final JPanel branchForm =
                                    form("Existing branches", new JScrollPane(branch));
                            if (JOptionPane.showConfirmDialog(
                                            actionContext.window(),
                                            branchForm,
                                            TITLE,
                                            JOptionPane.OK_CANCEL_OPTION)
                                    != JOptionPane.OK_OPTION) {
                                return;
                            }

                            final List<BranchChoice> selected = branch.getSelectedValuesList();
                            if (selected.isEmpty()) {
                                return;
                            }
                            final Set<String> names =
                                    SessionNames.existing(actionContext.appState(), project);
                            for (final BranchChoice choice : selected) {
                                final String name = SessionNames.unique(choice.localName(), names);
                                names.add(name.toLowerCase(Locale.ROOT));
                                importBranch(actionContext, projectId, choice, name);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    protected static String uniqueName(final String base, final Set<String> names) {
        return SessionNames.unique(base, names);
    }

    public static void importBranch(
            final ActionContext actionContext,
            final ProjectId projectId,
            final BranchChoice branch,
            final String sessionName) {
        final Git git = new Git();
        final String description = "Imported branch " + branch.displayName();
        importWorktree(
                actionContext,
                projectId,
                sessionName,
                description,
                worktree -> {
                    final Project project = actionContext.appState().projects().get(projectId);
                    return git.addWorktree(
                            project, branch.ref(), worktree, branch.remote(), branch.localName());
                });
    }

    public static void importPullRequest(
            final ActionContext actionContext, final PullRequest request) {
        final Git git = new Git();
        final ProjectId projectId = request.projectId();
        final String sessionName = request.title();
        final String description = "Imported pull request #" + request.number();
        importWorktree(
                actionContext,
                projectId,
                sessionName,
                description,
                worktree -> {
                    final Project project = actionContext.appState().projects().get(projectId);
                    final String branch = "pr-" + request.number();
                    return git.fetchPullRequest(project, request.number())
                            .thenCompose(
                                    ignored ->
                                            git.addWorktree(
                                                    project, branch, worktree, true, branch))
                            .whenCompleteAsync(
                                    (ignored, failure) -> {
                                        if (failure != null) {
                                            LOG.log(Level.SEVERE, "Import pull request", failure);
                                        }
                                    },
                                    SwingUtilities::invokeLater);
                });
    }

    private static void importWorktree(
            final ActionContext actionContext,
            final ProjectId projectId,
            final String sessionName,
            final String description,
            final Function<Path, CompletableFuture<Void>> createWorktree) {
        final AppState state = actionContext.appState();
        final Project project = state.projects().get(projectId);
        if (project == null) {
            return;
        }
        if (project.sessionIds().stream()
                .map(state.sessions()::get)
                .anyMatch(
                        session ->
                                session != null && session.name().equalsIgnoreCase(sessionName))) {
            LOG.severe("Import worktree: A session with that name already exists.");
            return;
        }

        final Session draft = new Session(projectId, sessionName, description, "", "");
        final Path worktree =
                Path.of(
                        Template.resolvePath(
                                Template.expand(
                                        Template.worktree(project, state.appSettings()),
                                        project,
                                        draft,
                                        false),
                                project));
        if (GitUtils.isWorktreeRegistered(state.sessions(), worktree) || Files.exists(worktree)) {
            LOG.severe("Import worktree: The worktree path is already in use.");
            return;
        }

        final ProgressOperation progress =
                ProgressOperation.start(actionContext.window(), TITLE, "Importing worktree...");
        actionContext.window().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        createWorktree
                .apply(worktree)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            progress.close();
                            actionContext.window().setCursor(Cursor.getDefaultCursor());
                            if (failure != null) {
                                LOG.log(Level.SEVERE, TITLE, failure);
                                return;
                            }
                            final Session session =
                                    new Session(
                                            projectId,
                                            sessionName,
                                            description,
                                            "",
                                            worktree.toString());
                            try {
                                final var sessionId = state.addSession(projectId, session);
                                actionContext
                                        .viewCoordinator()
                                        .updateView(
                                                ViewId.SESSION,
                                                ViewState.session(projectId, sessionId));
                            } catch (InvalidObjectException exception) {
                                LOG.log(Level.SEVERE, TITLE, exception);
                            }
                        },
                        SwingUtilities::invokeLater);
    }
}
