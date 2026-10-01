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
import com.jagent.desktop.models.github.CliCredential;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.Defaults;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class PullRequestCardUiTest {
    private static final String FIX_LOGIN = "Fix login";
    private static final String AUTHOR = "author";
    private static final String PROJECT_NAME = "Test";
    private static final String PROJECT_PATH = "/tmp/test";

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
                                        context(projectId, project), request, details, () -> {}));

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
                                        context(projectId, project), request, details, () -> {}));

        assertNotNull(
                card.getComponentPopupMenu(), "pull request card should expose a context menu");
        assertEquals(
                List.of("Open PR", "Copy URL", "Import PR branch", "Review PR"),
                java.util.Arrays.stream(card.getComponentPopupMenu().getComponents())
                        .filter(JMenuItem.class::isInstance)
                        .map(component -> ((JMenuItem) component).getText())
                        .filter(text -> !"Approve".equals(text) && !"Merge PR".equals(text))
                        .toList(),
                "context menu should include stable default pull request actions");
    }

    @Test
    void authoredDraftPullRequestHidesAuthorActionsWhenLoginUnavailable()
            throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        PROJECT_PATH,
                        new CliCredential("github.com", "author", "author"));
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
                                        new ActionContext(new ViewCoordinator(state), state, null),
                                        request,
                                        details,
                                        () -> {}));

        final var actions =
                java.util.Arrays.stream(card.getComponentPopupMenu().getComponents())
                        .filter(JMenuItem.class::isInstance)
                        .map(component -> ((JMenuItem) component).getText())
                        .toList();
        assertFalse(
                actions.contains("Request approval"),
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
                                        new ActionContext(new ViewCoordinator(state), state, null),
                                        request,
                                        details,
                                        () -> {}));

        final var actions = menuActions(card);
        assertTrue(actions.contains("Merge PR"), "ready open PRs should expose merge action");
        assertFalse(
                actions.contains("Request approval"),
                "ready authored PRs should not show request approval");
    }

    @Test
    void reviewerPullRequestHidesApproveWhenLoginUnavailable() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        PROJECT_PATH,
                        new CliCredential("github.com", "reviewer", "reviewer"));
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
                                        new ActionContext(new ViewCoordinator(state), state, null),
                                        request,
                                        details,
                                        () -> {}));

        final var actions = menuActions(card);
        assertFalse(actions.contains("Approve"), "approve action requires resolved GitHub login");
        assertFalse(actions.contains("Request approval"), "reviewers should not request approval");
        assertFalse(actions.contains("Make draft"), "reviewers should not toggle draft state");
    }

    private static List<String> menuActions(final PullRequestCard card) {
        return java.util.Arrays.stream(card.getComponentPopupMenu().getComponents())
                .filter(JMenuItem.class::isInstance)
                .map(component -> ((JMenuItem) component).getText())
                .toList();
    }

    private static ActionContext context(final ProjectId projectId, final Project project) {
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());
        return new ActionContext(new ViewCoordinator(state), state, null);
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
        return new PullRequestDetails(
                projectId, project, 12, draft, mergeable, mergeableState, 9, 3, 2);
    }
}
