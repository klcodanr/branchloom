package com.jagent.desktop.models.board;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.SessionId;
import java.net.URL;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record AgentSessionBoardItem(
        String state,
        String agent,
        String project,
        String branch,
        ProjectId projectId,
        SessionId sessionId,
        BoardSource source,
        String id,
        String title,
        String description,
        @NotNull Date createdAt,
        @NotNull Date updatedAt,
        @Nullable URL url)
        implements BoardItem {}
