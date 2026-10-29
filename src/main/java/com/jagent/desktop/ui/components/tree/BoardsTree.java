package com.jagent.desktop.ui.components.tree;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.board.BoardDefinition;
import com.jagent.desktop.ui.components.tree.items.BoardTreeItem;
import com.jagent.desktop.ui.components.tree.items.BoardsTreeItem;
import java.util.Comparator;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;

public final class BoardsTree {

    private final ActionContext actionContext;
    private final DefaultMutableTreeNode boardsNode;

    public BoardsTree(final ActionContext actionContext) {
        this.actionContext = actionContext;
        this.boardsNode = new DefaultMutableTreeNode(new BoardsTreeItem());
        rebuild();
    }

    public DefaultMutableTreeNode getNode() {
        return boardsNode;
    }

    public void rebuild() {
        boardsNode.removeAllChildren();
        sortedBoards()
                .forEach(
                        board ->
                                boardsNode.add(
                                        new DefaultMutableTreeNode(
                                                new BoardTreeItem(actionContext, board))));
    }

    private List<BoardDefinition> sortedBoards() {
        return actionContext.appState().appSettings().boards().stream()
                .sorted(Comparator.comparing(b -> b.name()))
                .toList();
    }
}
