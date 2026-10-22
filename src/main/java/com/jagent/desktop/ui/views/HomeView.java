package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import com.jagent.desktop.ui.components.BaseButton;
import com.jagent.desktop.ui.components.SessionMonitoringPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.UiText;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Comparator;
import java.util.Map.Entry;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

/** Home dashboard for continuing an existing session or adding a project. */
public final class HomeView extends JPanel implements View {
    private final transient ActionContext actionContext;
    private final transient AppState appState;
    private final JScrollPane scrollPane;

    public HomeView(final ActionContext actionContext) {
        super(new BorderLayout());
        this.actionContext = actionContext;
        this.appState = actionContext.appState();
        scrollPane = new JScrollPane(body());
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    public ViewId id() {
        return ViewId.HOME;
    }

    @Override
    public String title() {
        return "Home";
    }

    @Override
    public JPanel render() {
        return this;
    }

    @Override
    public void detach() {}

    public void refresh() {
        scrollPane.setViewportView(body());
        revalidate();
        repaint();
    }

    private JPanel body() {
        final JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(Box.createVerticalGlue());
        final JPanel cards =
                new JPanel(new FlowLayout(FlowLayout.CENTER, UiConstants.SECTION_PADDING, 0));
        cards.setOpaque(false);
        if (!appState.sessions().isEmpty()) {
            cards.add(continueCard());
        }
        cards.add(getStartedCard());
        cards.setAlignmentX(CENTER_ALIGNMENT);
        cards.setMaximumSize(cards.getPreferredSize());
        final JPanel dashboard = new JPanel();
        dashboard.setOpaque(false);
        dashboard.setLayout(new BoxLayout(dashboard, BoxLayout.Y_AXIS));
        dashboard.setAlignmentX(CENTER_ALIGNMENT);
        dashboard.add(cards);
        if (!appState.sessions().isEmpty()) {
            dashboard.add(Box.createVerticalStrut(UiConstants.SECTION_PADDING));
            final JPanel monitoring = monitoringTable();
            monitoring.setPreferredSize(
                    new Dimension(
                            cards.getPreferredSize().width, monitoring.getPreferredSize().height));
            monitoring.setMaximumSize(
                    new Dimension(
                            cards.getPreferredSize().width, monitoring.getPreferredSize().height));
            monitoring.setAlignmentX(CENTER_ALIGNMENT);
            dashboard.add(monitoring);
        }
        body.add(dashboard);
        body.add(Box.createVerticalGlue());
        return body;
    }

    private JPanel monitoringTable() {
        return new SessionMonitoringPanel(actionContext);
    }

    private JPanel continueCard() {
        final JPanel card = card("Continue where you left off");
        final JComboBox<SessionOption> sessions = new JComboBox<>();
        sessions.addItem(null);
        appState.sessions().entrySet().stream()
                .sorted(
                        Comparator.comparing(
                                        (Entry<SessionId, Session> entry) ->
                                                entry.getValue().created())
                                .reversed())
                .map(entry -> new SessionOption(entry.getKey(), entry.getValue()))
                .forEach(sessions::addItem);
        sessions.setRenderer(new SessionRenderer());
        sessions.setPreferredSize(new Dimension(300, 48));
        sessions.addActionListener(
                event -> {
                    final SessionOption selected = (SessionOption) sessions.getSelectedItem();
                    if (selected != null) {
                        openSession(selected.sessionId(), selected.session());
                    }
                });
        card.add(centered(sessions), BorderLayout.CENTER);
        return card;
    }

    private JPanel getStartedCard() {
        final JPanel card = card("Get started");
        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER));
        final var create = new CreateProjectAction(actionContext);
        final JButton addProject = new BaseButton(create.label(), UiIcons.plus());
        addProject.addActionListener(event -> create.execute());
        actions.add(addProject);
        final var importProject = new ImportProjectAction(actionContext);
        final JButton importButton = new BaseButton(importProject.label());
        importButton.addActionListener(event -> importProject.execute());
        actions.add(importButton);
        card.add(actions, BorderLayout.CENTER);
        return card;
    }

    private JPanel card(final String title) {
        final JPanel card = new JPanel(new BorderLayout(0, UiConstants.COMPONENT_GAP));
        card.setBorder(
                Theme.sectionBorder(
                        UiConstants.CARD_PADDING,
                        UiConstants.CARD_PADDING,
                        UiConstants.CARD_PADDING,
                        UiConstants.CARD_PADDING));
        card.setAlignmentX(CENTER_ALIGNMENT);
        card.setPreferredSize(new Dimension(360, 132));
        final var heading = new JLabel(title);
        heading.setFont(Theme.font(Theme.FontSize.LG));
        heading.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(heading, BorderLayout.NORTH);
        return card;
    }

    private JPanel centered(final Component component) {
        final JPanel content = new JPanel(new FlowLayout(FlowLayout.CENTER));
        content.setOpaque(false);
        content.add(component);
        return content;
    }

    private void openSession(final SessionId sessionId, final Session session) {
        actionContext.viewController().openSession(session.projectId(), sessionId);
    }

    private record SessionOption(SessionId sessionId, Session session) {}

    private static final class SessionRenderer extends JPanel
            implements ListCellRenderer<SessionOption> {
        private final JLabel name = new JLabel();
        private final JLabel age = new JLabel();

        private SessionRenderer() {
            super(new BorderLayout(0, 2));
            age.setFont(Theme.font(Theme.FontSize.SM));
            setOpaque(true);
            add(name, BorderLayout.CENTER);
            add(age, BorderLayout.SOUTH);
        }

        @Override
        public JPanel getListCellRendererComponent(
                final JList<? extends SessionOption> list,
                final SessionOption value,
                final int index,
                final boolean selected,
                final boolean focused) {
            final boolean placeholder = value == null;
            name.setText(placeholder ? "Select a session" : value.session().name());
            name.setIcon(placeholder ? UiIcons.hatGlasses() : null);
            age.setText(placeholder ? "" : UiText.relativeTime(value.session().created()));
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            name.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            age.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }
}
