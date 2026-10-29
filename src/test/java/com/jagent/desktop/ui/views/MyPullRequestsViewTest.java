package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class MyPullRequestsViewTest {
    @Test
    void showsQueryPromptWithoutProjects() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ActionContext context =
                new ActionContext(TestAppState.controller(state), state, null, null, null);

        final MyPullRequestsView view = new MyPullRequestsView(context);
        assertNotNull(
                SwingTestSupport.find(
                        view,
                        JLabel.class,
                        label -> "Enter a pull request query".equals(label.getText())),
                "an empty pull request view should show the query prompt");
        view.detach();

        assertSame(view, view.render(), "render should return the view itself");
        assertEquals(ViewId.PULL_REQUESTS, view.id(), "view id should match");
        assertEquals("Pull Requests", view.title(), "view title should match");
    }
}
