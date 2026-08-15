package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.junit.jupiter.api.Test;

/**
 * Сворачиваемые рамки проигрывателя (просьба дизайнера 12.08.2026): у всего,
 * кроме самого поля, должна быть кнопка «свернуть до полоски», а границы должны
 * тянуться мышкой.
 */
class CollapsibleTest {

    @Test
    void collapsingHidesContentAndShrinksToTheBar() {
        JLabel content = new JLabel("зона игрока");
        Collapsible box = new Collapsible("игрок 3", content);

        assertTrue(box.isOpen(), "по умолчанию рамка раскрыта");
        assertTrue(content.isVisible());

        box.setOpen(false);
        assertFalse(box.isOpen());
        assertFalse(content.isVisible(), "содержимое спрятано");
        assertTrue(box.getPreferredSize().height < 40,
            "свёрнутая рамка занимает одну полоску, а не всю высоту: "
                + box.getPreferredSize().height);

        box.setOpen(true);
        assertTrue(content.isVisible(), "содержимое вернулось");
    }

    @Test
    void collapsingInsideASplitterGivesTheSpaceToTheNeighbour() {
        JPanel other = new JPanel();
        JLabel content = new JLabel("лог");
        Collapsible box = new Collapsible("лог партии", content);

        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, other, box);
        sp.setSize(400, 300);
        sp.doLayout();
        sp.setDividerLocation(150);
        box.bindTo(sp, false);

        box.setOpen(false);
        assertTrue(sp.getDividerLocation() > 150,
            "разделитель ушёл вниз, отдав место соседу: " + sp.getDividerLocation());

        box.setOpen(true);
        assertEquals(150, sp.getDividerLocation(),
            "разворот вернул разделитель на прежнее место");
    }
}
