package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.ui.components.AgentSelector;
import com.jagent.desktop.ui.components.BaseTextArea;
import com.jagent.desktop.ui.components.FormPanel;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Collects one session description per pasted line. */
public final class PasteSessionsDialog extends FormDialog {
    private final transient Consumer<Request> onValid;
    private final JTextArea lines = new BaseTextArea(12, 45);
    private final JTextArea basePrompt = new BaseTextArea(12, 45);
    private final JComboBox<Agent> agent;

    public record Request(List<String> lines, Agent agent, String basePrompt) {}

    public PasteSessionsDialog(final ActionContext actionContext, final Consumer<Request> onValid) {
        super(actionContext.window(), "Start sessions from pasted lines");
        this.onValid = onValid;
        lines.setName("paste-session-lines");
        lines.setLineWrap(true);
        lines.setWrapStyleWord(true);
        basePrompt.setName("paste-session-base-prompt");
        basePrompt.setLineWrap(true);
        basePrompt.setWrapStyleWord(true);
        basePrompt.setText("{prompt}");
        agent =
                new AgentSelector(
                        "paste-session-agent", actionContext.appState().appSettings().agents());
        initialize(
                new FormPanel(
                        "Prompt template (use {prompt})",
                        new JScrollPane(basePrompt),
                        "Session names (one per line)",
                        new JScrollPane(lines),
                        "Agent",
                        agent),
                "Create sessions",
                "paste-session-cancel",
                "paste-session-create",
                this::submit,
                actionContext.window());
    }

    /* package */
    static List<String> nonBlankLines(final String text) {
        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /* package */
    static Request request(final String text, final Agent agent, final String basePrompt) {
        return new Request(nonBlankLines(text), agent, basePrompt.trim());
    }

    private void submit() {
        final List<String> values = nonBlankLines(lines.getText());
        if (values.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Enter at least one non-blank line.",
                    "Start sessions from pasted lines",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        dispose();
        onValid.accept(
                request(lines.getText(), (Agent) agent.getSelectedItem(), basePrompt.getText()));
    }
}
