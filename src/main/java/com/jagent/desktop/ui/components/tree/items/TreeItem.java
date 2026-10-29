package com.jagent.desktop.ui.components.tree.items;

import javax.swing.Icon;
import javax.swing.JPopupMenu;
import org.eclipse.jgit.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public interface TreeItem {

    public record CellRenderContents(@NotNull String text, @Nullable Icon icon) {}

    String getId();

    String getTooltip();

    CellRenderContents getCellRenderContents();

    JPopupMenu getMenu();

    void select();
}
