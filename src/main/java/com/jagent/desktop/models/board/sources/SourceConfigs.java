package com.jagent.desktop.models.board.sources;

public record SourceConfigs(
        AgentSessionConfig AGENT_SESSION,
        GithubIssuesConfig GITHUB_ISSUES,
        PullRequestConfig PULL_REQUEST,
        JiraConfig JIRA) {

    public static SourceConfigs empty() {
        return new SourceConfigs(
                new AgentSessionConfig(false),
                new GithubIssuesConfig(false, ""),
                new PullRequestConfig(false, ""),
                new JiraConfig(false, ""));
    }
}
