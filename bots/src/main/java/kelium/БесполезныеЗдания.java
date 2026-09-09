package kelium;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.GameEngine;

/**
 * БЕСПОЛЕЗНЫЕ ВОЕННЫЕ ЗДАНИЯ — сколько заводов, казарм и авиабаз стоят там, где
 * их жетон уже не поставить.
 *
 * <p>Замечание дизайнера 08.09.2026 по записи партии: «почему он ставит завод
 * таким образом, что невозможно поставить технику?» Технике нужны две смежные
 * свободные ячейки гекса; завод, вставший не туда, не выпустит ни одного жетона
 * до конца партии — деньги потрачены, толку нет.
 *
 * <p>С правилом 09.09.2026 («здание производит всегда — если не на гекс, то на
 * себя») такое здание уже не бесполезно: жетон садится ГАРНИЗОНОМ внутрь. Но
 * гарнизон не стреляет, и застрявший там жетон — тоже потеря. Поэтому стенд
 * считает две вещи: тесные здания (жетону некуда встать на гексе) и сколько
 * жетонов в конце партии сидят внутри зданий.
 *
 * <p>Мерить надо ДО и ПОСЛЕ правки: цифра сама по себе ничего не значит, а
 * разница до и после — значит.
 *
 * <p>Запуск: {@code kelium.БесполезныеЗдания [партий] [игроков] [уровень ботов]}
 */
public final class БесполезныеЗдания {

    private БесполезныеЗдания() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int уровень = args.length > 2 ? Integer.parseInt(args[2]) : 2;

        Map<String, int[]> счёт = new LinkedHashMap<>();   // код здания -> [всего, тесных]
        int вГарнизоне = 0;
        int наПоле = 0;
        Random rng = new Random(4242);
        for (int i = 0; i < партий; i++) {
            long seed = rng.nextLong();
            GameConfig cfg = GameConfig.build(GameConfig.DEFAULT_RULESET, игроков, seed, null, null);
            GameState s = kelium.engine.Setup.buildGame(cfg);
            List<Agent> боты = new ArrayList<>();
            List<String> роли = Bots.ROSTER_4;
            for (int seat = 0; seat < игроков; seat++) {
                боты.add(Bots.create(роли.get(seat % роли.size()), Bots.Level.of(уровень),
                    seat, new Random(seed * 31 + seat), игроков));
            }
            new GameEngine(s, боты, ev -> { }).run();
            for (PlayerState p : s.players) {
                for (kelium.core.UnitToken u : p.unitsOnField()) {
                    наПоле++;
                    if (u.inside()) {
                        вГарнизоне++;
                    }
                }
                for (BuildingToken b : p.buildingsOnField()) {
                    UnitType род = Actions.ASSEMBLY_UNIT.get(b.type);
                    if (род == null || b.type == BuildingType.COMMAND_CENTER) {
                        continue;          // ЦУ делает вышки в любой гекс зоны
                    }
                    int[] c = счёт.computeIfAbsent(b.type.code, k -> new int[2]);
                    c[0]++;
                    if (!Actions.roomForBuildingAndUnit(s, b.hexId, 0, род)) {
                        c[1]++;
                    }
                }
            }
        }
        System.out.println("свод " + GameConfig.DEFAULT_RULESET + ", партий " + партий
            + ", игроков " + игроков + ", боты уровня " + уровень);
        int всего = 0;
        int плохих = 0;
        for (var e : счёт.entrySet()) {
            int[] c = e.getValue();
            всего += c[0];
            плохих += c[1];
            System.out.printf("  %-14s всего %3d, некуда ставить жетон %3d  (%.1f%%)%n",
                e.getKey(), c[0], c[1], 100.0 * c[1] / Math.max(1, c[0]));
        }
        System.out.printf("ИТОГО тесных зданий: %d из %d (%.1f%%)%n",
            плохих, всего, 100.0 * плохих / Math.max(1, всего));
        System.out.printf("ЖЕТОНОВ В ГАРНИЗОНЕ в конце партии: %d из %d на поле (%.1f%%)%n",
            вГарнизоне, наПоле, 100.0 * вГарнизоне / Math.max(1, наПоле));
    }
}
