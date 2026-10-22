package kelium.core;

/**
 * Жетон здания на поле или в запасе. Как и у войск, характеристики одинаковы у
 * всех игроков. Здание «запитано», когда все его ячейки энергии заполнены.
 */
public final class BuildingToken implements Token {
    public final BuildingType type;
    public final int owner;
    public int hp;                   // прочность (+бонус правил)
    public int energySlots;          // сколько ячеек энергии требует
    public int trophyValue = 1;      // ценность на трофейной стороне
    public Integer level = null;     // уровень для добытчика/энергостанции (1..4)
    public int energyPlaced = 0;     // кубиков энергии сейчас на здании (потребитель)
    public int damage = 0;
    public final int uid;
    public String hexId = null;
    public Integer capturedBy = null;

    // --- Модель энергии «гекс-исход» (К3, §3.2 свода) ---
    // У кубика ЭНР всего два места: ячейка здания-потребителя или его
    // ИСТОЧНИК (ЭС/ЦУ), где он простаивает. Кубик навсегда принадлежит
    // источнику; Смена энергии перекладывает кубики ВЫБРАННОГО гекса-исхода.
    /** Кубики на этом здании ПО ИСТОЧНИКАМ: uid источника -> сколько. */
    public final java.util.Map<Integer, Integer> energyBySource = new java.util.HashMap<>();
    /** Простаивающие кубики НА этом источнике (для ЭС/ЦУ; у прочих 0). */
    public int energyIdle = 0;

    /** Положить n кубиков источника srcUid на это здание (потребителя). */
    public void addEnergyFrom(int srcUid, int n) {
        if (n <= 0) {
            return;
        }
        energyBySource.merge(srcUid, n, Integer::sum);
        energyPlaced += n;
    }

    /** Снять с этого здания ВСЕ кубики источника srcUid; вернуть их число. */
    public int stripEnergyOf(int srcUid) {
        Integer n = energyBySource.remove(srcUid);
        if (n == null) {
            return 0;
        }
        energyPlaced = Math.max(0, energyPlaced - n);
        return n;
    }

    public BuildingToken(BuildingType type, int owner, int hp, int energySlots,
                         Integer level, int uid) {
        this.type = type;
        this.owner = owner;
        this.hp = hp;
        this.energySlots = energySlots;
        this.level = level;
        this.uid = uid;
    }

    public boolean alive() {
        return damage < hp;
    }

    /**
      * ЗАПИТАНО ЛИ ЗДАНИЕ.
      *
      * <p>Обычное здание — когда все его ячейки энергии заполнены.
      *
      * <p>ЦУ — как только на жетоне лежит кубик, любой (правило дизайнера
      * 17.09.2026). ЦУ один жетон, и источник, и потребитель: зон на нём две,
      * но кубик и в ячейке, и в свободной энергии лежит НА ЖЕТОНЕ. Перекладывать
      * его с ЦУ на само ЦУ, чтобы «вставить в ячейку», за столом никто не станет —
      * это движение из руки в ту же руку. Прежде {@code powered()} видел только
      * ячейки, и ЦУ с тремя кубиками в свободной зоне числилось незапитанным.
      */
    public boolean powered() {
        int наЖетоне = energyPlaced
            + (type == BuildingType.COMMAND_CENTER ? energyIdle : 0);
        return наЖетоне >= energySlots;
    }

    @Override
    public void resetDamage() {
        damage = 0;
    }

    /** Эффект карты: снять ОДИН кубик урона. */
    @Override
    public void healOneDamage() {
        if (damage > 0) {
            damage -= 1;
        }
    }

    /** Точная копия жетона (для копии состояния при просчёте вперёд). */
    public BuildingToken copy() {
        BuildingToken b = new BuildingToken(type, owner, hp, energySlots, level, uid);
        b.trophyValue = trophyValue;
        b.energyPlaced = energyPlaced;
        b.damage = damage;
        b.hexId = hexId;
        b.capturedBy = capturedBy;
        b.energyIdle = energyIdle;
        b.energyBySource.putAll(energyBySource);
        return b;
    }

    @Override public Target category() { return Target.BUILDINGS_TOWERS; }
    @Override public int owner() { return owner; }
    @Override public int uid() { return uid; }
    @Override public int trophyValue() { return trophyValue; }
    @Override public Integer capturedBy() { return capturedBy; }
    @Override public void setCapturedBy(Integer seat) { this.capturedBy = seat; }
    @Override public String hexId() { return hexId; }
    @Override public void setHexId(String hexId) { this.hexId = hexId; }
}
