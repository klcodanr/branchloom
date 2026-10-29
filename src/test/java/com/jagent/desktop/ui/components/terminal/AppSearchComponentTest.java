package com.jagent.desktop.ui.components.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.ui.components.SearchInput;
import com.jediterm.terminal.SubstringFinder.FindResult;
import com.jediterm.terminal.ui.JediTermSearchComponentListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class AppSearchComponentTest {
    @Test
    void exposesSearchComponentAndNotifiesListenersOfSearchChanges() {
        final var component = GuiActionRunner.execute(AppSearchComponent::new);
        final var search = require(component, SearchInput.class);
        final var ignoreCase = require(component, JCheckBox.class);
        final var query = new AtomicReference<String>();
        final var caseSensitive = new AtomicBoolean(true);
        component.addListener(
                new JediTermSearchComponentListener() {
                    @Override
                    public void searchSettingsChanged(final String text, final boolean ignore) {
                        query.set(text);
                        caseSensitive.set(!ignore);
                    }

                    @Override
                    public void hideSearchComponent() {}

                    @Override
                    public void selectNextFindResult() {}

                    @Override
                    public void selectPrevFindResult() {}
                });

        GuiActionRunner.execute(() -> search.setText("Build"));
        assertEquals("Build", query.get(), "typing should notify listeners of the query");
        assertFalse(caseSensitive.get(), "the default search should ignore case");

        GuiActionRunner.execute((GuiActionRunnable) ignoreCase::doClick);
        assertTrue(caseSensitive.get(), "clearing ignore-case should notify listeners");
        assertSame(component, component.getComponent(), "the component should expose itself");
    }

    @Test
    void updatesResultLabelForEmptyAndNullResults() {
        final var component = GuiActionRunner.execute(AppSearchComponent::new);
        final var result = require(component, JLabel.class);

        GuiActionRunner.execute(() -> component.onResultUpdated(new FindResult()));
        assertEquals("", result.getText(), "an empty result should clear the result label");
        GuiActionRunner.execute(() -> component.onResultUpdated(null));
        assertEquals("", result.getText(), "a null result should clear the result label");
    }

    @Test
    void nextAndPreviousButtonsNotifyListeners() {
        final var component = GuiActionRunner.execute(AppSearchComponent::new);
        final var next = new AtomicBoolean();
        final var previous = new AtomicBoolean();
        component.addListener(
                new JediTermSearchComponentListener() {
                    @Override
                    public void searchSettingsChanged(final String text, final boolean ignore) {}

                    @Override
                    public void hideSearchComponent() {}

                    @Override
                    public void selectNextFindResult() {
                        next.set(true);
                    }

                    @Override
                    public void selectPrevFindResult() {
                        previous.set(true);
                    }
                });

        final var buttons = component.getComponents();
        GuiActionRunner.execute((GuiActionRunnable) ((JButton) buttons[3])::doClick);
        GuiActionRunner.execute((GuiActionRunnable) ((JButton) buttons[4])::doClick);

        assertTrue(next.get(), "the next button should select the next result");
        assertTrue(previous.get(), "the previous button should select the previous result");
    }

    private static <T> T require(final java.awt.Container container, final Class<T> type) {
        final var found = find(container, type);
        if (found == null) {
            throw new AssertionError("Missing component: " + type.getName());
        }
        return found;
    }

    private static <T> T find(final java.awt.Container container, final Class<T> type) {
        for (final var child : container.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof java.awt.Container nested) {
                final var found = find(nested, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
