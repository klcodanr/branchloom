package com.jagent.desktop.models.git;

import java.nio.file.Path;

public record Worktree(Path path, String branch, boolean prunable) {}
