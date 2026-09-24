package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ГЕОМЕТРИЯ ПОЛЕЙ (25.09.2026): можно ли с первого раунда создать угрозу и
 * сколько у игрока путей к соперникам — для разговора о раскладках.
 *
 * <p>По каждой раскладке на {@code игроков}:
 * <ul>
 *   <li>расстояние (в шагах наземного войска) от ЦУ до ближайшего чужого ЦУ;</li>
 *   <li>число НЕЗАВИСИМЫХ путей от своего ЦУ к чужим (пути не делят гексов,
 *       кроме конечных) — «пути выхода» дизайнера;</li>
 *   <li>сколько шагов нужно, чтобы встать РЯДОМ с чужим ЦУ: из стартового гекса
 *       и из соседнего с ним (где можно поставить военное здание).</li>
 * </ul>
 * Проходимость — наземная: запретные гексы и тайлы зарождения закрыты, стенки
 * зданий и войска не учитываются (их на старте почти нет).
 *
 * <p>Запуск: {@code kelium.ГеометрияПолей [игроков]}.
 */
public final class ГеометрияПолей {

    private ГеометрияПолей() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int игроков = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int раскладок = Math.max(1, LayoutLibrary.pool(игроков).size());
        out.printf("раскладок на %d игроков: %d%n%n", игроков, раскладок);
        out.println("раскладка                     | гексов | ЦУ→чужое ЦУ (мин/ср) | путей к соперникам (по местам) | шагов до «рядом с чужим ЦУ»: со старта / с соседнего");
        Set<String> видели = new HashSet<>();
        for (long seed = 0; seed < раскладок * 3L; seed++) {
            GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
            String имя = String.valueOf(LayoutLibrary.pool(игроков)
                .get((int) Math.floorMod(seed, раскладок)).id());
            if (!видели.add(имя)) {
                continue;
            }
            List<String> старты = new ArrayList<>();
            for (int i = 0; i < игроков; i++) {
                старты.add(s.player(i).startHex);
            }
            int мин = Integer.MAX_VALUE;
            double сумма = 0;
            int пар = 0;
            StringBuilder пути = new StringBuilder();
            StringBuilder рядом = new StringBuilder();
            for (int i = 0; i < игроков; i++) {
                Map<String, Integer> d = расстояния(s, старты.get(i));
                for (int j = 0; j < игроков; j++) {
                    if (j != i && d.containsKey(старты.get(j))) {
                        мин = Math.min(мин, d.get(старты.get(j)));
                        сумма += d.get(старты.get(j));
                        пар++;
                    }
                }
                Set<String> чужие = new HashSet<>(старты);
                чужие.remove(старты.get(i));
                пути.append(независимыхПутей(s, старты.get(i), чужие)).append(' ');
                // шаги до гекса, соседнего с ближайшим чужим ЦУ
                Set<String> цели = new HashSet<>();
                for (String ч : чужие) {
                    for (String nb : s.field.neighbors(ч)) {
                        if (проходим(s, nb)) {
                            цели.add(nb);
                        }
                    }
                }
                int соСтарта = доЦели(d, цели);
                int сСоседнего = Integer.MAX_VALUE;
                for (String nb : s.field.neighbors(старты.get(i))) {
                    if (проходим(s, nb)) {
                        сСоседнего = Math.min(сСоседнего, доЦели(расстояния(s, nb), цели));
                    }
                }
                рядом.append(соСтарта).append('/').append(сСоседнего).append(' ');
            }
            out.printf("%-29s | %6d | %4d / %4.1f          | %-30s | %s%n", имя,
                s.field.hexes.size(), мин, пар == 0 ? 0 : сумма / пар, пути.toString().trim(),
                рядом.toString().trim());
        }
        out.println("\nСкорости по своду: пехота и техника 2, авиация 3 (у фракций 1–3).");
    }

    private static boolean проходим(GameState s, String id) {
        Hex h = s.field.get(id);
        return h != null && h.kind != HexKind.FORBIDDEN && h.spawnTile == null;
    }

    private static Map<String, Integer> расстояния(GameState s, String от) {
        Map<String, Integer> d = new HashMap<>();
        ArrayDeque<String> q = new ArrayDeque<>();
        d.put(от, 0);
        q.add(от);
        while (!q.isEmpty()) {
            String x = q.poll();
            for (String nb : s.field.neighbors(x)) {
                if (!d.containsKey(nb) && (проходим(s, nb) || s.field.get(nb).kind == HexKind.START)) {
                    d.put(nb, d.get(x) + 1);
                    q.add(nb);
                }
            }
        }
        return d;
    }

    private static int доЦели(Map<String, Integer> d, Set<String> цели) {
        int best = Integer.MAX_VALUE;
        for (String c : цели) {
            if (d.containsKey(c)) {
                best = Math.min(best, d.get(c));
            }
        }
        return best;
    }

    /**
     * Сколько путей от {@code от} к множеству {@code цели} не делят промежуточных
     * гексов (теорема Менгера: максимальный поток с расщеплением вершин).
     */
    private static int независимыхПутей(GameState s, String от, Set<String> цели) {
        // вершина v → (v_in, v_out) с ёмкостью 1; источник — от_out, сток — общий
        Map<String, Integer> номер = new HashMap<>();
        for (String id : s.field.hexes.keySet()) {
            номер.put(id, номер.size());
        }
        int n = номер.size();
        int сток = 2 * n;
        Map<Long, Integer> cap = new HashMap<>();
        Map<Integer, List<Integer>> adj = new HashMap<>();
        for (String id : s.field.hexes.keySet()) {
            boolean ok = проходим(s, id) || id.equals(от) || цели.contains(id);
            if (!ok) {
                continue;
            }
            int v = номер.get(id);
            ребро(adj, cap, 2 * v, 2 * v + 1, id.equals(от) ? 99 : 1);
            if (цели.contains(id)) {
                ребро(adj, cap, 2 * v + 1, сток, 99);
            }
            for (String nb : s.field.neighbors(id)) {
                boolean ok2 = проходим(s, nb) || nb.equals(от) || цели.contains(nb);
                if (ok2 && !цели.contains(id)) {
                    ребро(adj, cap, 2 * v + 1, 2 * номер.get(nb), 1);
                }
            }
        }
        int исток = 2 * номер.get(от) + 1;
        int поток = 0;
        while (true) {
            Map<Integer, Integer> откуда = new HashMap<>();
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(исток);
            откуда.put(исток, -1);
            while (!q.isEmpty() && !откуда.containsKey(сток)) {
                int x = q.poll();
                for (int y : adj.getOrDefault(x, List.of())) {
                    if (!откуда.containsKey(y) && cap.getOrDefault(ключ(x, y), 0) > 0) {
                        откуда.put(y, x);
                        q.add(y);
                    }
                }
            }
            if (!откуда.containsKey(сток)) {
                return поток;
            }
            for (int y = сток; y != исток; y = откуда.get(y)) {
                int x = откуда.get(y);
                cap.merge(ключ(x, y), -1, Integer::sum);
                cap.merge(ключ(y, x), 1, Integer::sum);
            }
            поток++;
        }
    }

    private static void ребро(Map<Integer, List<Integer>> adj, Map<Long, Integer> cap,
                              int a, int b, int c) {
        adj.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
        adj.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
        cap.merge(ключ(a, b), c, Integer::sum);
    }

    private static long ключ(int a, int b) {
        return ((long) a << 32) | (b & 0xffffffffL);
    }
}
