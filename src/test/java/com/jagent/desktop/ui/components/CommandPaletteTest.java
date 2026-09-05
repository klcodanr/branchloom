package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandPaletteTest {
    @Test
    void filtersCommandsCaseInsensitively() {
        final List<CommandPalette.Choice> choices =
                List.of(
                        new CommandPalette.Choice("Open project", () -> {}),
                        new CommandPalette.Choice("Open settings", () -> {}));

        assertEquals(
                List.of("Open settings"),
                CommandPalette.filteredChoices(choices, "settings")
                        .map(CommandPalette.Choice::label)
                        .toList(),
                "matching should be case-insensitive");
        assertEquals(
                2,
                CommandPalette.filteredChoices(choices, "").count(),
                "an empty query should retain every choice");
        assertTrue(
                CommandPalette.filteredChoices(choices, "missing").findAny().isEmpty(),
                "an unmatched query should return no choices");
    }
}
