package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.services.AgentContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

/** Agent-context file and editor controls for a session summary. */
public final class AgentContextEditor extends JPanel {
    private final transient AppState appState;
    private final transient ViewController viewController;
    private final JTextArea content = new BaseTextArea("Loading context...");
    private final JButton save = new BaseButton("Save");

    public AgentContextEditor(final AppState appState, final ViewController viewController) {
        super(new BorderLayout(0, UiConstants.SPACING_XS));
        this.appState = appState;
        this.viewController = viewController;
        content.setRows(10);
        showLoading();
        save.setFont(Theme.font(Theme.FontSize.XS));
        save.setMargin(UiConstants.ZERO_INSETS);
        save.addActionListener(event -> save());

        add(contentScroll(), BorderLayout.CENTER);
        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(save);
        add(actions, BorderLayout.SOUTH);
    }

    public void setContent(final String value) {
        content.setText(UiText.valueOrDefault(value, "No context configured."));
        content.setCaretPosition(0);
        final boolean hasValue = value != null && !value.isEmpty();
        content.setEditable(hasValue);
        save.setEnabled(hasValue);
    }

    public void showLoading() {
        content.setText("Loading context...");
        content.setEditable(false);
        save.setEnabled(false);
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
