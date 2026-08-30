package com.jagent.desktop.services;

import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.ui.GitUtils;
import java.nio.file.Path;

/** Expands project and session templates for commands and filesystem paths. */
public final class Template {
    private Template() {}

    public static String worktree(final Project project, final AppSettings appSettings) {
        final String projectTemplate = project.worktreeTemplate();
        return projectTemplate == null || projectTemplate.isBlank()
                ? appSettings.worktreeTemplate()
                : projectTemplate;
    }

    public static String expand(
            final String template,
            final Project project,
            final Session session,
            final boolean escapePaths) {
        final String worktreePath = session.worktreePath();
        final String projectName =
                escapePaths ? GitUtils.toBranchSlug(project.name()) : project.name();
        final String sessionName =
                escapePaths ? session.name() : GitUtils.toBranchSlug(session.name());
        final String projectPath = escapePaths ? Git.shellQuote(project.path()) : project.path();
        final String resolvedWorktreePath = worktreePath == null ? "" : worktreePath;
        final String path =
                escapePaths ? Git.shellQuote(resolvedWorktreePath) : resolvedWorktreePath;
        return template.replace("{projectName}", projectName)
                .replace("{projectPath}", projectPath)
                .replace("{sessionName}", sessionName)
                .replace("{sessionSlug}", GitUtils.toBranchSlug(session.name()))
                .replace("{worktreePath}", path);
    }

    public static String resolvePath(final String template, final Project project) {
        final Path path = Path.of(template);
        return (path.isAbsolute() ? path : Path.of(project.path()).resolve(path))
                .normalize()
                .toString();
    }
}
