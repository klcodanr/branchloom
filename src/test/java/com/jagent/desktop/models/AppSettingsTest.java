package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jagent.desktop.models.board.BoardDefinition;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppSettingsTest {
    @Test
    void normalizesMissingValuesAndUsesDefaultFilters() {
        final var settings =
                new AppSettings(null, null, null, null, null, null, null, null, " ", null);

        assertEquals(List.of(), settings.agents(), "missing agents should be empty");
        assertEquals(List.of(), settings.tools(), "missing tools should be empty");
        assertEquals(
                "{projectPath}/../{projectName}-{sessionSlug}",
                settings.worktreeTemplate(),
                "missing worktree templates should use the default");
        assertEquals("", settings.agentContextPath(), "missing context paths should be empty");
        assertEquals(
                AppSettings.defaultPullRequestFilters(),
                settings.pullRequestFilters(),
                "missing filters should use defaults");
        assertEquals(List.of(), settings.boards(), "missing boards should be empty");
        assertNull(settings.jiraEndpoint(), "blank Jira endpoints should be absent");
        assertNull(settings.projectGroups(), "missing project groups should remain absent");
    }

    @Test
    void sanitizesConfiguredFiltersAndBoards() {
        final var settings =
                new AppSettings(
                        List.of(),
                        "prompt",
                        "theme",
                        List.of(),
                        "template",
                        " context ",
                        Arrays.asList(null, new PullRequestFilter(" ", "ignored")),
                        Arrays.asList(
                                null,
                                new BoardDefinition(" ", null),
                                new BoardDefinition("Review", null)),
                        " https://jira.example ",
                        List.of("Work"));

        assertEquals(
                List.of(new PullRequestFilter("All Open PRs", "")),
                settings.pullRequestFilters(),
                "empty configured filters should use the fallback");
        assertEquals(1, settings.boards().size(), "blank boards should be removed");
        assertEquals("Review", settings.boards().getFirst().name(), "valid boards should remain");
        assertEquals("context", settings.agentContextPath(), "context paths should be trimmed");
        assertEquals(
                "https://jira.example",
                settings.jiraEndpoint(),
                "Jira endpoints should be trimmed");
    }
}
