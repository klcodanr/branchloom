package com.jagent.desktop.api;

import javax.swing.JComponent;

public interface View {

    ViewId id();

    String title();

    JComponent render();

    default void refresh() {}

    default void detach() {}

    default void dispose() {}
}
