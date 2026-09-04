package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.components.SettingsPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

public final class GlobalSettingsView implements View {
    private static final int CONFIGURED_LIST_HEIGHT = 260;
    private static final int ROW_HEIGHT = 50;
    private static final EmptyBorder ROW_BORDER =
            new EmptyBorder(
                    UiConstants.SPACING_SM,
                    UiConstants.SPACING_MD,
                    UiConstants.SPACING_SM,
                    UiConstants.SPACING_MD);
    private static final String WORKTREE_VARIABLES_TOOLTIP =
            "Interpolated variables: {projectName}, {projectPath}, {sessionName}, "
                    + "{sessionSlug}, {worktreePath}";
    private static final String AGENT_VARIABLES_TOOLTIP = WORKTREE_VARIABLES_TOOLTIP + ", {prompt}";
    private static final String REVIEW_VARIABLES_TOOLTIP =
            "Interpolated variables: {number}, {title}, {url}, {branch}, {projectName}, "
                    + "{worktreePath}";

    private final transient AppState state;
    private final transient ViewCoordinator viewCoordinator;

    public GlobalSettingsView(final ActionContext actionContext) {
        this.state = actionContext.appState();
        this.viewCoordinator = actionContext.viewCoordinator();
    }

    @Override
    public ViewId id() {
        return ViewId.SETTINGS;
    }

    @Override
    public String title() {
        return "Settings";
    }

    @Override
    public JComponent render() {
        final AppSettings settings = state.appSettings();
        final JTextArea work = new JTextArea(settings.worktreeTemplate(), 2, 45);
        UiFactory.configureTextAreaTraversal(work);
        work.setToolTipText(WORKTREE_VARIABLES_TOOLTIP);
        final JComboBox<Theme.FlatLafTheme> theme = new JComboBox<>(Theme.FlatLafTheme.values());
        theme.setSelectedItem(Theme.FlatLafTheme.from(settings.theme()));
        final JPanel general = new JPanel(new BorderLayout());
        general.setOpaque(false);
        general.setBorder(
                new EmptyBorder(
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING));
        final JPanel generalForm = new JPanel();
        generalForm.setOpaque(false);
        generalForm.setLayout(new BoxLayout(generalForm, BoxLayout.Y_AXIS));
        generalForm.add(SettingsPanel.labeledField("Default worktree path", work));
        generalForm.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        generalForm.add(SettingsPanel.labeledField("Appearance", theme));
        general.add(generalForm, BorderLayout.NORTH);
        final List<JTextField> names = new ArrayList<>();
        final List<JTextField> newSessionCommands = new ArrayList<>();
        final List<JTextField> openCommands = new ArrayList<>();
        final List<JTextField> toolNames = new ArrayList<>();
        final List<JTextField> toolCommands = new ArrayList<>();
        final JTextArea reviewPrompt = new JTextArea(settings.reviewPrompt(), 10, 60);
        UiFactory.configureTextAreaTraversal(reviewPrompt);
        reviewPrompt.setToolTipText(REVIEW_VARIABLES_TOOLTIP);
        final JTextArea reviewPlanPrompt = new JTextArea(settings.reviewPlanPrompt(), 8, 60);
        UiFactory.configureTextAreaTraversal(reviewPlanPrompt);
        final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded");
        tabs.addTab("General", general);
        tabs.addTab(
                "Agents", agentEditor(settings.agents(), names, newSessionCommands, openCommands));
        tabs.addTab("Editors", toolEditor(settings.tools(), toolNames, toolCommands));
        tabs.addTab("Review", reviewEditor(reviewPrompt, reviewPlanPrompt));
        styleTabs(tabs);
        tabs.addChangeListener(
                event -> viewCoordinator.updateSelectedTab(id(), tabs.getSelectedIndex()));
        final int selectedTab = viewCoordinator.selectedTab(id());
        if (selectedTab < tabs.getTabCount()) {
            tabs.setSelectedIndex(selectedTab);
        }
        final AtomicBoolean dirty = new AtomicBoolean();
        installDirtyTracking(tabs, dirty);
        return SettingsPanel.render(
                "",
                "",
                tabs,
                () -> {
                    final List<Agent> configured = new ArrayList<>();
                    for (int i = 0; i < names.size(); i++) {
                        if (!names.get(i).getText().isBlank()
                                && !newSessionCommands.get(i).getText().isBlank()
                                && !openCommands.get(i).getText().isBlank()) {
                            configured.add(
                                    new Agent(
                                            names.get(i).getText().trim(),
                                            newSessionCommands.get(i).getText().trim(),
                                            openCommands.get(i).getText().trim()));
                        }
                    }
                    final Theme.FlatLafTheme selectedTheme =
                            (Theme.FlatLafTheme) theme.getSelectedItem();
                    state.updateAppSettings(
                            new AppSettings(
                                    configured,
                                    settings.groupOrder(),
                                    reviewPrompt.getText().trim(),
                                    selectedTheme.toString(),
                                    configuredTools(toolNames, toolCommands),
                                    work.getText().trim(),
                                    settings.reviewPlanEnabled(),
                                    settings.reviewPlanCommand(),
                                    reviewPlanPrompt.getText().trim()));
                    viewCoordinator.updateView(ViewId.HOME, null);
                    Theme.apply(selectedTheme);
                },
                () -> viewCoordinator.updateView(ViewId.HOME, null),
                dirty::get);
    }

    private static JPanel reviewEditor(final JTextArea prompt, final JTextArea reviewPlanPrompt) {
        final JPanel editor = new JPanel(new BorderLayout(0, UiConstants.COMPONENT_GAP));
        editor.setOpaque(false);
        editor.setBorder(
                new EmptyBorder(
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING));
        final JPanel fields = new JPanel();
        fields.setOpaque(false);
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        reviewPlanPrompt.setLineWrap(true);
        reviewPlanPrompt.setWrapStyleWord(true);
        fields.add(SettingsPanel.labeledField("Pull request prompt", prompt));
        fields.add(Box.createVerticalStrut(UiConstants.SECTION_PADDING));
        fields.add(SettingsPanel.labeledField("Review plan prompt", reviewPlanPrompt));
        editor.add(fields, BorderLayout.CENTER);
        return editor;
    }

    private static void installDirtyTracking(final Component component, final AtomicBoolean dirty) {
        if (component instanceof JTextComponent text) {
            text.getDocument()
                    .addDocumentListener(
                            new javax.swing.event.DocumentListener() {
                                @Override
                                public void insertUpdate(javax.swing.event.DocumentEvent event) {
                                    dirty.set(true);
                                }

                                @Override
                                public void removeUpdate(javax.swing.event.DocumentEvent event) {
                                    dirty.set(true);
                                }

                                @Override
                                public void changedUpdate(javax.swing.event.DocumentEvent event) {
                                    dirty.set(true);
                                }
                            });
        }
        if (component instanceof JComboBox<?> combo) {
            combo.addActionListener(event -> dirty.set(true));
        }
        if (component instanceof Container container) {
            for (final Component child : container.getComponents()) {
                installDirtyTracking(child, dirty);
            }
        }
    }

    private static JPanel agentEditor(
            final List<Agent> agents,
            final List<JTextField> names,
            final List<JTextField> newSessionCommands,
            final List<JTextField> openCommands) {
        final JPanel editor = new JPanel(new BorderLayout(0, UiConstants.COMPONENT_GAP));
        editor.setOpaque(false);
        editor.setBorder(
                new EmptyBorder(
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING));
        final JPanel intro = new JPanel(new BorderLayout());
        intro.setOpaque(false);
        intro.add(UiFactory.label("Configured agents", Theme.FontSize.LG), BorderLayout.WEST);
        intro.add(
                UiFactory.label(
                        "Configure new-session and open commands for each agent",
                        Theme.FontSize.SM),
                BorderLayout.EAST);
        editor.add(intro, BorderLayout.NORTH);
        final JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        for (final Agent agent : agents) {
            addAgentRow(
                    rows,
                    names,
                    newSessionCommands,
                    openCommands,
                    agent.name,
                    agent.newSessionCommand,
                    agent.openCommand);
        }
        editor.add(configuredTable(rows, agentHeaders()), BorderLayout.CENTER);
        final JPanel footer = new JPanel(new BorderLayout(0, UiConstants.COMPONENT_GAP));
        footer.setOpaque(false);
        final JButton add = UiFactory.button("+  Add agent");
        add.addActionListener(
                e -> {
                    addAgentRow(rows, names, newSessionCommands, openCommands, "", "", "");
                    rows.revalidate();
                    rows.repaint();
                });
        footer.add(add, BorderLayout.NORTH);
        editor.add(footer, BorderLayout.SOUTH);
        return editor;
    }

    private static JPanel toolEditor(
            final List<Tool> tools, final List<JTextField> names, final List<JTextField> commands) {
        final JPanel editor = new JPanel(new BorderLayout(0, UiConstants.COMPONENT_GAP));
        editor.setOpaque(false);
        editor.setBorder(
                new EmptyBorder(
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING));
        final JPanel intro = new JPanel(new BorderLayout());
        intro.setOpaque(false);
        intro.add(UiFactory.label("Configured editors", Theme.FontSize.LG), BorderLayout.WEST);
        intro.add(
                UiFactory.label(
                        "Commands run from the current session worktree", Theme.FontSize.SM),
                BorderLayout.EAST);
        editor.add(intro, BorderLayout.NORTH);
        final JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        for (final Tool tool : tools) {
            addToolRow(rows, names, commands, tool.label(), tool.command());
        }
        editor.add(configuredTable(rows, editorHeaders()), BorderLayout.CENTER);
        final JButton add = UiFactory.button("+  Add editor");
        add.setHorizontalAlignment(SwingConstants.LEFT);
        add.addActionListener(
                e -> {
                    addToolRow(rows, names, commands, "", "");
                    rows.revalidate();
                    rows.repaint();
                });
        editor.add(add, BorderLayout.SOUTH);
        return editor;
    }

    private static JScrollPane configuredList(final JPanel rows) {
        final JScrollPane scroll = new JScrollPane(rows);
        scroll.setPreferredSize(new Dimension(0, CONFIGURED_LIST_HEIGHT));
        scroll.setMinimumSize(new Dimension(0, CONFIGURED_LIST_HEIGHT));
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private static JPanel configuredTable(final JPanel rows, final JPanel headers) {
        final JPanel table = new JPanel(new BorderLayout());
        table.setOpaque(false);
        table.add(headers, BorderLayout.NORTH);
        table.add(configuredList(rows), BorderLayout.CENTER);
        return table;
    }

    private static JPanel agentHeaders() {
        final JPanel header = new JPanel(new BorderLayout(UiConstants.CONTENT_PADDING, 0));
        header.setOpaque(false);
        header.setBorder(
                new EmptyBorder(
                        0,
                        UiConstants.COMPONENT_GAP,
                        UiConstants.SPACING_XS,
                        UiConstants.COMPONENT_GAP));
        final JLabel agent = columnHeader("Agent");
        agent.setPreferredSize(new JTextField(14).getPreferredSize());
        header.add(agent, BorderLayout.WEST);
        final JPanel commands = new JPanel(new GridLayout(1, 2, 8, 0));
        commands.setOpaque(false);
        commands.add(columnHeader("New session command"));
        commands.add(columnHeader("Open command"));
        header.add(commands, BorderLayout.CENTER);
        header.add(columnHeader(""), BorderLayout.EAST);
        return header;
    }

    private static JPanel editorHeaders() {
        final JPanel header = new JPanel(new BorderLayout(UiConstants.CONTENT_PADDING, 0));
        header.setOpaque(false);
        header.setBorder(
                new EmptyBorder(
                        0,
                        UiConstants.COMPONENT_GAP,
                        UiConstants.SPACING_XS,
                        UiConstants.COMPONENT_GAP));
        final JLabel editor = columnHeader("Editor");
        editor.setPreferredSize(new JTextField(14).getPreferredSize());
        header.add(editor, BorderLayout.WEST);
        header.add(columnHeader("Command"), BorderLayout.CENTER);
        header.add(columnHeader(""), BorderLayout.EAST);
        return header;
    }

    private static JLabel columnHeader(final String text) {
        final JLabel header = UiFactory.label(text, Theme.FontSize.XS);
        header.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        return header;
    }

    private static void configureRow(final JPanel row) {
        row.setOpaque(false);
        row.setBorder(ROW_BORDER);
        row.setPreferredSize(new Dimension(0, ROW_HEIGHT));
        row.setMinimumSize(new Dimension(0, ROW_HEIGHT));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
    }

    private static void addToolRow(
            final JPanel rows,
            final List<JTextField> names,
            final List<JTextField> commands,
            final String name,
            final String command) {
        final JTextField nameField = new JTextField(name, 14);
        final JTextField commandField = new JTextField(command, 28);
        final JPanel row = new JPanel(new BorderLayout(UiConstants.CONTENT_PADDING, 0));
        configureRow(row);
        row.add(nameField, BorderLayout.WEST);
        row.add(commandField, BorderLayout.CENTER);
        final JButton remove = UiFactory.button("Remove");
        remove.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        remove.addActionListener(
                e -> {
                    names.remove(nameField);
                    commands.remove(commandField);
                    rows.remove(row);
                    rows.revalidate();
                    rows.repaint();
                });
        row.add(remove, BorderLayout.EAST);
        names.add(nameField);
        commands.add(commandField);
        rows.add(row);
    }

    private static List<Tool> configuredTools(
            final List<JTextField> names, final List<JTextField> commands) {
        final List<Tool> configured = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (!names.get(i).getText().isBlank() && !commands.get(i).getText().isBlank()) {
                configured.add(
                        new Tool(names.get(i).getText().trim(), commands.get(i).getText().trim()));
            }
        }
        return configured;
    }

    private static void addAgentRow(
            final JPanel rows,
            final List<JTextField> names,
            final List<JTextField> newSessionCommands,
            final List<JTextField> openCommands,
            final String name,
            final String newSessionCommand,
            final String openCommand) {
        final JTextField nameField = new JTextField(name, 14);
        final JTextField newSessionField = new JTextField(newSessionCommand, 28);
        final JTextField openField = new JTextField(openCommand, 28);
        newSessionField.setToolTipText(AGENT_VARIABLES_TOOLTIP);
        openField.setToolTipText(AGENT_VARIABLES_TOOLTIP);
        final JPanel row = new JPanel(new BorderLayout(UiConstants.CONTENT_PADDING, 0));
        configureRow(row);
        row.add(nameField, BorderLayout.WEST);
        final JPanel commands = new JPanel(new GridLayout(1, 2, 8, 0));
        commands.setOpaque(false);
        commands.add(newSessionField);
        commands.add(openField);
        row.add(commands, BorderLayout.CENTER);
        final JButton remove = UiFactory.button("Remove");
        remove.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        remove.addActionListener(
                e -> {
                    names.remove(nameField);
                    newSessionCommands.remove(newSessionField);
                    openCommands.remove(openField);
                    rows.remove(row);
                    rows.revalidate();
                    rows.repaint();
                });
        row.add(remove, BorderLayout.EAST);
        names.add(nameField);
        newSessionCommands.add(newSessionField);
        openCommands.add(openField);
        rows.add(row);
    }

    private static void styleTabs(final JTabbedPane tabs) {
        tabs.setTabPlacement(JTabbedPane.TOP);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setOpaque(false);
    }

    public static List<String> lines(final String value) {
        return java.util.Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
