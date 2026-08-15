package kelium.core;

/**
 * Жетон войска на поле или в запасе. Характеристики (прочность, ценность трофея)
 * одинаковы у всех игроков — «железное правило асимметрии»: меняются доски, а не
 * жетоны.
 */
public final class UnitToken implements Token {
    public final UnitType type;
    public final int owner;          // индекс места (seat)
    public int hp;                   // напечатанная прочность (+бонус правил)
    public int trophyValue = 1;      // ценность на трофейной стороне
    public int damage = 0;           // накопленный урон в этом раунде
    public final int uid;            // уникальный id экземпляра
    public String hexId = null;      // где стоит; null = в запасе
    public Integer capturedBy = null; // кто держит на месте трофеев (уничтожен в раунде)
    // Супер-войско с карты супер-арсенала: +1 HP уже вшит, атаки УНИВЕРСАЛЬНЫЕ
    // (любой жетон войск за 1 БП / здания-вышки за 2 БП) — см. CombatResolver.
    public boolean superUnit = false;
    public String superCardId = null;

    /**
     * ВОЙСКО ВНУТРИ ЗДАНИЯ: uid своего здания, в которое жетон вставлен (или null).
     *
     * <p>Правило (уточнение дизайнера 12.08.2026). Найм идёт на гекс со зданием.
     * Если на гексе места нет, войско можно вставить ПРЯМО В ЗДАНИЕ — но не
     * больше одного войска в здание, и такое здание у игрока только одно. Вышка в
     * здание не вставляется никогда. Если места нет совсем, жетон не нанимается и
     * остаётся в запасе.
     *
     * <p>Вставленное войско ячейку гекса не занимает и НЕ АТАКУЕМО, пока здание
     * живо: сперва надо снести здание. Это элемент тактики, а не общее свойство —
     * поэтому состояние ЯВНОЕ, а не выводится из «войско стоит на гексе со своим
     * зданием подходящего рода». Прежний вывод «по совпадению» делал неуязвимыми
     * все войска у своих зданий (казарма превращалась в крепость) и вышку на гексе
     * ЦУ, хотя вышке прятаться запрещено прямым правилом.
     */
    public Integer insideBuildingUid = null;

    /** Вставлено ли войско внутрь своего здания. */
    public boolean inside() {
        return insideBuildingUid != null;
    }

    public UnitToken(UnitType type, int owner, int hp, int uid) {
        this.type = type;
        this.owner = owner;
        this.hp = hp;
        this.uid = uid;
    }

    /** Жив ли жетон (урон меньше прочности). */
    public boolean alive() {
        return damage < hp;
    }

    /** Снять весь урон (при возврате уничтоженного жетона владельцу). */
    @Override
    public void resetDamage() {
        damage = 0;
    }

    /** Обновление: снять ОДИН кубик урона (не весь). Урон копится по раундам. */
    @Override
    public void healOneDamage() {
        if (damage > 0) {
            damage -= 1;
        }
    }

    /** Вышка бьётся как здание (правило свода), остальные — по своему роду. */
    @Override public Target category() {
        return type == UnitType.TOWER ? Target.BUILDINGS_TOWERS : Target.fromCode(type.code);
    }

    /** Точная копия жетона (для копии состояния при просчёте вперёд). */
    public UnitToken copy() {
        UnitToken u = new UnitToken(type, owner, hp, uid);
        u.trophyValue = trophyValue;
        u.damage = damage;
        u.hexId = hexId;
        u.capturedBy = capturedBy;
        u.superUnit = superUnit;
        u.superCardId = superCardId;
        u.insideBuildingUid = insideBuildingUid;
        return u;
    }

    @Override public int owner() { return owner; }
    @Override public int uid() { return uid; }
    @Override public int trophyValue() { return trophyValue; }
    @Override public Integer capturedBy() { return capturedBy; }
    @Override public void setCapturedBy(Integer seat) { this.capturedBy = seat; }
    @Override public String hexId() { return hexId; }

    /**
     * Переставить жетон. Смена гекса ВЫВОДИТ войско из здания: внутри здания
     * можно стоять, только пока стоишь на его гексе.
     */
    @Override public void setHexId(String hexId) {
        if (this.hexId == null ? hexId != null : !this.hexId.equals(hexId)) {
            insideBuildingUid = null;
        }
        this.hexId = hexId;
    }
}
