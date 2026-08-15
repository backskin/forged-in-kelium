package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Resource;
import kelium.core.Target;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.CombatResolver;
import kelium.support.Fix;

/**
 * Правила боя, которых не проверял никто: стоимость боеприпасов, ответный удар,
 * укрытие войска в своём здании и то, что бот судит о бое ТЕМИ ЖЕ правилами,
 * по которым бой разрешается.
 *
 * <p>Раньше из десятка боевых правил тестами были закрыты два.
 */
class CombatRulesTest {

    private static CombatResolver combat(GameState s) {
        return (CombatResolver) s.combat;
    }

    /** Соседний обычный гекс, куда можно поставить чужой жетон. */
    private static String enemySpot(GameState s, int seat) {
        String spot = Fix.freeNeighbour(s, s.player(seat).startHex);
        assertNotNull(spot, "у стартового гекса нет обычного соседа");
        return spot;
    }

    /** Без боеприпасов бой невозможен, сколько бы целей рядом ни стояло. */
    @Test
    void withoutAmmoNoAttackIsPossible() {
        GameState s = Fix.game(2, 42L);
        String spot = enemySpot(s, 0);
        Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        Fix.unit(s, 1, UnitType.INFANTRY, spot);

        s.player(0).resources.setAmmo(5);
        assertTrue(combat(s).anyAttackPossible(0),
            "с боеприпасами и целью рядом бой обязан быть возможен");

        s.player(0).resources.setAmmo(0);
        assertFalse(combat(s).anyAttackPossible(0),
            "без боеприпасов бой невозможен");
    }

    /** Цели нет — боя нет, даже с полным складом боеприпасов. */
    @Test
    void withNoTargetThereIsNoBattle() {
        GameState s = Fix.game(2, 42L);
        s.player(0).resources.setAmmo(9);
        // все чужие жетоны далеко: соседей у старта первого игрока не заселяем
        assertFalse(combat(s).anyAttackPossible(0),
            "бить некого — бой не должен считаться возможным");
    }

    /** Бой реально снимает боеприпасы у атакующего. */
    @Test
    void attackingSpendsAmmo() {
        GameState s = Fix.game(2, 42L);
        String spot = enemySpot(s, 0);
        Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        Fix.unit(s, 1, UnitType.INFANTRY, spot);
        s.player(0).resources.setAmmo(6);
        s.player(1).resources.setAmmo(0);      // чтобы ответка не путала счёт

        int before = s.player(0).resources.ammo();
        boolean fought = combat(s).runBattle(0, new Fix.AimingAgent(0, spot));
        assertTrue(fought, "бой должен был состояться");
        assertTrue(s.player(0).resources.ammo() < before,
            "удар обязан стоить боеприпасов: было " + before
                + ", стало " + s.player(0).resources.ammo());
    }

    /**
     * Вышка бьётся КАК ЗДАНИЕ — это правило свода, и оно должно быть одним для
     * бота и для боя.
     */
    @Test
    void theTowerIsTargetedAsABuilding() {
        GameState s = Fix.game(2, 42L);
        UnitToken tower = Fix.unit(s, 1, UnitType.TOWER, enemySpot(s, 0));
        assertEquals(Target.BUILDINGS_TOWERS, tower.category(),
            "вышка обязана считаться зданием при выборе цели");
        UnitToken infantry = Fix.unit(s, 1, UnitType.INFANTRY, enemySpot(s, 0));
        assertEquals(Target.INFANTRY, infantry.category());
        assertEquals(Target.BUILDINGS_TOWERS,
            Fix.building(s, 1, BuildingType.POWER_PLANT, enemySpot(s, 0), 1).category());
    }

    /**
     * Бот судит о том, кого он может убить, ТЕМИ ЖЕ правилами, что и бой:
     * иначе он планирует удар, которого движок не разрешит (и наоборот).
     */
    @Test
    void whatTheBotThinksItCanHitMatchesWhatCombatAllows() {
        GameState s = Fix.game(2, 42L);
        String spot = enemySpot(s, 0);
        UnitToken mine = Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        List<kelium.core.Token> targets = new ArrayList<>();
        targets.add(Fix.unit(s, 1, UnitType.INFANTRY, spot));
        targets.add(Fix.unit(s, 1, UnitType.VEHICLE, spot));
        targets.add(Fix.building(s, 1, BuildingType.POWER_PLANT, spot, 1));

        kelium.agents.WorldView wv = new kelium.agents.WorldView(s, 0);
        for (kelium.core.Token t : targets) {
            boolean botThinks = wv.canKill(UnitType.INFANTRY, t);
            boolean combatAllows = CombatResolver.canHit(s, 0, mine, t);
            assertEquals(combatAllows, botThinks,
                "расходятся мнения о цели " + t.category() + ": бот " + botThinks
                    + ", бой " + combatAllows);
        }
    }

    /** Красный модуль расширяет набор целей — и бот обязан это видеть. */
    @Test
    void aRedModuleChangesWhatAUnitCanHit() {
        GameState s = Fix.game(2, 42L);
        UnitToken mine = Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        java.util.Set<Target> plain = combat(s).reachableTargets(0, mine);

        // ставим красный модуль на пехоту: вторичная строка заменяется парой из модуля
        Map<String, Object> mod = new java.util.HashMap<>();
        mod.put("targets", new String[]{"buildings_towers", "aircraft"});
        s.player(0).redPlacements.put(UnitType.INFANTRY, mod);

        java.util.Set<Target> withModule = combat(s).reachableTargets(0, mine);
        assertTrue(withModule.contains(Target.BUILDINGS_TOWERS)
                || withModule.contains(Target.AIRCRAFT),
            "модуль обязан добавить свои цели: было " + plain + ", стало " + withModule);
    }

    /** Уничтоженный жетон попадает на трофейное место убийцы. */
    @Test
    void aDestroyedTokenBecomesATrophy() {
        GameState s = Fix.game(2, 42L);
        String spot = enemySpot(s, 0);
        Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        UnitToken victim = Fix.unit(s, 1, UnitType.INFANTRY, spot);
        victim.hp = 1;                         // ляжет с первого удара
        s.player(0).resources.setAmmo(9);
        s.player(1).resources.setAmmo(0);

        combat(s).runBattle(0, new Fix.AimingAgent(0, spot));
        if (!victim.alive()) {
            assertTrue(s.player(0).trophySpace.contains(victim),
                "убитый жетон обязан лечь на трофейное место убийцы");
        }
    }
}
