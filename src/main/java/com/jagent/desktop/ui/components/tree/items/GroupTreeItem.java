package com.jagent.desktop.ui.components.tree.items;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import javax.swing.JMenuItem;
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

        final JPopupMenu menu = new JPopupMenu();

        final JMenuItem addLocal = new JMenuItem("Add local project to " + groupName);
        addLocal.addActionListener(
                event ->
                        new CreateProjectAction(viewController, appState, dialogService, groupName)
                                .execute());
        menu.add(addLocal);
        final JMenuItem cloneRemote = new JMenuItem("Clone remote project to " + groupName);
        cloneRemote.addActionListener(
                event ->
                        new ImportProjectAction(viewController, appState, dialogService, groupName)
                                .execute());
        menu.add(cloneRemote);
        return menu;
    }

    @Override
    public void select() {}
}
