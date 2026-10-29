package com.jagent.desktop.ui.components.tree.items;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class GroupTreeItem implements TreeItem {

    private final ActionContext actionContext;
    private final String groupName;

    public GroupTreeItem(final ActionContext actionContext, final String groupName) {
        this.actionContext = actionContext;
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
                event -> new CreateProjectAction(actionContext, groupName).execute());
        menu.add(addLocal);
        final JMenuItem cloneRemote = new JMenuItem("Clone remote project to " + groupName);
        cloneRemote.addActionListener(
                event -> new ImportProjectAction(actionContext, groupName).execute());
        menu.add(cloneRemote);
        return menu;
    }

    @Override
    public void select() {}
}
