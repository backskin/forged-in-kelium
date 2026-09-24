package kelium.engine;

import java.util.HashMap;
import java.util.Map;

import kelium.core.Resource;

/**
 * Четыре приказа игры (верхний/нижний приказ карты приказа), по два действия в
 * каждом — по картам «карты-приказов-симметрия» (экспорт 24.09.2026):
 * <pre>
 *   place   (РАЗМЕСТИТЬ)      -> манёвр (движение), стройка
 *   acquire (ПРИОБРЕСТИ)      -> снабжение (сборка), рынок
 *   control (КОНТРОЛИРОВАТЬ)  -> питание (смена энергии), бой
 *   explore (ИССЛЕДОВАТЬ)     -> добыча, наука
 * </pre>
 * Пятая карта — ЗАТАИТЬСЯ (джокер): любые два разных действия из восьми.
 *
 * <p>СТАРЫЕ КОДЫ приказов (development, infrastructure, operation,
 * acquisitions) остаются читаемыми — в записях прошлых партий и в старых наборах
 * карт. Они сопоставлены новым приказам по главному действию: инфраструктура →
 * Разместить (стройка), разработка → Приобрести (снабжение), наступление →
 * Контролировать (бой), приобретения → Исследовать (наука).
 */
public enum Order {
    PLACE("place"),
    ACQUIRE("acquire"),
    CONTROL("control"),
    EXPLORE("explore");

    public final String code;

    Order(String code) {
        this.code = code;
    }

    /** Найти приказ по строковому коду из данных приказов (новому или старому). */
    public static Order fromCode(String code) {
        for (Order o : values()) {
            if (o.code.equals(code)) {
                return o;
            }
        }
        return switch (code == null ? "" : code) {
            case "infrastructure" -> PLACE;
            case "development" -> ACQUIRE;
            case "operation" -> CONTROL;
            case "acquisitions" -> EXPLORE;
            default -> throw new IllegalArgumentException("неизвестный приказ: " + code);
        };
    }

    /** Русское имя приказа — как на карте. */
    public String имя() {
        return switch (this) {
            case PLACE -> "РАЗМЕСТИТЬ";
            case ACQUIRE -> "ПРИОБРЕСТИ";
            case CONTROL -> "КОНТРОЛИРОВАТЬ";
            case EXPLORE -> "ИССЛЕДОВАТЬ";
        };
    }

    /** Действия приказа (коды действий движка). */
    public static final Map<Order, String[]> ORDER_ACTIONS = new HashMap<>();

    /** Ресурс, которым чаще всего питается приказ (для оценок старых ботов). */
    public static final Map<Order, Resource> ORDER_RESOURCE = new HashMap<>();

    static {
        ORDER_ACTIONS.put(PLACE, new String[]{"movement", "build"});
        ORDER_ACTIONS.put(ACQUIRE, new String[]{"assembly", "market"});
        ORDER_ACTIONS.put(CONTROL, new String[]{"energy_swap", "combat"});
        ORDER_ACTIONS.put(EXPLORE, new String[]{"mining", "science"});

        ORDER_RESOURCE.put(PLACE, Resource.COIN);
        ORDER_RESOURCE.put(ACQUIRE, Resource.KELIUM);
        ORDER_RESOURCE.put(CONTROL, Resource.AMMO);
        ORDER_RESOURCE.put(EXPLORE, null);
    }
}
