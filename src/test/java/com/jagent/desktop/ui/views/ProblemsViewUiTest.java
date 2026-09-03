package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.LogEntry;
import com.jagent.desktop.test.SwingTestSupport;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ProblemsViewUiTest {
    @Test
    void tableShowsProblemsNewestFirst() throws InterruptedException {
        final var first = new LogEntry("first-source", "warning", "first message");
        final var second = new LogEntry("second-source", "error", "second message");
        final var view =
                GuiActionRunner.execute(() -> new ProblemsView(() -> List.of(first, second)));
        waitForRows(view, 2);

        final var table = table(view);
        assertEquals(2, table.getRowCount(), "loaded problems should be displayed");
        assertEquals(
                "second-source", table.getValueAt(0, 2), "newest problem source should be first");
        assertEquals(
                "second message", table.getValueAt(0, 3), "newest problem message should be first");
        assertEquals(
                "first-source", table.getValueAt(1, 2), "oldest problem source should be second");
        assertEquals(
                "first message", table.getValueAt(1, 3), "oldest problem message should be second");
    }

    @Test
    void clearButtonRemovesDisplayedProblemsAndShowAllStartsEnabled() throws InterruptedException {
        final var view =
                GuiActionRunner.execute(
                        () ->
                                new ProblemsView(
                                        () -> List.of(new LogEntry("source", "error", "message"))));
        waitForRows(view, 1);

        final var showAll = SwingTestSupport.findButton(view, "Show all logs");
        final var clear = SwingTestSupport.findButton(view, "Clear");
        assertTrue(showAll != null, "show-all button should exist");
        assertTrue(clear != null, "clear button should exist");
        assertTrue(showAll.isEnabled(), "show-all should initially be enabled");

        GuiActionRunner.execute((GuiActionRunnable) clear::doClick);

        assertEquals(0, table(view).getRowCount(), "clear should remove displayed problems");
    }

    @Test
    void tableFillsAvailableViewHeight() {
        final var view = GuiActionRunner.execute(() -> new ProblemsView(List::of));

        GuiActionRunner.execute(
                () -> {
                    view.setSize(new Dimension(800, 600));
                    view.validate();
                    view.doLayout();
                });

        final var scroll = SwingTestSupport.find(view, JScrollPane.class);
        assertTrue(scroll != null, "problem table should be inside a scroll pane");
        assertTrue(
                scroll.getHeight() > 260,
                "problem table should expand beyond its preferred height: " + scroll.getHeight());
        assertTrue(table(view).getFillsViewportHeight(), "table should fill the viewport height");
    }

    private static JTable table(final ProblemsView view) {
        return view.problemTable();
    }

    private static void waitForRows(final ProblemsView view, final int rows)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (table(view).getRowCount() == rows) {
                GuiActionRunner.execute(() -> {});
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("problem rows did not load");
    }
}
