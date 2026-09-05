package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Collects one session description per pasted line. */
public final class PasteSessionsDialog extends JDialog {
    private final transient Consumer<Request> onValid;
    private final JTextArea lines = new JTextArea(12, 45);
    private final JTextArea basePrompt = new JTextArea(12, 45);
    private final JComboBox<Agent> agent;

    public record Request(List<String> lines, Agent agent, String basePrompt) {}

    public PasteSessionsDialog(final ActionContext actionContext, final Consumer<Request> onValid) {
        super(
                actionContext.window(),
                "Start sessions from pasted lines",
                ModalityType.APPLICATION_MODAL);
        this.onValid = onValid;
        lines.setName("paste-session-lines");
        lines.setLineWrap(true);
        lines.setWrapStyleWord(true);
        basePrompt.setName("paste-session-base-prompt");
        basePrompt.setLineWrap(true);
        basePrompt.setWrapStyleWord(true);
        basePrompt.setText("{prompt}");
        agent =
                new JComboBox<>(
                        actionContext.appState().appSettings().agents().toArray(new Agent[0]));
        agent.setName("paste-session-agent");
        agent.setPreferredSize(new Dimension(350, agent.getPreferredSize().height));
        final JButton cancel = new JButton("Cancel");
        final JButton create = new JButton("Create sessions");
        cancel.setName("paste-session-cancel");
        create.setName("paste-session-create");
        cancel.addActionListener(event -> dispose());
        create.addActionListener(event -> submit());
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(create);
        setLayout(new BorderLayout(UiConstants.COMPONENT_GAP, UiConstants.COMPONENT_GAP));
        add(
                UiFactory.form(
                        "Prompt template (use {prompt})",
                        new JScrollPane(basePrompt),
                        "Session names (one per line)",
                        new JScrollPane(lines),
                        "Agent",
                        agent),
                BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(actionContext.window());
    }

    private void submit() {
        final List<String> values =
                Arrays.stream(lines.getText().split("\\R"))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
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
                new Request(values, (Agent) agent.getSelectedItem(), basePrompt.getText().trim()));
    }
}
