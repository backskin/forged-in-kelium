package kelium.report;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * РАЗМЕРЫ ПЕЧАТНЫХ КОМПОНЕНТОВ В МИЛЛИМЕТРАХ — одна таблица на всю игру.
 *
 * <p>Зачем. Рисункам книги и справочника приходится класть жетоны и карты
 * рядом, и до сих пор каждый рисунок подбирал масштаб на глаз: жетоны на
 * свалке выходили размером с карту, а пехота — ростом с технику (замечание
 * дизайнера 16.09.2026). Размер компонента — физический факт, и он обязан
 * лежать в одном месте, а не в двадцати скриптах.
 *
 * <p>Таблица лежит в {@code data/components/sizes.yaml}. Там же записан
 * ЕДИНСТВЕННЫЙ МАСШТАБ ЭКСПОРТА — 11,8333 точки на миллиметр, — по которому
 * размер любого жетона выводится из его картинки. Масштаб проверен по трём
 * размерам, названным дизайнером прямо: пехота 18×18, техника 39×28,5,
 * добытчик и энергостанция 39×20.
 *
 * <p>Нет файла — {@link #есть()} отвечает {@code false}, а размеры приходят
 * {@code null}: печать не источник правил, и её отсутствие не должно валить
 * игру.
 */
public final class РазмерыПечати {

    /** Размер компонента в миллиметрах, как он лежит на столе. */
    public record Размер(double ширина, double высота) {

        /** Во сколько раз этот компонент шире другого. */
        public double доля(Размер другой) {
            return другой == null || другой.ширина <= 0 ? 1 : ширина / другой.ширина;
        }
    }

    private static boolean загружено;
    private static double точекНаМм;
    private static double скруглениеКарт;
    private static final Map<String, Размер> КАРТЫ = new LinkedHashMap<>();
    private static final Map<String, Размер> ЖЕТОНЫ = new LinkedHashMap<>();
    private static final Map<String, Размер> ПОЛЕ = new LinkedHashMap<>();

    private РазмерыПечати() {
    }

    /** Загрузить таблицу из каталога данных (повторные вызовы бесплатны). */
    public static synchronized void изКаталога(Path dataRoot) {
        if (загружено) {
            return;
        }
        загружено = true;
        Path p = dataRoot == null ? null
            : dataRoot.resolve("components").resolve("sizes.yaml");
        if (p == null || !Files.exists(p)) {
            return;
        }
        try (InputStream in = Files.newInputStream(p)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> doc = new org.yaml.snakeyaml.Yaml().load(in);
            точекНаМм = число(вложенный(doc, "печать"), "точек_на_мм", 0);
            Map<String, Object> карты = вложенный(doc, "карты");
            скруглениеКарт = число(карты, "скругление", 3.0);
            собрать(карты, КАРТЫ);
            собрать(вложенный(doc, "жетоны"), ЖЕТОНЫ);
            собрать(вложенный(doc, "поле"), ПОЛЕ);
        } catch (IOException | RuntimeException ex) {
            КАРТЫ.clear();
            ЖЕТОНЫ.clear();
            ПОЛЕ.clear();
        }
    }

    /** Загрузить таблицу из каталога данных по умолчанию. */
    public static void загрузить() {
        изКаталога(kelium.dataio.GameConfig.resolveDataRoot(null));
    }

    /** Есть ли таблица: без неё размеры приходят {@code null}. */
    public static boolean есть() {
        загрузить();
        return !ЖЕТОНЫ.isEmpty();
    }

    /** Точек на миллиметр в экспорте жетонов; 0 — таблицы нет. */
    public static double точекНаМм() {
        загрузить();
        return точекНаМм;
    }

    /** Скругление углов карт в миллиметрах. */
    public static double скруглениеКарт() {
        загрузить();
        return скруглениеКарт;
    }

    /**
     * Размер жетона по коду типа ({@code infantry}, {@code miner},
     * {@code command_center}). Уровень и владелец на размер не влияют, поэтому
     * {@code miner_l2_p3} и {@code miner} — одно и то же.
     */
    public static Размер жетон(String код) {
        загрузить();
        return ЖЕТОНЫ.get(корень(код));
    }

    /** Размер карты по имени колоды ({@code задание}, {@code арсенал}, …). */
    public static Размер карта(String имя) {
        загрузить();
        return КАРТЫ.get(имя);
    }

    /** Размер элемента поля ({@code гекс}). */
    public static Размер поле(String имя) {
        загрузить();
        return ПОЛЕ.get(имя);
    }

    /**
     * КОРЕНЬ ИМЕНИ ТЕКСТУРЫ. Имена файлов несут уровень и владельца
     * ({@code miner_l2_p3}, {@code infantry_trophy1}), а размер у всех
     * вариантов один — он физический.
     */
    public static String корень(String код) {
        if (код == null) {
            return "";
        }
        String s = код.endsWith(".png") ? код.substring(0, код.length() - 4) : код;
        s = s.replaceAll("_(l\\d+|p\\d+|super|trophy\\d*)$", "");
        s = s.replaceAll("_(l\\d+|p\\d+|super|trophy\\d*)$", "");
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> вложенный(Map<String, Object> doc, String ключ) {
        Object o = doc == null ? null : doc.get(ключ);
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    private static double число(Map<String, Object> m, String ключ, double иначе) {
        Object o = m.get(ключ);
        return o instanceof Number n ? n.doubleValue() : иначе;
    }

    private static void собрать(Map<String, Object> из, Map<String, Размер> куда) {
        for (var e : из.entrySet()) {
            if (e.getValue() instanceof List<?> пара && пара.size() == 2
                    && пара.get(0) instanceof Number ш && пара.get(1) instanceof Number в) {
                куда.put(String.valueOf(e.getKey()),
                    new Размер(ш.doubleValue(), в.doubleValue()));
            }
        }
    }
}
