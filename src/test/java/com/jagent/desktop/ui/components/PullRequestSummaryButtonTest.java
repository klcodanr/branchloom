package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class PullRequestSummaryButtonTest {
    @Test
    void rendersLoadingAndUnavailableStates() {
        final PullRequestSummaryButton button =
                GuiActionRunner.execute(PullRequestSummaryButton::new);

        assertTrue(
                button.getText().contains("Loading pull request"),
                "button should show loading state");

        GuiActionRunner.execute(() -> button.setPullRequest(null));

        assertTrue(
                button.getText().contains("Could not load pull request"),
                "button should show unavailable state");
    }
}
