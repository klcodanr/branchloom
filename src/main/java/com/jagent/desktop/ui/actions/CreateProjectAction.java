package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.github.GitHubAuth;
import com.jagent.desktop.ui.components.BaseButton;
import com.jagent.desktop.ui.components.FormPanel;
import com.jagent.desktop.ui.components.GitHubAuthSelector;
import com.jagent.desktop.ui.components.UiText;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts the workflow for adding an existing local project. */
public class CreateProjectAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;

    private static final Logger LOG = LoggerFactory.getLogger(CreateProjectAction.class);
    private static final String ADD_PROJECT_TITLE = "Add local project";
    private final String targetGroup;

    @Inject
    public CreateProjectAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        this(viewController, appState, dialogService, null);
    }

    public CreateProjectAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final String targetGroup) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
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
                        ADD_PROJECT_TITLE,
                        "Loading GitHub accounts...",
                        () -> new GitHubAuth().listCredentials())
                .thenAccept(this::showDialog)
                .exceptionally(
                        failure -> {
                            LOG.error("Add local project: Failed to load GitHub accounts.");
                            return null;
                        });
    }

    private void showDialog(final List<Credential> configuredAuths) {
        final var appState = this.appState;
        final JTextField name = new JTextField(35);
        final JTextField path = new JTextField(35);
        final JComboBox<Credential> githubAuth =
                GitHubAuthSelector.renderConfigured(configuredAuths);
        githubAuth.setPreferredSize(new Dimension(350, githubAuth.getPreferredSize().height));
        final JButton browse = new BaseButton("Browse...");
        browse.addActionListener(
                event -> {
                    final Path selected =
                            dialogService.chooseDirectory(
                                    "Select project folder",
                                    Path.of(
                                            UiText.valueOrDefault(
                                                    path.getText().trim(),
                                                    System.getProperty("user.home"))),
                                    "Select Folder");
                    if (selected != null) {
                        path.setText(selected.toAbsolutePath().toString());
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
        if (dialogService.confirm(
                        projectForm,
                        ADD_PROJECT_TITLE,
                        DialogService.OptionType.OK_CANCEL,
                        DialogService.MessageType.PLAIN)
                != DialogService.ReturnType.OK) {
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
            dialogService.message(message, ADD_PROJECT_TITLE, DialogService.MessageType.ERROR);
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
        final Project project = Project.create(projectName, projectPath.toString(), auth);
        final ProjectId projectId =
                appState.addProject(targetGroup == null ? project : project.withGroup(targetGroup));
        viewController.openProject(projectId);
    }
}
