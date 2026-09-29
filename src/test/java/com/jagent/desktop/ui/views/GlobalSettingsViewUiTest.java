package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.PullRequestFilter;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.Defaults;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class GlobalSettingsViewUiTest {
    private static final String ASSERTION_MESSAGE = "target view behavior should match";

    @Test
    void rendersConfiguredRowsAndSavesChanges() {
        final AppSettings settings =
                new AppSettings(
                        List.of(new Agent("Claude", "claude {prompt}", "claude")),
                        List.of("Approved"),
                        "Review {title}",
                        "System",
                        List.of(new Tool("Editor", "editor .")),
                        "{projectPath}/worktree",
                        ".branchloom/context.md");
        final AppState state = new AppState(settings, Map.of(), Map.of(), Map.of());
        final ViewCoordinator coordinator = new ViewCoordinator(state);
        final GlobalSettingsView view =
                new GlobalSettingsView(new ActionContext(coordinator, state, null));

        final JComponent rendered = GuiActionRunner.execute(view::render);
        final JScrollPane scroll = (JScrollPane) rendered.getComponent(0);
        final JPanel body = (JPanel) scroll.getViewport().getView();
        final JTabbedPane tabs = (JTabbedPane) body.getComponent(0);
        final JPanel filters = (JPanel) tabs.getComponentAt(4);
        final JButton addFilter = SwingTestSupport.findButton(filters, "+  Add filter");
        GuiActionRunner.execute(
                () -> {
                    addFilter.doClick();
                    final List<JTextField> filterFields = textFields(filters);
                    filterFields.get(filterFields.size() - 2).setText("Needs Attention");
                    filterFields
                            .get(filterFields.size() - 1)
                            .setText("review-requested:@me label:urgent");
                });

        final JPanel actions = (JPanel) rendered.getComponent(1);
        final JButton save = (JButton) actions.getComponent(1);
        GuiActionRunner.execute((GuiActionRunnable) save::doClick);

        assertEquals(ViewId.HOME, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(
                "{projectPath}/worktree",
                state.appSettings().worktreeTemplate(),
                ASSERTION_MESSAGE);
        assertEquals(
                ".branchloom/context.md",
                state.appSettings().agentContextPath(),
                ASSERTION_MESSAGE);
        assertEquals(
                List.of(
                        AppSettings.defaultPullRequestFilters().get(0),
                        AppSettings.defaultPullRequestFilters().get(1),
                        AppSettings.defaultPullRequestFilters().get(2),
                        new PullRequestFilter(
                                "Needs Attention", "review-requested:@me label:urgent")),
                state.appSettings().pullRequestFilters(),
                ASSERTION_MESSAGE);
        assertEquals(1, state.appSettings().agents().size(), ASSERTION_MESSAGE);
        assertEquals(1, state.appSettings().tools().size(), ASSERTION_MESSAGE);
    }

    @Test
    void cancelReturnsHomeWithoutChangingState() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ViewCoordinator coordinator = new ViewCoordinator(state);
        final GlobalSettingsView view =
                new GlobalSettingsView(new ActionContext(coordinator, state, null));
        final JComponent rendered = GuiActionRunner.execute(view::render);
        final JPanel actions = (JPanel) rendered.getComponent(1);

        GuiActionRunner.execute(() -> ((JButton) actions.getComponent(0)).doClick());

        assertEquals(ViewId.HOME, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(
                Defaults.DEFAULT_WORKTREE_TEMPLATE,
                state.appSettings().worktreeTemplate(),
                ASSERTION_MESSAGE);
    }

    private static List<JTextField> textFields(final java.awt.Container container) {
        final List<JTextField> fields = new ArrayList<>();
        collectTextFields(container, fields);
        return fields;
    }

    private static void collectTextFields(
            final java.awt.Container container, final List<JTextField> fields) {
        for (final java.awt.Component child : container.getComponents()) {
            if (child instanceof JTextField field) {
                fields.add(field);
            }
            if (child instanceof java.awt.Container nested) {
                collectTextFields(nested, fields);
            }
        }
    }
}
