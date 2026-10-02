package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

/** Read-only source and diff viewer for a workspace file. */
public final class FileViewer extends JPanel {
    private static final String SOURCE = "source";
    private static final String DIFF = "diff";
    private final Path workspace;
    private final Path file;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final RSyntaxTextArea source = new RSyntaxTextArea();
    private final RSyntaxTextArea diff = new RSyntaxTextArea();
    private final List<Object> diffIndicatorHighlights = new ArrayList<>();
    private final FileViewerToolbar toolbar;
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

    public FileViewer(final Path workspace, final Path file, final boolean showDiffInitially) {
        super(new BorderLayout(0, UiConstants.CONTENT_PADDING));
        this.workspace = workspace.toAbsolutePath().normalize();
        this.file = file.toAbsolutePath().normalize();
        setBorder(UiBorders.section());
        configureSource();
        configureDiff();
        content.add(new JScrollPane(source), SOURCE);
        content.add(new JScrollPane(diff), DIFF);
        cards.show(content, showDiffInitially ? DIFF : SOURCE);
        toolbar =
                new FileViewerToolbar(
                        this.workspace,
                        this.file,
                        showDiffInitially,
                        searchControls,
                        () -> cards.show(content, SOURCE),
                        () -> cards.show(content, DIFF));
        add(toolbar, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        load();
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
        diff.setSyntaxEditingStyle(RSyntaxTextArea.SYNTAX_STYLE_NONE);
        diff.setFont(Theme.terminalFont(Theme.FontSize.SM));
        diff.setBorder(UiBorders.card());
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
        final Color background = Theme.Colors.textareaBackground();
        final Color foreground = Theme.Colors.textareaForeground();
        final Color selectionBackground = Theme.Colors.textSelectionBackground();
        final Color selectionForeground = Theme.Colors.textSelectionForeground();
        final Color caret = Theme.Colors.textCaretForeground();

        source.setBackground(background);
        source.setForeground(foreground);
        source.setCaretColor(caret);
        source.setSelectionColor(selectionBackground);
        source.setSelectedTextColor(selectionForeground);
        source.setCurrentLineHighlightColor(background);
        source.setSyntaxScheme(syntaxScheme(foreground));

        diff.setBackground(background);
        diff.setForeground(foreground);
        diff.setCaretColor(caret);
        diff.setSelectionColor(selectionBackground);
        diff.setSelectedTextColor(selectionForeground);
        diff.setCurrentLineHighlightColor(lineHighlight(background, foreground));
    }

    private static SyntaxScheme syntaxScheme(final Color foreground) {
        final Color keyword = Theme.Colors.purple();
        final Color string = Theme.Colors.green();
        final Color comment = Theme.Colors.textareaForeground();
        final Color number = Theme.Colors.blue();
        final Color literal = Theme.Colors.darkYellow();
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

    private static Color lineHighlight(final Color background, final Color foreground) {
        final double amount = isDark(foreground) ? 0.10d : 0.06d;
        return new Color(
                blend(background.getRed(), foreground.getRed(), amount),
                blend(background.getGreen(), foreground.getGreen(), amount),
                blend(background.getBlue(), foreground.getBlue(), amount));
    }

    private static int blend(final int background, final int foreground, final double amount) {
        return (int) Math.round(background + (foreground - background) * amount);
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
                            return new LoadedFile(
                                    content, fileDiff, binary, changedLines(fileDiff));
                        })
                .thenAccept(
                        loaded -> {
                            if (loaded.binary()) {
                                loadedContent = null;
                                source.setText("Binary file cannot be displayed.");
                                renderDiff(loaded.diff());
                                highlightDiffLines(loaded.changedLines());
                                setStatus("Binary");
                                return;
                            }
                            loadedContent = loaded.content();
                            source.setText(loaded.content());
                            source.setCaretPosition(0);
                            renderDiff(loaded.diff());
                            highlightDiffLines(loaded.changedLines());
                            setStatus(loaded.diff().isBlank() ? "Unchanged" : "Changed");
                            searchControls.refresh();
                        })
                .exceptionally(
                        failure -> {
                            loadedContent = null;
                            source.setText(
                                    "Could not load file: "
                                            + ErrorMessages.deepestCause(failure, "Unknown error"));
                            setStatus("Unavailable");
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

    private void renderDiff(final String output) {
        diff.setText(output.isBlank() ? "No changes from HEAD." : output);
        diff.setCaretPosition(0);
    }

    private static List<Integer> changedLines(final String output) {
        final List<Integer> changedLines = new ArrayList<>();
        if (output.isBlank()) {
            return changedLines;
        }
        final String[] lines = output.split("\\R", -1);
        for (int line = 0; line < lines.length; line++) {
            final String text = lines[line].stripLeading();
            if (text.startsWith("+") && !text.startsWith("+++")) {
                changedLines.add(line + 1);
            } else if (text.startsWith("-") && !text.startsWith("---")) {
                changedLines.add(-(line + 1));
            }
        }
        return List.copyOf(changedLines);
    }

    private void setStatus(final String text) {
        toolbar.setStatus(text);
    }

    private void highlightDiffLines(final List<Integer> changedLines) {
        diff.removeAllLineHighlights();
        diffIndicatorHighlights.forEach(diff.getHighlighter()::removeHighlight);
        diffIndicatorHighlights.clear();
        if (changedLines.isEmpty()) {
            return;
        }
        final Color background = diff.getBackground();
        final Color addedAccent = Theme.Colors.success();
        final Color removedAccent = Theme.Colors.danger();
        final Color added = diffLineColor(background, addedAccent);
        final Color removed = diffLineColor(background, removedAccent);
        final Highlighter.HighlightPainter addedPainter =
                new DefaultHighlighter.DefaultHighlightPainter(addedAccent);
        final Highlighter.HighlightPainter removedPainter =
                new DefaultHighlighter.DefaultHighlightPainter(removedAccent);
        for (final int changedLine : changedLines) {
            try {
                final boolean addition = changedLine > 0;
                final int line = Math.abs(changedLine) - 1;
                final int start = diff.getLineStartOffset(line);
                diff.addLineHighlight(line, addition ? added : removed);
                diffIndicatorHighlights.add(
                        diff.getHighlighter()
                                .addHighlight(
                                        start,
                                        start + 1,
                                        addition ? addedPainter : removedPainter));
            } catch (BadLocationException ignored) {
                // The document cannot change while this EDT callback is running.
            }
        }
    }

    private static Color diffLineColor(final Color background, final Color accent) {
        final double amount = isDark(background) ? 0.20d : 0.12d;
        return new Color(
                blend(background.getRed(), accent.getRed(), amount),
                blend(background.getGreen(), accent.getGreen(), amount),
                blend(background.getBlue(), accent.getBlue(), amount));
    }

    private record LoadedFile(
            String content, String diff, boolean binary, List<Integer> changedLines) {}
}
