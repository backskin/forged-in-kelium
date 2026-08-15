package kelium.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BlockAssembler — сборка нарисованного поля из ФИЗИЧЕСКИХ блоков гексов.
 *
 * <p>У дизайнера на руках два вида цельных блоков (их можно вращать и
 * отражать) и стопка чёрных накладок «недоступный гекс»:
 * <pre>
 *   малый (5 гексов)      большой (6 гексов)
 *     ⬡ ⬡ ⬡                  ⬡
 *      ⬡ ⬡                 ⬡ ⬡ ⬡
 *                           ⬡ ⬡
 * </pre>
 *
 * <p>Задача: покрыть блоками ВСЕ игровые гексы раскладки, не накладывая блоки
 * друг на друга. Блок — цельный кусок картона: он может высунуться за пределы
 * задуманного поля, и каждый такой лишний гекс (а равно нарисованный запретный,
 * если он попал под блок) закрывается ЧЁРНОЙ накладкой. Отсутствие гекса и
 * чёрная накладка для игры равнозначны, поэтому запретные гексы покрывать не
 * обязательно — их можно просто оставить пустым местом.
 *
 * <p>Решается перебором с отсечениями: на каждом шаге берём первый непокрытый
 * игровой гекс и пробуем все положения блоков, которые его накрывают. Порядок
 * кандидатов — по возрастанию числа нужных накладок, поэтому «аккуратные»
 * сборки находятся первыми.
 */
public final class BlockAssembler {

    private BlockAssembler() {
    }

    /** Клетка поля в осевых координатах (как в редакторе и симуляторе). */
    public record Cell(int q, int r) { }

    /** Поставленный блок: 6 или 5 гексов и занятые им клетки. */
    public record Placement(int size, List<Cell> cells) { }

    /**
     * Итог сборки.
     *
     * @param status  OK · IMPOSSIBLE (перебор исчерпан) · TIMEOUT (не успели)
     * @param blocks  поставленные блоки
     * @param blacks  клетки под чёрными накладками
     * @param optimal доказано ли, что меньше накладок не бывает (иначе — лучшее
     *                из найденного за отведённое время)
     */
    public record Result(Status status, List<Placement> blocks, List<Cell> blacks,
                         long nodes, long millis, boolean optimal) {

        public int bigUsed() {
            return (int) blocks.stream().filter(b -> b.size() == 6).count();
        }

        public int smallUsed() {
            return (int) blocks.stream().filter(b -> b.size() == 5).count();
        }
    }

    /** Чем закончился перебор. */
    public enum Status { OK, IMPOSSIBLE, TIMEOUT, EMPTY }

    // ==================== формы блоков ====================
    private static final int[][] DIRS = kelium.core.Field.AXIAL_DIRS;

    /** Малый блок: ряд из трёх и ряд из двух под ним. */
    private static final List<Cell> SMALL = List.of(
        new Cell(0, 0), new Cell(1, 0), new Cell(2, 0),
        new Cell(0, 1), new Cell(1, 1));

    /** Большой блок: малый плюс один гекс сверху. */
    private static final List<Cell> BIG = List.of(
        new Cell(0, 0), new Cell(1, 0), new Cell(2, 0),
        new Cell(0, 1), new Cell(1, 1),
        new Cell(1, -1));

    /** Поворот на 60° по часовой в осевых координатах. */
    private static Cell rot(Cell c) {
        return new Cell(-c.r(), c.q() + c.r());
    }

    /** Отражение (даёт вторую половину из 12 симметрий). */
    private static Cell mirror(Cell c) {
        return new Cell(c.q() + c.r(), -c.r());
    }

    /** Все различные ориентации формы: 6 поворотов × отражение. */
    public static List<List<Cell>> orientations(List<Cell> shape) {
        Set<String> seen = new HashSet<>();
        List<List<Cell>> out = new ArrayList<>();
        for (int m = 0; m < 2; m++) {
            List<Cell> cur = new ArrayList<>(shape);
            if (m == 1) {
                cur.replaceAll(BlockAssembler::mirror);
            }
            for (int r = 0; r < 6; r++) {
                List<Cell> norm = normalize(cur);
                if (seen.add(key(norm))) {
                    out.add(norm);
                }
                cur.replaceAll(BlockAssembler::rot);
            }
        }
        return out;
    }

    /** Сдвинуть форму так, чтобы её первая (по r, затем q) клетка была в нуле. */
    private static List<Cell> normalize(List<Cell> cells) {
        List<Cell> s = new ArrayList<>(cells);
        s.sort(Comparator.comparingInt(Cell::r).thenComparingInt(Cell::q));
        Cell first = s.get(0);
        List<Cell> out = new ArrayList<>(s.size());
        for (Cell c : s) {
            out.add(new Cell(c.q() - first.q(), c.r() - first.r()));
        }
        return out;
    }

    private static String key(List<Cell> cells) {
        StringBuilder sb = new StringBuilder();
        for (Cell c : cells) {
            sb.append(c.q()).append(':').append(c.r()).append(';');
        }
        return sb.toString();
    }

    // ==================== перебор ====================

    /**
     * Подобрать сборку.
     *
     * @param playable  игровые гексы — обязаны быть покрыты блоками
     * @param maxBig    сколько больших блоков в наличии
     * @param maxSmall  сколько малых блоков
     * @param maxBlack  сколько чёрных накладок
     * @param budgetMs  предел времени на перебор
     */
    public static Result solve(Set<Cell> playable, int maxBig, int maxSmall,
                               int maxBlack, long budgetMs) {
        return solve(playable, maxBig, maxSmall, maxBlack, budgetMs, null);
    }

    /**
     * Найти НЕСКОЛЬКО разных сборок одного поля — чтобы кнопку «Пересобрать»
     * было зачем нажимать (просьба дизайнера 12.08.2026).
     *
     * <p>Как получаются разные варианты: перебор всегда берёт первый подошедший
     * блок из списка кандидатов, поэтому достаточно ПЕРЕТАСОВАТЬ этот список
     * своим зерном — и та же задача решится другим способом. Все варианты
     * укладываются в одинаковое (минимальное) число чёрных накладок: показывать
     * заведомо худшую сборку смысла нет.
     *
     * @param wanted сколько разных вариантов хочется набрать
     * @return список различных сборок; первый — тот же, что вернул бы solve()
     */
    public static List<Result> solveVariants(Set<Cell> playable, int maxBig, int maxSmall,
                                             int maxBlack, long budgetMs, int wanted) {
        long t0 = System.currentTimeMillis();
        List<Result> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Result first = solve(playable, maxBig, maxSmall, maxBlack, budgetMs, null);
        if (first.status() != Status.OK) {
            return List.of(first);
        }
        out.add(first);
        seen.add(signature(first));
        int target = first.blacks().size();
        for (long seed = 1; out.size() < wanted; seed++) {
            if (System.currentTimeMillis() - t0 > budgetMs * 3 || seed > 60) {
                break;
            }
            Result r = solve(playable, maxBig, maxSmall, target, budgetMs, seed);
            if (r.status() != Status.OK || r.blacks().size() != target) {
                continue;
            }
            if (seen.add(signature(r))) {
                out.add(r);
            }
        }
        return out;
    }

    /** Отпечаток сборки: набор блоков без учёта порядка их постановки. */
    private static String signature(Result r) {
        List<String> parts = new ArrayList<>();
        for (Placement p : r.blocks()) {
            parts.add(key(p.cells()));
        }
        java.util.Collections.sort(parts);
        return String.join("|", parts);
    }

    private static Result solve(Set<Cell> playable, int maxBig, int maxSmall,
                                int maxBlack, long budgetMs, Long shuffleSeed) {
        long t0 = System.currentTimeMillis();
        if (playable.isEmpty()) {
            return new Result(Status.EMPTY, List.of(), List.of(), 0, 0, true);
        }

        // Вселенная: игровые клетки плюс два кольца вокруг — блок вправе
        // высунуться за край поля, но недалеко.
        Set<Cell> universe = new LinkedHashSet<>(playable);
        for (int ring = 0; ring < 2; ring++) {
            for (Cell c : new ArrayList<>(universe)) {
                for (int[] d : DIRS) {
                    universe.add(new Cell(c.q() + d[0], c.r() + d[1]));
                }
            }
        }
        List<Cell> cells = new ArrayList<>(universe);
        cells.sort(Comparator.comparingInt(Cell::r).thenComparingInt(Cell::q));
        Map<Cell, Integer> index = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            index.put(cells.get(i), i);
        }
        if (cells.size() > 128) {
            return new Result(Status.TIMEOUT, List.of(), List.of(), 0,
                System.currentTimeMillis() - t0, false);   // поле больше маски
        }

        // Порядок покрытия: игровые клетки сверху вниз, слева направо.
        List<Integer> targets = new ArrayList<>();
        for (Cell c : cells) {
            if (playable.contains(c)) {
                targets.add(index.get(c));
            }
        }

        // Заготовки: для каждой клетки — все положения блоков, её накрывающие.
        List<List<Cand>> byCell = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            byCell.add(new ArrayList<>());
        }
        for (int kind = 0; kind < 2; kind++) {
            List<Cell> shape = kind == 0 ? BIG : SMALL;
            int size = kind == 0 ? 6 : 5;
            for (List<Cell> or : orientations(shape)) {
                for (Cell anchor : cells) {
                    for (Cell pivot : or) {
                        int dq = anchor.q() - pivot.q();
                        int dr = anchor.r() - pivot.r();
                        long lo = 0;
                        long hi = 0;
                        int blacks = 0;
                        boolean fits = true;
                        List<Cell> placed = new ArrayList<>(size);
                        for (Cell c : or) {
                            Cell p = new Cell(c.q() + dq, c.r() + dr);
                            Integer idx = index.get(p);
                            if (idx == null) {
                                fits = false;
                                break;
                            }
                            if (idx < 64) {
                                lo |= 1L << idx;
                            } else {
                                hi |= 1L << (idx - 64);
                            }
                            if (!playable.contains(p)) {
                                blacks++;
                            }
                            placed.add(p);
                        }
                        if (!fits) {
                            continue;
                        }
                        Cand cand = new Cand(size, lo, hi, blacks, placed);
                        int ai = index.get(anchor);
                        // класть кандидата только в список «своей» клетки-якоря
                        if (!containsCand(byCell.get(ai), cand)) {
                            byCell.get(ai).add(cand);
                        }
                    }
                }
            }
        }
        // Сначала пробуем варианты, требующие меньше чёрных накладок.
        for (List<Cand> lst : byCell) {
            lst.sort(Comparator.comparingInt((Cand c) -> c.blacks)
                .thenComparingInt(c -> -c.size));
        }

        // Ищем сборку с МИНИМАЛЬНЫМ числом чёрных накладок: перебираем порог
        // сверху вниз. Как только нашли решение с k накладками, следующий заход
        // ищет строго лучше (k−1); первый же неуспех означает, что k —
        // оптимум. Нижняя граница считается арифметически: блоки кратны 5 и 6,
        // поэтому меньше некоторого остатка накладок не бывает физически.
        int lower = minimalBlacks(playable.size(), maxBig, maxSmall);
        // Перетасовка кандидатов даёт ДРУГОЕ решение той же задачи: перебор
        // берёт первый подошедший вариант, значит порядок и определяет ответ.
        if (shuffleSeed != null) {
            java.util.Random rnd = new java.util.Random(shuffleSeed * 1103515245L + 12345L);
            for (List<Cand> list : byCell) {
                java.util.Collections.shuffle(list, rnd);
            }
        }
        Search best = null;
        int limit = maxBlack;
        long ms;
        boolean timedOut = false;
        long nodes = 0;
        while (limit >= lower) {
            Search s = new Search(byCell, targets, playable, cells, limit, t0 + budgetMs);
            boolean ok = s.go(0L, 0L, maxBig, maxSmall, 0);
            nodes += s.nodes;
            if (ok) {
                best = s;
                int used = 0;
                Set<Cell> cov = new HashSet<>();
                for (Placement p : s.stack) {
                    cov.addAll(p.cells());
                }
                for (Cell c : cov) {
                    if (!playable.contains(c)) {
                        used++;
                    }
                }
                limit = used - 1;      // в следующий раз — строго лучше
                if (used <= lower) {
                    break;             // лучше уже невозможно
                }
            } else {
                timedOut = s.timedOut;
                break;                 // с меньшим числом накладок не выходит
            }
            if (System.currentTimeMillis() > t0 + budgetMs) {
                timedOut = true;
                break;
            }
        }
        ms = System.currentTimeMillis() - t0;
        if (best != null) {
            List<Cell> blacks = new ArrayList<>();
            Set<Cell> covered = new HashSet<>();
            for (Placement p : best.stack) {
                covered.addAll(p.cells());
            }
            for (Cell c : covered) {
                if (!playable.contains(c)) {
                    blacks.add(c);
                }
            }
            blacks.sort(Comparator.comparingInt(Cell::r).thenComparingInt(Cell::q));
            // Найден оптимум, если дошли до арифметического минимума либо
            // перебор честно доказал, что меньше нельзя (не по таймауту).
            Status st = Status.OK;
            return new Result(st, new ArrayList<>(best.stack), blacks, nodes, ms,
                blacks.size() <= lower || !timedOut);
        }
        return new Result(timedOut ? Status.TIMEOUT : Status.IMPOSSIBLE,
            List.of(), List.of(), nodes, ms, false);
    }

    /**
     * Сколько накладок понадобится минимум: блоки покрывают по 5 и 6 клеток,
     * поэтому суммарное покрытие почти никогда не равно числу игровых гексов —
     * остаток и есть неизбежные накладки.
     */
    public static int minimalBlacks(int playable, int maxBig, int maxSmall) {
        int best = Integer.MAX_VALUE;
        for (int b = 0; b <= maxBig; b++) {
            for (int s = 0; s <= maxSmall; s++) {
                int cover = b * 6 + s * 5;
                if (cover >= playable) {
                    best = Math.min(best, cover - playable);
                }
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private static boolean containsCand(List<Cand> list, Cand c) {
        for (Cand x : list) {
            if (x.lo == c.lo && x.hi == c.hi) {
                return true;
            }
        }
        return false;
    }

    /** Кандидат-положение блока: маска занятых клеток + цена в накладках. */
    private static final class Cand {
        final int size;
        final long lo;
        final long hi;
        final int blacks;
        final List<Cell> cells;

        Cand(int size, long lo, long hi, int blacks, List<Cell> cells) {
            this.size = size;
            this.lo = lo;
            this.hi = hi;
            this.blacks = blacks;
            this.cells = cells;
        }
    }

    /** Ключ памяти неудач: состояние покрытия + остаток ресурсов. */
    private record Memo(long lo, long hi, int big, int small, int black) { }

    private static final class Search {
        final List<List<Cand>> byCell;
        final List<Integer> targets;
        final Set<Cell> playable;
        final List<Cell> cells;
        final int maxBlack;
        final long deadline;
        final Set<Memo> dead = new HashSet<>();
        final List<Placement> stack = new ArrayList<>();
        long nodes;
        boolean timedOut;

        Search(List<List<Cand>> byCell, List<Integer> targets, Set<Cell> playable,
               List<Cell> cells, int maxBlack, long deadline) {
            this.byCell = byCell;
            this.targets = targets;
            this.playable = playable;
            this.cells = cells;
            this.maxBlack = maxBlack;
            this.deadline = deadline;
        }

        private static boolean bit(long lo, long hi, int idx) {
            return idx < 64 ? (lo & (1L << idx)) != 0 : (hi & (1L << (idx - 64))) != 0;
        }

        boolean go(long lo, long hi, int bigLeft, int smallLeft, int blacksUsed) {
            if (++nodes % 4096 == 0 && System.currentTimeMillis() > deadline) {
                timedOut = true;
                return false;
            }
            if (timedOut) {
                return false;
            }
            // первая непокрытая игровая клетка
            int target = -1;
            int remaining = 0;
            for (int idx : targets) {
                if (!bit(lo, hi, idx)) {
                    if (target < 0) {
                        target = idx;
                    }
                    remaining++;
                }
            }
            if (target < 0) {
                return true;                     // всё покрыто
            }
            if (remaining > bigLeft * 6 + smallLeft * 5) {
                return false;                    // блоков физически не хватит
            }
            Memo memo = new Memo(lo, hi, bigLeft, smallLeft, maxBlack - blacksUsed);
            if (dead.contains(memo)) {
                return false;
            }
            for (Cand c : byCell.get(target)) {
                if ((c.lo & lo) != 0 || (c.hi & hi) != 0) {
                    continue;                    // пересекается с уже стоящим блоком
                }
                if (c.size == 6 ? bigLeft == 0 : smallLeft == 0) {
                    continue;
                }
                if (blacksUsed + c.blacks > maxBlack) {
                    continue;
                }
                stack.add(new Placement(c.size, c.cells));
                boolean ok = go(lo | c.lo, hi | c.hi,
                    c.size == 6 ? bigLeft - 1 : bigLeft,
                    c.size == 5 ? smallLeft - 1 : smallLeft,
                    blacksUsed + c.blacks);
                if (ok) {
                    return true;
                }
                stack.remove(stack.size() - 1);
                if (timedOut) {
                    return false;
                }
            }
            dead.add(memo);
            return false;
        }
    }
}
