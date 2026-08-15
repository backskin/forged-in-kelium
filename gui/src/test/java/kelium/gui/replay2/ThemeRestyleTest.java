package kelium.gui.replay2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * ПЕРЕКРАСКА ПРИ СМЕНЕ ТЕМЫ. Жалоба дизайнера 14.08.2026: часть экранов
 * (полоса прокрутки, карты супер-заданий и рынка) оставалась в теме, с которой
 * приложение запустили, и чинилась только перезапуском.
 *
 * <p>Причина: цвет, выставленный кодом, ЗАПЕКАЕТСЯ в компонент — оформление его
 * не трогает. Список «наших поверхностей» пополняли руками и забывали. Теперь
 * краска узнаётся по палитре и меняется на равную по смыслу, что бы её ни
 * выставило.
 */
class ThemeRestyleTest {

    @AfterEach
    void backToDark() {
        // Тема — состояние на весь процесс: возвращаем как было, иначе
        // соседние тесты получат чужие цвета.
        Theme.apply(true);
    }

    @Test
    void bakedColoursFollowTheTheme() {
        Theme.apply(true);
        JPanel root = new JPanel();
        root.setBackground(Theme.panel());
        JLabel text = new JLabel("подпись");
        text.setForeground(Theme.ink3());
        root.add(text);

        Theme.apply(false);
        int changed = Theme.restyleTree(root);

        assertTrue(changed >= 2, "перекрашены и фон, и текст: " + changed);
        assertEquals(Theme.panel().getRGB(), root.getBackground().getRGB(),
            "фон панели стал цветом светлой темы");
        assertEquals(Theme.ink3().getRGB(), text.getForeground().getRGB(),
            "тихий текст стал цветом светлой темы");
    }

    @Test
    void scrollbarInsideScrollPaneIsRepainted() {
        Theme.apply(true);
        JScrollPane sp = new JScrollPane(new JPanel());
        sp.getVerticalScrollBar().setBackground(Theme.panel());

        Theme.apply(false);
        Theme.restyleTree(sp);

        assertEquals(Theme.panel().getRGB(),
            sp.getVerticalScrollBar().getBackground().getRGB(),
            "полоса прокрутки — та самая, что оставалась в прежней теме");
    }

    @Test
    void coloursOutsideThePaletteAreLeftAlone() {
        Theme.apply(true);
        Color seat = Theme.seat(0);
        JPanel p = new JPanel();
        p.setBackground(seat);

        Theme.apply(false);
        Theme.restyleTree(p);

        assertEquals(seat.getRGB(), p.getBackground().getRGB(),
            "цвет места принадлежит игре, а не теме — трогать нельзя");
        assertNull(Theme.counterpart(seat), "такой краски в палитре темы нет");
    }
}
