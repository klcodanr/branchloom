package com.jagent.desktop.ui.components.terminal;

import com.jagent.desktop.ui.components.IconButton;
import com.jagent.desktop.ui.components.SearchInput;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiIcons;
import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.ui.JediTermSearchComponent;
import com.jediterm.terminal.ui.JediTermSearchComponentListener;
import java.awt.FlowLayout;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class AppSearchComponent extends JPanel implements JediTermSearchComponent {
    private final SearchInput textField =
            new SearchInput(
                    new SearchInput.Text(
                            "terminal-search", "Find in terminal", "Find in terminal"));
    private final JCheckBox ignoreCase = new JCheckBox("Ignore Case", true);
    private final JLabel resultLabel = new JLabel();
    private final List<JediTermSearchComponentListener> listeners = new CopyOnWriteArrayList<>();

    public AppSearchComponent() {
        super(new FlowLayout(FlowLayout.LEFT, 4, 4));
        setOpaque(true);
        setBackground(Theme.Colors.background());
        setBorder(Theme.sectionBorder(4, 6, 4, 6));
        textField.setVisible(true);
        textField.setColumns(24);
        ignoreCase.setOpaque(false);
        resultLabel.setFont(Theme.font(Theme.FontSize.XS));
        final IconButton next = new IconButton(UiIcons.chevronDown(), "Next match");
        final IconButton previous = new IconButton(UiIcons.chevronUp(), "Previous match");
        next.addActionListener(
                event -> listeners.forEach(JediTermSearchComponentListener::selectNextFindResult));
        previous.addActionListener(
                event -> listeners.forEach(JediTermSearchComponentListener::selectPrevFindResult));
        add(textField);
        add(ignoreCase);
        add(resultLabel);
        add(next);
        add(previous);
        final DocumentListener searchListener =
                new DocumentListener() {
                    @Override
                    public void insertUpdate(final DocumentEvent event) {
                        searchChanged();
                    }

                    @Override
                    public void removeUpdate(final DocumentEvent event) {
                        searchChanged();
                    }

                    @Override
                    public void changedUpdate(final DocumentEvent event) {
                        searchChanged();
                    }
                };
        textField.getDocument().addDocumentListener(searchListener);
        ignoreCase.addItemListener(event -> searchChanged());
        textField.onCancel(
                () -> listeners.forEach(JediTermSearchComponentListener::hideSearchComponent));
    }

    private void searchChanged() {
        listeners.forEach(
                listener ->
                        listener.searchSettingsChanged(
                                textField.getText(), ignoreCase.isSelected()));
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public void addListener(final JediTermSearchComponentListener listener) {
        listeners.add(listener);
    }

    @Override
    public void addKeyListener(final java.awt.event.KeyListener listener) {
        textField.addKeyListener(listener);
    }

    @Override
    public void requestFocus() {
        textField.requestFocus();
    }

    @Override
    public boolean requestFocusInWindow() {
        return textField.requestFocusInWindow();
    }

    @Override
    public void onResultUpdated(final SubstringFinder.FindResult result) {
        resultLabel.setText(
                result == null || result.getItems().isEmpty()
                        ? ""
                        : result.selectedItem().getIndex() + " of " + result.getItems().size());
    }
}
