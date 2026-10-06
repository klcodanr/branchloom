package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.git.GitRepository;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Optional changes section for a session summary. */
public final class SessionChangesSection extends JPanel {
    private final SessionDiffPanel diff = new SessionDiffPanel();
    private Consumer<Boolean> visibilityChanged = ignored -> {};

    public SessionChangesSection() {
        super(new BorderLayout(UiConstants.SPACING_MD, 0));
        setOpaque(false);
        final JLabel title = new JLabel("Changes");
        title.setFont(Theme.font(Theme.FontSize.SM));
        title.setPreferredSize(
                new java.awt.Dimension(
                        SessionSummary.LABEL_WIDTH, title.getPreferredSize().height));
        add(title, BorderLayout.WEST);
        add(diff, BorderLayout.CENTER);
    }

    public void onVisibilityChanged(final Consumer<Boolean> listener) {
        visibilityChanged = listener;
    }

    public void showLoading() {
        diff.showLoading();
    }

    public void show(final GitRepository.WorktreeStatus status) {
        if (status.clean()) {
            visibilityChanged.accept(false);
            return;
        }
        diff.show(status);
        visibilityChanged.accept(true);
    }

    public void showUnavailable(final String message) {
        diff.showMessage(message);
        visibilityChanged.accept(true);
    }
}
