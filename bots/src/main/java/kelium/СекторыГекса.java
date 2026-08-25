package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ВЛЕЗАЮТ ЛИ ЖЕТОНЫ В СЕКТОРЫ ГЕКСА — цена расхождения со СВОДом.
 *
 * <p>НАЙДЕНО 25.08.2026. СВОД («ПРАВИЛА», термин СЕКТОР): «Жетон занимает
 * секторы: пехота один, техника два смежных, здание — по своему размеру;
 * авиация встаёт только в небо на гексе». У гекса шесть секторов земли.
 *
 * <p>Движок этого не делает: {@code Hex.occupySides} зовётся только для ЗДАНИЙ
 * и нейтралов, а войско получает лишь {@code hexId}. Методы
 * {@code freeSideIndices} и {@code hasFreeAdjacentPair} написаны и не вызываются
 * ниоткуда — заготовленный выключатель без провода, ровно как было с
 * needs_expansion и contested.
 *
 * <p>Прямое следствие: пять карт заданий про НЕПРЕРЫВНОЕ СОСЕДСТВО невыполнимы
 * в принципе. Цепочка ищется по секторам ({@code Shapes.addCellsOf}), а
 * наземные войска секторов не занимают — значит в цепочку они не входят вовсе.
 * Замер: o50, o54, o56, o58, o62 — 451 раздача за 200 партий, условие не
 * выполнилось НИ РАЗУ.
 *
 * <p>ЧТО СЧИТАЕТ ЭТОТ СТЕНД. Сколько секторов ПОТРЕБОВАЛОСЬ БЫ жетонам, которые
 * сейчас стоят на гексе, и сколько раз это больше шести. Это и есть цена
 * синхронизации: столько положений на поле движок разрешает вопреки СВОДу.
 *
 * <p>Запуск: {@code kelium.СекторыГекса [партий] [игроков] [свод]}
 */
public final class СекторыГекса {

    private СекторыГекса() {
    }

    /** Сколько секторов земли занимает жетон войска по СВОДу. */
    private static int секторов(UnitType t) {
        return switch (t) {
            case VEHICLE -> 2;      // два смежных
            case AIRCRAFT -> 0;     // небо на гексе, земли не занимает
            default -> 1;           // пехота и вышка
        };
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        long замеров = 0;
        long перебор = 0;
        long ходов = 0;
        long ходовСПеребором = 0;
        int худший = 0;
        Map<Integer, Long> нужноСекторов = new TreeMap<>();

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 66000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 233L + g), players));
            }
            long[] счёт = new long[4];   // замеров, переборов, ходов, ходов с перебором
            int[] макс = {0};

            GameEngine.playGame(s, ags, ev -> {
                if (!"turn_end".equals(String.valueOf(ev.get("type")))) {
                    return;
                }
                счёт[2]++;
                boolean былПеребор = false;
                // ВСЕ жетоны всех игроков на гексе: секторы общие, не по игрокам.
                Map<String, Integer> надо = new HashMap<>();
                for (PlayerState p : s.players) {
                    for (UnitToken u : p.unitsOnField()) {
                        if (u.hexId() == null || u.inside()) {
                            continue;   // войско в своём здании укрыто и сектора не занимает
                        }
                        надо.merge(u.hexId(), секторов(u.type), Integer::sum);
                    }
                }
                for (var e : надо.entrySet()) {
                    Hex h = s.field.hexes.get(e.getKey());
                    if (h == null) {
                        continue;
                    }
                    // Занятое зданиями и нейтралами вычитаем: это уже в секторах.
                    int свободно = h.freeSideIndices().size();
                    int нужно = e.getValue();
                    счёт[0]++;
                    нужноСекторов.merge(нужно, 1L, Long::sum);
                    if (нужно > свободно) {
                        счёт[1]++;
                        былПеребор = true;
                        макс[0] = Math.max(макс[0], нужно - свободно);
                    }
                }
                if (былПеребор) {
                    счёт[3]++;
                }
            });

            замеров += счёт[0];
            перебор += счёт[1];
            ходов += счёт[2];
            ходовСПеребором += счёт[3];
            худший = Math.max(худший, макс[0]);
        }

        StringBuilder b = new StringBuilder();
        b.append("# Влезают ли войска в секторы гекса\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players).append(". Места ротируются.\n\n");
        b.append("По СВОДу жетон занимает секторы: пехота и вышка по одному, ")
            .append("техника два смежных, авиация встаёт в небо и земли не ")
            .append("занимает. У гекса шесть секторов земли, часть из них уже ")
            .append("под зданиями и стенками нейтралов.\n\n");
        b.append("Движок секторы войскам не выдаёт вовсе. Ниже — сколько ")
            .append("положений на поле он из-за этого разрешает вопреки СВОДу.\n\n");
        b.append("| показатель | значение |\n|---|---:|\n");
        b.append("| проверок «гекс с войсками», шт | ").append(замеров).append(" |\n");
        b.append("| из них НЕ ВЛЕЗАЕТ в секторы, шт | ").append(перебор)
            .append(" (").append(проц(перебор, замеров)).append(") |\n");
        b.append("| ходов всего, шт | ").append(ходов).append(" |\n");
        b.append("| ходов, где хоть один гекс переполнен, шт | ")
            .append(ходовСПеребором).append(" (")
            .append(проц(ходовСПеребором, ходов)).append(") |\n");
        b.append("| худший перебор, секторов сверх свободных | ").append(худший)
            .append(" |\n");

        b.append("\n## Сколько секторов требуют войска на одном гексе\n\n");
        b.append("| секторов нужно | случаев | доля |\n|---:|---:|---:|\n");
        for (var e : нужноСекторов.entrySet()) {
            b.append("| ").append(e.getKey()).append(" | ").append(e.getValue())
                .append(" | ").append(проц(e.getValue(), замеров)).append(" |\n");
        }

        Path out = Path.of("reports", "balance", "секторы-гекса-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("не влезает: " + проц(перебор, замеров) + " положений");
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static String проц(long часть, long всего) {
        return всего == 0 ? "—"
            : String.format(Locale.ROOT, "%.1f%%", 100.0 * часть / всего);
    }
}
