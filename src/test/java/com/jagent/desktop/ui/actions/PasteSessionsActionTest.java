package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import org.junit.jupiter.api.Test;

class PasteSessionsActionTest {
    @Test
    void templateReplacesEveryPromptToken() {
        assertEquals(
                "Please fix: Add login validation, then test Add login validation.",
                PasteSessionsAction.template(
                        "Please fix: {prompt}, then test {prompt}.", "Add login validation"),
                "template should replace every prompt token");
    }

    @Test
    void blankTemplateUsesThePastedLineAsPrompt() {
        assertEquals(
                "Add login validation",
                PasteSessionsAction.template("", "Add login validation"),
                "blank template should use the pasted line");
    }

    @Test
    void actionRequiresASelectedProject() {
        final AppState state = TestAppState.empty();
        final PasteSessionsAction action = new PasteSessionsAction(TestAppState.context(state));

        assertFalse(action.enabled(), "paste action should require a selected project");
        action.execute();
    }

    @Test
    void actionIsEnabledForASelectedProject() {
        final AppState state = TestAppState.empty();
        final var projectId = state.addProject(new Project("Demo", "/tmp", null));
        state.updateCurrentProject(projectId);
        final PasteSessionsAction action = new PasteSessionsAction(TestAppState.context(state));

        assertTrue(action.enabled(), "paste action should be enabled for a selected project");
    }
}
