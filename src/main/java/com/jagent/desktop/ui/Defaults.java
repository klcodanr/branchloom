package com.jagent.desktop.ui;

import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.services.AgentDetection;
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

    public static final String DEFAULT_REVIEW_PLAN_PROMPT = AppSettings.DEFAULT_REVIEW_PLAN_PROMPT;

    private Defaults() {}

    public static AppSettings appSettings() {
        return new AppSettings(
                List.of(),
                List.of(),
                DEFAULT_REVIEW_PROMPT,
                "System",
                List.of(),
                DEFAULT_WORKTREE_TEMPLATE,
                false,
                AgentDetection.headlessCommand(),
                DEFAULT_REVIEW_PLAN_PROMPT);
    }
}
