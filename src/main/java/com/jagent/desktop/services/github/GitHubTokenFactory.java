package com.jagent.desktop.services.github;

import com.jagent.desktop.models.github.CliCredential;
import com.jagent.desktop.models.github.Credential;
import java.io.IOException;

/** Acquires GitHub CLI tokens. */
public final class GitHubTokenFactory {
    private final GitHubTokenProvider<CliCredential> cliTokenProvider;

    public GitHubTokenFactory(final GitHubTokenProvider<CliCredential> cliTokenProvider) {
        this.cliTokenProvider = cliTokenProvider;
    }

    public static GitHubTokenFactory create() {
        return new GitHubTokenFactory(new CliTokenProvider());
    }

    public String getToken(final Credential credential) throws IOException {
        return cliTokenProvider.acquire(credential).value();
    }
}
