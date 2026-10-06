package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.github.Issue;
import com.jagent.desktop.ui.components.AgentSelector;
import com.jagent.desktop.ui.components.FormPanel;
import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

public final class BulkSessionDialog extends FormDialog {
    private final transient Consumer<Request> onValid;
    private final JList<Issue> issues;
    private final JComboBox<Agent> agent;

    public record Request(List<Issue> issues, Agent agent) {}

    public BulkSessionDialog(
            final ActionContext actionContext,
            final List<Issue> availableIssues,
            final Consumer<Request> onValid) {
        super(actionContext.window(), "Bulk agent sessions");
        this.onValid = onValid;
        issues = new JList<>(availableIssues.toArray(new Issue[0]));
        issues.setName("bulk-issues");
        issues.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        issues.setCellRenderer(new IssueRenderer());
        issues.setVisibleRowCount(Math.min(12, Math.max(4, availableIssues.size())));
        agent = new AgentSelector("bulk-agent", actionContext.appState().appSettings().agents());
        initialize(
                new FormPanel("GitHub issues", new JScrollPane(issues), "Agent", agent),
                "Create sessions",
                "bulk-cancel",
                "bulk-create",
                this::submit,
                actionContext.window());
    }

    /* package */
    static Request request(final List<Issue> selected, final Agent agent) {
        return new Request(List.copyOf(selected), agent);
    }

    /* package */
    static boolean hasSelection(final List<Issue> selected) {
        return !selected.isEmpty();
    }

    private void submit() {
        final List<Issue> selected = issues.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select at least one issue.",
                    "Bulk agent sessions",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        dispose();
        onValid.accept(request(selected, (Agent) agent.getSelectedItem()));
    }

    private static final class IssueRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                final JList<?> list,
                final Object value,
                final int index,
                final boolean selected,
                final boolean focused) {
            final Issue issue = (Issue) value;
            return super.getListCellRendererComponent(
                    list, "#" + issue.number() + " " + issue.title(), index, selected, focused);
        }
    }
}
