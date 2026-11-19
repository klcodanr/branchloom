package com.jagent.desktop.ui.components.tree.items;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.ProjectAddMenu;
import javax.swing.JPopupMenu;

public class DefaultTreeItem implements TreeItem {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;

    public DefaultTreeItem(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
    }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public String getTooltip() {
        return null;
    }

    @Override
    public CellRenderContents getCellRenderContents() {
        return null;
    }

    @Override
    public JPopupMenu getMenu() {
        return new ProjectAddMenu(viewController, appState, dialogService, null);
    }

    @Override
    public void select() {}
}
