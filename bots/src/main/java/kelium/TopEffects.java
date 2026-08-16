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
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * МЕТРИКИ УТИЛЬ-ЭФФЕКТОВ — что срабатывает в партии и что это даёт.
 *
 * <p>Заказ дизайнера 15.08.2026. Разовые эффекты («утиль») приходят из ЧЕТЫРЁХ
 * источников, и до сих пор их считали порознь или не считали вовсе:
 *
 * <ul>
 *   <li><b>задания</b> — карту сжигают ради верхнего эффекта вместо выполнения;</li>
 *   <li><b>арсенал</b> — то же самое верхом карты;</li>
 *   <li><b>контейнеры</b> — вариант А или Б открытой находки;</li>
 *   <li><b>рынок</b> — уникальное предложение активной карты.</li>
 * </ul>
 *
 * <p>Мерить их надо ВМЕСТЕ и по ЭФФЕКТУ, а не по карте: один и тот же эффект
 * лежит на разных картах, и вопрос «работает ли он вообще» — про эффект. Пример,
 * ради которого стенд и заведён: «забрать жетон первого игрока» встречается на
 * контейнере и на двух картах рынка, и до этой правки о его срабатывании не
 * сообщал никто.
 *
 * <p>Запуск: {@code kelium.TopEffects [партий] [игроков]}.
 */
public final class TopEffects {

    private TopEffects() {
    }

    /** Счётчик одного эффекта: сколько раз сработал и откуда пришёл. */
    private static final class Tally {
        int total;
        final Map<String, Integer> bySource = new TreeMap<>();
        final Map<String, Integer> results = new TreeMap<>();
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 250;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> lineup = List.of("warlord", "balanced", "axiom", "dove");

        Map<String, Tally> byEffect = new TreeMap<>();

        for (int g = 0; g < games; g++) {
            long seed = 9_700_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get((i + g) % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            GameEngine.playGame(s, agents, ev -> {
                String type = String.valueOf(ev.get("type"));
                String effect = null;
                String source = null;
                Object got = ev.get("got");
                switch (type) {
                    case "objective_burn" -> {
                        effect = String.valueOf(ev.get("effect"));
                        source = "задание";
                    }
                    case "arsenal" -> {
                        if ("burn".equals(String.valueOf(ev.get("mode")))) {
                            effect = String.valueOf(ev.get("effect"));
                            source = "арсенал";
                        }
                    }
                    case "container" -> {
                        effect = String.valueOf(ev.get("effect"));
                        source = "контейнер";
                    }
                    case "action" -> {
                        if (ev.get("telemetry") instanceof Map<?, ?> tel
                                && tel.get("offer_effect") instanceof String e) {
                            effect = e;
                            source = "рынок";
                            got = tel.get("offer_got");
                        }
                    }
                    default -> { }
                }
                if (effect == null || effect.isBlank() || "null".equals(effect)) {
                    return;
                }
                Tally t = byEffect.computeIfAbsent(effect, k -> new Tally());
                t.total++;
                t.bySource.merge(source, 1, Integer::sum);
                // ЧТО ЭФФЕКТ ВЕРНУЛ — по ключам возвращённой карты. Это и есть
                // ответ на вопрос «сработал ли он или отработал вхолостую»:
                // пустой результат означает, что применить было некуда.
                if (got instanceof Map<?, ?> m && !m.isEmpty()) {
                    for (var e : m.entrySet()) {
                        t.results.merge(String.valueOf(e.getKey()), 1, Integer::sum);
                    }
                } else {
                    t.results.merge("(вхолостую)", 1, Integer::sum);
                }
            });
        }

        StringBuilder md = new StringBuilder();
        md.append("# Утиль-эффекты: что срабатывает в партии\n\n");
        md.append("Партий: ").append(games).append(", игроков: ").append(players)
          .append(", боты ").append(Bots.describe()).append(".\n\n");
        md.append("Разовые эффекты приходят из четырёх источников — заданий, "
            + "арсенала, контейнеров и рынка. Считаются ПО ЭФФЕКТУ, а не по карте: "
            + "один эффект лежит на разных картах.\n\n");
        md.append("| эффект | раз за партию | откуда | что вернул |\n");
        md.append("|---|---:|---|---|\n");
        out.printf("%-24s %8s  %-34s %s%n", "эффект", "за партию", "откуда", "результат");
        byEffect.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().total, a.getValue().total))
            .forEach(e -> {
                Tally t = e.getValue();
                String src = t.bySource.entrySet().stream()
                    .map(x -> x.getKey() + " " + x.getValue())
                    .reduce((x, y) -> x + ", " + y).orElse("—");
                String res = t.results.entrySet().stream()
                    .map(x -> x.getKey() + " " + x.getValue())
                    .reduce((x, y) -> x + ", " + y).orElse("—");
                md.append(String.format(Locale.ROOT, "| `%s` | %.2f | %s | %s |%n",
                    e.getKey(), t.total / (double) games, src, res));
                out.printf(Locale.ROOT, "%-24s %8.2f  %-34s %s%n",
                    e.getKey(), t.total / (double) games, src, res);
            });

        Path p = Path.of("reports", "balance", "утиль-эффекты.md");
        Files.createDirectories(p.getParent());
        Files.writeString(p, md.toString(), StandardCharsets.UTF_8);
        out.println("\nотчёт: " + p.toAbsolutePath());
    }
}
