package com.jagent.desktop.services;

import com.jagent.desktop.models.GitHubConnection;
import java.io.IOException;

/** Acquires a token for one GitHub connection type. */
@FunctionalInterface
public interface GitHubTokenProvider {
    GitHubToken acquire(GitHubConnection connection) throws IOException, InterruptedException;
}
