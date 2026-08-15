package kelium.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Гексовое поле целиком: граф гексов по их идентификаторам.
 *
 * <p>Незыблемое ядро: граф гексов. Соседство задаётся осевыми координатами
 * (см. загрузчик сценариев); индекс стороны i на гексе указывает на соседа по
 * направлению i из {@link #AXIAL_DIRS}.
 */
public final class Field {

    /** Шесть осевых направлений (плоский верх), индекс = номер стороны гекса 0..5. */
    public static final int[][] AXIAL_DIRS = {
        {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}
    };

    // LinkedHashMap ради детерминированного порядка обхода гексов.
    public final Map<String, Hex> hexes = new LinkedHashMap<>();

    /** Точная копия поля со всей обстановкой (для просчёта вперёд). */
    public Field copy() {
        Field f = new Field();
        for (Hex h : hexes.values()) {
            f.addHex(h.copy());
        }
        return f;
    }

    /** Добавить гекс на поле (по его id). */
    public void addHex(Hex h) {
        hexes.put(h.id, h);
    }

    /**
     * Ненаправленное соседство. Если задан sideA (индекс стороны гекса a,
     * обращённой к b), записываем и обратную сторону (sideA+3)%6 у b — так поле
     * знает, какая сторона к какому соседу примыкает.
     */
    public void link(String a, String b, Integer sideA) {
        Hex ha = hexes.get(a);
        Hex hb = hexes.get(b);
        if (!ha.neighbors.contains(b)) {
            ha.neighbors.add(b);
        }
        if (!hb.neighbors.contains(a)) {
            hb.neighbors.add(a);
        }
        if (sideA != null) {
            ha.neighborBySide[sideA] = b;
            hb.neighborBySide[(sideA + 3) % 6] = a;
        }
    }

    /** Ненаправленное соседство без указания стороны. */
    public void link(String a, String b) {
        link(a, b, null);
    }

    /**
     * Список id соседних гексов (КОПИЯ — вызывающий может её менять).
     *
     * <p>Копия нужна не всем: поиск пути и перебор ходов только читают список,
     * а вызываются десятки тысяч раз за партию. Для чтения есть
     * {@link #neighborsView(String)} без аллокации.
     */
    public List<String> neighbors(String hexId) {
        return new ArrayList<>(hexes.get(hexId).neighbors);
    }

    /** Соседи ТОЛЬКО ДЛЯ ЧТЕНИЯ — без создания нового списка. */
    public List<String> neighborsView(String hexId) {
        Hex h = hexes.get(hexId);
        return h == null ? List.of() : java.util.Collections.unmodifiableList(h.neighbors);
    }

    /** Доступ к гексу по id. */
    public Hex get(String hexId) {
        return hexes.get(hexId);
    }

    /** Число гексов на поле. */
    public int size() {
        return hexes.size();
    }

    /** Все гексы-грядки (тайлы зарождения) на поле. */
    public List<Hex> spawnTiles() {
        List<Hex> out = new ArrayList<>();
        for (Hex h : hexes.values()) {
            if (h.hasSpawnTile()) {
                out.add(h);
            }
        }
        return out;
    }
}
