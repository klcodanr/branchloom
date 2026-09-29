package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbstractWorkspaceViewTest {
    private static final String SHOW_FILES_BUTTON = "show-files-button";
    private static final String WORKSPACE_ACTIONS_BUTTON = "workspace-actions-button";
    private static final String HIDE_FILES_TOOLTIP = "Hide files";

    @Test
    void opensAndClosesWorkspaceFilesAndRestoresTabs(@TempDir final Path directory)
            throws IOException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);
        final FakeWorkspaceView view = new FakeWorkspaceView(context, directory);
        final Path file = directory.resolve("notes.txt");
        Files.writeString(file, "hello");

        view.openWorkspaceFile(file);
        view.openWorkspaceFile(file);
        Assertions.assertTrue(view.tabCount() >= 2, "opening a workspace file should add a tab");
        Assertions.assertTrue(
                view.closeActiveFile(), "active workspace file tabs should be closable");
        Assertions.assertFalse(
                view.closeActiveFile(), "closing when no file tab is active should return false");

        Assertions.assertTrue(
                view.restoreTabForTest(true, 0), "valid selected tab index should be restored");
        Assertions.assertFalse(
                view.restoreTabForTest(false, 0), "missing selected-tab state should not restore");
        Assertions.assertFalse(
                view.restoreTabForTest(true, 99), "invalid selected-tab index should not restore");
        Assertions.assertEquals(ViewId.PROBLEMS, view.id(), "view id should be retained");
        Assertions.assertEquals("Workspace", view.title(), "title should be retained");
    }

    @Test
    void togglesWorkspaceTreeFromCollapsedDock(@TempDir final Path directory)
            throws InvocationTargetException, InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);
        final FakeWorkspaceView view = new FakeWorkspaceView(context, directory);

        final JButton showFiles = buttonByName(view, SHOW_FILES_BUTTON);
        Assertions.assertNotNull(
                showFiles, "workspace views should start with a collapsed files dock");

        SwingUtilities.invokeAndWait(showFiles::doClick);
        SwingUtilities.invokeAndWait(() -> {});
        Assertions.assertNotNull(
                buttonByTooltip(view, HIDE_FILES_TOOLTIP),
                "expanded workspace tree should expose a hide control");
        Assertions.assertNull(
                buttonByName(view, SHOW_FILES_BUTTON),
                "dock button should be replaced while files tree is visible");

        final JButton hideFiles = buttonByTooltip(view, HIDE_FILES_TOOLTIP);
        Assertions.assertNotNull(hideFiles, "hide control should exist after expanding files tree");
        SwingUtilities.invokeAndWait(hideFiles::doClick);
        SwingUtilities.invokeAndWait(() -> {});
        Assertions.assertNotNull(
                buttonByName(view, SHOW_FILES_BUTTON),
                "collapsing files tree should restore dock button");
    }

    @Test
    void omitsWorkspaceTreeWhenWorkspacePathIsNotDirectory(@TempDir final Path directory)
            throws IOException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);
        final Path fileWorkspace = directory.resolve("workspace.txt");
        Files.writeString(fileWorkspace, "not a directory");
        final FakeWorkspaceView view = new FakeWorkspaceView(context, fileWorkspace);

        Assertions.assertNull(
                buttonByName(view, SHOW_FILES_BUTTON),
                "non-directory workspaces should not render a files dock");
        Assertions.assertNull(
                buttonByName(view, WORKSPACE_ACTIONS_BUTTON),
                "non-directory workspaces should not render workspace actions");
    }

    private static JButton buttonByName(final Component component, final String name) {
        if (component instanceof JButton button && name.equals(button.getName())) {
            return button;
        }
        if (!(component instanceof Container container)) {
            return null;
        }
        for (final Component child : container.getComponents()) {
            final JButton candidate = buttonByName(child, name);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static JButton buttonByTooltip(final Component component, final String tooltip) {
        if (component instanceof JButton button && tooltip.equals(button.getToolTipText())) {
            return button;
        }
        if (!(component instanceof Container container)) {
            return null;
        }
        for (final Component child : container.getComponents()) {
            final JButton candidate = buttonByTooltip(child, tooltip);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static final class FakeWorkspaceView extends AbstractWorkspaceView {
        private final Path directory;

        private FakeWorkspaceView(final ActionContext actionContext, final Path directory) {
            super(actionContext, ViewId.PROBLEMS);
            this.directory = directory;
            initializeWorkspace("Workspace");
        }

        @Override
        protected Path workspacePath() {
            return directory;
        }

        @Override
        protected void addTitleDetails(final JPanel titleArea) {}

        @Override
        protected void addDefaultTabs() {
            tabs.addTab("Summary", new JPanel());
        }

        @Override
        protected void showActions(final JButton button) {}

        @Override
        protected void openTerminal(final Path path) {}

        @Override
        public void refresh() {}

        private void openWorkspaceFile(final Path file) {
            openFile(file);
        }

        private int tabCount() {
            return tabs.getTabCount();
        }

        private boolean restoreTabForTest(final boolean hasSelectedTab, final int selectedTab) {
            return restoreSelectedTab(hasSelectedTab, selectedTab);
        }
    }
}
