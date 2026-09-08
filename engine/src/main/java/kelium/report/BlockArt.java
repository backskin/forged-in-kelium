package kelium.report;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * ПЕЧАТНЫЕ МОДУЛИ ПОЛЯ — картинки настоящего картона вместо рисованных гексов.
 *
 * <p>Поле на столе собирается из двадцати картонных модулей: пять малых по пять
 * гексов и пять больших по шесть, каждый двусторонний. Дизайнер отрисовал все
 * двадцать сторон, и здесь они кладутся туда, где игра рисует модуль: в каталог
 * Конструктора и на само поле.
 *
 * <p><b>Как картинка садится на гексы.</b> Рядом с картинками лежит
 * {@code block/anchors.yaml}: радиус гекса на картинке, точка, где на ней центр
 * гекса (0,0) набора, и поворот печати относительно координат набора. Модуль,
 * положенный на поле с поворотом {@code rot}, рисуется картинкой, повёрнутой на
 * {@code (rot − поворот)·60°} вокруг этой точки. Файл порождает
 * {@code tools/gen_block_art.py}, и он же сверяет каждую картинку с
 * {@code data/blocks}: жёлтые ячейки и контейнеры на печати обязаны стоять на
 * тех же гексах, что в наборе.
 *
 * <p>Нет картинок или нет якоря — {@link #art()} отвечает {@code null}, и модуль
 * рисуется прежним рисованным видом. Печать не источник правил: играется движок.
 */
public final class BlockArt {

    /**
     * Якорь печати.
     *
     * @param artW ширина картинки в пикселях
     * @param artH высота картинки
     * @param hex  радиус гекса на картинке
     * @param ox   x центра гекса (0,0) на картинке
     * @param oy   y центра гекса (0,0) на картинке
     * @param rot  на сколько шагов по 60° по часовой повёрнута печать
     *             относительно координат набора блоков
     * @param blocks версия набора блоков, по которой нарисована печать
     */
    public record Anchor(int artW, int artH, double hex, double ox, double oy, int rot,
                         String blocks) {
    }

    private static boolean loaded;
    private static Anchor anchor;

    private BlockArt() {
    }

    /** Якорь печатных модулей ({@code null} — картинок или якоря нет). */
    public static synchronized Anchor art() {
        load();
        return anchor;
    }

    /** Картинка стороны модуля ({@code null} — этой стороны не нарисовано). */
    public static BufferedImage face(String blockId, String side) {
        String key = key(blockId, side);
        return key == null ? null : Textures.block(key);
    }

    /** Есть ли печать вообще: и якорь, и хотя бы одна картинка. */
    public static boolean ready() {
        return art() != null && face("Б1", "A") != null;
    }

    /**
     * Имя файла стороны: {@code big-1-A}, {@code small-3-B}.
     *
     * <p>В данных блоки названы по-русски ({@code Б1}, {@code М3}), а имена
     * файлов держим латиницей: картинки уезжают в сборку приложения, и русские
     * имена там уже однажды теряли (правило про кириллицу в путях).
     */
    static String key(String blockId, String side) {
        if (blockId == null || blockId.length() < 2 || side == null || side.isBlank()) {
            return null;
        }
        char c = blockId.charAt(0);
        String kind = c == 'Б' || c == 'B' || c == 'b' ? "big"
            : c == 'М' || c == 'M' || c == 'm' ? "small" : null;
        if (kind == null) {
            return null;
        }
        String num = blockId.substring(1).trim();
        for (int i = 0; i < num.length(); i++) {
            if (!Character.isDigit(num.charAt(i))) {
                return null;
            }
        }
        return kind + "-" + num + "-" + side.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Насколько повернуть КАРТИНКУ, чтобы модуль лёг на поле с поворотом
     * {@code rot} шагов по 60° по часовой (градусы для полотна).
     */
    public static double rotationDeg(int rot) {
        Anchor a = art();
        return 60.0 * (rot - (a == null ? 0 : a.rot()));
    }

    /**
     * ПОЛОЖИТЬ КАРТОНКУ НА ГЕКСЫ — единственное место, где живёт это
     * преобразование. Им пользуются и каталог Конструктора, и сборка поля, и
     * само поле: разъедутся — и картон поедет относительно гексов там, где
     * этого никто не заметит.
     *
     * @param cx      экранный центр гекса, который у блока значится как (0,0)
     * @param cy      он же по вертикали
     * @param hexSize радиус гекса на экране
     * @param rot      на сколько шагов по 60° по часовой повёрнут блок на поле
     * @return нарисовали ли (нет картинки — нет)
     */
    public static boolean paint(java.awt.Graphics2D g, String blockId, String side,
                                double cx, double cy, double hexSize, int rot) {
        Anchor a = art();
        BufferedImage img = face(blockId, side);
        if (a == null || img == null || hexSize <= 0) {
            return false;
        }
        java.awt.geom.AffineTransform было = g.getTransform();
        Object сглаживание = g.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.translate(cx, cy);
        g.rotate(Math.toRadians(rotationDeg(rot)));
        double k = hexSize / a.hex();
        g.scale(k, k);
        g.translate(-a.ox(), -a.oy());
        g.drawImage(img, 0, 0, null);
        g.setTransform(было);
        if (сглаживание != null) {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, сглаживание);
        }
        return true;
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path folder = Textures.folder();
        if (folder == null) {
            return;
        }
        Path file = folder.resolve("block").resolve("anchors.yaml");
        if (!Files.isRegularFile(file)) {
            return;
        }
        Object root;
        try (var in = Files.newInputStream(file)) {
            root = new org.yaml.snakeyaml.Yaml().load(in);
        } catch (Exception e) {
            // Якорь — украшение показа: испорченный файл не должен ронять партию.
            return;
        }
        if (!(root instanceof Map<?, ?> m) || !(m.get("арт") instanceof Map<?, ?> a)) {
            return;
        }
        int[] size = pair(a.get("size"));
        double[] origin = pairD(a.get("origin"));
        if (size == null || origin == null || !(a.get("hex") instanceof Number hex)) {
            return;
        }
        int rot = a.get("поворот") instanceof Number n ? n.intValue() : 0;
        String blocks = m.get("meta") instanceof Map<?, ?> meta
            ? String.valueOf(meta.get("blocks")) : "";
        anchor = new Anchor(size[0], size[1], hex.doubleValue(), origin[0], origin[1],
            rot, blocks);
    }

    /**
     * НАРИСОВАНА ЛИ ПЕЧАТЬ ПОД ЭТУ ВЕРСИЮ НАБОРА.
     *
     * <p>Спрашивать обязательно. Печать модуля показывает КОНКРЕТНЫЕ жёлтые
     * ячейки и контейнеры; положить её на другую версию набора — значит
     * показать одно, а играть другое.
     */
    public static boolean matches(String blocksVersion) {
        Anchor a = art();
        return a != null && blocksVersion != null && !blocksVersion.isBlank()
            && a.blocks().equals(blocksVersion.trim());
    }

    /** Забыть прочитанное — при смене папки текстур. */
    public static synchronized void forget() {
        loaded = false;
        anchor = null;
    }

    private static int[] pair(Object o) {
        double[] d = pairD(o);
        return d == null ? null : new int[]{(int) Math.round(d[0]), (int) Math.round(d[1])};
    }

    private static double[] pairD(Object o) {
        if (!(o instanceof java.util.List<?> l) || l.size() != 2
                || !(l.get(0) instanceof Number a) || !(l.get(1) instanceof Number b)) {
            return null;
        }
        return new double[]{a.doubleValue(), b.doubleValue()};
    }
}
