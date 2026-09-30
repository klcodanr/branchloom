package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

/** Read-only source and diff viewer for a workspace file. */
public final class FileViewer extends JPanel {
    private static final String SOURCE = "source";
    private static final String DIFF = "diff";
    private final Path workspace;
    private final Path file;
    private final boolean showDiffInitially;
    private final JLabel status = UiFactory.label("Loading...", Theme.FontSize.XS);
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final RSyntaxTextArea source = new RSyntaxTextArea();
    private final RSyntaxTextArea diff = new RSyntaxTextArea();
    private volatile String loadedContent;
    private final FileSearchControls searchControls =
            new FileSearchControls(
                    () -> loadedContent,
                    (start, end) -> {
                        source.select(start, end);
                        source.requestFocusInWindow();
                    },
                    () -> cards.show(content, SOURCE),
                    source::requestFocusInWindow);

    public FileViewer(final Path workspace, final Path file) {
        this(workspace, file, false);
    }

    public FileViewer(final Path workspace, final Path file, final boolean showDiffInitially) {
        super(new BorderLayout(0, UiConstants.CONTENT_PADDING));
        this.workspace = workspace.toAbsolutePath().normalize();
        this.file = file.toAbsolutePath().normalize();
        this.showDiffInitially = showDiffInitially;
        setBorder(UiFactory.sectionBorder());
        add(toolbar(), BorderLayout.NORTH);
        configureSource();
        configureDiff();
        content.add(new JScrollPane(source), SOURCE);
        content.add(new JScrollPane(diff), DIFF);
        cards.show(content, showDiffInitially ? DIFF : SOURCE);
        add(content, BorderLayout.CENTER);
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
        sourceButton.setSelected(!showDiffInitially);
        diffButton.setSelected(showDiffInitially);
        sourceButton.addActionListener(event -> cards.show(content, SOURCE));
        diffButton.addActionListener(event -> cards.show(content, DIFF));
        viewModes.add(sourceButton);
        viewModes.add(diffButton);
        controls.add(viewModes);
        controls.add(searchControls);
        controls.add(status);
        toolbar.add(controls, BorderLayout.EAST);
        return toolbar;
    }

    private static JToggleButton segmentedButton(
            final javax.swing.Icon icon, final String name, final String position) {
        final JToggleButton button = new JToggleButton(icon);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        button.putClientProperty("JButton.buttonType", "segmented");
        button.putClientProperty("JButton.segmentPosition", position);
        UiFactory.configureButtonEnter(button);
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
        diff.setCodeFoldingEnabled(false);
        diff.setHighlightCurrentLine(true);
        diff.setLineWrap(false);
        diff.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
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
        BackgroundOperations.submit(
                        "Workspace",
                        "file-view",
                        () -> {
                            final byte[] bytes = Files.readAllBytes(file);
                            final boolean binary = isBinary(bytes);
                            final String content =
                                    binary ? "" : new String(bytes, StandardCharsets.UTF_8);
                            final String fileDiff;
                            try (GitRepository repository = GitRepository.open(workspace)) {
                                fileDiff = repository.getFileDiff(file, false, "HEAD");
                            }
                            return new LoadedFile(content, fileDiff, binary);
                        })
                .thenAccept(
                        loaded -> {
                            if (loaded.binary()) {
                                loadedContent = null;
                                source.setText("Binary file cannot be displayed.");
                                GitFormatter.renderDiff(diff, loaded.diff());
                                status.setText("Binary");
                                return;
                            }
                            loadedContent = loaded.content();
                            source.setText(loaded.content());
                            source.setCaretPosition(0);
                            GitFormatter.renderDiff(diff, loaded.diff());
                            status.setText(loaded.diff().isBlank() ? "Unchanged" : "Changed");
                            searchControls.refresh();
                        })
                .exceptionally(
                        failure -> {
                            loadedContent = null;
                            source.setText(
                                    "Could not load file: "
                                            + ErrorMessages.deepestCause(failure, "Unknown error"));
                            status.setText("Unavailable");
                            return null;
                        });
    }

    private static boolean isBinary(final byte[] bytes) {
        for (final byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private record LoadedFile(String content, String diff, boolean binary) {}
}
