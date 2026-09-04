package com.jagent.desktop.ui.views;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.LogEntry;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.JsonLogging;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

public final class ProblemsView extends JPanel implements View {
    private static final String TITLE = "Problems";
    private static final int INITIAL_PROBLEM_LIMIT = 500;
    private static final Gson PRETTY_JSON = new GsonBuilder().setPrettyPrinting().create();
    private final transient List<LogEntry> problems;
    private final ProblemTableModel tableModel;
    private final JTable table;
    private JButton showAllButton;

    public ProblemsView() {
        this(() -> JsonLogging.load(INITIAL_PROBLEM_LIMIT));
    }

    protected ProblemsView(final Supplier<List<LogEntry>> problemLoader) {
        super();
        this.problems = new ArrayList<>();
        setLayout(new BorderLayout(0, UiConstants.SECTION_PADDING));
        add(header(), BorderLayout.NORTH);
        tableModel = new ProblemTableModel(problems);
        table = new JTable(tableModel);
        table.setRowHeight(30);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);
        table.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent event) {
                        if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                            showProblemDetails(table.rowAtPoint(event.getPoint()));
                        }
                    }
                });
        table.getColumnModel().getColumn(0).setPreferredWidth(125);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(600);
        final JScrollPane scroll = new JScrollPane(table);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(0, 260));
        add(scroll, BorderLayout.CENTER);
        refresh();
        BackgroundTasks.submit(
                TITLE,
                "load-log",
                () -> {
                    final List<LogEntry> loaded = problemLoader.get();
                    SwingUtilities.invokeLater(
                            () -> {
                                problems.addAll(loaded);
                                refresh();
                            });
                });
    }

    @Override
    public ViewId id() {
        return ViewId.PROBLEMS;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public JPanel render() {
        refresh();
        return this;
    }

    public void refresh() {
        tableModel.fireTableDataChanged();
        table.clearSelection();
    }

    protected JTable problemTable() {
        return table;
    }

    private void showProblemDetails(final int row) {
        if (row < 0 || row >= problems.size()) {
            return;
        }
        final LogEntry problem = problems.get(problems.size() - row - 1);
        final JTextArea details =
                UiFactory.selectableText(PRETTY_JSON.toJson(logEntry(problem)), Theme.FontSize.SM);
        details.setRows(20);
        details.setColumns(80);
        details.setCaretPosition(0);
        details.setLineWrap(false);
        JOptionPane.showMessageDialog(
                this, new JScrollPane(details), "Log Entry", JOptionPane.INFORMATION_MESSAGE);
    }

    private static Map<String, Object> logEntry(final LogEntry problem) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", problem.timestamp());
        entry.put("level", problem.level());
        entry.put("source", problem.source());
        entry.put("message", problem.message());
        if (problem.data() != null && !problem.data().isEmpty()) {
            entry.put("data", problem.data());
        }
        if (problem.exception() != null) {
            entry.put("exception", problem.exception());
        }
        return entry;
    }

    private JPanel header() {
        final JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        final JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        title.add(UiFactory.label(TITLE, Theme.FontSize.XXL));
        title.add(UiFactory.label("Application events and failures", Theme.FontSize.MD));
        header.add(title, BorderLayout.WEST);
        final JPanel actions =
                new JPanel(new FlowLayout(FlowLayout.RIGHT, UiConstants.CONTENT_PADDING, 0));
        actions.setOpaque(false);
        showAllButton = UiFactory.button("Show all logs");
        showAllButton.addActionListener(event -> loadAllLogs());
        actions.add(showAllButton);
        final JButton clearButton = UiFactory.button("Clear");
        clearButton.addActionListener(
                e -> {
                    problems.clear();
                    JsonLogging.clear();
                    refresh();
                });
        actions.add(clearButton);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private void loadAllLogs() {
        showAllButton.setEnabled(false);
        BackgroundTasks.submit(
                TITLE,
                "load-all-log",
                () -> {
                    final List<LogEntry> loaded = JsonLogging.load();
                    SwingUtilities.invokeLater(
                            () -> {
                                problems.clear();
                                problems.addAll(loaded);
                                showAllButton.setVisible(false);
                                refresh();
                            });
                });
    }

    private static final class ProblemTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Time", "Severity", "Source", "Message"};
        private final List<LogEntry> problems;

        private ProblemTableModel(final List<LogEntry> problems) {
            super();
            this.problems = problems;
        }

        @Override
        public int getRowCount() {
            return problems.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(final int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(final int row, final int column) {
            final LogEntry problem = problems.get(problems.size() - row - 1);
            return switch (column) {
                case 0 -> problem.timestamp();
                case 1 -> problem.level();
                case 2 -> problem.source();
                case 3 -> problem.message();
                default -> "";
            };
        }
    }
}
