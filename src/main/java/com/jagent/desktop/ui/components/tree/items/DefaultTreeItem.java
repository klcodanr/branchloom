package com.jagent.desktop.ui.components.tree.items;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import javax.swing.JMenuItem;
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
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem create = new JMenuItem("Add local project");
        create.addActionListener(
                event ->
                        new CreateProjectAction(viewController, appState, dialogService).execute());
        menu.add(create);
        final JMenuItem importProject = new JMenuItem("Clone remote project");
        importProject.addActionListener(
                event ->
                        new ImportProjectAction(viewController, appState, dialogService).execute());
        menu.add(importProject);
        return menu;
    }

    @Override
    public void select() {}
}
