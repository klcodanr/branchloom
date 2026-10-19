package com.jagent.desktop.ui.components;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JPanel;

/** A fluent twelve-column grid that automatically advances to the next row. */
public class FlexGrid extends JPanel {
    private static final int COLUMN_COUNT = 12;

    private int column;
    private int row;
    private final int gap;

    public FlexGrid() {
        this(UiConstants.SPACING_SM);
    }

    public FlexGrid(final int gap) {
        super(new GridBagLayout());
        this.gap = gap;
    }

    @Override
    public Dimension getMinimumSize() {
        final Dimension preferredSize = getPreferredSize();
        return new Dimension(0, preferredSize.height);
    }

    @Override
    public final FlexGrid add(final Component component, final int width) {
        if (width < 1 || width > COLUMN_COUNT) {
            throw new IllegalArgumentException("Width must be between 1 and 12");
        }
        if (column + width > COLUMN_COUNT) {
            nextRow();
        }
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.gridwidth = width;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = width;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, gap, gap);
        final Dimension minimumSize = component.getMinimumSize();
        component.setMinimumSize(new Dimension(0, minimumSize.height));
        super.add(component, constraints);
        column += width;
        if (column == COLUMN_COUNT) {
            nextRow();
        }
        return this;
    }

    public final FlexGrid row() {
        nextRow();
        return this;
    }

    private void nextRow() {
        column = 0;
        row++;
    }
}
