package kelium.gui.replay2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.report.Textures;

/**
 * ЯКОРЯ ПЕЧАТНЫХ ПЛАНШЕТОВ — где на картинке настоящего планшета лежит живое.
 *
 * <p>Печатный планшет игрока показывается в игре той же картинкой, что уходит в
 * типографию, а поверх неё кладутся кубики склада и жетоны модулей. Чтобы они
 * ложились ровно в напечатанные ячейки, рядом с картинкой лежит
 * {@code board/anchors.yaml} — координаты этих ячеек в пикселях ИСХОДНОЙ
 * картинки. Файл не пишется руками: его порождает разборщик самой картинки
 * (scratchpad/gen_anchors.py) и сверяет число найденных ячеек с
 * {@code data/boards}. Художник перерисовал планшет — перегенерировать.
 *
 * <p>Нет файла или нет стороны — {@link #storage}/{@link #troop} возвращают
 * пусто, и планшет рисуется прежним рисованным видом. Партия от этого не
 * зависит: якоря нужны только показу.
 */
public final class BoardAnchors {

    /** Ячейка склада: чьё это место, какого уровня, подо что и где на картинке. */
    public record Cell(String group, int level, char type, int x, int y, int w, int h) {
    }

    /** Колонка планшета войск: род, здание и две рамки под жетоны модулей. */
    public record Column(String unit, String building,
                          int ax, int ay, int aw, int ah,
                          int bx, int by, int bw, int bh) {
    }

    private static boolean loaded;
    private static final Map<String, List<Cell>> STORAGE = new LinkedHashMap<>();
    private static final Map<String, List<Column>> TROOP = new LinkedHashMap<>();

    private BoardAnchors() {
    }

    /** Ячейки планшета хранилища этой стороны (пусто — якорей нет). */
    public static synchronized List<Cell> storage(String side) {
        load();
        return STORAGE.getOrDefault(key(side), List.of());
    }

    /** Колонки планшета войск этой стороны (пусто — якорей нет). */
    public static synchronized List<Column> troop(String side) {
        load();
        return TROOP.getOrDefault(key(side), List.of());
    }

    /**
     * Сторона в том виде, в каком она записана в именах файлов: {@code A},
     * {@code B1}… Запись партии может нести её по-русски («А», «Б1») — латиница
     * и кириллица здесь неразличимы на глаз, и путать их нельзя.
     */
    private static String key(String side) {
        if (side == null || side.isBlank()) {
            return "A";
        }
        String s = side.trim().toUpperCase(java.util.Locale.ROOT);
        return s.replace('А', 'A').replace('Б', 'B');
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path folder = Textures.folder();
        if (folder == null) {
            return;
        }
        Path file = folder.resolve("board").resolve("anchors.yaml");
        if (!Files.isRegularFile(file)) {
            return;
        }
        Object root;
        try (var in = Files.newInputStream(file)) {
            root = new org.yaml.snakeyaml.Yaml().load(in);
        } catch (Exception e) {
            // Якоря — украшение показа: испорченный файл не должен ронять партию.
            return;
        }
        if (!(root instanceof Map<?, ?> m) || !(m.get("boards") instanceof List<?> boards)) {
            return;
        }
        for (Object bo : boards) {
            if (!(bo instanceof Map<?, ?> b)) {
                continue;
            }
            String side = key(String.valueOf(b.get("side")));
            if ("storage".equals(b.get("kind")) && b.get("cells") instanceof List<?> cs) {
                List<Cell> out = new ArrayList<>();
                for (Object co : cs) {
                    if (!(co instanceof Map<?, ?> c)) {
                        continue;
                    }
                    int[] box = box(c.get("box"));
                    if (box == null) {
                        continue;
                    }
                    String t = String.valueOf(c.get("type"));
                    out.add(new Cell(String.valueOf(c.get("group")),
                        num(c.get("level")), t.isEmpty() ? 'U' : t.charAt(0),
                        box[0], box[1], box[2], box[3]));
                }
                STORAGE.put(side, List.copyOf(out));
            } else if ("troop".equals(b.get("kind")) && b.get("columns") instanceof List<?> cs) {
                List<Column> out = new ArrayList<>();
                for (Object co : cs) {
                    if (!(co instanceof Map<?, ?> c)) {
                        continue;
                    }
                    int[] a = box(c.get("attack"));
                    int[] s = box(c.get("assembly"));
                    if (a == null || s == null) {
                        continue;
                    }
                    out.add(new Column(String.valueOf(c.get("unit")),
                        String.valueOf(c.get("building")),
                        a[0], a[1], a[2], a[3], s[0], s[1], s[2], s[3]));
                }
                TROOP.put(side, List.copyOf(out));
            }
        }
    }

    private static int[] box(Object o) {
        if (!(o instanceof List<?> l) || l.size() != 4) {
            return null;
        }
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            out[i] = num(l.get(i));
        }
        return out;
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
