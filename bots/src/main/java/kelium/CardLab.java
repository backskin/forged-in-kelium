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
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.rules.Ruleset;

/**
 * ЛАБОРАТОРИЯ КАРТ — проверка КАЖДОЙ карты по отдельности, а не «в среднем».
 *
 * <p>Заказ дизайнера 12.08.2026. Средние по колоде ничего не говорят о конкретной
 * карте: слабая карта тонет в общей статистике. Поэтому карту ставят в условия, где
 * её нельзя не увидеть:
 *
 * <ul>
 *   <li><b>рынок</b> — колода рынка целиком составляется из ОДНОЙ исследуемой
 *       карты: все восемь раундов действует она. Видно, берут ли её предложения
 *       вообще и сколько раз;</li>
 *   <li><b>задания</b> — в колоду заданий добавляются копии исследуемой карты по
 *       числу игроков, и она гарантированно приходит в руку. Видно, выполняют её
 *       или сжигают ради верхнего эффекта;</li>
 *   <li><b>модули</b> — отчёт по жетонам модулей из мешков: какие вытянуты, какие
 *       поставлены на планшет и что они дают (скорость, прочность, атака).</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.CardLab рынок|задания|модули [игроков] [партий на карту]}.
 */
public final class CardLab {

    private CardLab() {
    }

    // ==================================================================
    //  РЫНОК: все восемь карт — одна и та же
    // ==================================================================

    private static void marketMode(PrintStream out, int players, int games) throws Exception {
        GameConfig probe = LayoutLibrary.configFor(players, 1L);
        List<Map<String, Object>> cards = probe.content.get("market").entries;
        StringBuilder md = new StringBuilder();
        md.append("# Карты рынка по одной: колода из восьми копий одной карты\n\n");
        md.append("Партий на карту: ").append(games).append(", игроков: ").append(players)
          .append(", боты: ").append(Bots.describe()).append(".\n\n");
        md.append("Смысл: если карта действует ВСЮ партию и её предложения всё равно "
            + "не берут — дело в карте, а не в том, что она редко выпадает.\n\n");
        md.append("| карта | предложений взято за партию | левое | правое | ПО на игрока |\n");
        md.append("|---|---:|---:|---:|---:|\n");

        for (Map<String, Object> card : cards) {
            String id = String.valueOf(card.get("id"));
            String name = String.valueOf(card.getOrDefault("name", id));
            double offers = 0;
            double left = 0;
            double right = 0;
            double vp = 0;
            for (int g = 0; g < games; g++) {
                long seed = 2_200_000L + g;
                GameConfig base = LayoutLibrary.configFor(players, seed);
                Ruleset rules = base.ruleset.copy();
                // ВСЯ КОЛОДА — из этой карты: движок раздаёт по карте в раунд, и
                // если в колоде одна и та же, она действует все восемь раундов.
                rules.override("market.only_card", id);
                GameConfig cfg = new GameConfig(rules, base.content, players, seed,
                    base.dataRoot, base.boardSides, base.scenarioId, base.cuFacing,
                    base.scenarioFile);
                GameState s = Setup.buildGame(cfg);
                List<Agent> agents = new ArrayList<>();
                List<String> lineup = List.of("hawk", "dove", "balanced", "opportunist");
                for (int i = 0; i < players; i++) {
                    agents.add(Bots.create(lineup.get(i % lineup.size()), i,
                        new Random(seed * 31 + i), players));
                }
                double[] acc = new double[3];
                GameEngine.playGame(s, agents, ev -> {
                    if (!"action".equals(String.valueOf(ev.get("type")))
                            || !"market".equals(String.valueOf(ev.get("action")))) {
                        return;
                    }
                    if (ev.get("telemetry") instanceof Map<?, ?> m) {
                        if (Boolean.TRUE.equals(m.get("card_offer"))) {
                            acc[0]++;
                        }
                        if (m.get("offer_side") instanceof String side) {
                            if ("left".equals(side)) {
                                acc[1]++;
                            } else {
                                acc[2]++;
                            }
                        }
                    }
                });
                offers += acc[0];
                left += acc[1];
                right += acc[2];
                for (PlayerState p : s.players) {
                    vp += kelium.engine.Scoring.scorePlayer(s, p.seat)
                        .getOrDefault("total", 0);
                }
            }
            md.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f | %.2f | %.2f |%n",
                name, offers / games, left / games, right / games,
                vp / (games * (double) players)));
            out.println("  " + name + ": предложений за партию "
                + String.format(Locale.ROOT, "%.2f", offers / games));
        }
        write(out, md, "reports/balance/лаборатория-карт-рынка.md");
    }

    // ==================================================================
    //  ЗАДАНИЯ: копии карты по числу игроков
    // ==================================================================

    private static void objectivesMode(PrintStream out, int players, int games) throws Exception {
        GameConfig probe = LayoutLibrary.configFor(players, 1L);
        List<Map<String, Object>> cards = probe.content.get("objectives").entries;
        StringBuilder md = new StringBuilder();
        md.append("# Карты заданий по одной: копии по числу игроков\n\n");
        md.append("Партий на карту: ").append(games).append(", игроков: ").append(players)
          .append(", боты: ").append(Bots.describe()).append(".\n\n");
        md.append("Правило чтения (дизайнер 13.08.2026): если карта СЖИГАЕТСЯ ради "
            + "верхнего эффекта чаще, чем выполняется, — поднимать награду. Если "
            + "усиленное требование выполняется реже, чем вдвое от обычного, — "
            + "поднимать усиленную награду.\n\n");
        md.append("| задание | выполнено за партию | из них усиленно | сожжено | вердикт |\n");
        md.append("|---|---:|---:|---:|---|\n");

        for (Map<String, Object> card : cards) {
            String id = String.valueOf(card.get("id"));
            if (!"regular".equals(String.valueOf(card.getOrDefault("kind", "regular")))) {
                continue;
            }
            String name = String.valueOf(card.getOrDefault("name", id));
            double done = 0;
            double enhanced = 0;
            double burned = 0;
            for (int g = 0; g < games; g++) {
                long seed = 2_400_000L + g;
                GameConfig base = LayoutLibrary.configFor(players, seed);
                Ruleset rules = base.ruleset.copy();
                // ПО КОПИИ НА ИГРОКА добавляется в колоду заданий, чтобы карта
                // гарантированно дошла до руки — иначе её выполнение утонет в
                // случайности добора.
                rules.override("objectives.extra_copies_card", id);
                rules.override("objectives.extra_copies_count", players);
                GameConfig cfg = new GameConfig(rules, base.content, players, seed,
                    base.dataRoot, base.boardSides, base.scenarioId, base.cuFacing,
                    base.scenarioFile);
                GameState s = Setup.buildGame(cfg);
                List<Agent> agents = new ArrayList<>();
                List<String> lineup = List.of("hawk", "dove", "balanced", "opportunist");
                for (int i = 0; i < players; i++) {
                    agents.add(Bots.create(lineup.get(i % lineup.size()), i,
                        new Random(seed * 31 + i), players));
                }
                double[] acc = new double[3];
                GameEngine.playGame(s, agents, ev -> {
                    String type = String.valueOf(ev.get("type"));
                    if (!id.equals(String.valueOf(ev.get("card")))) {
                        return;
                    }
                    if ("objective".equals(type)) {
                        acc[0]++;
                        if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                            acc[1]++;
                        }
                    } else if ("objective_burn".equals(type)) {
                        acc[2]++;
                    }
                });
                done += acc[0];
                enhanced += acc[1];
                burned += acc[2];
            }
            String verdict = burned > done ? "**поднять базовую награду**"
                : enhanced * 2 < done ? "поднять усиленную награду" : "в порядке";
            md.append(String.format(Locale.ROOT, "| %s (%s) | %.2f | %.2f | %.2f | %s |%n",
                name, id, done / games, enhanced / games, burned / games, verdict));
            out.println("  " + id + " " + name + ": выполнено "
                + String.format(Locale.ROOT, "%.2f", done / games) + ", сожжено "
                + String.format(Locale.ROOT, "%.2f", burned / games));
        }
        write(out, md, "reports/balance/лаборатория-карт-заданий.md");
    }

    // ==================================================================
    //  МОДУЛИ: что тянется из мешков и что это даёт
    // ==================================================================

    private static void modulesMode(PrintStream out, int players, int games) throws Exception {
        Map<String, Integer> drawn = new TreeMap<>();
        Map<String, Integer> placed = new TreeMap<>();
        double red = 0;
        double blue = 0;
        double gold = 0;
        for (int g = 0; g < games; g++) {
            long seed = 2_600_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            List<String> lineup = List.of("hawk", "dove", "balanced", "opportunist");
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get(i % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            GameEngine.playGame(s, agents, ev -> {
                String type = String.valueOf(ev.get("type"));
                if ("module_swap".equals(type)) {
                    if (ev.get("placed_red") instanceof Map<?, ?> pr) {
                        pr.forEach((k, v) -> placed.merge(
                            "красный " + v + " на " + k, 1, Integer::sum));
                    }
                    if (ev.get("placed_blue") instanceof Map<?, ?> pb) {
                        pb.forEach((k, v) -> placed.merge(
                            "синий " + v + " на " + k, 1, Integer::sum));
                    }
                }
            });
            for (PlayerState p : s.players) {
                red += p.redModules;
                blue += p.blueModules;
                gold += p.goldModules;
                for (String t : p.redTokens) {
                    drawn.merge("красный " + t, 1, Integer::sum);
                }
                for (String t : p.blueTokens) {
                    drawn.merge("синий " + t, 1, Integer::sum);
                }
            }
        }
        StringBuilder md = new StringBuilder();
        md.append("# Модули из мешков: что вытянуто и что поставлено\n\n");
        md.append("Партий: ").append(games).append(", игроков: ").append(players)
          .append(", боты: ").append(Bots.describe()).append(".\n\n");
        md.append(String.format(Locale.ROOT,
            "На игрока к концу партии: красных %.2f, синих %.2f, позолочено %.2f.%n%n",
            red / (games * (double) players), blue / (games * (double) players),
            gold / (games * (double) players)));
        md.append("## Какие жетоны вытянуты\n\n| жетон | вытянут раз |\n|---|---:|\n");
        drawn.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v)
            .append(" |\n"));
        if (!placed.isEmpty()) {
            md.append("\n## Какие поставлены на планшет\n\n| жетон | поставлен раз |\n|---|---:|\n");
            placed.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v)
                .append(" |\n"));
        } else {
            md.append("\n**Ни один модуль не был поставлен на планшет за все партии** — "
                + "либо боты не пользуются сменой модулей, либо жетоны до них не доходят.\n");
        }
        write(out, md, "reports/balance/модули-из-мешков.md");
    }

    private static void write(PrintStream out, StringBuilder md, String path) throws Exception {
        Path file = Path.of(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
        out.println(md);
        out.println("записано: " + file.toAbsolutePath());
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "рынок";
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int games = args.length > 2 ? Integer.parseInt(args[2]) : 40;
        switch (mode) {
            case "рынок", "market" -> marketMode(out, players, games);
            case "задания", "objectives" -> objectivesMode(out, players, games);
            case "модули", "modules" -> modulesMode(out, players, games);
            default -> out.println("режимы: рынок | задания | модули");
        }
    }
}
