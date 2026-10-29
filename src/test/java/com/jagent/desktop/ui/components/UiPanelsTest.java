package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.awt.Color;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.Test;

class UiPanelsTest {
    @Test
    void createsLoadingMetricFormAndEmptyPanels() {
        final var loading = new LoadingPanel("Loading");
        final var form = new FormPanel("Name", new JTextArea());
        final var empty = new EmptyStatePanel("Nothing", "Try again");

        assertTrue(
                ((javax.swing.JProgressBar) ((JPanel) loading.getComponent(0)).getComponent(0))
                        .isIndeterminate(),
                "loading progress should be indeterminate");
        assertEquals(2, form.getComponentCount(), "form components");
        assertEquals("Nothing", ((JLabel) empty.getComponent(1)).getText(), "empty title");
    }

    @Test
    void contextEditorUsesMarkdownHighlightingAndEditingActions() throws BadLocationException {
        final var state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var editor =
                new AgentContextEditor(
                        state, TestAppState.controller(state), TestAppState.dialogService());
        final var content =
                (RSyntaxTextArea) ((JScrollPane) editor.getComponent(0)).getViewport().getView();
        final var actions = (JPanel) editor.getComponent(1);
        final var save = (JButton) actions.getComponent(0);

        assertEquals(
                RSyntaxTextArea.SYNTAX_STYLE_MARKDOWN,
                content.getSyntaxEditingStyle(),
                "context should use Markdown highlighting");
        assertNotEquals(
                Color.YELLOW,
                content.getCurrentLineHighlightColor(),
                "current-line highlight should not use the unreadable default yellow");
        assertEquals(1, actions.getComponentCount(), "editor should expose only save visibly");
        assertEquals("Save", save.getText(), "save should be a regular labeled button");
        assertNotNull(save.getMargin(), "save should retain standard button insets");
        assertNotEquals(
                UiConstants.ZERO_INSETS, save.getMargin(), "save should not use compact insets");
        assertEquals(
                "editor-undo",
                content.getInputMap().get(KeyStroke.getKeyStroke("control Z")),
                "undo should remain available by keyboard");
        assertEquals(
                "editor-redo",
                content.getInputMap().get(KeyStroke.getKeyStroke("control Y")),
                "redo should remain available by keyboard");

        editor.setContent("# Context");
        content.getDocument().insertString(content.getDocument().getLength(), "\nnotes", null);
        content.getActionMap().get("editor-undo").actionPerformed(null);
        assertEquals("# Context", content.getText(), "undo should restore the original text");
        content.getActionMap().get("editor-redo").actionPerformed(null);
        assertEquals("# Context\nnotes", content.getText(), "redo should restore the edit");
    }
}
