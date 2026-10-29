package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class BoardsUiTest {
    private static final String PROJECT_NAME = "Demo";
    private static final String PROJECT_PATH = "/tmp/demo";

    @Test
    void blankInitialQueryShowsQueryPromptWithoutLoadingPullRequests() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var context =
                new ActionContext(TestAppState.controller(state), state, null, null, null);
        final PullRequestsBoard board =
                GuiActionRunner.execute(() -> new PullRequestsBoard(context));

        assertNotNull(
                SwingTestSupport.find(
                        board,
                        javax.swing.JLabel.class,
                        label -> "Enter a pull request query".equals(label.getText())),
                "blank queries should show the query prompt");
    }
}
