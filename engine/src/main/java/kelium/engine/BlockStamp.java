package kelium.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Field;
import kelium.core.Hex;
import kelium.core.HexKind;

/**
 * BlockStamp — печатные контейнеры на поле.
 *
 * <p>Поле в реальности всегда собирается из картонных блоков: 5 малых (по 5
 * гексов) и 5 больших (по 6), каждый двусторонний — 20 разных сторон. Малый
 * блок несёт 4 напечатанных контейнера, большой — 5 (один гекс блока пустой),
 * и ровно один контейнер каждой стороны стоит в воздушной ячейке. Набор
 * физический и неизменный: {@code simulator/data/blocks/blocks.1.1.0.yaml}.
 *
 * <p>Класс делает то же, что дизайнер за столом: раскладывает поле блоками,
 * поворачивает каждый блок как ляжет и переносит напечатанные контейнеры на
 * ячейки гексов ({@link Hex#containerCell}).
 *
 * <p><b>Честная оговорка про точность.</b> Разбиение поля на блоки берётся не
 * из перебора (он дорогой и живёт в конструкторе), а простой жадной нарезкой
 * по 5–6 смежных гексов: для контейнеров важно не то, КАКИЕ куски картона
 * легли, а сколько на поле контейнеров, как они распределены по ячейкам и
 * сколько из них воздушных. Эти три вещи воспроизводятся точно: на каждые
 * 5–6 гексов приходится 4–5 контейнеров и ровно один воздушный — как на
 * настоящем блоке.
 */
public final class BlockStamp {

    private BlockStamp() {
    }

    /** Индекс воздушной ячейки (наземные — 0..5). */
    public static final int AIR = 6;

    /** Одна сторона блока: список ячеек с контейнерами по её гексам. */
    public record Face(String blockId, String side, String kind, List<Integer> cells) {
        public int size() {
            return cells.size();
        }
    }

    private static List<Face> cache;

    /**
     * Прочитать набор блоков. Файл лежит рядом с прочими данными игры
     * ({@code <data>/blocks/blocks.<версия>.yaml}); если его нет — работаем без
     * печатных контейнеров, а не падаем.
     */
    @SuppressWarnings("unchecked")
    public static synchronized List<Face> faces(Path dataRoot) {
        if (cache != null) {
            return cache;
        }
        List<Face> out = new ArrayList<>();
        Path p = dataRoot == null ? null : dataRoot.resolve("blocks").resolve("blocks.1.1.0.yaml");
        if (p != null && Files.exists(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                Map<String, Object> doc = new org.yaml.snakeyaml.Yaml().load(in);
                for (Object bo : (List<Object>) doc.getOrDefault("blocks", List.of())) {
                    Map<String, Object> b = (Map<String, Object>) bo;
                    String id = String.valueOf(b.get("id"));
                    String kind = String.valueOf(b.get("kind"));
                    Map<String, Object> facesMap = (Map<String, Object>) b.get("faces");
                    if (facesMap == null) {
                        continue;
                    }
                    for (var e : facesMap.entrySet()) {
                        List<Integer> cells = new ArrayList<>();
                        for (Object co : (List<Object>) e.getValue()) {
                            Map<String, Object> c = (Map<String, Object>) co;
                            cells.add(((Number) c.get("cell")).intValue());
                        }
                        out.add(new Face(id, String.valueOf(e.getKey()), kind, cells));
                    }
                }
            } catch (IOException | RuntimeException ex) {
                out.clear();
            }
        }
        cache = out;
        return cache;
    }

    /**
     * Разложить поле блоками и перенести печатные контейнеры на гексы.
     *
     * <p>Тайлы зарождения и запретные гексы контейнеров не несут: на них не
     * встают жетоны, а картон под ними всё равно накрыт.
     */
    public static void stamp(Field field, Path dataRoot, Random rng,
                             kelium.rules.Ruleset rules) {
        List<Face> all = faces(dataRoot);
        if (all.isEmpty()) {
            return;
        }
        List<Face> small = new ArrayList<>();
        List<Face> big = new ArrayList<>();
        for (Face f : all) {
            ("small".equals(f.kind()) ? small : big).add(f);
        }

        // Гексы, куда вообще можно встать жетоном (остальные картон не кормит).
        List<String> free = new ArrayList<>();
        for (Hex h : field.hexes.values()) {
            h.containerCell = -1;
            if (h.kind != HexKind.FORBIDDEN && h.spawnTile == null) {
                free.add(h.id);
            }
        }
        Collections.shuffle(free, rng);
        java.util.Set<String> left = new java.util.LinkedHashSet<>(free);

        while (!left.isEmpty()) {
            boolean wantBig = rng.nextBoolean() && left.size() >= 6;
            int want = wantBig ? 6 : 5;
            List<String> piece = grabPiece(field, left, want);
            List<Face> pool = piece.size() >= 6 ? big : small;
            if (pool.isEmpty()) {
                pool = piece.size() >= 6 ? small : big;
            }
            Face face = pool.get(rng.nextInt(pool.size()));
            int rot = rng.nextInt(6);
            List<Integer> cells = new ArrayList<>(face.cells());
            Collections.shuffle(cells, rng);
            // УМЕНЬШЕННЫЙ НАБОР (правило-вариант containers.printed_per_small_block /
            // printed_per_big_block, запрос дизайнера 13.08.2026): на блоке
            // напечатано МЕНЬШЕ контейнеров, чем в наборе 1.1.0 (4 на малом, 5 на
            // большом). Лишние гексы блока остаются без контейнера. Это самый
            // прямой способ придавить поток, не меняя правил получения.
            int limit = piece.size() >= 6
                ? ((Number) rules.get("containers.printed_per_big_block", 5)).intValue()
                : ((Number) rules.get("containers.printed_per_small_block", 4)).intValue();
            int placed = 0;
            for (int i = 0; i < piece.size(); i++) {
                int cell = cells.get(i % cells.size());
                if (cell >= 0) {
                    if (placed >= limit) {
                        cell = -1;      // контейнеров на блоке уже достаточно
                    } else {
                        placed++;
                    }
                }
                // cell < 0 — гекс блока БЕЗ контейнера (малый блок несёт 4
                // контейнера на 5 гексов, большой 5 на 6). Поворот блока на
                // столе двигает НАЗЕМНЫЕ ячейки по кругу, воздушная остаётся.
                field.get(piece.get(i)).containerCell =
                    cell < 0 ? -1 : cell == AIR ? AIR : Math.floorMod(cell + rot, 6);
            }
            left.removeAll(piece);
        }
    }

    /** Отрезать от остатка связный кусок до {@code want} гексов. */
    private static List<String> grabPiece(Field field, java.util.Set<String> left, int want) {
        String seed = left.iterator().next();
        List<String> piece = new ArrayList<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        queue.add(seed);
        seen.add(seed);
        while (!queue.isEmpty() && piece.size() < want) {
            String cur = queue.poll();
            if (!left.contains(cur)) {
                continue;
            }
            piece.add(cur);
            for (String nb : field.neighbors(cur)) {
                if (left.contains(nb) && seen.add(nb)) {
                    queue.add(nb);
                }
            }
        }
        return piece;
    }

    /** Сбросить кэш набора блоков (для тестов). */
    public static synchronized void resetCache() {
        cache = null;
    }

    /** Сводка по полю: сколько печатных контейнеров и сколько из них воздушных. */
    public static Map<String, Integer> summary(Field field) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int total = 0;
        int air = 0;
        for (Hex h : field.hexes.values()) {
            if (h.containerCell >= 0) {
                total++;
                if (h.containerCell == AIR) {
                    air++;
                }
            }
        }
        out.put("printed", total);
        out.put("air", air);
        return out;
    }
}
