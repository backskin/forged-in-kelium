package kelium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * ContainerProbe — НЕ СЛОМАЛИСЬ ЛИ КОНТЕЙНЕРЫ после перехода на печатные ячейки.
 *
 * <p>Правило изменилось принципиально: контейнеры больше не выкладываются на поле
 * жетонами, а НАПЕЧАТАНЫ на картонных блоках, и карта достаётся тому, кто накрыл
 * ячейку своим жетоном. У такой замены есть два очевидных способа сломаться, и
 * оба надо проверять числом, а не рассуждением:
 * <ul>
 *   <li><b>бездонный источник</b> — контейнеров стало слишком много: ячейка не
 *       выгорает, значит на неё можно наступать снова и снова, и её доят;</li>
 *   <li><b>засуха</b> — контейнеров стало слишком мало (ячейки оказались в местах,
 *       куда никто не ходит, или их закрывают зданиями в первый же раунд).</li>
 * </ul>
 *
 * <p>Меряется: сколько печатных ячеек на поле, сколько контейнеров вскрыто за
 * партию, сколько осталось на руках, сколько ячеек в итоге накрыто зданиями
 * (то есть выведено из игры), и сколько контейнеров приходится на одного игрока.
 *
 * <p>Запуск: {@code kelium.ContainerProbe [игроков] [партий]}.
 */
public final class ContainerProbe {

    private ContainerProbe() {
    }

    private static final class Tally {
        int games;
        int cells;              // печатных ячеек на поле
        int airCells;           // из них воздушных
        int opened;             // вскрыто карт контейнеров
        int massOpened;         // из них массовым вскрытием
        int leftInHands;        // осталось на руках к концу
        int cellsUnderBuilding; // ячеек, накрытых зданием к концу партии
        int gamesWithoutAny;    // партий, где НИ ОДИН контейнер не вскрыт
        int rounds;
    }

    private static synchronized void merge(Tally into, Tally one) {
        into.games += one.games;
        into.cells += one.cells;
        into.airCells += one.airCells;
        into.opened += one.opened;
        into.massOpened += one.massOpened;
        into.leftInHands += one.leftInHands;
        into.cellsUnderBuilding += one.cellsUnderBuilding;
        into.gamesWithoutAny += one.gamesWithoutAny;
        into.rounds += one.rounds;
    }

    private static Tally playOne(int players, long seed) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        Tally t = new Tally();
        t.games = 1;
        for (Hex h : s.field.hexes.values()) {
            if (h.containerCell >= 0) {
                t.cells++;
                if (h.containerCell == kelium.engine.BlockStamp.AIR) {
                    t.airCells++;
                }
            }
        }

        List<Agent> agents = new ArrayList<>();
        String[] mixed = {"hawk", "dove", "balanced", "opportunist"};
        int shift = (int) (seed % players);
        for (int i = 0; i < players; i++) {
            agents.add(Bots.create(mixed[(i + shift) % mixed.length], i,
                new Random(seed * 31 + i), players));
        }
        Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
            if ("container".equals(String.valueOf(ev.get("type")))) {
                t.opened++;
            }
        });
        t.rounds = res.get("rounds") instanceof Number r ? r.intValue() : 0;
        for (int i = 0; i < players; i++) {
            t.leftInHands += s.player(i).containers;
        }
        if (t.opened == 0) {
            t.gamesWithoutAny = 1;
        }
        // Сколько печатных ячеек к концу партии накрыто ЗДАНИЕМ — такие ячейки из
        // игры выведены (жетон стоит там навсегда, ячейка больше не срабатывает).
        for (Hex h : s.field.hexes.values()) {
            if (h.containerCell >= 0 && h.containerCell != kelium.engine.BlockStamp.AIR
                    && h.sideOwner[h.containerCell] != null) {
                t.cellsUnderBuilding++;
            }
        }
        return t;
    }

    private static Tally run(int players, int games) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Tally total = new Tally();
        List<Future<Tally>> futures = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            final long seed = 5_500_000L + g;
            futures.add(pool.submit((Callable<Tally>) () -> playOne(players, seed)));
        }
        for (Future<Tally> f : futures) {
            try {
                merge(total, f.get());
            } catch (Exception e) {
                System.err.println("партия сорвалась: " + e.getMessage());
            }
        }
        pool.shutdown();
        return total;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 300;
        List<Integer> counts = args.length > 0
            ? List.of(Integer.parseInt(args[0])) : List.of(2, 3, 4);

        Map<Integer, Tally> results = new LinkedHashMap<>();
        Map<Integer, Map<String, Long>> sources = new LinkedHashMap<>();
        for (int n : counts) {
            kelium.engine.Storage.resetContainerStats();
            Tally t = run(n, games);
            sources.put(n, kelium.engine.Storage.containerStats());
            results.put(n, t);
            System.out.printf(Locale.ROOT,
                "  %dp: ячеек %.1f, вскрыто %.2f за партию (%.2f на игрока), "
                + "осталось %.2f, партий без контейнеров %.0f%%%n",
                n, t.cells / (double) t.games, t.opened / (double) t.games,
                t.opened / (double) t.games / n, t.leftInHands / (double) t.games,
                100.0 * t.gamesWithoutAny / t.games);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Контейнеры после перехода на печатные ячейки\n\n");
        sb.append("По ").append(games).append(" партий на состав. Контейнеры больше не ")
          .append("выкладываются жетонами — они НАПЕЧАТАНЫ на блоках поля, и карта ")
          .append("достаётся тому, кто накрыл ячейку своим жетоном. Проверяем два ")
          .append("способа сломаться: **бездонный источник** (ячейка не выгорает, её ")
          .append("доят без конца) и **засуха** (ячейки в мёртвых местах или закрыты ")
          .append("зданиями сразу).\n\n");
        sb.append("| игроков | печатных ячеек | из них воздушных | вскрыто за партию "
            + "| на игрока | осталось на руках | ячеек под зданиями к концу "
            + "| партий без контейнеров | раундов |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (int n : counts) {
            Tally t = results.get(n);
            int g = Math.max(1, t.games);
            sb.append(String.format(Locale.ROOT,
                "| %d | %.1f | %.1f | **%.2f** | %.2f | %.2f | %.2f | %.0f%% | %.1f |%n",
                n, t.cells / (double) g, t.airCells / (double) g, t.opened / (double) g,
                t.opened / (double) g / n, t.leftInHands / (double) g,
                t.cellsUnderBuilding / (double) g, 100.0 * t.gamesWithoutAny / g,
                t.rounds / (double) g));
        }
        sb.append("\n## ОТКУДА берутся контейнеры — все источники\n\n");
        sb.append("Все выдачи контейнеров в движке, помеченные по месту. Столбец — ")
          .append("сколько карт за партию приходит этим путём. Так видно, какой ")
          .append("источник даёт основной поток.\n\n");
        sb.append("| источник |");
        for (int n : counts) {
            sb.append(' ').append(n).append("p |");
        }
        sb.append("\n|---|");
        for (int i = 0; i < counts.size(); i++) {
            sb.append("---:|");
        }
        sb.append('\n');
        // Источники сортируем по вкладу при БОЛЬШЕМ составе — сверху главные.
        int ref = counts.get(counts.size() - 1);
        List<String> srcNames = new ArrayList<>(sources.get(ref).keySet());
        for (Map<String, Long> m : sources.values()) {
            for (String k : m.keySet()) {
                if (!srcNames.contains(k)) {
                    srcNames.add(k);
                }
            }
        }
        srcNames.sort(java.util.Comparator.comparingLong(
            (String k) -> -sources.get(ref).getOrDefault(k, 0L)));
        for (String srcName : srcNames) {
            sb.append("| ").append(srcName).append(" |");
            for (int n : counts) {
                int g = Math.max(1, results.get(n).games);
                sb.append(String.format(Locale.ROOT, " %.2f |",
                    sources.get(n).getOrDefault(srcName, 0L) / (double) g));
            }
            sb.append('\n');
        }
        sb.append("| **всего** |");
        for (int n : counts) {
            int g = Math.max(1, results.get(n).games);
            long sum = 0;
            for (long v : sources.get(n).values()) {
                sum += v;
            }
            sb.append(String.format(Locale.ROOT, " **%.2f** |", sum / (double) g));
        }
        sb.append('\n');

        sb.append("\n## Как это читать\n\n");
        sb.append("- **Вскрыто за партию** — сколько карт контейнеров реально ушло в ")
          .append("игру. Это главный показатель: если он близок к нулю, механика ")
          .append("мертва; если он в разы больше числа ячеек, ячейки доят.\n");
        sb.append("- **Ячеек под зданиями к концу** — сколько печатных ячеек выведено ")
          .append("из игры навсегда: здание встало и больше не сойдёт. Если таких ")
          .append("почти все, механика выключается сама к середине партии.\n");
        sb.append("- **Партий без контейнеров** — насколько часто механика не ")
          .append("срабатывает вообще.\n");

        String report = sb.toString();
        System.out.println();
        System.out.println(report);
        Path out = Path.of("reports", "balance", "контейнеры-печатные.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }
}
