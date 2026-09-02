package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.actions.OpenDirectoryAction;
import com.jagent.desktop.ui.components.GitHubAuthSelector;
import com.jagent.desktop.ui.components.SettingsPanel;
import com.jagent.desktop.ui.components.UiFactory;
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
        final AppState state = actionContext.appState();
        final Project project = state.projects().get(state.currentProjectId());
        if (project == null) {
            throw new IllegalStateException("A project must be selected.");
        }
        final Runnable close =
                () ->
                        actionContext
                                .viewCoordinator()
                                .updateView(
                                        ViewId.PROJECT,
                                        ViewCoordinator.ViewState.project(
                                                state.currentProjectId()));
        final JTextField name = new JTextField(project.name(), 45);
        final JTextField group = new JTextField(project.group(), 45);
        final JTextArea template = new JTextArea(project.worktreeTemplate(), 2, 45);
        final JTextArea startup =
                new JTextArea(String.join("\n", project.startupCommands()), 4, 45);
        final JTextField agentContextPath = new JTextField(agentContextPath(project), 45);
        final JTextArea agentContextText = new JTextArea(agentContextText(project), 6, 45);
        UiFactory.configureTextAreaTraversal(template);
        UiFactory.configureTextAreaTraversal(agentContextText);
        UiFactory.configureTextAreaTraversal(startup);
        template.setToolTipText(WORKTREE_VARIABLES_TOOLTIP);
        startup.setToolTipText(WORKTREE_VARIABLES_TOOLTIP);
        agentContextPath.setToolTipText(
                "Blank disables context generation. Relative paths are created in each worktree.");
        final JComboBox<GitHub.Auth> githubAuth = GitHubAuthSelector.render();
        final JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(SettingsPanel.labeledField("Project name", name));
        form.add(Box.createVerticalStrut(18));
        form.add(SettingsPanel.labeledField("Group", group));
        form.add(Box.createVerticalStrut(18));
        form.add(SettingsPanel.labeledField("Repository path", repositoryField(project, form)));
        form.add(Box.createVerticalStrut(18));
        form.add(SettingsPanel.labeledField("Worktree path", template));
        form.add(Box.createVerticalStrut(18));
        form.add(
                SettingsPanel.labeledField(
                        "Startup command files / commands (one per line)", startup));
        form.add(Box.createVerticalStrut(18));
        form.add(SettingsPanel.labeledField("Agent context file path", agentContextPath));
        form.add(Box.createVerticalStrut(18));
        form.add(SettingsPanel.labeledField("Additional agent context", agentContextText));
        form.add(Box.createVerticalStrut(18));
        form.add(SettingsPanel.labeledField("GitHub CLI auth", githubAuth));
        final JPanel formContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        formContainer.setOpaque(false);
        formContainer.add(form);
        final String groupValue = project.group();
        final String initialGroup =
                groupValue == null || groupValue.isBlank() ? Defaults.DEFAULT_GROUP : groupValue;
        final String initialTemplate =
                project.worktreeTemplate() == null ? "" : project.worktreeTemplate();
        final String initialStartup = String.join("\n", project.startupCommands());
        final String initialAgentContextPath = agentContextPath(project);
        final String initialAgentContextText = agentContextText(project);
        final String initialHost = project.githubHost() == null ? "" : project.githubHost();
        final String initialUser = project.githubUser() == null ? "" : project.githubUser();
        return SettingsPanel.render(
                TITLE,
                "Overrides for " + project.name(),
                formContainer,
                () ->
                        saveProject(
                                state,
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
                                initialHost,
                                initialUser,
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
        final JButton openRepository = UiFactory.button("Open in file manager");
        openRepository.addActionListener(event -> OpenDirectoryAction.open(project.path(), parent));
        final JPanel repository = new JPanel(new BorderLayout(8, 0));
        repository.setOpaque(false);
        repository.add(repositoryPath, BorderLayout.CENTER);
        repository.add(openRepository, BorderLayout.EAST);
        return repository;
    }

    private static String agentContextPath(final Project project) {
        return project.agentContextPath() == null ? "" : project.agentContextPath();
    }

    private static String agentContextText(final Project project) {
        return project.agentContextText() == null ? "" : project.agentContextText();
    }

    private static void saveProject(
            final AppState state,
            final Project project,
            final JTextField name,
            final JTextField group,
            final JTextArea template,
            final JTextArea startup,
            final JTextField agentContextPath,
            final JTextArea agentContextText,
            final JComboBox<GitHub.Auth> githubAuth,
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
        final GitHub.Auth selected = (GitHub.Auth) githubAuth.getSelectedItem();
        final Project updated =
                new Project(
                        updatedName,
                        project.path(),
                        updatedGroup,
                        selected == null ? "" : selected.host(),
                        selected == null ? "" : selected.user(),
                        template.getText().trim(),
                        project.worktreeCommand(),
                        GlobalSettingsView.lines(startup.getText()),
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
    private static boolean hasChanges(
            final String initialName,
            final String initialGroup,
            final String initialTemplate,
            final String initialStartup,
            final String initialAgentContextPath,
            final String initialAgentContextText,
            final String initialHost,
            final String initialUser,
            final JTextField name,
            final JTextField group,
            final JTextArea template,
            final JTextArea startup,
            final JTextField agentContextPath,
            final JTextArea agentContextText,
            final JComboBox<GitHub.Auth> githubAuth) {
        return !initialName.equals(name.getText().trim())
                || !initialGroup.equals(
                        group.getText().isBlank() ? Defaults.DEFAULT_GROUP : group.getText().trim())
                || !Objects.equals(initialTemplate, template.getText())
                || !initialStartup.equals(startup.getText())
                || !initialAgentContextPath.equals(agentContextPath.getText().trim())
                || !initialAgentContextText.equals(agentContextText.getText())
                || !initialHost.equals(selectedAuthHost(githubAuth))
                || !initialUser.equals(selectedAuthUser(githubAuth));
    }

    private static String selectedAuthHost(final JComboBox<GitHub.Auth> githubAuth) {
        final GitHub.Auth selected = (GitHub.Auth) githubAuth.getSelectedItem();
        return selected == null ? "" : selected.host();
    }

    private static String selectedAuthUser(final JComboBox<GitHub.Auth> githubAuth) {
        final GitHub.Auth selected = (GitHub.Auth) githubAuth.getSelectedItem();
        return selected == null ? "" : selected.user();
    }
}
