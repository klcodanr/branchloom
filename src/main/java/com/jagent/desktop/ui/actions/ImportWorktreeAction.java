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
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
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

        final JComboBox<String> worktree = new JComboBox<>(worktrees.toArray(new String[0]));
        final Path first = Path.of(worktrees.getFirst()).getFileName();
        final JTextField name =
                new JTextField(first == null ? worktrees.getFirst() : first.toString(), 28);
        final JPanel worktreeForm = form("Existing worktree", worktree, "Session name", name);
        if (JOptionPane.showConfirmDialog(
                        actionContext.window(), worktreeForm, TITLE, JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        if (name.getText().isBlank() || worktree.getSelectedItem() == null) {
            return;
        }

        final String sessionName = name.getText().trim();
        if (project.sessionIds().stream()
                .map(state.sessions()::get)
                .anyMatch(
                        session ->
                                session != null && session.name().equalsIgnoreCase(sessionName))) {
            LOG.severe("Import worktree: A session with that name already exists.");
            return;
        }
        addSession(projectId, sessionName, worktree.getSelectedItem().toString());
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
