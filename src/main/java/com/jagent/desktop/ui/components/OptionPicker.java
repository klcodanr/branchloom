package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.dialog.DialogService.ReturnType;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** Reusable searchable command and navigation chooser. */
public final class OptionPicker {
    private static final String OPEN = "Open";
    private final DialogService dialogService;

    public OptionPicker(final DialogService dialogService) {
        this.dialogService = dialogService;
    }

    public void open(final String title, final List<Choice> choices) {
        final SearchInput search =
                new SearchInput(
                        new SearchInput.Text(
                                "find-search",
                                "Find project, session, or terminal",
                                "Find project, session, or terminal"));
        final DefaultListModel<Choice> model = new DefaultListModel<>();
        choices.forEach(model::addElement);
        final JList<Choice> results = new JList<>(model);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setSelectedIndex(0);
        results.setVisibleRowCount(Math.min(10, Math.max(4, choices.size())));
        final JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(search, BorderLayout.NORTH);
        content.add(new JScrollPane(results), BorderLayout.CENTER);
        search.setVisible(true);

        final Runnable accept =
                () -> {
                    if (results.getSelectedValue() != null) {
                        content.putClientProperty("dialog.completed", Boolean.TRUE);
                    }
                };
        search.onChange(
                value -> {
                    final String query = value.trim().toLowerCase(Locale.ROOT);
                    model.clear();
                    filteredChoices(choices, query).forEach(model::addElement);
                    if (!model.isEmpty()) {
                        results.setSelectedIndex(0);
                    }
                });
        search.onSubmit(accept);
        search.onCancel(() -> content.putClientProperty("dialog.cancelled", Boolean.TRUE));
        results.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent event) {
                        if (event.getClickCount() == 2) {
                            accept.run();
                        }
                    }
                });
        final ReturnType result = dialogService.show(title, content, OPEN);
        if (result == DialogService.ReturnType.OK && results.getSelectedValue() != null) {
            results.getSelectedValue().action().run();
        }
    }

    private static Stream<Choice> filteredChoices(final List<Choice> choices, final String query) {
        return choices.stream()
                .filter(
                        choice ->
                                query.isBlank()
                                        || choice.label().toLowerCase(Locale.ROOT).contains(query));
    }

    public record Choice(String label, Runnable action) {
        @Override
        public String toString() {
            return label;
        }
    }
}
