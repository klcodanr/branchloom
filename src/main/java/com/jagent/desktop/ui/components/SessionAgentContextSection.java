package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AgentContext;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** Optional agent-context section for a session summary. */
public final class SessionAgentContextSection extends JPanel {
    private final SessionContextPanel context;
    private final transient Project project;
    private final transient Session session;
    private final String globalContextPath;
    private final transient GitHub gitHub;
    private Consumer<Boolean> visibilityChanged = ignored -> {};

    public SessionAgentContextSection(
            final Project project,
            final Session session,
            final String globalContextPath,
            final GitHub gitHub) {
        super(new BorderLayout(UiConstants.SPACING_MD, 0));
        this.project = project;
        this.session = session;
        this.globalContextPath = globalContextPath;
        this.gitHub = gitHub;
        setOpaque(false);
        context = new SessionContextPanel(this::save);
        final JLabel title = new JLabel("Agent context");
        title.setFont(Theme.font(Theme.FontSize.SM));
        title.setPreferredSize(
                new java.awt.Dimension(
                        SessionSummary.LABEL_WIDTH, title.getPreferredSize().height));
        add(title, BorderLayout.WEST);
        add(context.contentView(), BorderLayout.CENTER);
    }

    public void onVisibilityChanged(final Consumer<Boolean> listener) {
        visibilityChanged = listener;
    }

    public void load() {
        context.showLoading();
        BackgroundOperations.submit(
                        "Session summary",
                        "session-agent-context",
                        () -> {
                            final Path path =
                                    AgentContext.path(project, session, globalContextPath);
                            final String githubUser =
                                    path != null && Files.exists(path) ? null : githubUser();
                            return AgentContext.read(
                                    project, session, globalContextPath, githubUser);
                        })
                .thenAccept(
                        value ->
                                show(AgentContext.path(project, session, globalContextPath), value))
                .exceptionally(
                        failure -> {
                            show(
                                    AgentContext.path(project, session, globalContextPath),
                                    "Unavailable: " + ErrorMessages.deepestCause(failure, ""));
                            return null;
                        });
    }

    public JTextArea content() {
        return context.content();
    }

    public void showLoading() {
        context.showLoading();
    }

    public void show(final Path path, final String value) {
        context.show(path, value);
        visibilityChanged.accept(path != null);
    }

    public boolean configured(final Path path) {
        return path != null;
    }

    private void save() {
        final String content = context.content().getText();
        BackgroundOperations.submit(
                        "Session summary",
                        "save-session-agent-context",
                        () -> {
                            AgentContext.save(project, session, globalContextPath, content);
                            return null;
                        })
                .exceptionally(
                        failure -> {
                            javax.swing.JOptionPane.showMessageDialog(
                                    this,
                                    "Could not save agent context: "
                                            + ErrorMessages.deepestCause(failure, ""),
                                    "Agent context",
                                    javax.swing.JOptionPane.ERROR_MESSAGE);
                            return null;
                        });
    }

    private String githubUser() {
        try {
            return gitHub.getLogin();
        } catch (IOException exception) {
            return null;
        }
    }
}
