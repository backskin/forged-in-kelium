package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * КЛАДЁТ ЛИ БОТ МОДУЛИ ПО ДЕЛУ — а не «куда попало».
 *
 * <p>Цель дизайнера: «боты должны тащить модули боя на те рода войск, которыми
 * собираются воевать, а модули сборки — туда, где собираются производить».
 *
 * <p>Мерить «долю модулей на авиации» нельзя: её накрутит любой вес, и она
 * вырастет, даже если бот авиацией не воюет. Числитель обязан быть привязан к
 * ФАКТИЧЕСКОМУ применению, поэтому здесь считается совпадение намерения и дела:
 *
 * <ul>
 *   <li><b>мёртвый модуль боя</b> — доля красных жетонов, лежащих на роде, от
 *       которого за партию не было НИ ОДНОЙ атаки. Это и есть «положил куда
 *       попало» в чистом виде;</li>
 *   <li><b>покрытие атак</b> — доля всех атак, сделанных родом, на котором
 *       лежит красный жетон. Зеркальная мера: воюет ли бот тем, что усилил;</li>
 *   <li><b>мёртвый модуль сборки</b> — доля синих жетонов на здании, которое за
 *       партию ничего не произвело.</li>
 * </ul>
 *
 * <p>Ничего нового в движок для этого не нужно: род атакующего есть в событии
 * {@code combat_hit} (поле {@code attacker} вида «infantry.1»), а куда лёг
 * жетон — видно в состоянии на конец партии.
 *
 * <p>Запуск: {@code kelium.МодулиПоДелу [партий] [игроков] [уровень]}.
 */
public final class МодулиПоДелу {

    private МодулиПоДелу() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int уровень = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        List<String> состав = Bots.ROSTER_4;

        double красныхВсего = 0;
        double красныхМёртвых = 0;
        double синихВсего = 0;
        double синихМёртвых = 0;
        double атакВсего = 0;
        double атакСМодулем = 0;
        Map<String, double[]> поРодам = new TreeMap<>();   // {жетонов, атак}
        Map<String, double[]> ценаАтаки = new TreeMap<>();  // {атак, БПР, сносов}

        for (int g = 0; g < партий; g++) {
            long seed = 7_700_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < игроков; i++) {
                agents.add(Bots.create(состав.get((i + g) % состав.size()),
                    Bots.Level.of(уровень), i, new Random(seed * 31 + i), игроков));
            }
            // Сколько атак сделал каждый род у каждого места за партию.
            Map<Integer, Map<String, Integer>> атаки = new HashMap<>();
            // Что произвело каждое здание каждого места.
            Map<Integer, Map<String, Integer>> произвели = new HashMap<>();

            new GameEngine(s, agents, ev -> {
                String тип = String.valueOf(ev.get("type"));
                int место = ev.get("seat") instanceof Number n ? n.intValue() : -1;
                if (место < 0) {
                    return;
                }
                if ("combat_hit".equals(тип)) {
                    String кто = String.valueOf(ev.get("attacker"));
                    int точка = кто.indexOf('.');
                    String род = точка > 0 ? кто.substring(0, точка) : кто;
                    атаки.computeIfAbsent(место, k -> new HashMap<>())
                        .merge(род, 1, Integer::sum);
                    // ЦЕНА АТАКИ ПО РОДАМ. Вилка по весу авиации показала: чем
                    // больше авиации, тем МЕНЬШЕ сносов (3.82 при весе 1.15 и
                    // 2.73 при весе 20). Подозрение: авиация бьёт универсальной
                    // ячейкой за два боеприпаса, потому что её печатная цель —
                    // техника, а техники на поле почти нет. Здесь это и
                    // проверяется: сколько боеприпасов уходит на одну атаку
                    // каждого рода и сколько таких атак добивает.
                    double[] ц = ценаАтаки.computeIfAbsent(род, k -> new double[3]);
                    ц[0]++;
                    ц[1] += ev.get("ammo") instanceof Number a ? a.doubleValue() : 0;
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        ц[2]++;
                    }
                } else if ("action".equals(тип)
                        && "assembly".equals(ev.get("action"))
                        && ev.get("telemetry") instanceof Map<?, ?> м
                        && м.get("units_by_type") instanceof Map<?, ?> роды) {
                    for (var e : роды.entrySet()) {
                        произвели.computeIfAbsent(место, k -> new HashMap<>())
                            .merge(String.valueOf(e.getKey()),
                                e.getValue() instanceof Number n2 ? n2.intValue() : 0,
                                Integer::sum);
                    }
                }
            }).run();

            for (PlayerState p : s.players) {
                Map<String, Integer> моиАтаки = атаки.getOrDefault(p.seat, Map.of());
                int всегоАтак = моиАтаки.values().stream().mapToInt(Integer::intValue).sum();
                атакВсего += всегоАтак;
                for (UnitType род : p.redPlacements.keySet()) {
                    красныхВсего++;
                    int сколько = моиАтаки.getOrDefault(род.code, 0);
                    if (сколько == 0) {
                        красныхМёртвых++;
                    }
                    атакСМодулем += сколько;
                    поРодам.computeIfAbsent(род.code, k -> new double[2])[0]++;
                }
                for (var e : моиАтаки.entrySet()) {
                    поРодам.computeIfAbsent(e.getKey(), k -> new double[2])[1] += e.getValue();
                }
                Map<String, Integer> моёПроизводство = произвели.getOrDefault(p.seat, Map.of());
                for (BuildingType зд : p.bluePlacements.keySet()) {
                    синихВсего++;
                    String род = родЗдания(зд);
                    boolean работало = род != null && моёПроизводство.getOrDefault(род, 0) > 0;
                    if (!работало) {
                        синихМёртвых++;
                    }
                }
            }
        }

        out.printf("МОДУЛИ ПО ДЕЛУ · свод %s · %d партий · %d игроков · уровень %d%n",
            GameConfig.DEFAULT_RULESET, партий, игроков, уровень);
        out.println("поля: " + LayoutLibrary.describePool(игроков));
        out.printf("вес авиации в оценке бота: %s%n%n",
            System.getProperty("kelium.bot.air", "1.5"));

        out.printf("КРАСНЫЕ (бой): жетонов положено %.2f на партию-место%n",
            красныхВсего / (партий * игроков));
        out.printf("  МЁРТВЫХ (род за партию не атаковал ни разу): %.0f%%%n",
            красныхВсего == 0 ? 0 : 100 * красныхМёртвых / красныхВсего);
        out.printf("  ПОКРЫТИЕ АТАК (доля атак родом, у которого есть жетон): %.0f%%%n",
            атакВсего == 0 ? 0 : 100 * атакСМодулем / атакВсего);

        out.printf("%nСИНИЕ (сборка): жетонов положено %.2f на партию-место%n",
            синихВсего / (партий * игроков));
        out.printf("  МЁРТВЫХ (здание за партию ничего не произвело): %.0f%%%n",
            синихВсего == 0 ? 0 : 100 * синихМёртвых / синихВсего);

        out.println("\nЦЕНА АТАКИ ПО РОДАМ: атак, БПР за атаку, доля добиваний");
        for (var e : ценаАтаки.entrySet()) {
            double[] ц = e.getValue();
            out.printf("  %-10s %6.0f атак, %.2f БПР за атаку, добивают %.0f%%%n",
                e.getKey(), ц[0], ц[0] == 0 ? 0 : ц[1] / ц[0],
                ц[0] == 0 ? 0 : 100 * ц[2] / ц[0]);
        }

        out.println("\nПО РОДАМ: жетонов положено / атак сделано (на партию-место)");
        for (var e : поРодам.entrySet()) {
            out.printf("  %-10s %5.2f / %5.2f%n", e.getKey(),
                e.getValue()[0] / (партий * игроков), e.getValue()[1] / (партий * игроков));
        }
    }

    private static String родЗдания(BuildingType зд) {
        return switch (зд) {
            case BARRACKS -> "infantry";
            case FACTORY -> "vehicle";
            case AIRBASE -> "aircraft";
            case COMMAND_CENTER -> "tower";
            default -> null;
        };
    }
}
