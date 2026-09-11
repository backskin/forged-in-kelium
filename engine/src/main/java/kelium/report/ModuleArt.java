package kelium.report;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * ModuleArt — ПЕЧАТНЫЕ ЖЕТОНЫ МОДУЛЕЙ: какая картинка у этого жетона.
 *
 * <p>Картинки лежат в {@code data/textures/module/} и названы ПО СОДЕРЖАНИЮ, а
 * не по номеру в наборе: {@code mod_red_infantry_vehicle},
 * {@code mod_blue_a2u1_units}, золотая сторона — тот же файл с хвостом
 * {@code _gold}. Имя собирается здесь из того, ЧТО жетон даёт, поэтому набор
 * может смениться (R1 → R30, C → C30), а картинки останутся на местах: важно не
 * как жетон зовётся, а что на нём напечатано.
 *
 * <p>ПОРЯДОК ЦЕЛЕЙ В ИМЕНИ ВСЕГДА ОДИН: пехота, техника, авиация,
 * здания-вышки. На самом жетоне пара может стоять как угодно — имя обязано быть
 * предсказуемым, иначе по паре целей его не собрать.
 *
 * <p>Картинки нет — методы возвращают {@code null}, и рисующий показывает жетон
 * прежним рисованным видом. Так и должно быть: у жетонов «+1 здоровье» и у
 * предложенных наборов печати ещё нет вовсе.
 */
public final class ModuleArt {

    private ModuleArt() {
    }

    /** Порядок целей в имени файла. */
    private static final List<String> ПОРЯДОК =
        List.of("infantry", "vehicle", "aircraft", "buildings_towers");

    /**
     * Имя картинки КРАСНОГО жетона по паре целей; {@code null} — пары нет
     * (жетон характеристики или особого эффекта, у него печати не заведено).
     */
    public static String redKey(List<String> targets, boolean gold) {
        if (targets == null || targets.size() != 2) {
            return null;
        }
        int a = ПОРЯДОК.indexOf(targets.get(0).toLowerCase(Locale.ROOT));
        int b = ПОРЯДОК.indexOf(targets.get(1).toLowerCase(Locale.ROOT));
        if (a < 0 || b < 0 || a == b) {
            return null;
        }
        return "mod_red_" + ПОРЯДОК.get(Math.min(a, b)) + "_" + ПОРЯДОК.get(Math.max(a, b))
            + (gold ? "_gold" : "");
    }

    /**
     * Имя картинки СИНЕГО жетона по числам Сборки.
     *
     * @param gild что поднимает золото: {@code units} | {@code ammo}
     */
    public static String blueKey(int ammo, int units, String gild, boolean gold) {
        if (ammo <= 0 || units <= 0 || gild == null) {
            return null;
        }
        String g = gild.toLowerCase(Locale.ROOT);
        if (!"units".equals(g) && !"ammo".equals(g)) {
            return null;
        }
        return "mod_blue_a" + ammo + "u" + units + "_" + g + (gold ? "_gold" : "");
    }

    /** Картинка красного жетона (или null). */
    public static BufferedImage red(List<String> targets, boolean gold) {
        String key = redKey(targets, gold);
        return key == null ? null : Textures.module(key);
    }

    /**
     * ЖЕТОН УНИЧТОЖЕННОГО ЦУ, лицом — он закрывает ячейку спец-атаки своего
     * рода войск. Оборот у него один на все рода: три звезды над руинами.
     *
     * @param unit код рода: {@code infantry} | {@code vehicle} | {@code aircraft}
     *             | {@code tower}; {@code null} — оборот (трофейная сторона)
     */
    public static BufferedImage cu(String unit) {
        if (unit == null || unit.isBlank()) {
            return Textures.module("mod_cu_trophy");
        }
        return Textures.module("mod_cu_" + unit.toLowerCase(Locale.ROOT));
    }

    /**
     * ЖЕТОН ХРАНИЛИЩА, той стороной, какой его положили.
     *
     * @param energy {@code true} — сторона «энергия», {@code false} — «склад»
     */
    public static BufferedImage store(boolean energy) {
        return Textures.module(energy ? "mod_store_energy" : "mod_store_cells");
    }

    /** Картинка синего жетона (или null). */
    public static BufferedImage blue(int ammo, int units, String gild, boolean gold) {
        String key = blueKey(ammo, units, gild, gold);
        return key == null ? null : Textures.module(key);
    }

    /**
     * ПОЛОЖИТЬ ЖЕТОН НА СТОЛ — с «блоком тени»: картонный жетон имеет толщину, и
     * под ним видна его же кромка, а не пустота (заказ дизайнера 07.09.2026 про
     * жетоны войск; модули из того же картона).
     *
     * <p>Кромка рисуется ТЕМ ЖЕ силуэтом, что и сам жетон, только смещённым и
     * залитым цветом рамки. Именно поэтому она берётся из скруглённого
     * прямоугольника, а не из тени-размытия: у картона край резкий.
     *
     * @param edge цвет кромки — цвет самого жетона (красный или синий)
     * @return {@code false}, если картинки нет и рисовать было нечего
     */
    public static boolean paint(Graphics2D g, BufferedImage art, Color edge,
                                double x, double y, double side) {
        return paint(g, art, edge, x, y, side, side, true);
    }

    /**
     * ТО ЖЕ, НО В ПРЯМОУГОЛЬНУЮ РАМКУ И БЕЗ ТОРЦА ПО ЖЕЛАНИЮ.
     *
     * <p>Рамки на планшете войск не квадратные: место Сборки вытянуто по высоте,
     * место спец-атаки почти квадратное. Вписывать жетон в квадрат и центровать
     * его в рамке значило класть его не туда — дизайнер видел это как «съехал
     * вправо» (11.09.2026).
     *
     * @param торец рисовать ли кромку картона снизу. На печатной рамке она
     *              мешает: паз нарисован, и толщина под жетоном читается как
     *              грязь (заказ того же дня: «убери у них эффект блочной тени
     *              вообще»)
     */
    public static boolean paint(Graphics2D g, BufferedImage art, Color edge,
                                double x, double y, double ширинаМеста,
                                double высотаМеста, boolean торец) {
        if (art == null || ширинаМеста <= 0 || высотаМеста <= 0) {
            return false;
        }
        // ПРОПОРЦИИ. Красные модули нарисованы квадратом, синие — вытянутым
        // прямоугольником (400x667). Растягивать синий жетон до квадрата
        // нельзя: на столе это картон ровно той формы, что нарисована. Поэтому
        // картинка ВПИСЫВАЕТСЯ в отведённый квадрат по большей стороне и
        // центрируется в нём.
        double доля = (double) art.getWidth() / art.getHeight();
        double ш = Math.min(ширинаМеста, высотаМеста * доля);
        double в = ш / доля;
        double лx = x + (ширинаМеста - ш) / 2;
        double лy = y + (высотаМеста - в) / 2;
        double lift = Math.max(1.0, Math.min(ш, в) * 0.055);
        double arc = Math.min(ш, в) * 0.20;
        if (edge != null && торец) {
            // ТОРЕЦ ПРОТЯНУТ, А НЕ СМЕЩЁН: силуэт заливается на всём пути от
            // жетона до конца сдвига — получается боковая стенка картонки.
            // Одна смещённая копия читается как плоская тень под плоской
            // картинкой (замечание дизайнера 09.09.2026).
            g.setColor(edge);
            ТеньЖетона.блок(g, new RoundRectangle2D.Double(лx, лy, ш, в, arc, arc),
                lift, lift);
        }
        BufferedImage lvl = Mips.forWidth(art, (int) Math.ceil(ш));
        g.drawImage(lvl, (int) Math.round(лx), (int) Math.round(лy),
            (int) Math.round(ш), (int) Math.round(в), null);
        return true;
    }
}
