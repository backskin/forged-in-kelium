package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Resource;
import kelium.core.UnitType;
import kelium.dataio.Ctx;
import kelium.engine.Predicates;
import kelium.support.Fix;

/**
 * Предикаты условий заданий: их 74, и на них не было ни одного теста.
 *
 * <p>Проверяется не баланс, а то, без чего механика заданий ломается тихо:
 * ни один предикат не падает на живом состоянии, на пустом старте почти
 * никто не «выполнен», а те, что зависят от фактов хода и от имущества,
 * реагируют на их появление.
 */
class PredicatesTest {

    private static boolean check(GameState s, String pid, Map<String, Object> params) {
        return Predicates.check(pid, s, 0, s.journal, params);
    }

    /** Ни один предикат не бросает исключение на нормальном состоянии партии. */
    @Test
    void noPredicateBlowsUpOnALiveGame() {
        GameState s = Fix.game();
        Fix.turn(s, 0);
        List<String> broken = new ArrayList<>();
        for (String pid : Predicates.allIds()) {
            try {
                Predicates.check(pid, s, 0, s.journal, defaults());
            } catch (Predicates.PredicateError e) {
                broken.add(pid + " — " + e.getMessage());
            } catch (RuntimeException e) {
                broken.add(pid + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        assertTrue(broken.isEmpty(), "предикаты падают на живой партии: " + broken);
    }

    /**
     * На СТАРТЕ партии почти ничего не выполнено — иначе задания закрывались бы
     * сами собой, ничего не требуя от игрока.
     */
    @Test
    void almostNothingIsSatisfiedAtTheVeryStart() {
        GameState s = Fix.game();
        Fix.turn(s, 0);
        List<String> satisfied = new ArrayList<>();
        for (String pid : Predicates.allIds()) {
            try {
                if (Predicates.check(pid, s, 0, s.journal, defaults())) {
                    satisfied.add(pid);
                }
            } catch (RuntimeException ignored) {
                // падения ловит соседний тест
            }
        }
        assertTrue(satisfied.size() * 3 < Predicates.allIds().size(),
            "на старте выполнено подозрительно много условий (" + satisfied.size()
                + " из " + Predicates.allIds().size() + "): " + satisfied);
    }

    /** Условие «есть N келемия» реагирует на сам келемий, а не на что попало. */
    @Test
    void resourceConditionsFollowTheResource() {
        GameState s = Fix.game();
        Fix.turn(s, 0);
        String pid = pick("kelium");
        if (pid == null) {
            return;                        // в этой версии такого условия нет
        }
        Map<String, Object> p = new HashMap<>(defaults());
        p.put("count", 3);
        s.player(0).resources.setKelium(0);
        boolean poor = check(s, pid, p);
        s.player(0).resources.setKelium(9);
        boolean rich = check(s, pid, p);
        assertTrue(rich || !poor,
            "условие про келемий не должно выполняться при нуле и не выполняться при девяти");
    }

    /** Условия про постройки реагируют на появление здания на поле. */
    @Test
    void buildingConditionsSeeNewBuildings() {
        GameState s = Fix.game();
        Fix.turn(s, 0);
        String spot = Fix.freeNeighbour(s, s.player(0).startHex);
        Map<String, Object> p = new HashMap<>(defaults());
        p.put("count", 1);

        List<String> before = satisfiedSet(s, p);
        Fix.power(Fix.building(s, 0, BuildingType.BARRACKS, spot, null));
        Fix.power(Fix.building(s, 0, BuildingType.MINER, spot, 1));
        List<String> after = satisfiedSet(s, p);

        after.removeAll(before);
        assertFalse(after.isEmpty(),
            "постройка двух зданий не изменила НИ ОДНОГО условия — подозрительно");
    }

    /** Условия про войска реагируют на появление жетонов на поле. */
    @Test
    void unitConditionsSeeNewUnits() {
        GameState s = Fix.game();
        Fix.turn(s, 0);
        String spot = Fix.freeNeighbour(s, s.player(0).startHex);
        Map<String, Object> p = new HashMap<>(defaults());
        p.put("count", 2);

        List<String> before = satisfiedSet(s, p);
        Fix.unit(s, 0, UnitType.INFANTRY, spot);
        Fix.unit(s, 0, UnitType.VEHICLE, spot);
        Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        List<String> after = satisfiedSet(s, p);

        after.removeAll(before);
        assertFalse(after.isEmpty(),
            "три выставленных жетона не изменили НИ ОДНОГО условия — подозрительно");
    }

    /** Все предикаты, названные в картах заданий этой версии, реально работают. */
    @Test
    void everyPredicateUsedByCardsRuns() {
        GameState s = Fix.game();
        Fix.turn(s, 0);
        List<String> broken = new ArrayList<>();
        for (Map<String, Object> card : Ctx.cards(s, "objectives").entries) {
            for (String pid : Predicates.allIds()) {
                if (!card.toString().contains(pid)) {
                    continue;
                }
                try {
                    Predicates.check(pid, s, 0, s.journal, defaults());
                } catch (RuntimeException e) {
                    broken.add(card.get("id") + " / " + pid + " — " + e.getMessage());
                }
            }
        }
        assertTrue(broken.isEmpty(), "условия карт заданий не работают: " + broken);
    }

    /** Первый предикат, в имени которого встречается это слово (или null). */
    private static String pick(String word) {
        for (String pid : Predicates.allIds()) {
            if (pid.contains(word)) {
                return pid;
            }
        }
        return null;
    }

    private static List<String> satisfiedSet(GameState s, Map<String, Object> params) {
        List<String> out = new ArrayList<>();
        for (String pid : Predicates.allIds()) {
            try {
                if (Predicates.check(pid, s, 0, s.journal, params)) {
                    out.add(pid);
                }
            } catch (RuntimeException ignored) {
                // сюда попадают предикаты, которым нужны другие параметры
            }
        }
        return out;
    }

    /** Параметры «по умолчанию»: предикаты читают из карты count/kind/track. */
    private static Map<String, Object> defaults() {
        Map<String, Object> p = new HashMap<>();
        p.put("count", 1);
        p.put("amount", 1);
        p.put("track", "left");
        p.put("kind", "infantry");
        p.put("type", "barracks");
        p.put("resource", Resource.KELIUM.code);
        return p;
    }
}
