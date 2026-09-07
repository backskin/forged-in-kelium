package kelium.core;

/**
 * Четыре расходуемых ресурса, которыми владеет игрок (энергия — отдельно, она
 * не тратится, а размещается на зданиях, поэтому в этот пул не входит).
 *
 * <ul>
 *   <li>{@link #COIN} — монета (МОН): деньги; 5 монет = 1 ПО.</li>
 *   <li>{@link #KELIUM} — келемий (КЕЛ): самый очковый ресурс; 1 = 1 ПО.</li>
 *   <li>{@link #AMMO} — боеприпас (БПР): тратится на атаки и лишние ходы.</li>
 *   <li>{@link #TROPHY} — трофей (ТРФ): чёрные кубики в хранилище, идут на
 *       треки науки. Не путать с УНИЧТОЖЕННЫМ ЖЕТОНОМ
 *       ({@link PlayerState#destroyedTokens}) — это целый снесённый жетон
 *       противника, лежащий трофейной стороной вверх на отложенной карте
 *       приказа. Такой жетон конвертируется в трофеи 1:1 в конце раунда
 *       (см. {@code returnStep}), и сколько их даст — написано на его
 *       трофейной стороне ({@code trophyValue}).</li>
 * </ul>
 */
public enum Resource {
    COIN("coin"), KELIUM("kelium"), AMMO("ammo"), TROPHY("trophy");

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
