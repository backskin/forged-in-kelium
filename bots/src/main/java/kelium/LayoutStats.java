package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.core.Field;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.dataio.GameConfig;
import kelium.engine.Scenario;
import kelium.engine.BlockStamp;

/**
 * LayoutStats — ЧЕМ РАСКЛАДКИ ДИЗАЙНЕРА ОТЛИЧАЮТСЯ ОТ СТАРЫХ ВСТРОЕННЫХ.
 *
 * <p>Повод (13.08.2026): на нарисованном дизайнером поле печатные контейнеры дают
 * 14.9 за партию, а на старых встроенных вариантах, которые движок берёт «по сиду»,
 * — 51.9. Разница в три с половиной раза, то есть кран в первую очередь в
 * ГЕОМЕТРИИ поля, а не в правилах. Прежде чем делать новые поля, надо назвать
 * числом, в чём именно разница.
 *
 * <p>Меряется по каждой раскладке: сколько гексов доступно, сколько запретных,
 * сколько тайлов зарождения (большие/малые), сколько нейтралов, плотность (гексов
 * на игрока), средняя связность (соседей у гекса), расстояния между стартами и
 * сколько печатных контейнерных ячеек получается на этом поле.
 *
 * <p>Запуск: {@code kelium.LayoutStats [игроков]}.
 */
public final class LayoutStats {

    private LayoutStats() {
    }

    private record Stat(String name, int hexes, int forbidden, int spawnBig, int spawnSmall,
                        int neutrals, double neighbours, int startMin, double startAvg,
                        int cells, double cellsPerHex) { }

    private static Stat measure(String name, Map<String, Object> scn, int players) {
        Scenario.FieldWithStarts fw = Scenario.buildFieldFromScenario(scn);
        Field field = fw.field();
        int hexes = 0;
        int forbidden = 0;
        int big = 0;
        int small = 0;
        int neutrals = 0;
        long nbSum = 0;
        for (Hex h : field.hexes.values()) {
            if (h.kind == HexKind.FORBIDDEN) {
                forbidden++;
                continue;
            }
            hexes++;
            nbSum += h.neighbors.size();
            neutrals += h.neutrals.size();
            if (h.spawnTile != null) {
                if (h.spawnTile.isStart) {
                    small++;
                } else {
                    big++;
                }
            }
        }
        // Расстояния между стартами — по числу шагов, а не по координатам.
        List<String> starts = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            String hx = fw.starts().get(seat);
            if (hx != null) {
                starts.add(hx);
            }
        }
        int min = Integer.MAX_VALUE;
        double sum = 0;
        int pairs = 0;
        for (int i = 0; i < starts.size(); i++) {
            for (int j = i + 1; j < starts.size(); j++) {
                int d = steps(field, starts.get(i), starts.get(j));
                min = Math.min(min, d);
                sum += d;
                pairs++;
            }
        }
        BlockStamp.stamp(field, GameConfig.resolveDataRoot(null), new Random(1),
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players, 1L, null, null).ruleset);
        int cells = 0;
        for (Hex h : field.hexes.values()) {
            if (h.containerCell >= 0) {
                cells++;
            }
        }
        return new Stat(name, hexes, forbidden, big, small, neutrals,
            hexes == 0 ? 0 : (double) nbSum / hexes,
            min == Integer.MAX_VALUE ? 0 : min, pairs == 0 ? 0 : sum / pairs,
            cells, hexes == 0 ? 0 : (double) cells / hexes);
    }

    /** Кратчайший путь в шагах по соседству гексов (поле маленькое, хватит обхода). */
    private static int steps(Field field, String from, String to) {
        Map<String, Integer> dist = new java.util.LinkedHashMap<>();
        java.util.Deque<String> q = new java.util.ArrayDeque<>();
        dist.put(from, 0);
        q.add(from);
        while (!q.isEmpty()) {
            String cur = q.poll();
            if (cur.equals(to)) {
                return dist.get(cur);
            }
            for (String nb : field.neighbors(cur)) {
                if (!dist.containsKey(nb)) {
                    dist.put(nb, dist.get(cur) + 1);
                    q.add(nb);
                }
            }
        }
        return -1;
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        Path root = GameConfig.resolveDataRoot(null);

        List<Stat> builtin = new ArrayList<>();
        for (Map<String, Object> v : Scenario.loadAllVariants(players, "1.0.0", root)) {
            builtin.add(measure("встроенная: " + v.get("id"), v, players));
        }

        List<Stat> drawn = new ArrayList<>();
        Path newDir = root.resolve("scenarios").resolve("new");
        if (Files.isDirectory(newDir)) {
            try (var st = Files.list(newDir)) {
                for (Path f : st.filter(p -> p.toString().endsWith(".yaml")).toList()) {
                    String base = f.getFileName().toString().replace(".yaml", "");
                    if (!base.contains(players + " игрок")) {
                        continue;   // раскладка на другой состав
                    }
                    for (Map<String, Object> v : Scenario.loadVariantsFromFile(f)) {
                        drawn.add(measure("нарисована: " + base, v, players));
                    }
                }
            }
        }

        out.printf("РАСКЛАДКИ НА %d ИГРОКОВ%n%n", players);
        out.println("| раскладка | гексов | запретных | зарождений Б/М | нейтралов "
            + "| соседей у гекса | старты мин/сред | контейнерных ячеек | ячеек на гекс |");
        out.println("|---|---|---|---|---|---|---|---|---|");
        List<Stat> all = new ArrayList<>(builtin);
        all.addAll(drawn);
        for (Stat s : all) {
            out.printf(Locale.ROOT,
                "| %s | %d | %d | %d/%d | %d | %.2f | %d/%.1f | %d | %.2f |%n",
                s.name(), s.hexes(), s.forbidden(), s.spawnBig(), s.spawnSmall(),
                s.neutrals(), s.neighbours(), s.startMin(), s.startAvg(),
                s.cells(), s.cellsPerHex());
        }
        out.printf("%nгексов на игрока: встроенные %.1f, нарисованные %.1f%n",
            avg(builtin, players), avg(drawn, players));
    }

    private static double avg(List<Stat> list, int players) {
        if (list.isEmpty()) {
            return 0;
        }
        double s = 0;
        for (Stat st : list) {
            s += (double) st.hexes() / players;
        }
        return s / list.size();
    }
}
