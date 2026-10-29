package com.jagent.desktop.ui.views;

import com.google.inject.Inject;
import com.jagent.desktop.api.RefreshableView;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.AsyncLoadingStateFactory;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.github.GitHubProvider;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TabBody;
import java.awt.BorderLayout;
import java.util.concurrent.CompletableFuture;
import javax.swing.JPanel;

/** Pull requests authored by the current user across all projects. */
public final class MyPullRequestsView extends JPanel implements RefreshableView {
    private final PullRequestsBoard board;

    @Inject
    public MyPullRequestsView(
            final ViewController viewController,
            final AppState appState,
            final AsyncLoadingStateFactory asyncStateFactory,
            final BackgroundJobs backgroundJobs,
            final GitHubProvider githubProvider,
            final DialogService dialogService) {
        super(new BorderLayout());
        board =
                new PullRequestsBoard(
                        asyncStateFactory,
                        backgroundJobs,
                        viewController,
                        appState,
                        githubProvider,
                        dialogService);
        add(TabBody.wrap(board), BorderLayout.CENTER);
    }

    @Override
    public ViewId id() {
        return ViewId.PULL_REQUESTS;
    }

    @Override
    public String title() {
        return "Pull Requests";
    }

    @Override
    public JPanel render() {
        return this;
    }

    @Override
    public CompletableFuture<?> refresh() {
        return board.refresh();
    }

    @Override
    public void detach() {}
}
