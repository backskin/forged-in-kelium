package kelium;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

import kelium.gui.replay2.Theme;

/**
 * СГЛАЖИВАНИЕ ТЕКСТА В ОКНАХ, а не только на поле.
 *
 * <p>Поле, планшеты и карты рисует наш код, и он включает сглаживание сам. А
 * подписи, кнопки и списки рисует Swing, и настройку он берёт из оформления —
 * то есть, по умолчанию, ИЗ СИСТЕМЫ. Выключено сглаживание шрифтов в Windows —
 * и весь текст приложения ступенчатый, хотя поле рядом гладкое. Дизайнер это и
 * увидел (11.09.2026: «сглаживание только на поле работает»).
 *
 * <p>Проверяем не намерение, а результат: рисуем подпись и считаем оттенки.
 * У несглаженных букв их два — краска и фон; у сглаженных края набирают
 * десятки полутонов.
 */
class СглаживаниеОконTest {

    @Test
    void подписиОконСглажены() {
        Theme.apply(true);
        assertNotEquals(RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
            UIManager.get(RenderingHints.KEY_TEXT_ANTIALIASING),
            "оформление обязано просить сглаживание текста, а не отключать его");

        JLabel подпись = new JLabel("Привет, Келемий 123");
        подпись.setSize(320, 48);
        BufferedImage im = new BufferedImage(320, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        подпись.paint(g);
        g.dispose();

        Set<Integer> оттенки = new HashSet<>();
        for (int y = 0; y < im.getHeight(); y++) {
            for (int x = 0; x < im.getWidth(); x++) {
                оттенки.add(im.getRGB(x, y));
            }
        }
        assertTrue(оттенки.size() > 8,
            "текст подписи не сглажен: всего " + оттенки.size()
                + " оттенков, а у сглаженного их десятки");
    }
}
