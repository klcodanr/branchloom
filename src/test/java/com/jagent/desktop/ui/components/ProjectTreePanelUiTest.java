package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ProjectTreePanelUiTest {
    @Test
    void projectTreeIsVerticallyScrollable() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = TestAppState.controller(state);
        final var panel =
                GuiActionRunner.execute(
                        () ->
                                new ProjectTreePanel(
                                        coordinator,
                                        state,
                                        TestAppState.dialogService(),
                                        TestAppState.sessionActions(state),
                                        TestAppState.projectActions(state)));

        final var treeContent = (JPanel) panel.getComponent(1);
        final var scroll = (JScrollPane) treeContent.getComponent(1);

        assertInstanceOf(JTree.class, scroll.getViewport().getView());
        assertEquals(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                scroll.getVerticalScrollBarPolicy(),
                "the tree should show a vertical scrollbar when its contents are too tall");
        assertEquals(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                scroll.getHorizontalScrollBarPolicy(),
                "the tree should not scroll horizontally");
    }
}
