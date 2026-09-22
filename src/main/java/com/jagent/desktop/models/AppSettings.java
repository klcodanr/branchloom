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
        List<PullRequestFilter> pullRequestFilters) {
    private static final String DEFAULT_WORKTREE_TEMPLATE =
            "{projectPath}/../{projectName}-{sessionSlug}";
    private static final PullRequestFilter ACTIVE_FILTER =
            new PullRequestFilter("My PRs", "author:@me");
    private static final PullRequestFilter REVIEWABLE_FILTER =
            new PullRequestFilter("Reviewable", "review-requested:@me -status:failure");
    private static final PullRequestFilter MY_REVIEWS_FILTER =
            new PullRequestFilter(
                    "My Reviews",
                    "reviewed-by:@me is:unmerged -author:adobe-renovate-prod[bot] -author:@me");
    private static final PullRequestFilter FALLBACK_FILTER =
            new PullRequestFilter("All Open PRs", "");

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
                defaultPullRequestFilters());
    }

    public AppSettings(
            final List<Agent> agents,
            final List<String> groupOrder,
            final String reviewPrompt,
            final String theme,
            final List<Tool> tools,
            final String worktreeTemplate,
            final String agentContextPath) {
        this(
                agents,
                groupOrder,
                reviewPrompt,
                theme,
                tools,
                worktreeTemplate,
                agentContextPath,
                defaultPullRequestFilters());
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
        pullRequestFilters = sanitizePullRequestFilters(pullRequestFilters);
    }

    public PullRequestFilter filterNamed(final String name) {
        if (name == null || name.isBlank()) {
            return pullRequestFilters.getFirst();
        }
        return pullRequestFilters.stream()
                .filter(filter -> filter.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseGet(pullRequestFilters::getFirst);
    }

    public static List<PullRequestFilter> defaultPullRequestFilters() {
        return List.of(ACTIVE_FILTER, REVIEWABLE_FILTER, MY_REVIEWS_FILTER);
    }

    private static List<PullRequestFilter> sanitizePullRequestFilters(
            final List<PullRequestFilter> filters) {
        if (filters == null) {
            return defaultPullRequestFilters();
        }
        final List<PullRequestFilter> configured =
                filters.stream()
                        .filter(filter -> filter != null && !filter.name().isBlank())
                        .toList();
        if (configured.isEmpty()) {
            return List.of(FALLBACK_FILTER);
        }
        return List.copyOf(configured);
    }
}
