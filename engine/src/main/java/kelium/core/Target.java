package kelium.core;

/** Четыре типа целей атаки. Вышка поражается как «здания и вышки». */
public enum Target {
    INFANTRY("infantry"),                  // пехота
    VEHICLE("vehicle"),                    // техника
    AIRCRAFT("aircraft"),                  // авиация
    BUILDINGS_TOWERS("buildings_towers");  // здания и вышки

    /** Строковый код, как в YAML-контенте. */
    public final String code;

    Target(String code) {
        this.code = code;
    }

    /** Найти цель по строковому коду из данных. */
    public static Target fromCode(String code) {
        for (Target t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("неизвестная цель: " + code);
    }
}
