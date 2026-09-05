package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.BiConsumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Collects the agent and prompt for a pull-request review. */
public final class ReviewDialog extends JDialog {
    private final transient BiConsumer<Agent, String> onReview;
    private final JComboBox<Agent> agent;
    private final JTextArea prompt = new JTextArea(10, 50);

    public ReviewDialog(
            final ActionContext actionContext,
            final PullRequest request,
            final BiConsumer<Agent, String> onReview) {
        super(
                actionContext.window(),
                "Review pull request #" + request.number(),
                ModalityType.APPLICATION_MODAL);
        setFocusTraversalPolicy(new ContainerOrderFocusTraversalPolicy());
        UiFactory.configureDialogCloseOnEscape(this);
        final AppState state = actionContext.appState();
        this.onReview = onReview;
        agent = new JComboBox<>(state.appSettings().agents().toArray(new Agent[0]));
        agent.setPreferredSize(new Dimension(350, agent.getPreferredSize().height));
        prompt.setText(
                state.appSettings()
                        .reviewPrompt()
                        .replace("{number}", Integer.toString(request.number()))
                        .replace("{title}", request.title()));
        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        UiFactory.configureTextAreaTraversal(prompt);

        final JPanel promptInput = new JPanel(new BorderLayout());
        final JScrollPane promptScroll = new JScrollPane(prompt);
        promptScroll.setPreferredSize(new Dimension(600, 240));
        promptInput.add(promptScroll, BorderLayout.CENTER);
        final JButton cancel = UiFactory.button("Cancel");
        final JButton review = UiFactory.button("Start review");
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(review);
        setLayout(new BorderLayout(UiConstants.COMPONENT_GAP, UiConstants.COMPONENT_GAP));
        add(UiFactory.form("Agent", agent, "Prompt", promptInput), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        cancel.addActionListener(event -> dispose());
        review.addActionListener(event -> submit());
        getRootPane().setDefaultButton(review);
        pack();
        setLocationRelativeTo(actionContext.window());
    }

    /* package */
    static String defaultPrompt(final String template, final PullRequest request) {
        return template.replace("{number}", Integer.toString(request.number()))
                .replace("{title}", request.title());
    }

    /* package */
    static boolean validPrompt(final String value) {
        return !value.isBlank();
    }

    private void submit() {
        if (agent.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(
                    this, "Select an agent.", "Review pull request", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!validPrompt(prompt.getText())) {
            JOptionPane.showMessageDialog(
                    this, "Prompt is required.", "Review pull request", JOptionPane.ERROR_MESSAGE);
            return;
        }
        final Agent selected = (Agent) agent.getSelectedItem();
        dispose();
        onReview.accept(selected, prompt.getText().trim());
    }
}
