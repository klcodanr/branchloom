package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.Defaults;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class PullRequestCardUiTest {
    @Test
    void rendersPullRequestIdentityMetadataAndChecks() {
        final PullRequest request = request("APPROVED", "MERGEABLE", false, "PASSING");
        final PullRequestCard card =
                GuiActionRunner.execute(() -> new PullRequestCard(context(), request));

        final JButton title = SwingTestSupport.find(card, JButton.class);
        final JComponent checks =
                SwingTestSupport.find(
                        card,
                        JComponent.class,
                        component -> "2/3 checks Passing".equals(component.getToolTipText()));

        assertNotNull(title, "pull request title should be rendered");
        assertEquals("Fix login", title.getText(), "pull request title should match");
        assertNotNull(checks, "checks status should be rendered as a tooltip");
        assertTrue(card.getPreferredSize().width > 0, "card should have a preferred width");
    }

    @Test
    void rendersDetailedConflictingMergeState() {
        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(), request("UNKNOWN", "DIRTY", false, "PASSING")));

        final JLabel metadata =
                SwingTestSupport.find(
                        card,
                        JLabel.class,
                        component -> component.getText().contains("Cannot merge"));

        assertNotNull(metadata, "conflicting pull requests should show that they cannot merge");
    }

    @Test
    void contextMenuContainsSupportedPullRequestActions() {
        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        context(),
                                        request("UNKNOWN", "CONFLICTING", false, "FAILING")));

        assertNotNull(card.getComponentPopupMenu(), "pull request card should have a context menu");
        assertEquals(
                List.of("Open PR", "Import PR branch", "Review PR"),
                java.util.Arrays.stream(card.getComponentPopupMenu().getComponents())
                        .filter(JMenuItem.class::isInstance)
                        .map(component -> ((JMenuItem) component).getText())
                        .toList(),
                "context menu should expose supported actions");
    }

    @Test
    void authoredPullRequestShowsContextSensitiveLifecycleActions() {
        final ProjectId projectId = ProjectId.create();
        final PullRequest request = request(projectId, "APPROVED", "MERGEABLE", false, "PASSING");
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(
                                projectId.value().toString(),
                                new Project(
                                        "Test",
                                        "/tmp/test",
                                        new com.jagent.desktop.services.GitHub.Auth(
                                                "github.com", "author"))),
                        Map.of(),
                        Map.of());
        final PullRequestCard card =
                GuiActionRunner.execute(
                        () ->
                                new PullRequestCard(
                                        new ActionContext(new ViewCoordinator(state), state, null),
                                        request));

        assertEquals(
                List.of(
                        "Open PR",
                        "Import PR branch",
                        "Review PR",
                        "Convert to draft",
                        "Merge PR",
                        "Close PR"),
                java.util.Arrays.stream(card.getComponentPopupMenu().getComponents())
                        .filter(JMenuItem.class::isInstance)
                        .map(component -> ((JMenuItem) component).getText())
                        .toList(),
                "authored PR should expose its lifecycle actions");
    }

    @Test
    void buildsProjectAndApplicationMenusWithoutSelections() {
        assertTrue(
                ProjectActions.menu(context(), ProjectId.create()).getComponentCount() > 0,
                "project menu should contain actions");
    }

    private static ActionContext context() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        return new ActionContext(new ViewCoordinator(state), state, null);
    }

    private static PullRequest request(
            final String review, final String mergeable, final boolean draft, final String checks) {
        return request(null, review, mergeable, draft, checks);
    }

    private static PullRequest request(
            final ProjectId projectId,
            final String review,
            final String mergeable,
            final boolean draft,
            final String checks) {
        return new PullRequest(
                projectId,
                12,
                "Fix login",
                "Description",
                "Comments",
                "https://example.test/12",
                "created",
                "updated",
                review,
                mergeable,
                draft,
                "author",
                "login-fix",
                2,
                3,
                checks);
    }
}
