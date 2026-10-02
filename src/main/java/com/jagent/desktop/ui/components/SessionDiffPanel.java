package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.git.GitRepository;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Diff summary presentation for a session summary. */
public final class SessionDiffPanel extends JPanel {
    public SessionDiffPanel() {
        super();
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    public void showLoading() {
        removeAll();
        add(new SelectableTextLabel("Loading diff...", Theme.FontSize.MD));
        revalidate();
        repaint();
    }

    public void show(final GitRepository.WorktreeStatus status) {
        removeAll();
        final List<FileStatus> files = new ArrayList<>();
        addFiles(files, "+", Theme.Colors.success(), status.added());
        addFiles(files, "-", Theme.Colors.danger(), status.removed());
        addFiles(files, "~", Theme.Colors.warning(), status.changed());
        addFiles(files, "M", Theme.Colors.warning(), status.modified());
        addFiles(files, "D", Theme.Colors.warning(), status.missing());
        addFiles(files, "??", Theme.Colors.warning(), status.untracked());
        files.sort(Comparator.comparing(FileStatus::path));
        for (final FileStatus file : files) {
            addFile(file);
        }
        revalidate();
        repaint();
    }

    public void showMessage(final String message) {
        removeAll();
        add(new SelectableTextLabel(message, Theme.FontSize.MD));
        revalidate();
        repaint();
    }

    private static void addFiles(
            final List<FileStatus> files,
            final String prefix,
            final Color color,
            final java.util.Set<String> paths) {
        for (final String path : paths) {
            files.add(new FileStatus(path, prefix, color));
        }
    }

    private void addFile(final FileStatus file) {
        final var row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        final var indicator = new JLabel(file.prefix() + " ");
        indicator.setFont(Theme.font(Theme.FontSize.MD));
        indicator.setForeground(file.color());
        row.add(indicator, BorderLayout.WEST);
        row.add(new SelectableTextLabel(file.path(), Theme.FontSize.MD), BorderLayout.CENTER);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        add(row);
    }

    private record FileStatus(String path, String prefix, Color color) {}
}
