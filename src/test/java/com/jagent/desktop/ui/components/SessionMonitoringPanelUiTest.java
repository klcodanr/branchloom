package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class SessionMonitoringPanelUiTest {
    @Test
    void rendersSessionsInProjectAndNameOrderWithMonitoringDetails() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final SessionId firstId = SessionId.create();
        final SessionId secondId = SessionId.create();
        final Project project = Project.create("Alpha", "/tmp/alpha", null);
        final Session first = Session.create(projectId, "zulu", null, null, null);
        final Session second = Session.create(projectId, "Bravo", null, null, null);
        final PullRequestDetails details = details(projectId, project);
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(
                                firstId.value().toString(), first,
                                secondId.value().toString(), second),
                        Map.of());
        state.updateMonitoringStatus(firstId, details);

        final var controller = TestAppState.controller(state);
        final SessionMonitoringPanel panel =
                GuiActionRunner.execute(() -> new SessionMonitoringPanel(controller, state));
        final JTable table = SwingTestSupport.find(panel, JTable.class);

        assertNotNull(table, "monitoring panel should contain a table");
        assertEquals(2, table.getRowCount(), "all sessions should be shown");
        assertEquals("Alpha > Bravo", table.getValueAt(0, 0), "sessions should sort by name");
        assertEquals("Alpha > zulu", table.getValueAt(1, 0), "sorting should ignore case");
        assertEquals("#12", table.getValueAt(1, 1), "monitored sessions should show PR number");
        assertEquals("", table.getValueAt(0, 1), "unmonitored sessions should have blank PR data");

        final TableCellRenderer renderer = table.getCellRenderer(0, 2);
        assertNotNull(
                renderer.getTableCellRendererComponent(
                        table, table.getValueAt(0, 2), false, false, 0, 2),
                "status renderer should render unmonitored rows");
        assertNotNull(
                renderer.getTableCellRendererComponent(
                        table, table.getValueAt(1, 2), true, false, 1, 2),
                "status renderer should render selected monitored rows");

        final MouseEvent move = new MouseEvent(table, MouseEvent.MOUSE_MOVED, 1, 0, 5, 5, 0, false);
        final MouseEvent click =
                new MouseEvent(table, MouseEvent.MOUSE_CLICKED, 1, 0, 5, 5, 1, false);
        for (final var listener : table.getMouseMotionListeners()) {
            listener.mouseMoved(move);
        }
        for (final var listener : table.getMouseListeners()) {
            listener.mouseClicked(click);
        }
        assertEquals(secondId, controller.currentSessionId(), "clicking a session should open it");

        final MouseEvent doubleClick =
                new MouseEvent(table, MouseEvent.MOUSE_CLICKED, 1, 0, 5, 5, 2, false);
        for (final var listener : table.getMouseListeners()) {
            listener.mouseClicked(doubleClick);
        }
    }

    @Test
    void usesFallbackNameWhenSessionProjectIsMissing() {
        final ProjectId projectId = ProjectId.create();
        final SessionId sessionId = SessionId.create();
        final Session session = Session.create(projectId, "orphan", null, null, null);
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(),
                        Map.of(sessionId.value().toString(), session),
                        Map.of());
        final SessionMonitoringPanel panel =
                GuiActionRunner.execute(
                        () -> new SessionMonitoringPanel(TestAppState.controller(state), state));
        final JTable table = SwingTestSupport.find(panel, JTable.class);

        assertNotNull(table, "monitoring panel should contain a table");
        assertEquals(
                "Unknown project > orphan",
                table.getValueAt(0, 0),
                "sessions without a project should use the fallback project name");

        final MouseEvent invalidClick =
                new MouseEvent(table, MouseEvent.MOUSE_CLICKED, 1, 0, -1, -1, 1, false);
        for (final var listener : table.getMouseListeners()) {
            listener.mouseClicked(invalidClick);
        }
        assertEquals(1, table.getRowCount(), "invalid clicks should leave the table unchanged");
    }

    private static PullRequestDetails details(final ProjectId projectId, final Project project)
            throws MalformedURLException {
        final PullRequest request =
                new PullRequest(
                        projectId,
                        project,
                        12,
                        PullRequest.State.OPEN,
                        "Fix",
                        "Description",
                        new URL("https://example.test/12"),
                        Date.from(Instant.now()),
                        Date.from(Instant.now()),
                        new com.jagent.desktop.models.GitHubUser(
                                "author", new URL("https://example.test/author")),
                        "feature",
                        "abc123",
                        "main");
        return new PullRequestDetails(
                request,
                false,
                false,
                true,
                "clean",
                1,
                2,
                3,
                new com.jagent.desktop.models.PullRequestChecks(List.of()),
                new com.jagent.desktop.models.PullRequestReviews(List.of()));
    }
}
