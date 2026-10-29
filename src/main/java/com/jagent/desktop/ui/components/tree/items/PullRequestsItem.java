package com.jagent.desktop.ui.components.tree.items;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.services.ViewController;
import javax.swing.JPopupMenu;

public class PullRequestsItem implements TreeItem {
    private final ViewController viewController;

    public PullRequestsItem(final ViewController viewController) {
        this.viewController = viewController;
    }

    @Override
    public String getId() {
        return "pull_requests";
    }

    @Override
    public String getTooltip() {
        return "Pull Requests";
    }

    @Override
    public CellRenderContents getCellRenderContents() {
        return new CellRenderContents("Pull Requests", null);
    }

    @Override
    public JPopupMenu getMenu() {
        return null;
    }

    @Override
    public void select() {
        viewController.updateView(ViewId.PULL_REQUESTS, ViewController.ViewState.reset());
    }
}
