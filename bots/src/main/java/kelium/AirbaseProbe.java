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
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * АВИАБАЗА: три ячейки энергии против двух — что меняется в партии.
 *
 * <p>Заказ дизайнера 13.08.2026. Замеры раньше показывали, что авиабаза почти
 * никогда не строится: она стоит дорого и просит три кубика энергии при общем
 * дефиците (источники дают 10, потребители держат 13). Вопрос — станет ли она
 * живой, если просить два.
 *
 * <p>Правка накладывается на КОПИЮ данных в памяти: печатные файлы не трогаются.
 *
 * <p>Запуск: {@code kelium.AirbaseProbe [игроков] [партий]}.
 */
public final class AirbaseProbe {

    private AirbaseProbe() {
    }

    private record Result(double airbases, double aircraft, double kills, double vp,
                          double rounds, double buildActions, Map<String, Integer> ends) {
    }

    @SuppressWarnings("unchecked")
    private static GameConfig config(int players, long seed, Integer airbaseSlots) {
        GameConfig base = LayoutLibrary.configFor(players, seed);
        if (airbaseSlots == null) {
            return base;
        }
        // Правим ЯЧЕЙКИ АВИАБАЗЫ в копии записи о жетонах. Копия обязательна: запись
        // из кеша контента общая на процесс, и правка «на месте» протекла бы во все
        // остальные замеры этого прогона.
        Map<String, Object> tokens = base.content.get("boards").find("tokens");
        Map<String, Object> copy = new java.util.LinkedHashMap<>(tokens);
        Map<String, Object> buildings =
            new java.util.LinkedHashMap<>((Map<String, Object>) copy.get("buildings"));
        Map<String, Object> airbase =
            new java.util.LinkedHashMap<>((Map<String, Object>) buildings.get("airbase"));
        airbase.put("energy_slots", airbaseSlots);
        buildings.put("airbase", airbase);
        copy.put("buildings", buildings);
        GameConfig cfg = new GameConfig(base.ruleset, base.content, players, seed,
            base.dataRoot, base.boardSides, base.scenarioId, base.cuFacing, base.scenarioFile);
        cfg.tokenStatsOverride = copy;
        return cfg;
    }

    private static Result run(int players, int games, Integer airbaseSlots) {
        double airbases = 0;
        double aircraft = 0;
        double kills = 0;
        double vp = 0;
        double rounds = 0;
        double buildActions = 0;
        Map<String, Integer> ends = new TreeMap<>();
        for (int g = 0; g < games; g++) {
            long seed = 7_700_000L + g;
            GameState s = Setup.buildGame(config(players, seed, airbaseSlots));
            List<Agent> agents = new ArrayList<>();
            List<String> lineup = List.of("hawk", "dove", "balanced", "opportunist");
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get(i % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            double[] acc = new double[2];
            GameEngine.playGame(s, agents, ev -> {
                String type = String.valueOf(ev.get("type"));
                if ("combat_hit".equals(type) && Boolean.TRUE.equals(ev.get("destroyed"))) {
                    acc[0]++;
                }
                if ("action".equals(type) && "build".equals(String.valueOf(ev.get("action")))) {
                    acc[1]++;
                }
            });
            kills += acc[0];
            buildActions += acc[1];
            rounds += s.round;
            ends.merge(String.valueOf(s.winCondition), 1, Integer::sum);
            for (PlayerState p : s.players) {
                for (BuildingToken b : p.buildings) {
                    if (b.type == BuildingType.AIRBASE && b.hexId != null) {
                        airbases++;
                    }
                }
                aircraft += p.unitsOfKind(UnitType.AIRCRAFT);
                vp += Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0);
            }
        }
        double perGame = 1.0 / games;
        double perPlayer = 1.0 / (games * (double) players);
        return new Result(airbases * perPlayer, aircraft * perPlayer, kills * perGame,
            vp * perPlayer, rounds * perGame, buildActions * perGame, ends);
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 200;

        Result three = run(players, games, 3);
        Result two = run(players, games, 2);

        StringBuilder md = new StringBuilder();
        md.append("# Авиабаза: три ячейки энергии против двух\n\n");
        md.append("Партий на вариант: ").append(games).append(", игроков: ").append(players)
          .append(", боты: ").append(Bots.describe())
          .append(". Одни и те же сиды в обоих вариантах.\n\n");
        md.append("| показатель | 3 ячейки | 2 ячейки |\n|---|---:|---:|\n");
        md.append(String.format(Locale.ROOT, "| авиабаз на поле (на игрока) | %.2f | %.2f |%n",
            three.airbases(), two.airbases()));
        md.append(String.format(Locale.ROOT, "| жетонов авиации у игрока | %.2f | %.2f |%n",
            three.aircraft(), two.aircraft()));
        md.append(String.format(Locale.ROOT, "| уничтожено жетонов за партию | %.2f | %.2f |%n",
            three.kills(), two.kills()));
        md.append(String.format(Locale.ROOT, "| ПО на игрока | %.2f | %.2f |%n",
            three.vp(), two.vp()));
        md.append(String.format(Locale.ROOT, "| раундов за партию | %.2f | %.2f |%n",
            three.rounds(), two.rounds()));
        md.append(String.format(Locale.ROOT, "| действий Стройка за партию | %.2f | %.2f |%n",
            three.buildActions(), two.buildActions()));
        md.append("\nЧем кончались (3 ячейки): ").append(three.ends()).append('\n');
        md.append("\nЧем кончались (2 ячейки): ").append(two.ends()).append('\n');

        Path file = Path.of("reports/balance/авиабаза-2-против-3.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
        out.println(md);
        out.println("записано: " + file.toAbsolutePath());
    }
}
