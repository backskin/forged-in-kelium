package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.Target;
import kelium.core.UnitType;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.CombatResolver;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.rules.Ruleset;
import kelium.support.Fix;

/**
 * ДВЕ АТАКИ, ТРЕТЬЕЙ НЕТ (диктовка дизайнера 24.08.2026).
 *
 * <p>У каждого рода войск ровно две атаки: УНИВЕРСАЛЬНАЯ за 2 боеприпаса по
 * любому типу и СПЕЦИАЛЬНАЯ за 1 боеприпас по одному напечатанному типу.
 * Основной и второстепенной атак больше не существует — прежний сторож
 * {@code SecondaryAttackAmmoTest} стерёг именно их и потому снят.
 *
 * <p>ЧТО ИМЕННО СТЕРЕЖЁТСЯ. Цена живёт в двух местах — в своде и в разборе
 * строк резолвера, — и расходятся они молча. Поэтому проверяется и число в
 * своде, и то, что бой списывает ровно его. Плюс распределение спец-целей
 * стороны А: перестановка без неподвижных точек, где все четыре цели покрыты
 * по разу; ошибка здесь означает, что какой-то тип нельзя ударить дёшево вовсе.
 */
class ДвеАтакиTest {

    /** В своде: универсальная 2 БПР, специальная 1 БПР. */
    @Test
    void сводДержитЦеныДвухАтак() {
        GameState s = Fix.game();
        Ruleset rs = Ctx.rules(s);
        assertEquals(2, rs.getInt("actions.combat.universal_ammo_cost"),
            "универсальная атака — 2 боеприпаса за 1 урон по любому типу");
        assertEquals(1, rs.getInt("actions.combat.specialized_ammo_cost", -1),
            "специальная атака — 1 боеприпас");
        assertTrue(!rs.getBool("actions.combat.tower_specialized_free", false),
            "поблажки вышке нет: её специальная атака стоит те же 1 БПР");
    }

    /** Спец-цели стороны А — по диктовке, и это перестановка без своих типов. */
    @Test
    void спецЦелиСтороныАПоДиктовке() {
        GameState s = Fix.game();
        var side = s.player(0).board.troop;
        assertEquals(Target.AIRCRAFT, side.specializedTarget(UnitType.INFANTRY),
            "пехота бьёт авиацию");
        assertEquals(Target.BUILDINGS_TOWERS, side.specializedTarget(UnitType.VEHICLE),
            "техника бьёт здания и вышки");
        assertEquals(Target.VEHICLE, side.specializedTarget(UnitType.AIRCRAFT),
            "авиация бьёт технику");
        assertEquals(Target.INFANTRY, side.specializedTarget(UnitType.TOWER),
            "вышка бьёт пехоту");
    }

    /** У любого рода войск ДОСТИЖИМЫ все четыре цели: универсальная бьёт всех. */
    @Test
    void универсальнаяАтакаДостаётЛюбойТип() {
        GameState s = battleGame();
        bindWatching(s, new ArrayList<>());   // без привязки боя s.combat пуст
        for (UnitType t : List.of(UnitType.INFANTRY, UnitType.VEHICLE,
                UnitType.AIRCRAFT, UnitType.TOWER)) {
            var unit = Fix.unit(s, 0, t, s.player(0).startHex);
            var достижимо = ((CombatResolver) s.combat).reachableTargets(0, unit);
            assertEquals(4, достижимо.size(),
                "родом " + t.code + " обязаны достигаться все четыре цели, а вышло: "
                    + достижимо);
        }
    }

    /**
     * Удар СПЕЦИАЛЬНОЙ атакой списывает 1 боеприпас.
     *
     * <p>СЦЕНА: у пехоты РОВНО ОДИН боеприпас, а рядом стоит авиация — её
     * спец-цель. Универсальная атака стоит 2 и потому недоступна, значит выбора
     * нет и удар обязан пройти специальной. Так проверяется именно цена, а не
     * то, какой вариант агент возьмёт первым: универсальные строки идут в
     * списке раньше, и «первый вариант» — это всегда дорогая атака.
     */
    @Test
    void спецАтакаСтоитОдинБоеприпас() {
        GameState s = battleGame();
        int seat = 0;
        String spot = Fix.freeNeighbour(s, s.player(seat).startHex);
        assertTrue(spot != null, "у стартового гекса нет обычного соседа");

        Fix.unit(s, seat, UnitType.INFANTRY, s.player(seat).startHex);
        Fix.unit(s, 1, UnitType.AIRCRAFT, spot);
        s.player(seat).resources.setAmmo(1);
        s.player(1).resources.setAmmo(0);          // ответка не должна путать счёт

        List<Map<String, Object>> hits = new ArrayList<>();
        bindWatching(s, hits);

        int before = s.player(seat).resources.ammo();
        boolean fought = ((CombatResolver) s.combat)
            .runBattle(seat, new Fix.AimingAgent(seat, spot));
        assertTrue(fought, "бой должен был состояться");

        List<Map<String, Object>> спец = new ArrayList<>();
        for (Map<String, Object> h : hits) {
            if (String.valueOf(h.get("attacker")).endsWith(".specialized")) {
                спец.add(h);
            }
        }
        assertTrue(!спец.isEmpty(),
            "по авиации пехота обязана бить СПЕЦИАЛЬНОЙ атакой, а таких ударов "
            + "не случилось: " + hits);
        for (Map<String, Object> h : спец) {
            assertEquals(1, ((Number) h.get("base_ammo")).intValue(),
                "печатная цена специальной атаки — 1 БПР, а в бою: " + h);
        }
        assertEquals(before - 1, s.player(seat).resources.ammo(),
            "с одним боеприпасом возможен ровно один удар, и стоит он 1 БПР");
    }

    /** Партия с наблюдателем боевых событий (Fix.game привязывает бой без него). */
    private static GameState battleGame() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 2, 42L, null, null));
    }

    private static void bindWatching(GameState s, List<Map<String, Object>> hits) {
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            agents.add(new Fix.FirstChoiceAgent(seat));
        }
        GameEngine.bind(s, agents, ev -> {
            if ("combat_hit".equals(String.valueOf(ev.get("type")))) {
                hits.add(new java.util.HashMap<>(ev));
            }
        });
        s.round = 1;
        s.circle = 1;
    }
}
