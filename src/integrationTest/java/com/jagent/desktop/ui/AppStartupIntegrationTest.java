package com.jagent.desktop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.persistence.AppStatePersistence;
import com.jagent.desktop.ui.views.AppView;
import com.jagent.desktop.ui.views.HomeView;
import com.jagent.desktop.ui.views.ProblemsView;
import com.jagent.desktop.ui.views.ProjectView;
import com.jagent.desktop.ui.views.ResourceUsageView;
import com.jagent.desktop.ui.views.SessionView;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppStartupIntegrationTest {
    private static final String SAVE_BUTTON = "Save";

    @TempDir private Path dataDirectory;

    @Test
    void startsWithIsolatedDataDirectoryAndNavigatesToSettings() {
        final AppView app = GuiActionRunner.execute(() -> new AppView(dataDirectory));
        try {
            assertEquals("Branchloom", app.getTitle(), "application title should match");

            GuiActionRunner.execute(() -> app.setVisible(true));
            assertTrue(
                    findButtonWithText(app, SAVE_BUTTON) == null,
                    "application should initially render home");

            GuiActionRunner.execute(
                    () -> {
                        final JButton button = findButton(app, "settings-button");
                        assertTrue(button != null, "settings button should be available");
                        button.doClick();
                    });
            assertTrue(
                    findButtonWithText(app, SAVE_BUTTON) != null,
                    "settings navigation should render the settings view");
            GuiActionRunner.execute(
                    () -> {
                        final JButton button = findButton(app, "home-button");
                        assertTrue(button != null, "home button should be available");
                        button.doClick();
                    });
            assertTrue(
                    find(app, HomeView.class) != null,
                    "home navigation should render the dashboard");
        } finally {
            GuiActionRunner.execute(
                    () -> {
                        app.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        app.dispatchEvent(new WindowEvent(app, WindowEvent.WINDOW_CLOSING));
                    });
        }

        assertTrue(
                Files.exists(dataDirectory.resolve("windowState.json")),
                "window state should be persisted in the temporary directory");
    }

    @Test
    void reloadsPersistedProjectAndOpensItFromTheProjectTree() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Demo", dataDirectory.toString(), null));
        try (AppStatePersistence persistence = new AppStatePersistence(state, dataDirectory)) {
            persistence.persist();
        }

        final AppView app = GuiActionRunner.execute(() -> new AppView(dataDirectory));
        try {
            GuiActionRunner.execute(() -> app.setVisible(true));
            final JTree tree = find(app, JTree.class);
            assertTrue(tree != null, "persisted project tree should be rendered");

            GuiActionRunner.execute(() -> selectProject(tree));

            assertEquals(
                    projectId,
                    AppStatePersistence.load(dataDirectory).projects().keySet().stream()
                            .findFirst()
                            .orElse(null),
                    "project ID should survive reload");
            assertTrue(
                    find(app, ProjectView.class) != null,
                    "selecting the project should open its view");
        } finally {
            close(app);
        }
    }

    @Test
    void doesNotShowWorkspaceActionsWhenWorkspaceIsUnavailable() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(
                new Project(
                        "Unavailable",
                        dataDirectory.resolve("missing-workspace").toString(),
                        null));
        try (AppStatePersistence persistence = new AppStatePersistence(state, dataDirectory)) {
            persistence.persist();
        }

        final AppView app = GuiActionRunner.execute(() -> new AppView(dataDirectory));
        try {
            GuiActionRunner.execute(() -> app.setVisible(true));
            final JTree tree = find(app, JTree.class);
            GuiActionRunner.execute(() -> selectProject(tree));

            assertTrue(
                    findButton(app, "workspace-actions-button") == null,
                    "unavailable workspaces should not show workspace actions");
        } finally {
            close(app);
        }
    }

    @Test
    void savesSettingsChangedThroughTheApplicationView() {
        final AppView app = GuiActionRunner.execute(() -> new AppView(dataDirectory));
        final String updatedTemplate = "custom/{sessionSlug}";
        try {
            GuiActionRunner.execute(() -> app.setVisible(true));
            GuiActionRunner.execute(
                    () -> {
                        final JButton settings = findButton(app, "settings-button");
                        assertTrue(settings != null, "settings button should be available");
                        settings.doClick();
                    });
            GuiActionRunner.execute(
                    () -> {
                        final JTextArea worktree =
                                findTextArea(app, Defaults.DEFAULT_WORKTREE_TEMPLATE);
                        assertTrue(worktree != null, "worktree setting should be rendered");
                        worktree.setText(updatedTemplate);
                        final JButton save = findButtonWithText(app, SAVE_BUTTON);
                        assertTrue(save != null, "settings save button should be available");
                        save.doClick();
                    });
        } finally {
            close(app);
        }

        assertEquals(
                updatedTemplate,
                AppStatePersistence.load(dataDirectory).appSettings().worktreeTemplate(),
                "settings changed in the application should be persisted");
    }

    @Test
    void walksTheMainApplicationScreens() throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Demo", dataDirectory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                "Feature",
                                "Shell",
                                "Inspect the project",
                                dataDirectory.toString()));
        state.addTerminal(sessionId, new Terminal(sessionId, "Shell", "true"));
        try (AppStatePersistence persistence = new AppStatePersistence(state, dataDirectory)) {
            persistence.persist();
        }

        final AppView app = GuiActionRunner.execute(() -> new AppView(dataDirectory));
        try {
            GuiActionRunner.execute(() -> app.setVisible(true));
            assertTrue(find(app, HomeView.class) != null, "startup should show home");

            final JTree tree = find(app, JTree.class);
            assertTrue(tree != null, "project tree should be available");
            GuiActionRunner.execute(() -> selectProject(tree));
            assertTrue(
                    find(app, ProjectView.class) != null, "project selection should open project");

            clickMenuItem(app, "Project", "Settings");
            assertTrue(findLabel(app, "Project settings") != null, "project settings should open");

            GuiActionRunner.execute(() -> selectProject(tree));
            GuiActionRunner.execute(() -> selectSession(tree));
            assertTrue(
                    find(app, SessionView.class) != null, "session selection should open session");

            clickButton(app, "settings-button");
            assertTrue(findButtonWithText(app, SAVE_BUTTON) != null, "global settings should open");

            clickMenuItem(app, "View", "Problems");
            assertTrue(find(app, ProblemsView.class) != null, "problems should open");

            clickMenuItem(app, "View", "Resource Usage");
            assertTrue(find(app, ResourceUsageView.class) != null, "resource usage should open");
        } finally {
            close(app);
        }
    }

    private static void close(final AppView app) {
        GuiActionRunner.execute(
                () -> {
                    app.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    app.dispatchEvent(new WindowEvent(app, WindowEvent.WINDOW_CLOSING));
                });
    }

    private static void selectProject(final JTree tree) {
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        final DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getChildAt(2);
        final DefaultMutableTreeNode project = (DefaultMutableTreeNode) group.getChildAt(0);
        tree.setSelectionPath(new TreePath(project.getPath()));
    }

    private static void selectSession(final JTree tree) {
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        final DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getChildAt(2);
        final DefaultMutableTreeNode project = (DefaultMutableTreeNode) group.getChildAt(0);
        final DefaultMutableTreeNode session = (DefaultMutableTreeNode) project.getChildAt(0);
        tree.setSelectionPath(new TreePath(session.getPath()));
    }

    private static JButton findButton(final Container root, final String name) {
        for (final Component child : root.getComponents()) {
            if (child instanceof JButton button && name.equals(button.getName())) {
                return button;
            }
            if (child instanceof Container container) {
                final JButton result = findButton(container, name);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T find(final Container root, final Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        for (final Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                final T result = find(container, type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JButton findButtonWithText(final Container root, final String text) {
        for (final Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container container) {
                final JButton result = findButtonWithText(container, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static void clickButton(final Container root, final String name) {
        GuiActionRunner.execute(
                () -> {
                    final JButton button = findButton(root, name);
                    assertTrue(button != null, "button should be available: " + name);
                    button.doClick();
                });
    }

    private static void clickMenuItem(
            final Container root, final String menuText, final String itemText) {
        GuiActionRunner.execute(
                () -> {
                    final JMenu menu = findMenu(root, menuText);
                    assertTrue(menu != null, "menu should be available: " + menuText);
                    final JMenuItem item = findMenuItem(menu, itemText);
                    assertTrue(item != null, "menu item should be available: " + itemText);
                    item.doClick();
                });
    }

    private static JMenu findMenu(final Container root, final String text) {
        for (final Component child : root.getComponents()) {
            if (child instanceof JMenu menu && text.equals(menu.getText())) {
                return menu;
            }
            if (child instanceof Container container) {
                final JMenu result = findMenu(container, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JMenuItem findMenuItem(final Container root, final String text) {
        final Component[] children =
                root instanceof JMenu menu ? menu.getMenuComponents() : root.getComponents();
        for (final Component child : children) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
            if (child instanceof Container container) {
                final JMenuItem result = findMenuItem(container, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JTextArea findTextArea(final Container root, final String text) {
        for (final Component child : root.getComponents()) {
            if (child instanceof JTextArea area && text.equals(area.getText())) {
                return area;
            }
            if (child instanceof Container container) {
                final JTextArea result = findTextArea(container, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JLabel findLabel(final Container root, final String text) {
        for (final Component child : root.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (child instanceof Container container) {
                final JLabel result = findLabel(container, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
