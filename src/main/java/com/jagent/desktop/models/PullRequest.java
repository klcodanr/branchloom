package com.jagent.desktop.models;

import java.net.URL;
import java.util.Date;
import org.jetbrains.annotations.NotNull;

public record PullRequest(
        @NotNull ProjectId projectId,
        @NotNull Project project,
        @NotNull int number,
        @NotNull State state,
        @NotNull String title,
        @NotNull String description,
        @NotNull URL url,
        @NotNull Date createdAt,
        @NotNull Date updatedAt,
        @NotNull GitHubUser author,
        @NotNull String headBranch,
        @NotNull String headSha,
        @NotNull String baseBranch) {

    public String abbreviatedHeadSha() {
        if (headSha().isBlank()) {
            return "missing";
        }
        if (headSha().length() <= 12) {
            return headSha();
        }
        return headSha().substring(0, 7) + "..." + headSha().substring(headSha().length() - 5);
    }

    public enum State {
        OPEN,
        CLOSED,
        MERGED
    }
}
