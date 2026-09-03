package com.jagent.desktop.ui.components;

import com.jagent.desktop.ui.utils.DocumentChangeListener;
import java.util.function.Consumer;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

public final class SearchInput extends JTextField {
    public record Text(String name, String tooltip, String accessibleName) {}

    public SearchInput(final Text text) {
        super(15);
        setName(text.name());
        setToolTipText(text.tooltip());
        getAccessibleContext().setAccessibleName(text.accessibleName());
        putClientProperty("JTextField.leadingIcon", UiIcons.search());
        setVisible(false);
    }

    public void onChange(final Consumer<String> callback) {
        getDocument()
                .addDocumentListener(new DocumentChangeListener(() -> callback.accept(getText())));
    }

    public void onSubmit(final Runnable callback) {
        addActionListener(event -> callback.run());
    }

    public void onCancel(final Runnable callback) {
        registerKeyboardAction(
                event -> callback.run(), KeyStroke.getKeyStroke("ESCAPE"), WHEN_FOCUSED);
    }

    public void activate(final char character) {
        if (Character.isISOControl(character)) {
            return;
        }
        setVisible(true);
        setText(String.valueOf(character));
        requestFocusInWindow();
    }

    @Override
    public void setVisible(final boolean visible) {
        super.setVisible(visible);
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
        }
    }
}
