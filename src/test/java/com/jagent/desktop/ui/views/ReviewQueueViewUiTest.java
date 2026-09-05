package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;
import javax.swing.JTabbedPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ReviewQueueViewUiTest {
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
}
