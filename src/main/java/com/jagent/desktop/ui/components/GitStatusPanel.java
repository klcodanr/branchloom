package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.Git;
import java.awt.FlowLayout;
import javax.swing.JPanel;

/** Colored aggregate Git status presentation shared by workspace surfaces. */
public final class GitStatusPanel extends JPanel {
    public GitStatusPanel() {
        super(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_SM, 0));
        setOpaque(false);
    }

    public void showRefreshing() {
        showMessage("Refreshing Git status");
    }

    public void showUnavailable(final String message) {
        showMessage(message);
    }

    public void showStatus(final Git.WorktreeStatus status) {
        removeAll();
        if (status.additions() == 0 && status.modifications() == 0 && status.deletions() == 0) {
            add(UiFactory.label("Clean", Theme.FontSize.XS));
        } else {
            addCount(status.additions(), "+", Theme.successColor());
            addCount(status.modifications(), "~", Theme.warningColor());
            addCount(status.deletions(), "-", Theme.dangerColor());
        }
        refresh();
    }

    private void showMessage(final String message) {
        removeAll();
        add(UiFactory.label(message, Theme.FontSize.XS));
        refresh();
    }

    private void addCount(final int count, final String prefix, final java.awt.Color color) {
        if (count == 0) {
            return;
        }
        final var label = UiFactory.label(prefix + count, Theme.FontSize.XS);
        label.setForeground(color);
        add(label);
    }

    private void refresh() {
        revalidate();
        repaint();
    }
}
