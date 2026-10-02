package com.jagent.desktop.test;

import com.jagent.desktop.ui.components.Theme;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Applies a deterministic Swing theme before each test class. */
public final class SwingThemeExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(final ExtensionContext context) {
        GuiActionRunner.execute(() -> Theme.apply(Theme.FlatLafTheme.LIGHT));
    }
}
