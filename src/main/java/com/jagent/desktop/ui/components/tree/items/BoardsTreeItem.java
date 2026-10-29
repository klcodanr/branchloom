package com.jagent.desktop.ui.components.tree.items;

import javax.swing.JPopupMenu;

public class BoardsTreeItem implements TreeItem {
    @Override
    public String getId() {
        return "boards";
    }

    @Override
    public String getTooltip() {
        return "Boards";
    }

    @Override
    public CellRenderContents getCellRenderContents() {
        return new CellRenderContents("Boards", null);
    }

    @Override
    public JPopupMenu getMenu() {
        return null;
    }

    @Override
    public void select() {
        // No action for selecting the Boards tree item
    }
}
