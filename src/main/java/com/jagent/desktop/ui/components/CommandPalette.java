package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/** Reusable searchable command and navigation chooser. */
public final class CommandPalette {
    private static final String OPEN = "Open";

    private CommandPalette() {}

    public static void open(final Window owner, final String title, final List<Choice> choices) {
        final JTextField search = new JTextField(35);
        final DefaultListModel<Choice> model = new DefaultListModel<>();
        choices.forEach(model::addElement);
        final JList<Choice> results = new JList<>(model);
        results.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        results.setSelectedIndex(0);
        results.setVisibleRowCount(Math.min(10, Math.max(4, choices.size())));
        final JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(search, BorderLayout.NORTH);
        content.add(new JScrollPane(results), BorderLayout.CENTER);

        final JOptionPane pane =
                new JOptionPane(
                        content,
                        JOptionPane.PLAIN_MESSAGE,
                        JOptionPane.OK_CANCEL_OPTION,
                        null,
                        new Object[] {OPEN, "Cancel"},
                        OPEN);
        final JDialog dialog = pane.createDialog(owner, title);
        final Runnable accept =
                () -> {
                    if (results.getSelectedValue() != null) {
                        dialog.dispose();
                    }
                };
        search.getDocument()
                .addDocumentListener(
                        new javax.swing.event.DocumentListener() {
                            private void filter() {
                                final String query =
                                        search.getText().trim().toLowerCase(java.util.Locale.ROOT);
                                model.clear();
                                choices.stream()
                                        .filter(
                                                choice ->
                                                        query.isBlank()
                                                                || choice.label()
                                                                        .toLowerCase(
                                                                                java.util.Locale
                                                                                        .ROOT)
                                                                        .contains(query))
                                        .forEach(model::addElement);
                                if (!model.isEmpty()) {
                                    results.setSelectedIndex(0);
                                }
                            }

                            @Override
                            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                                filter();
                            }

                            @Override
                            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                                filter();
                            }

                            @Override
                            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                                filter();
                            }
                        });
        search.addActionListener(event -> accept.run());
        results.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent event) {
                        if (event.getClickCount() == 2) {
                            accept.run();
                        }
                    }
                });
        pane.addPropertyChangeListener(
                event -> {
                    if (JOptionPane.VALUE_PROPERTY.equals(event.getPropertyName())
                            && OPEN.equals(pane.getValue())) {
                        accept.run();
                    }
                });
        dialog.setVisible(true);
        if (OPEN.equals(pane.getValue()) && results.getSelectedValue() != null) {
            results.getSelectedValue().action().run();
        }
    }

    public record Choice(String label, Runnable action) {
        @Override
        public String toString() {
            return label;
        }
    }
}
