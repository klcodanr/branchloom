package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

public final class NewProjectsHeader extends JPanel {

    public NewProjectsHeader(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        super(new BorderLayout());

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        final JButton add = new BaseButton("Project", UiIcons.plus());
        add.setFont(Theme.font(Theme.FontSize.XS));
        add.getAccessibleContext().setAccessibleName("Add project");
        add.setToolTipText("Add or clone a project");

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

        add.addActionListener(event -> UiPopupMenus.show(menu, add, 0, add.getHeight()));
        buttons.add(add);
        super.add(buttons, BorderLayout.WEST);
    }
}
