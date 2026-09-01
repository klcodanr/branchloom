package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.models.Tool;
import org.junit.jupiter.api.Test;

class EditorDetectionTest {
    @Test
    void detectsOnlyAvailableEditorsWithMatchingCommands() {
        EditorDetection.detect()
                .forEach(
                        editor -> {
                            assertNotNull(editor.label(), "available editors should have labels");
                            assertFalse(
                                    editor.command().isBlank(),
                                    "available editors should have commands");
                        });
        assertEquals(
                EditorDetection.detect().stream().map(Tool::label).distinct().count(),
                EditorDetection.detect().size(),
                "detected editor labels should be unique");
    }
}
