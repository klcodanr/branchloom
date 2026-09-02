package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentContextTest {
    @Test
    void writesConfiguredContextIntoTheWorktree(
            @org.junit.jupiter.api.io.TempDir final Path worktree) throws IOException {
        final Project project =
                new Project(
                        "Demo",
                        worktree.toString(),
                        null,
                        "github.example",
                        "dev",
                        null,
                        null,
                        List.of("./gradlew test"),
                        List.of(),
                        ".branchloom/context.md",
                        "Use the repository conventions.");
        final Session session =
                new Session(null, "Fix login", "Codex", "Fix it", worktree.toString());

        AgentContext.write(project, session);

        final Path context = worktree.resolve(".branchloom/context.md");
        final String content = Files.readString(context);
        assertTrue(content.contains("Demo"), "context should include the project name");
        assertFalse(
                content.contains("./gradlew test"), "context should not include startup commands");
        assertTrue(content.contains("github.example"), "context should include the GitHub host");
        assertTrue(content.contains("dev"), "context should include the GitHub user");
        assertTrue(
                content.contains("Use the repository conventions."),
                "context should include custom context text");
        assertFalse(content.contains("Branchloom"), "context should not include product branding");
        assertFalse(content.contains("Codex"), "context should not include the agent name");
        assertFalse(content.contains("Fix login"), "context should not include the session name");
    }

    @Test
    void doesNotWriteWhenContextPathIsBlank(@org.junit.jupiter.api.io.TempDir final Path worktree)
            throws IOException {
        final Project project = new Project("Demo", worktree.toString(), null);
        final Session session =
                new Session(null, "Fix login", "Codex", "Fix it", worktree.toString());

        AgentContext.write(project, session);

        assertFalse(
                Files.exists(worktree.resolve(".branchloom/context.md")),
                "blank context paths should not create a file");
    }
}
