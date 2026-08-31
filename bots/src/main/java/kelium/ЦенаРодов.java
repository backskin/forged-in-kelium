package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.BotCatalog;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.UnitToken;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ЦЕНА РОДА ВОЙСК — чем на самом деле оплачивается каждый род.
 *
 * <p>ЗАЧЕМ. На планшете у рода войск напечатана цена в МОНЕТАХ, но монеты в этой
 * игре не дефицит. Дефицит — ЭНЕРГИЯ: источники дают 10 кубиков, а ячеек у
 * потребителей 13. Значит настоящая цена рода — сколько ячеек энергии просит
 * здание, которое его производит, и вопрос «почему техники и авиации нет на
 * поле» решается не наблюдением, а этим учётом.
 *
 * <p>ЧТО СЧИТАЕТСЯ. По каждому типу здания: сколько построено и какая доля из
 * построенных ЗАПИТАНА (незапитанное здание не производит ничего, то есть
 * построено зря). По каждому роду войск: сколько нанято за партию и сколько
 * стоит на поле в конце. Плюс баланс энергии: сколько кубиков у игрока и сколько
 * ячеек он открыл.
 *
 * <p>Запуск: {@code kelium.ЦенаРодов [партий] [свод]}
 */
public final class ЦенаРодов {

    private ЦенаРодов() {
    }

    private static final List<String> ХАРАКТЕРЫ =
        List.of("builder", "supplier", "stalker", "punisher");

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        String ruleset = args.length > 1 ? args[1] : GameConfig.DEFAULT_RULESET;

        StringBuilder b = new StringBuilder();
        b.append("# Цена рода войск: чем он оплачивается на самом деле\n\n");
        b.append("Свод **").append(ruleset).append("**, по **").append(games)
            .append("** партий на состав. Считается на игрока.\n");

        for (int players = 2; players <= 4; players++) {
            // Здания: тип -> [построено, из них запитано]. Войска: род -> нанято.
            Map<String, double[]> здания = new TreeMap<>();
            Map<String, Double> нанято = new TreeMap<>();
            Map<String, Double> наПоле = new TreeMap<>();
            // БОЙ ПО РОДАМ: кто атаковал (род атакующего) -> сколько попаданий,
            // и кого убили (род жертвы) -> сколько уничтожено. Без этой разбивки
            // «боёв столько-то» не отвечает на вопрос, КЕМ воюют.
            Map<String, Double> билиРодом = new TreeMap<>();
            Map<String, Double> убитыеРода = new TreeMap<>();
            double[] бой = new double[4];   // боёв объявлено, попаданий, всухую, БПР
            // СИНИЕ МОДУЛИ (сборки). Модуль удваивает выход здания, то есть это
            // самая крупная прибавка в игре — и вопрос «куда игрок её кладёт»
            // важнее, чем сколько модулей он получил. Отдельно считается резерв:
            // модуль в резерве не работает вовсе.
            Map<String, double[]> модули = new TreeMap<>();  // здание -> [всего, на запитанном]
            double[] вРезерве = new double[1];
            // ПОЧЕМУ БОЙ НЕ ДАЛ НИ ОДНОЙ АТАКИ: причина -> сколько раз.
            Map<String, Double> причины = new TreeMap<>();
            // КУДА ЛЕЖАТ КУБИКИ ЦУ: тип здания-потребителя -> сколько кубиков.
            // Отдельная строка «простаивает на самом ЦУ» — это кубик, который
            // игрок не увёл никуда.
            Map<String, Double> кубикиЦУ = new TreeMap<>();
            double[] простой = new double[1];
            double[] энергия = new double[3];   // кубиков, ячеек открыто, ячеек закрыто
            double игроков = 0;

            for (int g = 0; g < games; g++) {
                GameConfig cfg = GameConfig.buildCached(ruleset, players, 3300L + g,
                    null, null);
                GameState s = Setup.buildGame(cfg);
                List<Agent> ags = new ArrayList<>();
                int shift = g % players;
                for (int i = 0; i < players; i++) {
                    ags.add(BotCatalog.create(
                        ХАРАКТЕРЫ.get((i + shift) % players) + ":3", i,
                        new Random(i * 53L + g), players));
                }
                GameEngine.playGame(s, ags, ev -> {
                    String тип = String.valueOf(ev.get("type"));
                    if ("action".equals(тип)) {
                        if ("combat".equals(String.valueOf(ev.get("action")))) {
                            бой[0]++;
                        }
                        if (ev.get("telemetry") instanceof Map<?, ?> t
                                && t.get("units_by_type") instanceof Map<?, ?> byType) {
                            for (var e : byType.entrySet()) {
                                if (e.getValue() instanceof Number n) {
                                    нанято.merge(String.valueOf(e.getKey()),
                                        n.doubleValue(), Double::sum);
                                }
                            }
                        }
                    } else if ("combat_hit".equals(тип)) {
                        бой[1]++;
                        if (ev.get("ammo") instanceof Number a) {
                            бой[3] += a.doubleValue();
                        }
                        // «attacker» приходит как «род.строка» — берём род.
                        String атк = String.valueOf(ev.get("attacker"));
                        int точка = атк.indexOf('.');
                        билиРодом.merge(точка > 0 ? атк.substring(0, точка) : атк,
                            1.0, Double::sum);
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            убитыеРода.merge(String.valueOf(ev.get("victim")),
                                1.0, Double::sum);
                        }
                    } else if ("combat_dry".equals(тип)) {
                        бой[2]++;
                        причины.merge(String.valueOf(ev.get("reason")), 1.0, Double::sum);
                    }
                });

                for (int i = 0; i < players; i++) {
                    игроков++;
                    var p = s.player(i);
                    for (BuildingToken bt : p.buildingsOnField()) {
                        String имя = имяЗдания(bt);
                        double[] пара = здания.computeIfAbsent(имя, k -> new double[3]);
                        пара[0]++;
                        if (bt.energySlots == 0 || bt.energyPlaced >= bt.energySlots) {
                            пара[1]++;
                        }
                        пара[2] = bt.energySlots;
                    }
                    for (UnitToken u : p.unitsOnField()) {
                        наПоле.merge(u.type.code, 1.0, Double::sum);
                    }
                    for (BuildingToken bt : p.buildingsOnField()) {
                        энергия[0] += bt.energyPlaced;
                        энергия[1] += bt.energySlots;
                    }
                    вРезерве[0] += p.blueModules;
                    // uid'ы ЦУ этого игрока — по ним узнаём кубики именно ЦУ.
                    java.util.Set<Integer> цу = new java.util.HashSet<>();
                    for (BuildingToken bt : p.buildingsOnField()) {
                        if (bt.type == kelium.core.BuildingType.COMMAND_CENTER) {
                            цу.add(bt.uid);
                        }
                        простой[0] += bt.energyIdle;
                    }
                    for (BuildingToken bt : p.buildingsOnField()) {
                        for (var e : bt.energyBySource.entrySet()) {
                            if (цу.contains(e.getKey())) {
                                String куда = bt.type
                                    == kelium.core.BuildingType.COMMAND_CENTER
                                    ? "на самом ЦУ (вышки и боеприпасы)"
                                    : имяЗдания(bt);
                                кубикиЦУ.merge(куда, (double) e.getValue(), Double::sum);
                            }
                        }
                    }
                    for (var e : p.bluePlacements.entrySet()) {
                        double[] м = модули.computeIfAbsent(e.getKey().code,
                            k -> new double[2]);
                        м[0]++;
                        // Модуль на НЕЗАПИТАННОМ здании не делает ничего: здание
                        // в Снаряжении не участвует, удваивать нечего.
                        for (BuildingToken bt : p.buildingsOnField()) {
                            if (bt.type == e.getKey()
                                    && (bt.energySlots == 0
                                        || bt.energyPlaced >= bt.energySlots)) {
                                м[1]++;
                                break;
                            }
                        }
                    }
                }
            }

            double и = Math.max(1, игроков);
            b.append("\n## На ").append(players).append(" игрока\n\n");
            b.append("### Здания: построено и сколько из них работает\n\n");
            b.append("| здание | ячеек энергии, шт | стоит на поле, шт | ")
                .append("из них запитано, шт | работает, % |\n");
            b.append("|---|---:|---:|---:|---:|\n");
            for (var e : здания.entrySet()) {
                double[] v = e.getValue();
                b.append("| ").append(e.getKey())
                    .append(" | ").append(ч(v[2]))
                    .append(" | ").append(ч(v[0] / и))
                    .append(" | ").append(ч(v[1] / и))
                    .append(" | ").append(ч(v[0] == 0 ? 0 : 100 * v[1] / v[0]))
                    .append(" |\n");
            }
            b.append("\n### Войска: нанято за партию и осталось на поле\n\n");
            b.append("| род | нанято за партию, шт | на поле в конце, шт |\n");
            b.append("|---|---:|---:|\n");
            for (String род : List.of("infantry", "vehicle", "aircraft", "tower")) {
                b.append("| ").append(род)
                    .append(" | ").append(ч(нанято.getOrDefault(род, 0.0) / и))
                    .append(" | ").append(ч(наПоле.getOrDefault(род, 0.0) / и))
                    .append(" |\n");
            }
            b.append("\n### Два кубика ЦУ: куда игрок их кладёт\n\n");
            b.append("ЦУ даёт 2 кубика и остаётся источником, даже стоя тёмным: ")
                .append("оба кубика можно увести куда угодно. Своя ячейка у ЦУ ")
                .append("одна — она нужна только чтобы ЦУ само делало вышки или ")
                .append("боеприпасы.\n\n");
            b.append("| где лежит кубик ЦУ | кубиков на игрока, шт |\n|---|---:|\n");
            for (var e : кубикиЦУ.entrySet()) {
                b.append("| ").append(e.getKey()).append(" | ")
                    .append(ч(e.getValue() / и)).append(" |\n");
            }
            b.append("\nПростаивает на источниках (ждёт Смены энергии): **")
                .append(ч(простой[0] / и)).append("** кубиков на игрока.\n");

            b.append("\n### Синие модули сборки: куда их ставят\n\n");
            b.append("Модуль удваивает выход здания (1 → 2, золотой → 3). ")
                .append("На незапитанном здании он не делает ничего.\n\n");
            b.append("| куда поставлен | модулей на игрока, шт | ")
                .append("из них на работающем здании, шт |\n|---|---:|---:|\n");
            for (var e : модули.entrySet()) {
                b.append("| ").append(e.getKey()).append(" | ")
                    .append(ч(e.getValue()[0] / и)).append(" | ")
                    .append(ч(e.getValue()[1] / и)).append(" |\n");
            }
            b.append("| ЛЕЖИТ В РЕЗЕРВЕ (не работает) | ")
                .append(ч(вРезерве[0] / и)).append(" | 0.00 |\n");

            b.append("\n### Бой: кем воюют и кого убивают\n\n");
            b.append("| показатель | за партию, шт |\n|---|---:|\n");
            b.append("| боёв объявлено | ").append(ч(бой[0] / games)).append(" |\n");
            b.append("| попаданий нанесено | ").append(ч(бой[1] / games)).append(" |\n");
            b.append("| залпов всухую | ").append(ч(бой[2] / games)).append(" |\n");
            b.append("| боеприпасов истрачено в атаках | ")
                .append(ч(бой[3] / games)).append(" |\n");
            b.append("\nБои БЕЗ ЕДИНОЙ АТАКИ — почему. Боеприпасы в них не ")
                .append("тратятся: движок платит только когда жертва найдена. ")
                .append("Теряется действие.\n\n");
            b.append("| причина | раз за партию, шт |\n|---|---:|\n");
            for (var e : причины.entrySet()) {
                b.append("| ").append(e.getKey()).append(" | ")
                    .append(ч(e.getValue() / games)).append(" |\n");
            }

            b.append("\n| чем били | попаданий за партию, шт |\n|---|---:|\n");
            for (var e : билиРодом.entrySet()) {
                b.append("| ").append(e.getKey()).append(" | ")
                    .append(ч(e.getValue() / games)).append(" |\n");
            }
            b.append("\n| кого уничтожили | жетонов за партию, шт |\n|---|---:|\n");
            for (var e : убитыеРода.entrySet()) {
                b.append("| ").append(e.getKey()).append(" | ")
                    .append(ч(e.getValue() / games)).append(" |\n");
            }
            b.append("\nЭнергия на игрока: **").append(ч(энергия[0] / и))
                .append("** кубиков при **").append(ч(энергия[1] / и))
                .append("** открытых ячейках потребителей.\n");
        }

        Path out = Path.of("reports", "balance", "цена-родов.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static String имяЗдания(BuildingToken bt) {
        return bt.level == null ? bt.type.code : bt.type.code + bt.level;
    }

    private static String ч(double v) {
        return String.format("%.2f", v).replace(',', '.');
    }
}
