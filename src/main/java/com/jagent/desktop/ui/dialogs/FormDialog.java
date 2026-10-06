package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.ui.components.BaseButton;
import com.jagent.desktop.ui.components.UiConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/** Common modal shell for dialogs that collect input and have one primary action. */
abstract class FormDialog extends JDialog {
    protected JButton cancelButton;
    protected JButton primaryButton;

    protected FormDialog(final Window owner, final String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
    }

    protected final void initialize(
            final JPanel content,
            final String primaryLabel,
            final String cancelName,
            final String primaryName,
            final Runnable submit,
            final Window owner) {
        cancelButton = new BaseButton("Cancel");
        primaryButton = new BaseButton(primaryLabel);
        cancelButton.setName(cancelName);
        primaryButton.setName(primaryName);
        cancelButton.addActionListener(event -> dispose());
        primaryButton.addActionListener(event -> submit.run());

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        buttons.add(primaryButton);
        setLayout(new BorderLayout(UiConstants.COMPONENT_GAP, UiConstants.COMPONENT_GAP));
        add(content, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane()
                .registerKeyboardAction(
                        event -> dispose(),
                        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                        JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(primaryButton);
        pack();
        setLocationRelativeTo(owner);
    }
}
