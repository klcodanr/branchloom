package com.jagent.desktop.ui.actions;

import static com.jagent.desktop.ui.components.UiFactory.button;
import static com.jagent.desktop.ui.components.UiFactory.form;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.components.GitHubAuthSelector;
import com.jagent.desktop.ui.dialogs.ProgressOperation;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Starts the workflow for adding an existing local project. */
public class CreateProjectAction extends BaseAction {

    private static final Logger LOG = Logger.getLogger(CreateProjectAction.class.getName());
    private static final String ADD_PROJECT_TITLE = "Add local project";

    public CreateProjectAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "new-project";
    }

    @Override
    public String label() {
        return ADD_PROJECT_TITLE;
    }

    @Override
    public void execute() {
        final ProgressOperation progress =
                ProgressOperation.start(
                        this.actionContext.window(),
                        ADD_PROJECT_TITLE,
                        "Loading GitHub accounts...");
        BackgroundTasks.submit("Operations", "Load GitHub accounts", GitHub::configuredAuths)
                .whenComplete(
                        (configuredAuths, failure) ->
                                SwingUtilities.invokeLater(
                                        () -> {
                                            progress.close();
                                            if (failure != null) {
                                                LOG.severe(
                                                        "Add local project: Failed to load GitHub accounts.");
                                                return;
                                            }
                                            showDialog(configuredAuths);
                                        }));
    }

    protected static boolean duplicateName(
            final java.util.Collection<Project> projects, final String name) {
        return projects.stream().anyMatch(project -> project.name().equalsIgnoreCase(name));
    }

    protected static boolean duplicatePath(
            final java.util.Collection<Project> projects, final Path path) {
        return projects.stream()
                .anyMatch(
                        project ->
                                path.equals(Path.of(project.path()).toAbsolutePath().normalize()));
    }

    private void showDialog(final java.util.List<GitHub.Auth> configuredAuths) {
        final var appState = this.actionContext.appState();
        final JTextField name = new JTextField(35);
        final JTextField path = new JTextField(35);
        final JComboBox<GitHub.Auth> githubAuth =
                GitHubAuthSelector.renderConfigured(configuredAuths);
        githubAuth.setPreferredSize(new Dimension(350, githubAuth.getPreferredSize().height));
        final JButton browse = button("Browse...");
        browse.addActionListener(
                event -> {
                    final JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
                    chooser.setDialogTitle("Select project folder");
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setAcceptAllFileFilterUsed(false);
                    chooser.setApproveButtonText("Select Folder");
                    chooser.setMultiSelectionEnabled(false);
                    if (chooser.showOpenDialog(this.actionContext.window())
                            == JFileChooser.APPROVE_OPTION) {
                        path.setText(chooser.getSelectedFile().getAbsolutePath());
                    }
                });
        final JPanel pathInput = new JPanel(new java.awt.BorderLayout(8, 0));
        pathInput.add(path, java.awt.BorderLayout.CENTER);
        pathInput.add(browse, java.awt.BorderLayout.EAST);
        final JPanel projectForm =
                form(
                        "Project name",
                        name,
                        "Project folder",
                        pathInput,
                        "GitHub CLI auth",
                        githubAuth);
        if (JOptionPane.showConfirmDialog(
                        this.actionContext.window(),
                        projectForm,
                        ADD_PROJECT_TITLE,
                        JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        final String projectName = name.getText().trim();
        final Path projectPath = Path.of(path.getText().trim()).toAbsolutePath().normalize();
        if (projectName.isBlank()) {
            LOG.severe("Add local project: Project name is required.");
            return;
        }
        if (!Files.isDirectory(projectPath)) {
            final String message = "The selected path is not a folder.";
            LOG.severe("Add local project: " + message);
            JOptionPane.showMessageDialog(
                    this.actionContext.window(),
                    message,
                    ADD_PROJECT_TITLE,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (duplicatePath(appState.projects().values(), projectPath)) {
            LOG.severe("Add local project: That project is already registered.");
            return;
        }
        if (duplicateName(appState.projects().values(), projectName)) {
            LOG.severe("Add local project: A project with that name already exists.");
            return;
        }
        final GitHub.Auth auth =
                githubAuth.getSelectedItem() instanceof GitHub.Auth selected ? selected : null;
        final ProjectId projectId =
                appState.addProject(new Project(projectName, projectPath.toString(), auth));
        actionContext.viewCoordinator().updateView(ViewId.PROJECT, ViewState.project(projectId));
    }
}
