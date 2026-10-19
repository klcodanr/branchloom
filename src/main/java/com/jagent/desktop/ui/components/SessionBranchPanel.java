package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.git.GitRepository;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** Branch and worktree status presentation for a session summary. */
public final class SessionBranchPanel extends JPanel {
    private final JTextArea value =
            new SelectableTextLabel("Loading branch status...", Theme.FontSize.MD);

    public SessionBranchPanel() {
        super(new BorderLayout());
        value.setRows(1);
        add(value, BorderLayout.CENTER);
        showLoading();
    }

    public void showLoading() {
        value.setText("Loading branch status...");
    }

    public void setWorktreeStatus(final GitRepository.WorktreeStatus status) {
        if (status != null) {
            value.setText(
                    (status.branch().isBlank() ? "Detached HEAD" : status.branch())
                            + (status.clean() ? "  ·  Clean" : "  ·  Changes present"));
        } else {
            value.setText("Could not load branch status");
        }
    }
}
