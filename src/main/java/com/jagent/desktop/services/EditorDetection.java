package com.jagent.desktop.services;

import com.jagent.desktop.models.Tool;
import java.util.ArrayList;
import java.util.List;

/** Detects supported editor command-line launchers available on PATH. */
public final class EditorDetection {
    private EditorDetection() {}

    public static List<Tool> detect() {
        final List<Tool> editors = new ArrayList<>();
        addIfAvailable(editors, "VS Code", "code", "code .");
        addIfAvailable(editors, "Cursor", "cursor", "cursor .");
        addIfAvailable(editors, "Zed", "zed", "zed .");
        addIfAvailable(editors, "Sublime Text", "subl", "subl .");
        addIfAvailable(editors, "IntelliJ IDEA", "idea", "idea .");
        addIfAvailable(editors, "Neovim", "nvim", "nvim .");
        addIfAvailable(editors, "Vim", "vim", "vim .");
        return List.copyOf(editors);
    }

    private static void addIfAvailable(
            final List<Tool> editors,
            final String label,
            final String executable,
            final String command) {
        if (PlatformCommands.commandAvailable(executable)) {
            editors.add(new Tool(label, command));
        }
    }
}
