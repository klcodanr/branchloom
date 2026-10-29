package com.jagent.desktop.ui.components.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.board.BoardDefinition;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.components.tree.items.BoardTreeItem;
import com.jagent.desktop.ui.components.tree.items.BoardsTreeItem;
import com.jagent.desktop.ui.components.tree.items.DefaultTreeItem;
import com.jagent.desktop.ui.components.tree.items.GroupTreeItem;
import com.jagent.desktop.ui.components.tree.items.ProjectTreeItem;
import com.jagent.desktop.ui.components.tree.items.PullRequestsItem;
import com.jagent.desktop.ui.components.tree.items.SessionTreeItem;
import java.io.InvalidObjectException;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;

class TreeItemsTest {
    private static final String WORK_GROUP = "Work";

    @Test
    void simpleItemsExposeTheirNavigationMetadata() {
        final AppState state = TestAppState.empty();
        final var context = TestAppState.context(state);

        final var boards = new BoardsTreeItem();
        assertItem(boards, "boards", "Boards", "Boards");
        assertNull(boards.getMenu(), "boards should not have a context menu");

        final var pullRequests = new PullRequestsItem(context);
        assertItem(pullRequests, "pull_requests", "Pull Requests", "Pull Requests");
        assertNull(pullRequests.getMenu(), "pull requests should not have a context menu");
        pullRequests.select();
        assertEquals(
                ViewId.PULL_REQUESTS,
                context.viewController().currentViewId(),
                "pull requests should open its view");

        final var group = new GroupTreeItem(context, WORK_GROUP);
        assertItem(group, WORK_GROUP, WORK_GROUP, WORK_GROUP);
        assertMenuLabels(
                group.getMenu(),
                "Add local project to " + WORK_GROUP,
                "Clone remote project to " + WORK_GROUP);

        final var defaultItem = new DefaultTreeItem(context);
        assertNull(defaultItem.getId(), "default item has no ID");
        assertNull(defaultItem.getTooltip(), "default item has no tooltip");
        assertNull(defaultItem.getCellRenderContents(), "default item has no rendered text");
        assertMenuLabels(defaultItem.getMenu(), "Add local project", "Clone remote project");
    }

    @Test
    void projectSessionAndBoardItemsRenderAndNavigate() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var context = TestAppState.context(state);
        final var projectId = state.addProject(new Project("Demo", "/tmp/demo", null));
        final var sessionId =
                state.addSession(
                        projectId, new Session(projectId, "Feature", "Agent", "Prompt", null));

        final var project = new ProjectTreeItem(context, projectId);
        assertEquals(projectId.toString(), project.getId(), "project ID should be rendered");
        assertEquals(
                "Demo", project.getCellRenderContents().text(), "project name should be rendered");
        assertEquals(
                1,
                project.getTooltip().split("session").length - 1,
                "session count should be rendered");
        assertNotNull(project.getMenu(), "project should have a context menu");
        project.select();
        assertEquals(
                projectId,
                context.viewController().currentProjectId(),
                "project should be selected");
        assertEquals(
                ViewId.PROJECT,
                context.viewController().currentViewId(),
                "project view should open");

        final var session = new SessionTreeItem(context, sessionId);
        assertEquals(sessionId.toString(), session.getId(), "session ID should be rendered");
        assertEquals(sessionId, session.getSessionId(), "session ID should be exposed");
        assertEquals(
                "Feature",
                session.getCellRenderContents().text(),
                "session name should be rendered");
        assertEquals(
                true,
                session.getTooltip().contains("<b>Feature</b>"),
                "session name should be in tooltip");
        assertNotNull(session.getMenu(), "session should have a context menu");
        session.select();
        assertEquals(
                projectId,
                context.viewController().currentProjectId(),
                "session project should be selected");
        assertEquals(
                sessionId,
                context.viewController().currentSessionId(),
                "session should be selected");
        assertEquals(
                ViewId.SESSION,
                context.viewController().currentViewId(),
                "session view should open");

        final var board = new BoardTreeItem(context, new BoardDefinition("Review", null));
        assertEquals("Review", board.getId(), "board name should be the ID");
        assertEquals("Review", board.getTooltip(), "board name should be the tooltip");
        assertThrows(
                UnsupportedOperationException.class,
                board::getMenu,
                "board menu is not implemented");
        board.select();
        assertEquals(
                ViewId.BOARD, context.viewController().currentViewId(), "board view should open");
    }

    @Test
    void sessionTooltipOmitsEmptyMetadataAndTruncatesLongPrompts() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var context = TestAppState.context(state);
        final var projectId = state.addProject(new Project("Demo", "/tmp/demo", null));
        final var prompt = "p".repeat(150);
        final var sessionId =
                state.addSession(projectId, new Session(projectId, "Feature", " ", prompt, " "));

        final var tooltip = new SessionTreeItem(context, sessionId).getTooltip();

        assertEquals(
                140,
                tooltip.substring(tooltip.indexOf("<br><br>") + 8, tooltip.length() - 7).length(),
                "long prompts should be truncated to 137 characters including the ellipsis");
        assertEquals(-1, tooltip.indexOf("Agent:"), "blank agents should be omitted");
        assertEquals(-1, tooltip.indexOf("Worktree:"), "blank worktrees should be omitted");
    }

    private static void assertItem(
            final com.jagent.desktop.ui.components.tree.items.TreeItem item,
            final String id,
            final String tooltip,
            final String text) {
        assertEquals(id, item.getId(), "item ID should match");
        assertEquals(tooltip, item.getTooltip(), "item tooltip should match");
        assertEquals(text, item.getCellRenderContents().text(), "item text should match");
    }

    private static void assertMenuLabels(final JPopupMenu menu, final String... labels) {
        assertEquals(
                labels.length, menu.getComponentCount(), "menu should have expected item count");
        for (int index = 0; index < labels.length; index++) {
            assertEquals(
                    labels[index],
                    ((javax.swing.JMenuItem) menu.getComponent(index)).getText(),
                    "menu item label should match");
        }
    }
}
