package com.jagent.desktop.services.github;

import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.models.github.GitHubToken;
import java.io.IOException;

/** Acquires a token for one GitHub connection type. */
@FunctionalInterface
public interface GitHubTokenProvider<C extends Credential> {
    GitHubToken acquire(Credential credential) throws IOException;
}
