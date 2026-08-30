package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.ui.components.UiFactory;
import java.awt.Window;
import javax.swing.JDialog;

/** Displays non-blocking progress while a background operation is running. */
public final class ProgressDialog extends JDialog {
    public ProgressDialog(final Window owner, final String title, final String message) {
        super(owner, title, ModalityType.MODELESS);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        add(UiFactory.loading(message));
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }
}
