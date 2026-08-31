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
import kelium.core.PlayerState;
import kelium.core.Target;
import kelium.core.UnitToken;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ПОЧЕМУ БЬЮТ ДОРОГОЙ АТАКОЙ: бот не додумал или дешёвой нечем?
 *
 * <p>ВОПРОС ДИЗАЙНЕРА 27.08.2026, и он справедливый. Замер показал, что 77.6%
 * ударов идут УНИВЕРСАЛЬНОЙ атакой за два боеприпаса, хотя специальная стоит
 * один. Вывод «модуль лучше держать на специальной ячейке» после этого повисает:
 * возможно, замер поймал не разницу правил, а то, что бот не умеет подводить к
 * цели нужный род войск.
 *
 * <p>РАЗДЕЛЯЕТСЯ ЭТО ОДНИМ ЧИСЛОМ. На каждый удар универсальной проверяется:
 * стоял ли на ТОМ ЖЕ гексе, откуда бьют, свой жетон, чья СПЕЦИАЛЬНАЯ атака
 * достаёт эту самую жертву. Если стоял — бот переплатил по собственной глупости.
 * Если не стоял — дешёвой атаки у него в этом бою не было вовсе, и виновата не
 * голова бота, а расклад: дешёвые цели розданы редким родам.
 *
 * <p>Запуск: {@code kelium.ДешёвыйУдар [партий] [игроков] [свод]}
 */
public final class ДешёвыйУдар {

    private ДешёвыйУдар() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 150;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        long универсальных = 0;
        long былаДешёвая = 0;
        long дешёвых = 0;
        // По виду жертвы: сколько ударов дорогой и сколько из них можно было дёшево.
        Map<String, long[]> поЖертвам = new TreeMap<>();

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 71000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 617L + g), players));
            }
            long[] счёт = new long[3];
            Map<String, long[]> жертвы = new TreeMap<>();

            GameEngine.playGame(s, ags, ev -> {
                if (!"combat_hit".equals(String.valueOf(ev.get("type")))) {
                    return;
                }
                String строка = String.valueOf(ev.get("attacker"));
                boolean дорогая = строка.endsWith(".universal");
                String жертва = String.valueOf(ev.get("victim"));
                Target вид = категория(жертва);
                if (!дорогая) {
                    счёт[2]++;
                    return;
                }
                счёт[0]++;
                long[] ж = жертвы.computeIfAbsent(имяЖертвы(вид), k -> new long[2]);
                ж[0]++;
                // БЫЛА ЛИ ДЕШЁВАЯ АЛЬТЕРНАТИВА на том же гексе, откуда били.
                int seat = ((Number) ev.get("seat")).intValue();
                String откуда = String.valueOf(ev.get("source"));
                PlayerState p = s.player(seat);
                boolean могДёшево = false;
                for (UnitToken u : p.unitsOnField()) {
                    if (!откуда.equals(u.hexId())) {
                        continue;
                    }
                    Target спец = p.board.troop.specializedTarget(u.type);
                    if (спец != null && спец == вид) {
                        могДёшево = true;
                        break;
                    }
                }
                if (могДёшево) {
                    счёт[1]++;
                    ж[1]++;
                }
            });
            универсальных += счёт[0];
            былаДешёвая += счёт[1];
            дешёвых += счёт[2];
            жертвы.forEach((k, v) -> {
                long[] о = поЖертвам.computeIfAbsent(k, x -> new long[2]);
                о[0] += v[0];
                о[1] += v[1];
            });
        }

        StringBuilder b = new StringBuilder();
        b.append("# Почему бьют дорогой атакой\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players).append(". Места ротируются.\n\n");
        b.append("На каждый удар УНИВЕРСАЛЬНОЙ (2 БПР) проверено: стоял ли на том же ")
            .append("гексе свой жетон, чья СПЕЦИАЛЬНАЯ атака (1 БПР) достаёт эту же ")
            .append("жертву. Стоял — бот переплатил сам. Не стоял — дешёвой атаки у ")
            .append("него не было вовсе.\n\n");
        b.append("| показатель | значение |\n|---|---:|\n");
        b.append("| ударов дешёвой (1 БПР) | ").append(дешёвых).append(" |\n");
        b.append("| ударов дорогой (2 БПР) | ").append(универсальных).append(" |\n");
        b.append("| из них МОЖНО было дёшево | ").append(былаДешёвая)
            .append(" (").append(проц(былаДешёвая, универсальных)).append(") |\n");
        b.append("| из них дешёвой НЕ БЫЛО | ").append(универсальных - былаДешёвая)
            .append(" (").append(проц(универсальных - былаДешёвая, универсальных))
            .append(") |\n");

        b.append("\n## По кому били дорогой\n\n");
        b.append("| жертва | ударов дорогой | из них можно было дёшево |\n|---|---:|---:|\n");
        for (var e : поЖертвам.entrySet()) {
            long[] v = e.getValue();
            b.append("| ").append(e.getKey()).append(" | ").append(v[0])
                .append(" | ").append(v[1]).append(" (").append(проц(v[1], v[0]))
                .append(") |\n");
        }

        Path out = Path.of("reports", "balance", "дешёвый-удар-" + ruleset + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("дорогой: " + универсальных + ", из них можно дёшево: "
            + былаДешёвая + " (" + проц(былаДешёвая, универсальных) + ")");
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static Target категория(String ярлык) {
        if (ярлык.startsWith("infantry")) {
            return Target.INFANTRY;
        }
        if (ярлык.startsWith("vehicle")) {
            return Target.VEHICLE;
        }
        if (ярлык.startsWith("aircraft")) {
            return Target.AIRCRAFT;
        }
        return Target.BUILDINGS_TOWERS;      // вышки и все здания
    }

    private static String имяЖертвы(Target t) {
        return switch (t) {
            case INFANTRY -> "пехота";
            case VEHICLE -> "техника";
            case AIRCRAFT -> "авиация";
            case BUILDINGS_TOWERS -> "здания и вышки";
        };
    }

    private static String проц(long часть, long всего) {
        return всего == 0 ? "—"
            : String.format(Locale.ROOT, "%.1f%%", 100.0 * часть / всего);
    }
}
