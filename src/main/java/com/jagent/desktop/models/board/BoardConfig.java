package com.jagent.desktop.models.board;

import com.jagent.desktop.models.board.sources.SourceConfigs;
import java.util.List;

public record BoardConfig(List<BoardColumn> columns, SourceConfigs sources) {

    public BoardConfig {
        columns = columns == null ? List.of() : List.copyOf(columns);
        sources = sources != null ? sources : SourceConfigs.empty();
    }

    public static BoardConfig empty() {
        return new BoardConfig(List.of(), SourceConfigs.empty());
    }
}
