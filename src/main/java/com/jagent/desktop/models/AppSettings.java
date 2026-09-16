package com.jagent.desktop.models;

import java.util.List;

public record AppSettings(
        List<Agent> agents,
        List<String> groupOrder,
        String reviewPrompt,
        String theme,
        List<Tool> tools,
        String worktreeTemplate,
        String agentContextPath,
        String reviewPlanPrompt) {
    private static final String DEFAULT_WORKTREE_TEMPLATE =
            "{projectPath}/../{projectName}-{sessionSlug}";
    public static final String DEFAULT_REVIEW_PLAN_PROMPT =
            "Prioritize the supplied pull requests for review urgency using the provided fields "
                    + "(change size, checks, mergeability, draft state, recency, and comments). "
                    + "For each selected pull request, provide a short change description, an "
                    + "urgency rationale, review focus, and blockers (or NONE).";

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
        agentContextPath = agentContextPath == null ? "" : agentContextPath.trim();
        reviewPlanPrompt =
                reviewPlanPrompt == null || reviewPlanPrompt.isBlank()
                        ? DEFAULT_REVIEW_PLAN_PROMPT
                        : reviewPlanPrompt.trim();
    }
}
