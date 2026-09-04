package kelium.engine;

import java.util.HashMap;
import java.util.Map;

import kelium.core.Resource;

/**
 * Четыре приказа игры (верхний/нижний приказ карты приказа), по два действия в
 * каждом:
 * <pre>
 *   development (разработка)        -> сборка, добыча
 *   infrastructure (инфраструктура) -> стройка, смена энергии
 *   operation (операция)            -> движение, бой
 *   acquisitions (приобретения)     -> рынок, наука
 * </pre>
 */
public enum Order {
    DEVELOPMENT("development"),
    INFRASTRUCTURE("infrastructure"),
    OPERATION("operation"),
    ACQUISITIONS("acquisitions");

    public final String code;

    Order(String code) {
        this.code = code;
    }

    /** Найти приказ по строковому коду из данных приказов. */
    public static Order fromCode(String code) {
        for (Order o : values()) {
            if (o.code.equals(code)) {
                return o;
            }
        }
        throw new IllegalArgumentException("неизвестный приказ: " + code);
    }

    /** Два действия, принадлежащие каждому приказу (имена действий движка). */
    public static final Map<Order, String[]> ORDER_ACTIONS = new HashMap<>();

    /**
     * Ресурс, который «питает» приказ (правило #5) — используется заданиями, не
     * самим действием, но сохранено здесь как каноническое соответствие.
     */
    public static final Map<Order, Resource> ORDER_RESOURCE = new HashMap<>();

    static {
        ORDER_ACTIONS.put(DEVELOPMENT, new String[]{"assembly", "mining"});
        ORDER_ACTIONS.put(INFRASTRUCTURE, new String[]{"build", "energy_swap"});
        ORDER_ACTIONS.put(OPERATION, new String[]{"movement", "combat"});
        ORDER_ACTIONS.put(ACQUISITIONS, new String[]{"market", "science"});

        ORDER_RESOURCE.put(DEVELOPMENT, null);          // энергия — не ресурс из пула
        ORDER_RESOURCE.put(INFRASTRUCTURE, Resource.COIN);
        ORDER_RESOURCE.put(OPERATION, Resource.AMMO);
        ORDER_RESOURCE.put(ACQUISITIONS, Resource.KELIUM);
    }
}
