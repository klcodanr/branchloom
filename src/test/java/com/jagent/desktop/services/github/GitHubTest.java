package com.jagent.desktop.services.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jagent.desktop.models.github.Credential;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitHubTest {
    private static final String GITHUB_EXAMPLE = "github.example";

    @Test
    void listCredentialsIncludesDefaultCliOption() {
        final List<Credential> auths = new GitHubAuth().listCredentials();

        assertFalse(auths.isEmpty(), "default auth should be available");
    }

    @Test
    void apiEndpointUsesGitHubComForDefaultHostCaseInsensitively() {
        assertEquals(
                "https://api.github.com",
                GitHubAuth.apiEndpoint("github.com"),
                "default GitHub host should use public API endpoint");
        assertEquals(
                "https://api.github.com",
                GitHubAuth.apiEndpoint("GitHub.Com"),
                "default host matching should be case-insensitive");
    }

    @Test
    void apiEndpointUsesEnterprisePathForCustomHost() {
        assertEquals(
                "https://github.example/api/v3",
                GitHubAuth.apiEndpoint(GITHUB_EXAMPLE),
                "custom host should map to enterprise API path");
    }

    @Test
    void apiEndpointUsesPublicApiForNullHost() {
        assertEquals(
                "https://api.github.com",
                GitHubAuth.apiEndpoint(null),
                "missing host should use the public GitHub API endpoint");
    }
}
