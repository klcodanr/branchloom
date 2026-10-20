package com.jagent.desktop.models;

import com.jagent.desktop.models.board.BoardDefinition;
import java.util.List;

public record AppSettings(
        List<Agent> agents,
        String reviewPrompt,
        String theme,
        List<Tool> tools,
        String worktreeTemplate,
        String agentContextPath,
        List<PullRequestFilter> pullRequestFilters,
        List<BoardDefinition> boards,
        String jiraEndpoint) {
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
            final String reviewPrompt,
            final String theme,
            final List<Tool> tools,
            final String worktreeTemplate) {
        this(
                agents,
                reviewPrompt,
                theme,
                tools,
                worktreeTemplate,
                "",
                defaultPullRequestFilters(),
                List.of(),
                "");
    }

    public AppSettings(
            final List<Agent> agents,
            final String reviewPrompt,
            final String theme,
            final List<Tool> tools,
            final String worktreeTemplate,
            final String agentContextPath) {
        this(
                agents,
                reviewPrompt,
                theme,
                tools,
                worktreeTemplate,
                agentContextPath,
                defaultPullRequestFilters(),
                List.of(),
                "");
    }

    public AppSettings {
        agents = agents == null ? List.of() : List.copyOf(agents);
        tools = tools == null ? List.of() : List.copyOf(tools);
        worktreeTemplate =
                worktreeTemplate == null || worktreeTemplate.isBlank()
                        ? DEFAULT_WORKTREE_TEMPLATE
                        : worktreeTemplate;
        agentContextPath = agentContextPath == null ? "" : agentContextPath.trim();
        pullRequestFilters = sanitizePullRequestFilters(pullRequestFilters);
        boards = sanitizeBoards(boards);
        jiraEndpoint = jiraEndpoint == null || jiraEndpoint.isBlank() ? null : jiraEndpoint.trim();
    }

    private static List<BoardDefinition> sanitizeBoards(final List<BoardDefinition> boards) {
        if (boards == null) {
            return List.of();
        }
        return boards.stream().filter(board -> board != null && !board.name().isBlank()).toList();
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
