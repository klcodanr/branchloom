package com.jagent.desktop.models.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardModelsTest {
    @Test
    void boardColumnCopiesRules() {
        final Map<BoardSource, String> rules = new EnumMap<>(BoardSource.class);
        rules.put(BoardSource.PULL_REQUEST, "state == 'OPEN'");
        final BoardColumn column = new BoardColumn("Todo", rules);

        assertEquals("Todo", column.title(), "column title should be preserved");
        assertEquals(rules, column.rules(), "column rules should be preserved");
        assertNotSame(rules, column.rules(), "column rules should be copied");
    }

    @Test
    void boardConfigPreservesColumnOrderAndDefaultsSources() {
        final List<BoardColumn> columns =
                new ArrayList<>(
                        List.of(
                                new BoardColumn("Todo", Map.of()),
                                new BoardColumn("Done", Map.of())));
        final BoardConfig config = new BoardConfig(columns, null);

        columns.clear();

        assertEquals(2, config.columns().size(), "column order should be preserved");
        assertFalse(
                config.sources().AGENT_SESSION().enabled(),
                "default agent sessions should be disabled");
        assertEquals(
                BoardConfig.empty(), new BoardConfig(null, null), "null config should be empty");
    }
}
