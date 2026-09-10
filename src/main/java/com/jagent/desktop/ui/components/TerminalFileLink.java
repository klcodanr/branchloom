package com.jagent.desktop.ui.components;

import java.nio.file.Path;

/** A terminal path and its optional source location. */
public record TerminalFileLink(Path path, int line, int column) {}
