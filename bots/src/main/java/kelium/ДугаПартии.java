package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ДУГА ПАРТИИ — растёт ли насыщение цикла от раунда к раунду (23.09.2026).
 *
 * <p>Вопрос дизайнера: партия не ощущается развивающейся. По кредо проекта рост
 * даётся не новыми правилами, а тем, что тот же цикл прокручивается всё
 * мощнее. Замер отвечает, так ли это сейчас: на каждый раунд, средним на
 * игрока, —
 * <ul>
 *   <li>ТЕМП: действий с карты приказа, действий из наград заданий,
 *       спец-действий;</li>
 *   <li>ВЫХОД ЦИКЛА: боеприпасов и войск за Снаряжение, келемия за Добычу,
 *       попаданий и сносов в бою;</li>
 *   <li>ТАБЛО на конец раунда: военных зданий, добытчиков и станций на поле,
 *       модулей, шагов на треках, установленного арсенала, выполненных
 *       заданий, войск на поле;</li>
 *   <li>ЗАПАС на конец раунда: монеты, боеприпасы, келемий;</li>
 *   <li>ОЧКИ на конец раунда и отрыв лидера от последнего.</li>
 * </ul>
 *
 * <p>Играют ГРОССМЕЙСТЕРЫ (лучший уровень, с горизонтом) составом
 * {@link Bots#ROSTER_4}; цвета фракций крутятся по местам от партии к партии,
 * так что место, характер и цвет взаимно гасятся.
 *
 * <p>Запуск: {@code kelium.ДугаПартии [партий] [уровень]}.
 */
public final class ДугаПартии {

    private ДугаПартии() {
    }

    private static final int МАКС = 12;
    private static final List<String> ЦВЕТА = List.of("red", "green", "blue", "yellow");

    /** Числа одной строки таблицы — в порядке печати. */
    private static final String[] КЛЮЧИ = {
        "действ", "награда", "спец", "БПР+", "войск+", "келем+", "попад", "снос",
        "воен", "шахт", "станц", "модул", "шагов", "арсен", "задан", "войск",
        "монет", "БПР", "келем", "ПО", "отрыв"};

    /** Итог одной партии: суммы по раундам (на всех игроков) и финал по цветам. */
    private record Итог(Map<Integer, double[]> поРаундам, Map<Integer, Integer> игроковВРаунде,
                        String[] цвет, double[] очки, int победитель, int раундов) {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int уровень = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int игроков = 4;
        int потоков = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        out.printf("ДУГА ПАРТИИ · свод %s · %d партий · уровень %d · потоков %d%n%n",
            GameConfig.DEFAULT_RULESET, партий, уровень, потоков);

        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        AtomicInteger сделано = new AtomicInteger();
        List<Future<Итог>> задания = new ArrayList<>();
        for (int g = 0; g < партий; g++) {
            final int номер = g;
            задания.add(пул.submit(() -> {
                Итог и = партия(номер, игроков, уровень);
                int n = сделано.incrementAndGet();
                if (n % Math.max(1, партий / 10) == 0) {
                    System.err.printf("  … %d/%d%n", n, партий);
                }
                return и;
            }));
        }
        List<Итог> итоги = new ArrayList<>();
        for (Future<Итог> f : задания) {
            итоги.add(f.get());
        }
        пул.shutdown();
        напечатать(out, итоги, игроков);
    }

    private static Итог партия(int номер, int игроков, int уровень) {
        long seed = 9_100_000L + номер;
        List<String> стороны = new ArrayList<>();
        for (int i = 0; i < игроков; i++) {
            стороны.add(ЦВЕТА.get((i + номер) % ЦВЕТА.size()));
        }
        GameConfig base = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, игроков, seed,
            null, стороны);
        GameState s = Setup.buildGame(LayoutLibrary.configFor(base, игроков, seed));
        List<String> состав = Bots.ROSTER_4;
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < игроков; i++) {
            agents.add(Bots.create(состав.get((i + номер / 4) % состав.size()),
                Bots.Level.of(уровень), i, new Random(seed * 31 + i), игроков));
        }

        Map<Integer, double[]> поРаундам = new LinkedHashMap<>();
        Map<Integer, Integer> игроковВРаунде = new LinkedHashMap<>();
        double[] текущий = new double[КЛЮЧИ.length];   // счётчики событий раунда

        GameEngine.playGame(s, agents, ev -> {
            String тип = String.valueOf(ev.get("type"));
            switch (тип) {
                case "action" -> {
                    if (!Boolean.TRUE.equals(ev.get("ok"))) {
                        return;
                    }
                    текущий[i("действ")]++;
                    выход(текущий, String.valueOf(ev.get("action")), ev.get("telemetry"));
                }
                case "objective" -> {
                    текущий[i("спец")]++;
                    наградныеДействия(текущий, ev.get("granted"));
                }
                case "spec_combat", "ability_spec", "arsenal", "arsenal_spec_use" -> текущий[i("спец")]++;
                case "combat_hit" -> {
                    текущий[i("попад")]++;
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        текущий[i("снос")]++;
                    }
                }
                case "return" -> {
                    int r = ev.get("round") instanceof Number n ? n.intValue() : 0;
                    if (r < 1 || r > МАКС) {
                        return;
                    }
                    double[] acc = поРаундам.computeIfAbsent(r, k -> new double[КЛЮЧИ.length]);
                    for (int k = 0; k < текущий.length; k++) {
                        acc[k] += текущий[k];
                        текущий[k] = 0;
                    }
                    double макс = -1e9;
                    double мин = 1e9;
                    for (PlayerState p : s.players) {
                        снятьТабло(s, p, acc);
                        double по = Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0);
                        acc[i("ПО")] += по;
                        макс = Math.max(макс, по);
                        мин = Math.min(мин, по);
                    }
                    // отрыв — одно число на стол; умножаем на игроков, чтобы
                    // общее деление «на игрока» вернуло его как есть
                    acc[i("отрыв")] += (макс - мин) * s.players.size();
                    игроковВРаунде.merge(r, s.players.size(), Integer::sum);
                }
                default -> { }
            }
        });

        double[] очки = new double[игроков];
        int победитель = 0;
        for (int p = 0; p < игроков; p++) {
            очки[p] = Scoring.scorePlayer(s, p).getOrDefault("total", 0);
            if (очки[p] > очки[победитель]) {
                победитель = p;
            }
        }
        return new Итог(поРаундам, игроковВРаунде, стороны.toArray(new String[0]), очки,
            победитель, s.round);
    }

    /** Что дало действие: Снаряжение — боеприпасы и войска, Добыча — келемий. */
    private static void выход(double[] acc, String действие, Object телеметрия) {
        if (!(телеметрия instanceof Map<?, ?> т)) {
            return;
        }
        if ("assembly".equals(действие)) {
            acc[i("БПР+")] += чис(т.get("ammo"));
            acc[i("войск+")] += чис(т.get("units"));
        } else if ("mining".equals(действие)) {
            acc[i("келем+")] += чис(т.get("kelium"));
        }
    }

    /** Действия, полученные наградой задания (где бы в награде они ни лежали). */
    private static void наградныеДействия(double[] acc, Object узел) {
        if (!(узел instanceof Map<?, ?> m)) {
            return;
        }
        if (m.get("action") != null && !Boolean.FALSE.equals(m.get("action_ran"))) {
            acc[i("награда")]++;
            выход(acc, String.valueOf(m.get("action")), m.get("action_telemetry"));
        }
        for (Object v : m.values()) {
            if (v instanceof Map<?, ?>) {
                наградныеДействия(acc, v);
            }
        }
    }

    private static void снятьТабло(GameState s, PlayerState p, double[] acc) {
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.MINER) {
                acc[i("шахт")]++;
            } else if (b.type == BuildingType.POWER_PLANT) {
                acc[i("станц")]++;
            } else if (b.type != BuildingType.COMMAND_CENTER) {
                acc[i("воен")]++;
            }
        }
        long глухих = p.redPlacements.values().stream()
            .filter(м -> Boolean.TRUE.equals(м.get("blocks"))).count();
        acc[i("модул")] += p.redPlacements.size() - глухих + p.bluePlacements.size();
        for (String трек : s.tech.tracks) {
            for (int шаг = 0; шаг < s.tech.steps; шаг++) {
                if (s.tech.occupancy.get(трек).get(шаг).contains(p.seat)) {
                    acc[i("шагов")]++;
                }
            }
        }
        acc[i("арсен")] += p.arsenalInstalled.size();
        acc[i("задан")] += p.objectivesCompleted;
        acc[i("войск")] += p.unitsOnField().size();
        acc[i("монет")] += p.resources.coin();
        acc[i("БПР")] += p.resources.ammo();
        acc[i("келем")] += p.resources.kelium();
    }

    private static void напечатать(PrintStream out, List<Итог> итоги, int игроков) {
        Map<Integer, double[]> сумма = new LinkedHashMap<>();
        Map<Integer, Integer> мест = new LinkedHashMap<>();
        Map<Integer, Integer> партийВРаунде = new LinkedHashMap<>();
        double раундов = 0;
        for (Итог и : итоги) {
            раундов += и.раундов();
            for (var e : и.поРаундам().entrySet()) {
                double[] acc = сумма.computeIfAbsent(e.getKey(), k -> new double[КЛЮЧИ.length]);
                for (int k = 0; k < acc.length; k++) {
                    acc[k] += e.getValue()[k];
                }
                партийВРаунде.merge(e.getKey(), 1, Integer::sum);
            }
            и.игроковВРаунде().forEach((r, n) -> мест.merge(r, n, Integer::sum));
        }
        out.printf("раундов в среднем %.1f%n%n", раундов / итоги.size());
        out.print(" р  партий");
        for (String к : КЛЮЧИ) {
            out.printf(" %6s", к);
        }
        out.println();
        for (int r = 1; r <= МАКС; r++) {
            double[] acc = сумма.get(r);
            if (acc == null) {
                continue;
            }
            out.printf("%2d  %5d ", r, партийВРаунде.get(r));
            for (int k = 0; k < acc.length; k++) {
                out.printf(" %6.2f", acc[k] / мест.get(r));
            }
            out.println();
        }

        out.println();
        out.println("ФРАКЦИИ (цвета крутятся по местам): средние ПО и доля побед");
        for (String цвет : ЦВЕТА) {
            double очки = 0;
            int раз = 0;
            int побед = 0;
            for (Итог и : итоги) {
                for (int p = 0; p < игроков; p++) {
                    if (цвет.equals(и.цвет()[p])) {
                        очки += и.очки()[p];
                        раз++;
                        if (и.победитель() == p) {
                            побед++;
                        }
                    }
                }
            }
            out.printf("  %-7s %5.2f ПО · побед %4.1f%%  (%d партий)%n", цвет,
                раз == 0 ? 0 : очки / раз, раз == 0 ? 0 : 100.0 * побед / раз, раз);
        }
    }

    private static int i(String ключ) {
        for (int k = 0; k < КЛЮЧИ.length; k++) {
            if (КЛЮЧИ[k].equals(ключ)) {
                return k;
            }
        }
        throw new IllegalArgumentException(ключ);
    }

    private static double чис(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
