package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.WorkspaceFileReader;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

/** Read-only source and diff viewer for a workspace file. */
public final class FileViewer extends JPanel {
    private static final String SOURCE = "source";
    private static final String DIFF = "diff";
    private static final String FIND_IN_FILE = "Find in file";
    private final Path workspace;
    private final Path file;
    private final JLabel status = UiFactory.label("Loading...", Theme.FontSize.XS);
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final RSyntaxTextArea source = new RSyntaxTextArea();
    private final JTextArea diff = new JTextArea();
    private final SearchInput searchInput =
            new SearchInput(new SearchInput.Text("file-search", FIND_IN_FILE, FIND_IN_FILE));
    private final JButton searchButton = UiFactory.iconButton(UiIcons.search());
    private final JLabel searchCount = UiFactory.label("", Theme.FontSize.XS);
    private final JButton previousMatch = UiFactory.iconButton(UiIcons.chevronUp());
    private final JButton nextMatch = UiFactory.iconButton(UiIcons.chevronDown());
    private volatile String loadedContent;
    private List<Integer> matches = List.of();
    private int currentMatch = -1;
    private long searchGeneration;

    public FileViewer(final Path workspace, final Path file) {
        super(new BorderLayout(0, UiConstants.CONTENT_PADDING));
        this.workspace = workspace.toAbsolutePath().normalize();
        this.file = file.toAbsolutePath().normalize();
        setBorder(UiFactory.sectionBorder());
        add(toolbar(), BorderLayout.NORTH);
        configureSource();
        configureDiff();
        content.add(new JScrollPane(source), SOURCE);
        content.add(new JScrollPane(diff), DIFF);
        add(content, BorderLayout.CENTER);
        configureSearch();
        load();
    }

    private JPanel toolbar() {
        final JPanel toolbar = new JPanel(new BorderLayout(UiConstants.CONTENT_PADDING, 0));
        toolbar.setOpaque(false);
        final JLabel path =
                UiFactory.label(workspace.relativize(file).toString(), Theme.FontSize.SM);
        path.setToolTipText(file.toString());
        toolbar.add(path, BorderLayout.WEST);
        final JPanel controls =
                new JPanel(new FlowLayout(FlowLayout.RIGHT, UiConstants.SPACING_XS, 0));
        controls.setOpaque(false);
        final JPanel viewModes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        viewModes.setOpaque(false);
        final JToggleButton sourceButton = segmentedButton(UiIcons.fileCode(), "File", "first");
        final JToggleButton diffButton = segmentedButton(UiIcons.gitCompare(), "Diff", "last");
        final ButtonGroup group = new ButtonGroup();
        group.add(sourceButton);
        group.add(diffButton);
        sourceButton.setSelected(true);
        sourceButton.addActionListener(event -> cards.show(content, SOURCE));
        diffButton.addActionListener(event -> cards.show(content, DIFF));
        viewModes.add(sourceButton);
        viewModes.add(diffButton);
        controls.add(viewModes);
        controls.add(searchControls());
        controls.add(status);
        toolbar.add(controls, BorderLayout.EAST);
        return toolbar;
    }

    private JPanel searchControls() {
        final JPanel searchControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        searchControls.setOpaque(false);
        searchButton.setToolTipText(FIND_IN_FILE);
        searchButton.getAccessibleContext().setAccessibleName(FIND_IN_FILE);
        searchButton.addActionListener(event -> openSearch());
        searchCount.setHorizontalAlignment(JLabel.CENTER);
        searchCount.setToolTipText("Search matches");
        searchCount.getAccessibleContext().setAccessibleName("Search matches");
        searchControls.add(searchButton);
        searchControls.add(searchInput);
        searchControls.add(searchCount);
        previousMatch.setToolTipText("Previous match");
        previousMatch.getAccessibleContext().setAccessibleName("Previous match");
        previousMatch.setEnabled(false);
        previousMatch.setVisible(false);
        previousMatch.addActionListener(event -> selectMatch(-1));
        nextMatch.setToolTipText("Next match");
        nextMatch.getAccessibleContext().setAccessibleName("Next match");
        nextMatch.setEnabled(false);
        nextMatch.setVisible(false);
        nextMatch.addActionListener(event -> selectMatch(1));
        searchControls.add(previousMatch);
        searchControls.add(nextMatch);
        return searchControls;
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
        cards.show(content, SOURCE);
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
        source.requestFocusInWindow();
        clearSearch();
    }

    private void search(final String query) {
        final String contentSnapshot = loadedContent;
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
        source.select(start, start + query.length());
        source.requestFocusInWindow();
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
        source.select(0, 0);
    }

    private static List<Integer> findMatches(final String content, final String query) {
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

    private static JToggleButton segmentedButton(
            final javax.swing.Icon icon, final String name, final String position) {
        final JToggleButton button = new JToggleButton(icon);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        button.putClientProperty("JButton.buttonType", "segmented");
        button.putClientProperty("JButton.segmentPosition", position);
        return button;
    }

    private void configureSource() {
        source.setEditable(false);
        source.setCodeFoldingEnabled(true);
        source.setHighlightCurrentLine(true);
        source.setLineWrap(false);
        source.setFont(
                new Font(Font.MONOSPACED, Font.PLAIN, Theme.font(Theme.FontSize.SM).getSize()));
        source.setSyntaxEditingStyle(FileSyntax.styleFor(file));
        applyEditorTheme();
    }

    private void configureDiff() {
        diff.setEditable(false);
        diff.setLineWrap(false);
        diff.setFont(Theme.terminalFont(Theme.FontSize.SM));
        diff.setBorder(UiFactory.cardBorder());
        applyEditorTheme();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (source != null && diff != null) {
            applyEditorTheme();
        }
    }

    private void applyEditorTheme() {
        final Color background = color("TextArea.background", "Panel.background", Color.WHITE);
        final Color foreground = color("TextArea.foreground", "Label.foreground", Color.BLACK);
        final Color selectionBackground =
                color(
                        "TextArea.selectionBackground",
                        "TextComponent.selectionBackground",
                        new Color(184, 207, 229));
        final Color selectionForeground =
                color(
                        "TextArea.selectionForeground",
                        "TextComponent.selectionForeground",
                        Color.BLACK);
        final Color caret =
                color("TextArea.caretForeground", "TextComponent.foreground", foreground);
        final Color currentLine =
                color("TextArea.currentLineHighlight", "TextComponent.background", background);

        source.setBackground(background);
        source.setForeground(foreground);
        source.setCaretColor(caret);
        source.setSelectionColor(selectionBackground);
        source.setSelectedTextColor(selectionForeground);
        source.setCurrentLineHighlightColor(currentLine);
        source.setSyntaxScheme(syntaxScheme(foreground));

        diff.setBackground(background);
        diff.setForeground(foreground);
        diff.setCaretColor(caret);
        diff.setSelectionColor(selectionBackground);
        diff.setSelectedTextColor(selectionForeground);
    }

    private static SyntaxScheme syntaxScheme(final Color foreground) {
        final boolean dark = isDark(foreground);
        final Color keyword = dark ? new Color(198, 146, 255) : new Color(128, 48, 145);
        final Color string = dark ? new Color(165, 214, 129) : new Color(46, 125, 50);
        final Color comment = dark ? new Color(139, 148, 158) : new Color(94, 99, 104);
        final Color number = dark ? new Color(121, 192, 255) : new Color(0, 92, 170);
        final Color literal = dark ? new Color(255, 166, 87) : new Color(173, 80, 0);
        final SyntaxScheme scheme = new SyntaxScheme(true);
        for (int i = 0; i < scheme.getStyleCount(); i++) {
            scheme.getStyle(i).foreground = foreground;
        }
        scheme.getStyle(TokenTypes.RESERVED_WORD).foreground = keyword;
        scheme.getStyle(TokenTypes.RESERVED_WORD_2).foreground = keyword;
        scheme.getStyle(TokenTypes.LITERAL_BOOLEAN).foreground = literal;
        scheme.getStyle(TokenTypes.LITERAL_NUMBER_DECIMAL_INT).foreground = number;
        scheme.getStyle(TokenTypes.LITERAL_NUMBER_FLOAT).foreground = number;
        scheme.getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground = string;
        scheme.getStyle(TokenTypes.LITERAL_CHAR).foreground = string;
        scheme.getStyle(TokenTypes.COMMENT_EOL).foreground = comment;
        scheme.getStyle(TokenTypes.COMMENT_MULTILINE).foreground = comment;
        scheme.getStyle(TokenTypes.COMMENT_DOCUMENTATION).foreground = comment;
        return scheme;
    }

    private static boolean isDark(final Color color) {
        final int brightness =
                color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114;
        return brightness < 128_000;
    }

    private static Color color(
            final String key, final String fallbackKey, final Color defaultColor) {
        final Color value = UIManager.getColor(key);
        final Color fallback = UIManager.getColor(fallbackKey);
        return value == null && fallback == null ? defaultColor : value == null ? fallback : value;
    }

    private void load() {
        WorkspaceFileReader.read(workspace, file)
                .thenAcceptAsync(
                        document -> {
                            if (document.binary()) {
                                loadedContent = null;
                                source.setText("Binary file cannot be displayed.");
                                status.setText("Binary");
                            } else {
                                loadedContent = document.content();
                                source.setText(document.content());
                                source.setCaretPosition(0);
                                GitFormatter.renderDiff(diff, document.diff());
                                status.setText(document.diff().isBlank() ? "Unchanged" : "Changed");
                                if (!searchInput.getText().isBlank()) {
                                    search(searchInput.getText());
                                }
                            }
                        },
                        javax.swing.SwingUtilities::invokeLater)
                .exceptionally(
                        failure -> {
                            javax.swing.SwingUtilities.invokeLater(
                                    () -> {
                                        loadedContent = null;
                                        source.setText(
                                                "Could not load file: " + failure.getMessage());
                                        status.setText("Unavailable");
                                    });
                            return null;
                        });
    }
}
