package com.jagent.desktop.models.board;

import java.net.URL;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record GithubIssueBoardItem(
        int number,
        String state,
        String project,
        BoardSource source,
        String id,
        String title,
        String description,
        @NotNull Date createdAt,
        @NotNull Date updatedAt,
        @Nullable URL url,
        @Nullable String boardProjectId)
        implements BoardItem {}
