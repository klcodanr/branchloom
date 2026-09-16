package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.Defaults;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ReviewQueueViewUiTest {
    private static final String START_REVIEW_PLAN = "Start review plan";

    @Test
    void rendersEmptyReviewQueueAndPlan() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        final var view = GuiActionRunner.execute(() -> new ReviewQueueView(context));
        GuiActionRunner.execute(() -> {});

        assertEquals(ViewId.REVIEW_QUEUE, view.id(), "view identity should match");
        assertEquals("Review Queue", view.title(), "view title should match");
        assertSame(view, view.render(), "render should return the view");
        assertEquals(
                2,
                ((JTabbedPane) view.getComponent(0)).getTabCount(),
                "review queue should expose queue and plan tabs");
        view.dispose();
    }

    @Test
    void reviewPlanStartButtonStaysEnabledWhenAgentExists() throws InterruptedException {
        final AppSettings settings =
                new AppSettings(
                        List.of(new Agent("Demo Agent", "echo {prompt}")),
                        List.of(),
                        Defaults.DEFAULT_REVIEW_PROMPT,
                        "System",
                        List.of(),
                        Defaults.DEFAULT_WORKTREE_TEMPLATE,
                        "",
                        Defaults.DEFAULT_REVIEW_PLAN_PROMPT);
        final AppState state = new AppState(settings, Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        final var view = GuiActionRunner.execute(() -> new ReviewQueueView(context));
        final var tabs = (JTabbedPane) view.getComponent(0);
        GuiActionRunner.execute(() -> tabs.setSelectedIndex(1));
        SwingTestSupport.await(
                () -> SwingTestSupport.findButton(view, START_REVIEW_PLAN) != null,
                "review plan launcher should render");
        final JButton startButton = SwingTestSupport.findButton(view, START_REVIEW_PLAN);

        assertTrue(
                startButton.isEnabled(), "start button should stay enabled for configured agent");
        view.dispose();
    }

    @Test
    void reviewPlanStartButtonIsDisabledWithoutAgents() throws InterruptedException {
        final AppSettings settings =
                new AppSettings(
                        List.of(),
                        List.of(),
                        Defaults.DEFAULT_REVIEW_PROMPT,
                        "System",
                        List.of(),
                        Defaults.DEFAULT_WORKTREE_TEMPLATE,
                        "",
                        Defaults.DEFAULT_REVIEW_PLAN_PROMPT);
        final AppState state = new AppState(settings, Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        final var view = GuiActionRunner.execute(() -> new ReviewQueueView(context));
        final var tabs = (JTabbedPane) view.getComponent(0);
        GuiActionRunner.execute(() -> tabs.setSelectedIndex(1));
        SwingTestSupport.await(
                () -> SwingTestSupport.findButton(view, START_REVIEW_PLAN) != null,
                "review plan launcher should render");
        final JButton startButton = SwingTestSupport.findButton(view, START_REVIEW_PLAN);

        assertFalse(startButton.isEnabled(), "start button should be disabled without agents");
        view.dispose();
    }
}
