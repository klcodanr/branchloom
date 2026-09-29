package com.jagent.desktop.services;

import java.time.Instant;
import org.jetbrains.annotations.Nullable;

/** A GitHub credential and the metadata needed to decide when it can be reused. */
public record GitHubToken(String value, @Nullable Instant expiresAt, boolean renewable) {}
