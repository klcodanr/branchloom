package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.ui.Defaults;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PullRequestCacheTest {
    @Test
    void missingProjectReturnsAndCachesNoRequests() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final PullRequestCache cache = PullRequestCache.get(state);
        final var projectId = ProjectId.create();

        final var first = cache.get(projectId);

        assertEquals(0, first.authored().size(), "missing projects should have no authored PRs");
        assertEquals(0, first.review().size(), "missing projects should have no review PRs");
        assertFalse(cache.hasCached(projectId), "missing projects should not be cached");
    }

    @Test
    void unknownProjectCanBeReadAsEmptyWithoutCacheEntry() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final PullRequestCache cache = PullRequestCache.get(state);
        final var projectId = ProjectId.create();

        assertEquals(
                new PullRequestCache.ProjectPullRequests(List.of(), List.of()),
                cache.getCached(projectId),
                "unknown projects should read as empty");
        assertEquals(
                cache.getCached(projectId), cache.getCached(projectId), "reads should be stable");
    }

    @Test
    void projectLoadFailureReturnsEmptyRequests() throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Missing", "/missing/project", null));
        final PullRequestCache cache = PullRequestCache.get(state);

        final var requests = cache.refresh(projectId);

        assertTrue(
                requests.authored().isEmpty(), "failed project loads should have no authored PRs");
        assertTrue(requests.review().isEmpty(), "failed project loads should have no review PRs");
    }
}
