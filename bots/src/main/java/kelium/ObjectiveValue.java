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

import kelium.agents.Genome;
import kelium.agents.StrategicAgent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.dataio.Locations;
import kelium.core.Agent;

/**
 * ObjectiveValue — оценка ПОЛЕЗНОСТИ каждого задания. Считает по каждой карте:
 * <ul>
 *   <li>ЧАСТОТУ выполнения низа (эмпирика по большому батчу);</li>
 *   <li>ЦЕННОСТЬ награды в условных ПО-единицах (база + усиление);</li>
 *   <li>СЛОЖНОСТЬ двумя способами — аналитическую (тип + пороги предиката) и
 *       эмпирическую (1/частота);</li>
 *   <li>K = награда / сложность — БАЛАНС: K≈1 сбалансирована, K≫1 недооценена
 *       (легко+щедро = имба), K≪1 переоценена (трудно+скупо).</li>
 * </ul>
 * Расхождение аналитической и эмпирической сложности — само по себе сигнал
 * (боты не умеют то, что «на бумаге» легко). Отчёт: reports/balance/objective_value.md.
 *
 * <p>CLI: {@code kelium.ObjectiveValue [players] [games]}.
 */
public final class ObjectiveValue {

    private ObjectiveValue() {
    }

    // Ценность единицы награды в ПО-единицах (якоря из economy: 5 монет=1ПО,
    // 3 трофея=1ПО, 2 келемия=1ПО; модуль ~ ПО+сила, карта/арсенал/контейнер — задел).
    private static double rewardUnit(String key) {
        return switch (key) {
            case "kelium" -> 0.5;
            case "trophy" -> 0.4;
            case "coin" -> 0.2;
            case "ammo" -> 0.3;
            case "container" -> 0.25;
            case "objective_card" -> 0.4;
            case "arsenal" -> 0.8;
            case "module" -> 1.5;
            case "storage_token" -> 1.0;
            default -> 0.3;
        };
    }

    @SuppressWarnings("unchecked")
    private static double rewardValue(Map<String, Object> reward) {
        if (reward == null) {
            return 0;
        }
        double v = 0;
        for (var e : reward.entrySet()) {
            String k = e.getKey();
            Object val = e.getValue();
            double n = val instanceof Number num ? num.doubleValue() : 1.0;   // module: "attack" → 1
            v += rewardUnit(k) * n;
        }
        return v;
    }

    /** Аналитическая сложность по типу задания и порогам предиката (грубая модель). */
    @SuppressWarnings("unchecked")
    private static double analyticDifficulty(Map<String, Object> card, boolean enhanced) {
        Map<String, Object> req = (Map<String, Object>) card.get(enhanced ? "enhanced" : "requirement");
        if (req == null) {
            return enhanced ? Double.NaN : 1.0;
        }
        String pid = String.valueOf(req.get("predicate"));
        Map<String, Object> pr = req.get("params") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String type = String.valueOf(card.getOrDefault("type", "state"));

        // базовая сложность по типу
        double base = switch (type) {
            case "incident" -> 1.4;   // надо сделать за один ход
            case "sacrifice" -> 1.1;  // надо иметь чем заплатить
            default -> 1.0;           // state — просто состояние
        };
        // масштаб по порогу count/distance/amount/step
        double scale = 1.0;
        for (String key : new String[]{"count", "distance", "amount", "step", "tracks"}) {
            Object o = pr.get(key);
            if (o instanceof Number num) {
                scale *= 1.0 + 0.45 * (num.doubleValue() - 1);   // каждый +1 порога ~ +45%
            }
        }
        // боевые/геометрические предикаты объективно труднее для текущих ботов
        double kind = 1.0;
        if (pid.contains("destroyed") || pid.contains("damaged") || pid.contains("enemy")
                || pid.contains("aircraft_on_enemy") || pid.contains("retaliation")) {
            kind = 1.8;   // требует войны
        } else if (pid.contains("ring") || pid.contains("chain") || pid.contains("share_wall")
                || pid.contains("far_from")) {
            kind = 1.4;   // требует геометрии/позиции
        }
        double d = base * scale * kind;
        // флаги-модификаторы усиления
        if (Boolean.TRUE.equals(pr.get("distinct_kinds")) || pr.containsKey("distinct_kinds")) {
            d *= 1.3;
        }
        if (Boolean.TRUE.equals(pr.get("on_enemy_hex"))) {
            d *= 1.4;
        }
        return d;
    }

    public static void main(String[] a) {
        int players = a.length > 0 ? Integer.parseInt(a[0]) : 4;
        int games = a.length > 1 ? Integer.parseInt(a[1]) : 300;

        GameConfig cfg0 = GameConfig.buildCached(players, 0L);
        // карты в порядке файла
        List<Map<String, Object>> cards = cfg0.content.get("objectives").entries;

        Genome genome;
        try {
            genome = Genome.loadJson(Locations.botMemory()
                .resolve("strategic_" + players + "p.json"));
        } catch (Exception e) {
            genome = Genome.defaults();
        }

        // эмпирика: частота выполнения (низ) и усиления по картам
        Map<String, Integer> doneCount = new LinkedHashMap<>();
        Map<String, Integer> enhCount = new LinkedHashMap<>();
        for (Map<String, Object> c : cards) {
            doneCount.put((String) c.get("id"), 0);
            enhCount.put((String) c.get("id"), 0);
        }
        // 6 характеров (вкл. Исследователя и Хаос) — ротация по сиду; Исследователь
        // особенно важен здесь: он ЦЕЛЕНАПРАВЛЕННО пробует редкие механики,
        // без него редкие низы выглядят мёртвыми из-за ботов, а не карт.
        String[] profs = {"hawk", "opportunist", "dove", "balanced", "explorer", "chaos"};
        for (long seed = 0; seed < games; seed++) {
            GameState s = Setup.buildGame(GameConfig.buildCached(players, seed));
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < players; seat++) {
                String prof = profs[(seat + (int) Math.floorMod(seed, profs.length)) % profs.length];
                java.util.Random rr = new java.util.Random(seed * 10 + seat);
                agents.add(kelium.agents.Bots.create(prof, seat, rr, players));
            }
            GameEngine.playGame(s, agents, ev -> {
                if ("objective".equals(String.valueOf(ev.get("type")))) {
                    String id = String.valueOf(ev.get("card"));
                    doneCount.merge(id, 1, Integer::sum);
                    if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                        enhCount.merge(id, 1, Integer::sum);
                    }
                }
            });
        }

        // отчёт
        List<String> L = new ArrayList<>();
        L.add("# Полезность заданий (K = награда / сложность)");
        L.add("");
        L.add("Игроков: " + players + ", партий: " + games + ", версия: " + cfg0.ruleset.id);
        L.add("");
        L.add("K≈1 сбалансирована · K≫1 недооценена (имба: легко+щедро) · "
            + "K≪1 переоценена (трудно+скупо). D_ан — аналитич. сложность, "
            + "D_эмп — 1/частота(норм). Расхождение D_ан↔D_эмп = боты не умеют «лёгкое».");
        L.add("");
        L.add("| id | тип | вып. | усил% | награда | D_ан | D_эмп | K | вердикт |");
        L.add("|----|-----|------|-------|---------|------|-------|---|---------|");

        // нормировка эмпирической сложности: медиана частоты → 1.0
        List<Integer> freqs = new ArrayList<>();
        for (Map<String, Object> c : cards) {
            if (!"starting".equals(c.get("kind"))) {
                freqs.add(doneCount.get((String) c.get("id")));
            }
        }
        freqs.sort(null);
        double medianFreq = freqs.isEmpty() ? 1 : Math.max(1, freqs.get(freqs.size() / 2));

        for (Map<String, Object> c : cards) {
            if ("starting".equals(c.get("kind"))) {
                continue;
            }
            String id = (String) c.get("id");
            String type = String.valueOf(c.getOrDefault("type", "state"));
            int done = doneCount.get(id);
            int enh = enhCount.get(id);
            double enhPct = done > 0 ? 100.0 * enh / done : 0;

            @SuppressWarnings("unchecked")
            double rw = rewardValue((Map<String, Object>) c.get("base_reward"))
                + rewardValue((Map<String, Object>) c.get("special_reward")) * 0.5; // усиление реже
            double dAn = analyticDifficulty(c, false);
            double dEmp = medianFreq / Math.max(1, done);   // редкая карта → высокая эмпир. сложность
            double dCombined = Math.sqrt(Math.max(0.1, dAn) * Math.max(0.1, dEmp));
            double k = rw / dCombined;

            String verdict;
            if (done == 0) {
                verdict = "МЁРТВАЯ (не выполняется)";
            } else if (k > 1.8) {
                verdict = "недооценена (усилить требование/срезать награду)";
            } else if (k < 0.5) {
                verdict = "переоценена (поднять награду/снизить порог)";
            } else {
                verdict = "ок";
            }
            L.add(String.format(Locale.ROOT, "| %s | %s | %d | %.0f%% | %.2f | %.2f | %.2f | %.2f | %s |",
                id, type, done, enhPct, rw, dAn, dEmp, k, verdict));
        }

        String text = String.join("\n", L);
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println(text);
        try {
            Path p = Paths.get("reports", "balance", "objective_value.md");
            Files.createDirectories(p.getParent());
            Files.writeString(p, text, StandardCharsets.UTF_8);
            out.println("\n(сохранено: " + p.toAbsolutePath() + ")");
        } catch (Exception e) {
            out.println("не удалось сохранить: " + e.getMessage());
        }
    }
}
