package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.async.AsyncLoadingStateFactory;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class PullRequestsBoardUiTest {
    @Test
    void blankQueryShowsPrompt() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        new AsyncLoadingStateFactory(
                                                state, TestAppState.backgroundJobs(state)),
                                        TestAppState.backgroundJobs(state),
                                        TestAppState.controller(state),
                                        state,
                                        TestAppState.githubProvider(state),
                                        TestAppState.dialogService()));

        assertNotNull(
                SwingTestSupport.find(
                        board,
                        JLabel.class,
                        label -> "Enter a pull request query".equals(label.getText())),
                "blank query should show the query prompt");
    }

    @Test
    void refreshLoadsAndFiltersPullRequestsFromInjectedGitHub()
            throws InterruptedException,
                    java.util.concurrent.ExecutionException,
                    MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project = Project.create("Project", "/tmp/project", null);
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());
        final PullRequestDetails request = request(projectId, project);
        final FakeGitHub gitHub = new FakeGitHub(state, projectId, request);
        final var board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        new AsyncLoadingStateFactory(
                                                state, TestAppState.backgroundJobs(state)),
                                        TestAppState.backgroundJobs(state),
                                        TestAppState.controller(state),
                                        state,
                                        ignored -> gitHub,
                                        TestAppState.dialogService()));

        GuiActionRunner.execute(() -> board.setQuery("bug"));
        final var refresh = board.refresh();
        refresh.get();
        SwingTestSupport.await(
                () ->
                        SwingTestSupport.find(
                                        board,
                                        JLabel.class,
                                        label -> "Results  1".equals(label.getText()))
                                != null,
                "loaded pull requests should be rendered");

        GuiActionRunner.execute(() -> board.setFilter("unmatched"));
        assertNotNull(
                SwingTestSupport.find(
                        board,
                        JLabel.class,
                        label -> "No pull requests match this filter.".equals(label.getText())),
                "local filters should hide non-matching pull requests");
        assertTrue(gitHub.searches.contains("bug"), "refresh should use the current query");
    }

    @Test
    void normalizesNullInputsAndUsesConfiguredFilterQueries() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final PullRequestsBoard board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        new AsyncLoadingStateFactory(
                                                state, TestAppState.backgroundJobs(state)),
                                        TestAppState.backgroundJobs(state),
                                        TestAppState.controller(state),
                                        state,
                                        TestAppState.githubProvider(state),
                                        TestAppState.dialogService()));

        GuiActionRunner.execute(
                () -> {
                    board.setFilter(null);
                    board.setQuery(null);
                });
        assertNotNull(
                SwingTestSupport.find(
                        board,
                        JLabel.class,
                        label ->
                                "No pull requests match this filter.".equals(label.getText())
                                        || "Enter a pull request query".equals(label.getText())),
                "null inputs should leave the board in a rendered state");

        final JComboBox<?> filters = SwingTestSupport.find(board, JComboBox.class);
        assertNotNull(filters, "board should expose configured pull request filters");
        GuiActionRunner.execute(() -> filters.setSelectedIndex(1));
        final SearchInput search = SwingTestSupport.find(board, SearchInput.class);
        assertNotNull(search, "board should expose its query input");
        assertTrue(
                search.getText().contains("@me"),
                "selecting a configured filter should populate its query");
        GuiActionRunner.execute(() -> filters.setSelectedIndex(-1));
        assertTrue(
                search.getText().isEmpty(),
                "clearing the selected filter should use the fallback empty query");
    }

    @Test
    void localFilterMatchesNumberAuthorDescriptionAndBranch()
            throws MalformedURLException,
                    InterruptedException,
                    java.util.concurrent.ExecutionException {
        final ProjectId projectId = ProjectId.create();
        final Project project = Project.create("Project", "/tmp/project", null);
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());
        final PullRequestDetails request = request(projectId, project);
        final FakeGitHub gitHub = new FakeGitHub(state, projectId, request);
        final PullRequestsBoard board =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestsBoard(
                                        new AsyncLoadingStateFactory(
                                                state, TestAppState.backgroundJobs(state)),
                                        TestAppState.backgroundJobs(state),
                                        TestAppState.controller(state),
                                        state,
                                        ignored -> gitHub,
                                        TestAppState.dialogService()));

        GuiActionRunner.execute(() -> board.setQuery("bug"));
        board.refresh().get();
        SwingTestSupport.await(
                () ->
                        SwingTestSupport.find(
                                        board,
                                        JLabel.class,
                                        label -> "Results  1".equals(label.getText()))
                                != null,
                "request should be rendered before local filtering");

        for (final String filter : List.of("12", "author", "Description", "feature")) {
            GuiActionRunner.execute(() -> board.setFilter(filter));
            assertNotNull(
                    SwingTestSupport.find(
                            board, JLabel.class, label -> "Results  1".equals(label.getText())),
                    "local filter should match request field: " + filter);
        }

        final JPanel cardRow =
                SwingTestSupport.find(
                        board,
                        JPanel.class,
                        panel -> request.equals(panel.getClientProperty("pullRequest")));
        assertNotNull(cardRow, "rendered requests should have selectable rows");
        final MouseEvent rightClick =
                new MouseEvent(
                        cardRow,
                        MouseEvent.MOUSE_PRESSED,
                        1,
                        MouseEvent.BUTTON3_DOWN_MASK,
                        1,
                        1,
                        1,
                        false,
                        MouseEvent.BUTTON3);
        final MouseEvent leftClick =
                new MouseEvent(
                        cardRow,
                        MouseEvent.MOUSE_PRESSED,
                        1,
                        0,
                        1,
                        1,
                        1,
                        false,
                        MouseEvent.BUTTON1);
        for (final var listener : cardRow.getMouseListeners()) {
            listener.mousePressed(rightClick);
            listener.mousePressed(leftClick);
        }
    }

    private static PullRequestDetails request(final ProjectId projectId, final Project project)
            throws MalformedURLException {
        final PullRequest pullRequest =
                new PullRequest(
                        projectId,
                        project,
                        12,
                        PullRequest.State.OPEN,
                        "Fix bug",
                        "Description",
                        new URL("https://example.test/12"),
                        new Date(),
                        new Date(),
                        new com.jagent.desktop.models.GitHubUser(
                                "author", new URL("https://example.test/author")),
                        "feature",
                        "abc123",
                        "main");
        return new PullRequestDetails(
                pullRequest,
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

    private static final class FakeGitHub extends GitHub {
        private final PullRequestDetails request;
        private final List<String> searches = new java.util.ArrayList<>();

        private FakeGitHub(
                final AppState state, final ProjectId projectId, final PullRequestDetails request) {
            super(state, projectId);
            this.request = request;
        }

        @Override
        public List<PullRequest> listPullRequests(final String search) throws IOException {
            searches.add(search);
            return List.of(request);
        }

        @Override
        public PullRequestDetails getPullRequestDetails(final PullRequest pullRequest)
                throws IOException {
            return request;
        }
    }
}
