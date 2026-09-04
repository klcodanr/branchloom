package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;
import javax.swing.JScrollPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ViewsUiTest {
    private static final String VALUE_MESSAGE = "view value should match";

    @Test
    void problemsViewExposesItsViewIdentityAndRefreshes() {
        final var view = GuiActionRunner.execute(() -> new ProblemsView());

        assertEquals(ViewId.PROBLEMS, view.id(), VALUE_MESSAGE);
        assertEquals("Problems", view.title(), VALUE_MESSAGE);
        assertSame(view, view.render(), VALUE_MESSAGE);
    }

    @Test
    void resourceUsageViewExposesItsViewIdentityAndRefreshes() {
        final var view = GuiActionRunner.execute(() -> new ResourceUsageView());

        assertEquals(ViewId.RESOURCE_USAGE, view.id(), VALUE_MESSAGE);
        assertEquals("Resource Usage", view.title(), VALUE_MESSAGE);
        assertSame(view, view.render(), VALUE_MESSAGE);
    }

    @Test
    void homeViewBuildsItsDashboardForAnEmptyApplication() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        final var view = GuiActionRunner.execute(() -> new HomeView(context));

        assertEquals(ViewId.HOME, view.id(), VALUE_MESSAGE);
        assertEquals("Home", view.title(), VALUE_MESSAGE);
        assertEquals(1, view.getComponentCount(), VALUE_MESSAGE);
        assertEquals(JScrollPane.class, view.getComponent(0).getClass(), VALUE_MESSAGE);
        assertSame(view, view.render(), VALUE_MESSAGE);
        view.dispose();
    }
}
