package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.git.GitRepository;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** Branch and worktree status presentation for a session summary. */
public final class SessionBranchPanel extends JPanel {
    private final JTextArea value =
            new SelectableTextLabel("Loading branch status...", Theme.FontSize.MD);
    private final Consumer<Boolean> cleanChanged;

    public SessionBranchPanel(final Consumer<Boolean> cleanChanged) {
        super(new BorderLayout());
        this.cleanChanged = cleanChanged;
        value.setRows(1);
        add(value, BorderLayout.CENTER);
    }

    public void show(final GitRepository.WorktreeStatus status) {
        value.setText(
                (status.branch().isBlank() ? "Detached HEAD" : status.branch())
                        + (status.clean() ? "  ·  Clean" : "  ·  Changes present"));
        cleanChanged.accept(status.clean());
    }

    public void showUnavailable(final String message) {
        value.setText(message);
    }
}
