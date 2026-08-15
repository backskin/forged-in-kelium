package kelium.engine;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * ПРОХОДИМОСТЬ РЕБРА между двумя гексами — ОДНО правило на всю игру.
 *
 * <p>Зачем отдельный класс. Правило «что закрывает проход» жило внутри действия
 * Движение, а Бой о нём не знал вовсе и брал просто всех соседей гекса. Дизайнер
 * поймал это в проигрывателе: техника выстрелила в соседний гекс СКВОЗЬ
 * нейтральное здание, которое стояло в её же гексе и закрывало ровно эту сторону.
 * Пока правило было в одном месте, а стреляли из другого, такие расхождения были
 * неизбежны — поэтому здесь оно ровно одно, и им пользуются оба действия.
 *
 * <p>Правило (СВОД, §12.3, решение дизайнера): блокировка ПО-СТОРОННЯЯ, а не
 * по-гексовая. Сторону закрывает ЧУЖОЕ здание или НЕЙТРАЛЬНАЯ постройка; СВОИ
 * здания не мешают, войска не мешают никому (они переупаковываются внутри гекса).
 * Ребро открыто, если есть хотя бы одна пара смежных сторон, не закрытая ни с
 * одной стороны.
 *
 * <p>АВИАЦИЯ стенок не замечает — она летит над ними, и это касается и полёта, и
 * стрельбы: закрытая сторона авиации не мешает.
 */
public final class Passability {

    private Passability() {
    }

    /**
     * Открыто ли ребро между гексами для НАЗЕМНОГО войска игрока {@code seat}.
     *
     * @param from откуда (гекс войска); {@code null} — считаем открытым
     * @param to   куда (соседний гекс)
     */
    public static boolean groundEdgeOpen(GameState state, String from, String to, int seat) {
        if (from == null || to == null || !state.field.hexes.containsKey(from)
                || !state.field.hexes.containsKey(to)) {
            return true;
        }
        Hex fh = state.field.get(from);
        Hex th = state.field.get(to);
        for (int i : fh.sidesFacing(to)) {
            if (blocksGround(state, fh.sideOwner[i], seat)) {
                continue;               // стенка на исходном гексе закрывает ребро
            }
            boolean backBlocked = false;
            for (int j : th.sidesFacing(from)) {
                if (blocksGround(state, th.sideOwner[j], seat)) {
                    backBlocked = true;
                    break;
                }
            }
            if (!backBlocked) {
                return true;
            }
        }
        return false;
    }

    /**
     * Может ли КОНКРЕТНОЕ войско бить в соседний гекс — то есть достаёт ли оно до
     * него физически. Авиация достаёт всегда, наземные — только через открытое
     * ребро.
     */
    public static boolean canShootAcross(GameState state, UnitToken unit, String to) {
        if (unit.type == UnitType.AIRCRAFT) {
            return true;
        }
        // Ручка правил: закрывает ли стенка ВЫСТРЕЛ так же, как проход. По умолчанию
        // да — сквозь здание не стреляют. Ручка нужна не для красоты: перекрытие
        // выстрелов заметно снижает число боёв, а дизайнер как раз ищет, чем поднять
        // агрессию, и обе стороны надо мерить, а не обсуждать.
        if (!Boolean.TRUE.equals(kelium.dataio.Ctx.rules(state)
                .get("combat_model.walls_block_shots", Boolean.TRUE))) {
            return true;
        }
        return groundEdgeOpen(state, unit.hexId, to, unit.owner);
    }

    /**
     * Закрывает ли жетон, занимающий сторону гекса, проход наземному войску
     * игрока {@code seat}: чужое здание и нейтральная постройка — да, своё
     * здание и любые войска — нет.
     */
    public static boolean blocksGround(GameState state, Integer uid, int seat) {
        if (uid == null) {
            return false;
        }
        if (uid < 0) {
            return true;                      // нейтральное здание
        }
        for (PlayerState p : state.players) {
            for (BuildingToken b : p.buildings) {
                if (b.uid == uid) {
                    return p.seat != seat;    // чужое здание закрывает, своё — нет
                }
            }
        }
        return false;                         // войско: не стенка
    }
}
