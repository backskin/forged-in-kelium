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
 * BlockStamp — ПЕЧАТНАЯ РАЗМЕТКА БЛОКОВ на поле: контейнеры и жёлтые ячейки.
 *
 * <p>Поле в реальности всегда собирается из картонных блоков: 5 малых (по 5
 * гексов) и 5 больших (по 6), каждый двусторонний — 20 разных сторон. С
 * релиза 1.4.0 (заказ дизайнера 30.08.2026) покрытие разреженное: малый блок
 * несёт 3 напечатанных контейнера, большой — 4 (два гекса блока пустые), и
 * ровно один контейнер каждой стороны стоит в воздушной ячейке. Жёлтая ячейка
 * напечатана на ТЕХ ЖЕ гексах, что и контейнер (пустые гексы не несут ни
 * того, ни другого) — наземная и никогда не та же, что контейнерная. Набор
 * физический и неизменный: {@code <data>/blocks/blocks.1.4.0.yaml}.
 *
 * <p>Класс делает то же, что дизайнер за столом: раскладывает поле блоками,
 * поворачивает каждый блок как ляжет и переносит печать на ячейки гексов —
 * контейнер в {@link Hex#containerCell}, жёлтую ячейку в {@link Hex#energyCell}.
 *
 * <p><b>Честная оговорка про точность.</b> Разбиение поля на блоки берётся не
 * из перебора (он дорогой и живёт в конструкторе), а простой жадной нарезкой
 * по 5–6 смежных гексов: для контейнеров важно не то, КАКИЕ куски картона
 * легли, а сколько на поле контейнеров, как они распределены по ячейкам и
 * сколько из них воздушных. Эти три вещи воспроизводятся точно: на каждые
 * 5–6 гексов приходится 3–4 контейнера и ровно один воздушный — как на
 * настоящем блоке.
 */
public final class BlockStamp {

    private BlockStamp() {
    }

    /** Индекс воздушной ячейки (наземные — 0..5). */
    public static final int AIR = 6;

    /**
     * Печать ОДНОГО ГЕКСА стороны блока: ячейка контейнера (−1 нет, 0..5
     * наземная, 6 воздушная) и ячейка жёлтая (0..5; −1 только у наборов,
     * которые её ещё не несут).
     *
     * <p>Пара, а не два списка, нарочно: обе ячейки принадлежат ОДНОМУ гексу
     * картона и обязаны ехать вместе и при перемешивании, и при повороте.
     * Разъедини их — и жёлтая ячейка может встать на контейнерную, чего на
     * картоне не бывает.
     */
    public record Cell(int container, int energy) {
    }

    /** Одна сторона блока: печать по её гексам. */
    public record Face(String blockId, String side, String kind, List<Cell> cells) {
        public int size() {
            return cells.size();
        }
    }

    private static List<Face> cache;

    /**
     * Прочитать набор блоков. Файл лежит рядом с прочими данными игры
     * ({@code <data>/blocks/blocks.<версия>.yaml}); если его нет — работаем без
     * печатной разметки, а не падаем.
     */
    @SuppressWarnings("unchecked")
    public static synchronized List<Face> faces(Path dataRoot) {
        if (cache != null) {
            return cache;
        }
        List<Face> out = new ArrayList<>();
        Path p = dataRoot == null ? null : dataRoot.resolve("blocks").resolve("blocks.1.4.0.yaml");
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
                        List<Cell> cells = new ArrayList<>();
                        for (Object co : (List<Object>) e.getValue()) {
                            Map<String, Object> c = (Map<String, Object>) co;
                            Object en = c.get("energy");
                            cells.add(new Cell(((Number) c.get("cell")).intValue(),
                                en instanceof Number n ? n.intValue() : -1));
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
        // Гексы, куда вообще можно встать жетоном (остальные картон не кормит).
        List<String> free = new ArrayList<>();
        for (Hex h : field.hexes.values()) {
            h.containerCell = -1;
            h.energyCell = -1;
            if (h.kind != HexKind.FORBIDDEN && h.spawnTile == null) {
                free.add(h.id);
            }
        }

        List<Face> all = faces(dataRoot);
        if (all.isEmpty()) {
            // Набора блоков нет — печати не будет вовсе. Разыгрывать жёлтые
            // ячейки самим тут нельзя: они напечатаны на том же картоне, что и
            // контейнеры, и без картона их взять неоткуда.
            return;
        }
        List<Face> small = new ArrayList<>();
        List<Face> big = new ArrayList<>();
        for (Face f : all) {
            ("small".equals(f.kind()) ? small : big).add(f);
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
            // Перемешиваем ПАРАМИ: контейнер и жёлтая ячейка одного гекса
            // картона едут вместе, иначе жёлтая может встать на контейнерную.
            List<Cell> cells = new ArrayList<>(face.cells());
            Collections.shuffle(cells, rng);
            // ПОТОЛОК ПРАВИЛА (правило-вариант containers.printed_per_small_block /
            // printed_per_big_block, запрос дизайнера 13.08.2026): срезать
            // контейнеры блока сверх заданного числа. С набором 1.4.0 (3 на
            // малом, 4 на большом) потолки по умолчанию 4/5 ничего не режут —
            // они остались для вариантов правил, которым нужно ЕЩЁ реже.
            int limit = piece.size() >= 6
                ? ((Number) rules.get("containers.printed_per_big_block", 5)).intValue()
                : ((Number) rules.get("containers.printed_per_small_block", 4)).intValue();
            int placed = 0;
            for (int i = 0; i < piece.size(); i++) {
                Cell printed = cells.get(i % cells.size());
                int cell = printed.container();
                if (cell >= 0) {
                    if (placed >= limit) {
                        cell = -1;      // контейнеров на блоке уже достаточно
                    } else {
                        placed++;
                    }
                }
                // cell < 0 — гекс блока БЕЗ контейнера (с набора 1.4.0 малый
                // блок несёт 3 контейнера на 5 гексов, большой 4 на 6).
                // Поворот блока на столе двигает НАЗЕМНЫЕ ячейки по кругу,
                // воздушная остаётся.
                Hex h = field.get(piece.get(i));
                h.containerCell =
                    cell < 0 ? -1 : cell == AIR ? AIR : Math.floorMod(cell + rot, 6);
                // ЖЁЛТАЯ ЯЧЕЙКА крутится тем же поворотом: она напечатана на том
                // же картоне и относительно контейнера стоит намертво.
                h.energyCell = printed.energy() < 0 ? -1
                    : Math.floorMod(printed.energy() + rot, 6);
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
