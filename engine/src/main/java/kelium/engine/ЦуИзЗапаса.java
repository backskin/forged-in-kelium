package kelium.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;

/**
 * ЦУ ИЗ ЗАПАСА — НА ПОЛЕ СПЕЦ-ДЕЙСТВИЕМ (решения дизайнера 25.08, 14.09 и
 * 23.09.2026).
 *
 * <p>ЦУ в запасе обязан вернуться на поле: в свой ход игрок тратит на это
 * спец-действие и ставит ЦУ на свободные сектора любого гекса, где нет чужих
 * войск (даже с чужими зданиями) — зона стройки здесь не действует. Цены у
 * этой постановки нет.
 *
 * <p>Сюда же ведёт снос своего ЦУ Стройкой: снести его можно, только если в ходу
 * осталось спец-действие, и им ЦУ сразу ставится заново.
 */
public final class ЦуИзЗапаса {

    private ЦуИзЗапаса() {
    }

    /** Жетон ЦУ игрока в запасе, или null. */
    public static BuildingToken вЗапасе(PlayerState p) {
        for (BuildingToken b : p.buildings) {
            if (b.type == BuildingType.COMMAND_CENTER && b.hexId == null) {
                return b;
            }
        }
        return null;
    }

    /**
     * Гексы, где ЦУ помещается на свободные сектора: гекс без чужих войск
     * (решение дизайнера 23.09.2026 — «на любом гексе, где нет чужих войск,
     * даже на гексе с чужими зданиями»), свои войска на гексе умещаются.
     */
    public static List<String> места(GameState s, int seat) {
        int fp = Placement.footprint(BuildingType.COMMAND_CENTER);
        List<String> out = new ArrayList<>();
        for (Hex h : s.field.hexes.values()) {
            if (h.kind == HexKind.FORBIDDEN || h.spawnTile != null
                    || Passability.enemyUnitsOn(s, h.id, seat)) {
                continue;
            }
            int[] груз = Actions.groundLoad(s, h.id, -1);
            if (h.chooseFootprint(fp, груз[0], груз[1]) != null) {
                out.add(h.id);
            }
        }
        java.util.Collections.sort(out);     // порядок — ради повторяемости партии
        return out;
    }

    /**
     * Поставить ЦУ из запаса: игрок выбирает гекс и поворот. Спец-действие не
     * тратится здесь — это делает вызывающий.
     *
     * @return поставлен ли ЦУ
     */
    public static boolean поставить(GameState s, PlayerState p, Agent агент) {
        BuildingToken цу = вЗапасе(p);
        if (цу == null) {
            return false;
        }
        List<String> места = места(s, p.seat);
        if (места.isEmpty()) {
            return false;
        }
        String гекс = места.get(0);
        if (места.size() > 1) {
            List<Choice> варианты = new ArrayList<>();
            for (String id : места) {
                варианты.add(new Choice("cu_hex", id, "ЦУ на " + id));
            }
            гекс = String.valueOf(агент.choose(s, варианты, Map.of("kind", "cu_hex")).payload());
        }
        Hex h = s.field.get(гекс);
        int fp = Placement.footprint(BuildingType.COMMAND_CENTER);
        int[] груз = Actions.groundLoad(s, гекс, -1);
        List<List<Integer>> повороты = new ArrayList<>();
        for (int start = 0; start < 6; start++) {
            List<Integer> след = h.footprintAt(start, fp, груз[0], груз[1]);
            if (след != null) {
                повороты.add(след);
            }
        }
        if (повороты.isEmpty()) {
            return false;
        }
        List<Integer> стороны = повороты.get(0);
        if (повороты.size() > 1) {
            List<Choice> варианты = new ArrayList<>();
            for (List<Integer> след : повороты) {
                варианты.add(new Choice("cu_sides", след, "стенками " + след));
            }
            @SuppressWarnings("unchecked")
            List<Integer> выбор = (List<Integer>) агент.choose(s, варианты,
                Map.of("kind", "cu_sides", "hex", гекс)).payload();
            стороны = выбор;
        }
        java.util.Set<String> открытыДо = PrintedContainers.открытые(s);
        цу.hexId = гекс;
        h.occupySides(цу.uid, стороны);
        // ЦУ приходит со своими кубиками: один в его ячейку, остальные простаивают.
        int даёт = Power.sourceCubes(s, цу);
        int себе = Math.min(даёт, цу.energySlots);
        цу.energyBySource.clear();
        цу.energyPlaced = 0;
        цу.addEnergyFrom(цу.uid, себе);
        цу.energyIdle = даёт - себе;
        if (s.journal != null) {
            s.journal.of(p.seat).cuPlacedHexes.add(гекс);
        }
        PrintedContainers.накрытия(s, p, открытыДо);
        return true;
    }
}
