package kelium.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.yaml.snakeyaml.Yaml;

/**
 * СНИМОК РАСКЛАДКИ — картинка поля из файла {@code .kmap} БЕЗ ОКНА.
 *
 * <p>Зачем. Раскладки идут картинками в справочник, и рисовать их руками из
 * конструктора значит каждый раз заново возить мышью и попадать в разные
 * масштабы. Здесь тот же холст и тот же экспорт, что в конструкторе
 * ({@link LayoutEditor.Canvas}, {@link PngExport}), — картинка не может
 * разойтись с тем, что дизайнер видит в программе.
 *
 * <p>Окно не показывается и фокус не забирает: холст рисуется в память, как
 * это делают остальные снимки в пакете конструктора.
 *
 * <p>Запуск:
 * <pre>
 *   java -cp gui/target/kelium-runner.jar kelium.gui.СнимокРаскладки \
 *        &lt;куда.png&gt; &lt;файл.kmap&gt; [ширина] [высота]
 * </pre>
 */
public final class СнимокРаскладки {

    private СнимокРаскладки() {
    }

    /**
     * Легенда раскладок. Отличается от конструкторской двумя вещами, которых
     * дизайнеру не хватало на странице справочника:
     *
     * <ul>
     *   <li><b>места игроков одной строкой</b> — четыре кружка подряд вместо
     *       четырёх одинаковых строк;</li>
     *   <li><b>нейтральные постройки СВОЕЙ формой</b>, малая и большая
     *       отдельно: серый квадрат на их месте не говорит ничего.</li>
     * </ul>
     *
     * <p>КОНТЕЙНЕРА В ЛЕГЕНДЕ НЕТ. На поле он больше не выкладывается: контейнеры
     * НАПЕЧАТАНЫ на самих блоках, и на какой сектор они попадут — решает
     * раскладка блоков, а не сценарий. Показывать их в легенде раскладки значит
     * обещать то, чего на картинке нет.
     */
    private static List<PngExport.Item> легенда() {
        List<PngExport.Item> л = new ArrayList<>();
        л.add(PngExport.Item.seats(
            List.of(LayoutEditor.Canvas.SEAT[0], LayoutEditor.Canvas.SEAT[1],
                    LayoutEditor.Canvas.SEAT[2], LayoutEditor.Canvas.SEAT[3]),
            "стартовый гекс игрока: ЦУ и пехота"));
        л.add(PngExport.Item.hex(LayoutEditor.Canvas.SPAWN_START,
            "малое зарождение: лицо 3 келемия, оборот 2"));
        л.add(PngExport.Item.hex(LayoutEditor.Canvas.SPAWN_NORMAL,
            "большое зарождение: лицо 4 келемия, оборот 3"));
        л.add(PngExport.Item.neutral(LayoutEditor.Canvas.NEUTRAL_FILL, false,
            "нейтральная постройка, малая — одна стенка"));
        л.add(PngExport.Item.neutral(LayoutEditor.Canvas.NEUTRAL_FILL, true,
            "нейтральная постройка, большая — две стенки"));
        л.add(PngExport.Item.hex(new java.awt.Color(0x3A3A3A),
            "чёрная накладка — гекса в этой раскладке нет"));
        return л;
    }

    /**
     * СРЕЗАТЬ ПУСТЫЕ ПОЛЯ КАДРА.
     *
     * <p>Холст конструктора оставляет вокруг поля несколько колец угасающей
     * сетки — это решение дизайнера, и трогать его нельзя. Но кадр при этом
     * квадратный, а поле почти всегда шире, чем выше, и сверху с снизу
     * остаётся чистый фон в треть картинки. На странице книги это выброшенное
     * место, поэтому здесь срезается ровно фон — всё, что нарисовано, включая
     * саму угасающую сетку, остаётся на месте.
     */
    private static BufferedImage обрезать(BufferedImage img) {
        return обрезать(img, true);
    }

    /** Насколько пиксель должен отличаться от фона, чтобы считаться СОДЕРЖИМЫМ. */
    private static final int ПОРОГ_СОДЕРЖИМОГО = 26;

    private static boolean содержимое(int rgb, int фон) {
        int d = Math.max(Math.max(
            Math.abs(((rgb >> 16) & 255) - ((фон >> 16) & 255)),
            Math.abs(((rgb >> 8) & 255) - ((фон >> 8) & 255))),
            Math.abs((rgb & 255) - (фон & 255)));
        return d > ПОРОГ_СОДЕРЖИМОГО;
    }

    /**
     * ОБРЕЗАТЬ КАДР ПО СОДЕРЖИМОМУ.
     *
     * <p>Угасающая сетка залита по всей площади кадра, поэтому обрезать «по
     * тому, что нарисовано» нельзя — обрезать будет нечего. Границу ищем по
     * ЯРКИМ пикселям: блоки, тайлы, кружки мест и чёрные накладки от фона
     * отличаются сильно, бледная сетка — почти нет. Сетка при этом остаётся:
     * кадр вырезается из исходной картинки, а не заливается заново.
     *
     * @param вКвадрат достраивать ли кадр до квадрата. Полю — да, оно идёт в
     *                 сетку 2×2 и кадры разной формы в ней разъезжаются.
     *                 Легенде — нет: это полоса, и квадрат из неё делает
     *                 пустой лист с текстом посередине.
     */
    private static BufferedImage обрезать(BufferedImage img, boolean вКвадрат) {
        int фон = img.getRGB(0, 0);
        int л = img.getWidth();
        int в = img.getHeight();
        int п = -1;
        int н = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (!содержимое(img.getRGB(x, y), фон)) {
                    continue;
                }
                л = Math.min(л, x);
                п = Math.max(п, x);
                в = Math.min(в, y);
                н = Math.max(н, y);
            }
        }
        if (п < 0) {
            return img;               // пустая раскладка — резать нечего
        }
        int запас = Math.max(6, img.getWidth() / 120);
        if (!вКвадрат) {
            л = Math.max(0, л - запас);
            в = Math.max(0, в - запас);
            п = Math.min(img.getWidth() - 1, п + запас);
            н = Math.min(img.getHeight() - 1, н + запас);
            return img.getSubimage(л, в, п - л + 1, н - в + 1);
        }
        // СТРОГО КВАДРАТ И ВЫРЕЗКОЙ ИЗ ИСХОДНОЙ КАРТИНКИ. Достраивать заливкой
        // нельзя: на слиянии фон — это сетка, и залитые поля оборвали бы её
        // пустыми полосами. Поэтому окно двигается внутри кадра, а если места
        // не хватает — прижимается к краю.
        int сторона = Math.min(img.getWidth(), Math.min(img.getHeight(),
            Math.max(п - л + 1, н - в + 1) + 2 * запас));
        int cx = (л + п) / 2;
        int cy = (в + н) / 2;
        int x0 = Math.max(0, Math.min(img.getWidth() - сторона, cx - сторона / 2));
        int y0 = Math.max(0, Math.min(img.getHeight() - сторона, cy - сторона / 2));
        return img.getSubimage(x0, y0, сторона, сторона);
    }

    @SuppressWarnings("unchecked")
    public static BufferedImage нарисовать(Path kmap, int ширина, int высота)
            throws Exception {
        Map<String, Object> data = new Yaml().load(
            Files.readString(kmap, StandardCharsets.UTF_8));
        List<Object> сценарии = (List<Object>) data.get("scenarios");
        if (сценарии == null || сценарии.isEmpty()) {
            throw new IllegalArgumentException("в файле нет раскладок: " + kmap);
        }
        // В файле может лежать несколько вариантов; берём первый — конструктор
        // при открытии спрашивает, а здесь спрашивать некого.
        Map<String, Object> scn = (Map<String, Object>) сценарии.get(0);

        SwingUtilities.invokeAndWait(() -> LayoutEditor.loadScenarioIntoModel(scn));

        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        canvas.model = LayoutEditor.modelRef();
        canvas.fitToView();
        BufferedImage поле = обрезать(canvas.render(ширина, высота));

        LayoutEditor.Model m = LayoutEditor.modelRef();
        PngExport.Content содержимое = new PngExport.Content(
            легенда(), LayoutEditor.playerBlocks(m), LayoutEditor.mapStats(m));

        String имя = String.valueOf(scn.getOrDefault("id", kmap.getFileName()));
        int игроков = LayoutEditor.playerBlocks(m).size();
        String подпись = "гексов: " + m.hexes.size() + "   ·   игроков: " + игроков;
        return PngExport.compose(имя, подпись, поле, содержимое);
    }

    /** ТОЛЬКО ПОЛЕ, без легенды и статистики — для страницы книги. */
    @SuppressWarnings("unchecked")
    public static BufferedImage толькоПоле(Path kmap, int ширина, int высота)
            throws Exception {
        Map<String, Object> data = new Yaml().load(
            Files.readString(kmap, StandardCharsets.UTF_8));
        Map<String, Object> scn =
            (Map<String, Object>) ((List<Object>) data.get("scenarios")).get(0);
        SwingUtilities.invokeAndWait(() -> LayoutEditor.loadScenarioIntoModel(scn));
        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        canvas.model = LayoutEditor.modelRef();
        canvas.fitToView();
        return обрезать(canvas.render(ширина, высота));
    }

    /**
     * СЛИЯНИЕ — поле вместе со сборкой из блоков и чёрными накладками.
     *
     * <p>Для справочника нужна не схема, а то, что игрок увидит на столе: из
     * каких картонок собрано поле и какие гексы закрыты чёрным. Рисуют это те
     * же слои конструктора, что и его кнопка «экспорт слиянием»; подбор блоков
     * считается здесь же, синхронно.
     *
     * <p>Сетка на фоне оставлена нарочно ({@code hexGrid = true}): на слиянии
     * обычных гексов не рисуют вовсе, и без сетки поле висит в пустоте.
     */
    @SuppressWarnings("unchecked")
    public static BufferedImage слияние(Path kmap, int ширина, int высота,
                                        int больших, int малых, int чёрных)
            throws Exception {
        Map<String, Object> data = new Yaml().load(
            Files.readString(kmap, StandardCharsets.UTF_8));
        Map<String, Object> scn =
            (Map<String, Object>) ((List<Object>) data.get("scenarios")).get(0);
        final boolean[] собралось = {false};
        SwingUtilities.invokeAndWait(() -> {
            LayoutEditor.loadScenarioIntoModel(scn);
            собралось[0] = LayoutEditor.подготовитьБезОкна()
                .подобратьБезОкна(больших, малых, чёрных);
        });
        if (!собралось[0]) {
            throw new IllegalStateException(
                "сборка из блоков не подобралась: " + kmap.getFileName());
        }
        PngExport.Options опции = new PngExport.Options(
            false, false, false, PngExport.Layout.FUSION, true, true);
        return обрезать(LayoutEditor.fuseLayers(ширина, высота, опции));
    }

    /**
     * ЛЕГЕНДА ОТДЕЛЬНОЙ КАРТИНКОЙ — она одна на все раскладки.
     *
     * <p>Заголовок пустой нарочно: у самой легенды внутри уже есть подпись
     * «Обозначения», а над картинкой в книге стоит заголовок страницы. Три
     * заголовка подряд об одном и том же. Пустая шапка после обрезки уходит
     * вместе с фоном, и картинка получается ровно в высоту списка.
     */
    public static BufferedImage толькоЛегенда() {
        BufferedImage пусто = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        return обрезать(PngExport.compose("", null, пусто,
            PngExport.Content.legendOnly(легенда())), false);
    }

    private static void записать(BufferedImage img, String путь) throws Exception {
        File out = new File(путь);
        if (out.getParentFile() != null) {
            out.getParentFile().mkdirs();
        }
        ImageIO.write(img, "png", out);
        System.out.println(out + "  " + img.getWidth() + "x" + img.getHeight());
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("нужно: <режим: поле|слияние|полный|легенда> <куда.png> "
                + "[файл.kmap] [ширина] [высота]");
            System.exit(2);
        }
        String режим = args[0];
        if ("легенда".equals(режим)) {
            записать(толькоЛегенда(), args[1]);
            System.exit(0);
        }
        int ш = args.length > 3 ? Integer.parseInt(args[3]) : 1600;
        int в = args.length > 4 ? Integer.parseInt(args[4]) : 1100;
        Path kmap = Path.of(args[2]);
        BufferedImage img = switch (режим) {
            case "поле" -> толькоПоле(kmap, ш, в);
            case "слияние" -> слияние(kmap, ш, в, 5, 5, 8);
            default -> нарисовать(kmap, ш, в);
        };
        записать(img, args[1]);
        System.exit(0);
    }
}
