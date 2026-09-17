package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.Defaults;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class BoardsUiTest {
    private static final String DEMO = "Demo";
    private static final String PROJECT_PATH = "/tmp/demo";
    private static final String ALPHA_LOWER = "alpha";
    private static final String ALPHA_UPPER = "Alpha";
    private static final String DESCRIPTION = "Description";
    private static final String COMMENTS = "Comments";
    private static final String CREATED = "created";
    private static final String UPDATED = "updated";
    private static final String APPROVED = "APPROVED";
    private static final String MERGEABLE = "MERGEABLE";
    private static final String PASSING = "PASSING";

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
        SwingTestSupport.await(
                () -> cardContainer.getComponentCount() > 0, "project card should be rendered");
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
    void projectCardsRenderInAlphabeticalOrder() throws InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project("Zulu", "/tmp/zulu", null));
        state.addProject(new Project(ALPHA_LOWER, "/tmp/alpha", null));
        state.addProject(new Project("Beta", "/tmp/beta", null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);

        final var cards = GuiActionRunner.execute(() -> new ProjectCards(context));

        final var cardContainer = (JPanel) cards.getComponent(0);
        SwingTestSupport.await(
                () ->
                        GuiActionRunner.execute(() -> projectCardNames(cardContainer))
                                .equals(java.util.List.of(ALPHA_LOWER, "Beta", "Zulu")),
                "project cards should be rendered in alphabetical order");

        assertEquals(
                java.util.List.of(ALPHA_LOWER, "Beta", "Zulu"),
                GuiActionRunner.execute(() -> projectCardNames(cardContainer)),
                "project cards should render alphabetically");
    }

    @Test
    void projectCardsSortCaseInsensitiveTiesByExactName() throws InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(ALPHA_LOWER, "/tmp/alpha-lower", null));
        state.addProject(new Project(ALPHA_UPPER, "/tmp/alpha-upper", null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);

        final var cards = GuiActionRunner.execute(() -> new ProjectCards(context));

        final var cardContainer = (JPanel) cards.getComponent(0);
        SwingTestSupport.await(
                () ->
                        GuiActionRunner.execute(() -> projectCardNames(cardContainer))
                                .equals(java.util.List.of(ALPHA_UPPER, ALPHA_LOWER)),
                "project cards should respect case-sensitive tie ordering");

        assertEquals(
                java.util.List.of(ALPHA_UPPER, ALPHA_LOWER),
                GuiActionRunner.execute(() -> projectCardNames(cardContainer)),
                "uppercase variant should sort first when names differ only by case");
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
                        DESCRIPTION,
                        COMMENTS,
                        "https://example.test/12",
                        CREATED,
                        UPDATED,
                        APPROVED,
                        MERGEABLE,
                        false,
                        "author",
                        "login-fix",
                        12,
                        4,
                        2,
                        2,
                        2,
                        PASSING);

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
        SwingTestSupport.await(
                () -> board.getComponentCount() >= 2,
                "pull request board should render loaded requests");
        GuiActionRunner.execute(() -> board.setFilter("login"));
        SwingTestSupport.await(
                () ->
                        board.getComponent(1) instanceof javax.swing.JSplitPane split
                                && split.getLeftComponent() instanceof javax.swing.JScrollPane left
                                && ((JPanel) left.getViewport().getView()).getComponentCount() >= 3,
                "filtered pull request board should render list rows");

        final var split = (javax.swing.JSplitPane) board.getComponent(1);
        final var list =
                (JPanel)
                        ((javax.swing.JScrollPane) split.getLeftComponent())
                                .getViewport()
                                .getView();
        final var summary =
                (JPanel)
                        ((javax.swing.JScrollPane) split.getRightComponent())
                                .getViewport()
                                .getView();
        assertTrue(componentText(list).contains("Results  1"), "result count should be rendered");
        assertTrue(componentText(list).contains("#12"), "loaded PR should be rendered");
        assertTrue(componentText(list).contains("Fix login"), "PR title should be rendered");
        assertTrue(componentText(summary).contains(DESCRIPTION), "summary should be rendered");
        assertTrue(board.isVisible(), "assertion condition should hold");
    }

    @Test
    void pullRequestBoardKeepsSingleCardRowCompactHeight() throws InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(DEMO, PROJECT_PATH, null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final var loaded = new CountDownLatch(1);
        final var request =
                new PullRequest(
                        null,
                        12,
                        "Fix login",
                        DESCRIPTION,
                        COMMENTS,
                        "https://example.test/12",
                        CREATED,
                        UPDATED,
                        APPROVED,
                        MERGEABLE,
                        false,
                        "author",
                        "login-fix",
                        12,
                        4,
                        2,
                        2,
                        2,
                        PASSING);

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
        awaitLoaded(board);

        final javax.swing.JSplitPane split = (javax.swing.JSplitPane) board.getComponent(1);
        final JPanel list =
                (JPanel)
                        ((javax.swing.JScrollPane) split.getLeftComponent())
                                .getViewport()
                                .getView();
        final JPanel cardRow = (JPanel) list.getComponent(2);

        assertEquals(
                UiConstants.PR_CARD_HEIGHT + UiConstants.SPACING_XS,
                cardRow.getMaximumSize().height,
                "single-card row should keep compact max height");
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

        SwingTestSupport.await(
                () -> componentText(board).contains("PR refresh failed"),
                "refresh failure should be displayed");

        assertTrue(
                componentText(board).contains("PR refresh failed"),
                "refresh failure should be displayed");
        assertTrue(board.isVisible(), "failed board should remain visible");
    }

    @Test
    void pullRequestBoardRefreshLoadsDataAgain() throws InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(DEMO, PROJECT_PATH, null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final var loads = new AtomicInteger();
        final var initialLoad = new CountDownLatch(1);
        final var refreshed = new CountDownLatch(1);
        final var board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        context,
                                        () -> {
                                            if (loads.incrementAndGet() == 1) {
                                                initialLoad.countDown();
                                            } else {
                                                refreshed.countDown();
                                            }
                                            return java.util.List.of();
                                        }));

        assertTrue(initialLoad.await(5, TimeUnit.SECONDS), "initial refresh should complete");
        GuiActionRunner.execute(board::refresh);
        assertTrue(refreshed.await(5, TimeUnit.SECONDS), "manual refresh should complete");
        assertTrue(loads.get() >= 2, "refresh should load pull requests again");
    }

    @Test
    void pullRequestBoardKeepsExistingRequestsVisibleWhileRefreshing() throws InterruptedException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(DEMO, PROJECT_PATH, null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final var initialLoad = new CountDownLatch(1);
        final var refreshStarted = new CountDownLatch(1);
        final var releaseRefresh = new CountDownLatch(1);
        final var request =
                new PullRequest(
                        null,
                        12,
                        "Existing request",
                        DESCRIPTION,
                        COMMENTS,
                        "https://example.test/12",
                        CREATED,
                        UPDATED,
                        APPROVED,
                        MERGEABLE,
                        false,
                        "author",
                        "feature",
                        6,
                        2,
                        1,
                        1,
                        1,
                        PASSING);
        final var loads = new AtomicInteger();
        final var board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        context,
                                        () -> {
                                            if (loads.incrementAndGet() == 1) {
                                                initialLoad.countDown();
                                                return java.util.List.of(request);
                                            }
                                            refreshStarted.countDown();
                                            try {
                                                releaseRefresh.await();
                                            } catch (InterruptedException exception) {
                                                Thread.currentThread().interrupt();
                                            }
                                            return java.util.List.of(request);
                                        }));

        assertTrue(initialLoad.await(5, TimeUnit.SECONDS), "initial refresh should complete");
        awaitLoaded(board);
        GuiActionRunner.execute(board::refresh);
        assertTrue(refreshStarted.await(5, TimeUnit.SECONDS), "manual refresh should start");
        assertTrue(
                componentText(board).contains("Existing request"),
                "existing requests should remain visible while refreshing");
        assertTrue(
                componentText(board).contains("Refreshing PRs..."),
                "refresh status should be visible while refreshing");
        releaseRefresh.countDown();
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
        SwingTestSupport.await(
                () ->
                        board.getComponentCount() > 1
                                && board.getComponent(1) instanceof javax.swing.JSplitPane,
                "pull request board did not load");
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
                DESCRIPTION,
                COMMENTS,
                "https://example.test/" + number,
                CREATED,
                UPDATED,
                review,
                mergeable,
                false,
                author,
                branch,
                10,
                2,
                2,
                1,
                1,
                PASSING);
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
        if (component instanceof JComponent container) {
            final var text = new StringBuilder();
            for (final var child : container.getComponents()) {
                text.append(componentText(child)).append(' ');
            }
            return text.toString();
        }
        return "";
    }

    private static java.util.List<String> projectCardNames(final JPanel cardContainer) {
        final java.util.List<String> names = new ArrayList<>();
        for (final java.awt.Component component : cardContainer.getComponents()) {
            if (component instanceof JPanel card
                    && card.getComponentCount() > 0
                    && card.getComponent(0) instanceof javax.swing.AbstractButton button
                    && button.getText() != null) {
                names.add(button.getText());
            }
        }
        return names;
    }
}
