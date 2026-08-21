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
 * ЧЕТЫРЕ ПЕЧАТНЫХ ОБМЕНА КАЖДОГО ПЛАНШЕТА — И СКОЛЬКО КАРТ АРСЕНАЛА ПРИХОДИТ.
 *
 * <p>Два вопроса дизайнера 21.08.2026: сколько карт арсенала в среднем приходит
 * игроку за партию, и как используются четыре постоянных обмена на планшете рынка
 * и четыре на планшете науки — в штуках и в долях.
 *
 * <p>ВСЁ СЧИТАЕТСЯ ПО СОБЫТИЯМ ДВИЖКА, не по выведенным числам:
 * <ul>
 *   <li>обмены рынка — телеметрия действия {@code market}, поля
 *       {@code deal_<что>}: движок пишет, сколько раз за действие взят каждый
 *       печатный обмен;</li>
 *   <li>обмены науки — телеметрия действия {@code science}, поле
 *       {@code exchange}: взятые за действие обмены, склеенные через «+»;</li>
 *   <li>приход арсенала — ОБЪЕДИНЕНИЕМ всех карт, побывавших в руке: единого
 *       события «карта пришла» в движке нет, карты приходят пятью путями, и счёт
 *       по событиям забыл бы один из них молча. Стартовая карта считается
 *       отдельно, иначе раздача смешалась бы с добычей.</li>
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
        Map.entry("gild_module", "НАУКА: 3 трофея → золотой жетон модуля"));

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> стол = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Map<String, Long> рынок = new TreeMap<>();
        Map<String, Long> наука = new TreeMap<>();
        long пришлоАрсенала = 0;
        long стартовых = 0;
        long установлено = 0;
        long сожжено = 0;
        long действийРынок = 0;
        long действийНаука = 0;

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 21000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            // Стартовые карты арсенала уже в руках — это раздача, не добыча.
            for (int i = 0; i < players; i++) {
                стартовых += s.player(i).arsenalHand.size();
            }
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(стол.get((i + shift) % players), i,
                    new Random(i * 97L + g), players));
            }
            long[] c = new long[5];
            // СКОЛЬКО КАРТ ПОБЫВАЛО В РУКЕ — объединением, а не событием.
            //
            // Единого события «карта арсенала пришла» в движке нет: карты
            // приходят пятью разными путями (витрина, обмен науки, награда
            // задания, эффект карты, наплыв в Обновление), и каждый пишет своё.
            // Считать по событиям значило бы забыть один из путей и не узнать об
            // этом. Объединение всех карт, ЛЕЖАВШИХ в руке, ничего не забывает:
            // мимо руки карта не проходит.
            List<java.util.Set<String>> вРуке = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                вРуке.add(new java.util.LinkedHashSet<>(s.player(i).arsenalHand));
            }
            GameEngine.playGame(s, ags, ev -> {
                for (int i = 0; i < players; i++) {
                    вРуке.get(i).addAll(s.player(i).arsenalHand);
                }
                String t = String.valueOf(ev.get("type"));
                if ("arsenal".equals(t)) {
                    String mode = String.valueOf(ev.get("mode"));
                    if (mode.startsWith("install")) {
                        c[1]++;
                    } else if ("burn".equals(mode)) {
                        c[2]++;
                    }
                } else if ("action".equals(t)) {
                    String action = String.valueOf(ev.get("action"));
                    if ("market".equals(action)) {
                        c[3]++;
                        for (Map.Entry<String, Object> e : ev.entrySet()) {
                            if (e.getKey().startsWith("deal_")
                                    && e.getValue() instanceof Number n) {
                                рынок.merge(e.getKey().substring("deal_".length()),
                                    n.longValue(), Long::sum);
                            }
                        }
                    } else if ("science".equals(action)) {
                        c[4]++;
                        Object ex = ev.get("exchange");
                        if (ex != null) {
                            for (String part : String.valueOf(ex).split("\\+")) {
                                if (!part.isBlank()) {
                                    наука.merge(part.trim(), 1L, Long::sum);
                                }
                            }
                        }
                    }
                }
            });
            for (java.util.Set<String> набор : вРуке) {
                пришлоАрсенала += набор.size();
            }
            установлено += c[1];
            сожжено += c[2];
            действийРынок += c[3];
            действийНаука += c[4];
        }

        int мест = games * players;
        StringBuilder b = new StringBuilder();
        b.append("# Печатные обмены планшетов и приход карт арсенала\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players).append(", стол: ")
            .append(String.join(", ", стол)).append(". Места ротируются.\n\n");

        b.append("## Карты арсенала: сколько приходит игроку за партию\n\n");
        b.append("| откуда | за партию на игрока |\n|---|---:|\n");
        b.append("| стартовая карта (раздача) | ")
            .append(String.format("%.2f", стартовых / (double) мест)).append(" |\n");
        b.append("| добыто за партию (витрина, награды, обмены) | ")
            .append(String.format("%.2f",
                Math.max(0, пришлоАрсенала - стартовых) / (double) мест)).append(" |\n");
        b.append("| **всего побывало в руке за партию** | **")
            .append(String.format("%.2f", пришлоАрсенала / (double) мест))
            .append("** |\n");
        b.append("| из них установлено | ")
            .append(String.format("%.2f", установлено / (double) мест)).append(" |\n");
        b.append("| из них сожжено на утиль | ")
            .append(String.format("%.2f", сожжено / (double) мест)).append(" |\n\n");

        печать(b, "## Планшет РЫНКА: четыре печатных обмена", рынок, мест,
            действийРынок, games);
        печать(b, "## Планшет НАУКИ: четыре печатных обмена", наука, мест,
            действийНаука, games);

        b.append("\n## Как читать\n\n");
        b.append("«За партию на игрока» — абсолютное число: сколько раз этот обмен взят.\n");
        b.append("«Доля» — от всех взятых обменов ЭТОГО планшета: чем пользуются, а чем нет.\n");
        b.append("«На одно действие» — сколько раз обмен берут за один заход на планшет:\n");
        b.append("печатным обменом можно пользоваться сколько угодно раз за действие,\n");
        b.append("поэтому число больше единицы здесь законно.\n");

        Path out = Path.of("reports", "balance", "обмены-планшетов.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println(b);
        System.out.println("отчёт: " + out.toAbsolutePath());
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
            b.append("| — | 0.00 | — | — |\n");
        }
        b.append("\n");
    }
}
