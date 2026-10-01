package com.jagent.desktop.services.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.models.github.PatCredential;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.Defaults;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitHubTest {
    private static final String GITHUB_EXAMPLE = "github.example";

    @Test
    void listCredentialsIncludesDefaultCliOption() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());

        final List<Credential> auths = new GitHubAuth().listCredentials(state);

        assertFalse(auths.isEmpty(), "default auth should be available");
    }

    @Test
    void listCredentialsIncludesPatConnections() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addGitHubConnection(new PatCredential("pat-1", "github.com", "Personal token"));

        final List<Credential> auths = new GitHubAuth().listCredentials(state);

        assertTrue(auths.size() >= 2, "PAT connection should be included alongside CLI auths");
        assertTrue(
                auths.stream().map(Credential::id).anyMatch("pat-1"::equals),
                "PAT id should be retained");
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
