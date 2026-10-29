package com.jagent.desktop.ui.views;

import com.google.inject.Inject;
import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.github.GitHubAuth;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.components.BaseButton;
import com.jagent.desktop.ui.components.BaseTextArea;
import com.jagent.desktop.ui.components.GitHubAuthSelector;
import com.jagent.desktop.ui.components.LabeledField;
import com.jagent.desktop.ui.components.SettingsPanel;
import com.jagent.desktop.ui.components.UiBorders;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiText;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Map;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public final class ProjectSettingsView extends JPanel implements View {
    private static final String TITLE = "Project settings";
    private static final String WORKTREE_VARIABLES_TOOLTIP =
            "Interpolated variables: {projectName}, {projectPath}, {sessionName}, "
                    + "{sessionSlug}, {worktreePath}";

    @Inject
    public ProjectSettingsView(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        super();
        setLayout(new BorderLayout());
        final Project project = appState.projects().get(viewController.currentProjectId());
        if (project == null) {
            throw new IllegalStateException("A project must be selected.");
        }
        final Runnable close =
                () ->
                        viewController.updateView(
                                ViewId.PROJECT,
                                ViewController.ViewState.project(
                                        viewController.currentProjectId()));
        final JTextField name = new JTextField(project.name(), 45);
        final JTextField group = new JTextField(project.group(), 45);
        final JTextField template = new JTextField(project.worktreeTemplate(), 45);
        final JTextArea startup =
                new BaseTextArea(String.join("\n", project.startupCommands()), 4, 45);
        final JTextField agentContextPath =
                new JTextField(UiText.valueOrDefault(project.agentContextPath(), ""), 45);
        final JTextArea agentContextText =
                new BaseTextArea(UiText.valueOrDefault(project.agentContextText(), ""), 6, 45);
        template.setToolTipText(WORKTREE_VARIABLES_TOOLTIP);
        startup.setToolTipText(WORKTREE_VARIABLES_TOOLTIP);
        agentContextPath.setToolTipText(
                "Blank disables context generation. Relative paths are created in each worktree.");
        final JComboBox<Credential> githubAuth =
                GitHubAuthSelector.renderConfigured(new GitHubAuth().listCredentials());
        final JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(new LabeledField("Project name", name));
        form.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        form.add(new LabeledField("Group", group));
        form.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        form.add(new LabeledField("Repository path", repositoryField(project)));
        form.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        form.add(new LabeledField("Worktree path", template));
        form.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        form.add(new LabeledField("Startup command files / commands (one per line)", startup));
        form.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        form.add(new LabeledField("Agent context file path", agentContextPath));
        form.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        form.add(new LabeledField("Additional agent context", agentContextText));
        form.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        form.add(new LabeledField("GitHub connection", githubAuth));
        final JPanel formContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        formContainer.setBorder(UiBorders.section());
        formContainer.add(form);
        final String groupValue = project.group();
        final String initialGroup = UiText.valueOrDefault(groupValue, Defaults.DEFAULT_GROUP);
        final String initialTemplate = UiText.valueOrDefault(project.worktreeTemplate(), "");
        final String initialStartup = String.join("\n", project.startupCommands());
        final String initialAgentContextPath =
                UiText.valueOrDefault(project.agentContextPath(), "");
        final String initialAgentContextText =
                UiText.valueOrDefault(project.agentContextText(), "");
        final Credential initialCredential = project.credential();
        add(
                SettingsPanel.render(
                        TITLE,
                        "Overrides for " + project.name(),
                        formContainer,
                        () ->
                                saveProject(
                                        appState,
                                        viewController,
                                        project,
                                        name,
                                        group,
                                        template,
                                        startup,
                                        agentContextPath,
                                        agentContextText,
                                        githubAuth,
                                        close,
                                        dialogService),
                        close,
                        () ->
                                hasChanges(
                                        project.name(),
                                        initialGroup,
                                        initialTemplate,
                                        initialStartup,
                                        initialAgentContextPath,
                                        initialAgentContextText,
                                        initialCredential == null ? null : initialCredential.id(),
                                        name,
                                        group,
                                        template,
                                        startup,
                                        agentContextPath,
                                        agentContextText,
                                        githubAuth),
                        dialogService));
    }

    @Override
    public ViewId id() {
        return ViewId.PROJECT_SETTINGS;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public JComponent render() {
        return this;
    }

    private static JPanel repositoryField(final Project project) {
        final JTextField repositoryPath = new JTextField(project.path());
        repositoryPath.setEditable(false);
        final JButton openRepository = new BaseButton("Open in file manager");
        openRepository.addActionListener(event -> PlatformCommands.openPath(project.path()));
        final JPanel repository = new JPanel(new BorderLayout(8, 0));
        repository.setOpaque(false);
        repository.add(repositoryPath, BorderLayout.CENTER);
        repository.add(openRepository, BorderLayout.EAST);
        return repository;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    /* package */ static void saveProject(
            final AppState state,
            final ViewController viewController,
            final Project project,
            final JTextField name,
            final JTextField group,
            final JTextField template,
            final JTextArea startup,
            final JTextField agentContextPath,
            final JTextArea agentContextText,
            final JComboBox<Credential> githubAuth,
            final Runnable close,
            final DialogService dialogs) {
        final String updatedName = name.getText().trim();
        if (updatedName.isBlank()) {
            dialogs.errorMessage("Project name is required.", TITLE);
            return;
        }
        if (state.projects().values().stream()
                .anyMatch(
                        other ->
                                !other.equals(project)
                                        && other.name().equalsIgnoreCase(updatedName))) {
            dialogs.errorMessage("A project with that name already exists.", TITLE);
            return;
        }
        final String updatedGroup =
                group.getText().isBlank() ? Defaults.DEFAULT_GROUP : group.getText().trim();
        final Credential selected = (Credential) githubAuth.getSelectedItem();
        final Project updated =
                new Project(
                        project.id(),
                        updatedName,
                        project.path(),
                        updatedGroup,
                        selected,
                        template.getText().trim(),
                        project.worktreeCommand(),
                        startup.getText().lines().toList(),
                        project.sessionIds(),
                        agentContextPath.getText().trim(),
                        agentContextText.getText());
        state.projects().entrySet().stream()
                .filter(entry -> entry.getValue().equals(project))
                .map(Map.Entry::getKey)
                .findFirst()
                .ifPresent(
                        projectId -> {
                            state.updateProject(projectId, updated);
                            close.run();
                        });
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    /* package */ static boolean hasChanges(
            final String initialName,
            final String initialGroup,
            final String initialTemplate,
            final String initialStartup,
            final String initialAgentContextPath,
            final String initialAgentContextText,
            final String initialConnectionId,
            final JTextField name,
            final JTextField group,
            final JTextField template,
            final JTextArea startup,
            final JTextField agentContextPath,
            final JTextArea agentContextText,
            final JComboBox<Credential> githubAuth) {
        return !initialName.equals(name.getText().trim())
                || !initialGroup.equals(
                        group.getText().isBlank() ? Defaults.DEFAULT_GROUP : group.getText().trim())
                || !Objects.equals(initialTemplate, template.getText())
                || !initialStartup.equals(startup.getText())
                || !initialAgentContextPath.equals(agentContextPath.getText().trim())
                || !initialAgentContextText.equals(agentContextText.getText())
                || !Objects.equals(
                        initialConnectionId,
                        githubAuth.getSelectedItem() == null
                                ? null
                                : ((Credential) githubAuth.getSelectedItem()).id());
    }
}
