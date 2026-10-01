package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MyPullRequestsViewTest {
    @Test
    void viewIdentityAndRefreshWorkWithoutProjects() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);

        final MyPullRequestsView view = new MyPullRequestsView(context);
        view.refresh();
        view.detach();

        assertNotNull(view.render(), "view should render a panel");
        assertSame(view, view.render(), "render should return the view itself");
        assertEquals(ViewId.MY_PULL_REQUESTS, view.id(), "view id should match");
        assertEquals("Pull Requests", view.title(), "view title should match");
    }
}
