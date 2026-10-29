package com.jagent.desktop.ui.components.tree.items;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class DefaultTreeItem implements TreeItem {
    private final ActionContext context;

    public DefaultTreeItem(final ActionContext context) {
        this.context = context;
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
        create.addActionListener(event -> new CreateProjectAction(context).execute());
        menu.add(create);
        final JMenuItem importProject = new JMenuItem("Clone remote project");
        importProject.addActionListener(event -> new ImportProjectAction(context).execute());
        menu.add(importProject);
        return menu;
    }

    @Override
    public void select() {}
}
