package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;

class PullRequestBoardSupportTest {
    @Test
    void selectionAndLoadingHelpersCallCallbacks() throws MalformedURLException {
        final PullRequest request = request(12);
        final AtomicReference<PullRequest> selected = new AtomicReference<>();
        final JPanel panel = new JPanel();
        PullRequestBoardSupport.registerSelectionClick(panel, request, selected::set);
        panel.dispatchEvent(
                new MouseEvent(
                        panel,
                        MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(),
                        0,
                        5,
                        5,
                        1,
                        false,
                        MouseEvent.BUTTON1));
        assertEquals(request, selected.get(), "left click should select the pull request");

        PullRequestBoardSupport.loadSelectedDetails(null, selected::set);
        assertEquals(request, selected.get(), "null selected request should not invoke callback");
        PullRequestBoardSupport.loadSelectedDetails(request, selected::set);
        assertEquals(request, selected.get(), "selected request should invoke callback");
    }

    @Test
    void visibleRowsAreLoadedAndVisibilityCanBeChecked() throws MalformedURLException {
        final PullRequest visibleRequest = request(10);
        final PullRequest hiddenRequest = request(11);
        final JPanel list = new JPanel(null);
        list.setPreferredSize(new java.awt.Dimension(300, 300));

        final JPanel visibleRow = new JPanel();
        visibleRow.putClientProperty("pullRequest", visibleRequest);
        visibleRow.setBounds(0, 0, 200, 40);
        list.add(visibleRow);

        final JPanel hiddenRow = new JPanel();
        hiddenRow.putClientProperty("pullRequest", hiddenRequest);
        hiddenRow.setBounds(0, 240, 200, 40);
        list.add(hiddenRow);

        final JScrollPane scroll = new JScrollPane(list);
        scroll.setBounds(new Rectangle(0, 0, 220, 120));
        scroll.setSize(220, 120);
        scroll.doLayout();
        scroll.getViewport().setViewSize(list.getPreferredSize());
        scroll.getViewport().setViewPosition(new java.awt.Point(0, 0));

        final AtomicInteger loaded = new AtomicInteger();
        PullRequestBoardSupport.loadVisibleDetails(
                list, scroll, ignored -> loaded.incrementAndGet());

        assertEquals(1, loaded.get(), "only visible rows should trigger detail loading");
        assertTrue(
                PullRequestBoardSupport.isVisible(visibleRequest, list, scroll),
                "visible requests should be reported as visible");
        assertFalse(
                PullRequestBoardSupport.isVisible(hiddenRequest, list, scroll),
                "offscreen requests should not be reported as visible");
    }

    @Test
    void refreshAnimationHelpersStartAndStopTimer() {
        final RotatingIcon icon = new RotatingIcon(UiIcons.refresh());
        final Timer timer = new Timer(1000, ignored -> {});
        final JButton button = new JButton("Refresh");

        PullRequestBoardSupport.startRefreshAnimation(icon, timer);
        assertTrue(timer.isRunning(), "start helper should start timer");

        PullRequestBoardSupport.stopRefreshAnimation(icon, timer, button);
        assertFalse(timer.isRunning(), "stop helper should stop timer");
    }

    private static PullRequest request(final int number) throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project("Demo", "/tmp/demo", null);
        return new PullRequest(
                projectId,
                project,
                number,
                PullRequest.State.OPEN,
                "Title " + number,
                "Description",
                new URL("https://example.test/" + number),
                new Date(),
                new Date(),
                new GitHubUser("author", new URL("https://example.test/author")),
                "feature/test",
                "abc1234def5678",
                "main");
    }
}
