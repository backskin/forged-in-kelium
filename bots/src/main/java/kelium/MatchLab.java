package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ЛАБОРАТОРИЯ СОСТАВОВ — что происходит с партией, когда за столом сидят боты
 * ОДНОГО склада, и что меняется, если подсадить одного чужого.
 *
 * <p>Заказ дизайнера 14.08.2026. Смысл: средние по всем прогонам скрывают
 * главное — воюет ли бот потому, что он такой, или потому, что рядом сидит тот,
 * кто его вынуждает. Стол из одних воителей отвечает на первый вопрос, стол из
 * трёх мирных и одного воителя — на второй.
 *
 * <p>Составы задаются буквами: {@code В} воитель, {@code Р} вредитель,
 * {@code М} мирный. Какие именно характеры за ними стоят — аргументы запуска,
 * чтобы не переписывать стенд, когда линий станет больше.
 *
 * <p>Запуск: {@code kelium.MatchLab [своду] [игроков] [партий] [В=..,Р=..,М=..]},
 * например {@code kelium.MatchLab 1.7.1 4 120 warlord chaos dove}.
 */
public final class MatchLab {

    private MatchLab() {
    }

    /** Один состав: показное имя и характеры по местам. */
    private record Comp(String name, List<String> seats) {
    }

    /** Итог по составу. */
    private static final class Acc {
        double kills;
        double hires;
        double kinds;
        double onField;
        double vpTop;
        double vpSpread;
        double rounds;
        final Map<String, Integer> conditions = new TreeMap<>();
        final Map<String, Integer> winsByLine = new TreeMap<>();
        final Map<String, double[]> perLine = new TreeMap<>();   // [убийства, найм, ПО, партий]
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String ruleset = args.length > 0 ? args[0] : GameConfig.DEFAULT_RULESET;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int games = args.length > 2 ? Integer.parseInt(args[2]) : 120;
        String war = args.length > 3 ? args[3] : "warlord";
        String pest = args.length > 4 ? args[4] : "chaos";
        String calm = args.length > 5 ? args[5] : "dove";

        Map<Character, String> letter = new LinkedHashMap<>();
        letter.put('В', war);
        letter.put('Р', pest);
        letter.put('М', calm);

        // Составы по заказу: только свои, пополам, один чужой среди своих и
        // наоборот — и то же самое для пары воитель/мирный. Повторов «только
        // одни» по три раза дизайнер просил не делать, поэтому каждый чистый
        // состав встречается ровно один раз.
        List<String> patterns = new ArrayList<>(List.of(
            "ВВВВ", "РРРР", "ММММ",
            "ВВРР", "ВРРР", "РВВВ",
            "ВВММ", "ВМММ", "МВВВ",
            "РРММ", "РМММ", "МРРР"));
        List<Comp> comps = new ArrayList<>();
        for (String p : patterns) {
            List<String> seats = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                seats.add(letter.get(p.charAt(i % p.length())));
            }
            comps.add(new Comp(p.substring(0, Math.min(players, p.length())), seats));
        }

        out.println("свод правил: " + ruleset + ", игроков: " + players
            + ", партий на состав: " + games);
        out.println("В=" + war + "  Р=" + pest + "  М=" + calm);
        out.println(LayoutLibrary.describePool(players));

        StringBuilder md = new StringBuilder();
        md.append("# Лаборатория составов — свод правил ").append(ruleset).append("\n\n");
        md.append("Партий на состав: ").append(games).append(", игроков: ").append(players)
          .append(". В=").append(war).append(", Р=").append(pest)
          .append(", М=").append(calm).append(".\n\n");
        md.append("Зачем: понять, воюет ли бот сам по себе или только когда его "
            + "вынуждает сосед. Стол из одинаковых ботов отвечает на первое, "
            + "подсадка одного чужого — на второе.\n\n");
        md.append("| состав | раундов | уничтожений | найма | родов | войск на поле |"
            + " ПО победителя | разрыв ПО | чем кончилось |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---|\n");

        for (Comp c : comps) {
            Acc a = run(c, ruleset, players, games);
            String cond = a.conditions.entrySet().stream()
                .map(e -> e.getKey() + " " + (100 * e.getValue() / games) + "%")
                .reduce((x, y) -> x + ", " + y).orElse("—");
            md.append(String.format(Locale.ROOT,
                "| %s | %.1f | %.2f | %.2f | %.2f | %.2f | %.1f | %.1f | %s |%n",
                c.name(), a.rounds / games, a.kills / games, a.hires / games,
                a.kinds / games, a.onField / games, a.vpTop / games,
                a.vpSpread / games, cond));
            out.printf(Locale.ROOT, "%-6s раундов %.1f · уничтожений %.2f · найма %.2f "
                + "· родов %.2f · разрыв ПО %.1f · %s%n",
                c.name(), a.rounds / games, a.kills / games, a.hires / games,
                a.kinds / games, a.vpSpread / games, cond);
            if (a.perLine.size() > 1) {
                md.append("\n<sub>по линиям в этом составе: ");
                for (var e : a.perLine.entrySet()) {
                    double[] v = e.getValue();
                    md.append(String.format(Locale.ROOT,
                        "%s — уничтожений %.2f, найма %.2f, ПО %.1f, побед %d%%; ",
                        e.getKey(), v[0] / v[3], v[1] / v[3], v[2] / v[3],
                        100 * a.winsByLine.getOrDefault(e.getKey(), 0) / games));
                }
                md.append("</sub>\n\n");
            }
        }
        write(out, md, "reports/balance/лаборатория-составов-" + ruleset + ".md");
    }

    private static Acc run(Comp c, String ruleset, int players, int games) {
        Acc a = new Acc();
        for (int g = 0; g < games; g++) {
            long seed = 4_100_000L + g;
            GameConfig base = GameConfig.build(ruleset, players, seed, null, null);
            GameConfig cfg = LayoutLibrary.configFor(base, players, seed);
            GameState s = Setup.buildGame(cfg);

            // МЕСТА СДВИГАЮТСЯ каждую партию: место у стола само по себе даёт
            // перевес (первый ход, положение на поле), и без сдвига весь замер
            // мерил бы место, а не характер.
            List<String> seats = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                seats.add(c.seats().get((i + g) % players));
            }
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(seats.get(i), i, new Random(seed * 31 + i), players));
            }
            int[] kills = new int[players];
            int[] hires = new int[players];
            Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
                if (!(ev.get("seat") instanceof Number n)) {
                    return;
                }
                int st = n.intValue();
                if (st < 0 || st >= players) {
                    return;
                }
                String t = String.valueOf(ev.get("type"));
                if ("combat_hit".equals(t) && Boolean.TRUE.equals(ev.get("destroyed"))) {
                    kills[st]++;
                }
                // НАЙМ читается по ключу "units" — именно его пишет действие
                // Сборка (kelium.engine.Actions). Ключ "units_made" здесь не
                // работает, на этом уже один замер показал нули.
                if ("action".equals(t) && ev.get("telemetry") instanceof Map<?, ?> tel
                        && tel.get("units") instanceof Number um) {
                    hires[st] += um.intValue();
                }
            });
            int winner = res.get("winner") instanceof Number w ? w.intValue() : -1;
            a.rounds += res.get("rounds") instanceof Number r ? r.intValue() : 0;
            a.conditions.merge(String.valueOf(res.get("condition")), 1, Integer::sum);

            int top = Integer.MIN_VALUE;
            int low = Integer.MAX_VALUE;
            for (int i = 0; i < players; i++) {
                int vp = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
                top = Math.max(top, vp);
                low = Math.min(low, vp);
                Set<UnitType> kinds = EnumSet.noneOf(UnitType.class);
                int onField = 0;
                for (UnitToken u : s.player(i).unitsOnField()) {
                    kinds.add(u.type);
                    onField++;
                }
                a.kills += kills[i];
                a.hires += hires[i];
                a.kinds += kinds.size();
                a.onField += onField;
                double[] line = a.perLine.computeIfAbsent(seats.get(i), k -> new double[4]);
                line[0] += kills[i];
                line[1] += hires[i];
                line[2] += vp;
                line[3]++;
                if (winner == i) {
                    a.winsByLine.merge(seats.get(i), 1, Integer::sum);
                }
            }
            a.vpTop += top;
            a.vpSpread += top - low;
        }
        // Средние по игроку, а не по столу: иначе состав на 4 игрока выглядел бы
        // вчетверо воинственнее того же состава на 2.
        a.kills /= players;
        a.hires /= players;
        a.kinds /= players;
        a.onField /= players;
        return a;
    }

    private static void write(PrintStream out, StringBuilder md, String rel) throws Exception {
        // Место отчётов — как у всех остальных стендов: reports/balance рядом с
        // рабочей папкой. Сначала я писал на уровень выше, и файлы оказывались в
        // отдельной куче, которую никто не искал.
        Path p = Path.of(rel).normalize();
        Files.createDirectories(p.getParent());
        Files.writeString(p, md.toString(), StandardCharsets.UTF_8);
        out.println("отчёт: " + p.toAbsolutePath());
    }
}
