package kelium.core;

import java.util.List;
import java.util.Map;

/**
 * Складская сторона доски: раскладка ячеек добытчиков/энергостанций и цены уровней.
 *
 * <p>Ячейки уровня открываются по мере постройки складских зданий этого уровня;
 * тип ячейки: U (universal), K (kelium), A (ammo).
 */
public final class StorageSide {

    public final String side;
    public final Map<String, Object> raw;

    public StorageSide(String side, Map<String, Object> raw) {
        this.side = side;
        this.raw = raw;
    }

    @SuppressWarnings("unchecked")
    private List<Object> minersList() {
        return (List<Object>) raw.get("miners");
    }

    @SuppressWarnings("unchecked")
    private List<Object> plantsList() {
        return (List<Object>) raw.get("plants");
    }

    /** Раскладка ячеек добытчика указанного уровня (1..4). */
    public String minerCells(int level) {
        return String.valueOf(minersList().get(level - 1));
    }

    /** Раскладка ячеек энергостанции указанного уровня (1..4). */
    public String plantCells(int level) {
        return String.valueOf(plantsList().get(level - 1));
    }

    /** Цена улучшения склада до указанного уровня (1..4). */
    @SuppressWarnings("unchecked")
    public int price(int level) {
        List<Object> prices = (List<Object>) raw.get("prices");
        return ((Number) prices.get(level - 1)).intValue();
    }

    /** Уровень склада, дающий трофеи (звезду); по умолчанию 4. */
    public int vpStarLevel() {
        Object v = raw.get("vp_star_level");
        return v != null ? ((Number) v).intValue() : 4;
    }
}
