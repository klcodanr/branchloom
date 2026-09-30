package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.persistence.AppStatePersistence;
import com.jagent.desktop.ui.Defaults;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppViewShortcutIntegrationTest {
    private static final long CLOSE_TIMEOUT_NANOS = 5_000_000_000L;

    @TempDir private Path dataDirectory;

    @Test
    void closeShortcutClosesFileBeforeFallingBackToTerminal() throws IOException {
        final Path file = Files.writeString(dataDirectory.resolve("notes.txt"), "notes");
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project("Demo", dataDirectory.toString(), null));
        try (AppStatePersistence persistence = new AppStatePersistence(state, dataDirectory)) {
            persistence.persist();
        }

        final AppView app = GuiActionRunner.execute(() -> new AppView(dataDirectory));
        try {
            final JTree tree = find(app, JTree.class);
            assertNotNull(tree, "project tree should be present");
            GuiActionRunner.execute(() -> selectProject(tree));

            final ProjectView projectView = find(app, ProjectView.class);
            assertNotNull(projectView, "project view should be present");
            assertNotNull(projectView.titleLabel(), "project title should be present");
            invokeAction(app, "select-terminal-1");
            invokeAction(app, "clear-transient-focus");
            final JTabbedPane tabs = find(projectView, JTabbedPane.class);
            assertNotNull(tabs, "project tabs should be present");
            final int defaultTabCount = tabs.getTabCount();
            GuiActionRunner.execute(() -> projectView.openFile(file));
            assertEquals(
                    defaultTabCount + 1, tabs.getTabCount(), "opening a file should add a tab");
            GuiActionRunner.execute(() -> projectView.openFile(file));
            assertEquals(
                    defaultTabCount + 1,
                    tabs.getTabCount(),
                    "opening an already open file should select it");
            final JComponent fileViewer = (JComponent) tabs.getSelectedComponent();
            final IntConsumer closeCallback =
                    (IntConsumer) fileViewer.getClientProperty("JTabbedPane.tabCloseCallback");
            GuiActionRunner.execute(() -> closeCallback.accept(tabs.getSelectedIndex()));
            assertEquals(
                    defaultTabCount,
                    tabs.getTabCount(),
                    "the tab close callback should close the file");
            GuiActionRunner.execute(() -> projectView.openFile(file));

            invokeCloseShortcut(app);
            assertEquals(defaultTabCount, tabs.getTabCount(), "the active file should close first");

            invokeCloseShortcut(app);
            assertEquals(
                    defaultTabCount,
                    tabs.getTabCount(),
                    "default tabs should remain after closing the file");
            projectView.openDefaultTab();
            projectView.openFile(dataDirectory.getRoot());
        } finally {
            close(app);
        }
    }

    private static void invokeCloseShortcut(final AppView app) {
        invokeAction(app, "close-terminal");
    }

    private static void invokeAction(final AppView app, final String actionId) {
        GuiActionRunner.execute(
                () ->
                        app.getRootPane()
                                .getActionMap()
                                .get(actionId)
                                .actionPerformed(
                                        new ActionEvent(app, ActionEvent.ACTION_PERFORMED, "")));
    }

    private static void selectProject(final JTree tree) {
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        final DefaultMutableTreeNode project = firstProjectNode(root);
        assertNotNull(project, "project should exist in tree");
        tree.setSelectionPath(new TreePath(project.getPath()));
    }

    private static DefaultMutableTreeNode firstProjectNode(final DefaultMutableTreeNode root) {
        final Enumeration<?> traversal = root.breadthFirstEnumeration();
        while (traversal.hasMoreElements()) {
            final Object current = traversal.nextElement();
            if (current instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof Map.Entry<?, ?> entry
                    && entry.getValue() instanceof Project) {
                return node;
            }
        }
        return null;
    }

    private static void close(final AppView app) {
        GuiActionRunner.execute(
                () -> {
                    app.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    app.dispatchEvent(new WindowEvent(app, WindowEvent.WINDOW_CLOSING));
                });
        final long deadline = System.nanoTime() + CLOSE_TIMEOUT_NANOS;
        while (app.isDisplayable() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
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
}
