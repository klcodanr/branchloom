package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ProblemEvent;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.JsonLogging;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

public final class ProblemsView extends JPanel implements View {
    private static final String TITLE = "Problems";
    private static final int INITIAL_PROBLEM_LIMIT = 500;
    private final transient List<ProblemEvent> problems;
    private final ProblemTableModel tableModel;
    private final JTable table;
    private JButton showAllButton;

    public ProblemsView() {
        super();
        this.problems = new ArrayList<>();
        setLayout(new BorderLayout(0, 18));
        add(header(), BorderLayout.NORTH);
        tableModel = new ProblemTableModel(problems);
        table = new JTable(tableModel);
        table.setRowHeight(30);
        table.setFillsViewportHeight(false);
        table.setAutoCreateRowSorter(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(125);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(600);
        final JScrollPane scroll = new JScrollPane(table);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(0, 260));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        final JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(scroll, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        refresh();
        BackgroundTasks.submit(
                TITLE,
                "load-log",
                () -> {
                    final List<ProblemEvent> loaded = JsonLogging.load(INITIAL_PROBLEM_LIMIT);
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

    private JPanel header() {
        final JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        final JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        title.add(UiFactory.label(TITLE, Theme.FontSize.XXL));
        title.add(UiFactory.label("Application events and failures", Theme.FontSize.MD));
        header.add(title, BorderLayout.WEST);
        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
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
                    final List<ProblemEvent> loaded = JsonLogging.load();
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
        private final List<ProblemEvent> problems;

        private ProblemTableModel(final List<ProblemEvent> problems) {
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
            final ProblemEvent problem = problems.get(problems.size() - row - 1);
            return switch (column) {
                case 0 -> problem.created;
                case 1 -> problem.severity;
                case 2 -> problem.source;
                case 3 -> problem.message;
                default -> "";
            };
        }
    }
}
