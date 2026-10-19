package com.jagent.desktop.ui.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.board.BasicBoardItem;
import com.jagent.desktop.models.board.BoardItem;
import com.jagent.desktop.models.board.BoardSource;
import java.awt.Component;
import java.awt.Container;
import java.util.Date;
import javax.swing.JLabel;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class BoardCardTest {
    @Test
    void rendersItemDetails() {
        final BoardItem item =
                new BasicBoardItem(
                        BoardSource.JIRA,
                        "JRA-1",
                        "Fix issue",
                        "Needs attention",
                        new Date(),
                        new Date(),
                        null,
                        "project");

        final BoardCard card = GuiActionRunner.execute(() -> new BoardCard(item));

        assertEquals("Fix issue", namedLabel(card, "title").getText(), "title should render");
        assertTrue(card.isOpaque(), "cards should use the standard panel background");
    }

    private static JLabel namedLabel(final Container container, final String name) {
        return (JLabel) namedComponent(container, name);
    }

    private static Component namedComponent(final Container container, final String name) {
        for (final Component component : container.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
            if (component instanceof Container child) {
                final Component result = findNamedComponent(child, name);
                if (result != null) {
                    return result;
                }
            }
        }
        throw new AssertionError("missing component: " + name);
    }

    private static Component findNamedComponent(final Container container, final String name) {
        for (final Component component : container.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
            if (component instanceof Container child) {
                final Component result = findNamedComponent(child, name);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
