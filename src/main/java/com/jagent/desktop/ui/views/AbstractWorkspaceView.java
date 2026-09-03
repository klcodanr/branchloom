package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.ui.components.FileViewer;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiFactory;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.WorkspaceSplitPane;
import com.jagent.desktop.ui.components.WorkspaceTerminalTabs;
import com.jagent.desktop.ui.components.WorkspaceTreePanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.border.EmptyBorder;

abstract class AbstractWorkspaceView extends JPanel implements View {
    protected final transient ActionContext actionContext;
    protected final transient ViewCoordinator viewCoordinator;
    protected final JTabbedPane tabs = new JTabbedPane();
    protected WorkspaceSplitPane contentSplit = new WorkspaceSplitPane(tabs, JPanel::new);
    private transient WorkspaceTerminalTabs terminalTabs;
    protected Map<TerminalPanel, TerminalState> terminalStates;
    protected Map<TerminalPanel, TerminalId> terminalIds;
    private final ViewId viewId;
    private JLabel titleLabel;
    private String titleText;

    protected AbstractWorkspaceView(final ActionContext actionContext, final ViewId viewId) {
        super(new BorderLayout(0, 16));
        this.actionContext = actionContext;
        this.viewCoordinator = actionContext.viewCoordinator();
        this.viewId = viewId;
    }

    protected final void initializeWorkspace(final String title) {
        titleText = title;
        add(header(), BorderLayout.NORTH);
        tabs.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded");
        final JButton addTerminal = new JButton(UiIcons.plus());
        addTerminal.setToolTipText("New terminal");
        addTerminal.getAccessibleContext().setAccessibleName("New terminal");
        addTerminal.addActionListener(event -> openTerminal(workspacePath()));
        final JToolBar trailingComponent = new JToolBar();
        trailingComponent.setFloatable(false);
        trailingComponent.setBorder(null);
        trailingComponent.add(addTerminal);
        tabs.putClientProperty("JTabbedPane.trailingComponent", trailingComponent);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        contentSplit = new WorkspaceSplitPane(tabs, this::workspace);
        add(contentSplit, BorderLayout.CENTER);
        terminalTabs =
                new WorkspaceTerminalTabs(
                        tabs,
                        ignored -> terminalStateChanged(),
                        (terminal, terminalId) -> {
                            if (terminalId != null) {
                                actionContext.appState().removeTerminal(terminalId);
                            }
                            terminalClosed();
                        },
                        this::terminalRenamed);
        terminalStates = terminalTabs.states();
        terminalIds = terminalTabs.ids();
        addDefaultTabs();
        tabs.addChangeListener(event -> updateCurrentTerminal());
    }

    private JPanel header() {
        final JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 0, 12));
        final JPanel titleArea = new JPanel();
        titleArea.setOpaque(false);
        titleArea.setLayout(new javax.swing.BoxLayout(titleArea, javax.swing.BoxLayout.Y_AXIS));
        titleLabel = UiFactory.label(titleText, Theme.FontSize.XXL);
        titleLabel.setMinimumSize(new Dimension(0, titleLabel.getMinimumSize().height));
        titleArea.add(titleLabel);
        addTitleDetails(titleArea);
        header.add(titleArea, BorderLayout.CENTER);

        final JButton actions = UiFactory.iconButton(UiIcons.ellipsis());
        actions.setToolTipText("Actions");
        actions.getAccessibleContext().setAccessibleName("Actions");
        actions.addActionListener(event -> showActions(actions));
        final JButton files = UiFactory.iconButton(UiIcons.folderOpen());
        files.setToolTipText("Show files");
        files.getAccessibleContext().setAccessibleName("Show files");
        files.addActionListener(event -> showWorkspace());
        final JPanel actionArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actionArea.setOpaque(false);
        actionArea.setBorder(new EmptyBorder(0, 12, 0, 0));
        actionArea.add(actions);
        actionArea.add(files);
        actionArea.setMinimumSize(actionArea.getPreferredSize());
        header.add(actionArea, BorderLayout.EAST);
        return header;
    }

    private JPanel workspace() {
        final JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        workspace.setBorder(new EmptyBorder(16, 2, 2, 2));
        workspace.add(
                new WorkspaceTreePanel(
                        actionContext, workspacePath(), this::openTerminal, this::openFile),
                BorderLayout.CENTER);
        return workspace;
    }

    protected abstract Path workspacePath();

    protected abstract void addTitleDetails(JPanel titleArea);

    protected abstract void addDefaultTabs();

    protected abstract void showActions(JButton button);

    protected abstract void openTerminal(Path path);

    protected abstract void terminalStateChanged();

    protected abstract void terminalClosed();

    protected final void mountTerminal(
            final String title,
            final TerminalId terminalId,
            final TerminalPanel terminal,
            final boolean selected) {
        terminalTabs.mount(title, terminalId, terminal, selected);
    }

    public void closeActiveTerminal() {
        terminalTabs.closeActive();
    }

    public void renameActiveTerminal() {
        terminalTabs.renameActive(this);
    }

    protected final void terminalRenamed(final TerminalPanel terminal, final String title) {
        final TerminalId terminalId = terminalIds.get(terminal);
        if (terminalId == null) {
            return;
        }
        final Terminal current = actionContext.appState().terminals().get(terminalId);
        if (current != null) {
            actionContext.appState().updateTerminal(terminalId, current.withTitle(title));
        }
    }

    protected final JLabel titleLabel() {
        return titleLabel;
    }

    protected final void showWorkspace() {
        contentSplit.showWorkspace();
    }

    protected final void openFile(final Path file) {
        final Path normalized = file.toAbsolutePath().normalize();
        for (int index = 0; index < tabs.getTabCount(); index++) {
            if (tabs.getComponentAt(index) instanceof javax.swing.JComponent component
                    && normalized.equals(component.getClientProperty("workspaceFile"))) {
                tabs.setSelectedIndex(index);
                return;
            }
        }
        final Path name = normalized.getFileName();
        if (name == null) {
            return;
        }
        final FileViewer viewer = new FileViewer(workspacePath(), normalized);
        viewer.putClientProperty("workspaceFile", normalized);
        tabs.addTab(name.toString(), viewer);
        viewer.putClientProperty("JTabbedPane.tabClosable", true);
        viewer.putClientProperty(
                "JTabbedPane.tabCloseCallback",
                (java.util.function.IntConsumer)
                        index -> {
                            if (index >= 0
                                    && index < tabs.getTabCount()
                                    && viewer.equals(tabs.getComponentAt(index))) {
                                tabs.removeTabAt(index);
                            }
                        });
        tabs.setSelectedComponent(viewer);
    }

    protected final void selectTerminal(final int index) {
        terminalTabs.select(index);
    }

    protected final void openDefaultTab() {
        if (tabs.getTabCount() > 0) {
            tabs.setSelectedIndex(0);
        }
    }

    protected final void updateCurrentTerminal() {
        final int selectedIndex = tabs.getSelectedIndex();
        viewCoordinator.updateSelectedTab(id(), selectedIndex);
        TerminalId currentTerminal = null;
        if (selectedIndex > 0
                && tabs.getComponentAt(selectedIndex) instanceof TerminalPanel terminal) {
            currentTerminal = terminalIds.get(terminal);
        }
        actionContext.appState().updateCurrentTerminal(currentTerminal);
    }

    protected final boolean selectTerminal(
            final TerminalId terminalId, final Map<TerminalPanel, TerminalId> terminalIds) {
        if (terminalId == null) {
            return false;
        }
        for (final Map.Entry<TerminalPanel, TerminalId> entry : terminalIds.entrySet()) {
            if (entry.getValue().equals(terminalId)) {
                tabs.setSelectedComponent(entry.getKey());
                return true;
            }
        }
        return false;
    }

    protected final boolean restoreSelectedTab(
            final boolean hasSelectedTab, final int selectedTab) {
        if (!hasSelectedTab) {
            return false;
        }
        if (selectedTab < tabs.getTabCount()) {
            tabs.setSelectedIndex(selectedTab);
            return true;
        }
        return false;
    }

    @Override
    public final ViewId id() {
        return viewId;
    }

    @Override
    public final String title() {
        return titleText;
    }

    @Override
    public final JPanel render() {
        return this;
    }

    @Override
    public void detach() {
        terminalTabs.detach();
    }

    @Override
    public void dispose() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof TerminalPanel terminal) {
                terminal.dispose();
            }
        }
    }
}
