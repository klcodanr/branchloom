package com.jagent.desktop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.WindowState;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.persistence.AppStatePersistence;
import com.jagent.desktop.services.persistence.WindowStatePersistence;
import com.jagent.desktop.ui.components.tree.items.ProjectTreeItem;
import com.jagent.desktop.ui.components.tree.items.SessionTreeItem;
import com.jagent.desktop.ui.views.AppView;
import com.jagent.desktop.ui.views.HomeView;
import com.jagent.desktop.ui.views.ProblemsView;
import com.jagent.desktop.ui.views.ProjectView;
import com.jagent.desktop.ui.views.ResourceUsageView;
import com.jagent.desktop.ui.views.SessionView;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppStartupIntegrationTest {
    private static final long CLOSE_TIMEOUT_NANOS = 5_000_000_000L;

    @TempDir private Path dataDirectory;

    @Test
    void startsWithIsolatedDataDirectoryAndNavigatesToSettings() {
        try (ApplicationTestHarness application =
                GuiActionRunner.execute(() -> new ApplicationTestHarness(dataDirectory))) {
            final AppView app = application.view();
            assertEquals(
                    "Branchloom",
                    application.window().getTitle(),
                    "application title should match");

            GuiActionRunner.execute(application::start);
            assertTrue(
                    find(app, HomeView.class) != null, "application should initially render home");

            GuiActionRunner.execute(
                    () -> {
                        final JButton button = findButton(app, "home-button");
                        assertTrue(button != null, "home button should be available");
                        button.doClick();
                    });
            assertTrue(
                    find(app, HomeView.class) != null,
                    "home navigation should render the dashboard");
        }

        assertTrue(
                waitForFile(dataDirectory.resolve("windowState.json")),
                "window state should be persisted in the temporary directory");
    }

    @Test
    void reloadsPersistedWindowGeometryDuringStartup() {
        final WindowState saved = new WindowState();
        saved.windowX = 123;
        saved.windowY = 145;
        saved.windowWidth = 1000;
        saved.windowHeight = 650;
        try (WindowStatePersistence persistence = new WindowStatePersistence(dataDirectory)) {
            persistence.update(saved);
        }

        try (ApplicationTestHarness application =
                GuiActionRunner.execute(() -> new ApplicationTestHarness(dataDirectory))) {
            assertEquals(123, application.window().getX(), "saved window x should be restored");
            assertEquals(145, application.window().getY(), "saved window y should be restored");
            assertEquals(
                    1000, application.window().getWidth(), "saved window width should be restored");
            assertEquals(
                    650,
                    application.window().getHeight(),
                    "saved window height should be restored");
        }
    }

    private static boolean waitForFile(final Path path) {
        final long deadline = System.nanoTime() + CLOSE_TIMEOUT_NANOS;
        while (!Files.exists(path) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return Files.exists(path);
    }

    @Test
    void reloadsPersistedProjectAndOpensItFromTheProjectTree() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(
                        Project.create("Demo", dataDirectory.toString(), null).withGroup(null));
        try (AppStatePersistence persistence = new AppStatePersistence(state, dataDirectory)) {
            persistence.persist();
        }

        try (ApplicationTestHarness application =
                GuiActionRunner.execute(() -> new ApplicationTestHarness(dataDirectory))) {
            final AppView app = application.view();
            GuiActionRunner.execute(application::start);
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
        }
    }

    @Test
    void doesNotShowWorkspaceActionsWhenWorkspaceIsUnavailable() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(
                Project.create(
                                "Unavailable",
                                dataDirectory.resolve("missing-workspace").toString(),
                                null)
                        .withGroup(null));
        try (AppStatePersistence persistence = new AppStatePersistence(state, dataDirectory)) {
            persistence.persist();
        }

        try (ApplicationTestHarness application =
                GuiActionRunner.execute(() -> new ApplicationTestHarness(dataDirectory))) {
            final AppView app = application.view();
            GuiActionRunner.execute(application::start);
            final JTree tree = find(app, JTree.class);
            GuiActionRunner.execute(() -> selectProject(tree));

            assertTrue(
                    findButton(app, "workspace-actions-button") == null,
                    "unavailable workspaces should not show workspace actions");
        }
    }

    @Test
    void walksTheMainApplicationScreens() throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId =
                state.addProject(
                        Project.create("Demo", dataDirectory.toString(), null).withGroup(null));
        final var sessionId =
                state.addSession(
                        projectId,
                        Session.create(
                                projectId,
                                "Feature",
                                "Shell",
                                "Inspect the project",
                                dataDirectory.toString()));
        state.addTerminal(sessionId, Terminal.create(sessionId, "Shell", "true"));
        try (AppStatePersistence persistence = new AppStatePersistence(state, dataDirectory)) {
            persistence.persist();
        }

        try (ApplicationTestHarness application =
                GuiActionRunner.execute(() -> new ApplicationTestHarness(dataDirectory))) {
            final AppView app = application.view();
            GuiActionRunner.execute(application::start);
            assertTrue(find(app, HomeView.class) != null, "startup should show home");

            final JTree tree = find(app, JTree.class);
            assertTrue(tree != null, "project tree should be available");
            GuiActionRunner.execute(() -> selectProject(tree));
            assertTrue(
                    find(app, ProjectView.class) != null, "project selection should open project");

            clickMenuItem(application.window(), "Project", "Settings");
            GuiActionRunner.execute(() -> {});
            assertTrue(findLabel(app, "Project settings") != null, "project settings should open");

            GuiActionRunner.execute(() -> selectProject(tree));
            GuiActionRunner.execute(() -> selectSession(tree));
            assertTrue(
                    find(app, SessionView.class) != null, "session selection should open session");

            clickMenuItem(application.window(), "View", "Problems");
            assertTrue(find(app, ProblemsView.class) != null, "problems should open");

            clickMenuItem(application.window(), "View", "Resource Usage");
            assertTrue(find(app, ResourceUsageView.class) != null, "resource usage should open");
        }
    }

    private static void selectProject(final JTree tree) {
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        final DefaultMutableTreeNode project = firstProjectNode(root);
        assertTrue(project != null, "project should exist in tree");
        tree.setSelectionPath(new TreePath(project.getPath()));
    }

    private static void selectSession(final JTree tree) {
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        final DefaultMutableTreeNode project = firstProjectNode(root);
        assertTrue(project != null, "project should exist in tree");
        final DefaultMutableTreeNode session = firstSessionNode(project);
        assertTrue(session != null, "session should exist in tree");
        tree.setSelectionPath(new TreePath(session.getPath()));
    }

    private static DefaultMutableTreeNode firstProjectNode(final DefaultMutableTreeNode root) {
        final Enumeration<?> traversal = root.breadthFirstEnumeration();
        while (traversal.hasMoreElements()) {
            final Object current = traversal.nextElement();
            if (current instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof ProjectTreeItem) {
                return node;
            }
        }
        return null;
    }

    private static DefaultMutableTreeNode firstSessionNode(final DefaultMutableTreeNode project) {
        for (int index = 0; index < project.getChildCount(); index++) {
            final Object child = project.getChildAt(index);
            if (child instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof SessionTreeItem) {
                return node;
            }
        }
        return null;
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
        if (root instanceof javax.swing.JFrame frame) {
            final JMenuBar menuBar = frame.getJMenuBar();
            if (menuBar != null) {
                return findMenu(menuBar, text);
            }
        }
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
