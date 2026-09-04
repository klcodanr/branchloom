package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.ui.components.SearchableComboBox;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public final class NewSessionDialog extends JDialog {
    private final transient AppState appState;
    private final transient Consumer<Request> onValid;
    private final JTextField name = new JTextField(35);
    private final JComboBox<Agent> agent;
    private final SearchableComboBox<String> baseBranch;
    private final List<String> branchNames;
    private final JTextArea prompt = new JTextArea(5, 35);
    private final JButton cancel = new JButton("Cancel");
    private final JButton ok = new JButton("OK");

    public record Request(String name, Agent agent, String prompt, String baseBranch) {
        public Request(final String name, final Agent agent, final String prompt) {
            this(name, agent, prompt, null);
        }
    }

    public NewSessionDialog(final ActionContext actionContext, final Consumer<Request> onValid) {
        this(actionContext, List.of(), onValid);
    }

    public NewSessionDialog(
            final ActionContext actionContext,
            final List<Git.Branch> branches,
            final Consumer<Request> onValid) {
        super(actionContext.window(), "New agent session", ModalityType.APPLICATION_MODAL);
        setFocusTraversalPolicy(new ContainerOrderFocusTraversalPolicy());
        UiFactory.configureDialogCloseOnEscape(this);

        this.appState = actionContext.appState();
        this.onValid = onValid;
        name.setName("session-name");
        agent = new JComboBox<>(appState.appSettings().agents().toArray(new Agent[0]));
        agent.setName("session-agent");
        agent.setPreferredSize(new Dimension(350, agent.getPreferredSize().height));
        branchNames = branches.stream().map(Git.Branch::name).toList();
        baseBranch = new SearchableComboBox<>(branchNames);
        baseBranch.setName("session-base-branch");
        baseBranch.setPreferredSize(new Dimension(350, baseBranch.getPreferredSize().height));
        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        UiFactory.configureTextAreaTraversal(prompt);
        prompt.setName("session-prompt");
        cancel.setName("session-cancel");
        ok.setName("session-ok");

        final JPanel promptInput = new JPanel(new BorderLayout());
        promptInput.setOpaque(false);
        promptInput.add(new JScrollPane(prompt), BorderLayout.CENTER);
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);
        setLayout(new BorderLayout(UiConstants.COMPONENT_GAP, UiConstants.COMPONENT_GAP));
        add(
                UiFactory.form(
                        "Session name",
                        name,
                        "Agent",
                        agent,
                        "Base branch",
                        baseBranch,
                        "Prompt",
                        promptInput),
                BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        cancel.addActionListener(event -> dispose());
        ok.addActionListener(event -> validateAndCheckBranch());
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(actionContext.window());
    }

    private void validateAndCheckBranch() {
        if (name.getText().isBlank()) {
            showError(new IOException("Session name is required."));
            return;
        }
        if (agent.getSelectedItem() == null) {
            showError(new IOException("Select an agent."));
            return;
        }
        if (prompt.getText().isBlank()) {
            showError(new IOException("Prompt is required."));
            return;
        }
        final Agent selectedAgent = (Agent) agent.getSelectedItem();
        dispose();
        final String selectedBranch = (String) baseBranch.getSelectedItem();
        onValid.accept(
                new Request(
                        name.getText().trim(),
                        selectedAgent,
                        prompt.getText().trim(),
                        branchNames.contains(selectedBranch) ? selectedBranch : null));
    }

    private void showError(final Throwable exception) {
        JOptionPane.showMessageDialog(
                this, exception.getMessage(), "New agent session", JOptionPane.ERROR_MESSAGE);
    }
}
