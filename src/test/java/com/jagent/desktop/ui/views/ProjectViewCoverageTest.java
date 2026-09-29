package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectViewCoverageTest {
    @Test
    void rendersProjectWorkspaceAndCanFocusReviewFilter(@TempDir final Path directory)
            throws MalformedURLException {
        final Project persisted = new Project("Persisted", directory.toString(), null);
        final ProjectId projectId = ProjectId.create();
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), persisted),
                        Map.of(),
                        Map.of());
        state.updateCurrentProject(projectId);
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);

        final Project passedToView = new Project("Unmapped", directory.toString(), null);
        final ProjectView view = new ProjectView(context, passedToView);
        view.refresh();
        view.reviewPullRequest(request(projectId, passedToView));

        assertEquals(ViewId.PROJECT, view.id(), "project view should expose project view id");
        assertEquals("Unmapped", view.title(), "project view title should use project name");
        assertNotNull(view.render(), "project view should render");
        assertSame(view, view.render(), "render should return the view itself");
        view.dispose();
    }

    private static PullRequest request(final ProjectId projectId, final Project project)
            throws MalformedURLException {
        return new PullRequest(
                projectId,
                project,
                42,
                PullRequest.State.OPEN,
                "Review me",
                "Description",
                new URL("https://example.test/42"),
                new Date(),
                new Date(),
                new GitHubUser("author", new URL("https://example.test/author")),
                "feature/review",
                "abc1234def5678",
                "main");
    }
}
