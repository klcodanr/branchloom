package com.jagent.desktop.ui.components;

import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

/** Workspace tab container. */
public final class WorkspaceSplitPane extends JSplitPane {
    public WorkspaceSplitPane(final JTabbedPane content) {
        super(HORIZONTAL_SPLIT);
        setLeftComponent(content);
        setDividerSize(0);
        setContinuousLayout(true);
        setBorder(null);
    }
}
