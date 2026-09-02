package com.jagent.desktop.ui.components;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** An editable combo box that filters its choices as the user types. */
public final class SearchableComboBox<T> extends JComboBox<T> {
    private final List<T> choices;
    private final DefaultComboBoxModel<T> model;
    private boolean filtering;

    public SearchableComboBox(final Collection<T> choices) {
        super();
        this.choices = List.copyOf(choices);
        model = new DefaultComboBoxModel<>();
        setModel(model);
        this.choices.forEach(model::addElement);
        setEditable(true);
        ((JTextField) getEditor().getEditorComponent())
                .getDocument()
                .addDocumentListener(
                        new javax.swing.event.DocumentListener() {
                            @Override
                            public void insertUpdate(final javax.swing.event.DocumentEvent event) {
                                scheduleFiltering();
                            }

                            @Override
                            public void removeUpdate(final javax.swing.event.DocumentEvent event) {
                                scheduleFiltering();
                            }

                            @Override
                            public void changedUpdate(final javax.swing.event.DocumentEvent event) {
                                scheduleFiltering();
                            }
                        });
    }

    private void scheduleFiltering() {
        if (!filtering) {
            SwingUtilities.invokeLater(this::filterChoices);
        }
    }

    private void filterChoices() {
        if (filtering) {
            return;
        }
        final JTextField editor = (JTextField) getEditor().getEditorComponent();
        final String text = editor.getText();
        final String query = text.toLowerCase(Locale.ROOT);
        filtering = true;
        try {
            model.removeAllElements();
            choices.stream()
                    .filter(choice -> choice.toString().toLowerCase(Locale.ROOT).contains(query))
                    .forEach(model::addElement);
            editor.setText(text);
            if (!text.isEmpty() && isShowing()) {
                showPopup();
            }
        } finally {
            filtering = false;
        }
    }
}
