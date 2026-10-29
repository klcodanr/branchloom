package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.PullRequestReview;
import com.jagent.desktop.models.PullRequestReviews;
import com.jagent.desktop.models.github.CliCredential;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class PullRequestCardUiTest {
    private static final String FIX_LOGIN = "Fix login";
    private static final String AUTHOR = "author";
    private static final String GITHUB_HOST = "github.com";
    private static final String REVIEWER = "reviewer";
    private static final String PROJECT_NAME = "Test";
    private static final String PROJECT_PATH = "/tmp/test";
    private static final String APPROVE_ACTION = "Approve";
    private static final String REQUEST_APPROVAL_ACTION = "Request approval";

    @Test
    void rendersPullRequestIdentityMetadataAndStatus() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project(PROJECT_NAME, PROJECT_PATH, null);
        final PullRequest request = request(projectId, FIX_LOGIN, AUTHOR, project);
        final PullRequestDetails details =
                details(request.projectId(), request.project(), true, false, "clean");
        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(projectId, project),
                                        request,
                                        details,
                                        ignored -> completedRefresh()));

        final JLabel title =
                SwingTestSupport.find(
                        card, JLabel.class, label -> FIX_LOGIN.equals(label.getText()));
        assertNotNull(title, "card should render the PR title");
        assertEquals(FIX_LOGIN, title.getText(), "title text should match pull request title");
        assertTrue(card.getPreferredSize().width > 0, "card should have a positive width");
    }

    @Test
    void contextMenuContainsSupportedPullRequestActions() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project(PROJECT_NAME, PROJECT_PATH, null);
        final PullRequest request = request(projectId, FIX_LOGIN, AUTHOR, project);
        final PullRequestDetails details =
                details(request.projectId(), request.project(), false, false, "dirty");
        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(projectId, project),
                                        request,
                                        details,
                                        ignored -> completedRefresh()));

        assertNotNull(
                card.getComponentPopupMenu(), "pull request card should expose a context menu");
        assertEquals(
                List.of("Open PR", "Copy URL", "Import PR branch", "Review PR"),
                java.util.Arrays.stream(card.getComponentPopupMenu().getComponents())
                        .filter(JMenuItem.class::isInstance)
                        .map(component -> ((JMenuItem) component).getText())
                        .filter(text -> !APPROVE_ACTION.equals(text) && !"Merge PR".equals(text))
                        .toList(),
                "context menu should include stable default pull request actions");
    }

    @Test
    void authoredDraftPullRequestHidesAuthorActionsWhenLoginUnavailable()
            throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project =
                new Project(
                        PROJECT_NAME, PROJECT_PATH, new CliCredential(GITHUB_HOST, AUTHOR, AUTHOR));
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());
        final PullRequest request = request(projectId, FIX_LOGIN, AUTHOR, project);
        final PullRequestDetails details = details(projectId, project, true, false, "draft");
        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(state, unavailableGithub(state, projectId)),
                                        request,
                                        details,
                                        ignored -> completedRefresh()));

        final var actions =
                java.util.Arrays.stream(card.getComponentPopupMenu().getComponents())
                        .filter(JMenuItem.class::isInstance)
                        .map(component -> ((JMenuItem) component).getText())
                        .toList();
        assertFalse(
                actions.contains(REQUEST_APPROVAL_ACTION),
                "author actions should be hidden when GitHub login is unavailable");
    }

    @Test
    void authoredReadyPullRequestShowsMergeAction() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        PROJECT_PATH,
                        new CliCredential("github.com", AUTHOR, AUTHOR));
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());
        final PullRequest request = request(projectId, FIX_LOGIN, AUTHOR, project);
        final PullRequestDetails details = details(projectId, project, false, true, "clean");
        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(state, github(state, projectId, "author")),
                                        request,
                                        details,
                                        ignored -> completedRefresh()));

        final var actions = menuActions(card);
        assertTrue(actions.contains("Merge PR"), "ready open PRs should expose merge action");
        assertFalse(
                actions.contains(REQUEST_APPROVAL_ACTION),
                "ready authored PRs should not show request approval");
    }

    @Test
    void authoredDraftPullRequestShowsRequestApprovalAction() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project = projectWithCredential(AUTHOR);
        final AppState state = state(projectId, project);
        final PullRequest request = request(projectId, FIX_LOGIN, AUTHOR, project);
        final PullRequestDetails details = details(projectId, project, true, false, "draft");

        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(state, github(state, projectId, AUTHOR)),
                                        request,
                                        details,
                                        ignored -> completedRefresh()));

        assertTrue(
                menuActions(card).contains(REQUEST_APPROVAL_ACTION),
                "authors should be able to request approval for draft PRs");
    }

    @Test
    void authoredReadyPullRequestShowsMakeDraftAction() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project = projectWithCredential(AUTHOR);
        final AppState state = state(projectId, project);
        final PullRequest request = request(projectId, FIX_LOGIN, AUTHOR, project);
        final PullRequestDetails details = details(projectId, project, false, false, "clean");

        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(state, github(state, projectId, AUTHOR)),
                                        request,
                                        details,
                                        ignored -> completedRefresh()));

        assertTrue(
                menuActions(card).contains("Make draft"),
                "authors should be able to make ready PRs draft");
    }

    @Test
    void reviewerPullRequestShowsApproveWhenCurrentUserHasNotApproved()
            throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project = projectWithCredential(REVIEWER);
        final AppState state = state(projectId, project);
        final PullRequest request = request(projectId, FIX_LOGIN, AUTHOR, project);
        final PullRequestDetails details = details(projectId, project, false, false, "blocked");

        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(state, github(state, projectId, REVIEWER)),
                                        request,
                                        details,
                                        ignored -> completedRefresh()));

        assertTrue(
                menuActions(card).contains(APPROVE_ACTION),
                "reviewers who have not approved should see the approve action");
    }

    @Test
    void missingProjectDoesNotAddProjectActions() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project(PROJECT_NAME, PROJECT_PATH, null);
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final PullRequest request = request(projectId, FIX_LOGIN, AUTHOR, project);
        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(state, github(state, projectId, REVIEWER)),
                                        request,
                                        null,
                                        ignored -> completedRefresh()));

        final var actions = menuActions(card);
        assertFalse(
                actions.contains(APPROVE_ACTION),
                "missing projects should not expose review actions");
        assertFalse(
                actions.contains("Merge PR"), "missing projects should not expose merge actions");
    }

    @Test
    void reviewerPullRequestHidesApproveWhenLoginUnavailable() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        PROJECT_PATH,
                        new CliCredential(GITHUB_HOST, REVIEWER, REVIEWER));
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());
        final PullRequest request = request(projectId, FIX_LOGIN, AUTHOR, project);
        final PullRequestDetails details = details(projectId, project, false, false, "blocked");
        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(state, unavailableGithub(state, projectId)),
                                        request,
                                        details,
                                        ignored -> completedRefresh()));

        final var actions = menuActions(card);
        assertFalse(
                actions.contains(APPROVE_ACTION), "approve action requires resolved GitHub login");
        assertFalse(
                actions.contains(REQUEST_APPROVAL_ACTION), "reviewers should not request approval");
        assertFalse(actions.contains("Make draft"), "reviewers should not toggle draft state");
    }

    @Test
    void reviewerPullRequestHidesApproveAfterCurrentUserApproved() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        PROJECT_PATH,
                        new CliCredential(GITHUB_HOST, REVIEWER, REVIEWER));
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());
        final PullRequest request = request(projectId, FIX_LOGIN, AUTHOR, project);
        final PullRequestDetails details =
                new PullRequestDetails(
                        request,
                        false,
                        false,
                        false,
                        "blocked",
                        0,
                        0,
                        0,
                        new com.jagent.desktop.models.PullRequestChecks(List.of()),
                        new PullRequestReviews(
                                List.of(
                                        new PullRequestReview(
                                                new GitHubUser(
                                                        REVIEWER, new URL("https://example.test")),
                                                PullRequestReview.Status.APPROVED))));
        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(state, github(state, projectId, REVIEWER)),
                                        request,
                                        details,
                                        ignored -> completedRefresh()));

        assertFalse(
                menuActions(card).contains(APPROVE_ACTION),
                "approve should be hidden after the current user approved the PR");
    }

    private static List<String> menuActions(final PullRequestCard card) {
        return java.util.Arrays.stream(card.getComponentPopupMenu().getComponents())
                .filter(JMenuItem.class::isInstance)
                .map(component -> ((JMenuItem) component).getText())
                .toList();
    }

    private CompletableFuture<Void> completedRefresh() {
        return CompletableFuture.completedFuture(null);
    }

    private static ActionContext context(final ProjectId projectId, final Project project) {
        final AppState state = state(projectId, project);
        return context(state, unavailableGithub(state, projectId));
    }

    private static AppState state(final ProjectId projectId, final Project project) {
        return new AppState(
                Defaults.appSettings(),
                Map.of(projectId.value().toString(), project),
                Map.of(),
                Map.of());
    }

    private static Project projectWithCredential(final String login) {
        return new Project(
                PROJECT_NAME, PROJECT_PATH, new CliCredential(GITHUB_HOST, login, login));
    }

    private static ActionContext context(final AppState state, final GitHub github) {
        return new ActionContext(
                TestAppState.controller(state), state, null, null, null, ignored -> github);
    }

    private static GitHub github(
            final AppState state, final ProjectId projectId, final String login) {
        return new GitHub(state, projectId) {
            @Override
            public String getLogin() {
                return login;
            }
        };
    }

    private static GitHub unavailableGithub(final AppState state, final ProjectId projectId) {
        return new GitHub(state, projectId) {
            @Override
            public String getLogin() throws IOException {
                throw new IOException("GitHub login unavailable");
            }
        };
    }

    private static PullRequest request(
            final ProjectId projectId,
            final String title,
            final String author,
            final Project project)
            throws MalformedURLException {
        return new PullRequest(
                projectId,
                project,
                12,
                PullRequest.State.OPEN,
                title,
                "Description",
                new URL("https://example.test/12"),
                new Date(),
                new Date(),
                new GitHubUser(author, new URL("https://example.test/" + author)),
                "feature/login-fix",
                "abc1234def5678",
                "main");
    }

    private static PullRequestDetails details(
            final ProjectId projectId,
            final Project project,
            final boolean draft,
            final boolean mergeable,
            final String mergeableState) {
        try {
            return new PullRequestDetails(
                    request(projectId, "Test", AUTHOR, project),
                    draft,
                    false,
                    mergeable,
                    mergeableState,
                    9,
                    3,
                    2,
                    new com.jagent.desktop.models.PullRequestChecks(List.of()),
                    new PullRequestReviews(List.of()));
        } catch (MalformedURLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
