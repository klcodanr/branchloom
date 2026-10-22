package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.github.GitHubAuth;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.actions.OpenDirectoryAction;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public final class ProjectSettingsView extends JPanel implements View {
    private static final String TITLE = "Project settings";
    private static final String WORKTREE_VARIABLES_TOOLTIP =
            "Interpolated variables: {projectName}, {projectPath}, {sessionName}, "
                    + "{sessionSlug}, {worktreePath}";

    public ProjectSettingsView(final ActionContext actionContext) {
        super();
        setLayout(new BorderLayout());
        add(create(actionContext), BorderLayout.CENTER);
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

    public static JComponent create(final ActionContext actionContext) {
        final var state = actionContext.appState();
        final Project project =
                state.projects().get(actionContext.viewController().currentProjectId());
        if (project == null) {
            throw new IllegalStateException("A project must be selected.");
        }
        final Runnable close =
                () ->
                        actionContext
                                .viewController()
                                .updateView(
                                        ViewId.PROJECT,
                                        ViewController.ViewState.project(
                                                actionContext.viewController().currentProjectId()));
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
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(new LabeledField("Project name", name));
        form.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        form.add(new LabeledField("Group", group));
        form.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        form.add(new LabeledField("Repository path", repositoryField(project, form)));
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
        formContainer.setOpaque(false);
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
        return SettingsPanel.render(
                TITLE,
                "Overrides for " + project.name(),
                formContainer,
                () ->
                        saveProject(
                                state,
                                actionContext.viewController(),
                                project,
                                name,
                                group,
                                template,
                                startup,
                                agentContextPath,
                                agentContextText,
                                githubAuth,
                                close),
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
                                githubAuth));
    }

    private static JPanel repositoryField(final Project project, final JPanel parent) {
        final JTextField repositoryPath = new JTextField(project.path());
        repositoryPath.setEditable(false);
        final JButton openRepository = new BaseButton("Open in file manager");
        openRepository.addActionListener(event -> OpenDirectoryAction.open(project.path(), parent));
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
            final Runnable close) {
        final String updatedName = name.getText().trim();
        if (updatedName.isBlank()) {
            JOptionPane.showMessageDialog(
                    name, "Project name is required.", TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (state.projects().values().stream()
                .anyMatch(
                        other ->
                                !other.equals(project)
                                        && other.name().equalsIgnoreCase(updatedName))) {
            JOptionPane.showMessageDialog(
                    name,
                    "A project with that name already exists.",
                    TITLE,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        final String updatedGroup =
                group.getText().isBlank() ? Defaults.DEFAULT_GROUP : group.getText().trim();
        final Credential selected = (Credential) githubAuth.getSelectedItem();
        final Project updated =
                new Project(
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
