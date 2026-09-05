package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.services.persistence.AppStatePersistence;
import com.jagent.desktop.services.persistence.WindowStatePersistence;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.CreateSessionAction;
import com.jagent.desktop.ui.actions.CreateTerminalAction;
import com.jagent.desktop.ui.actions.FindAction;
import com.jagent.desktop.ui.actions.OpenSettingsAction;
import com.jagent.desktop.ui.actions.ProblemsAction;
import com.jagent.desktop.ui.actions.ResourceUsageAction;
import com.jagent.desktop.ui.components.AppIcon;
import com.jagent.desktop.ui.components.AppMenuBar;
import com.jagent.desktop.ui.components.BottomBar;
import com.jagent.desktop.ui.components.CommandPalette;
import com.jagent.desktop.ui.components.ProjectTreePanel;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiFactory;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.WorkspaceTreePanel;
import com.jagent.desktop.ui.utils.TerminalShortcutDispatcher;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Application shell until the individual views are migrated to the current state model. */
public final class AppView extends JFrame {
    private static final Logger LOG = Logger.getLogger(AppView.class.getName());
    private final transient AppState state;
    private final transient AppStatePersistence persistence;
    private final transient WindowStatePersistence windowStatePersistence;
    private final transient ViewCoordinator viewCoordinator;
    private final transient ActionContext actionContext;
    private final transient TopLevelViewFactory topLevelViewFactory;
    private final JLabel placeholder = UiFactory.label("", Theme.FontSize.XL);
    private final JPanel content = new JPanel(new BorderLayout());
    private final JPanel workspaceTree = new JPanel(new BorderLayout());
    private final JSplitPane workspaceSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private int workspaceDividerSize;
    private final ProjectTreePanel projectTreePanel;
    private final BottomBar bottomBar;
    private transient java.awt.KeyEventDispatcher terminalShortcutDispatcher;
    private transient View currentView;
    private transient WorkspaceTreePanel currentWorkspaceTree;

    public AppView() {
        this(Path.of(System.getProperty("user.home"), ".branchloom"));
    }

    public AppView(final Path dataDirectory) {
        super("Branchloom");
        state = AppStatePersistence.load(dataDirectory);
        persistence = new AppStatePersistence(state, dataDirectory);
        windowStatePersistence = new WindowStatePersistence(dataDirectory);
        viewCoordinator = new ViewCoordinator(state, ignored -> render());
        actionContext = new ActionContext(viewCoordinator, state, this);
        topLevelViewFactory = new TopLevelViewFactory(actionContext);
        configureWindow();
        content.setOpaque(false);
        content.setBorder(UiFactory.contentAreaBorder());
        workspaceTree.setOpaque(false);
        workspaceTree.setVisible(false);
        projectTreePanel = new ProjectTreePanel(actionContext);
        bottomBar =
                new BottomBar(
                        state,
                        viewCoordinator.backgroundJobs(),
                        () -> viewCoordinator.updateView(ViewId.HOME, null),
                        () -> new OpenSettingsAction(actionContext).execute(),
                        () -> new FindAction(actionContext).execute(),
                        () -> new ProblemsAction(actionContext).execute());
        content.add(placeholder, BorderLayout.CENTER);
        add(shell(actionContext), BorderLayout.CENTER);
        installShortcuts();
        render();
    }

    private void installShortcuts() {
        final JRootPane rootPane = getRootPane();
        final var inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        final var actionMap = rootPane.getActionMap();
        final int menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        for (int index = 1; index <= 9; index++) {
            final int terminalIndex = index;
            bind(
                    inputMap,
                    actionMap,
                    KeyStroke.getKeyStroke(KeyEvent.VK_0 + index, menuMask),
                    "select-terminal-" + index,
                    () ->
                            currentWorkspaceView()
                                    .ifPresent(view -> view.selectTerminal(terminalIndex)));
        }
        bind(
                inputMap,
                actionMap,
                KeyStroke.getKeyStroke('K', menuMask),
                "command-palette",
                this::openCommandPalette);
        bind(
                inputMap,
                actionMap,
                KeyStroke.getKeyStroke('W', menuMask),
                "close-terminal",
                () ->
                        currentWorkspaceView()
                                .ifPresent(
                                        view -> {
                                            if (!view.closeActiveFile()) {
                                                view.closeActiveTerminal();
                                            }
                                        }));
        bind(
                inputMap,
                actionMap,
                KeyStroke.getKeyStroke('R', menuMask | InputEvent.SHIFT_DOWN_MASK),
                "rename-terminal",
                () ->
                        currentWorkspaceView()
                                .ifPresent(AbstractWorkspaceView::renameActiveTerminal));
        bind(
                inputMap,
                actionMap,
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "clear-transient-focus",
                () -> {
                    javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner();
                });
        terminalShortcutDispatcher =
                new TerminalShortcutDispatcher(
                        inputMap,
                        actionMap,
                        component ->
                                component instanceof TerminalPanel
                                        || SwingUtilities.getAncestorOfClass(
                                                        TerminalPanel.class, component)
                                                != null);
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(terminalShortcutDispatcher);
    }

    private void bind(
            final javax.swing.InputMap inputMap,
            final javax.swing.ActionMap actionMap,
            final KeyStroke keyStroke,
            final String id,
            final Runnable operation) {
        inputMap.put(keyStroke, id);
        actionMap.put(id, new BoundAction(operation));
    }

    private static final class BoundAction extends AbstractAction {
        private final Runnable operation;

        private BoundAction(final Runnable operation) {
            super();
            this.operation = operation;
        }

        @Override
        public void actionPerformed(final ActionEvent event) {
            operation.run();
        }
    }

    private java.util.Optional<AbstractWorkspaceView> currentWorkspaceView() {
        final java.awt.Component rendered =
                content.getComponentCount() == 0 ? null : content.getComponent(0);
        return rendered instanceof AbstractWorkspaceView workspaceView
                ? java.util.Optional.of(workspaceView)
                : java.util.Optional.empty();
    }

    private void openCommandPalette() {
        final List<Action> actions =
                List.of(
                        new FindAction(actionContext),
                        new CreateProjectAction(actionContext),
                        new CreateSessionAction(actionContext),
                        new CreateTerminalAction(actionContext),
                        new OpenSettingsAction(actionContext),
                        new ProblemsAction(actionContext),
                        new ResourceUsageAction(actionContext));
        CommandPalette.open(
                this,
                "Command palette",
                actions.stream()
                        .filter(Action::enabled)
                        .map(action -> new CommandPalette.Choice(action.label(), action::execute))
                        .toList());
    }

    private void configureWindow() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent event) {
                        if (currentView != null) {
                            currentView.dispose();
                        }
                        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                .removeKeyEventDispatcher(terminalShortcutDispatcher);
                        saveWindowState();
                        persistence.close();
                        windowStatePersistence.close();
                    }
                });
        setMinimumSize(new Dimension(900, 600));
        final var windowState = windowStatePersistence.state();
        if (windowState != null
                && windowState.windowWidth >= 900
                && windowState.windowHeight >= 600) {
            setSize(windowState.windowWidth, windowState.windowHeight);
            setLocation(windowState.windowX, windowState.windowY);
        } else {
            setSize(1240, 760);
            setLocationRelativeTo(null);
        }
        setIconImage(AppIcon.image(64));
        UIManager.put("OptionPane.questionIcon", null);
        UIManager.put("OptionPane.informationIcon", null);
        UIManager.put("OptionPane.warningIcon", null);
        UIManager.put("OptionPane.errorIcon", null);
        Theme.apply(Theme.FlatLafTheme.from(state.appSettings().theme()));
    }

    private JPanel shell(final ActionContext actionContext) {
        final JPanel shell = new JPanel(new BorderLayout());
        shell.setOpaque(true);
        shell.setBackground(UIManager.getColor("Panel.background"));
        workspaceSplit.setLeftComponent(content);
        workspaceSplit.setRightComponent(workspaceTree);
        workspaceSplit.setResizeWeight(1.0);
        workspaceSplit.setContinuousLayout(true);
        workspaceSplit.setOpaque(false);
        workspaceSplit.setBorder(null);
        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(projectTreePanel);
        splitPane.setRightComponent(workspaceSplit);
        workspaceSplit.updateUI();
        workspaceDividerSize = splitPane.getDividerSize();
        workspaceSplit.setDividerSize(workspaceDividerSize);
        projectTreePanel.setMinimumSize(new Dimension(220, 0));
        content.setMinimumSize(new Dimension(300, 0));
        splitPane.setDividerLocation(projectTreePanel.getPreferredSize().width);
        splitPane.setResizeWeight(0);
        splitPane.setContinuousLayout(true);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        shell.add(splitPane, BorderLayout.CENTER);
        shell.add(bottomBar, BorderLayout.SOUTH);
        setJMenuBar(AppMenuBar.create(actionContext));
        return shell;
    }

    private void render() {
        final var view = viewCoordinator.currentViewId();
        final Project project = state.currentProject();
        final Session session = state.currentSession();
        bottomBar.refresh();
        TerminalPanel.reconcile(state.terminals().keySet());
        final View topLevel;
        final JComponent rendered;
        try {
            topLevel = topLevelViewFactory.create(view, project, session);
            rendered = topLevel.render();
        } catch (RuntimeException exception) {
            LOG.log(
                    Level.SEVERE,
                    "Failed to render view "
                            + view
                            + " for project "
                            + (project == null ? "<none>" : project.name()),
                    exception);
            if (currentView != null) {
                currentView.detach();
                currentView = null;
            }
            content.removeAll();
            workspaceTree.removeAll();
            final String detail =
                    exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getClass().getSimpleName() + ": " + exception.getMessage();
            content.add(
                    UiFactory.empty(
                            "Could not open project view",
                            "The selected view failed to render: " + detail),
                    BorderLayout.CENTER);
            workspaceSplit.setRightComponent(null);
            workspaceTree.setMinimumSize(new Dimension(0, 0));
            workspaceTree.setVisible(false);
            content.revalidate();
            content.repaint();
            return;
        }
        if (currentView != null) {
            currentView.detach();
        }
        currentView = topLevel;
        content.removeAll();
        content.add(rendered, BorderLayout.CENTER);
        workspaceTree.removeAll();
        currentWorkspaceTree = null;
        if (topLevel instanceof AbstractWorkspaceView workspaceView
                && Files.isDirectory(workspaceView.workspacePath())) {
            workspaceSplit.setRightComponent(workspaceTree);
            final WorkspaceTreePanel tree = workspaceView.workspaceTreePanel();
            tree.setBorder(UiFactory.contentAreaBorder());
            tree.setHideAction(this::hideWorkspaceTree);
            currentWorkspaceTree = tree;
            hideWorkspaceTree();
        } else {
            workspaceSplit.setRightComponent(null);
            workspaceTree.setMinimumSize(new Dimension(0, 0));
            workspaceTree.setVisible(false);
        }
        projectTreePanel.refresh(project, session);
        workspaceTree.revalidate();
        workspaceTree.repaint();
        setJMenuBar(AppMenuBar.create(new ActionContext(viewCoordinator, state, this)));
        revalidate();
        repaint();
    }

    private void showWorkspaceTree() {
        if (currentWorkspaceTree == null) {
            return;
        }
        workspaceTree.removeAll();
        workspaceTree.add(currentWorkspaceTree, BorderLayout.CENTER);
        workspaceTree.setMinimumSize(new Dimension(220, 0));
        workspaceTree.setVisible(true);
        workspaceSplit.setDividerSize(workspaceDividerSize);
        workspaceSplit.setDividerLocation(0.75);
        workspaceTree.revalidate();
        workspaceTree.repaint();
    }

    private void hideWorkspaceTree() {
        if (currentWorkspaceTree == null) {
            return;
        }
        final JPanel dock = new JPanel(new BorderLayout());
        dock.setOpaque(false);
        dock.setMinimumSize(new Dimension(32, 0));
        dock.setPreferredSize(new Dimension(32, 0));
        final JButton filesButton = UiFactory.iconButton(UiIcons.folderOpen());
        filesButton.setName("show-files-button");
        filesButton.setToolTipText("Show files");
        filesButton.getAccessibleContext().setAccessibleName("Show files");
        filesButton.addActionListener(ignored -> showWorkspaceTree());
        dock.add(filesButton, BorderLayout.NORTH);
        workspaceTree.removeAll();
        workspaceTree.add(dock, BorderLayout.CENTER);
        workspaceTree.setMinimumSize(new Dimension(32, 0));
        workspaceTree.setPreferredSize(new Dimension(32, 0));
        workspaceTree.setVisible(true);
        workspaceSplit.setDividerSize(0);
        workspaceTree.revalidate();
        workspaceTree.repaint();
        SwingUtilities.invokeLater(
                () ->
                        workspaceSplit.setDividerLocation(
                                workspaceSplit.getWidth() - dock.getPreferredSize().width));
    }

    private void saveWindowState() {
        if (getExtendedState() != NORMAL || windowStatePersistence.state() == null) {
            return;
        }
        final Rectangle bounds = getBounds();
        final var windowState = windowStatePersistence.state();
        windowState.windowX = bounds.x;
        windowState.windowY = bounds.y;
        windowState.windowWidth = bounds.width;
        windowState.windowHeight = bounds.height;
        windowStatePersistence.update(windowState);
    }
}
