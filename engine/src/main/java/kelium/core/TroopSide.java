package kelium.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Боевая сторона доски: скорости, цены построек и таблица атак юнитов.
 *
 * <p>Обёртка над сырым словарём контента доски (сторона A или Б1–Б4). Меняется
 * только доска — характеристики жетонов не меняются никогда.
 */
public final class TroopSide {

    public final String side;
    public final Map<String, Object> raw;

    public TroopSide(String side, Map<String, Object> raw) {
        this.side = side;
        this.raw = raw;
    }

    /** Отображаемое имя стороны (или её код, если имя не задано). */
    public String name() {
        Object n = raw.get("name");
        return n != null ? n.toString() : side;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> speedsMap() {
        return (Map<String, Object>) raw.get("speeds");
    }

    /** Скорость движения указанного типа юнита на этой стороне. */
    public int speed(UnitType unit) {
        Object v = speedsMap().get(unit.code);
        return ((Number) v).intValue();
    }

    /** Все скорости движения по типам юнитов. */
    public Map<UnitType, Integer> speeds() {
        Map<UnitType, Integer> out = new EnumMap<>(UnitType.class);
        for (Map.Entry<String, Object> e : speedsMap().entrySet()) {
            out.put(UnitType.fromCode(e.getKey()), ((Number) e.getValue()).intValue());
        }
        return out;
    }

    /** Цена стройки указанного здания (ключ barracks/factory/airbase) на этой стороне. */
    @SuppressWarnings("unchecked")
    public int buildingPrice(String building) {
        Map<String, Object> prices = (Map<String, Object>) raw.get("building_prices");
        return ((Number) prices.get(building)).intValue();
    }

    /**
     * БОЙ 2.0 (заказ дизайнера 18.08.2026): у стороны вместо печатной пары
     * целей — универсальная ячейка (любая цель, задаётся ценой ruleset'а, не
     * этим файлом) плюс одна специализированная печатная цель на род войск.
     * Флаг живёт на самой стороне, чтобы старые доски (boards.1.0.x) работали
     * ровно как раньше — см. {@link #attacks} и {@code CombatResolver}.
     */
    public boolean dualCell() {
        return Boolean.TRUE.equals(raw.get("dual_cell"));
    }

    /**
     * ОДНА печатная цель специализированной ячейки (только для {@link
     * #dualCell()} сторон — {@code attacks:} там хранит скаляр, не пару).
     */
    @SuppressWarnings("unchecked")
    public Target specializedTarget(UnitType unit) {
        Map<String, Object> tbl = (Map<String, Object>) raw.get("attacks");
        if (tbl == null) {
            return null;
        }
        Object row = tbl.get(unit.code);
        return row == null ? null : Target.fromCode(row.toString());
    }

    /** Цели (основная, вторичная) для юнита, если полная таблица атак задана; иначе null. */
    @SuppressWarnings("unchecked")
    public Target[] attacks(UnitType unit) {
        Object tblObj = raw.get("attacks");
        if (tblObj == null) {
            return null;
        }
        Map<String, Object> tbl = (Map<String, Object>) tblObj;
        Object row = tbl.get(unit.code);
        if (row == null) {
            return null;
        }
        List<Object> pair = (List<Object>) row;
        return new Target[]{
            Target.fromCode(pair.get(0).toString()),
            Target.fromCode(pair.get(1).toString())
        };
    }

    /** Могут ли вышки двигаться на этой стороне (по флагу или по скорости). */
    public boolean towersMove() {
        Object flag = raw.get("towers_move");
        if (flag != null) {
            return Boolean.TRUE.equals(flag);
        }
        return speed(UnitType.TOWER) > 0;
    }
}
