package com.jagent.desktop.models.board;

/** A named board with its own columns, mappings, and sources. */
public record BoardDefinition(String name, BoardConfig config) implements java.io.Serializable {
    public BoardDefinition {
        name = name == null ? "" : name.trim();
        config = config == null ? BoardConfig.empty() : config;
    }
}
