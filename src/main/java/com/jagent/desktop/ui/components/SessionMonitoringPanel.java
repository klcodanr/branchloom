package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** Displays the current pull-request status for all sessions. */
public final class SessionMonitoringPanel extends JPanel {
    private static final int VISIBLE_ROWS = 10;
    private final transient AppState appState;
    private final transient ActionContext actionContext;
    private final List<SessionId> sessionIds = new ArrayList<>();
    private final JTable table;

    public SessionMonitoringPanel(final ActionContext actionContext) {
        super(new BorderLayout(0, UiConstants.COMPONENT_GAP));
        this.actionContext = actionContext;
        this.appState = actionContext.appState();
        setOpaque(false);

        final DefaultTableModel model =
                new DefaultTableModel(
                        new Object[] {"Session", "Pull request", "Status", "Last status change"},
                        0);
        appState.sessions().entrySet().stream()
                .sorted(
                        Comparator.comparing(
                                        (Entry<SessionId, Session> entry) ->
                                                projectName(entry.getValue()))
                                .thenComparing(
                                        entry -> entry.getValue().name(),
                                        String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> addRow(model, entry.getKey(), entry.getValue()));

        table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.getColumnModel().getColumn(0).setPreferredWidth(420);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(125);
        table.getColumnModel().getColumn(3).setPreferredWidth(135);
        final var defaultCellRenderer =
                (JComponent)
                        table.getDefaultRenderer(Object.class)
                                .getTableCellRendererComponent(table, "", false, false, 0, 0);
        final var cellBorder = defaultCellRenderer.getBorder();
        table.getColumnModel()
                .getColumn(2)
                .setCellRenderer(
                        (ignored, value, selected, focused, row, column) -> {
                            final JPanel renderer =
                                    new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                            final JLabel label = new JLabel(value == null ? "" : value.toString());
                            final int modelRow = table.convertRowIndexToModel(row);
                            final PullRequestDetails monitoring =
                                    appState.monitoringStatuses().get(sessionIds.get(modelRow));
                            renderer.setOpaque(true);
                            renderer.setBorder(cellBorder);
                            renderer.setBackground(
                                    selected
                                            ? table.getSelectionBackground()
                                            : table.getBackground());
                            label.setForeground(
                                    selected
                                            ? table.getSelectionForeground()
                                            : table.getForeground());
                            final StatusDot dot = new StatusDot(null);
                            dot.update(
                                    monitoring == null ? null : monitoring.indicatorColor(),
                                    label.getText());
                            renderer.add(dot);
                            renderer.add(label);
                            return renderer;
                        });
        final MouseAdapter mouse =
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent event) {
                        openTarget(event);
                    }

                    @Override
                    public void mouseMoved(final MouseEvent event) {
                        table.setCursor(
                                table.columnAtPoint(event.getPoint()) < 2
                                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                        : Cursor.getDefaultCursor());
                    }
                };
        table.addMouseListener(mouse);
        table.addMouseMotionListener(mouse);
        table.setPreferredScrollableViewportSize(
                new Dimension(
                        0, table.getRowHeight() * Math.min(VISIBLE_ROWS, model.getRowCount())));
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    private void addRow(final DefaultTableModel model, final SessionId id, final Session session) {
        sessionIds.add(id);
        final PullRequestDetails monitoring = appState.monitoringStatuses().get(id);
        model.addRow(
                new Object[] {
                    projectName(session) + " > " + session.name(),
                    monitoring == null ? "" : "#" + monitoring.number(),
                    monitoring == null ? "" : UiText.titleCase(monitoring.status().toString()),
                    monitoring == null ? "" : UiText.relativeTime(monitoring.updatedAt())
                });
    }

    private void openTarget(final MouseEvent event) {
        if (event.getClickCount() != 1) {
            return;
        }
        final int row = table.rowAtPoint(event.getPoint());
        final int column = table.columnAtPoint(event.getPoint());
        if (row < 0 || column < 0) {
            return;
        }
        final SessionId id = sessionIds.get(table.convertRowIndexToModel(row));
        if (column == 0) {
            final Session session = appState.sessions().get(id);
            if (session != null) {
                actionContext.appState().updateCurrentProject(session.projectId());
                actionContext.appState().updateCurrentSession(id);
                actionContext
                        .viewCoordinator()
                        .updateView(ViewId.SESSION, ViewState.session(session.projectId(), id));
            }
        } else if (column == 1) {
            final PullRequestDetails monitoring = appState.monitoringStatuses().get(id);
            if (monitoring == null) {
                return;
            }
            final var url = monitoring.url();
            if (url == null) {
                return;
            }
            PlatformCommands.openUrl(url.toExternalForm());
        }
    }

    private String projectName(final Session session) {
        final var project = appState.projects().get(session.projectId());
        return project == null ? "Unknown project" : project.name();
    }
}
