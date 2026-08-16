package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ДИАГНОЗ ВОЙНЫ — почему в живых партиях дизайнера дерутся много, а в симуляции
 * мало.
 *
 * <p>Наблюдение дизайнера 2026-08-15: «в реальных партиях к концу игры почти
 * каждый игрок уничтожает по 3–5 жетонов за последние два раунда». В симуляции
 * выходит 1.3–2.7 жетона за ВСЮ партию. Разрыв такой, что дело не в настройке —
 * что-то устроено иначе.
 *
 * <p>Стенд не гадает, а раскладывает партию ПО РАУНДАМ и смотрит, где обрывается
 * то, что дизайнер описывает как кульминацию:
 *
 * <ul>
 *   <li><b>бои и уничтожения по раундам</b> — растёт ли война к концу партии, и
 *       на каком раунде партия обрывается;</li>
 *   <li><b>чем кончилась партия</b> и на каком раунде — если игра заканчивается
 *       ДО кульминации, то никакой войны в ней не будет по построению;</li>
 *   <li><b>сколько войск и зданий на поле по раундам</b> — есть ли вообще кого
 *       бить: война невозможна, если поле пустое;</li>
 *   <li><b>боеприпасы</b> — не упирается ли всё в то, что бить нечем.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.WarDiagnosis [партий] [игроков]}.
 */
public final class WarDiagnosis {

    private WarDiagnosis() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> lineup = args.length > 2 ? List.of(args[2].split(","))
            : List.of("reaper", "warlord", "axiom", "balanced");

        int maxRound = 16;
        double[] kills = new double[maxRound + 1];
        double[] hits = new double[maxRound + 1];
        double[] combats = new double[maxRound + 1];
        double[] unitsOnField = new double[maxRound + 1];
        double[] ammoHeld = new double[maxRound + 1];
        double[] hired = new double[maxRound + 1];          // нанято войск за раунд
        double[] assemblyActs = new double[maxRound + 1];   // действий Сборка
        double[] energyFree = new double[maxRound + 1];     // свободных кубиков энергии
        double[] reached = new double[maxRound + 1];   // сколько партий дожило до раунда
        Map<String, Integer> endBy = new TreeMap<>();
        Map<Integer, Integer> endRound = new TreeMap<>();

        for (int g = 0; g < games; g++) {
            long seed = 6_100_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get((i + g) % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
                int r = ev.get("round") instanceof Number n ? n.intValue() : s.round;
                if (r < 0 || r > maxRound) {
                    return;
                }
                switch (String.valueOf(ev.get("type"))) {
                    case "combat_hit" -> {
                        hits[r]++;
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            kills[r]++;
                        }
                    }
                    case "action" -> {
                        String a = String.valueOf(ev.get("action"));
                        boolean ok = Boolean.TRUE.equals(ev.get("ok"));
                        if ("combat".equals(a) && ok) {
                            combats[r]++;
                        }
                        if ("assembly".equals(a) && ok) {
                            assemblyActs[r]++;
                        }
                        if (ok && ev.get("telemetry") instanceof Map<?, ?> tel
                                && tel.get("units") instanceof Number u) {
                            hired[r] += u.intValue();
                        }
                    }
                    // Срез поля берём на этапе Обновления: он один раз за раунд и
                    // приходит ПОСЛЕ всех действий, то есть показывает раунд целиком.
                    case "refresh" -> {
                        reached[r]++;
                        for (int i = 0; i < players; i++) {
                            unitsOnField[r] += s.player(i).unitsOnField().size();
                            ammoHeld[r] += s.player(i).resources.ammo();
                            // «Свободная энергия» — кубики, лежащие на источниках
                            // и НЕ поставленные на потребителей: это запас, которым
                            // можно оплатить Сборку прямо сейчас.
                            for (var b : s.player(i).buildingsOnField()) {
                                energyFree[r] += b.energyIdle;
                            }
                        }
                    }
                    default -> { }
                }
            });
            endBy.merge(String.valueOf(res.get("condition")), 1, Integer::sum);
            endRound.merge(res.get("rounds") instanceof Number r ? r.intValue() : 0,
                1, Integer::sum);
        }

        StringBuilder md = new StringBuilder();
        md.append("# Диагноз войны: что происходит по раундам\n\n");
        md.append("Партий: ").append(games).append(", игроков: ").append(players)
          .append(", линии: ").append(String.join(", ", lineup))
          .append(", боты ").append(Bots.describe()).append(".\n\n");
        md.append("Вопрос дизайнера: в живых партиях к концу игры каждый игрок сносит "
            + "3–5 жетонов за последние два раунда. Проверяем, доживает ли партия "
            + "до этого места вообще.\n\n");
        md.append("| раунд | дожило партий | боёв | попаданий | УНИЧТОЖЕНО |"
            + " войск на поле | боеприпасов на руках |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|\n");
        out.printf("%6s %8s %8s %10s %12s %10s %10s%n", "раунд", "дожило", "боёв",
            "попаданий", "УНИЧТОЖЕНО", "войск", "боеприп.");
        for (int r = 1; r <= maxRound; r++) {
            if (reached[r] < 1) {
                continue;
            }
            double alive = reached[r];
            md.append(String.format(Locale.ROOT,
                "| %d | %.0f (%.0f%%) | %.2f | %.2f | **%.2f** | %.1f | %.2f | %.2f"
                + " | %.1f | %.1f |%n",
                r, alive, 100 * alive / games, combats[r] / alive, hits[r] / alive,
                kills[r] / alive, unitsOnField[r] / alive, hired[r] / alive,
                assemblyActs[r] / alive, ammoHeld[r] / alive, energyFree[r] / alive));
            out.printf(Locale.ROOT,
                "%6d %6.0f%% %7.2f %9.2f %11.2f %8.1f %8.2f %8.2f %9.1f %8.1f%n",
                r, 100 * alive / games, combats[r] / alive, hits[r] / alive,
                kills[r] / alive, unitsOnField[r] / alive, hired[r] / alive,
                assemblyActs[r] / alive, ammoHeld[r] / alive, energyFree[r] / alive);
        }
        md.append("\nЧисла на игрока в раунде делить на ").append(players)
          .append(" (в таблице — на весь стол).\n\n");

        md.append("## Чем и когда кончаются партии\n\n| условие | доля |\n|---|---:|\n");
        out.println("\nчем кончилось:");
        for (var e : endBy.entrySet()) {
            md.append(String.format("| %s | %d%% |%n", e.getKey(),
                100 * e.getValue() / games));
            out.printf("  %-22s %d%%%n", e.getKey(), 100 * e.getValue() / games);
        }
        md.append("\n| последний раунд | доля партий |\n|---|---:|\n");
        out.println("на каком раунде:");
        for (var e : endRound.entrySet()) {
            md.append(String.format("| %d | %d%% |%n", e.getKey(),
                100 * e.getValue() / games));
            out.printf("  раунд %-3d %d%%%n", e.getKey(), 100 * e.getValue() / games);
        }

        Path p = Path.of("reports", "balance", "диагноз-войны.md");
        Files.createDirectories(p.getParent());
        Files.writeString(p, md.toString(), StandardCharsets.UTF_8);
        out.println("\nотчёт: " + p.toAbsolutePath());
    }
}
