package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.RefreshableView;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TabBody;
import java.awt.BorderLayout;
import java.util.concurrent.CompletableFuture;
import javax.swing.JPanel;

/** Pull requests authored by the current user across all projects. */
public final class MyPullRequestsView extends JPanel implements RefreshableView {
    private final PullRequestsBoard board;

    public MyPullRequestsView(final ActionContext actionContext) {
        super(new BorderLayout());
        board = new PullRequestsBoard(actionContext);
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
