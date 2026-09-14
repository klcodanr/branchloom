package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** A multi-select list with a text filter. */
public final class SearchableList<T> extends JPanel {
    private final List<T> choices;
    private final DefaultListModel<T> model = new DefaultListModel<>();
    private final JList<T> list = new JList<>(model);
    private final Set<T> selected = new LinkedHashSet<>();
    private boolean filtering;

    public SearchableList(
            final Collection<T> choices, final String name, final String placeholder) {
        super(new BorderLayout());
        this.choices = List.copyOf(choices);
        final SearchInput search =
                new SearchInput(new SearchInput.Text(name + "-search", placeholder, placeholder));
        search.setVisible(true);
        search.onChange(this::filter);
        list.setName(name);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.addListSelectionListener(
                event -> {
                    if (!event.getValueIsAdjusting() && !filtering) {
                        selected.removeIf(choice -> model.indexOf(choice) >= 0);
                        selected.addAll(list.getSelectedValuesList());
                    }
                });
        choices.forEach(model::addElement);
        add(search, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    public List<T> selectedValues() {
        return choices.stream().filter(selected::contains).toList();
    }

    public void setVisibleRowCount(final int rows) {
        list.setVisibleRowCount(rows);
    }

    private void filter(final String text) {
        final String query = text.toLowerCase(Locale.ROOT);
        filtering = true;
        try {
            model.removeAllElements();
            choices.stream()
                    .filter(choice -> choice.toString().toLowerCase(Locale.ROOT).contains(query))
                    .forEach(model::addElement);
            for (int index = 0; index < model.size(); index++) {
                if (selected.contains(model.get(index))) {
                    list.addSelectionInterval(index, index);
                }
            }
        } finally {
            filtering = false;
        }
    }
}
