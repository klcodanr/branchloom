package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.Defaults;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class BoardsUiTest {
    private static final String PROJECT_NAME = "Demo";
    private static final String PROJECT_PATH = "/tmp/demo";

    @Test
    void emptyPullRequestBoardShowsAndAcceptsFilters() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final PullRequestsBoard board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        context, (query, forceRefresh) -> List.of(), ""));
        board.setFilter(null);
        board.setFilter("missing");
        assertTrue(board.isVisible(), "board should remain visible after filter changes");
    }

    @Test
    void blankInitialQueryDoesNotLoadPullRequests() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final var loaded = new AtomicBoolean();

        GuiActionRunner.execute(
                () ->
                        new PullRequestsBoard(
                                context,
                                (query, forceRefresh) -> {
                                    loaded.set(true);
                                    return List.of();
                                },
                                ""));

        assertFalse(loaded.get(), "blank queries should not trigger a pull request search");
    }

    @Test
    void pullRequestBoardRendersLoadedRequestsAndFiltersThem()
            throws InterruptedException, MalformedURLException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId = state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final var loaded = new CountDownLatch(1);
        final var initialQuery = new java.util.concurrent.atomic.AtomicReference<String>();
        final PullRequest request = pullRequest(projectId, 12, "Fix login", "author-one");
        final PullRequestsBoard board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        context,
                                        (query, forceRefresh) -> {
                                            initialQuery.set(query);
                                            loaded.countDown();
                                            return List.of(request);
                                        },
                                        "author:@me"));
        assertTrue(loaded.await(5, TimeUnit.SECONDS), "board should invoke refresh supplier");
        assertTrue(
                "author:@me".equals(initialQuery.get()),
                "board should use the selected filter query on initial refresh");
        SwingTestSupport.await(
                () -> componentText(board).contains("Fix login"), "PR should render");
        GuiActionRunner.execute(() -> board.setFilter("login"));
        assertTrue(
                componentText(board).contains("Results"), "filter results summary should be shown");
    }

    @Test
    void pullRequestBoardReportsRefreshFailures() throws InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final PullRequestsBoard board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        context,
                                        (query, forceRefresh) -> {
                                            throw new IllegalStateException("fixture failure");
                                        },
                                        "author:@me"));
        SwingTestSupport.await(
                () -> componentText(board).contains("PR refresh failed"),
                "refresh failures should be surfaced");
        assertTrue(
                componentText(board).contains("PR refresh failed"),
                "refresh failure text should be rendered after async update");
    }

    @Test
    void summaryTitleRetainsFullTextAndWraps() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final String longTitle =
                "Share remove unused code from features without clipping the summary title";
        final PullRequest request = pullRequest(projectId, 8087, longTitle, "author-one");
        final PullRequestSummaryPanel summary =
                GuiActionRunner.execute(
                        () -> {
                            final PullRequestSummaryPanel panel = new PullRequestSummaryPanel();
                            panel.render(request, null);
                            panel.setSize(300, 500);
                            panel.doLayout();
                            return panel;
                        });

        final JButton title = findButton(summary);
        assertTrue(title.getText().contains(longTitle), "summary should retain the full PR title");
        assertTrue(
                title.getPreferredSize().height > title.getFont().getSize(),
                "long summary titles should wrap vertically");
    }

    private static PullRequest pullRequest(
            final ProjectId projectId, final int number, final String title, final String author)
            throws MalformedURLException {
        return new PullRequest(
                projectId,
                new Project(PROJECT_NAME, PROJECT_PATH, null),
                number,
                PullRequest.State.OPEN,
                title,
                "Description",
                new URL("https://example.test/" + number),
                new Date(),
                new Date(),
                new GitHubUser(author, new URL("https://example.test/" + author)),
                "feature/test",
                "abcd1234efgh5678",
                "main");
    }

    private static String componentText(final java.awt.Component component) {
        if (component instanceof javax.swing.AbstractButton button) {
            return button.getText() == null ? "" : button.getText();
        }
        if (component instanceof javax.swing.JLabel label) {
            return label.getText() == null ? "" : label.getText();
        }
        if (component instanceof javax.swing.text.JTextComponent text) {
            return text.getText();
        }
        if (component instanceof javax.swing.JComponent container) {
            final var text = new StringBuilder();
            for (final var child : container.getComponents()) {
                text.append(componentText(child)).append(' ');
            }
            return text.toString();
        }
        return "";
    }

    private static JButton findButton(final java.awt.Component component) {
        if (component instanceof JButton button && button.getText() != null) {
            return button;
        }
        if (component instanceof java.awt.Container container) {
            for (final java.awt.Component child : container.getComponents()) {
                final JButton result = findButton(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
