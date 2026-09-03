package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.SessionSetup;
import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/** Displays the non-persisted setup state for a session being created. */
public final class SessionSetupProgress extends JPanel {
    private final JLabel message = UiFactory.label("Preparing session...", Theme.FontSize.SM);
    private final JProgressBar loader = new JProgressBar();

    public SessionSetupProgress(final SessionSetup setup, final SessionId sessionId) {
        super(new BorderLayout(8, 0));
        setOpaque(false);
        loader.setIndeterminate(true);
        loader.setPreferredSize(new java.awt.Dimension(16, 16));
        loader.setBorderPainted(false);
        loader.getAccessibleContext().setAccessibleName("Session setup in progress");
        add(loader, BorderLayout.WEST);
        add(message, BorderLayout.CENTER);
        setVisible(setup.progress(sessionId) != null);
        if (isVisible()) {
            setup.listen(sessionId, this::update);
            update(setup.progress(sessionId));
        }
    }

    private void update(final SessionSetup.SetupProgress progress) {
        if (progress == null) {
            return;
        }
        final Runnable update =
                () -> {
                    message.setText(progress.message());
                    loader.setVisible(!progress.failed());
                    revalidate();
                    repaint();
                };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }
}
