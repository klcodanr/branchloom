package com.jagent.desktop.ui.components.tree;

import com.jagent.desktop.models.board.BoardDefinition;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.ui.components.tree.items.BoardTreeItem;
import com.jagent.desktop.ui.components.tree.items.BoardsTreeItem;
import java.util.Comparator;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;

public final class BoardsTree {

    private final ViewController viewController;
    private final AppState appState;
    private final DefaultMutableTreeNode boardsNode;

    public BoardsTree(final ViewController viewController, final AppState appState) {
        this.viewController = viewController;
        this.appState = appState;
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
                                                new BoardTreeItem(viewController, board))));
    }

    private List<BoardDefinition> sortedBoards() {
        return appState.appSettings().boards().stream()
                .sorted(Comparator.comparing(b -> b.name()))
                .toList();
    }
}
