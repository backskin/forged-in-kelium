package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * РАВНЫ ЛИ ПЛОМБЫ МЕЖДУ СОБОЙ.
 *
 * <p>ЗАМЫСЕЛ ДИЗАЙНЕРА 25.08.2026: жетон уничтожения ЦУ лежит лицом на планшете
 * владельца и заваривает ячейку специальной атаки одного рода — какого, решает
 * жеребьёвка. Красиво и без единого решения при подготовке, но опасность
 * очевидна: пломбы НЕ равны по цене. Запечатанная техника лишает единственной
 * дешёвой атаки по зданиям, то есть по чужому ЦУ; запечатанная вышка — куда
 * мягче. Если разница велика, жеребьёвка становится лотереей на старте, а это
 * худший род несправедливости: игрок проиграл до первого хода.
 *
 * <p>МЕРЯЕТСЯ ГЛАВНОЕ И ЧЕСТНО: доля побед и средние очки игрока в разрезе
 * ТОГО, ЧТО ЕМУ ЗАПЕЧАТАЛИ. Места и характеры ботов ротируются, поэтому разница
 * между строками — это разница пломб, а не рассадки.
 *
 * <p>Запуск: {@code kelium.Пломбы [партий] [игроков] [свод]}
 */
public final class Пломбы {

    private Пломбы() {
    }

    /** Счётчики одной пломбы. */
    private static final class Итог {
        long партий;
        long побед;
        long очков;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 150;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : "1.24.0-пломба";
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Map<String, Итог> поПломбам = new LinkedHashMap<>();
        long сносовЦУ = 0;
        long военныхПобед = 0;

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 31000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            // Места И характеры ротируются: иначе замер поймал бы рассадку.
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 313L + g), players));
            }
            GameEngine.playGame(s, ags, ev -> { });

            for (PlayerState p : s.players) {
                String пломба = p.sealedUnit == null ? "без пломбы" : p.sealedUnit.code;
                Итог t = поПломбам.computeIfAbsent(пломба, k -> new Итог());
                t.партий++;
                t.очков += Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0);
                if (s.winner != null && s.winner == p.seat) {
                    t.побед++;
                }
                сносовЦУ += p.cuKills;
            }
            if ("military".equals(s.winCondition)) {
                военныхПобед++;
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# Равны ли пломбы между собой\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players)
            .append(". Места и характеры ротируются.\n\n");
        b.append("Пломба заваривает ячейку СПЕЦИАЛЬНОЙ атаки одного рода: этот род ")
            .append("бьёт только универсальной за 2 боеприпаса, пока цело своё ЦУ.\n\n");
        b.append("| что запечатано | партий | средние очки | побед | доля побед |\n");
        b.append("|---|---:|---:|---:|---:|\n");
        for (var e : поПломбам.entrySet()) {
            Итог t = e.getValue();
            b.append("| ").append(имя(e.getKey())).append(" | ").append(t.партий)
                .append(" | ").append(окр((double) t.очков / Math.max(1, t.партий)))
                .append(" | ").append(t.побед)
                .append(" | ").append(проц(t.побед, t.партий)).append(" |\n");
        }
        b.append("\n| показатель | значение |\n|---|---:|\n");
        b.append("| сносов ЦУ за партию, шт | ")
            .append(окр((double) сносовЦУ / games)).append(" |\n");
        b.append("| военных побед | ").append(военныхПобед).append(" из ").append(games)
            .append(" (").append(проц(военныхПобед, games)).append(") |\n");

        Path out = Path.of("reports", "balance", "пломбы-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static String имя(String code) {
        return switch (code) {
            case "infantry" -> "пехота";
            case "vehicle" -> "техника";
            case "aircraft" -> "авиация";
            case "tower" -> "вышка";
            default -> code;
        };
    }

    private static String проц(long часть, long всего) {
        return всего == 0 ? "—"
            : String.format(Locale.ROOT, "%.1f%%", 100.0 * часть / всего);
    }

    private static String окр(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
