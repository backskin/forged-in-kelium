package kelium.agents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Target;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * WorldView — картина ОТКРЫТОЙ информации с точки зрения одного игрока (seat).
 *
 * <p>Бот видит ВСЁ поле (юниты/здания/ресурсы соперников на столе, треки, тайлы,
 * состояние грядок), но НЕ видит чужие руки приказов и закрытые карты — их тут
 * попросту нет. Объект строится один раз на точку решения и кэширует дорогие
 * вычисления (множества гексов, BFS-дистанции).
 *
 * <p>Ключевая способность — {@link #canKill}: бот сверяется с СОБСТВЕННОЙ
 * таблицей атак (troop.attacks) и понимает, какие вражеские жетоны он вообще
 * способен уничтожить. На этом строится осмысленное движение (идти к цели,
 * которую реально можно убить) и приоритет боя.
 */
public final class WorldView {

    public final GameState state;
    public final int seat;
    public final PlayerState me;

    /** Гексы, где стоят МОИ токены. */
    public final Set<String> myHexes = new HashSet<>();
    /** Гексы, где стоят ЧУЖИЕ токены. */
    public final Set<String> enemyHexes = new HashSet<>();
    /** Все вражеские жетоны на поле (юниты и здания). */
    public final List<Token> enemyTokens = new ArrayList<>();

    public WorldView(GameState state, int seat) {
        this.state = state;
        this.seat = seat;
        this.me = state.player(seat);
        for (PlayerState pl : state.players) {
            boolean mine = pl.seat == seat;
            for (UnitToken u : pl.unitsOnField()) {
                (mine ? myHexes : enemyHexes).add(u.hexId);
                // ВОЙСКО ВНУТРИ ЗДАНИЯ в список целей НЕ попадает: пока здание
                // живо, его не достать, и бот, целящийся в такой жетон, просто
                // сжигает боеприпасы. Само здание в целях остаётся — снести его и
                // есть правильный путь к спрятанному войску.
                if (!mine && !u.inside()) {
                    enemyTokens.add(u);
                }
            }
            for (BuildingToken b : pl.buildingsOnField()) {
                (mine ? myHexes : enemyHexes).add(b.hexId);
                if (!mine) {
                    enemyTokens.add(b);
                }
            }
        }
    }

    /** Категория цели — свойство самого жетона ({@link Token#category()}). */
    public static Target targetCategory(Token token) {
        return token.category();
    }

    /**
     * Может ли мой жетон этого рода в принципе поразить вражеский жетон.
     *
     * <p>Спрашиваем НАСТОЯЩИЙ бой, а не печатную таблицу планшета: красный
     * модуль меняет вторичную строку атаки, а супер-войско бьёт вообще всех.
     * Раньше бот этого не видел и не понимал, что модуль дал ему право бить,
     * например, ЦУ.
     */
    public boolean canKill(UnitType myUnit, Token enemy) {
        for (UnitToken u : me.unitsOnField()) {
            if (u.type == myUnit) {
                return kelium.engine.CombatResolver.canHit(state, me.seat, u, enemy);
            }
        }
        // такого рода на поле сейчас нет — судим по печатной таблице
        Target[] pair = me.board.troop.attacks(myUnit);
        if (pair == null) {
            return false;
        }
        for (Target t : pair) {
            if (t == enemy.category()) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли у меня хоть какой-то юнит на поле, способный убить этот жетон. */
    public boolean anyUnitCanKill(Token enemy) {
        for (UnitToken u : me.unitsOnField()) {
            if (canKill(u.type, enemy)) {
                return true;
            }
        }
        return false;
    }

    /** Хватает ли боеприпасов на атаку (минимум 1 — грубая оценка для планирования). */
    public boolean haveAmmoForAttack() {
        return me.resources.ammo() >= 1;
    }

    /**
     * Расстояние от гекса до ближайшего из {@code targets} — тем же поиском,
     * которым ходит движок ({@link kelium.engine.Movement}).
     *
     * <p>Раньше здесь был свой обход БЕЗ ПРАВИЛ: он не видел ни стенок, ни
     * тайлов зарождения, и бот планировал маршрут сквозь непроходимое.
     */
    public Integer bfsDistanceTo(String fromHex, Set<String> targets) {
        return kelium.engine.Movement.distance(state, fromHex, targets);
    }

    /** BFS-дистанция до ближайшего ЛЮБОГО вражеского токена. */
    public Integer distanceToNearestEnemy(String fromHex) {
        return bfsDistanceTo(fromHex, enemyHexes);
    }

    /**
     * Гексы вражеских жетонов, которые указанный юнит СПОСОБЕН убить (по таблице
     * атак). По ним строится целеустремлённое движение.
     */
    public Set<String> killableEnemyHexes(UnitType myUnit) {
        Set<String> out = new HashSet<>();
        for (Token t : enemyTokens) {
            if (canKill(myUnit, t)) {
                out.add(t.hexId());
            }
        }
        return out;
    }

    /**
     * Число вражеских жетонов в «досягаемости удара»: стоят на соседнем гексе с
     * каким-либо моим юнитом, который может их убить. Признак для оценочной
     * функции (чем больше — тем ближе размен в мою пользу).
     */
    public int killableTargetsInStrikeRange() {
        int count = 0;
        for (Token t : enemyTokens) {
            String eh = t.hexId();
            boolean adjacentToKiller = false;
            for (UnitToken u : me.unitsOnField()) {
                if (!canKill(u.type, t)) {
                    continue;
                }
                for (String nb : state.field.neighbors(u.hexId)) {
                    if (nb.equals(eh)) {
                        adjacentToKiller = true;
                        break;
                    }
                }
                if (adjacentToKiller) {
                    break;
                }
            }
            if (adjacentToKiller) {
                count++;
            }
        }
        return count;
    }

    /** Сколько моих юнитов стоят вплотную (d==1) к любому врагу. */
    public int myUnitsInStrikeRange() {
        int count = 0;
        for (UnitToken u : me.unitsOnField()) {
            boolean near = false;
            for (String nb : state.field.neighbors(u.hexId)) {
                if (enemyHexes.contains(nb)) {
                    near = true;
                    break;
                }
            }
            if (near) {
                count++;
            }
        }
        return count;
    }
}
