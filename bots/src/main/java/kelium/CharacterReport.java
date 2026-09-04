package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.agents.Genome;
import kelium.agents.StrategicAgent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.dataio.Locations;
import kelium.core.Agent;
import kelium.engine.LayoutLibrary;

/**
 * CharacterReport — «кто чем занимается»: отчёт о поведении каждого характера
 * бота.
 *
 * <p>Каждый характер по очереди сажается на МЕСТО 1, остальные места занимают
 * ровные стратеги. Так видно именно его повадки, а не среднее по столу.
 * Считаются действия, постройки по типам, произведённые рода войск, добыча,
 * бои, задания и очки.
 *
 * <p>Запуск: {@code java -cp ... kelium.CharacterReport [players] [gamesPerCharacter]}
 * Отчёт сохраняется в {@code reports/balance/characters.md}.
 */
public final class CharacterReport {

    private CharacterReport() {
    }

    public static final List<String> CHARACTERS = List.of(
        "hawk", "opportunist", "dove", "balanced", "explorer", "chaos");

    /** Накопленные повадки одного характера. */
    public static final class Tally {
        public int games;
        public int wins;
        public double vp;
        public double vpRivalsBest;
        public final Map<String, Integer> actions = new LinkedHashMap<>();
        public final Map<String, Integer> buildings = new LinkedHashMap<>();
        public final Map<String, Integer> units = new LinkedHashMap<>();
        public int keliumMined;
        public int battles;
        public int kills;
        public int lost;
        public int objectives;
        public int tops;
        public int containers;
        public int techSteps;
        public int tilesDepleted;
        /** ОТКУДА берутся победные очки: источник -> сумма по всем партиям. */
        public final Map<String, Integer> vpFrom = new LinkedHashMap<>();
        /** Сколько РАЗНЫХ механик тронул за партию (цель «исследователя»). */
        public int breadthSum;
        final java.util.Set<String> touchedThisGame = new java.util.HashSet<>();

        double per(int v) {
            return games == 0 ? 0 : (double) v / games;
        }
    }

    /** Сыграть партии с данным характером на месте 1 и собрать статистику. */
    public static Tally measure(String character, int players, int games, long baseSeed) {
        Tally t = new Tally();
        Genome genome = loadGenome(players);
        for (int g = 0; g < games; g++) {
            long seed = baseSeed + g;
            GameConfig cfg = LayoutLibrary.configFor(players, seed);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < players; seat++) {
                Random r = new Random(seed * 131 + seat + 1);
                agents.add(seat == 0 ? make(character, seat, r, profileGenome(players, character))
                    : new StrategicAgent(seat, r, profileGenome(players, "balanced")));
            }
            final Tally tt = t;
            Map<String, Object> res = GameEngine.playGame(s, agents, ev -> record(tt, ev));
            t.games++;
            if (res.get("winner") instanceof Number w && w.intValue() == 0) {
                t.wins++;
            }
            Map<String, Integer> bd = Scoring.scorePlayer(s, 0);
            t.vp += bd.getOrDefault("total", 0);
            for (var ve : bd.entrySet()) {
                if (!"total".equals(ve.getKey())) {
                    t.vpFrom.merge(ve.getKey(), ve.getValue(), Integer::sum);
                }
            }
            int bestRival = 0;
            for (int seat = 1; seat < players; seat++) {
                bestRival = Math.max(bestRival,
                    Scoring.scorePlayer(s, seat).getOrDefault("total", 0));
            }
            t.vpRivalsBest += bestRival;

            PlayerState p = s.player(0);
            for (BuildingToken b : p.buildingsOnField()) {
                t.buildings.merge(ru(b.type), 1, Integer::sum);
                t.touchedThisGame.add("здание:" + ru(b.type));
            }
            for (UnitToken u : p.unitsOnField()) {
                t.units.merge(ru(u.type), 1, Integer::sum);
                t.touchedThisGame.add("войско:" + ru(u.type));
            }
            for (var te : p.techSteps.entrySet()) {
                if (te.getValue() > 0) {
                    t.touchedThisGame.add("трек:" + te.getKey());
                }
            }
            t.breadthSum += t.touchedThisGame.size();
            t.touchedThisGame.clear();
            t.techSteps += p.techSteps.values().stream().mapToInt(Integer::intValue).sum();
            for (var h : s.field.hexes.values()) {
                if (h.spawnTile != null && (h.spawnTile.flipped
                        || h.spawnTile.kelium < h.spawnTile.faceKelium)) {
                    t.tilesDepleted++;
                }
            }
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    private static void record(Tally t, Map<String, Object> ev) {
        Object type = ev.get("type");
        int seat = ev.get("seat") instanceof Number n ? n.intValue() : -1;
        if ("action".equals(type) && seat == 0 && Boolean.TRUE.equals(ev.get("ok"))) {
            t.actions.merge(String.valueOf(ev.get("action")), 1, Integer::sum);
            t.touchedThisGame.add("действие:" + ev.get("action"));
            if (ev.get("telemetry") instanceof Map<?, ?> tel
                    && tel.get("kelium") instanceof Number k) {
                t.keliumMined += k.intValue();
            }
        } else if ("combat_hit".equals(type)) {
            if (seat == 0) {
                t.battles++;
                if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                    t.kills++;
                }
            } else if (ev.get("victim_owner") instanceof Number vo && vo.intValue() == 0
                    && Boolean.TRUE.equals(ev.get("destroyed"))) {
                t.lost++;
            }
        } else if ("objective".equals(type) && seat == 0) {
            t.objectives++;
            t.touchedThisGame.add("задание");
        } else if ("objective_burn".equals(type) && seat == 0) {
            t.tops++;
            t.touchedThisGame.add("верх задания");
        } else if ("container".equals(type) && seat == 0) {
            t.containers++;   // движок шлёт тип «container», а не «container_open»
            t.touchedThisGame.add("контейнер");
        } else if (seat == 0 && ("arsenal_burn".equals(type) || "arsenal_install".equals(type)
                || "science_step".equals(type) || "module".equals(type)
                || "super_assemble".equals(type) || "maneuver".equals(type))) {
            t.touchedThisGame.add(String.valueOf(type));
        }
    }

    private static Agent make(String kind, int seat, Random r, Genome genome) {
        // Все характеры — геномы своей линии (решение дизайнера 12.08.2026).
        // Геном сюда приходит УЖЕ линии этого характера, второй раз перекос
        // накладывать нельзя — иначе замеряется не то, что обучено.
        return new StrategicAgent(seat, r, genome, kind);
    }

    private static final Map<String, Genome> PROFILE_CACHE = new LinkedHashMap<>();

    /**
     * Геном линии характера: если для неё обучен ОТДЕЛЬНЫЙ файл
     * ({@code strategic_Np_<профиль>.json}) — берём его, иначе базовый с
     * множителями характера. Раньше замер всегда брал базовый, поэтому
     * измерялось не то, что обучено.
     */
    private static Genome profileGenome(int players, String profile) {
        return PROFILE_CACHE.computeIfAbsent(players + ":" + profile, k -> {
            Path p = Locations.botMemory()
                .resolve("strategic_" + players + "p_" + profile + ".json");
            try {
                return Genome.loadJson(p);
            } catch (Exception e) {
                return loadGenome(players).withProfile(profile);
            }
        });
    }

    private static Genome loadGenome(int players) {
        try {
            return Genome.loadJson(Locations.botMemory()
                .resolve("strategic_" + players + "p.json"));
        } catch (Exception e) {
            return Genome.defaults();
        }
    }

    /** Человеческое имя источника победных очков. */
    private static String vpRu(String key) {
        return switch (key) {
            case "kelium" -> "келемий";
            case "coins" -> "монеты";
            case "trophy" -> "трофеи";
            case "buildings_on_field" -> "здания";
            case "units_on_field" -> "войска";
            case "tech" -> "треки";
            case "gold_modules" -> "золотые модули";
            case "spawn_tiles" -> "тайлы зарожд.";
            case "cu_tokens" -> "жетоны ЦУ";
            case "war_track" -> "военный трек";
            case "super_arsenal" -> "супер арсенал";
            case "level4_stars" -> "звёзды L4";
            case "super_first_part" -> "1-я часть супер-зад.";
            case "kills" -> "уничтожения";
            default -> key;
        };
    }

    private static String ru(BuildingType t) {
        return switch (t) {
            case COMMAND_CENTER -> "ЦУ";
            case BARRACKS -> "казарма";
            case FACTORY -> "завод";
            case AIRBASE -> "авиабаза";
            case MINER -> "добытчик";
            case POWER_PLANT -> "станция";
        };
    }

    private static String ru(UnitType t) {
        return switch (t) {
            case INFANTRY -> "пехота";
            case VEHICLE -> "техника";
            case AIRCRAFT -> "авиация";
            case TOWER -> "вышка";
        };
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 30;

        Map<String, Tally> all = new LinkedHashMap<>();
        for (String c : CHARACTERS) {
            out.println("считаю характер: " + c + "…");
            all.put(c, measure(c, players, games, 1000));
        }

        StringBuilder md = new StringBuilder();
        md.append("# Характеры ботов — что они делают на самом деле\n\n");
        md.append("Каждый характер сидит на МЕСТЕ 1, остальные — ровные стратеги. ")
          .append("Игроков: ").append(players).append(", партий на характер: ")
          .append(games).append(". Всё в среднем ЗА ПАРТИЮ.\n\n");

        md.append("| характер | побед | ПО | ПО лучшего соперника | широта | бои | убил | потерял "
            + "| келемий | заданий | верхов | контейнеров | шагов науки |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (var e : all.entrySet()) {
            Tally t = e.getValue();
            md.append(String.format(Locale.ROOT,
                "| **%s** | %.0f%% | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f |%n",
                e.getKey(), 100.0 * t.wins / Math.max(1, t.games), t.vp / t.games,
                t.vpRivalsBest / t.games, t.per(t.breadthSum), t.per(t.battles), t.per(t.kills),
                t.per(t.lost), t.per(t.keliumMined), t.per(t.objectives), t.per(t.tops),
                t.per(t.containers), t.per(t.techSteps)));
        }
        md.append("\n**Широта** — сколько РАЗНЫХ механик характер тронул за партию "
            + "(действия, типы зданий и войск, треки, задания, контейнеры, арсенал). "
            + "Это целевой показатель «Исследователя»: он играет не на победу, а на охват.\n");

        // ---- ОТКУДА ОЧКИ ----
        md.append("\n## Откуда победные очки\n\n");
        md.append("Средние очки ЗА ПАРТИЮ по источникам. Сумма строки — это "
            + "столбец «ПО» из первой таблицы.\n\n");
        List<String> vpKeys = new ArrayList<>();
        for (Tally tt : all.values()) {
            for (String k : tt.vpFrom.keySet()) {
                if (!vpKeys.contains(k)) {
                    vpKeys.add(k);
                }
            }
        }
        md.append("| характер");
        for (String k : vpKeys) {
            md.append(" | ").append(vpRu(k));
        }
        md.append(" | ВСЕГО |\n|---");
        for (int i = 0; i <= vpKeys.size(); i++) {
            md.append("|---");
        }
        md.append("|\n");
        for (var e : all.entrySet()) {
            Tally t = e.getValue();
            md.append("| **").append(e.getKey()).append("**");
            for (String k : vpKeys) {
                md.append(String.format(Locale.ROOT, " | %.2f",
                    t.per(t.vpFrom.getOrDefault(k, 0))));
            }
            md.append(String.format(Locale.ROOT, " | **%.1f** |%n", t.vp / t.games));
        }

        // «Военный трек» — ЭКСПЕРИМЕНТАЛЬНЫЙ рычаг economy.leftover_destroyed_vp_per.
        // Ключа нет ни в одном ruleset → дефолт 0 → источник выключен и столбец
        // структурно нулевой. Без этой пометки нули читаются как поломка
        // (вопрос дизайнера 12.08.2026).
        int warPer = ((Number) kelium.dataio.GameConfig
            .buildCached(kelium.dataio.GameConfig.DEFAULT_RULESET, players, 1L, null, null)
            .ruleset.get("economy.leftover_destroyed_vp_per", 0)).intValue();
        md.append(warPer > 0
            ? "\n«Военный трек» ВКЛЮЧЁН: 1 ПО за каждые " + warPer
              + " трофеев, оставшихся под трофеями к концу раунда.\n"
            : "\n«Военный трек» ВЫКЛЮЧЕН (`economy.leftover_destroyed_vp_per` = 0), "
              + "поэтому столбец нулевой у всех — это не поломка, а неактивный "
              + "экспериментальный рычаг.\n");

        md.append("\n## Что строят (зданий на поле к концу партии)\n\n");
        md.append("| характер | ЦУ | казарма | завод | авиабаза | добытчик | станция |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (var e : all.entrySet()) {
            Tally t = e.getValue();
            md.append(String.format(Locale.ROOT, "| **%s** | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f |%n",
                e.getKey(), t.per(t.buildings.getOrDefault("ЦУ", 0)),
                t.per(t.buildings.getOrDefault("казарма", 0)),
                t.per(t.buildings.getOrDefault("завод", 0)),
                t.per(t.buildings.getOrDefault("авиабаза", 0)),
                t.per(t.buildings.getOrDefault("добытчик", 0)),
                t.per(t.buildings.getOrDefault("станция", 0))));
        }

        md.append("\n## Какие войска стоят на поле к концу партии\n\n");
        md.append("| характер | пехота | техника | авиация | вышка |\n|---|---|---|---|---|\n");
        for (var e : all.entrySet()) {
            Tally t = e.getValue();
            md.append(String.format(Locale.ROOT, "| **%s** | %.1f | %.1f | %.1f | %.1f |%n",
                e.getKey(), t.per(t.units.getOrDefault("пехота", 0)),
                t.per(t.units.getOrDefault("техника", 0)),
                t.per(t.units.getOrDefault("авиация", 0)),
                t.per(t.units.getOrDefault("вышка", 0))));
        }

        md.append("\n## Какие действия играют\n\n");
        List<String> acts = List.of("assembly", "mining", "build", "energy_swap",
            "movement", "combat", "market", "science");
        md.append("| характер |");
        for (String a : acts) {
            md.append(' ').append(a).append(" |");
        }
        md.append("\n|---|");
        acts.forEach(a -> md.append("---|"));
        md.append('\n');
        for (var e : all.entrySet()) {
            Tally t = e.getValue();
            md.append("| **").append(e.getKey()).append("** |");
            for (String a : acts) {
                md.append(String.format(Locale.ROOT, " %.1f |", t.per(t.actions.getOrDefault(a, 0))));
            }
            md.append('\n');
        }

        try {
            Path p = Paths.get("reports", "balance", "characters.md");
            Files.createDirectories(p.getParent());
            Files.writeString(p, md.toString(), StandardCharsets.UTF_8);
            out.println("отчёт: " + p.toAbsolutePath());
        } catch (java.io.IOException e) {
            out.println("не записал отчёт: " + e.getMessage());
        }
        out.println(md);
    }
}
