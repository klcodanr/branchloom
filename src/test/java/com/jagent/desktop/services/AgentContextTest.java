package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final String AGENT_NAME = "Codex";
    private static final String PROJECT_NAME = "Demo";
    private static final String SESSION_NAME = "Fix login";
    private static final String FIX_PROMPT = "Fix it";
    private static final String CONTEXT_PATH = ".branchloom/context.md";

    @Test
    void writesConfiguredContextIntoTheWorktree(
            @org.junit.jupiter.api.io.TempDir final Path worktree) throws IOException {
        final Project project =
                new Project(
                        PROJECT_NAME,
                        worktree.toString(),
                        null,
                        "github.example",
                        "dev",
                        null,
                        null,
                        List.of("./gradlew test"),
                        List.of(),
                        CONTEXT_PATH,
                        "Use the repository conventions.");
        final Session session =
                new Session(null, SESSION_NAME, AGENT_NAME, FIX_PROMPT, worktree.toString());

        AgentContext.write(project, session);

        final Path context = worktree.resolve(CONTEXT_PATH);
        final String content = Files.readString(context);
        assertTrue(content.contains("Demo"), "context should include the project name");
        assertFalse(
                content.contains("./gradlew test"), "context should not include startup commands");
        assertTrue(content.contains("github.example"), "context should include the GitHub host");
        assertTrue(content.contains("dev"), "context should include the GitHub user");
        assertTrue(
                content.contains("Use the repository conventions."),
                "context should include custom context text");
        assertTrue(
                content.contains(
                        "Read this file at the start of each session before making changes."),
                "context should instruct agents to read existing notes");
        assertTrue(
                content.contains("Keep ongoing notes in this file as you work"),
                "context should instruct agents to keep shared notes");
        assertFalse(content.contains("Branchloom"), "context should not include product branding");
        assertFalse(content.contains("Codex"), "context should not include the agent name");
        assertFalse(content.contains("Fix login"), "context should not include the session name");
    }

    @Test
    void doesNotWriteWhenContextPathIsBlank(@org.junit.jupiter.api.io.TempDir final Path worktree)
            throws IOException {
        final Project project = new Project(PROJECT_NAME, worktree.toString(), null);
        final Session session =
                new Session(null, SESSION_NAME, AGENT_NAME, FIX_PROMPT, worktree.toString());

        AgentContext.write(project, session);

        assertFalse(
                Files.exists(worktree.resolve(CONTEXT_PATH)),
                "blank context paths should not create a file");
    }

    @Test
    void writesToAnExistingFileWhenItConflictsWithAConfiguredParent(
            @org.junit.jupiter.api.io.TempDir final Path worktree) throws IOException {
        final Project project =
                new Project(
                        PROJECT_NAME,
                        worktree.toString(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        ".cursor-notes/agent-start.md",
                        null);
        final Session session =
                new Session(null, SESSION_NAME, AGENT_NAME, FIX_PROMPT, worktree.toString());
        final Path context = worktree.resolve(".cursor-notes");
        Files.writeString(context, "old agent start content");

        AgentContext.write(project, session);

        final String content = Files.readString(context);
        assertTrue(content.contains("# Agent context"), "context should be written");
        assertFalse(
                content.contains("old agent start content"),
                "old conflicting file content should be replaced");
        assertFalse(
                Files.exists(worktree.resolve(".cursor-notes/agent-start.md")),
                "conflicting file should not become a directory");
    }

    @Test
    void readReturnsGeneratedContentWhenNoContextFileExists(
            @org.junit.jupiter.api.io.TempDir final Path worktree) throws IOException {
        final Project project =
                new Project(
                        PROJECT_NAME,
                        worktree.toString(),
                        null,
                        "github.example",
                        "dev",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        CONTEXT_PATH,
                        "Use the repository conventions.");
        final Session session =
                new Session(null, SESSION_NAME, AGENT_NAME, FIX_PROMPT, worktree.toString());

        final String content = AgentContext.read(project, session);

        assertTrue(content.contains("# Agent context"), "context should include heading");
        assertFalse(
                Files.exists(worktree.resolve(CONTEXT_PATH)), "read should not create the file");
    }

    @Test
    void saveWritesUpdatedContent(@org.junit.jupiter.api.io.TempDir final Path worktree)
            throws IOException {
        final Project project =
                new Project(
                        PROJECT_NAME,
                        worktree.toString(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        CONTEXT_PATH,
                        null);
        final Session session =
                new Session(null, SESSION_NAME, AGENT_NAME, FIX_PROMPT, worktree.toString());

        AgentContext.save(project, session, "session notes");

        assertEquals(
                "session notes",
                Files.readString(worktree.resolve(CONTEXT_PATH)),
                "save should persist updated content");
    }

    @Test
    void readReturnsBlankWhenContextPathIsBlank(
            @org.junit.jupiter.api.io.TempDir final Path worktree) throws IOException {
        final Project project = new Project(PROJECT_NAME, worktree.toString(), null);
        final Session session =
                new Session(null, SESSION_NAME, AGENT_NAME, FIX_PROMPT, worktree.toString());

        assertEquals("", AgentContext.read(project, session), "blank paths should read as blank");
    }

    @Test
    void globalContextPathIsUsedWhenProjectPathIsBlank(
            @org.junit.jupiter.api.io.TempDir final Path worktree) throws IOException {
        final Project project = new Project(PROJECT_NAME, worktree.toString(), null);
        final Session session =
                new Session(null, SESSION_NAME, AGENT_NAME, FIX_PROMPT, worktree.toString());

        AgentContext.write(project, session, CONTEXT_PATH);

        assertTrue(
                Files.exists(worktree.resolve(CONTEXT_PATH)),
                "global context path should be used when project path is blank");
    }

    @Test
    void projectContextPathOverridesGlobalContextPath(
            @org.junit.jupiter.api.io.TempDir final Path worktree) throws IOException {
        final Project project =
                new Project(
                        PROJECT_NAME,
                        worktree.toString(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        ".project-context.md",
                        null);
        final Session session =
                new Session(null, SESSION_NAME, AGENT_NAME, FIX_PROMPT, worktree.toString());

        AgentContext.write(project, session, CONTEXT_PATH);

        assertTrue(
                Files.exists(worktree.resolve(".project-context.md")),
                "project-specific context path should take priority");
        assertFalse(
                Files.exists(worktree.resolve(CONTEXT_PATH)),
                "global context path should not be used when project path is set");
    }
}
