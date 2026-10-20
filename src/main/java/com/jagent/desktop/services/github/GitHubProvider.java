package com.jagent.desktop.services.github;

import com.jagent.desktop.models.ProjectId;

@FunctionalInterface
public interface GitHubProvider {
    GitHub forProject(ProjectId projectId);
}
