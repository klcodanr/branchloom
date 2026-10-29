package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

public final class NewProjectsHeader extends JPanel {

    public NewProjectsHeader(final ActionContext actionContext) {
        super(new BorderLayout());

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        final JButton add = new BaseButton("Project", UiIcons.plus());
        add.setFont(Theme.font(Theme.FontSize.XS));
        add.getAccessibleContext().setAccessibleName("Add project");
        add.setToolTipText("Add or clone a project");

        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem create = new JMenuItem("Add local project");
        create.addActionListener(event -> new CreateProjectAction(actionContext).execute());
        menu.add(create);
        final JMenuItem importProject = new JMenuItem("Clone remote project");
        importProject.addActionListener(event -> new ImportProjectAction(actionContext).execute());
        menu.add(importProject);

        add.addActionListener(event -> UiPopupMenus.show(menu, add, 0, add.getHeight()));
        buttons.add(add);
        super.add(buttons, BorderLayout.WEST);
    }
}
