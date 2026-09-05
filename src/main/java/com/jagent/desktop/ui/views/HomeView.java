package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import com.jagent.desktop.ui.components.UiIcons;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Duration;
import java.time.Instant;
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

    public HomeView(final ActionContext actionContext) {
        super(new BorderLayout());
        this.actionContext = actionContext;
        this.appState = actionContext.appState();
        final JScrollPane scrollPane = new JScrollPane(body());
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
        body.add(cards);
        body.add(Box.createVerticalGlue());
        return body;
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
        final JButton addProject = UiFactory.button(create.label(), UiIcons.plus());
        addProject.addActionListener(event -> create.execute());
        actions.add(addProject);
        final var importProject = new ImportProjectAction(actionContext);
        final JButton importButton = UiFactory.button(importProject.label());
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
        final var heading = UiFactory.label(title, Theme.FontSize.LG);
        heading.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(heading, BorderLayout.NORTH);
        return card;
    }

    private JPanel centered(final java.awt.Component component) {
        final JPanel content = new JPanel(new FlowLayout(FlowLayout.CENTER));
        content.setOpaque(false);
        content.add(component);
        return content;
    }

    private void openSession(final SessionId sessionId, final Session session) {
        actionContext.appState().updateCurrentProject(session.projectId());
        actionContext.appState().updateCurrentSession(sessionId);
        actionContext
                .viewCoordinator()
                .updateView(
                        ViewId.SESSION,
                        com.jagent.desktop.services.ViewCoordinator.ViewState.session(
                                session.projectId(), sessionId));
    }

    private String relativeTime(final Instant created) {
        final long seconds = Math.max(0, Duration.between(created, Instant.now()).toSeconds());
        if (seconds < 60) {
            return "Just now";
        }
        final long minutes = seconds / 60;
        if (minutes < 60) {
            return elapsed(minutes, "minute");
        }
        final long hours = minutes / 60;
        if (hours < 24) {
            return elapsed(hours, "hour");
        }
        return elapsed(hours / 24, "day");
    }

    private String elapsed(final long amount, final String unit) {
        return amount + " " + unit + (amount == 1 ? "" : "s") + " ago";
    }

    private record SessionOption(SessionId sessionId, Session session) {}

    private final class SessionRenderer extends JPanel implements ListCellRenderer<SessionOption> {
        private final JLabel name = new JLabel();
        private final JLabel age = UiFactory.label("", Theme.FontSize.SM);

        private SessionRenderer() {
            super(new BorderLayout(0, 2));
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
            age.setText(placeholder ? "" : relativeTime(value.session().created()));
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            name.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            age.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }
}
