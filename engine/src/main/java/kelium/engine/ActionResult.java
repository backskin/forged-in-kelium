package kelium.engine;

import java.util.Map;

/**
 * Результат выполнения действия: успех, текстовое пояснение и телеметрия.
 *
 * <p>{@code telemetry} — небольшая карта фактов о произошедшем (для журнала хода
 * и отчётов).
 */
public record ActionResult(boolean ok, String detail, Map<String, Object> telemetry) {

    public static ActionResult ok(String detail) {
        return new ActionResult(true, detail, null);
    }

    public static ActionResult ok(String detail, Map<String, Object> telemetry) {
        return new ActionResult(true, detail, telemetry);
    }

    public static ActionResult fail(String detail) {
        return new ActionResult(false, detail, null);
    }
}
