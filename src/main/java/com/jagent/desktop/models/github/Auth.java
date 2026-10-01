package com.jagent.desktop.models.github;

import org.jetbrains.annotations.NotNull;

public record Auth(String host, @NotNull String token) {}
