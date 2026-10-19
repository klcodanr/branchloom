package com.jagent.desktop.models.board;

import java.util.Map;
import java.util.Objects;

public record BoardColumn(String title, Map<BoardSource, String> rules) {

    public BoardColumn {
        title = Objects.requireNonNullElse(title, "");
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    public static BoardColumn empty() {
        return new BoardColumn("", Map.of());
    }
}
