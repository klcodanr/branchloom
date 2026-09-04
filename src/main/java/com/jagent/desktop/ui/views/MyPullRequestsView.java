package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.PullRequestCache;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TabBody;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;

/** Pull requests authored by the current user across all projects. */
public final class MyPullRequestsView extends JPanel implements View {
    private final transient ActionContext actionContext;
    private final transient PullRequestCache pullRequestCache;

    public MyPullRequestsView(final ActionContext actionContext) {
        super(new BorderLayout());
        this.actionContext = actionContext;
        this.pullRequestCache = PullRequestCache.get(actionContext.appState());
        add(
                TabBody.wrap(new PullRequestsBoard(actionContext, this::pullRequests)),
                BorderLayout.CENTER);
    }

    @Override
    public ViewId id() {
        return ViewId.MY_PULL_REQUESTS;
    }

    @Override
    public String title() {
        return "My Pull Requests";
    }

    @Override
    public JPanel render() {
        return this;
    }

    @Override
    public void detach() {}

    private List<PullRequest> pullRequests() {
        return actionContext.appState().projects().keySet().stream()
                .flatMap(projectId -> pullRequestCache.get(projectId).authored().stream())
                .toList();
    }
}
