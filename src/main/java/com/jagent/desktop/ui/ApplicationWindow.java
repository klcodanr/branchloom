package com.jagent.desktop.ui;

import com.jagent.desktop.models.WindowState;
import com.jagent.desktop.services.persistence.WindowStatePersistence;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

/** Owns the application frame without knowing about application actions or content. */
public final class ApplicationWindow extends JFrame {
    public ApplicationWindow(
            final WindowStatePersistence windowStatePersistence, final Runnable closeIntent) {
        super("Branchloom");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        configureBounds(windowStatePersistence.state());
        addComponentListener(
                new ComponentAdapter() {
                    @Override
                    public void componentResized(final ComponentEvent event) {
                        windowStatePersistence.update(currentWindowState());
                    }

                    @Override
                    public void componentMoved(final ComponentEvent event) {
                        windowStatePersistence.update(currentWindowState());
                    }
                });
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(final WindowEvent event) {
                        closeIntent.run();
                    }
                });
    }

    private void configureBounds(final WindowState windowState) {
        if (windowState != null
                && windowState.windowWidth >= 900
                && windowState.windowHeight >= 600) {
            setSize(windowState.windowWidth, windowState.windowHeight);
            setLocation(windowState.windowX, windowState.windowY);
        } else {
            setSize(1240, 760);
            setLocationRelativeTo(null);
        }
    }

    private WindowState currentWindowState() {
        final var bounds = getBounds();
        final var state = new WindowState();
        state.windowX = bounds.x;
        state.windowY = bounds.y;
        state.windowWidth = bounds.width;
        state.windowHeight = bounds.height;
        return state;
    }
}
