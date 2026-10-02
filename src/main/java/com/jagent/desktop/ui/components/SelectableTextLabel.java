package com.jagent.desktop.ui.components;

import javax.swing.border.EmptyBorder;

/** Read-only, selectable text presented with label-like styling. */
public final class SelectableTextLabel extends BaseTextArea {
    public SelectableTextLabel(final String text, final Theme.FontSize size) {
        super(UiText.valueOrDefault(text, ""));
        setFont(Theme.font(size));
        setEditable(false);
        setOpaque(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setRows(1);
        setBorder(new EmptyBorder(0, 0, 0, 0));
    }
}
