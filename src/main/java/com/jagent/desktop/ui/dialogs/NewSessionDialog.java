package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.git.Branch;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.components.SearchableComboBox;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import com.jagent.desktop.ui.utils.ClipboardImagePaster;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FocusTraversalPolicy;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
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
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

public final class NewSessionDialog extends JDialog {
    private static final String TITLE = "New agent session";
    private final transient AppState appState;
    private final transient Consumer<Request> onValid;
    private final JTextField name = new JTextField(35);
    private final JComboBox<Agent> agent;
    private final SearchableComboBox<BranchChoice> baseBranch;
    private final JTextArea prompt = new JTextArea(5, 35);
    private final JButton cancel = UiFactory.button("Cancel");
    private final JButton ok = UiFactory.button("OK");

    private record BranchChoice(String displayName, String ref) {
        @Override
        public String toString() {
            return displayName;
        }
    }

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
            final List<Branch> branches,
            final Consumer<Request> onValid) {
        super(actionContext.window(), TITLE, ModalityType.APPLICATION_MODAL);
        UiFactory.configureDialogCloseOnEscape(this);

        this.appState = actionContext.appState();
        this.onValid = onValid;
        name.setName("session-name");
        agent = new JComboBox<>(appState.appSettings().agents().toArray(new Agent[0]));
        agent.setName("session-agent");
        agent.setPreferredSize(new Dimension(350, agent.getPreferredSize().height));
        final List<BranchChoice> branchChoices =
                branches.stream()
                        .map(branch -> new BranchChoice(branch.displayName(), branch.name()))
                        .toList();
        baseBranch = new SearchableComboBox<>(branchChoices);
        baseBranch.setName("session-base-branch");
        baseBranch.setPreferredSize(new Dimension(350, baseBranch.getPreferredSize().height));
        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        UiFactory.configureTextAreaTraversal(prompt);
        prompt.setName("session-prompt");
        installImagePasteHandler();
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
        setFocusTraversalPolicy(
                new FocusTraversalPolicy() {
                    private final List<Component> order =
                            List.of(name, agent, baseBranch, prompt, cancel, ok);

                    @Override
                    public Component getComponentAfter(
                            final Container container, final Component component) {
                        return adjacent(component, 1);
                    }

                    @Override
                    public Component getComponentBefore(
                            final Container container, final Component component) {
                        return adjacent(component, -1);
                    }

                    @Override
                    public Component getFirstComponent(final Container container) {
                        return order.getFirst();
                    }

                    @Override
                    public Component getLastComponent(final Container container) {
                        return order.getLast();
                    }

                    @Override
                    public Component getDefaultComponent(final Container container) {
                        return order.getFirst();
                    }

                    private Component adjacent(final Component component, final int direction) {
                        final int index = indexOf(component);
                        if (index < 0) {
                            return direction > 0 ? order.getFirst() : order.getLast();
                        }
                        return order.get(Math.floorMod(index + direction, order.size()));
                    }

                    private int indexOf(final Component component) {
                        for (int index = 0; index < order.size(); index++) {
                            if (order.get(index).equals(component)
                                    || SwingUtilities.isDescendingFrom(
                                            component, order.get(index))) {
                                return index;
                            }
                        }
                        return -1;
                    }
                });
        pack();
        setLocationRelativeTo(actionContext.window());
    }

    private void installImagePasteHandler() {
        final TransferHandler delegate = prompt.getTransferHandler();
        prompt.setTransferHandler(
                new TransferHandler() {
                    @Override
                    public boolean importData(
                            final javax.swing.JComponent component,
                            final Transferable transferable) {
                        if (!transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                            return delegate.importData(component, transferable);
                        }
                        return ClipboardImagePaster.paste(
                                transferable,
                                prompt::replaceSelection,
                                message ->
                                        JOptionPane.showMessageDialog(
                                                NewSessionDialog.this,
                                                message,
                                                "Clipboard image paste failed",
                                                JOptionPane.ERROR_MESSAGE));
                    }
                });
    }

    private void validateAndCheckBranch() {
        final String validationMessage =
                validationFailure(
                        name.getText(), (Agent) agent.getSelectedItem(), prompt.getText());
        if (validationMessage != null) {
            JOptionPane.showMessageDialog(
                    this, validationMessage, TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }
        final Agent selectedAgent = (Agent) agent.getSelectedItem();
        dispose();
        final Object selectedBranch = baseBranch.getSelectedItem();
        onValid.accept(
                new Request(
                        name.getText().trim(),
                        selectedAgent,
                        prompt.getText().trim(),
                        selectedBranch instanceof BranchChoice branchChoice
                                ? branchChoice.ref()
                                : null));
    }

    /* default */ static String validationFailure(
            final String name, final Agent agent, final String prompt) {
        if (name == null || name.isBlank()) {
            return "Session name is required.";
        }
        if (agent == null) {
            return "Select an agent.";
        }
        if (prompt == null || prompt.isBlank()) {
            return "Prompt is required.";
        }
        return null;
    }
}
