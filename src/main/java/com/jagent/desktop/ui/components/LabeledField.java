package com.jagent.desktop.ui.components;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** A consistently styled label and form field row. */
public final class LabeledField extends JPanel {
    public LabeledField(final String title, final JComponent field) {
        super(new GridBagLayout());
        final GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        labelConstraints.insets = new Insets(0, 0, 0, UiConstants.COMPONENT_GAP);
        final JLabel label = new JLabel(title);
        label.setPreferredSize(new Dimension(180, label.getPreferredSize().height));
        add(label, labelConstraints);
        final GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.anchor = GridBagConstraints.NORTHWEST;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1;
        fieldConstraints.gridwidth = GridBagConstraints.REMAINDER;
        fieldConstraints.insets = new Insets(0, 0, 0, 0);
        if (field instanceof JTextArea area) {
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            final JScrollPane areaScroll = new JScrollPane(area);
            fieldConstraints.fill = GridBagConstraints.BOTH;
            add(areaScroll, fieldConstraints);
        } else {
            add(field, fieldConstraints);
        }
    }
}
