package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.services.AgentContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

/** Agent-context file and editor controls for a session summary. */
public final class AgentContextEditor extends JPanel {
    private final transient AppState appState;
    private final transient ViewController viewController;
    private final RSyntaxTextArea content = new RSyntaxTextArea("Loading context...");
    private final UndoManager undoManager = new UndoManager();
    private final BaseButton save = new BaseButton("Save");
    private boolean changingHistory;

    public AgentContextEditor(final AppState appState, final ViewController viewController) {
        super(new BorderLayout(0, UiConstants.SPACING_XS));
        this.appState = appState;
        this.viewController = viewController;
        configureContent();
        configureActions();
        add(contentScroll(), BorderLayout.CENTER);
        add(save.wrap(), BorderLayout.SOUTH);
        showLoading();
    }

    private void configureContent() {
        content.setSyntaxEditingStyle(RSyntaxTextArea.SYNTAX_STYLE_MARKDOWN);
        content.setCodeFoldingEnabled(true);
        content.setHighlightCurrentLine(true);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setFont(Theme.terminalFont(Theme.FontSize.SM));
        content.getDocument()
                .addUndoableEditListener(
                        event -> {
                            if (!changingHistory) {
                                undoManager.addEdit(event.getEdit());
                            }
                        });
        content.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "editor-undo");
        content.getActionMap()
                .put(
                        "editor-undo",
                        new javax.swing.AbstractAction() {
                            @Override
                            public void actionPerformed(final ActionEvent event) {
                                undo();
                            }
                        });
        content.getInputMap().put(KeyStroke.getKeyStroke("control Y"), "editor-redo");
        content.getInputMap().put(KeyStroke.getKeyStroke("control shift Z"), "editor-redo");
        content.getActionMap()
                .put(
                        "editor-redo",
                        new javax.swing.AbstractAction() {
                            @Override
                            public void actionPerformed(final ActionEvent event) {
                                redo();
                            }
                        });
        content.getInputMap().put(KeyStroke.getKeyStroke("control S"), "editor-save");
        content.getActionMap()
                .put(
                        "editor-save",
                        new javax.swing.AbstractAction() {
                            @Override
                            public void actionPerformed(final ActionEvent event) {
                                save();
                            }
                        });
        applyEditorTheme();
    }

    private void configureActions() {
        save.addActionListener(event -> save());
    }

    private void applyEditorTheme() {
        content.setBackground(Theme.Colors.textareaBackground());
        content.setForeground(Theme.Colors.textareaForeground());
        content.setCaretColor(Theme.Colors.textCaretForeground());
        content.setSelectionColor(Theme.Colors.textSelectionBackground());
        content.setSelectedTextColor(Theme.Colors.textSelectionForeground());
        content.setCurrentLineHighlightColor(
                lineHighlight(
                        Theme.Colors.textareaBackground(), Theme.Colors.textareaForeground()));
        content.setSyntaxScheme(syntaxScheme());
    }

    private static Color lineHighlight(final Color background, final Color foreground) {
        final double amount = isDark(foreground) ? 0.10d : 0.06d;
        return new Color(
                blend(background.getRed(), foreground.getRed(), amount),
                blend(background.getGreen(), foreground.getGreen(), amount),
                blend(background.getBlue(), foreground.getBlue(), amount));
    }

    private static boolean isDark(final Color color) {
        return color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114 < 128_000;
    }

    private static int blend(final int background, final int foreground, final double amount) {
        return (int) Math.round(background + (foreground - background) * amount);
    }

    private static SyntaxScheme syntaxScheme() {
        final SyntaxScheme scheme = new SyntaxScheme(true);
        for (int index = 0; index < scheme.getStyleCount(); index++) {
            scheme.getStyle(index).foreground = Theme.Colors.textareaForeground();
        }
        scheme.getStyle(TokenTypes.RESERVED_WORD).foreground = Theme.Colors.purple();
        scheme.getStyle(TokenTypes.RESERVED_WORD_2).foreground = Theme.Colors.purple();
        scheme.getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground = Theme.Colors.green();
        scheme.getStyle(TokenTypes.COMMENT_EOL).foreground = Theme.Colors.textareaForeground();
        scheme.getStyle(TokenTypes.COMMENT_MULTILINE).foreground =
                Theme.Colors.textareaForeground();
        return scheme;
    }

    public void setContent(final String value) {
        content.setText(UiText.valueOrDefault(value, "No context configured."));
        content.setCaretPosition(0);
        final boolean hasValue = value != null && !value.isEmpty();
        content.setEditable(hasValue);
        undoManager.discardAllEdits();
        save.setEnabled(hasValue);
    }

    public void showLoading() {
        content.setText("Loading context...");
        content.setEditable(false);
        undoManager.discardAllEdits();
        save.setEnabled(false);
    }

    private void undo() {
        changingHistory = true;
        try {
            undoManager.undo();
        } catch (CannotUndoException ignored) {
            // The button and shortcut can race with a document update.
        } finally {
            changingHistory = false;
        }
    }

    private void redo() {
        changingHistory = true;
        try {
            undoManager.redo();
        } catch (CannotRedoException ignored) {
            // The button and shortcut can race with a document update.
        } finally {
            changingHistory = false;
        }
    }

    private JScrollPane contentScroll() {
        final JScrollPane scroll = new JScrollPane(content);
        scroll.setPreferredSize(new Dimension(0, 180));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private void save() {
        final String textContent = this.content.getText();
        final var project = viewController.currentProject();
        final var session = viewController.currentSession();
        if (project == null || session == null) {
            return;
        }
        BackgroundOperations.submit(
                        "Session summary",
                        "save-session-agent-context",
                        () -> {
                            AgentContext.save(
                                    project,
                                    session,
                                    appState.appSettings().agentContextPath(),
                                    textContent);
                            return null;
                        })
                .exceptionally(
                        failure -> {
                            javax.swing.JOptionPane.showMessageDialog(
                                    this,
                                    "Could not save agent context: "
                                            + ErrorMessages.deepestCause(failure, ""),
                                    "Agent context",
                                    javax.swing.JOptionPane.ERROR_MESSAGE);
                            return null;
                        });
    }
}
