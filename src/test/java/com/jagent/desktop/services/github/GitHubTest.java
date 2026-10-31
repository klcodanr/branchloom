package com.jagent.desktop.services.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitHubTest {
    private static final String GITHUB_EXAMPLE = "github.example";

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

    @Test
    void forProjectReplacesCachedInstanceWhenProjectChanges() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId =
                state.addProject(Project.create("Project", "/tmp/project", null));
        final GitHub original = GitHub.forProject(state, projectId);

        state.updateProject(projectId, state.projects().get(projectId).withName("Renamed project"));

        assertNotSame(
                original,
                GitHub.forProject(state, projectId),
                "changed project settings should invalidate the cached GitHub instance");
    }
}
