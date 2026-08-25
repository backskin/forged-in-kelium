package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * КАЖДАЯ КАРТА ЗАДАНИЯ ПО ОТДЕЛЬНОСТИ: приходит, выполняется, сжигается.
 *
 * <p>ЗАЧЕМ. Средние по колоде («выполнено 8.3 за партию, доля толку 26%») не
 * говорят главного: КАКИЕ карты не выполняются НИКОГДА. Задача дизайнера от
 * 24.08.2026 — научить ботов выполнять ВСЕ задания, и первый шаг — узнать, у
 * каких карт счётчик выполнений стоит на нуле. Ноль бывает двух родов, и
 * различить их можно только по числам:
 * <ul>
 *   <li>карту НЕ ВЫБИРАЮТ — она вообще не доходит до руки или её сразу жгут;
 *   <li>карту ВЫБИРАЮТ, но выполнить не выходит — тогда либо условие
 *       непосильное, либо в его проверке БАГ.
 * </ul>
 *
 * <p>Поэтому в таблице стоят рядом четыре числа на карту: пришла, выполнена,
 * выполнена усиленно, сожжена. И отдельной колонкой — ДОЛЯ ТОЛКУ карты:
 * выполнений на одно попадание в руку. Карта с большой раздачей и нулём
 * выполнений — это подозрение на баг, а не на сложность.
 *
 * <p>ЧТО НЕ СЧИТАЕТСЯ ТОЛКОМ. Сожжённая карта не считается использованной: у
 * задания есть своё назначение, и утиль — это отказ от него.
 *
 * <p>Запуск: {@code kelium.ЗаданияПоКартам [партий] [игроков] [свод]}
 */
public final class ЗаданияПоКартам {

    private ЗаданияПоКартам() {
    }

    /** Счётчики одной карты за весь прогон. */
    private static final class Карта {
        long пришла;
        long выполнена;
        long усиленно;
        long сожжена;
        /** Сколько раз подсказка видела карту в руке (знаменатель для «была готова»). */
        long вРуке;
        /** Сколько раз условие БЫЛО ВЫПОЛНЕНО — карту оставалось только сыграть. */
        long готова;
        /** Сколько раз усиленное условие было выполнено. */
        long готоваУсиленно;
        /** Сколько раз условие было НЕ выполнено, но достижимо этим же ходом. */
        long достижима;
        String имя = "";
        String условие = "";
        String вид = "";
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Map<String, Карта> карты = new TreeMap<>();
        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 55000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            if (g == 0) {
                for (Map<String, Object> e : cfg.content.get("objectives").entries) {
                    Карта к = карты.computeIfAbsent(String.valueOf(e.get("id")), x -> new Карта());
                    к.имя = String.valueOf(e.getOrDefault("name", ""));
                    к.вид = String.valueOf(e.getOrDefault("kind", "regular"));
                    Object тр = e.get("requirement");
                    к.условие = тр instanceof Map<?, ?> m
                        ? String.valueOf(m.containsKey("условие") ? m.get("условие") : m)
                        : String.valueOf(тр);
                }
            }
            List<Agent> ags = new ArrayList<>();
            // Места РОТИРУЮТСЯ: иначе замер мерил бы раскладку стола, а не карты.
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 131L + g), players));
            }
            GameEngine.playGame(s, ags, ev -> {
                String тип = String.valueOf(ev.get("type"));
                // ПОДСКАЗКИ РАЗДЕЛЯЮТ ДВЕ ПРИЧИНЫ НУЛЯ. Движок на каждом
                // СПЕЦ-действии считает по каждой карте руки, выполнено ли её
                // условие (ready) и достижимо ли оно этим ходом (reachable).
                // Карта, которая часто была ГОТОВА и ни разу не выполнена, —
                // вина ботов. Карта, которая ни разу не была готова, — вина
                // условия или его проверки.
                if ("objective_hints".equals(тип)) {
                    Object hs = ev.get("hints");
                    if (hs instanceof List<?> список) {
                        for (Object o : список) {
                            if (!(o instanceof Map<?, ?> h)) {
                                continue;
                            }
                            Карта к = счёт(карты, h.get("card"));
                            к.вРуке++;
                            if (Boolean.TRUE.equals(h.get("ready"))) {
                                к.готова++;
                            }
                            if (Boolean.TRUE.equals(h.get("enhanced_ready"))) {
                                к.готоваУсиленно++;
                            }
                            if (Boolean.TRUE.equals(h.get("reachable"))) {
                                к.достижима++;
                            }
                        }
                    }
                    return;
                }
                Object cid = ev.get("card");
                if (cid == null) {
                    return;
                }
                switch (тип) {
                    case "objective_drawn" -> счёт(карты, cid).пришла++;
                    case "objective_burn" -> счёт(карты, cid).сожжена++;
                    case "objective" -> {
                        Карта к = счёт(карты, cid);
                        к.выполнена++;
                        if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                            к.усиленно++;
                        }
                    }
                    default -> {
                    }
                }
            });
        }

        StringBuilder b = new StringBuilder();
        b.append("# Каждая карта задания по отдельности\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players)
            .append(". Места ротируются.\n\n");
        b.append("Толк карты = выполнений на одно попадание в руку. Сожжённая ")
            .append("карта толком НЕ считается: у задания своё назначение.\n\n");

        List<Map.Entry<String, Карта>> список = new ArrayList<>(карты.entrySet());
        // НУЛИ ВПЕРЁД, и внутри нулей — по числу раздач: карта, которую раздали
        // сто раз и не выполнили ни разу, требует объяснения первой.
        список.sort((x, y) -> {
            double тx = толк(x.getValue());
            double тy = толк(y.getValue());
            if (тx != тy) {
                return Double.compare(тx, тy);
            }
            return Long.compare(y.getValue().пришла, x.getValue().пришла);
        });

        long ниразу = список.stream().filter(e -> e.getValue().выполнена == 0).count();
        b.append("**Ни разу не выполнено: ").append(ниразу).append(" карт из ")
            .append(карты.size()).append("**\n\n");

        b.append("«Была готова» — сколько раз условие карты УЖЕ выполнялось и ")
            .append("карту оставалось только сыграть. Это и делит ноль на два ")
            .append("рода: карта, которая часто была готова и не сыграна, — ")
            .append("вина ботов; карта, которая не была готова ни разу, — вина ")
            .append("условия или его проверки.\n\n");
        b.append("| карта | название | вид | пришла, раз | была готова, раз | ")
            .append("выполнена, раз | усиленно, раз | сожжена, раз | ")
            .append("толк карты | сыграно из готовых |\n");
        b.append("|---|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var e : список) {
            Карта к = e.getValue();
            b.append("| ").append(e.getKey()).append(" | ").append(к.имя)
                .append(" | ").append("starting".equals(к.вид) ? "начальное" : "обычное")
                .append(" | ").append(к.пришла)
                .append(" | ").append(к.готова)
                .append(" | ").append(к.выполнена)
                .append(" | ").append(к.усиленно)
                .append(" | ").append(к.сожжена)
                .append(" | ").append(к.пришла == 0 ? "—"
                    : String.format(Locale.ROOT, "%.0f%%", 100.0 * толк(к)))
                .append(" | ").append(к.готова == 0 ? "—"
                    : String.format(Locale.ROOT, "%.0f%%", 100.0 * к.выполнена / к.готова))
                .append(" |\n");
        }

        b.append("\n## Условия карт, которые не выполнены НИ РАЗУ\n\n");
        b.append("Здесь ищут баг, а не сложность: карта, которую раздали много ")
            .append("раз и не выполнили ни разу, скорее сломана, чем трудна.\n\n");
        b.append("| карта | пришла, раз | сожжена, раз | условие |\n");
        b.append("|---|---:|---:|---|\n");
        for (var e : список) {
            Карта к = e.getValue();
            if (к.выполнена == 0) {
                b.append("| ").append(e.getKey()).append(" | ").append(к.пришла)
                    .append(" | ").append(к.сожжена)
                    .append(" | ").append(к.условие.replace('\n', ' ')).append(" |\n");
            }
        }

        Path out = Path.of("reports", "balance", "задания-по-картам-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("ни разу не выполнено: " + ниразу + " из " + карты.size());
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static double толк(Карта к) {
        return к.пришла == 0 ? 0 : (double) к.выполнена / к.пришла;
    }

    private static Карта счёт(Map<String, Карта> карты, Object cid) {
        return карты.computeIfAbsent(String.valueOf(cid), x -> new Карта());
    }
}
