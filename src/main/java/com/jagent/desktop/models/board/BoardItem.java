package com.jagent.desktop.models.board;

import java.net.URL;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BoardItem {
    BoardSource source();

    String id();

    @NotNull
    String title();

    @NotNull
    String description();

    @NotNull
    Date createdAt();

    @NotNull
    Date updatedAt();

    @Nullable
    URL url();
}
