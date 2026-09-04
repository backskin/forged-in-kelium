package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Genome;
import kelium.agents.StrategicAgent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scenario;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.dataio.Locations;
import kelium.core.Agent;

/**
 * LayoutProbe — зонд БАЛАНСА РАСКЛАДОК ПОЛЯ. Гоняет батч партий отдельно по
 * КАЖДОМУ варианту раскладки (сид подбирается так, что вариант фиксирован:
 * {@code seed = (5000+g)*K + v}, где K — число вариантов) и сравнивает поля:
 * <ul>
 *   <li>победы по МЕСТУ на данном поле (главный сигнал: перекос = у кого-то
 *       позиция на карте лучше);</li>
 *   <li>ср. ПО победителя и маржа (напряжённость);</li>
 *   <li>боёв/уничтожений за партию (насколько поле воинственное);</li>
 *   <li>выполненных заданий за партию, ср. раундов, пути победы.</li>
 * </ul>
 * Профили стратегов ротируются по сидам, чтобы характер не прилипал к месту.
 *
 * <p>CLI: {@code kelium.LayoutProbe [gamesPerVariant=60]} →
 * reports/balance/layouts.md (русский Markdown, зонд не выносит вердиктов).
 */
public final class LayoutProbe {

    private LayoutProbe() {
    }

    private static final class VariantStats {
        String id;
        int games;
        int roundsSum;
        final Map<Integer, Integer> winsBySeat = new TreeMap<>();
        final Map<String, Integer> endConditions = new TreeMap<>();
        final List<Integer> winnerVps = new ArrayList<>();
        final List<Integer> margins = new ArrayList<>();
        long combatHits;
        long destroyed;
        long objectivesDone;
        long objectivesEnhanced;
        long neutralRazes;
        long neutralRazeRoundSum;   // сумма раундов сноса (для среднего)
        // источник ПО -> [суммарные ПО; игроко-партий с ненулём]
        final Map<String, long[]> vpSources = new TreeMap<>();
        long playerGames;           // игроко-партий всего (games * players)
        // полные комплекты модулей (все 4 у одного игрока к концу партии)
        long fullRedSets;
        long fullBlueSets;
        int maxRed;
        int maxBlue;
        // золочение модулей (улучшение за 3 трофея и др. источники)
        long goldModulesTotal;
        long playersWithGold;
        // супер-арсенал: взятые с вершин карты
        long superArsenalTaken;
        // характер -> [игроко-партий, побед, суммарные ПО]
        final Map<String, long[]> profileStats = new TreeMap<>();
        // гистограмма длины партий: раундов -> число партий
        final Map<Integer, Integer> roundsHist = new TreeMap<>();
        // индикаторы АРСЕНАЛА: сжигания, установки, стоит в конце, игроки с установкой,
        // удары со СКИДКОЙ от пассивок/модулей (арсенал влияет на бой)
        long arsenalBurns;
        long arsenalInstalls;
        long installedAtEnd;
        long playersWithInstall;
        long discountedHits;
    }

    public static void main(String[] args) throws Exception {
        int gamesPer = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        boolean noSuper = "nosuper".equals(mode);
        int warTrackPer = mode.startsWith("wartrack")
            ? Integer.parseInt(mode.substring("wartrack".length())) : 0;
        List<String> out = new ArrayList<>();
        out.add("# Зонд раскладок — сравнение полей между собой"
            + (noSuper ? " (СУПЕР-ЗАДАНИЯ ВЫКЛЮЧЕНЫ)" : "")
            + (warTrackPer > 0
                ? " (ВОЕННЫЙ ТРЕК: 1 ПО за " + warTrackPer + " несданных трофея)"
                : ""));
        out.add("");
        out.add(String.format(Locale.ROOT,
            "Партий на раскладку: **%d**, боты: стратеги (обученный геном, "
            + "характеры ротируются по сидам).%s%s", gamesPer,
            noSuper ? " Супер-задания: **выключены**." : "",
            warTrackPer > 0
                ? " Военный трек: **1 ПО за " + warTrackPer + " оставшихся трофея** в Возврат."
                : ""));
        out.add("");

        for (int players : new int[]{2, 3, 4}) {
            GameConfig probe = GameConfig.build(players, 0L);
            String version = probe.ruleset.getStr("content_versions.boards", "1.0.0");
            List<Map<String, Object>> variants =
                Scenario.loadAllVariants(players, version, probe.dataRoot);
            int k = variants.size();
            Genome genome = tryGenome(players);
            out.add("## Игроков: " + players + " (раскладок: " + k + ")");
            out.add("");

            VariantStats agg = new VariantStats();
            agg.id = "ИТОГО (" + players + " игрока, все раскладки)";
            for (int v = 0; v < k; v++) {
                VariantStats st = new VariantStats();
                st.id = String.valueOf(variants.get(v).get("id"));
                for (int g = 0; g < gamesPer; g++) {
                    long seed = (5000L + g) * k + v;   // floorMod(seed,k)==v → вариант фиксирован
                    GameConfig cfg = GameConfig.build(players, seed);
                    if (noSuper) {
                        cfg.ruleset.override("super_objectives.enabled", Boolean.FALSE);
                    }
                    if (warTrackPer > 0) {
                        cfg.ruleset.override("economy.leftover_destroyed_vp_per", warTrackPer);
                    }
                    GameState s = Setup.buildGame(cfg);
                    String[] seatProfiles = seatProfiles(players, seed);
                    List<Agent> agents = agents(players, seed, genome, seatProfiles);
                    final int[] roundMax = {0};
                    GameEngine.playGame(s, agents, ev -> observe(ev, st, roundMax));
                    st.games++;
                    st.roundsSum += roundMax[0];
                    st.roundsHist.merge(roundMax[0], 1, Integer::sum);
                    recordEnd(s, st, seatProfiles);
                }
                report(out, players, st);
                merge(agg, st);
            }
            report(out, players, agg);
        }

        Path outPath = Paths.get("reports", "balance",
            noSuper ? "layouts-nosuper.md"
                : warTrackPer > 0 ? "layouts-wartrack" + warTrackPer + ".md"
                : "layouts.md");
        Files.createDirectories(outPath.getParent());
        Files.writeString(outPath, String.join("\n", out), StandardCharsets.UTF_8);
        System.out.println("written: " + outPath.toAbsolutePath());
    }

    /** Слить статистику варианта в агрегат по числу игроков. */
    private static void merge(VariantStats agg, VariantStats st) {
        agg.games += st.games;
        agg.roundsSum += st.roundsSum;
        agg.combatHits += st.combatHits;
        agg.destroyed += st.destroyed;
        agg.objectivesDone += st.objectivesDone;
        agg.objectivesEnhanced += st.objectivesEnhanced;
        agg.neutralRazes += st.neutralRazes;
        agg.neutralRazeRoundSum += st.neutralRazeRoundSum;
        agg.playerGames += st.playerGames;
        agg.fullRedSets += st.fullRedSets;
        agg.fullBlueSets += st.fullBlueSets;
        agg.maxRed = Math.max(agg.maxRed, st.maxRed);
        agg.maxBlue = Math.max(agg.maxBlue, st.maxBlue);
        agg.goldModulesTotal += st.goldModulesTotal;
        agg.playersWithGold += st.playersWithGold;
        agg.superArsenalTaken += st.superArsenalTaken;
        for (var e : st.profileStats.entrySet()) {
            long[] acc = agg.profileStats.computeIfAbsent(e.getKey(), x -> new long[3]);
            for (int i = 0; i < 3; i++) {
                acc[i] += e.getValue()[i];
            }
        }
        for (var e : st.roundsHist.entrySet()) {
            agg.roundsHist.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        agg.arsenalBurns += st.arsenalBurns;
        agg.arsenalInstalls += st.arsenalInstalls;
        agg.installedAtEnd += st.installedAtEnd;
        agg.playersWithInstall += st.playersWithInstall;
        agg.discountedHits += st.discountedHits;
        for (var e : st.winsBySeat.entrySet()) {
            agg.winsBySeat.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        for (var e : st.endConditions.entrySet()) {
            agg.endConditions.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        agg.winnerVps.addAll(st.winnerVps);
        agg.margins.addAll(st.margins);
        for (var e : st.vpSources.entrySet()) {
            long[] acc = agg.vpSources.computeIfAbsent(e.getKey(), x -> new long[2]);
            acc[0] += e.getValue()[0];
            acc[1] += e.getValue()[1];
        }
    }

    private static void observe(Map<String, Object> ev, VariantStats st, int[] roundMax) {
        switch (String.valueOf(ev.get("type"))) {
            case "refresh" -> {
                if (ev.get("round") instanceof Number n) {
                    roundMax[0] = Math.max(roundMax[0], n.intValue());
                }
            }
            case "combat_hit" -> {
                st.combatHits++;
                if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                    st.destroyed++;
                }
                // скидка к печатной цене = сработала пассивка арсенала/супер-карты
                if (ev.get("ammo") instanceof Number paid
                        && ev.get("base_ammo") instanceof Number base
                        && paid.intValue() < base.intValue()) {
                    st.discountedHits++;
                }
            }
            case "arsenal" -> {
                if ("burn".equals(ev.get("mode"))) {
                    st.arsenalBurns++;
                } else if ("install".equals(ev.get("mode"))) {
                    st.arsenalInstalls++;
                }
            }
            case "objective" -> {
                st.objectivesDone++;
                if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                    st.objectivesEnhanced++;
                }
            }
            case "raze_neutral" -> {
                st.neutralRazes++;
                st.neutralRazeRoundSum += Math.max(1, roundMax[0]);
            }
            default -> { }
        }
    }

    /** Характер каждого места в этой партии (ротация по сиду). */
    private static String[] seatProfiles(int players, long seed) {
        // 6 характеров на 4 места: ротация по сиду прогоняет всех, включая
        // Исследователя (explorer) и Хаос (chaos) — добавлены 2026-08-11.
        String[] profs = {"hawk", "opportunist", "dove", "balanced", "explorer", "chaos"};
        String[] out = new String[players];
        for (int seat = 0; seat < players; seat++) {
            out[seat] = profs[(seat + (int) Math.floorMod(seed, profs.length)) % profs.length];
        }
        return out;
    }

    private static void recordEnd(GameState s, VariantStats st, String[] seatProfiles) {
        st.endConditions.merge(String.valueOf(s.winCondition), 1, Integer::sum);
        int best = Integer.MIN_VALUE;
        int second = Integer.MIN_VALUE;
        for (PlayerState p : s.players) {
            Map<String, Integer> bd = Scoring.scorePlayer(s, p.seat);
            int vp = bd.getOrDefault("total", 0);
            st.playerGames++;
            if (p.redModules >= 4) {
                st.fullRedSets++;
            }
            if (p.blueModules >= 4) {
                st.fullBlueSets++;
            }
            st.maxRed = Math.max(st.maxRed, p.redModules);
            st.maxBlue = Math.max(st.maxBlue, p.blueModules);
            st.goldModulesTotal += p.goldModules;
            if (p.goldModules > 0) {
                st.playersWithGold++;
            }
            st.superArsenalTaken += p.superArsenalCards.size();
            st.installedAtEnd += p.arsenalInstalled.size();
            if (!p.arsenalInstalled.isEmpty()) {
                st.playersWithInstall++;
            }
            long[] prof = st.profileStats.computeIfAbsent(
                seatProfiles[p.seat], x -> new long[3]);
            prof[0]++;
            prof[2] += vp;
            if (s.winner != null && s.winner == p.seat) {
                prof[1]++;
            }
            for (var e : bd.entrySet()) {
                if ("total".equals(e.getKey())) {
                    continue;
                }
                long[] acc = st.vpSources.computeIfAbsent(e.getKey(), k -> new long[2]);
                acc[0] += e.getValue();
                if (e.getValue() != 0) {
                    acc[1]++;
                }
            }
            if (vp > best) {
                second = best;
                best = vp;
            } else if (vp > second) {
                second = vp;
            }
        }
        if (second > Integer.MIN_VALUE) {
            st.margins.add(best - second);
        }
        if (s.winner != null && s.winner >= 0) {
            st.winsBySeat.merge(s.winner, 1, Integer::sum);
            st.winnerVps.add(Scoring.scorePlayer(s, s.winner).getOrDefault("total", 0));
        }
    }

    // Стратеги; характер НЕ прилипает к месту (ротация по сиду, см. seatProfiles).
    // Если для характера обучена ОТДЕЛЬНАЯ линия (strategic_Np_<профиль>.json) —
    // берём её геном; иначе базовый геном с множителями характера.
    private static final Map<String, Genome> PROFILE_GENOMES = new java.util.HashMap<>();

    private static List<Agent> agents(int players, long seed, Genome genome,
                                      String[] seatProfiles) {
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            Random r = new Random(seed * 131 + seat + 1);
            String prof = seatProfiles[seat];
            // Все шесть характеров — линии геномов (12.08.2026).
            if ("explorer".equals(prof) || "chaos".equals(prof)) {
                agents.add(kelium.agents.Bots.create(prof, seat, r, players));
                continue;
            }
            Genome g = PROFILE_GENOMES.computeIfAbsent(players + ":" + prof, key -> {
                try {
                    return Genome.loadJson(Locations.botMemoryFile(
                        "strategic_" + players + "p_" + prof + ".json"));
                } catch (Exception e) {
                    return genome.withProfile(prof);
                }
            });
            agents.add(new StrategicAgent(seat, r, g));
        }
        return agents;
    }

    private static Genome tryGenome(int players) {
        try {
            return Genome.loadJson(Locations.botMemory().resolve("strategic_" + players + "p.json"));
        } catch (Exception e) {
            return Genome.defaults();
        }
    }

    private static void report(List<String> out, int players, VariantStats st) {
        out.add("### " + st.id + " — " + st.games + " партий");
        StringBuilder seats = new StringBuilder();
        for (int seat = 0; seat < players; seat++) {
            int w = st.winsBySeat.getOrDefault(seat, 0);
            seats.append(String.format(Locale.ROOT, "место%d %.0f%%  ",
                seat, 100.0 * w / Math.max(1, st.games)));
        }
        out.add("- Победы по месту: " + seats.toString().trim());
        out.add(String.format(Locale.ROOT,
            "- ПО победителя ср.: **%.1f**; маржа ср.: **%.2f**", avg(st.winnerVps), avg(st.margins)));
        out.add(String.format(Locale.ROOT,
            "- Боёв/партию: **%.1f**; уничтожений/партию: **%.1f**; заданий/партию: **%.1f** "
            + "(из них УСИЛЕННЫХ %d = **%.2f**/партию); раундов ср.: **%.1f**",
            (double) st.combatHits / st.games, (double) st.destroyed / st.games,
            (double) st.objectivesDone / st.games,
            st.objectivesEnhanced, (double) st.objectivesEnhanced / st.games,
            (double) st.roundsSum / st.games));
        out.add(String.format(Locale.ROOT,
            "- Модули: ПОЛНЫЙ комплект атаки (4/4) у %d игроко-партий (%.2f%%), "
            + "сборки (4/4) у %d (%.2f%%); макс за партию: атака %d, сборка %d",
            st.fullRedSets, 100.0 * st.fullRedSets / Math.max(1, st.playerGames),
            st.fullBlueSets, 100.0 * st.fullBlueSets / Math.max(1, st.playerGames),
            st.maxRed, st.maxBlue));
        out.add(String.format(Locale.ROOT,
            "- Золочение модулей: **%d** улучшений всего (%.3f/партию); хотя бы одно "
            + "у %d игроко-партий (%.1f%%). Супер-арсенал взят с вершин: **%d** карт (%.2f/партию)",
            st.goldModulesTotal, (double) st.goldModulesTotal / Math.max(1, st.games),
            st.playersWithGold, 100.0 * st.playersWithGold / Math.max(1, st.playerGames),
            st.superArsenalTaken, (double) st.superArsenalTaken / Math.max(1, st.games)));
        out.add(String.format(Locale.ROOT,
            "- Снос нейтралов: **%.2f**/партию, средний раунд сноса: **%s**",
            (double) st.neutralRazes / st.games,
            st.neutralRazes > 0
                ? String.format(Locale.ROOT, "%.1f", (double) st.neutralRazeRoundSum / st.neutralRazes)
                : "—"));
        StringBuilder ends = new StringBuilder();
        for (var e : st.endConditions.entrySet()) {
            ends.append(String.format(Locale.ROOT, "%s %d (%.0f%%)  ",
                e.getKey(), e.getValue(), 100.0 * e.getValue() / st.games));
        }
        out.add("- Пути победы (кол-во партий и %): " + ends.toString().trim());
        // Рейтинг характеров: у каждого равное число игроко-партий (ротация по
        // сидам и местам), так что винрейт и ср. ПО сравнимы напрямую.
        List<Map.Entry<String, long[]>> profs = new ArrayList<>(st.profileStats.entrySet());
        profs.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
        StringBuilder ps = new StringBuilder();
        for (var e : profs) {
            long[] v = e.getValue();
            ps.append(String.format(Locale.ROOT, "%s: побед %d (%.0f%%), ср. ПО %.2f  ·  ",
                e.getKey(), v[1], 100.0 * v[1] / Math.max(1, v[0]),
                (double) v[2] / Math.max(1, v[0])));
        }
        out.add("- Характеры (рейтинг по победам): " + ps.toString().trim());
        // Длина партий: сколько закончилось за N раундов.
        StringBuilder rh = new StringBuilder();
        for (var e : st.roundsHist.entrySet()) {
            rh.append(String.format(Locale.ROOT, "%dр: %d (%.0f%%)  ",
                e.getKey(), e.getValue(), 100.0 * e.getValue() / Math.max(1, st.games)));
        }
        out.add("- Длина партий (раундов: партий): " + rh.toString().trim());
        // Индикаторы АРСЕНАЛА: включается ли и влияет ли на ходы.
        out.add(String.format(Locale.ROOT,
            "- АРСЕНАЛ: сжиганий **%d** (%.2f/партию), установок **%d** (%.2f/партию); "
            + "стоит в конце: %.2f карт/игрока, хотя бы одна у %.0f%% игроков; "
            + "ударов со скидкой от пассивок: **%d** (%.1f%% всех ударов)",
            st.arsenalBurns, (double) st.arsenalBurns / Math.max(1, st.games),
            st.arsenalInstalls, (double) st.arsenalInstalls / Math.max(1, st.games),
            (double) st.installedAtEnd / Math.max(1, st.playerGames),
            100.0 * st.playersWithInstall / Math.max(1, st.playerGames),
            st.discountedHits,
            100.0 * st.discountedHits / Math.max(1, st.combatHits)));
        // ПОЛНЫЙ отчёт по источникам ПО: доля от всех очков + как часто
        // источник вообще приносит хоть что-то (доля игроко-партий с ненулём)
        long allVp = 0;
        for (long[] acc : st.vpSources.values()) {
            allVp += acc[0];
        }
        out.add(String.format(Locale.ROOT,
            "- Источники ПО (ВСЕГО очков за все партии: %d; абсолют | доля | "
            + "игроко-партий с >0 | %%):", allVp));
        List<Map.Entry<String, long[]>> sorted = new ArrayList<>(st.vpSources.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        for (var e : sorted) {
            out.add(String.format(Locale.ROOT,
                "    %-20s %7d ПО | %5.1f%% | %6d | %.0f%%",
                e.getKey(), e.getValue()[0],
                allVp > 0 ? 100.0 * e.getValue()[0] / allVp : 0,
                e.getValue()[1],
                st.playerGames > 0 ? 100.0 * e.getValue()[1] / st.playerGames : 0));
        }
        out.add("");
    }

    private static double avg(List<Integer> xs) {
        if (xs.isEmpty()) {
            return 0;
        }
        long s = 0;
        for (int x : xs) {
            s += x;
        }
        return (double) s / xs.size();
    }
}
