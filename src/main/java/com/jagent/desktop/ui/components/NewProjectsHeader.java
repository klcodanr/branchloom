package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
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
        add.setToolTipText("Create, add, or clone a project");

        final JPopupMenu menu = new ProjectAddMenu(viewController, appState, dialogService, null);

        add.addActionListener(event -> UiPopupMenus.show(menu, add, 0, add.getHeight()));
        buttons.add(add);
        super.add(buttons, BorderLayout.WEST);
    }
}
