package kelium.core;

/** Четыре рода войск. Прочность/скорость/атаки берутся из досок и правил. */
public enum UnitType {
    INFANTRY("infantry"),   // пехота
    VEHICLE("vehicle"),     // техника
    AIRCRAFT("aircraft"),   // авиация
    TOWER("tower");         // вышка (войско, но бьётся как здание)

    /** Строковый код, как в YAML-контенте (зеркалит value Python-перечисления). */
    public final String code;

    UnitType(String code) {
        this.code = code;
    }

    /** Найти тип войск по строковому коду из данных. */
    public static UnitType fromCode(String code) {
        for (UnitType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("неизвестный тип юнита: " + code);
    }
}
