package com.jagent.desktop.models;

import com.jagent.desktop.services.git.GitRepository;

public record SessionSummaryInfo(
        String agentContext,
        PullRequestDetails pullRequest,
        GitRepository.WorktreeStatus worktreeStatus) {}
