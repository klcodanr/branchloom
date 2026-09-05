package com.jagent.desktop.ui.actions;

import static com.jagent.desktop.ui.components.UiFactory.form;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.dialogs.ProgressOperation;
import com.jagent.desktop.ui.utils.GitUtils;
import com.jagent.desktop.ui.utils.SessionNames;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/** Starts importing a worktree into the selected project. */
public final class ImportWorktreeAction extends BaseAction {
    private static final String TITLE = "Import worktree";
    private static final Logger LOG = Logger.getLogger(ImportWorktreeAction.class.getName());
    private final Git git = new Git();

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

        final ProgressOperation progress =
                ProgressOperation.start(actionContext.window(), TITLE, "Loading worktrees...");
        git.listWorktrees(project)
                .whenCompleteAsync(
                        (paths, failure) -> {
                            progress.close();
                            if (failure != null) {
                                LOG.log(Level.SEVERE, "Git query", failure);
                            } else {
                                importWorktree(projectId, project, paths);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private void importWorktree(
            final ProjectId projectId, final Project project, final List<Path> paths) {
        final var state = this.actionContext.appState();
        final List<String> worktrees = availableWorktrees(project, state, paths);
        if (worktrees.isEmpty()) {
            LOG.severe("Import worktree: No linked worktrees found.");
            return;
        }

        final JList<String> worktree = new JList<>(worktrees.toArray(new String[0]));
        worktree.setName("import-worktrees");
        worktree.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        worktree.setVisibleRowCount(Math.min(12, Math.max(4, worktrees.size())));
        final JPanel worktreeForm = form("Existing worktrees", new JScrollPane(worktree));
        if (JOptionPane.showConfirmDialog(
                        actionContext.window(), worktreeForm, TITLE, JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        final List<String> selected = worktree.getSelectedValuesList();
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

    private List<String> availableWorktrees(
            final Project project, final AppState state, final List<Path> paths) {
        final Path repository = Path.of(project.path()).toAbsolutePath().normalize();
        return paths.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> !path.equals(repository))
                .filter(path -> !GitUtils.isWorktreeRegistered(state.sessions(), path))
                .map(Path::toString)
                .toList();
    }

    private void addSession(
            final ProjectId projectId, final String sessionName, final String worktreePath) {
        final Session session =
                new Session(
                        projectId,
                        sessionName,
                        "Imported worktree",
                        "",
                        Path.of(worktreePath).toAbsolutePath().normalize().toString());
        try {
            final var sessionId = this.actionContext.appState().addSession(projectId, session);
            actionContext
                    .viewCoordinator()
                    .updateView(ViewId.SESSION, ViewState.session(projectId, sessionId));
        } catch (java.io.InvalidObjectException exception) {
            LOG.log(Level.SEVERE, TITLE, exception);
        }
    }
}
