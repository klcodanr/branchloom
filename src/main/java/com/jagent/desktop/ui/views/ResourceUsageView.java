package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.TerminalResources;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Runtime reports for background threads and terminal processes. */
public final class ResourceUsageView extends JPanel implements View {
    private static final String UNAVAILABLE = "Unavailable";
    private final JLabel virtualThreads = metricValue();
    private final JLabel platformThreads = metricValue();
    private final JLabel executorStatus = metricValue();
    private final JLabel terminalCount = metricValue();
    private final JLabel terminalCpu = metricValue();
    private final JLabel terminalMemory = metricValue();
    private final DefaultTableModel threadModel =
            model("Group", "Active", "Submitted", "Completed");
    private final DefaultTableModel terminalModel =
            model("Terminal", "PID", "Processes", "CPU time", "Memory");
    private final DefaultTableModel activeTaskModel = model("Active task");
    private final JTable threadTable = table(threadModel);
    private final JTable terminalTable = table(terminalModel);
    private final JTable activeTaskTable = table(activeTaskModel);

    public ResourceUsageView() {
        this(true);
    }

    protected ResourceUsageView(final boolean loadResources) {
        super();
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(4, 4, 4, 4));

        final JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UiFactory.label("Resource Usage", Theme.FontSize.XXL), BorderLayout.WEST);
        final JButton refresh = UiFactory.button("Refresh");
        refresh.addActionListener(event -> refresh());
        header.add(refresh, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        final JPanel reports = new JPanel();
        reports.setOpaque(false);
        reports.setLayout(new BoxLayout(reports, BoxLayout.Y_AXIS));
        reports.add(threadsReport());
        reports.add(Box.createVerticalStrut(14));
        reports.add(terminalsReport());

        final JScrollPane scroll = new JScrollPane(reports);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        add(scroll, BorderLayout.CENTER);
        if (loadResources) {
            refresh();
        }
    }

    @Override
    public ViewId id() {
        return ViewId.RESOURCE_USAGE;
    }

    @Override
    public String title() {
        return "Resource Usage";
    }

    @Override
    public JPanel render() {
        refresh();
        return this;
    }

    private JPanel threadsReport() {
        final JPanel report = reportPanel("Threads");
        final JPanel metrics = metricsPanel();
        addMetric(metrics, "Live virtual threads", virtualThreads);
        addMetric(metrics, "Live platform threads", platformThreads);
        report.add(metrics, BorderLayout.NORTH);
        report.add(tableScroll(threadTable, 120), BorderLayout.CENTER);
        report.add(labeledTable("Active tasks", activeTaskTable, 80), BorderLayout.SOUTH);
        return report;
    }

    private JPanel terminalsReport() {
        final JPanel report = reportPanel("Terminals");
        final JPanel metrics = metricsPanel();
        addMetric(metrics, "Active terminals", terminalCount);
        addMetric(metrics, "CPU time", terminalCpu);
        addMetric(metrics, "Resident memory", terminalMemory);
        report.add(metrics, BorderLayout.NORTH);
        report.add(tableScroll(terminalTable, 180), BorderLayout.CENTER);
        return report;
    }

    private void refresh() {
        final BackgroundTasks.ThreadSummary summary = BackgroundTasks.summary();
        virtualThreads.setText(Long.toString(summary.virtualThreads()));
        platformThreads.setText(Long.toString(summary.platformThreads()));
        executorStatus.setText(summary.shutdown() ? "Stopped" : "Running");

        clear(threadModel);
        for (final BackgroundTasks.GroupSummary group : summary.groups()) {
            threadModel.addRow(
                    new Object[] {
                        group.group(), group.active(), group.submitted(), group.completed()
                    });
        }
        if (summary.groups().isEmpty()) {
            threadModel.addRow(new Object[] {"No background work has run yet", "", "", ""});
        }
        clear(activeTaskModel);
        if (summary.activeTasks().isEmpty()) {
            activeTaskModel.addRow(new Object[] {"None"});
        } else {
            summary.activeTasks().forEach(task -> activeTaskModel.addRow(new Object[] {task}));
        }

        terminalCount.setText("Loading...");
        terminalCpu.setText("Loading...");
        terminalMemory.setText("Loading...");
        clear(terminalModel);
        terminalModel.addRow(new Object[] {"Loading terminal resources...", "", "", "", ""});
        BackgroundTasks.submit(
                        "Monitoring",
                        "terminal-resources",
                        () ->
                                updateTerminals(
                                        TerminalResources.sample(
                                                TerminalManager.get().activeProcesses())))
                .exceptionally(
                        failure -> {
                            SwingUtilities.invokeLater(() -> showTerminalError(failure));
                            return null;
                        });
    }

    protected void updateTerminals(final TerminalResources.Sample sample) {
        SwingUtilities.invokeLater(
                () -> {
                    terminalCount.setText(Long.toString(sample.terminals().size()));
                    terminalCpu.setText(
                            formatDuration(
                                    sample.terminals().stream()
                                            .mapToLong(TerminalResources.Usage::cpuMillis)
                                            .sum()));
                    terminalMemory.setText(
                            sample.memoryAvailable()
                                    ? formatBytes(
                                            sample.terminals().stream()
                                                    .mapToLong(TerminalResources.Usage::memoryBytes)
                                                    .sum())
                                    : UNAVAILABLE);
                    clear(terminalModel);
                    if (sample.terminals().isEmpty()) {
                        terminalModel.addRow(new Object[] {"No active terminals", "", "", "", ""});
                    } else {
                        for (final TerminalResources.Usage terminal : sample.terminals()) {
                            terminalModel.addRow(
                                    new Object[] {
                                        terminal.name(),
                                        terminal.pid(),
                                        terminal.processCount(),
                                        formatDuration(terminal.cpuMillis()),
                                        sample.memoryAvailable()
                                                ? formatBytes(terminal.memoryBytes())
                                                : UNAVAILABLE
                                    });
                        }
                    }
                });
    }

    private void showTerminalError(final Throwable failure) {
        terminalCount.setText(UNAVAILABLE);
        terminalCpu.setText(UNAVAILABLE);
        terminalMemory.setText(UNAVAILABLE);
        clear(terminalModel);
        terminalModel.addRow(
                new Object[] {"Could not sample terminal resources: " + failure, "", "", "", ""});
    }

    private static JPanel reportPanel(final String title) {
        final JPanel report = UiFactory.panel();
        report.setAlignmentX(LEFT_ALIGNMENT);
        report.setLayout(new BorderLayout(0, 10));
        report.setBorder(BorderFactory.createTitledBorder(title));
        return report;
    }

    private static JPanel metricsPanel() {
        final JPanel metrics = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        metrics.setOpaque(false);
        return metrics;
    }

    private static JLabel metricValue() {
        final JLabel value = UiFactory.label("-", Theme.FontSize.XL);
        value.setFont(Theme.boldFont(Theme.FontSize.XL));
        return value;
    }

    private static void addMetric(final JPanel panel, final String label, final JLabel value) {
        final JPanel metric = new JPanel(new BorderLayout(0, 4));
        metric.setOpaque(false);
        metric.setPreferredSize(new Dimension(150, 58));
        metric.setMaximumSize(new Dimension(150, 58));
        metric.add(UiFactory.label(label, Theme.FontSize.SM), BorderLayout.NORTH);
        metric.add(value, BorderLayout.CENTER);
        panel.add(metric);
    }

    private static DefaultTableModel model(final String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable table(final DefaultTableModel model) {
        final JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setFont(Theme.font(Theme.FontSize.SM));
        table.setRowHeight(26);
        table.getTableHeader().setFont(Theme.boldFont(Theme.FontSize.SM));
        return table;
    }

    private static JScrollPane tableScroll(final JTable table, final int height) {
        final JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(0, height));
        scroll.setMinimumSize(new Dimension(0, height));
        return scroll;
    }

    private static JPanel labeledTable(final String title, final JTable table, final int height) {
        final JPanel group = new JPanel(new BorderLayout(0, 4));
        group.setOpaque(false);
        group.add(UiFactory.label(title, Theme.FontSize.SM), BorderLayout.NORTH);
        group.add(tableScroll(table, height), BorderLayout.CENTER);
        return group;
    }

    private static void clear(final DefaultTableModel model) {
        model.setRowCount(0);
    }

    private static String formatDuration(final long millis) {
        if (millis < 1_000) {
            return String.format("0m 00.%03ds", millis);
        }
        return String.format("%dm %02ds", millis / 60_000, millis / 1_000 % 60);
    }

    private static String formatBytes(final long bytes) {
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
