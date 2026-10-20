package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.awt.Component;
import java.awt.Container;
import java.util.Map;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ViewsUiTest {
    private static final String VALUE_MESSAGE = "view value should match";

    @Test
    void problemsViewExposesItsViewIdentityAndRefreshes() {
        final var view = GuiActionRunner.execute(() -> new ProblemsView());

        assertEquals(ViewId.PROBLEMS, view.id(), VALUE_MESSAGE);
        assertEquals("Problems", view.title(), VALUE_MESSAGE);
        assertSame(view, view.render(), VALUE_MESSAGE);
    }

    @Test
    void resourceUsageViewExposesItsViewIdentityAndRefreshes() {
        final var view = GuiActionRunner.execute(() -> new ResourceUsageView());

        assertEquals(ViewId.RESOURCE_USAGE, view.id(), VALUE_MESSAGE);
        assertEquals("Resource Usage", view.title(), VALUE_MESSAGE);
        assertSame(view, view.render(), VALUE_MESSAGE);
    }

    @Test
    void homeViewBuildsItsDashboardForAnEmptyApplication() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null, null, null);

        final var view = GuiActionRunner.execute(() -> new HomeView(context));

        assertEquals(ViewId.HOME, view.id(), VALUE_MESSAGE);
        assertEquals("Home", view.title(), VALUE_MESSAGE);
        assertEquals(1, view.getComponentCount(), VALUE_MESSAGE);
        assertEquals(JScrollPane.class, view.getComponent(0).getClass(), VALUE_MESSAGE);
        assertSame(view, view.render(), VALUE_MESSAGE);
        view.dispose();
    }

    @Test
    void homeViewSummarizesSessionsByProject() {
        final ProjectId projectId = ProjectId.create();
        final SessionId sessionId = SessionId.create();
        final Project project = new Project("Branchloom", "/tmp/branchloom", null);
        final Session session =
                new Session(projectId, "Improve dashboard", "agent", "prompt", "/tmp/worktree");
        final Project storedProject = project.withNewSession(sessionId);
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), storedProject),
                        Map.of(sessionId.value().toString(), session),
                        Map.of());
        final var view =
                GuiActionRunner.execute(
                        () ->
                                new HomeView(
                                        new ActionContext(
                                                new ViewCoordinator(state),
                                                state,
                                                null,
                                                null,
                                                null)));

        final JTable table = findTable(view);
        assertEquals(
                4, table.getColumnCount(), "monitoring table should show compact summary fields");
        assertEquals(1, table.getRowCount(), "monitoring table should show each session");
        assertEquals(
                "Branchloom > Improve dashboard",
                table.getValueAt(0, 0),
                "project and session should be combined");
        assertEquals("", table.getValueAt(0, 1), "unresolved PR should be empty");
        assertEquals("", table.getValueAt(0, 2).toString(), "unresolved status should be empty");
        assertEquals("", table.getValueAt(0, 3), "unresolved status change should be empty");
        view.dispose();
    }

    private static JTable findTable(final Container container) {
        for (final Component child : container.getComponents()) {
            if (child instanceof JTable table) {
                return table;
            }
            if (child instanceof Container childContainer) {
                try {
                    return findTable(childContainer);
                } catch (IllegalStateException ignored) {
                    // Continue searching sibling components.
                }
            }
        }
        throw new IllegalStateException("monitoring table was not rendered");
    }
}
