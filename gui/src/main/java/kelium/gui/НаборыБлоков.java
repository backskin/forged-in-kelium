package kelium.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * НАБОРЫ БЛОКОВ, ПРОЧИТАННЫЕ С ДИСКА — общие на весь конструктор.
 *
 * <p>ЗАЧЕМ ОТДЕЛЬНЫЙ КЛАСС. Чтение наборов жило внутри каталога блоков и было
 * там приватным. Сборке поля из блоков понадобилось то же самое — какие стороны
 * есть в наборе и что на них напечатано, — и второй читатель того же формата
 * гарантированно разошёлся бы с первым. Формат один, и читатель тоже один.
 *
 * <p>Набор блоков — ФИЗИЧЕСКИЙ КАРТОН: двадцать сторон (пять малых блоков по
 * пять гексов и пять больших по шесть, каждый двусторонний). Разные версии
 * набора различаются только РАЗМЕТКОЙ — где напечатан контейнер и где жёлтая
 * ячейка энергии, — а геометрия сторон у всех одна.
 */
public final class НаборыБлоков {

    private НаборыБлоков() {
    }

    /** Индекс сектора, означающий «метки нет». */
    public static final int ПУСТО = -1;

    /** Сектор авиации (небо) — в нём стоит обязательный контейнер стороны. */
    public static final int НЕБО = kelium.engine.BlockStamp.AIR;

    /**
     * Один гекс стороны блока.
     *
     * @param q         осевая координата в форме блока
     * @param r         осевая координата в форме блока
     * @param контейнер сектор печатного контейнера либо {@link #ПУСТО}
     * @param энергия   сектор жёлтой ячейки либо {@link #ПУСТО}
     */
    public record Гекс(int q, int r, int контейнер, int энергия) {

        public boolean естьКонтейнер() {
            return контейнер != ПУСТО;
        }

        public boolean естьЭнергия() {
            return энергия != ПУСТО;
        }
    }

    /**
     * Одна сторона блока — лицо А или Б.
     *
     * @param блок   номер блока на картоне: М1..М5 либо Б1..Б5
     * @param размер {@code small} или {@code big}
     * @param имя    «A» или «B»
     * @param гексы  гексы стороны в том порядке, в каком лежат в файле
     */
    public record Сторона(String блок, String размер, String имя, List<Гекс> гексы) {

        /** Как сторона называется на картоне: «М1-А». */
        public String подпись() {
            return блок + "-" + ("A".equals(имя) ? "А" : "Б");
        }

        public boolean малая() {
            return "small".equals(размер);
        }
    }

    /** Версия набора: её id плюс стороны, разложенные по размеру. */
    public record Версия(String id, List<Сторона> малые, List<Сторона> большие) {

        public List<Сторона> все() {
            List<Сторона> out = new ArrayList<>(малые);
            out.addAll(большие);
            return out;
        }

        private static int сколько(List<Сторона> стороны,
                                   java.util.function.Predicate<Гекс> что) {
            return стороны.isEmpty() ? 0
                : (int) стороны.get(0).гексы().stream().filter(что).count();
        }

        public int гексовМалый() {
            return малые.isEmpty() ? 0 : малые.get(0).гексы().size();
        }

        public int гексовБольшой() {
            return большие.isEmpty() ? 0 : большие.get(0).гексы().size();
        }

        public int контейнеровМалый() {
            return сколько(малые, Гекс::естьКонтейнер);
        }

        public int контейнеровБольшой() {
            return сколько(большие, Гекс::естьКонтейнер);
        }

        public int энергииМалый() {
            return сколько(малые, Гекс::естьЭнергия);
        }

        public int энергииБольшой() {
            return сколько(большие, Гекс::естьЭнергия);
        }

        /** Подпись сочетания по контейнерам: «3/5 и 4/6». */
        public String подписьКонтейнеров() {
            return контейнеровМалый() + "/" + гексовМалый() + " и "
                + контейнеровБольшой() + "/" + гексовБольшой();
        }

        /** Подпись сочетания по энергии; «нет жёлтых ячеек», если их нет вовсе. */
        public String подписьЭнергии() {
            int м = энергииМалый();
            int б = энергииБольшой();
            if (м == 0 && б == 0) {
                return "нет жёлтых ячеек";
            }
            return м + "/" + гексовМалый() + " и " + б + "/" + гексовБольшой();
        }
    }

    /**
     * Прочитать все наборы из {@code <data>/blocks}. Порядок — по версии
     * числами, а не по алфавиту (иначе 1.10.0 встаёт раньше 1.9.0).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Версия> загрузить(Path dataRoot) {
        Map<String, Версия> out = new TreeMap<>(kelium.dataio.VersionOrder.ASC);
        if (dataRoot == null) {
            return out;
        }
        Path dir = dataRoot.resolve("blocks");
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var stream = Files.list(dir)) {
            List<Path> файлы = stream
                .filter(p -> p.getFileName().toString().startsWith("blocks.")
                    && p.getFileName().toString().endsWith(".yaml"))
                .sorted((a, b) -> kelium.dataio.VersionOrder.compare(
                    a.getFileName().toString(), b.getFileName().toString()))
                .toList();
            for (Path f : файлы) {
                Map<String, Object> doc;
                try (var in = Files.newInputStream(f)) {
                    doc = new org.yaml.snakeyaml.Yaml().load(in);
                } catch (IOException | RuntimeException e) {
                    continue;
                }
                if (doc == null || !(doc.get("blocks") instanceof List<?> список)) {
                    continue;
                }
                String id = doc.get("meta") instanceof Map<?, ?> meta
                    && meta.get("id") != null ? String.valueOf(meta.get("id"))
                    : f.getFileName().toString();
                List<Сторона> малые = new ArrayList<>();
                List<Сторона> большие = new ArrayList<>();
                for (Object bo : список) {
                    Map<String, Object> b = (Map<String, Object>) bo;
                    String блок = String.valueOf(b.get("id"));
                    String размер = String.valueOf(b.get("kind"));
                    if (!(b.get("faces") instanceof Map<?, ?> стороны)) {
                        continue;
                    }
                    for (String имя : new String[]{"A", "B"}) {
                        if (!(стороны.get(имя) instanceof List<?> гексыСписок)) {
                            continue;
                        }
                        List<Гекс> гексы = new ArrayList<>();
                        for (Object ho : гексыСписок) {
                            Map<String, Object> h = (Map<String, Object>) ho;
                            гексы.add(new Гекс(число(h.get("q")), число(h.get("r")),
                                число(h.get("cell")), число(h.getOrDefault("energy", -1))));
                        }
                        Сторона сторона = new Сторона(блок, размер, имя, гексы);
                        ("small".equals(размер) ? малые : большие).add(сторона);
                    }
                }
                out.put(id, new Версия(id, малые, большие));
            }
        } catch (IOException e) {
            // нет папки данных — вернём то, что успели прочитать
        }
        return out;
    }

    /**
     * Версии, разложенные по сочетанию «сколько контейнеров» → «сколько
     * энергии» → список версий. Нужно обоим выборщикам: и в каталоге, и в
     * сборке поля дизайнер выбирает набор именно двумя этими числами.
     */
    public static Map<String, Map<String, List<Версия>>> посочетаниям(
            Map<String, Версия> версии) {
        Map<String, Map<String, List<Версия>>> out = new LinkedHashMap<>();
        for (Версия v : версии.values()) {
            out.computeIfAbsent(v.подписьКонтейнеров(), k -> new LinkedHashMap<>())
                .computeIfAbsent(v.подписьЭнергии(), k -> new ArrayList<>())
                .add(v);
        }
        return out;
    }

    private static int число(Object o) {
        return o instanceof Number n ? n.intValue() : ПУСТО;
    }
}
