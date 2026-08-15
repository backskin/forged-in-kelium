package kelium.core;

/**
 * Четыре расходуемых ресурса, которыми владеет игрок (энергия — отдельно, она
 * не тратится, а размещается на зданиях, поэтому в этот пул не входит).
 *
 * <ul>
 *   <li>{@link #COIN} — монета (МОН): деньги; 5 монет = 1 ПО.</li>
 *   <li>{@link #KELIUM} — келемий (КЕЛ): самый очковый ресурс; 1 = 1 ПО.</li>
 *   <li>{@link #AMMO} — боеприпас (БПР): тратится на атаки и лишние ходы.</li>
 *   <li>{@link #DEBRIS} — обломок (ОБЛ): чёрные кубики в хранилище, идут на
 *       треки. Не путать с «трофеем» ({@link PlayerState#trophySpace}) —
 *       перевёрнутым жетоном уничтожения на отложенной карте приказа; трофей
 *       конвертируется в обломки 1:1 в конце раунда (см. {@code returnStep}).</li>
 * </ul>
 */
public enum Resource {
    COIN("coin"), KELIUM("kelium"), AMMO("ammo"), DEBRIS("debris");

    /** Строковый код, как в YAML-контенте. */
    public final String code;

    Resource(String code) {
        this.code = code;
    }

    /** Найти ресурс по строковому коду из данных. */
    public static Resource fromCode(String code) {
        for (Resource r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        throw new IllegalArgumentException("неизвестный ресурс: " + code);
    }
}
