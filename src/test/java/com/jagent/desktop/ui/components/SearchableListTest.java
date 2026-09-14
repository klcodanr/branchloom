package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class SearchableListTest {
    @Test
    void filtersChoicesAndPreservesSelectionsOutsideTheFilter() {
        final SearchableList<String> choices =
                GuiActionRunner.execute(
                        () ->
                                new SearchableList<>(
                                        List.of("main", "feature/login", "bugfix/parser"),
                                        "branches",
                                        "Search branches"));
        final JTextField search = (JTextField) choices.getComponent(0);
        final JList<?> list =
                (JList<?>) ((JScrollPane) choices.getComponent(1)).getViewport().getView();

        GuiActionRunner.execute(() -> list.setSelectedIndex(0));
        GuiActionRunner.execute(() -> search.setText("parser"));
        GuiActionRunner.execute(() -> {});

        assertEquals(
                List.of("main"), choices.selectedValues(), "selection should survive filtering");
        assertEquals(1, list.getModel().getSize(), "only matching choices should remain");
        assertEquals(
                "bugfix/parser",
                list.getModel().getElementAt(0),
                "the matching choice should remain");
    }

    @Test
    void clearingFilterRestoresAllChoices() {
        final SearchableList<String> choices =
                GuiActionRunner.execute(
                        () ->
                                new SearchableList<>(
                                        List.of("main", "feature/login"),
                                        "branches",
                                        "Search branches"));
        final JTextField search = (JTextField) choices.getComponent(0);

        GuiActionRunner.execute(
                () -> {
                    search.setText("feature");
                    search.setText("");
                });
        GuiActionRunner.execute(() -> {});

        final JList<?> list =
                (JList<?>) ((JScrollPane) choices.getComponent(1)).getViewport().getView();
        assertEquals(
                2, list.getModel().getSize(), "clearing the filter should restore all choices");
    }
}
