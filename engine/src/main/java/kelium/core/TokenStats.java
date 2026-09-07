package kelium.core;

import java.util.List;
import java.util.Map;

/**
 * Разобранные характеристики жетонов из контента досок с применённым бонусом HP.
 *
 * <p>Незыблемое ядро: сами виды жетонов и набор их полей зафиксированы. Числовые
 * характеристики (HP, ячейки энергии, добыча/выход, трофейная ценность) берутся
 * из версионируемого контента досок, а вариант «+1 HP всем» применяет
 * {@code token_hp_bonus_all} единообразно при создании — жетоны остаются
 * одинаковыми у всех игроков (железное правило асимметрии).
 */
public final class TokenStats {

    public final Map<String, Object> raw;
    public final int hpBonus;
    public final Map<String, Object> units;
    public final Map<String, Object> buildings;
    public final List<Object> miners;
    public final List<Object> powerPlants;
    public final int unitTokensPerColor;
    public final int buildingTokensPerColor;

    @SuppressWarnings("unchecked")
    private TokenStats(Map<String, Object> raw, int hpBonus) {
        this.raw = raw;
        this.hpBonus = hpBonus;
        this.units = (Map<String, Object>) raw.get("units");
        this.buildings = (Map<String, Object>) raw.get("buildings");
        this.miners = (List<Object>) raw.get("miners");
        this.powerPlants = (List<Object>) raw.get("power_plants");
        Object upc = raw.get("unit_tokens_per_color");
        this.unitTokensPerColor = upc != null ? ((Number) upc).intValue() : 16;
        Object bpc = raw.get("building_tokens_per_color");
        this.buildingTokensPerColor = bpc != null ? ((Number) bpc).intValue() : 12;
    }

    /** Собрать TokenStats из записи контента, задав общий бонус к HP. */
    public static TokenStats fromContent(Map<String, Object> tokensEntry, int hpBonus) {
        return new TokenStats(tokensEntry, hpBonus);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unitRaw(UnitType t) {
        return (Map<String, Object>) units.get(t.code);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildingRaw(BuildingType t) {
        return (Map<String, Object>) buildings.get(t.code);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> minerRaw(int level) {
        return (Map<String, Object>) miners.get(level - 1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> plantRaw(int level) {
        return (Map<String, Object>) powerPlants.get(level - 1);
    }

    private static int asInt(Object o) {
        return ((Number) o).intValue();
    }

    /** HP юнита указанного типа с учётом общего бонуса. */
    public int unitHp(UnitType t) {
        return asInt(unitRaw(t).get("hp")) + hpBonus;
    }

    /** HP здания указанного типа (для добытчика/энергостанции — по уровню) с бонусом. */
    public int buildingHp(BuildingType t, Integer level) {
        if (t == BuildingType.MINER) {
            return asInt(minerRaw(level).get("hp")) + hpBonus;
        }
        if (t == BuildingType.POWER_PLANT) {
            return asInt(plantRaw(level).get("hp")) + hpBonus;
        }
        return asInt(buildingRaw(t).get("hp")) + hpBonus;
    }

    /** Трофейная ценность по числу захваченных юнитов, напр. техника 1/1/2/2. */
    @SuppressWarnings("unchecked")
    public List<Integer> unitTrophyList(UnitType t) {
        Object v = unitRaw(t).get("trophy");
        if (v instanceof List<?> list) {
            return (List<Integer>) list;
        }
        return List.of(asInt(v));
    }

    /** Трофейная ценность здания указанного типа (для добытчика/энергостанции — по уровню). */
    public int buildingTrophy(BuildingType t, Integer level) {
        Object v;
        if (t == BuildingType.MINER) {
            v = minerRaw(level).get("trophy");
        } else if (t == BuildingType.POWER_PLANT) {
            v = plantRaw(level).get("trophy");
        } else {
            v = buildingRaw(t).get("trophy");
        }
        return v != null ? asInt(v) : 1;
    }

    /** Выработка келемия добытчика указанного уровня. */
    public int minerYield(int level) {
        return asInt(minerRaw(level).get("yield_kelium"));
    }

    /** Цена постройки добытчика указанного уровня. */
    public int minerCost(int level) {
        return asInt(minerRaw(level).get("cost"));
    }

    /** Число ячеек энергии добытчика указанного уровня. */
    public int minerEnergySlots(int level) {
        return asInt(minerRaw(level).get("energy_slots"));
    }

    /** Цена постройки энергостанции указанного уровня. */
    public int plantCost(int level) {
        return asInt(plantRaw(level).get("cost"));
    }

    /** Сколько энергии даёт энергостанция указанного уровня. */
    public int plantEnergyGives(int level) {
        return asInt(plantRaw(level).get("energy_gives"));
    }

    /** Сколько энергии даёт здание указанного типа (например, ЦУ). */
    public int buildingEnergyGives(BuildingType t) {
        Object v = buildingRaw(t).get("energy_gives");
        return v != null ? asInt(v) : 0;
    }

    /**
     * ЛИЧНЫЙ ЗАПАС игрока по этому роду войск — сколько жетонов рода у него есть
     * ВСЕГО за партию (по правилам ровно 4 каждого рода; уничтоженные
     * возвращаются владельцу на этапе Возврата, поэтому число не убывает).
     *
     * <p>Читается из данных ({@code units.<род>.count}). Если в данных числа нет,
     * запас считается как общий на цвет, поделённый на число родов — но это
     * запасной путь: раньше {@code count} стоял пустым, движок ограничивал только
     * ОБЩЕЕ число жетонов, и игрок мог выставить девять вышек при четырёх по
     * правилам.
     */
    public int unitStock(UnitType t) {
        Object v = unitRaw(t).get("count");
        if (v instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        int kinds = units == null || units.isEmpty() ? 4 : units.size();
        return Math.max(1, unitTokensPerColor / kinds);
    }

    /**
     * Создать жетон юнита указанного типа для владельца owner с id uid.
     *
     * <p>ТРОФЕИ НАПЕЧАТАНЫ НА КОНКРЕТНОМ ЖЕТОНЕ, а не зависят от порядка
     * захвата. Четыре жетона рода различаются оборотом: у вышек на всех четырёх по
     * 1 ТО, у пехоты на трёх по 1 и на четвёртом 2, у техники и авиации на двух по
     * 2 и на двух по 1 (уточнение дизайнера 13.08.2026). Поэтому ценность берётся
     * ПО НОМЕРУ жетона в личном запасе рода.
     *
     * <p>Что было не так: движок всегда брал ПЕРВОЕ число списка, то есть все
     * жетоны выходили по 1 ТО, и жетонов на 2 ТО в игре не существовало вовсе —
     * трофейная экономика недоплачивала примерно четверть очков.
     *
     * @param indexInStock номер жетона в личном запасе рода (0..3)
     */
    public UnitToken makeUnit(UnitType t, int owner, int uid, int indexInStock) {
        UnitToken u = new UnitToken(t, owner, unitHp(t), uid);
        List<Integer> printed = unitTrophyList(t);
        int i = Math.max(0, Math.min(printed.size() - 1, indexInStock));
        u.trophyValue = printed.get(i);
        return u;
    }

    /** Прежняя подпись: жетон с ценностью первого номера (для стендов и сцен). */
    public UnitToken makeUnit(UnitType t, int owner, int uid) {
        return makeUnit(t, owner, uid, 0);
    }

    /**
     * Сколько ЯЧЕЕК ЭНЕРГИИ требует здание этого типа и уровня — по данным, а
     * не по памяти. Боты считали это число хардкодом и разошлись бы с данными
     * при первой же правке дизайнера.
     */
    public int buildingEnergySlots(BuildingType t, Integer level) {
        if (t == BuildingType.MINER) {
            return minerEnergySlots(level);
        }
        if (t == BuildingType.POWER_PLANT) {
            return 0;                       // источники, а не потребители
        }
        Map<String, Object> raw = buildingRaw(t);
        Object need = raw.get("energy_needed");
        return asInt(need != null ? need : raw.get("energy_slots"));
    }

    /** Создать жетон здания указанного типа/уровня для владельца owner с id uid. */
    public BuildingToken makeBuilding(BuildingType t, int owner, int uid, Integer level) {
        int slots = buildingEnergySlots(t, level);
        BuildingToken b = new BuildingToken(t, owner, buildingHp(t, level), slots, level, uid);
        b.trophyValue = buildingTrophy(t, level);
        return b;
    }
}
