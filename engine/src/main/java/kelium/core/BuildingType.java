package kelium.core;

/** Типы зданий. Характеристики (ячейки энергии, прочность, выработка) — из данных. */
public enum BuildingType {
    BARRACKS("barracks"),              // казарма
    FACTORY("factory"),                // завод
    AIRBASE("airbase"),                // авиабаза
    COMMAND_CENTER("command_center"),  // ЦУ (командный центр)
    MINER("miner"),                    // добытчик (№1–4)
    POWER_PLANT("power_plant");        // энергостанция (№1–4)

    /** Строковый код, как в YAML-контенте. */
    public final String code;

    BuildingType(String code) {
        this.code = code;
    }

    /** Найти тип здания по строковому коду из данных. */
    public static BuildingType fromCode(String code) {
        for (BuildingType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("неизвестный тип здания: " + code);
    }
}
