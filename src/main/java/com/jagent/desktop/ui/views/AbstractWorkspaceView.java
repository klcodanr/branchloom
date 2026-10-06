package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.components.FileViewer;
import com.jagent.desktop.ui.components.IconButton;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiBorders;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.WorkspaceTabMenu;
import com.jagent.desktop.ui.components.WorkspaceTerminalTabs;
import com.jagent.desktop.ui.layout.WorkspaceSplitPane;
import com.jagent.desktop.ui.layout.WorkspaceTreePanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

abstract class AbstractWorkspaceView extends JPanel implements View {
    protected final transient ActionContext actionContext;
    protected final transient ViewCoordinator viewCoordinator;
    protected final JTabbedPane tabs = new JTabbedPane();
    protected WorkspaceSplitPane contentSplit = new WorkspaceSplitPane(tabs);
    private final transient WorkspaceTreeSidebarController workspaceTreeSidebar =
            new WorkspaceTreeSidebarController();
    private transient WorkspaceTerminalTabs terminalTabs;
    protected Map<TerminalPanel, TerminalId> terminalIds;
    private final ViewId viewId;
    private JLabel titleLabel;
    private String titleText;

    protected AbstractWorkspaceView(final ActionContext actionContext, final ViewId viewId) {
        super(new BorderLayout(0, UiConstants.SECTION_PADDING));
        this.actionContext = actionContext;
        this.viewCoordinator = actionContext.viewCoordinator();
        this.viewId = viewId;
    }

    protected final void initializeWorkspace(final String title) {
        titleText = title;
        add(header(), BorderLayout.NORTH);
        tabs.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded");
        final JButton addTerminal = new IconButton(UiIcons.plus(), "New terminal");
        addTerminal.addActionListener(event -> openTerminal(workspacePath()));
        final JToolBar trailingComponent = new JToolBar();
        trailingComponent.setFloatable(false);
        trailingComponent.setBorder(null);
        trailingComponent.add(addTerminal);
        tabs.putClientProperty("JTabbedPane.trailingComponent", trailingComponent);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        contentSplit = new WorkspaceSplitPane(tabs);
        contentSplit.setResizeWeight(1.0);
        contentSplit.setOpaque(false);
        contentSplit.setBorder(null);
        add(contentSplit, BorderLayout.CENTER);
        workspaceTreeSidebar.configure(
                contentSplit, workspacePath(), actionContext, this::openTerminal, this::openFile);
        terminalTabs =
                new WorkspaceTerminalTabs(
                        tabs,
                        (terminal, terminalId) -> {
                            if (terminalId != null) {
                                actionContext.appState().removeTerminal(terminalId);
                            }
                            terminalClosed();
                        },
                        this::terminalRenamed);
        terminalIds = terminalTabs.ids();
        new WorkspaceTabMenu(tabs, terminalTabs);
        addDefaultTabs();
        tabs.addChangeListener(event -> updateCurrentTerminal());
    }

    private JPanel header() {
        final JPanel header = new JPanel(new BorderLayout(UiConstants.COMPONENT_GAP, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 0, UiConstants.COMPONENT_GAP));
        final JPanel titleArea = new JPanel();
        titleArea.setOpaque(false);
        titleArea.setLayout(new BoxLayout(titleArea, BoxLayout.Y_AXIS));
        titleLabel = new JLabel(titleText);
        titleLabel.setFont(Theme.font(Theme.FontSize.XXL));
        titleLabel.setMinimumSize(new Dimension(0, titleLabel.getMinimumSize().height));
        titleArea.add(titleLabel);
        addTitleDetails(titleArea);
        header.add(titleArea, BorderLayout.CENTER);

        if (Files.isDirectory(workspacePath())) {
            final JButton actions = new IconButton(UiIcons.ellipsis(), "Actions");
            actions.setName("workspace-actions-button");
            actions.addActionListener(event -> showActions(actions));
            final JPanel actionArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            actionArea.setOpaque(false);
            actionArea.setBorder(new EmptyBorder(0, UiConstants.COMPONENT_GAP, 0, 0));
            actionArea.add(actions);
            actionArea.setMinimumSize(actionArea.getPreferredSize());
            header.add(actionArea, BorderLayout.EAST);
        }
        return header;
    }

    protected abstract Path workspacePath();

    protected abstract void addTitleDetails(JPanel titleArea);

    protected abstract void addDefaultTabs();

    protected abstract void showActions(JButton button);

    protected abstract void openTerminal(Path path);

    protected void terminalClosed() {
        updateCurrentTerminal();
    }

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

    public boolean closeActiveFile() {
        return Optional.ofNullable(tabs.getSelectedComponent())
                .filter(JComponent.class::isInstance)
                .map(JComponent.class::cast)
                .filter(component -> component.getClientProperty("workspaceFile") != null)
                .map(
                        component -> {
                            tabs.remove(component);
                            return true;
                        })
                .orElse(false);
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

    protected final void openFile(final Path file) {
        openFile(file, false);
    }

    private void openFile(final Path file, final boolean filtered) {
        final Path normalized = file.toAbsolutePath().normalize();
        for (int index = 0; index < tabs.getTabCount(); index++) {
            if (tabs.getComponentAt(index) instanceof JComponent component
                    && normalized.equals(component.getClientProperty("workspaceFile"))) {
                tabs.setSelectedIndex(index);
                return;
            }
        }
        final Path name = normalized.getFileName();
        if (name == null) {
            return;
        }
        final FileViewer viewer = new FileViewer(workspacePath(), normalized, filtered);
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
        updateSelectedTab(selectedIndex);
        TerminalId currentTerminal = null;
        if (selectedIndex > 0
                && tabs.getComponentAt(selectedIndex) instanceof TerminalPanel terminal) {
            currentTerminal = terminalIds.get(terminal);
        }
        actionContext.appState().updateCurrentTerminal(currentTerminal);
    }

    protected void updateSelectedTab(final int selectedIndex) {
        viewCoordinator.updateSelectedTab(id(), selectedIndex);
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

    private static final class WorkspaceTreeSidebarController {
        private static final int EXPANDED_WORKSPACE_TREE_WIDTH = 220;
        private static final int COLLAPSED_WORKSPACE_TREE_WIDTH = 32;
        private static final int WORKSPACE_TREE_DIVIDER_SIZE = 8;
        private final JPanel workspaceTreeContainer = new JPanel(new BorderLayout());
        private WorkspaceSplitPane contentSplit;

        private WorkspaceTreeSidebarController() {
            super();
            workspaceTreeContainer.setOpaque(false);
        }

        private void configure(
                final WorkspaceSplitPane splitPane,
                final Path workspacePath,
                final ActionContext actionContext,
                final Consumer<Path> openTerminal,
                final BiConsumer<Path, Boolean> openFile) {
            workspaceTreeContainer.removeAll();
            contentSplit = splitPane;
            if (!Files.isDirectory(workspacePath)) {
                contentSplit.setRightComponent(null);
                return;
            }
            final WorkspaceTreePanel treePanel =
                    new WorkspaceTreePanel(actionContext, workspacePath, openTerminal, openFile);
            treePanel.setBorder(UiBorders.contentArea());
            treePanel.setHideAction(() -> hideWorkspaceTree(treePanel));
            contentSplit.setRightComponent(workspaceTreeContainer);
            hideWorkspaceTree(treePanel);
        }

        private void showWorkspaceTree(final WorkspaceTreePanel treePanel) {
            workspaceTreeContainer.removeAll();
            workspaceTreeContainer.add(treePanel, BorderLayout.CENTER);
            workspaceTreeContainer.setMinimumSize(new Dimension(EXPANDED_WORKSPACE_TREE_WIDTH, 0));
            workspaceTreeContainer.setPreferredSize(
                    new Dimension(EXPANDED_WORKSPACE_TREE_WIDTH, 0));
            contentSplit.setDividerSize(WORKSPACE_TREE_DIVIDER_SIZE);
            contentSplit.setDividerLocation(0.75);
            workspaceTreeContainer.revalidate();
            workspaceTreeContainer.repaint();
        }

        private void hideWorkspaceTree(final WorkspaceTreePanel treePanel) {
            final JPanel dock = new JPanel(new BorderLayout());
            dock.setOpaque(false);
            dock.setMinimumSize(new Dimension(COLLAPSED_WORKSPACE_TREE_WIDTH, 0));
            dock.setPreferredSize(new Dimension(COLLAPSED_WORKSPACE_TREE_WIDTH, 0));
            final JButton filesButton = new IconButton(UiIcons.folderOpen(), "Show files");
            filesButton.setName("show-files-button");
            filesButton.addActionListener(ignored -> showWorkspaceTree(treePanel));
            dock.add(filesButton, BorderLayout.NORTH);
            workspaceTreeContainer.removeAll();
            workspaceTreeContainer.add(dock, BorderLayout.CENTER);
            workspaceTreeContainer.setMinimumSize(new Dimension(COLLAPSED_WORKSPACE_TREE_WIDTH, 0));
            workspaceTreeContainer.setPreferredSize(
                    new Dimension(COLLAPSED_WORKSPACE_TREE_WIDTH, 0));
            contentSplit.setDividerSize(0);
            workspaceTreeContainer.revalidate();
            workspaceTreeContainer.repaint();
            SwingUtilities.invokeLater(
                    () ->
                            contentSplit.setDividerLocation(
                                    contentSplit.getWidth() - dock.getPreferredSize().width));
        }
    }
}
