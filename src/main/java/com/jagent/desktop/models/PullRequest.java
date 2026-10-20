package com.jagent.desktop.models;

import com.jagent.desktop.models.board.BoardItem;
import com.jagent.desktop.models.board.BoardSource;
import java.net.URL;
import java.util.Date;
import org.jetbrains.annotations.NotNull;

public class PullRequest implements BoardItem {
    private final ProjectId projectId;
    private final Project project;
    private final int number;
    private final State state;
    private final String title;
    private final String description;
    private final URL url;
    private final Date createdAt;
    private final Date updatedAt;
    private final GitHubUser author;
    private final String headBranch;
    private final String headSha;
    private final String baseBranch;

    public PullRequest(
            @NotNull final ProjectId projectId,
            @NotNull final Project project,
            final int number,
            @NotNull final State state,
            @NotNull final String title,
            @NotNull final String description,
            @NotNull final URL url,
            @NotNull final Date createdAt,
            @NotNull final Date updatedAt,
            @NotNull final GitHubUser author,
            @NotNull final String headBranch,
            @NotNull final String headSha,
            @NotNull final String baseBranch) {
        this.projectId = projectId;
        this.project = project;
        this.number = number;
        this.state = state;
        this.title = title;
        this.description = description;
        this.url = url;
        this.createdAt = new Date(createdAt.getTime());
        this.updatedAt = new Date(updatedAt.getTime());
        this.author = author;
        this.headBranch = headBranch;
        this.headSha = headSha;
        this.baseBranch = baseBranch;
    }

    public ProjectId projectId() {
        return projectId;
    }

    public Project project() {
        return project;
    }

    public int number() {
        return number;
    }

    public State state() {
        return state;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public URL url() {
        return url;
    }

    @Override
    public Date createdAt() {
        return new Date(createdAt.getTime());
    }

    @Override
    public Date updatedAt() {
        return new Date(updatedAt.getTime());
    }

    public GitHubUser author() {
        return author;
    }

    public String headBranch() {
        return headBranch;
    }

    public String headSha() {
        return headSha;
    }

    public String baseBranch() {
        return baseBranch;
    }

    @Override
    public BoardSource source() {
        return BoardSource.PULL_REQUEST;
    }

    @Override
    public String id() {
        return projectId + ":" + number;
    }

    public String abbreviatedHeadSha() {
        if (headSha.isBlank()) {
            return "missing";
        }
        if (headSha.length() <= 12) {
            return headSha;
        }
        return headSha.substring(0, 7) + "..." + headSha.substring(headSha.length() - 5);
    }

    public enum State {
        OPEN,
        CLOSED
    }
}
