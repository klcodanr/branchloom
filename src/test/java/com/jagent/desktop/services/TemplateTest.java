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
    private static final String CUSTOM_WORKTREE_TEMPLATE = "custom/{sessionSlug}";
    private static final String DEMO_PROJECT_NAME = "Demo Project";

    @Test
    void worktreeUsesProjectOverrideOrApplicationDefault() {
        final AppSettings settings = Defaults.appSettings();
        final Project project = project(CUSTOM_WORKTREE_TEMPLATE);

        assertEquals(
                CUSTOM_WORKTREE_TEMPLATE,
                Template.worktree(project, settings),
                "project worktree template should take precedence");
        assertEquals(
                settings.worktreeTemplate(),
                Template.worktree(project("  "), settings),
                "blank project templates should use the application default");
    }

    @Test
    void expandsNamesPathsAndNullWorktrees() {
        final Project project = new Project(DEMO_PROJECT_NAME, "/workspace/demo project", null);
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
                        new Project(DEMO_PROJECT_NAME, " /workspace/demo project", null),
                        session,
                        true),
                "escaped expansion should quote paths and slug project names");
    }

    @Test
    void expandsProjectOnlyCommandWithoutSession() {
        final Project project = new Project("Demo Project", "/workspace/demo", null);

        assertEquals(
                "code .|Demo Project|",
                Template.expand("code .|{projectName}|{worktreePath}", project, null, false),
                "project menu commands should expand without a selected session");
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

    @Test
    void expansionHandlesDefaultsAndEscaping() {
        final Project project = new Project(DEMO_PROJECT_NAME, "/tmp/demo", null);
        final Session session = new Session(null, "Feature branch", "agent", "prompt", null);
        final AppSettings settings = Defaults.appSettings();

        assertEquals(
                settings.worktreeTemplate(),
                Template.worktree(project, settings),
                "global worktree template should be selected");
        final Project configured =
                new Project(
                        "Demo",
                        "/tmp/demo",
                        null,
                        null,
                        null,
                        CUSTOM_WORKTREE_TEMPLATE,
                        null,
                        List.of(),
                        List.of());
        assertEquals(
                CUSTOM_WORKTREE_TEMPLATE,
                Template.worktree(configured, settings),
                "project template should override global template");
        assertEquals(
                "Demo Project/feature-branch/feature-branch",
                Template.expand(
                        "{projectName}/{sessionName}/{sessionSlug}", project, session, false),
                "unescaped values should preserve display names");
        assertEquals(
                "demo-project/Feature branch",
                Template.expand("{projectName}/{sessionName}", project, session, true),
                "escaped values should use branch-safe values");
        assertEquals(
                "/tmp/demo/child",
                Template.resolvePath("child", project),
                "relative path should resolve against project");
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
