package kelium.cards;

import java.util.LinkedHashMap;
import java.util.Map;

import kelium.engine.cards.Card;

/**
 * Общая часть всех карт: номер, имя и связь с записью в данных.
 *
 * <p>Имя карта берёт из данных, а не хранит в коде: печатное название правит
 * дизайнер, и расхождение между кодом и картой на столе недопустимо. В коде
 * остаётся только поведение.
 */
public abstract class BaseCard implements Card {

    private final String id;
    private Map<String, Object> data = new LinkedHashMap<>();

    protected BaseCard(String id) {
        this.id = id;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public String name() {
        return String.valueOf(data.getOrDefault("name", id));
    }

    @Override
    public Map<String, Object> data() {
        return data;
    }

    @Override
    public void bind(Map<String, Object> entry) {
        this.data = entry == null ? new LinkedHashMap<>() : entry;
    }

    /** Число из записи карты (или из вложенной записи), с запасным значением. */
    protected int num(String key, int fallback) {
        return data.get(key) instanceof Number n ? n.intValue() : fallback;
    }

    /** Число из параметров требования: {@code requirement.params.<key>}. */
    protected int param(String key, int fallback) {
        if (data.get("requirement") instanceof Map<?, ?> req
                && req.get("params") instanceof Map<?, ?> p
                && p.get(key) instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    /** То же для усиленного требования. */
    protected int paramEnhanced(String key, int fallback) {
        if (data.get("enhanced") instanceof Map<?, ?> req
                && req.get("params") instanceof Map<?, ?> p
                && p.get(key) instanceof Number n) {
            return n.intValue();
        }
        return param(key, fallback);
    }

    /**
     * Доля выполнения по счётчику: сколько есть из скольких нужно, обрезано
     * единицей. Общий случай почти для всех считаемых условий.
     */
    protected static double ratio(double have, double need) {
        if (need <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, have / need));
    }

    @Override
    public String describe() {
        Object d = data.get("описание");
        return d == null ? name() : String.valueOf(d);
    }
}
