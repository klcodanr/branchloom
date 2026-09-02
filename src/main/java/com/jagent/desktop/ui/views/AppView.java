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
import com.jagent.desktop.ui.components.CommandPalette;
import com.jagent.desktop.ui.components.ProjectTreePanel;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/** Application shell until the individual views are migrated to the current state model. */
public final class AppView extends JFrame {
    private static final Logger LOG = Logger.getLogger(AppView.class.getName());
    private final transient AppState state;
    private final transient AppStatePersistence persistence;
    private final transient WindowStatePersistence windowStatePersistence;
    private final transient ViewCoordinator viewCoordinator;
    private final transient ActionContext actionContext;
    private final JLabel placeholder = UiFactory.label("", Theme.FontSize.XL);
    private final JPanel content = new JPanel(new BorderLayout());
    private final ProjectTreePanel projectTreePanel;
    private transient View currentView;

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
        configureWindow();
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(8, 8, 8, 8));
        projectTreePanel = new ProjectTreePanel(actionContext);
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
                () -> currentWorkspaceView().ifPresent(AbstractWorkspaceView::closeActiveTerminal));
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
        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(projectTreePanel);
        splitPane.setRightComponent(content);
        projectTreePanel.setMinimumSize(new Dimension(220, 0));
        content.setMinimumSize(new Dimension(300, 0));
        splitPane.setDividerLocation(projectTreePanel.getPreferredSize().width);
        splitPane.setResizeWeight(0);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        shell.add(splitPane, BorderLayout.CENTER);
        setJMenuBar(AppMenuBar.create(actionContext));
        return shell;
    }

    private void render() {
        final var view = viewCoordinator.currentViewId();
        final Project project = state.currentProject();
        final Session session = state.currentSession();
        TerminalPanel.reconcile(state.terminals().keySet());
        final View topLevel;
        final JComponent rendered;
        try {
            topLevel = createTopLevelView(view, project, session);
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
            final String detail =
                    exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getClass().getSimpleName() + ": " + exception.getMessage();
            content.add(
                    UiFactory.empty(
                            "Could not open project view",
                            "The selected view failed to render: " + detail),
                    BorderLayout.CENTER);
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
        projectTreePanel.refresh(project, session);
        setJMenuBar(AppMenuBar.create(new ActionContext(viewCoordinator, state, this)));
        revalidate();
        repaint();
    }

    private View createTopLevelView(
            final ViewId view, final Project project, final Session session) {
        final ViewId resolvedView = resolveView(view, project, session);
        return switch (resolvedView) {
            case HOME -> new HomeView(actionContext);
            case PROJECT -> new ProjectView(actionContext, project);
            case SESSION -> new SessionView(actionContext);
            case SETTINGS -> new GlobalSettingsView(actionContext);
            case PROJECT_SETTINGS -> new ProjectSettingsView(actionContext);
            case PROBLEMS -> new ProblemsView();
            case RESOURCE_USAGE -> new ResourceUsageView();
        };
    }

    private ViewId resolveView(final ViewId view, final Project project, final Session session) {
        final ViewId requested = view == null ? ViewId.HOME : view;
        if (requiresProject(requested) && project == null) {
            return ViewId.HOME;
        }
        if (requested == ViewId.SESSION && session == null) {
            return ViewId.HOME;
        }
        return requested;
    }

    private boolean requiresProject(final ViewId view) {
        return view == ViewId.PROJECT || view == ViewId.PROJECT_SETTINGS || view == ViewId.SESSION;
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
