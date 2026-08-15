package kelium.core;

/**
 * Тайл зарождения келемия — ФИЗИЧЕСКИЙ жетон, который кладётся НА гекс поля
 * (как в настолке), а не свойство гекса. Пока тайл лежит:
 * <ul>
 *   <li>заняты ВСЕ наземные ячейки гекса (стройка и стоянка войск невозможны);</li>
 *   <li>занята и воздушная ячейка — авиация НЕ останавливается, но пролетает;</li>
 *   <li>примыкающие стенкой запитанные добытчики извлекают с него келемий.</li>
 * </ul>
 * Тайл умеет переворачиваться (лицо → оборот) и после выработки оборота уходит
 * с гекса (стопка ×2 — два тайла подряд), полностью освобождая его.
 */
public final class SpawnTile {

    public final boolean isStart;    // стартовое зарождение (иные награды)
    public int faceKelium;           // келемий на лице
    public int backKelium;           // келемий на обороте
    public int kelium;               // сколько лежит сейчас
    public boolean flipped = false;  // текущий тайл перевёрнут на оборот
    public int stack;                // тайлов в стопке (×2 => 2)

    public SpawnTile(boolean isStart, int faceKelium, int backKelium, int stack) {
        this.isStart = isStart;
        this.faceKelium = faceKelium;
        this.backKelium = backKelium;
        this.kelium = faceKelium;
        this.stack = Math.max(1, stack);
    }

    /** Точная копия тайла (для копии состояния при просчёте вперёд). */
    public SpawnTile copy() {
        SpawnTile t = new SpawnTile(isStart, faceKelium, backKelium, stack);
        t.kelium = kelium;
        t.flipped = flipped;
        t.stack = stack;
        return t;
    }

    /** Перевернуть текущий тайл на оборот (после выработки лица). */
    public void flip() {
        flipped = true;
        kelium = backKelium;
    }

    /**
     * Снять текущий тайл после выработки оборота. true = под ним был ещё тайл
     * (стопка ×2, гекс остаётся закрытым); false = тайлов больше нет, вызывающий
     * обязан убрать жетон с гекса ({@code hex.spawnTile = null}).
     */
    public boolean popStack() {
        stack -= 1;
        if (stack > 0) {
            flipped = false;
            kelium = faceKelium;
            return true;
        }
        return false;
    }
}
