package com.jagent.desktop.ui.components;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Two-column form panel for label and component pairs. */
public final class FormPanel extends JPanel {
    public FormPanel(final Object... items) {
        super(new GridBagLayout());
        setBorder(UiBorders.section());

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets =
                new Insets(
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < items.length; i += 2) {
            constraints.gridy = i / 2;
            constraints.gridx = 0;
            constraints.weightx = 0;
            add(new JLabel(items[i].toString()), constraints);
            constraints.gridx = 1;
            constraints.weightx = 1;
            add((Component) items[i + 1], constraints);
        }
    }
}
