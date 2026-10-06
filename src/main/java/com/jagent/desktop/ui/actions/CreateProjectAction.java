package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.services.github.GitHubAuth;
import com.jagent.desktop.ui.components.BaseButton;
import com.jagent.desktop.ui.components.FormPanel;
import com.jagent.desktop.ui.components.GitHubAuthSelector;
import com.jagent.desktop.ui.components.UiText;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts the workflow for adding an existing local project. */
public class CreateProjectAction extends BaseAction {

    private static final Logger LOG = LoggerFactory.getLogger(CreateProjectAction.class);
    private static final String ADD_PROJECT_TITLE = "Add local project";
    private final String targetGroup;

    public CreateProjectAction(final ActionContext actionContext) {
        this(actionContext, null);
    }

    public CreateProjectAction(final ActionContext actionContext, final String targetGroup) {
        super(actionContext);
        this.targetGroup = targetGroup;
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
        ProgressOperation.run(
                        this.actionContext,
                        ADD_PROJECT_TITLE,
                        "Loading GitHub accounts...",
                        () -> new GitHubAuth().listCredentials(this.actionContext.appState()))
                .thenAccept(this::showDialog)
                .exceptionally(
                        failure -> {
                            LOG.error("Add local project: Failed to load GitHub accounts.");
                            return null;
                        });
    }

    private void showDialog(final java.util.List<Credential> configuredAuths) {
        final var appState = this.actionContext.appState();
        final JTextField name = new JTextField(35);
        final JTextField path = new JTextField(35);
        final JComboBox<Credential> githubAuth =
                GitHubAuthSelector.renderConfigured(configuredAuths);
        githubAuth.setPreferredSize(new Dimension(350, githubAuth.getPreferredSize().height));
        final JButton browse = new BaseButton("Browse...");
        browse.addActionListener(
                event -> {
                    final JFileChooser chooser =
                            new JFileChooser(
                                    UiText.valueOrDefault(
                                            path.getText().trim(),
                                            System.getProperty("user.home")));
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
                new FormPanel(
                        "Project name",
                        name,
                        "Project folder",
                        pathInput,
                        "GitHub connection",
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
            LOG.error("Add local project: Project name is required.");
            return;
        }
        if (!Files.isDirectory(projectPath)) {
            final String message = "The selected path is not a folder.";
            LOG.error("Add local project: {}", message);
            JOptionPane.showMessageDialog(
                    this.actionContext.window(),
                    message,
                    ADD_PROJECT_TITLE,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (appState.projects().values().stream()
                .anyMatch(
                        project ->
                                project.name().equalsIgnoreCase(projectName)
                                        || project.path().equals(projectPath.toString()))) {
            LOG.error("Add local project: That project is already registered.");
            return;
        }
        final Credential auth =
                githubAuth.getSelectedItem() instanceof Credential selected ? selected : null;
        final Project project = new Project(projectName, projectPath.toString(), auth);
        final ProjectId projectId =
                appState.addProject(targetGroup == null ? project : project.withGroup(targetGroup));
        actionContext.viewCoordinator().updateView(ViewId.PROJECT, ViewState.project(projectId));
    }
}
