package com.jagent.desktop.ui.actions;

import static com.jagent.desktop.ui.components.UiFactory.button;
import static com.jagent.desktop.ui.components.UiFactory.form;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.components.GitHubAuthSelector;
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

/** Starts the project creation workflow. */
public final class CreateProjectAction extends BaseAction {

    private static final Logger LOG = Logger.getLogger(CreateProjectAction.class.getName());

    public CreateProjectAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "new-project";
    }

    @Override
    public String label() {
        return "New project";
    }

    @Override
    public void execute() {
        final var appState = this.actionContext.appState();
        final JTextField name = new JTextField(35);
        final JTextField path = new JTextField(35);
        final JComboBox<GitHub.Auth> githubAuth = GitHubAuthSelector.render();
        githubAuth.setPreferredSize(new Dimension(350, githubAuth.getPreferredSize().height));
        final JButton browse = button("Browse...");
        browse.addActionListener(
                event -> {
                    final JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
                    chooser.setDialogTitle("Select Git repository");
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
                        "Git repository path",
                        pathInput,
                        "GitHub CLI auth",
                        githubAuth);
        if (JOptionPane.showConfirmDialog(
                        this.actionContext.window(),
                        projectForm,
                        "Add Git project",
                        JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        final String projectName = name.getText().trim();
        final Path projectPath = Path.of(path.getText().trim()).toAbsolutePath().normalize();
        if (projectName.isBlank()) {
            LOG.severe("Add Git project: Project name is required.");
            return;
        }
        if (!Files.isDirectory(projectPath) || !Git.isRepository(projectPath)) {
            final String message =
                    "The selected folder is not a Git repository, or Git is not available to the app.";
            LOG.severe("Add Git project: " + message);
            JOptionPane.showMessageDialog(
                    this.actionContext.window(),
                    message,
                    "Add Git project",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (appState.projects().values().stream()
                .anyMatch(
                        project ->
                                projectPath.equals(
                                        Path.of(project.path()).toAbsolutePath().normalize()))) {
            LOG.severe("Add Git project: That project is already registered.");
            return;
        }
        if (appState.projects().values().stream()
                .anyMatch(project -> project.name().equalsIgnoreCase(projectName))) {
            LOG.severe("Add Git project: A project with that name already exists.");
            return;
        }
        final GitHub.Auth auth =
                githubAuth.getSelectedItem() instanceof GitHub.Auth selected ? selected : null;
        final ProjectId projectId =
                appState.addProject(new Project(projectName, projectPath.toString(), auth));
        actionContext.viewCoordinator().updateView(ViewId.PROJECT, ViewState.project(projectId));
    }
}
