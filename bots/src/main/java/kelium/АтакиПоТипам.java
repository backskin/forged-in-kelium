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
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ЧЕМ И ПО КОМУ БЬЮТ — разбор боя по строкам атаки и по типам целей.
 *
 * <p>ВОПРОС ДИЗАЙНЕРА 27.08.2026: куда ставить ячейку под жетон модуля атаки —
 * на УНИВЕРСАЛЬНУЮ атаку (2 боеприпаса, любой тип) или на СПЕЦИАЛЬНУЮ (1
 * боеприпас, один тип). Общие числа партии на этот вопрос не отвечают: «боёв
 * стало больше» не говорит, чем именно били и по кому. Поэтому здесь считается
 * то, что решает:
 * <ul>
 *   <li>ДОЛЯ УДАРОВ по строкам: универсальная, специальная, модуль, золото;
 *   <li>КТО ЖЕРТВА: пехота, техника, авиация, здания и вышки;
 *   <li>СКОЛЬКО БОЕПРИПАСОВ уходит на партию — дорогая атака жрёт вдвое;
 *   <li>ЧТО СТОИТ НА ПОЛЕ к концу партии по родам: если авиацию дёшево не бьёт
 *       никто, она должна на поле задержаться.
 * </ul>
 *
 * <p>ЗАЧЕМ ПОСЛЕДНЕЕ. С планшета 2.3.0 спец-цель пехоты — техника, и авиация
 * перестала быть чьей-либо дешёвой целью нарочно: её достаёт только
 * универсальная за два боеприпаса или красный модуль. Замер должен показать, во
 * что это обошлось: стала ли авиация неуязвимой на деле или просто дорогой.
 *
 * <p>Запуск: {@code kelium.АтакиПоТипам [партий] [игроков] [свод]}
 */
public final class АтакиПоТипам {

    private АтакиПоТипам() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 150;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Map<String, long[]> поСтрокам = new LinkedHashMap<>();   // [ударов, уничтожений, БПР]
        Map<String, long[]> поЖертвам = new TreeMap<>();         // [ударов, уничтожений]
        Map<String, Long> наПоле = new TreeMap<>();
        long ударов = 0;
        long уничтожений = 0;
        long боеприпасов = 0;

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 41000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 419L + g), players));
            }
            long[] счёт = new long[3];
            GameEngine.playGame(s, ags, ev -> {
                if (!"combat_hit".equals(String.valueOf(ev.get("type")))) {
                    return;
                }
                // attacker = «род.строка», например infantry.specialized
                String[] части = String.valueOf(ev.get("attacker")).split("\\.");
                String строка = части.length > 1 ? части[1] : "?";
                boolean снесено = Boolean.TRUE.equals(ev.get("destroyed"));
                int бпр = ev.get("ammo") instanceof Number n ? n.intValue() : 0;

                long[] ст = поСтрокам.computeIfAbsent(имяСтроки(строка), k -> new long[3]);
                ст[0]++;
                ст[1] += снесено ? 1 : 0;
                ст[2] += бпр;

                long[] ж = поЖертвам.computeIfAbsent(
                    видЖертвы(String.valueOf(ev.get("victim"))), k -> new long[2]);
                ж[0]++;
                ж[1] += снесено ? 1 : 0;

                счёт[0]++;
                счёт[1] += снесено ? 1 : 0;
                счёт[2] += бпр;
            });
            ударов += счёт[0];
            уничтожений += счёт[1];
            боеприпасов += счёт[2];
            for (PlayerState p : s.players) {
                for (UnitToken u : p.unitsOnField()) {
                    наПоле.merge(u.type.code, 1L, Long::sum);
                }
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# Чем и по кому бьют\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players).append(". Места ротируются.\n\n");
        b.append("| показатель | значение |\n|---|---:|\n");
        b.append("| ударов за партию, шт | ").append(окр((double) ударов / games)).append(" |\n");
        b.append("| уничтожений за партию, шт | ")
            .append(окр((double) уничтожений / games)).append(" |\n");
        b.append("| боеприпасов на удары за партию, шт | ")
            .append(окр((double) боеприпасов / games)).append(" |\n");
        b.append("| боеприпасов на одно уничтожение | ")
            .append(уничтожений == 0 ? "—"
                : окр((double) боеприпасов / уничтожений)).append(" |\n");

        b.append("\n## Чем бьют\n\n");
        b.append("| строка атаки | ударов | доля | уничтожений | БПР на удар |\n");
        b.append("|---|---:|---:|---:|---:|\n");
        for (var e : поСтрокам.entrySet()) {
            long[] v = e.getValue();
            b.append("| ").append(e.getKey()).append(" | ").append(v[0])
                .append(" | ").append(проц(v[0], ударов))
                .append(" | ").append(v[1])
                .append(" | ").append(окр((double) v[2] / Math.max(1, v[0])))
                .append(" |\n");
        }

        b.append("\n## По кому бьют\n\n");
        b.append("| жертва | ударов | доля | уничтожений |\n|---|---:|---:|---:|\n");
        for (var e : поЖертвам.entrySet()) {
            long[] v = e.getValue();
            b.append("| ").append(e.getKey()).append(" | ").append(v[0])
                .append(" | ").append(проц(v[0], ударов))
                .append(" | ").append(v[1]).append(" |\n");
        }

        b.append("\n## Что стоит на поле к концу партии\n\n");
        b.append("| род войск | на игрока за партию |\n|---|---:|\n");
        for (var e : наПоле.entrySet()) {
            b.append("| ").append(родПоРусски(e.getKey())).append(" | ")
                .append(окр((double) e.getValue() / games / players)).append(" |\n");
        }

        Path out = Path.of("reports", "balance", "атаки-" + ruleset + "-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    /** Строка атаки по-русски; золотые половинки сводятся в одну строку. */
    private static String имяСтроки(String код) {
        if (код.startsWith("specialized_gold")) {
            return "модуль, золотая сторона";
        }
        return switch (код) {
            case "universal" -> "универсальная (2 БПР)";
            case "specialized" -> "специальная или модуль (1 БПР)";
            case "primary" -> "основная (старые планшеты)";
            case "secondary" -> "второстепенная (старые планшеты)";
            default -> код;
        };
    }

    /** Вид жертвы по ярлыку жетона в событии боя. */
    private static String видЖертвы(String ярлык) {
        if (ярлык.startsWith("infantry")) {
            return "пехота";
        }
        if (ярлык.startsWith("vehicle")) {
            return "техника";
        }
        if (ярлык.startsWith("aircraft")) {
            return "авиация";
        }
        if (ярлык.startsWith("tower")) {
            return "вышка";
        }
        return "здание: " + ярлык;
    }

    private static String родПоРусски(String код) {
        return switch (код) {
            case "infantry" -> "пехота";
            case "vehicle" -> "техника";
            case "aircraft" -> "авиация";
            case "tower" -> "вышка";
            default -> код;
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
