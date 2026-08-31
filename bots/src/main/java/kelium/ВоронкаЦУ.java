package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ПОЧЕМУ ЧУЖОЕ ЦУ ПОЧТИ НЕ СНОСЯТ — ВОРОНКА ОТ ВОЗМОЖНОСТИ ДО СНОСА.
 *
 * <p>ЗАМЕР 24.08.2026 показал: ЦУ сносят 0.10–0.20 раза за партию, то есть в
 * девяти партиях из десяти этого не происходит вовсе. Военная победа требует
 * ДВУХ сносов — значит она недостижима не из-за отстройки, а раньше. Но «редко»
 * не объясняет ПОЧЕМУ, а вариантов три, и они лечатся по-разному:
 * <ul>
 *   <li>НЕТ ВОЗМОЖНОСТИ: чужое ЦУ ни разу не оказывается по соседству с войском;
 *   <li>ЕСТЬ ВОЗМОЖНОСТЬ, НЕТ ПОПЫТКИ: соседство было, а атаки по ЦУ не было —
 *       это вина ботов или невыгодности удара;
 *   <li>ЕСТЬ ПОПЫТКА, НЕТ СНОСА: атаки шли, но три прочности не выбиты — тогда
 *       дело в цене и темпе, а не в желании.
 * </ul>
 *
 * <p>Воронка считается так: соседство проверяется в конце КАЖДОГО хода по
 * состоянию поля, попытки и урон — по событиям боя, снос — по {@code
 * cu_destroyed}. Соседством считается только то, откуда ВООБЩЕ можно ударить:
 * гекс войска рядом с гексом чужого ЦУ.
 *
 * <p>Запуск: {@code kelium.ВоронкаЦУ [партий] [игроков] [свод]}
 */
public final class ВоронкаЦУ {

    private ВоронкаЦУ() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        long ходов = 0;
        long ходовСВозможностью = 0;
        long партийСВозможностью = 0;
        long атакПоЦУ = 0;
        // Ходы, где возможность БЫЛА и игрок при этом сыграл Бой, и сколько из
        // них кончились ударом именно по ЦУ. Это и разделяет «не хочет» от «не
        // может»: если почти все такие бои идут по ЦУ, боты хотят, но Бой им
        // достаётся редко.
        long боёвПриВозможности = 0;
        long боёвПоЦУ = 0;
        long сносов = 0;
        long партий = 0;
        // Сколько своих войск стояло рядом с чужим ЦУ в тот момент, когда
        // возможность была: одним жетоном три прочности за ход не выбить.
        List<Integer> войскРядом = new ArrayList<>();

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 88000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 211L + g), players));
            }
            boolean[] былаВозможность = {false};
            // ходы · ходы с возможностью · атаки по ЦУ · ходы, где ПРИ возможности
            // игрок сыграл Бой · из них с атакой по ЦУ
            long[] счёт = new long[5];
            // Была ли возможность у ТОГО, кто сейчас ходит, и ударил ли он по ЦУ.
            // Возможность считается ПЕРЕД действиями хода, иначе Бой уже прошёл.
            boolean[] возможностьСейчас = {false};
            boolean[] боиСейчас = {false};
            boolean[] удармПоЦУ = {false};

            GameEngine.playGame(s, ags, ev -> {
                String тип = String.valueOf(ev.get("type"));
                switch (тип) {
                    case "turn_orders" -> {
                        // НАЧАЛО ХОДА: соседство надо мерить ЗДЕСЬ. К концу хода
                        // войско могло уехать или погибнуть, и «возможность» на
                        // момент решения о Бое была бы посчитана не та.
                        int ходит = ev.get("seat") instanceof Number n ? n.intValue() : -1;
                        возможностьСейчас[0] = ходит >= 0
                            && войскРядомСЧужимЦУ(s, ходит) > 0;
                        боиСейчас[0] = false;
                        удармПоЦУ[0] = false;
                    }
                    case "action" -> {
                        if ("combat".equals(String.valueOf(ev.get("action")))) {
                            боиСейчас[0] = true;
                        }
                    }
                    case "combat_hit" -> {
                        // Цель боя — ЦУ? Ярлык жертвы содержит тип жетона.
                        String жертва = String.valueOf(ev.get("victim"));
                        if (жертва.contains("command_center")) {
                            счёт[2]++;
                            удармПоЦУ[0] = true;
                        }
                    }
                    case "turn_end" -> {
                        счёт[0]++;
                        if (возможностьСейчас[0] && боиСейчас[0]) {
                            счёт[3]++;
                            if (удармПоЦУ[0]) {
                                счёт[4]++;
                            }
                        }
                        int ходил = ev.get("seat") instanceof Number n ? n.intValue() : -1;
                        if (ходил < 0) {
                            return;
                        }
                        int рядом = войскРядомСЧужимЦУ(s, ходил);
                        if (рядом > 0) {
                            счёт[1]++;
                            былаВозможность[0] = true;
                            войскРядом.add(рядом);
                        }
                    }
                    default -> {
                    }
                }
            });

            ходов += счёт[0];
            ходовСВозможностью += счёт[1];
            атакПоЦУ += счёт[2];
            боёвПриВозможности += счёт[3];
            боёвПоЦУ += счёт[4];
            if (былаВозможность[0]) {
                партийСВозможностью++;
            }
            for (PlayerState p : s.players) {
                сносов += p.cuKills;
            }
            партий++;
        }

        StringBuilder b = new StringBuilder();
        b.append("# Почему чужое ЦУ почти не сносят\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players).append(". Места ротируются.\n\n");
        b.append("Возможность = в конце своего хода у игрока есть войско на гексе, ")
            .append("соседнем с гексом чужого ЦУ. Оттуда можно ударить.\n\n");
        b.append("| ступень воронки | значение |\n|---|---:|\n");
        b.append("| ходов всего, шт | ").append(ходов).append(" |\n");
        b.append("| ходов, когда рядом стояло чужое ЦУ, шт | ").append(ходовСВозможностью)
            .append(" (").append(проц(ходовСВозможностью, ходов)).append(") |\n");
        b.append("| партий, где такая возможность была хоть раз | ")
            .append(партийСВозможностью).append(" из ").append(партий)
            .append(" (").append(проц(партийСВозможностью, партий)).append(") |\n");
        b.append("| атак ПО ЦУ за прогон, шт | ").append(атакПоЦУ).append(" |\n");
        b.append("| атак по ЦУ на партию, шт | ")
            .append(окр((double) атакПоЦУ / партий)).append(" |\n");
        b.append("| сносов ЦУ за прогон, шт | ").append(сносов).append(" |\n");
        b.append("| своих войск рядом с чужим ЦУ, среднее | ")
            .append(войскРядом.isEmpty() ? "—" : окр(войскРядом.stream()
                .mapToInt(Integer::intValue).average().orElse(0))).append(" |\n");
        b.append("| своих войск рядом с чужим ЦУ, максимум | ")
            .append(войскРядом.isEmpty() ? "—" : String.valueOf(войскРядом.stream()
                .mapToInt(Integer::intValue).max().orElse(0))).append(" |\n");

        b.append("| ходов, где возможность была И игрок сыграл Бой, шт | ")
            .append(боёвПриВозможности).append(" (")
            .append(проц(боёвПриВозможности, ходовСВозможностью))
            .append(" от ходов с возможностью) |\n");
        b.append("| из них с ударом по ЦУ, шт | ").append(боёвПоЦУ).append(" (")
            .append(проц(боёвПоЦУ, боёвПриВозможности)).append(") |\n");

        b.append("\n## Где теряется военная победа\n\n");
        b.append("* из ходов с возможностью до атаки по ЦУ доходит **")
            .append(проц(атакПоЦУ, ходовСВозможностью)).append("** ходов;\n");
        b.append("* НО Бой при этой возможности игрок играет лишь в **")
            .append(проц(боёвПриВозможности, ходовСВозможностью))
            .append("** таких ходов, а когда играет — бьёт по ЦУ в **")
            .append(проц(боёвПоЦУ, боёвПриВозможности)).append("** случаев;\n");
        b.append("* из атак по ЦУ сносом кончается **")
            .append(проц(сносов, атакПоЦУ)).append("** атак ")
            .append("(у ЦУ три прочности, значит на снос нужно три попадания).\n");

        Path out = Path.of("reports", "balance", "воронка-цу-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    /** Сколько своих войск стоит рядом с гексом чужого ЦУ. */
    private static int войскРядомСЧужимЦУ(GameState s, int seat) {
        List<String> чужиеЦУ = new ArrayList<>();
        for (PlayerState p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            for (BuildingToken b : p.buildingsOnField()) {
                if (b.type == BuildingType.COMMAND_CENTER && b.hexId != null) {
                    чужиеЦУ.add(b.hexId);
                }
            }
        }
        if (чужиеЦУ.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (UnitToken u : s.player(seat).unitsOnField()) {
            if (u.hexId() == null) {
                continue;
            }
            for (String цу : чужиеЦУ) {
                if (цу.equals(u.hexId()) || s.field.neighbors(u.hexId()).contains(цу)) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    private static String проц(long часть, long всего) {
        return всего == 0 ? "—"
            : String.format(Locale.ROOT, "%.1f%%", 100.0 * часть / всего);
    }

    private static String окр(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
