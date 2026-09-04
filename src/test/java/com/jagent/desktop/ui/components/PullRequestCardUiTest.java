package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.Defaults;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
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
                List.of(
                        card.getComponentPopupMenu().getComponent(0) instanceof JMenuItem item
                                ? item.getText()
                                : "",
                        card.getComponentPopupMenu().getComponent(2) instanceof JMenuItem item
                                ? item.getText()
                                : "",
                        card.getComponentPopupMenu().getComponent(3) instanceof JMenuItem item
                                ? item.getText()
                                : ""),
                "context menu should expose supported actions");
    }

    private static ActionContext context() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        return new ActionContext(new ViewCoordinator(state), state, null);
    }

    private static PullRequest request(
            final String review, final String mergeable, final boolean draft, final String checks) {
        return new PullRequest(
                null,
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
