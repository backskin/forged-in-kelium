package kelium.core;

/**
 * Одна допустимая опция, предлагаемая агенту в точке решения.
 *
 * <p>{@code kind} группирует опции (например, "reveal_order", "action",
 * "target_hex"). {@code payload} — заданные движком данные, с которыми работать
 * при выборе. {@code label} — человекочитаемая подпись для логов/отчётов.
 *
 * <p>Модель «точек выбора»: движок всегда предлагает только легальные варианты,
 * агент возвращает один — так один цикл управляет случайными, эвристическими и
 * будущими RL-агентами.
 */
public record Choice(String kind, Object payload, String label) {

    /** Опция без подписи. */
    public Choice(String kind, Object payload) {
        this(kind, payload, "");
    }
}
