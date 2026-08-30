package com.jagent.desktop.models;

import java.util.List;

public record AppSettings(
        List<Agent> agents,
        List<String> groupOrder,
        String reviewPrompt,
        String theme,
        List<Tool> tools,
        String worktreeTemplate) {
    private static final String DEFAULT_WORKTREE_TEMPLATE =
            "{projectPath}/../{projectName}-{sessionSlug}";

    public AppSettings {
        agents = agents == null ? List.of() : List.copyOf(agents);
        groupOrder = groupOrder == null ? List.of() : List.copyOf(groupOrder);
        tools = tools == null ? List.of() : List.copyOf(tools);
        worktreeTemplate =
                worktreeTemplate == null || worktreeTemplate.isBlank()
                        ? DEFAULT_WORKTREE_TEMPLATE
                        : worktreeTemplate;
    }
}
