package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.ui.Defaults;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateTest {
    @Test
    void worktreeUsesProjectOverrideOrApplicationDefault() {
        final AppSettings settings = Defaults.appSettings();
        final Project project = project("custom/{sessionSlug}");

        assertEquals(
                "custom/{sessionSlug}",
                Template.worktree(project, settings),
                "project worktree template should take precedence");
        assertEquals(
                settings.worktreeTemplate(),
                Template.worktree(project("  "), settings),
                "blank project templates should use the application default");
    }

    @Test
    void expandsNamesPathsAndNullWorktrees() {
        final Project project = new Project("Demo Project", "/workspace/demo project", null);
        final Session session = new Session(null, "Fix Login", null, null, null);
        final String template =
                "{projectName}|{projectPath}|{sessionName}|{sessionSlug}|{worktreePath}";

        assertEquals(
                "Demo Project|/workspace/demo project|fix-login|fix-login|",
                Template.expand(template, project, session, false),
                "unescaped expansion should preserve project names and paths");
        assertEquals(
                "demo-project|' /workspace/demo project'|Fix Login|fix-login|''",
                Template.expand(
                        "{projectName}|{projectPath}|{sessionName}|{sessionSlug}|{worktreePath}",
                        new Project("Demo Project", " /workspace/demo project", null),
                        session,
                        true),
                "escaped expansion should quote paths and slug project names");
    }

    @Test
    void resolvesRelativePathsAgainstProjectAndNormalizesAbsolutePaths(
            @org.junit.jupiter.api.io.TempDir final Path projectDirectory) {
        final Project project = new Project("Demo", projectDirectory.toString(), null);

        assertEquals(
                projectDirectory.resolve("build/output").normalize().toString(),
                Template.resolvePath("./build/../build/output", project),
                "relative paths should resolve from the project directory");
        final Path absolutePath = projectDirectory.resolveSibling("absolute").resolve("./output");
        assertEquals(
                absolutePath.normalize().toString(),
                Template.resolvePath(absolutePath.toString(), project),
                "absolute paths should not be relative to the project");
    }

    @Test
    void escapesApostrophesAndLeavesUnknownPlaceholdersUntouched() {
        final Project project =
                new Project(
                        "O'Reilly Project",
                        "/workspace/O'Reilly Project",
                        "default",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of());
        final Session session = new Session(null, "Fix bug", null, null, "/tmp/work tree");

        assertEquals(
                "o-reilly-project|'/workspace/O'\\''Reilly Project'|'/tmp/work tree'|{unknown}",
                Template.expand(
                        "{projectName}|{projectPath}|{worktreePath}|{unknown}",
                        project,
                        session,
                        true),
                "shell paths should escape embedded apostrophes");
    }

    private static Project project(final String worktreeTemplate) {
        return new Project(
                "Demo",
                "/workspace/demo",
                "default",
                null,
                null,
                worktreeTemplate,
                null,
                null,
                null);
    }
}
