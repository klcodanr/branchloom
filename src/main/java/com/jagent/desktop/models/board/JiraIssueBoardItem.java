package com.jagent.desktop.models.board;

import java.net.URL;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A Jira issue normalized for board rules and presentation. */
public record JiraIssueBoardItem(
        BoardSource source,
        String id,
        String title,
        String description,
        @NotNull Date createdAt,
        @NotNull Date updatedAt,
        @Nullable URL url,
        @Nullable String boardProjectId,
        String status,
        String author,
        String assignee)
        implements BoardItem {}
