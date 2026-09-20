package kelium.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Боевая сторона доски: скорости, цены построек и таблица атак юнитов.
 *
 * <p>Обёртка над сырым словарём контента доски (сторона A или Б1–Б4). Меняется
 * только доска — характеристики жетонов не меняются никогда.
 */
public final class TroopSide {

    public final String side;
    public final Map<String, Object> raw;

    public TroopSide(String side, Map<String, Object> raw) {
        this.side = side;
        this.raw = raw;
    }

    /** Отображаемое имя стороны (или её код, если имя не задано). */
    public String name() {
        Object n = raw.get("name");
        return n != null ? n.toString() : side;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> speedsMap() {
        Map<String, Object> печатные = (Map<String, Object>) raw.get("speeds");
        return Переопределение.наложить(печатные);
    }

    /**
     * СКОРОСТИ ИЗ ЗАПУСКА: {@code -Dkelium.speeds=infantry:2,vehicle:2,aircraft:3}.
     *
     * <p>Скорость напечатана на планшете войск, и менять её в файле досок ради
     * одного замера значит смешивать проверку с решением: пока не измерено,
     * правки в печатном компоненте быть не должно. Переключатель накладывается
     * поверх печатных значений и по умолчанию не делает ничего.
     *
     * <p>Разбор строки делается ОДИН РАЗ на процесс: {@code speedsMap()}
     * зовётся на каждое движение каждого жетона, и разбирать её там заново
     * стоило бы дороже самого хода.
     */
    private static final class Переопределение {

        private static final Map<String, Object> ИЗ_ЗАПУСКА = разобрать();

        private Переопределение() {
        }

        private static Map<String, Object> разобрать() {
            String строка = System.getProperty("kelium.speeds", "").trim();
            if (строка.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> итог = new java.util.LinkedHashMap<>();
            for (String кусок : строка.split(",")) {
                int двоеточие = кусок.indexOf(':');
                if (двоеточие <= 0) {
                    throw new IllegalArgumentException(
                        "kelium.speeds: ожидался вид infantry:2, а было «" + кусок + "»");
                }
                итог.put(кусок.substring(0, двоеточие).trim(),
                    Integer.parseInt(кусок.substring(двоеточие + 1).trim()));
            }
            return Map.copyOf(итог);
        }

        static Map<String, Object> наложить(Map<String, Object> печатные) {
            kelium.dataio.Вариант в = kelium.dataio.Вариант.сейчас();
            Map<String, Object> изВарианта = в == null ? Map.of() : в.скорости();
            if (ИЗ_ЗАПУСКА.isEmpty() && изВарианта.isEmpty()) {
                return печатные;
            }
            Map<String, Object> итог = new java.util.LinkedHashMap<>(печатные);
            итог.putAll(изВарианта);
            итог.putAll(ИЗ_ЗАПУСКА);
            return итог;
        }
    }

    /** Скорость движения указанного типа юнита на этой стороне. */
    public int speed(UnitType unit) {
        Object v = speedsMap().get(unit.code);
        return ((Number) v).intValue();
    }

    /** Все скорости движения по типам юнитов. */
    public Map<UnitType, Integer> speeds() {
        Map<UnitType, Integer> out = new EnumMap<>(UnitType.class);
        for (Map.Entry<String, Object> e : speedsMap().entrySet()) {
            out.put(UnitType.fromCode(e.getKey()), ((Number) e.getValue()).intValue());
        }
        return out;
    }

    /** Цена стройки указанного здания (ключ barracks/factory/airbase) на этой стороне. */
    @SuppressWarnings("unchecked")
    public int buildingPrice(String building) {
        Map<String, Object> prices = (Map<String, Object>) raw.get("building_prices");
        return ((Number) prices.get(building)).intValue();
    }

    /**
     * БОЙ 2.0 (заказ дизайнера 18.08.2026): у стороны вместо печатной пары
     * целей — универсальная ячейка (любая цель, задаётся ценой ruleset'а, не
     * этим файлом) плюс одна специализированная печатная цель на род войск.
     * Флаг живёт на самой стороне, чтобы старые доски (boards.1.0.x) работали
     * ровно как раньше — см. {@link #attacks} и {@code CombatResolver}.
     */
    public boolean dualCell() {
        return Boolean.TRUE.equals(raw.get("dual_cell"));
    }

    /**
     * ТАБЛИЦА ПЕЧАТНЫХ ЦЕЛЕЙ, с поправкой из запуска:
     * {@code -Dkelium.targets=infantry:vehicle,vehicle:aircraft,
     * aircraft:buildings_towers,tower:infantry}.
     *
     * <p>Цели напечатаны на планшете войск, и переставлять их в файле досок
     * ради замера значит менять компонент раньше, чем измерено. Поправка
     * накладывается поверх печатной таблицы по роду войск и по умолчанию не
     * делает ничего. Коды целей — {@link Target#code}.
     *
     * <p>Поправка работает только на сторонах с {@link #dualCell()}, где
     * {@code attacks:} хранит одну цель на род: у старых досок там пара, и
     * подменять половину пары строкой запуска было бы тихой ошибкой.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> таблицаЦелей() {
        Map<String, Object> печатная = (Map<String, Object>) raw.get("attacks");
        kelium.dataio.Вариант в = kelium.dataio.Вариант.сейчас();
        Map<String, String> изВарианта = в == null ? Map.of() : в.цели();
        boolean нечего = ЦелиИзЗапуска.СПИСОК.isEmpty() && изВарианта.isEmpty();
        if (печатная == null || нечего || !dualCell()) {
            return печатная;
        }
        Map<String, Object> итог = new java.util.LinkedHashMap<>(печатная);
        итог.putAll(изВарианта);
        итог.putAll(ЦелиИзЗапуска.СПИСОК);
        return итог;
    }

    /** Разбор {@code kelium.targets} — один раз на процесс. */
    private static final class ЦелиИзЗапуска {

        static final Map<String, Object> СПИСОК = разобрать();

        private ЦелиИзЗапуска() {
        }

        private static Map<String, Object> разобрать() {
            String строка = System.getProperty("kelium.targets", "").trim();
            if (строка.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> итог = new java.util.LinkedHashMap<>();
            for (String кусок : строка.split(",")) {
                int двоеточие = кусок.indexOf(':');
                if (двоеточие <= 0) {
                    throw new IllegalArgumentException(
                        "kelium.targets: ожидался вид infantry:vehicle, а было «" + кусок + "»");
                }
                String род = кусок.substring(0, двоеточие).trim();
                String цель = кусок.substring(двоеточие + 1).trim();
                UnitType.fromCode(род);            // падём сразу на опечатке
                Target.fromCode(цель);
                итог.put(род, цель);
            }
            return Map.copyOf(итог);
        }
    }

    /**
     * ОДНА печатная цель специализированной ячейки (только для {@link
     * #dualCell()} сторон — {@code attacks:} там хранит скаляр, не пару).
     */
    @SuppressWarnings("unchecked")
    public Target specializedTarget(UnitType unit) {
        Map<String, Object> tbl = таблицаЦелей();
        if (tbl == null) {
            return null;
        }
        Object row = tbl.get(unit.code);
        return row == null ? null : Target.fromCode(row.toString());
    }

    /** Цели (основная, вторичная) для юнита, если полная таблица атак задана; иначе null. */
    @SuppressWarnings("unchecked")
    public Target[] attacks(UnitType unit) {
        Map<String, Object> tbl = таблицаЦелей();
        if (tbl == null) {
            return null;
        }
        Object row = tbl.get(unit.code);
        if (row == null) {
            return null;
        }
        List<Object> pair = (List<Object>) row;
        return new Target[]{
            Target.fromCode(pair.get(0).toString()),
            Target.fromCode(pair.get(1).toString())
        };
    }

    /** Могут ли вышки двигаться на этой стороне (по флагу или по скорости). */
    public boolean towersMove() {
        Object flag = raw.get("towers_move");
        if (flag != null) {
            return Boolean.TRUE.equals(flag);
        }
        return speed(UnitType.TOWER) > 0;
    }
}
