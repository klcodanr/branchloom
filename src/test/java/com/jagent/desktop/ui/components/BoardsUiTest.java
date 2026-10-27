package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class BoardsUiTest {
    private static final String PROJECT_NAME = "Demo";
    private static final String PROJECT_PATH = "/tmp/demo";

    @Test
    void emptyPullRequestBoardShowsAndAcceptsFilters() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var context =
                new ActionContext(TestAppState.controller(state), state, null, null, null);
        final PullRequestsBoard board =
                GuiActionRunner.execute(() -> new PullRequestsBoard(context));
        board.setFilter(null);
        board.setFilter("missing");
        assertTrue(board.isVisible(), "board should remain visible after filter changes");
    }

    @Test
    void blankInitialQueryDoesNotLoadPullRequests() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(PROJECT_NAME, PROJECT_PATH, null));
        final var context =
                new ActionContext(TestAppState.controller(state), state, null, null, null);
        final PullRequestsBoard board =
                GuiActionRunner.execute(() -> new PullRequestsBoard(context));

        assertFalse(
                componentText(board).contains("Refreshing"),
                "blank queries should not trigger a pull request search");
    }

    private static String componentText(final java.awt.Component component) {
        if (component instanceof javax.swing.AbstractButton button) {
            return button.getText() == null ? "" : button.getText();
        }
        if (component instanceof javax.swing.JLabel label) {
            return label.getText() == null ? "" : label.getText();
        }
        if (component instanceof javax.swing.text.JTextComponent text) {
            return text.getText();
        }
        if (component instanceof javax.swing.JComponent container) {
            final var text = new StringBuilder();
            for (final var child : container.getComponents()) {
                text.append(componentText(child)).append(' ');
            }
            return text.toString();
        }
        return "";
    }
}
