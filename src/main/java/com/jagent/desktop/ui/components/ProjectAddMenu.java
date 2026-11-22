package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import com.jagent.desktop.ui.actions.NewProjectAction;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.jetbrains.annotations.Nullable;

/** Project creation and import choices for the sidebar and tree context menus. */
public final class ProjectAddMenu extends JPopupMenu {
    public ProjectAddMenu(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            @Nullable final String targetGroup) {
        super();
        for (final Action action :
                List.of(
                        new NewProjectAction(viewController, appState, dialogService, targetGroup),
                        new CreateProjectAction(
                                viewController, appState, dialogService, targetGroup),
                        new ImportProjectAction(
                                viewController, appState, dialogService, targetGroup))) {
            final var item = new JMenuItem(action.label(), UiIcons.forAction(action));
            item.setEnabled(action.enabled());
            item.addActionListener(event -> action.execute());
            add(item);
        }
    }
}
