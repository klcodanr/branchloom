package com.jagent.desktop.ui.components;

import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.KeyStroke;

/** Search input and match navigation controls for a file viewer. */
public final class FileSearchControls extends javax.swing.JPanel {
    private static final String FIND_IN_FILE = "Find in file";
    private final Supplier<String> content;
    private final BiConsumer<Integer, Integer> select;
    private final Runnable showSource;
    private final Runnable focusSource;
    private final SearchInput searchInput =
            new SearchInput(new SearchInput.Text("file-search", FIND_IN_FILE, FIND_IN_FILE));
    private final JButton searchButton = UiFactory.iconButton(UiIcons.search());
    private final JLabel searchCount = UiFactory.label("", Theme.FontSize.XS);
    private final JButton previousMatch = UiFactory.iconButton(UiIcons.chevronUp());
    private final JButton nextMatch = UiFactory.iconButton(UiIcons.chevronDown());
    private List<Integer> matches = List.of();
    private int currentMatch = -1;
    private long searchGeneration;

    public FileSearchControls(
            final Supplier<String> content,
            final BiConsumer<Integer, Integer> select,
            final Runnable showSource,
            final Runnable focusSource) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        this.content = content;
        this.select = select;
        this.showSource = showSource;
        this.focusSource = focusSource;
        setOpaque(false);
        configureControls();
        configureSearch();
    }

    public void refresh() {
        final String query = searchInput.getText();
        if (!query.isBlank()) {
            search(query);
        }
    }

    private void configureControls() {
        searchButton.setName("file-search-button");
        searchButton.setToolTipText(FIND_IN_FILE);
        searchButton.getAccessibleContext().setAccessibleName(FIND_IN_FILE);
        searchButton.addActionListener(event -> openSearch());
        searchCount.setName("file-search-count");
        searchCount.setHorizontalAlignment(JLabel.CENTER);
        searchCount.setToolTipText("Search matches");
        searchCount.getAccessibleContext().setAccessibleName("Search matches");
        searchCount.setVisible(false);
        add(searchButton);
        add(searchInput);
        add(searchCount);
        configureNavigation(previousMatch, "file-search-previous", "Previous match", -1);
        configureNavigation(nextMatch, "file-search-next", "Next match", 1);
        add(previousMatch);
        add(nextMatch);
    }

    private void configureNavigation(
            final JButton button, final String name, final String tooltip, final int direction) {
        button.setName(name);
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.setEnabled(false);
        button.setVisible(false);
        button.addActionListener(event -> selectMatch(direction));
    }

    private void configureSearch() {
        searchInput.onChange(this::search);
        searchInput.onSubmit(() -> selectMatch(1));
        searchInput.registerKeyboardAction(
                event -> selectMatch(-1),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                WHEN_FOCUSED);
        searchInput.onCancel(this::closeSearch);
        registerKeyboardAction(
                event -> openSearch(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F, menuShortcutMask()),
                WHEN_IN_FOCUSED_WINDOW);
    }

    private static int menuShortcutMask() {
        return GraphicsEnvironment.isHeadless()
                ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    private void openSearch() {
        showSource.run();
        searchButton.setVisible(false);
        searchCount.setText("(0/0)");
        searchCount.setVisible(true);
        searchInput.setVisible(true);
        previousMatch.setVisible(true);
        nextMatch.setVisible(true);
        searchInput.requestFocusInWindow();
        searchInput.selectAll();
    }

    private void closeSearch() {
        searchInput.setText("");
        searchInput.setVisible(false);
        searchCount.setVisible(false);
        searchButton.setVisible(true);
        previousMatch.setVisible(false);
        nextMatch.setVisible(false);
        focusSource.run();
        clearSearch();
    }

    private void search(final String query) {
        final String contentSnapshot = content.get();
        final long generation = ++searchGeneration;
        if (query.isBlank() || contentSnapshot == null) {
            clearSearch();
            return;
        }
        CompletableFuture.supplyAsync(() -> findMatches(contentSnapshot, query))
                .thenAcceptAsync(
                        result -> {
                            if (generation != searchGeneration) {
                                return;
                            }
                            matches = result;
                            currentMatch = result.isEmpty() ? -1 : 0;
                            updateSearchStatus();
                            if (!result.isEmpty()) {
                                selectCurrentMatch();
                            }
                        },
                        javax.swing.SwingUtilities::invokeLater);
    }

    private void selectMatch(final int direction) {
        if (matches.isEmpty()) {
            return;
        }
        currentMatch = (currentMatch + direction + matches.size()) % matches.size();
        selectCurrentMatch();
        updateSearchStatus();
    }

    private void selectCurrentMatch() {
        final String query = searchInput.getText();
        final int start = matches.get(currentMatch);
        select.accept(start, start + query.length());
        searchInput.requestFocusInWindow();
    }

    private void updateSearchStatus() {
        if (matches.isEmpty()) {
            searchCount.setText("(0/0)");
            previousMatch.setEnabled(false);
            nextMatch.setEnabled(false);
        } else {
            searchCount.setText("(" + (currentMatch + 1) + "/" + matches.size() + ")");
            previousMatch.setEnabled(true);
            nextMatch.setEnabled(true);
        }
    }

    private void clearSearch() {
        searchGeneration++;
        matches = List.of();
        currentMatch = -1;
        if (searchInput.isVisible()) {
            searchCount.setText("(0/0)");
        }
        previousMatch.setEnabled(false);
        nextMatch.setEnabled(false);
        select.accept(0, 0);
    }

    protected static List<Integer> findMatches(final String content, final String query) {
        final List<Integer> result = new ArrayList<>();
        int index = 0;
        while (index <= content.length() - query.length()) {
            if (content.regionMatches(true, index, query, 0, query.length())) {
                result.add(index);
                index += query.length();
            } else {
                index++;
            }
        }
        return result;
    }
}
