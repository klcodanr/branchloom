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
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppViewShortcutIntegrationTest {
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
            GuiActionRunner.execute(() -> projectView.openFile(file));
            final JTabbedPane tabs = find(projectView, JTabbedPane.class);
            assertNotNull(tabs, "project tabs should be present");
            assertEquals(3, tabs.getTabCount(), "opening a file should add a tab");
            GuiActionRunner.execute(() -> projectView.openFile(file));
            assertEquals(3, tabs.getTabCount(), "opening an already open file should select it");

            invokeCloseShortcut(app);
            assertEquals(2, tabs.getTabCount(), "the active file should close first");

            invokeCloseShortcut(app);
            assertEquals(
                    2, tabs.getTabCount(), "default tabs should remain after closing the file");
            projectView.openDefaultTab();
            projectView.openFile(dataDirectory.getRoot());
        } finally {
            GuiActionRunner.execute(
                    () -> {
                        app.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        app.dispatchEvent(new WindowEvent(app, WindowEvent.WINDOW_CLOSING));
                    });
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
        final DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getChildAt(2);
        final DefaultMutableTreeNode project = (DefaultMutableTreeNode) group.getChildAt(0);
        tree.setSelectionPath(new TreePath(project.getPath()));
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
