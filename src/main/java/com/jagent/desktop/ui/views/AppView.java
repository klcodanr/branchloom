package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.View;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.PullRequestWatcher.PullRequestStatus;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.CreateSessionAction;
import com.jagent.desktop.ui.actions.CreateTerminalAction;
import com.jagent.desktop.ui.actions.FindAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import com.jagent.desktop.ui.actions.OpenSettingsAction;
import com.jagent.desktop.ui.actions.ProblemsAction;
import com.jagent.desktop.ui.actions.RefreshSettingsAction;
import com.jagent.desktop.ui.actions.ResourceUsageAction;
import com.jagent.desktop.ui.components.CommandPalette;
import com.jagent.desktop.ui.components.EmptyStatePanel;
import com.jagent.desktop.ui.components.ProjectTreePanel;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiBorders;
import com.jagent.desktop.ui.layout.BottomBar;
import com.jagent.desktop.ui.utils.TerminalShortcutDispatcher;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Application shell until the individual views are migrated to the current state model. */
public final class AppView extends JPanel {
    private static final Logger LOG = LoggerFactory.getLogger(AppView.class);
    private final transient ViewController viewController;
    private final transient ActionContext actionContext;
    private final JLabel placeholder = new JLabel();
    private final JPanel content = new JPanel(new BorderLayout());
    private final ProjectTreePanel projectTreePanel;
    private final BottomBar bottomBar;
    private transient KeyEventDispatcher terminalShortcutDispatcher;

    public AppView(final ActionContext actionContext) {
        super(new BorderLayout());
        this.actionContext = actionContext;
        this.viewController = actionContext.viewController();
        placeholder.setFont(Theme.font(Theme.FontSize.XL));
        Theme.apply(Theme.FlatLafTheme.from(actionContext.appState().appSettings().theme()));
        content.setOpaque(false);
        content.setBorder(UiBorders.contentArea());
        projectTreePanel = new ProjectTreePanel(actionContext);
        bottomBar = new BottomBar(actionContext);
        content.add(placeholder, BorderLayout.CENTER);
        add(shell(), BorderLayout.CENTER);
        installShortcuts();
        render();
    }

    private void installShortcuts() {
        final var inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final var actionMap = getActionMap();
        final int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
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
            final InputMap inputMap,
            final ActionMap actionMap,
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

    private Optional<AbstractWorkspaceView> currentWorkspaceView() {
        final Component rendered =
                content.getComponentCount() == 0 ? null : content.getComponent(0);
        return rendered instanceof AbstractWorkspaceView workspaceView
                ? Optional.of(workspaceView)
                : Optional.empty();
    }

    private void openCommandPalette() {
        final List<Action> actions =
                List.of(
                        new FindAction(actionContext),
                        new CreateProjectAction(actionContext),
                        new ImportProjectAction(actionContext),
                        new CreateSessionAction(actionContext),
                        new CreateTerminalAction(actionContext),
                        new OpenSettingsAction(actionContext),
                        new RefreshSettingsAction(actionContext),
                        new ProblemsAction(actionContext),
                        new ResourceUsageAction(actionContext));
        CommandPalette.open(
                actionContext.window(),
                "Command palette",
                actions.stream()
                        .filter(Action::enabled)
                        .map(action -> new CommandPalette.Choice(action.label(), action::execute))
                        .toList());
    }

    public void disposeContent() {
        final View currentView = viewController.currentView();
        currentView.dispose();
        if (terminalShortcutDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(terminalShortcutDispatcher);
            terminalShortcutDispatcher = null;
        }
    }

    public void updatePullRequestStatus(final Session session, final PullRequestStatus status) {
        projectTreePanel.updatePullRequestStatus(session, status);
    }

    private JPanel shell() {
        final JPanel shell = new JPanel(new BorderLayout());
        shell.setOpaque(true);
        shell.setBackground(Theme.Colors.background());
        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(projectTreePanel);
        splitPane.setRightComponent(content);
        projectTreePanel.setMinimumSize(new Dimension(220, 0));
        content.setMinimumSize(new Dimension(300, 0));
        splitPane.setDividerLocation(projectTreePanel.getPreferredSize().width);
        splitPane.setResizeWeight(0);
        splitPane.setContinuousLayout(true);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        shell.add(splitPane, BorderLayout.CENTER);
        shell.add(bottomBar, BorderLayout.SOUTH);
        return shell;
    }

    public void render() {
        final var view = viewController.currentViewId();
        final View topLevel;
        final JComponent rendered;
        try {
            topLevel = viewController.currentView();
            rendered = topLevel.render();
            bottomBar.updateView(topLevel);
        } catch (RuntimeException exception) {
            LOG.error("Failed to render view {} ", view, exception);
            content.removeAll();
            final String detail =
                    exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getClass().getSimpleName() + ": " + exception.getMessage();
            content.add(
                    new EmptyStatePanel(
                            "Could not open "
                                    + (view == null
                                            ? "view"
                                            : view.name().toLowerCase(Locale.ROOT) + " view"),
                            "The selected view failed to render: " + detail),
                    BorderLayout.CENTER);
            content.revalidate();
            content.repaint();
            return;
        }
        content.removeAll();
        content.add(rendered, BorderLayout.CENTER);
        projectTreePanel.refresh();
        revalidate();
        repaint();
    }
}
