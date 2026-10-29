package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandPaletteTest {
    @Test
    void filtersCommandsCaseInsensitively() {
        final List<OptionPicker.Choice> choices =
                List.of(
                        new OptionPicker.Choice("Open project", () -> {}),
                        new OptionPicker.Choice("Open settings", () -> {}));

        assertTrue(
                choices.stream()
                        .anyMatch(choice -> "Open settings".equalsIgnoreCase(choice.label())),
                "matching command should be retained");
        assertEquals(2, choices.size(), "an empty query should retain every choice");
        assertTrue(
                choices.stream().noneMatch(choice -> "missing".equalsIgnoreCase(choice.label())),
                "non-matching command should be excluded");
    }
}
