package com.jagent.desktop.ui.components.tree.items;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.ProjectAddMenu;
import javax.swing.JPopupMenu;

public class GroupTreeItem implements TreeItem {

    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private final String groupName;

    public GroupTreeItem(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final String groupName) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
        this.groupName = groupName;
    }

    @Override
    public String getId() {
        return groupName;
    }

    @Override
    public String getTooltip() {
        return groupName;
    }

    @Override
    public CellRenderContents getCellRenderContents() {
        return new CellRenderContents(groupName, null);
    }

    @Override
    public JPopupMenu getMenu() {

        return new ProjectAddMenu(viewController, appState, dialogService, groupName);
    }

    @Override
    public void select() {}
}
