package com.jagent.desktop.ui;

import com.google.gson.Gson;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.board.BoardDefinition;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Defaults {
    public static final String DEFAULT_REVIEW_PROMPT =
            "Review pull request #{number}: {title}\n\n"
                    + "Inspect the changes carefully and report:\n"
                    + "1. Bugs or behavioral regressions\n"
                    + "2. Security or data-loss risks\n"
                    + "3. Missing tests\n"
                    + "4. Maintainability concerns\n\n"
                    + "Prioritize concrete findings with file and line references. "
                    + "If there are no findings, say so explicitly.";

    public static final String DEFAULT_WORKTREE_TEMPLATE =
            "{projectPath}/../{projectName}-{sessionSlug}";

    public static final String DEFAULT_GROUP = "Default";

    private Defaults() {}

    public static AppSettings appSettings() {
        try (Reader reader =
                new InputStreamReader(
                        Defaults.class.getResourceAsStream("/default-settings.json"),
                        StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, AppSettings.class);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load default settings", exception);
        }
    }

    public static List<BoardDefinition> boardDefinitions() {
        return appSettings().boards();
    }
}
