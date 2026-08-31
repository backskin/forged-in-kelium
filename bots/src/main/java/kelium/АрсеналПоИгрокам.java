package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * АРСЕНАЛ ПО ИГРОКАМ — строка на каждого игрока каждой партии.
 *
 * <p>Заказ дизайнера 21.08.2026: мало того, что нужно среднее — нужны МАКСИМУМЫ и
 * возможность посмотреть, что делал именно тот игрок, у которого карт было больше
 * всех: сколько сжёг на утиль, сколько установил, сколько раз применил
 * СПЕЦ-действие с установленной карты. Поэтому здесь не сводка, а таблица строк
 * («игрок в партии»), а сводка считается из неё.
 *
 * <p>СТАРТОВАЯ КАРТА ИСКЛЮЧЕНА ИЗ ВСЕХ ЧИСЕЛ: она раздаётся всем поровну и ни о
 * чём не говорит.
 *
 * <p>ОТДЕЛЬНО СЧИТАЮТСЯ ПУТИ ПРИХОДА КАРТЫ. Это и есть ответ на вопрос «почему
 * карт так мало»: путь может быть один, а остальные — только на бумаге.
 *
 * <p>Запуск: {@code kelium.АрсеналПоИгрокам [игроков] [партий] [свод]}.
 */
public final class АрсеналПоИгрокам {

    private АрсеналПоИгрокам() {
    }

    /** Одна строка базы: что было у игрока в одной партии. */
    private record Строка(int партия, int место, String бот, int взято, int установлено,
                          int сожжено, int спецПрименён, int неВлезло, int заменено,
                          int осталось) {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 150;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> стол = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        List<Строка> база = new ArrayList<>();
        Map<String, Long> пути = new TreeMap<>();

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 41000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<java.util.Set<String>> стартовые = new ArrayList<>();
            List<java.util.Set<String>> побывало = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                стартовые.add(new java.util.LinkedHashSet<>(s.player(i).arsenalHand));
                побывало.add(new java.util.LinkedHashSet<>());
            }
            List<Agent> ags = new ArrayList<>();
            String[] ктоНаМесте = new String[players];
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ктоНаМесте[i] = стол.get((i + shift) % players);
                ags.add(kelium.agents.BotCatalog.create(ктоНаМесте[i], i,
                    new Random(i * 97L + g), players));
            }
            int[][] счёт = new int[players][5];   // установлено, сожжено, спец, не влезло, заменено
            GameEngine.playGame(s, ags, ev -> {
                for (int i = 0; i < players; i++) {
                    for (String cid : s.player(i).arsenalHand) {
                        if (!стартовые.get(i).contains(cid)) {
                            побывало.get(i).add(cid);
                        }
                    }
                }
                int seat = ev.get("seat") instanceof Number n ? n.intValue() : -1;
                String t = String.valueOf(ev.get("type"));
                if (seat < 0 || seat >= players) {
                    return;
                }
                switch (t) {
                    case "arsenal" -> {
                        String карта = String.valueOf(ev.get("card"));
                        if (стартовые.get(seat).contains(карта)) {
                            return;    // стартовая карта в статистику не входит
                        }
                        String mode = String.valueOf(ev.get("mode"));
                        if (mode.startsWith("install")) {
                            счёт[seat][0]++;
                        } else if ("burn".equals(mode)) {
                            счёт[seat][1]++;
                        }
                    }
                    case "arsenal_spec_use", "ability_spec" -> счёт[seat][2]++;
                    case "arsenal_no_room" -> счёт[seat][3]++;
                    case "arsenal_replaced" -> счёт[seat][4]++;
                    // ПУТИ ПРИХОДА КАРТЫ — по тому событию, которое её принесло.
                    case "arsenal_flood" -> пути.merge(
                        "наплыв в Обновление (колода переполнена)", 1L, Long::sum);
                    case "action" -> {
                        Map<?, ?> tel = ev.get("telemetry") instanceof Map<?, ?> m
                            ? m : Map.of();
                        Object ex = tel.get("exchange");
                        if (ex != null && String.valueOf(ex).contains("draw_arsenal")) {
                            пути.merge("планшет НАУКИ: 2 трофея → две карты, оставь одну",
                                1L, Long::sum);
                        }
                    }
                    case "objective" -> {
                        // Награда задания может включать карту арсенала — смотрим
                        // саму карту задания, а не догадываемся по строке лога.
                        String cid = String.valueOf(ev.get("card"));
                        var карта = cfg.content.get("objectives").find(cid);
                        if (карта != null && (описаноАрсенал(карта.get("base_reward"))
                                || описаноАрсенал(карта.get("special_reward")))) {
                            пути.merge("награда ЗАДАНИЯ", 1L, Long::sum);
                        }
                    }
                    case "container_open" -> пути.merge("КОНТЕЙНЕР", 1L, Long::sum);
                    default -> { }
                }
            });
            for (int i = 0; i < players; i++) {
                int осталось = 0;
                for (String cid : s.player(i).arsenalHand) {
                    if (!стартовые.get(i).contains(cid)) {
                        осталось++;
                    }
                }
                база.add(new Строка(g, i, ктоНаМесте[i], побывало.get(i).size(),
                    счёт[i][0], счёт[i][1], счёт[i][2], счёт[i][3], счёт[i][4], осталось));
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# Арсенал по игрокам: сколько карт берут и что с ними делают\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players).append(", стол: ")
            .append(String.join(", ", стол)).append(". Места ротируются.\n\n");
        b.append("**Стартовая карта арсенала исключена из всех чисел.**\n\n");

        int n = база.size();
        b.append("## Сводка по ").append(n).append(" строкам «игрок в партии»\n\n");
        b.append("| показатель | среднее | максимум | доля строк с нулём |\n");
        b.append("|---|---:|---:|---:|\n");
        сводка(b, "ВЗЯТО карт за партию", база, Строка::взято);
        сводка(b, "установлено", база, Строка::установлено);
        сводка(b, "сожжено на утиль", база, Строка::сожжено);
        сводка(b, "применений СПЕЦ с установленной", база, Строка::спецПрименён);
        сводка(b, "заменило карту на полном планшете", база, Строка::заменено);
        сводка(b, "НЕ ВЛЕЗЛО (планшет полон, ничего не снял)", база, Строка::неВлезло);
        сводка(b, "осталось в руке к концу", база, Строка::осталось);

        b.append("\n## Рекордсмены: у кого карт было больше всех\n\n");
        b.append("| партия | место | бот | взято | установил | сжёг | применил СПЕЦ | не влезло |\n");
        b.append("|---|---|---|---:|---:|---:|---:|---:|\n");
        List<Строка> топ = new ArrayList<>(база);
        топ.sort(Comparator.comparingInt(Строка::взято).reversed());
        for (Строка r : топ.subList(0, Math.min(10, топ.size()))) {
            b.append("| ").append(r.партия()).append(" | ").append(r.место() + 1)
                .append(" | ").append(r.бот()).append(" | ").append(r.взято())
                .append(" | ").append(r.установлено()).append(" | ").append(r.сожжено())
                .append(" | ").append(r.спецПрименён()).append(" | ")
                .append(r.неВлезло()).append(" |\n");
        }

        b.append("\n## Откуда карты приходят (за все партии)\n\n");
        b.append("| путь | случаев за партию |\n|---|---:|\n");
        List<Map.Entry<String, Long>> rows = new ArrayList<>(пути.entrySet());
        rows.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
        for (Map.Entry<String, Long> e : rows) {
            b.append("| ").append(e.getKey()).append(" | ")
                .append(String.format("%.2f", e.getValue() / (double) games)).append(" |\n");
        }
        if (rows.isEmpty()) {
            b.append("| ни одного распознанного пути | 0.00 |\n");
        }

        Path out = Path.of("reports", "balance", "арсенал-по-игрокам.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        // Сама база — рядом, отдельным файлом: по ней можно считать что угодно ещё.
        StringBuilder csv = new StringBuilder("партия;место;бот;взято;установлено;"
            + "сожжено;спец;не_влезло;заменено;осталось\n");
        for (Строка r : база) {
            csv.append(r.партия()).append(';').append(r.место()).append(';').append(r.бот())
                .append(';').append(r.взято()).append(';').append(r.установлено())
                .append(';').append(r.сожжено()).append(';').append(r.спецПрименён())
                .append(';').append(r.неВлезло()).append(';').append(r.заменено())
                .append(';').append(r.осталось()).append('\n');
        }
        Files.writeString(Path.of("reports", "balance", "арсенал-по-игрокам.csv"),
            csv.toString(), StandardCharsets.UTF_8);
        System.out.println(b);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static boolean описаноАрсенал(Object награда) {
        return награда instanceof Map<?, ?> m && m.containsKey("arsenal");
    }

    private static void сводка(StringBuilder b, String имя, List<Строка> база,
                               java.util.function.ToIntFunction<Строка> что) {
        int max = 0;
        long сумма = 0;
        int нулей = 0;
        for (Строка r : база) {
            int v = что.applyAsInt(r);
            сумма += v;
            max = Math.max(max, v);
            if (v == 0) {
                нулей++;
            }
        }
        b.append("| ").append(имя).append(" | ")
            .append(String.format("%.2f", сумма / (double) база.size())).append(" | ")
            .append(max).append(" | ")
            .append(String.format("%.0f%%", 100.0 * нулей / база.size())).append(" |\n");
    }
}
