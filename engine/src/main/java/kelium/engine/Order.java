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
 * <p>ПЯТЬ ПРИКАЗОВ НАБОРА orders 5.0.0 (решение дизайнера 27.09.2026): действий
 * пять, каждое — развилка «одно из двух» ({@link Actions#FORKS}), и у каждого
 * приказа два соседних действия по кругу:
 * <pre>
 *   settle   (ОСВОИТЬ)        -> добыча, питание
 *   mobilize (МОБИЛИЗОВАТЬ)   -> питание, снабжение
 *   advance  (НАСТУПАТЬ)      -> снабжение, командование
 *   secure   (КОНТРОЛИРОВАТЬ) -> командование, развитие
 *   research (ИССЛЕДОВАТЬ)    -> развитие, добыча
 * </pre>
 * Коды у них свои, а не прежние control/explore: те же слова на карте, но
 * другие действия, а записи старых партий должны читаться по-старому.
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
    EXPLORE("explore"),
    SETTLE("settle"),
    MOBILIZE("mobilize"),
    ADVANCE("advance"),
    SECURE("secure"),
    RESEARCH("research");

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
            case SETTLE -> "ОСВОИТЬ";
            case MOBILIZE -> "МОБИЛИЗОВАТЬ";
            case ADVANCE -> "НАСТУПАТЬ";
            case SECURE -> "КОНТРОЛИРОВАТЬ";
            case RESEARCH -> "ИССЛЕДОВАТЬ";
        };
    }

    /**
     * БЛИЖАЙШИЙ ПРИКАЗ НАБОРА 4.0.0 — для оценок ботов, которые знают только
     * четыре прежних приказа: ОСВОИТЬ ≈ РАЗМЕСТИТЬ (стройка), МОБИЛИЗОВАТЬ ≈
     * ПРИОБРЕСТИ (снабжение), НАСТУПАТЬ и КОНТРОЛИРОВАТЬ ≈ прежний
     * КОНТРОЛИРОВАТЬ (бой), ИССЛЕДОВАТЬ ≈ прежний ИССЛЕДОВАТЬ (наука).
     */
    public Order legacy() {
        return switch (this) {
            case SETTLE -> PLACE;
            case MOBILIZE -> ACQUIRE;
            case ADVANCE, SECURE -> CONTROL;
            case RESEARCH -> EXPLORE;
            default -> this;
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
        ORDER_ACTIONS.put(SETTLE, new String[]{"extract", "power"});
        ORDER_ACTIONS.put(MOBILIZE, new String[]{"power", "supply"});
        ORDER_ACTIONS.put(ADVANCE, new String[]{"supply", "command"});
        ORDER_ACTIONS.put(SECURE, new String[]{"command", "develop"});
        ORDER_ACTIONS.put(RESEARCH, new String[]{"develop", "extract"});

        ORDER_RESOURCE.put(PLACE, Resource.COIN);
        ORDER_RESOURCE.put(ACQUIRE, Resource.KELIUM);
        ORDER_RESOURCE.put(CONTROL, Resource.AMMO);
        ORDER_RESOURCE.put(EXPLORE, null);
        ORDER_RESOURCE.put(SETTLE, Resource.KELIUM);
        ORDER_RESOURCE.put(MOBILIZE, Resource.AMMO);
        ORDER_RESOURCE.put(ADVANCE, Resource.AMMO);
        ORDER_RESOURCE.put(SECURE, Resource.COIN);
        ORDER_RESOURCE.put(RESEARCH, null);
    }
}
