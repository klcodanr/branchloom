package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.AgentSelector;
import com.jagent.desktop.ui.components.BaseTextArea;
import com.jagent.desktop.ui.components.FormPanel;
import java.awt.BorderLayout;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.awt.Dimension;
import java.util.function.BiConsumer;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Collects the agent and prompt for a pull-request review. */
public final class ReviewDialog extends FormDialog {
    private final transient BiConsumer<Agent, String> onReview;
    private final JComboBox<Agent> agent;
    private final JTextArea prompt = new BaseTextArea(10, 50);

    public ReviewDialog(
            final AppState appState,
            final DialogService dialogService,
            final PullRequest request,
            final BiConsumer<Agent, String> onReview) {
        super();
        setFocusTraversalPolicy(new ContainerOrderFocusTraversalPolicy());
        final var state = appState;
        this.onReview = onReview;
        agent = new AgentSelector("review-agent", state.appSettings().agents());
        prompt.setText(
                state.appSettings()
                        .reviewPrompt()
                        .replace("{number}", Integer.toString(request.number()))
                        .replace("{title}", request.title()));

        final JPanel promptInput = new JPanel(new BorderLayout());
        final JScrollPane promptScroll = new JScrollPane(prompt);
        promptScroll.setPreferredSize(new Dimension(600, 240));
        promptInput.add(promptScroll, BorderLayout.CENTER);
        initialize(
                new FormPanel("Agent", agent, "Prompt", promptInput),
                "Start review",
                "review-cancel",
                "review-submit",
                this::submit,
                dialogService);
    }

    /* package */
    static boolean validPrompt(final String value) {
        return !value.isBlank();
    }

    private void submit() {
        if (agent.getSelectedItem() == null) {
            dialogService.errorMessage("Select an agent.", "Review pull request");
            return;
        }
        if (!validPrompt(prompt.getText())) {
            dialogService.errorMessage("Prompt is required.", "Review pull request");
            return;
        }
        final Agent selected = (Agent) agent.getSelectedItem();
        onReview.accept(selected, prompt.getText().trim());
    }
}
