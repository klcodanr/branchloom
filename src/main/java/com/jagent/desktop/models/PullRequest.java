package com.jagent.desktop.models;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(PullRequest.class);

    public static PullRequest from(
            @NotNull final ProjectId projectId,
            @NotNull final Project project,
            @NotNull final GHPullRequest request) {
        try {
            return new PullRequest(
                    projectId,
                    project,
                    request.getNumber(),
                    request.getState() == null
                            ? State.OPEN
                            : State.valueOf(request.getState().toString()),
                    request.getTitle() == null ? "" : request.getTitle(),
                    request.getBody() == null ? "" : request.getBody(),
                    request.getHtmlUrl(),
                    request.getCreatedAt(),
                    request.getUpdatedAt(),
                    GitHubUser.from(request.getUser()),
                    request.getHead() == null ? "" : request.getHead().getRef(),
                    request.getHead() == null ? "" : request.getHead().getSha(),
                    request.getBase() == null ? "" : request.getBase().getRef());
        } catch (IOException e) {
            LOG.error("Failed to create PullRequest from GHPullRequest", e);
        }
        return null;
    }

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
