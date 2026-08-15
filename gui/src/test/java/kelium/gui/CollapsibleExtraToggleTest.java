package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

/**
 * ДВЕ СТЕПЕНИ СВОРАЧИВАНИЯ полоски (просьба дизайнера 12.08.2026: «сворачивать
 * двумя способами — полностью и чуть-чуть»).
 *
 * <p>Полностью — прежняя кнопка: содержимое прячется. Чуть-чуть — вторая кнопка:
 * содержимое остаётся, но владелец переключает его в компактный вид. Проверяем,
 * что вторая кнопка появляется, сообщает состояние наружу и меняет подпись, а
 * первая продолжает прятать содержимое.
 */
class CollapsibleExtraToggleTest {

    private static List<JButton> buttons(Container c) {
        List<JButton> out = new ArrayList<>();
        for (Component ch : c.getComponents()) {
            if (ch instanceof JButton b) {
                out.add(b);
            } else if (ch instanceof Container cc) {
                out.addAll(buttons(cc));
            }
        }
        return out;
    }

    @Test
    void extraToggleReportsCompactStateAndSwapsItsLabel() {
        JPanel content = new JPanel();
        content.add(new JLabel("содержимое"));
        Collapsible box = new Collapsible("показ", content);

        List<Boolean> reported = new ArrayList<>();
        box.addExtraToggle("сжать", "развернуть", "в одну полосу", reported::add);

        List<JButton> bs = buttons(box);
        assertEquals(2, bs.size(), "на полоске должны быть две кнопки: " + bs.size());
        JButton extra = bs.stream().filter(b -> "сжать".equals(b.getText()))
            .findFirst().orElse(null);
        assertTrue(extra != null, "кнопка «сжать» есть");
        assertFalse(box.isCompact(), "по умолчанию вид обычный");

        extra.doClick();
        assertEquals(List.of(true), reported, "нажатие сообщает «компактно»");
        assertTrue(box.isCompact(), "состояние запомнено");
        assertEquals("развернуть", extra.getText(), "подпись сменилась");

        extra.doClick();
        assertEquals(List.of(true, false), reported, "второе нажатие возвращает обычный вид");
        assertFalse(box.isCompact());
        assertEquals("сжать", extra.getText());
    }

    @Test
    void fullCollapseStillHidesContentAndKeepsTheBar() {
        JPanel content = new JPanel();
        content.add(new JLabel("содержимое"));
        Collapsible box = new Collapsible("показ", content);
        box.addExtraToggle("сжать", "развернуть", "в одну полосу", v -> { });

        assertTrue(content.isVisible(), "сначала содержимое видно");
        box.setOpen(false);
        assertFalse(content.isVisible(), "полное сворачивание прячет содержимое");
        assertTrue(box.getComponentCount() >= 1, "полоска заголовка остаётся");
        box.setOpen(true);
        assertTrue(content.isVisible(), "разворачивание возвращает содержимое");
    }
}
