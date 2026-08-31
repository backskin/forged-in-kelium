package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * АРСЕНАЛ: СКОЛЬКО КАРТ БЕРУТ — И ЧЕТЫРЕ ПЕЧАТНЫХ ОБМЕНА КАЖДОГО ПЛАНШЕТА.
 *
 * <p>Вопросы дизайнера 21.08.2026: сколько карт арсенала берут за партию (всего и
 * на игрока), как часто новая карта НЕ ВЛЕЗАЕТ в полный планшет и игрок решает
 * ничего не снимать, и как используются четыре постоянных обмена на планшете
 * рынка и четыре на планшете науки — в штуках и в долях.
 *
 * <p>СТАРТОВАЯ КАРТА АРСЕНАЛА В СТАТИСТИКУ НЕ ВХОДИТ ВООБЩЕ (требование дизайнера).
 * Она раздаётся на подготовке всем и поровну, ничего не говорит ни об игре, ни о
 * решениях игрока — и, попав в счёт, только завышает всё остальное.
 *
 * <p>ВСЁ СЧИТАЕТСЯ ПО СОБЫТИЯМ И СОСТОЯНИЮ ДВИЖКА, не по выведенным числам:
 * <ul>
 *   <li>взятые карты — объединение всех карт, побывавших в руке, МИНУС стартовая:
 *       единого события «карта пришла» в движке нет (пять путей прихода), и счёт
 *       по событиям молча забыл бы один из них;</li>
 *   <li>не влезло — событие {@code arsenal_no_room}: планшет полон, и игрок
 *       отказался что-либо снимать;</li>
 *   <li>заменено — событие {@code arsenal_replaced};</li>
 *   <li>обмены — телеметрия действий {@code market} и {@code science} (она лежит
 *       ВЛОЖЕННОЙ картой в поле {@code telemetry}, а не в самом событии).</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.ОбменыИАрсенал [игроков] [партий] [свод]}.
 */
public final class ОбменыИАрсенал {

    private ОбменыИАрсенал() {
    }

    /** Человеческие названия обменов — чтобы отчёт читался без словаря. */
    private static final Map<String, String> ИМЕНА = Map.ofEntries(
        Map.entry("coin", "РЫНОК: келемий → монеты"),
        Map.entry("ammo", "РЫНОК: келемий → боеприпасы"),
        Map.entry("cards", "РЫНОК: келемий → карты заданий"),
        Map.entry("objective_cards", "РЫНОК: келемий → карты заданий"),
        Map.entry("energy", "РЫНОК: келемий → кубик энергии"),
        Map.entry("trophy_to_coin", "НАУКА: трофеи → монеты"),
        Map.entry("move_module", "НАУКА: трофей → переставить жетон модуля"),
        Map.entry("draw_arsenal", "НАУКА: 2 трофея → две карты арсенала, оставить одну"),
        Map.entry("gild_module", "НАУКА: 2 трофея → золотой жетон модуля"));

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 150;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> стол = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Map<String, Long> рынок = new TreeMap<>();
        Map<String, Long> наука = new TreeMap<>();
        long взято = 0;
        long установлено = 0;
        long сожжено = 0;
        long неВлезло = 0;
        long заменено = 0;
        long осталосьВРуке = 0;
        long действийРынок = 0;
        long действийНаука = 0;

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 21000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            // СТАРТОВЫЕ КАРТЫ ЗАПОМИНАЮТСЯ, ЧТОБЫ ИХ ИСКЛЮЧИТЬ ИЗ ВСЕГО.
            List<java.util.Set<String>> стартовые = new ArrayList<>();
            List<java.util.Set<String>> вРуке = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                стартовые.add(new java.util.LinkedHashSet<>(s.player(i).arsenalHand));
                вРуке.add(new java.util.LinkedHashSet<>());
            }
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(стол.get((i + shift) % players), i,
                    new Random(i * 97L + g), players));
            }
            long[] c = new long[6];
            GameEngine.playGame(s, ags, ev -> {
                for (int i = 0; i < players; i++) {
                    for (String cid : s.player(i).arsenalHand) {
                        if (!стартовые.get(i).contains(cid)) {
                            вРуке.get(i).add(cid);
                        }
                    }
                }
                String t = String.valueOf(ev.get("type"));
                switch (t) {
                    case "arsenal" -> {
                        String карта = String.valueOf(ev.get("card"));
                        int seat = ev.get("seat") instanceof Number n ? n.intValue() : -1;
                        boolean стартовая = seat >= 0 && стартовые.get(seat).contains(карта);
                        if (стартовая) {
                            return;      // стартовая карта в статистику не входит
                        }
                        String mode = String.valueOf(ev.get("mode"));
                        if (mode.startsWith("install")) {
                            c[1]++;
                        } else if ("burn".equals(mode)) {
                            c[2]++;
                        }
                    }
                    case "arsenal_no_room" -> c[3]++;
                    case "arsenal_replaced" -> c[4]++;
                    case "action" -> {
                        String action = String.valueOf(ev.get("action"));
                        Map<?, ?> tel = ev.get("telemetry") instanceof Map<?, ?> m
                            ? m : Map.of();
                        if ("market".equals(action)) {
                            c[5]++;
                            for (Map.Entry<?, ?> e : tel.entrySet()) {
                                String k = String.valueOf(e.getKey());
                                if (k.startsWith("deal_") && e.getValue() instanceof Number n) {
                                    рынок.merge(k.substring("deal_".length()),
                                        n.longValue(), Long::sum);
                                }
                            }
                        } else if ("science".equals(action)) {
                            c[0]++;
                            Object ex = tel.get("exchange");
                            if (ex != null) {
                                for (String part : String.valueOf(ex).split("\\+")) {
                                    if (part.isBlank()) {
                                        continue;
                                    }
                                    // ОБМЕН, А НЕ «ОБМЕН И ЧТО ВЫПАЛО». Движок
                                    // пишет «draw_arsenal:b24» — с номером
                                    // доставшейся карты. Без отсечения хвоста
                                    // таблица распадалась на сорок строк по 2 %
                                    // вместо одной строки обмена.
                                    String id = part.trim();
                                    int двоеточие = id.indexOf(':');
                                    наука.merge(двоеточие < 0 ? id : id.substring(0, двоеточие),
                                        1L, Long::sum);
                                }
                            }
                        }
                    }
                    default -> { }
                }
            });
            for (int i = 0; i < players; i++) {
                взято += вРуке.get(i).size();
                for (String cid : s.player(i).arsenalHand) {
                    if (!стартовые.get(i).contains(cid)) {
                        осталосьВРуке++;
                    }
                }
            }
            установлено += c[1];
            сожжено += c[2];
            неВлезло += c[3];
            заменено += c[4];
            действийНаука += c[0];
            действийРынок += c[5];
        }

        int мест = games * players;
        StringBuilder b = new StringBuilder();
        b.append("# Арсенал: сколько карт берут — и печатные обмены планшетов\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players).append(", стол: ")
            .append(String.join(", ", стол)).append(". Места ротируются.\n\n");
        b.append("**СТАРТОВАЯ КАРТА АРСЕНАЛА ИСКЛЮЧЕНА ИЗ ВСЕХ ЧИСЕЛ**: она ")
            .append("раздаётся всем поровну и ни о чём не говорит.\n\n");

        b.append("## Карты арсенала\n\n");
        b.append("| что | за партию ВСЕГО | за партию на игрока |\n|---|---:|---:|\n");
        строка(b, "ВЗЯТО карт (витрина, обмены науки, награды, эффекты)",
            взято, games, мест);
        строка(b, "установлено в свою зону", установлено, games, мест);
        строка(b, "сожжено на утиль", сожжено, games, мест);
        строка(b, "заменило карту на полном планшете", заменено, games, мест);
        строка(b, "НЕ ВЛЕЗЛО: планшет полон, игрок ничего не снял", неВлезло, games, мест);
        строка(b, "осталось в руке к концу партии", осталосьВРуке, games, мест);
        b.append("\n");

        печать(b, "## Планшет РЫНКА: печатные обмены", рынок, мест, действийРынок, games);
        печать(b, "## Планшет НАУКИ: печатные обмены", наука, мест, действийНаука, games);

        b.append("\n## Как читать\n\n");
        b.append("«За партию на игрока» — абсолютное число на одного игрока.\n\n");
        b.append("«Доля» — от всех взятых обменов ЭТОГО планшета: чем пользуются, ")
            .append("а чем не пользуются вовсе.\n\n");
        b.append("«На одно действие» — сколько раз обмен берут за один заход на ")
            .append("планшет: печатным обменом можно пользоваться сколько угодно ")
            .append("раз за действие, поэтому число больше единицы здесь законно.\n");

        Path out = Path.of("reports", "balance", "арсенал-и-обмены.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println(b);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static void строка(StringBuilder b, String имя, long сумма, int games, int мест) {
        b.append("| ").append(имя).append(" | ")
            .append(String.format("%.2f", сумма / (double) games)).append(" | ")
            .append(String.format("%.2f", сумма / (double) мест)).append(" |\n");
    }

    private static void печать(StringBuilder b, String заголовок, Map<String, Long> счёт,
                               int мест, long действий, int games) {
        long всего = счёт.values().stream().mapToLong(Long::longValue).sum();
        b.append(заголовок).append("\n\n");
        b.append("Действий с этим планшетом за партию (всеми игроками): **")
            .append(String.format("%.2f", действий / (double) games)).append("**\n\n");
        b.append("| обмен | за партию на игрока | доля | на одно действие |\n");
        b.append("|---|---:|---:|---:|\n");
        List<Map.Entry<String, Long>> rows = new ArrayList<>(счёт.entrySet());
        rows.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
        for (Map.Entry<String, Long> e : rows) {
            b.append("| ").append(ИМЕНА.getOrDefault(e.getKey(), e.getKey()))
                .append(" | ").append(String.format("%.2f", e.getValue() / (double) мест))
                .append(" | ").append(всего == 0 ? "—"
                    : String.format("%.0f%%", 100.0 * e.getValue() / всего))
                .append(" | ").append(действий == 0 ? "—"
                    : String.format("%.2f", e.getValue() / (double) действий))
                .append(" |\n");
        }
        if (rows.isEmpty()) {
            b.append("| ни один обмен не взят ни разу | 0.00 | — | — |\n");
        }
        b.append("\n");
    }
}
