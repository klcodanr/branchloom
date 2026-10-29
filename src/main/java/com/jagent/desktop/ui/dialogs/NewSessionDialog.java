package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.git.Branch;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.AgentSelector;
import com.jagent.desktop.ui.components.BaseTextArea;
import com.jagent.desktop.ui.components.FormPanel;
import com.jagent.desktop.ui.components.SearchableComboBox;
import com.jagent.desktop.ui.utils.ClipboardImagePaster;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

public final class NewSessionDialog extends FormDialog {
    private static final String TITLE = "New agent session";
    private final transient Consumer<Request> onValid;
    private final JTextField name = new JTextField(35);
    private final JComboBox<Agent> agent;
    private final SearchableComboBox<BranchChoice> baseBranch;
    private final JTextArea prompt = new BaseTextArea(5, 35);

    public record Request(String name, Agent agent, String prompt, String baseBranch) {
        public Request(final String name, final Agent agent, final String prompt) {
            this(name, agent, prompt, null);
        }
    }

    private record BranchChoice(String displayName, String ref) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    public NewSessionDialog(
            final AppState appState,
            final DialogService dialogService,
            final Consumer<Request> onValid) {
        this(appState, dialogService, List.of(), onValid);
    }

    public NewSessionDialog(
            final AppState appState,
            final DialogService dialogService,
            final List<Branch> branches,
            final Consumer<Request> onValid) {
        super();
        this.onValid = onValid;
        name.setName("session-name");
        agent = new AgentSelector("session-agent", appState.appSettings().agents());
        final List<BranchChoice> branchChoices =
                branches.stream()
                        .map(branch -> new BranchChoice(branch.displayName(), branch.name()))
                        .toList();
        baseBranch = new SearchableComboBox<>(branchChoices);
        baseBranch.setName("session-base-branch");
        baseBranch.setPreferredSize(new Dimension(350, baseBranch.getPreferredSize().height));
        prompt.setName("session-prompt");
        installImagePasteHandler();
        final var promptInput = new javax.swing.JPanel(new java.awt.BorderLayout());
        promptInput.setOpaque(false);
        promptInput.add(new JScrollPane(prompt), java.awt.BorderLayout.CENTER);
        initialize(
                new FormPanel(
                        "Session name",
                        name,
                        "Agent",
                        agent,
                        "Base branch",
                        baseBranch,
                        "Prompt",
                        promptInput),
                "OK",
                "session-cancel",
                "session-ok",
                this::validateAndCheckBranch,
                dialogService);
        setFocusTraversalPolicy(
                new FocusTraversalPolicy() {
                    private final List<Component> order =
                            List.of(name, agent, baseBranch, prompt, cancelButton, primaryButton);

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
                                        dialogService.errorMessage(
                                                message, "Clipboard image paste failed"));
                    }
                });
    }

    private void validateAndCheckBranch() {
        final String validationMessage =
                validationFailure(
                        name.getText(), (Agent) agent.getSelectedItem(), prompt.getText());
        if (validationMessage != null) {
            dialogService.errorMessage(validationMessage, TITLE);
            return;
        }
        final Agent selectedAgent = (Agent) agent.getSelectedItem();
        final BranchChoice selectedBranch = (BranchChoice) baseBranch.getSelectedItem();
        onValid.accept(
                new Request(
                        name.getText().trim(),
                        selectedAgent,
                        prompt.getText().trim(),
                        selectedBranch == null ? null : selectedBranch.ref()));
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
