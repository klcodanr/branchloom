package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class BoardsUiTest {
    private static final String DEMO = "Demo";
    private static final String PROJECT_PATH = "/tmp/demo";

    @Test
    void emptyPullRequestBoardShowsItsEmptyColumnsAndAcceptsFilters() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var context = new ActionContext(new ViewCoordinator(state), state, null);

        final var board =
                GuiActionRunner.execute(() -> new PullRequestsBoard(context, java.util.List::of));
        board.setFilter(null);
        board.setFilter("missing");

        assertEquals(2, board.getComponentCount(), "assertion values should match");
        assertTrue(board.isVisible(), "assertion condition should hold");
    }

    @Test
    void emptyProjectCardsRenderAnEmptyState() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var context = new ActionContext(new ViewCoordinator(state), state, null);

        final var cards = GuiActionRunner.execute(() -> new ProjectCards(context));

        assertEquals(1, cards.getComponentCount(), "assertion values should match");
        assertTrue(cards.isVisible(), "assertion condition should hold");
    }

    @Test
    void projectCardsRenderProjectsWithRecentSessions()
            throws java.io.InvalidObjectException, InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(DEMO, PROJECT_PATH, null));
        state.addSession(projectId, new Session(projectId, "Feature", null, null, PROJECT_PATH));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);

        final var cards = GuiActionRunner.execute(() -> new ProjectCards(context));

        final var cardContainer = (JPanel) cards.getComponent(0);
        for (int attempt = 0; attempt < 100 && cardContainer.getComponentCount() == 0; attempt++) {
            GuiActionRunner.execute(() -> {});
            Thread.sleep(10);
        }
        final var card = (JPanel) cardContainer.getComponent(0);
        assertTrue(componentText(card).contains("Demo"), "project name should be rendered");
        assertTrue(componentText(card).contains(PROJECT_PATH), "project path should be rendered");
        assertTrue(componentText(card).contains("Recent:"), "recent sessions should be rendered");
        assertTrue(componentText(card).contains("Feature"), "session name should be rendered");
        assertEquals(
                3,
                ((JPanel) card.getComponent(7)).getComponentCount(),
                "card actions should render");
        assertTrue(cards.isVisible(), "populated cards should remain visible");
    }

    @Test
    void pullRequestBoardRendersLoadedRequestsAndFiltersThem() throws InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(DEMO, PROJECT_PATH, null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final var loaded = new CountDownLatch(1);
        final var request =
                new PullRequest(
                        null,
                        12,
                        "Fix login",
                        "Description",
                        "Comments",
                        "https://example.test/12",
                        "created",
                        "updated",
                        "APPROVED",
                        "MERGEABLE",
                        false,
                        "author",
                        "login-fix",
                        2,
                        2,
                        "PASSING");

        final var board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        context,
                                        () -> {
                                            loaded.countDown();
                                            return java.util.List.of(request);
                                        }));
        assertTrue(loaded.await(5, TimeUnit.SECONDS), "pull request loading should complete");
        for (int attempt = 0; attempt < 50 && board.getComponentCount() < 2; attempt++) {
            GuiActionRunner.execute(() -> {});
            Thread.sleep(20);
        }
        GuiActionRunner.execute(() -> board.setFilter("login"));
        for (int attempt = 0; attempt < 50; attempt++) {
            GuiActionRunner.execute(() -> {});
            if (board.getComponent(1) instanceof javax.swing.JScrollPane currentScroll
                    && ((JPanel) currentScroll.getViewport().getView()).getComponentCount() == 4) {
                break;
            }
            Thread.sleep(20);
        }

        final var scroll = (javax.swing.JScrollPane) board.getComponent(1);
        final var columns = (JPanel) scroll.getViewport().getView();
        assertEquals(4, columns.getComponentCount(), "all PR columns should be rendered");
        assertTrue(componentText(columns).contains("#12"), "loaded PR should be rendered");
        assertTrue(componentText(columns).contains("Fix login"), "PR title should be rendered");
        assertTrue(board.isVisible(), "assertion condition should hold");
    }

    @Test
    void pullRequestBoardReportsRefreshFailures() throws InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(DEMO, PROJECT_PATH, null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final var board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        context,
                                        () -> {
                                            throw new IllegalStateException("fixture failure");
                                        }));

        final long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline
                && !componentText(board).contains("PR refresh failed")) {
            GuiActionRunner.execute(() -> {});
            Thread.sleep(10);
        }

        assertTrue(
                componentText(board).contains("PR refresh failed"),
                "refresh failure should be displayed");
        assertTrue(board.isVisible(), "failed board should remain visible");
    }

    @Test
    void pullRequestBoardFiltersByNumberTitleAuthorAndBranch() throws InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(DEMO, PROJECT_PATH, null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final var loaded = new CountDownLatch(1);
        final var requests =
                java.util.List.of(
                        pullRequest(12, "Title match", "author-one", "branch-one", "Not Ready"),
                        pullRequest(
                                23,
                                "Other title",
                                "author-two",
                                "branch-two",
                                "Waiting for Changes"),
                        pullRequest(
                                34,
                                "Third title",
                                "author-three",
                                "branch-three",
                                "Ready For Review"),
                        pullRequest(45, "Fourth title", "author-four", "branch-four", "Approved"));
        final var board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        context,
                                        () -> {
                                            loaded.countDown();
                                            return requests;
                                        }));

        assertTrue(loaded.await(5, TimeUnit.SECONDS), "pull request loading should complete");
        awaitLoaded(board);
        for (final String filter :
                java.util.List.of("12", "title match", "author-three", "branch-four")) {
            GuiActionRunner.execute(() -> board.setFilter(filter));
            assertTrue(componentText(board).contains("#" + matchingNumber(filter)), filter);
        }
    }

    private static void awaitLoaded(final PullRequestsBoard board) throws InterruptedException {
        for (int attempt = 0;
                attempt < 100
                        && !GuiActionRunner.execute(
                                () ->
                                        board.getComponentCount() > 1
                                                && board.getComponent(1)
                                                        instanceof javax.swing.JScrollPane);
                attempt++) {
            GuiActionRunner.execute(() -> {});
            Thread.sleep(10);
        }
    }

    private static int matchingNumber(final String filter) {
        return switch (filter) {
            case "12", "title match" -> 12;
            case "author-three" -> 34;
            case "branch-four" -> 45;
            default -> throw new IllegalArgumentException(filter);
        };
    }

    private static PullRequest pullRequest(
            final int number,
            final String title,
            final String author,
            final String branch,
            final String group) {
        final String review = "Approved".equals(group) ? "APPROVED" : "UNKNOWN";
        final String mergeable = "Not Ready".equals(group) ? "CONFLICTING" : "MERGEABLE";
        return new PullRequest(
                null,
                number,
                title,
                "Description",
                "Comments",
                "https://example.test/" + number,
                "created",
                "updated",
                review,
                mergeable,
                false,
                author,
                branch,
                1,
                1,
                "PASSING");
    }

    private static String componentText(final java.awt.Component component) {
        if (component instanceof javax.swing.JLabel label) {
            return label.getText() == null ? "" : label.getText();
        }
        if (component instanceof javax.swing.text.JTextComponent text) {
            return text.getText();
        }
        if (component instanceof JComponent container) {
            final var text = new StringBuilder();
            for (final var child : container.getComponents()) {
                text.append(componentText(child)).append(' ');
            }
            return text.toString();
        }
        return "";
    }
}
