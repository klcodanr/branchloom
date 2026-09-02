package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import javax.swing.JTextField;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class SearchableComboBoxTest {
    @Test
    void filtersChoicesAsTheEditorChanges() {
        final SearchableComboBox<String> combo =
                GuiActionRunner.execute(
                        () ->
                                new SearchableComboBox<>(
                                        List.of("main", "feature/login", "bugfix/parser")));
        final JTextField editor = (JTextField) combo.getEditor().getEditorComponent();

        GuiActionRunner.execute(() -> editor.setText("parser"));
        GuiActionRunner.execute(() -> {});

        assertEquals(1, combo.getItemCount(), "only matching choices should remain");
        assertEquals("bugfix/parser", combo.getItemAt(0), "the matching choice should remain");
        assertEquals("parser", editor.getText(), "the search text should remain in the editor");
    }

    @Test
    void restoresAllChoicesWhenSearchIsCleared() {
        final SearchableComboBox<String> combo =
                GuiActionRunner.execute(
                        () -> new SearchableComboBox<>(List.of("main", "feature/login")));
        final JTextField editor = (JTextField) combo.getEditor().getEditorComponent();

        GuiActionRunner.execute(
                () -> {
                    editor.setText("feature");
                    editor.setText("");
                });
        GuiActionRunner.execute(() -> {});

        assertEquals(2, combo.getItemCount(), "clearing the search should restore all choices");
    }
}
