package com.jagent.desktop.models;

import java.util.List;

public record AppSettings(
        List<Agent> agents,
        List<String> groupOrder,
        String reviewPrompt,
        String theme,
        List<Tool> tools,
        String worktreeTemplate,
        boolean reviewPlanEnabled,
        String reviewPlanCommand,
        String reviewPlanPrompt) {
    private static final String DEFAULT_WORKTREE_TEMPLATE =
            "{projectPath}/../{projectName}-{sessionSlug}";
    public static final String DEFAULT_REVIEW_PLAN_PROMPT =
            "Prioritize the review requests below. For each pull request, explain why it belongs "
                    + "in that position, what to focus on, and any blockers. Return a concise "
                    + "review plan with the pull request number and project name.";

    public AppSettings(
            final List<Agent> agents,
            final List<String> groupOrder,
            final String reviewPrompt,
            final String theme,
            final List<Tool> tools,
            final String worktreeTemplate) {
        this(
                agents,
                groupOrder,
                reviewPrompt,
                theme,
                tools,
                worktreeTemplate,
                false,
                "",
                DEFAULT_REVIEW_PLAN_PROMPT);
    }

    public AppSettings {
        agents = agents == null ? List.of() : List.copyOf(agents);
        groupOrder = groupOrder == null ? List.of() : List.copyOf(groupOrder);
        tools = tools == null ? List.of() : List.copyOf(tools);
        worktreeTemplate =
                worktreeTemplate == null || worktreeTemplate.isBlank()
                        ? DEFAULT_WORKTREE_TEMPLATE
                        : worktreeTemplate;
        reviewPlanCommand = reviewPlanCommand == null ? "" : reviewPlanCommand.trim();
        reviewPlanPrompt =
                reviewPlanPrompt == null || reviewPlanPrompt.isBlank()
                        ? DEFAULT_REVIEW_PLAN_PROMPT
                        : reviewPlanPrompt.trim();
    }
}
