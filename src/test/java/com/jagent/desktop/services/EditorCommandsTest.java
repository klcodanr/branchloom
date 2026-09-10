package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.models.Tool;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EditorCommandsTest {
    private static final Path FILE =
            Path.of(System.getProperty("java.io.tmpdir"), "project", "file.java");
    private static final int LINE = 12;
    private static final int COLUMN = 4;

    @Test
    void buildsCodeCommandWithLocation() {
        assertEquals(
                "code --goto "
                        + PlatformCommands.shellQuote(FILE.toString())
                        + ":"
                        + LINE
                        + ":"
                        + COLUMN,
                EditorCommands.openFile(new Tool("VS Code", "code ."), FILE, LINE, COLUMN),
                "VS Code should receive its goto location syntax");
    }

    @Test
    void buildsVimCommandWithLineAndClampsMissingLine() {
        assertEquals(
                "nvim +1 " + PlatformCommands.shellQuote(FILE.toString()),
                EditorCommands.openFile(new Tool("Neovim", "nvim ."), FILE, 0, 0),
                "Neovim should receive a valid line argument");
    }

    @Test
    void buildsFallbackCommandForOtherEditors() {
        assertEquals(
                "zed " + PlatformCommands.shellQuote(FILE.toString()),
                EditorCommands.openFile(new Tool("Zed", "zed ."), FILE, 20, 3),
                "unsupported location syntax should still open the file");
    }
}
