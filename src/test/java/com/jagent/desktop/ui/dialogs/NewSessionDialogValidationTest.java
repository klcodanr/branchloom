package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jagent.desktop.models.Agent;
import org.junit.jupiter.api.Test;

class NewSessionDialogValidationTest {
    @Test
    void validateRequiresSessionName() {
        final String message =
                NewSessionDialog.validationFailure("   ", new Agent("A", "run"), "go");

        assertEquals(
                "Session name is required.", message, "blank session names should fail validation");
    }

    @Test
    void validateRequiresAgent() {
        final String message = NewSessionDialog.validationFailure("session", null, "go");

        assertEquals("Select an agent.", message, "missing agent selection should fail validation");
    }

    @Test
    void validateRequiresPrompt() {
        final String message =
                NewSessionDialog.validationFailure("session", new Agent("A", "run"), "\t");

        assertEquals("Prompt is required.", message, "blank prompt should fail validation");
    }

    @Test
    void validateAcceptsCompleteRequest() {
        final String message =
                NewSessionDialog.validationFailure("session", new Agent("A", "run"), "go");

        assertNull(message, "complete request should pass validation");
    }
}
