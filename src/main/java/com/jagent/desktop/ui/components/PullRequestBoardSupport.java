package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequest;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

final class PullRequestBoardSupport {
    private PullRequestBoardSupport() {}

    public static void registerSelectionClick(
            final JComponent component,
            final PullRequest request,
            final Consumer<PullRequest> onSelect) {
        addSelectionListener(component, request, onSelect);
        attachSelectionListeners(component, request, onSelect);
    }

    private static void attachSelectionListeners(
            final Container parent,
            final PullRequest request,
            final Consumer<PullRequest> onSelect) {
        for (final Component child : parent.getComponents()) {
            if (child instanceof JComponent childComponent && !(child instanceof AbstractButton)) {
                addSelectionListener(childComponent, request, onSelect);
            }
            if (child instanceof Container container) {
                attachSelectionListeners(container, request, onSelect);
            }
        }
    }

    private static void addSelectionListener(
            final JComponent component,
            final PullRequest request,
            final Consumer<PullRequest> onSelect) {
        component.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(final MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event)) {
                            onSelect.accept(request);
                        }
                    }
                });
    }

    public static void loadSelectedDetails(
            final PullRequest selectedRequest, final Consumer<PullRequest> ensureDetailsLoaded) {
        if (selectedRequest == null) {
            return;
        }
        ensureDetailsLoaded.accept(selectedRequest);
    }

    public static void loadVisibleDetails(
            final JPanel list,
            final JScrollPane listScroll,
            final Consumer<PullRequest> ensureDetailsLoaded) {
        final Rectangle visible = listScroll.getViewport().getViewRect();
        for (final Component component : list.getComponents()) {
            if (!(component instanceof JPanel row)) {
                continue;
            }
            final Object value = row.getClientProperty("pullRequest");
            if (!(value instanceof PullRequest request)) {
                continue;
            }
            if (row.getBounds().intersects(visible)) {
                ensureDetailsLoaded.accept(request);
            }
        }
    }

    public static boolean isVisible(
            final PullRequest request, final JPanel list, final JScrollPane listScroll) {
        final Rectangle visible = listScroll.getViewport().getViewRect();
        for (final Component component : list.getComponents()) {
            if (!(component instanceof JPanel row)) {
                continue;
            }
            if (!(row.getClientProperty("pullRequest") instanceof PullRequest rowRequest)) {
                continue;
            }
            if (rowRequest.equals(request)) {
                return row.getBounds().intersects(visible);
            }
        }
        return false;
    }

    public static void startRefreshAnimation(
            final RotatingIcon refreshIcon, final Timer refreshAnimation) {
        refreshIcon.reset();
        refreshAnimation.start();
    }

    public static void stopRefreshAnimation(
            final RotatingIcon refreshIcon,
            final Timer refreshAnimation,
            final JButton refreshButton) {
        refreshAnimation.stop();
        refreshIcon.reset();
        refreshButton.repaint();
    }
}
