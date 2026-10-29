package com.jagent.desktop.ui.components.tree.items;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.board.BoardDefinition;
import com.jagent.desktop.services.ViewController;
import javax.swing.JPopupMenu;

public class BoardTreeItem implements TreeItem {
    private final BoardDefinition board;

    private final ActionContext actionContext;

    public BoardTreeItem(final ActionContext actionContext, final BoardDefinition board) {
        this.actionContext = actionContext;
        this.board = board;
    }

    @Override
    public String getId() {
        return board.name();
    }

    @Override
    public String getTooltip() {
        return board.name();
    }

    @Override
    public CellRenderContents getCellRenderContents() {
        return new CellRenderContents(board.name(), null);
    }

    @Override
    public JPopupMenu getMenu() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMenu'");
    }

    @Override
    public void select() {
        actionContext
                .viewController()
                .updateView(ViewId.BOARD, ViewController.ViewState.board(board.name()));
    }
}
