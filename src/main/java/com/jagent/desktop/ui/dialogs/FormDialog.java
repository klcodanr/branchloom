package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.BaseButton;
import com.jagent.desktop.ui.components.UiConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/** Common modal shell for dialogs that collect input and have one primary action. */
abstract class FormDialog extends JPanel {
    protected DialogService dialogService;
    protected JButton cancelButton;
    protected JButton primaryButton;

    protected final void initialize(
            final JPanel content,
            final String primaryLabel,
            final String cancelName,
            final String primaryName,
            final Runnable submit,
            final DialogService dialogService) {
        this.dialogService = dialogService;
        cancelButton = new BaseButton("Cancel");
        primaryButton = new BaseButton(primaryLabel);
        cancelButton.setName(cancelName);
        primaryButton.setName(primaryName);
        cancelButton.addActionListener(
                event -> putClientProperty("dialog.cancelled", Boolean.TRUE));
        primaryButton.addActionListener(event -> submit.run());

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        buttons.add(primaryButton);
        setLayout(new BorderLayout(UiConstants.COMPONENT_GAP, UiConstants.COMPONENT_GAP));
        add(content, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        registerKeyboardAction(
                event -> cancelButton.doClick(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                WHEN_IN_FOCUSED_WINDOW);
    }
}
