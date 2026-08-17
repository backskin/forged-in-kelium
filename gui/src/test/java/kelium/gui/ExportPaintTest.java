package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import kelium.gui.LayoutEditor.LHex;
import kelium.gui.LayoutEditor.Model;
import kelium.gui.replay2.Theme;

/**
 * ЭКСПОРТ НИКОГДА НЕ БЫВАЕТ ТЁМНЫМ (баг дизайнера 17.08.2026).
 *
 * <p>Полотно конструктора рисует себя цветами {@code Theme}: в тёмной теме
 * темнеют и фон, и заливка гекса, и обводка. Ровно эти же методы зовёт выгрузка
 * картинки — и PNG уходил в печать чёрным, если пользователь работал в тёмной
 * теме. Проверяем то единственное, что здесь важно: картинка, снятая в тёмной
 * теме, ПОПИКСЕЛЬНО совпадает с картинкой, снятой в светлой.
 *
 * <p>Заодно проверяются два других требования того же заказа: фон кадра чуть
 * темнее бумаги (кадр читается как объект на листе) и слой гексовой сетки
 * рисуется за пределами поля.
 */
class ExportPaintTest {

    private static Model field() {
        Model m = new Model();
        for (int r = 0; r < 3; r++) {
            for (int q = 0; q < 4; q++) {
                m.hexes.put(Model.key(q, r), new LHex(q, r));
            }
        }
        m.get(0, 0).content = "player_start";
        m.get(0, 0).seat = 0;
        m.get(3, 2).content = "player_start";
        m.get(3, 2).seat = 1;
        m.get(1, 1).content = "kelium_tile";
        m.get(2, 0).containers = 2;
        m.get(2, 2).content = "forbidden";
        return m;
    }

    private static BufferedImage renderInTheme(boolean dark) {
        boolean was = Theme.isDark();
        try {
            Theme.apply(dark);
            LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
            canvas.model = field();
            return canvas.render(500, 360);
        } finally {
            Theme.apply(was);
        }
    }

    @Test
    void картинкаНеЗависитОтТемыИнтерфейса() {
        BufferedImage light = renderInTheme(false);
        BufferedImage dark = renderInTheme(true);
        assertEquals(light.getWidth(), dark.getWidth());
        assertEquals(light.getHeight(), dark.getHeight());
        int diff = 0;
        for (int y = 0; y < light.getHeight(); y++) {
            for (int x = 0; x < light.getWidth(); x++) {
                if (light.getRGB(x, y) != dark.getRGB(x, y)) {
                    diff++;
                }
            }
        }
        assertEquals(0, diff,
            "выгрузка в тёмной теме разошлась со светлой на " + diff + " пикселях — "
                + "экспорт обязан быть печатным, а не экранным");
    }

    @Test
    void фонКадраТемнееБумагиИСветлыйВЛюбойТеме() {
        // Угол кадра — заведомо вне поля, там должен лежать фон кадра.
        int corner = renderInTheme(true).getRGB(1, 1);
        assertEquals(ExportPaint.FIELD_BG.getRGB(), corner,
            "фон кадра обязан быть фоном экспорта, а не фоном холста");

        int bg = ExportPaint.FIELD_BG.getRed() + ExportPaint.FIELD_BG.getGreen()
            + ExportPaint.FIELD_BG.getBlue();
        int page = ExportPaint.PAGE.getRed() + ExportPaint.PAGE.getGreen()
            + ExportPaint.PAGE.getBlue();
        assertTrue(bg < page, "фон за полем обязан быть темнее бумаги страницы");
        assertTrue(bg > page * 0.85,
            "и всё же светлым: это оттенок бумаги, а не тёмная тема");
    }

    @Test
    void флагЭкспортаСнимаетсяДажеПриОшибке() {
        assertFalse(ExportPaint.active(), "до отрисовки флаг поднят быть не может");
        try {
            ExportPaint.with(() -> {
                throw new IllegalStateException("сбой отрисовки");
            });
        } catch (IllegalStateException expected) {
            // ровно этого и ждём
        }
        assertFalse(ExportPaint.active(),
            "флаг обязан сняться даже когда отрисовка упала — иначе интерфейс "
                + "навсегда остался бы в печатных красках");
    }

    @Test
    void сеткаРисуетсяЗаПределамиПоля() {
        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        canvas.model = field();
        BufferedImage grid = canvas.renderHexGridLayer(500, 360, 24, 250, 180);
        int painted = 0;
        for (int y = 0; y < grid.getHeight(); y += 2) {
            for (int x = 0; x < grid.getWidth(); x += 2) {
                if ((grid.getRGB(x, y) >>> 24) != 0) {
                    painted++;
                }
            }
        }
        assertTrue(painted > 0, "слой сетки оказался пустым");
    }

    @Test
    void слойКонтейнеровОтдельныйИВыключаемый() {
        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        canvas.model = field();
        BufferedImage withThem = canvas.renderContainersLayer(500, 360, 24, 250, 180);
        int painted = 0;
        for (int y = 0; y < withThem.getHeight(); y++) {
            for (int x = 0; x < withThem.getWidth(); x++) {
                if ((withThem.getRGB(x, y) >>> 24) != 0) {
                    painted++;
                }
            }
        }
        assertTrue(painted > 0, "на поле есть контейнеры — слой не может быть пустым");

        // А содержимое без контейнеров их не рисует: иначе выключатель ничего
        // не выключает, потому что они уже нарисованы соседним слоем.
        BufferedImage content = canvas.renderContentOnly(500, 360, 24, 250, 180, false);
        BufferedImage contentWith = canvas.renderContentOnly(500, 360, 24, 250, 180, true);
        int diff = 0;
        for (int y = 0; y < content.getHeight(); y++) {
            for (int x = 0; x < content.getWidth(); x++) {
                if (content.getRGB(x, y) != contentWith.getRGB(x, y)) {
                    diff++;
                }
            }
        }
        assertTrue(diff > 0, "выключатель контейнеров ничего не меняет");
    }
}
