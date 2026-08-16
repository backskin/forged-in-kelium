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
    public int faceKelium;           // НАПЕЧАТАННЫЙ келемий на лице
    public int backKelium;           // келемий на обороте
    public int kelium;               // сколько лежит сейчас
    public boolean flipped = false;  // текущий тайл перевёрнут на оборот
    public int stack;                // тайлов в стопке (×2 => 2)

    /**
     * ПРАВКА КЕЛЕМИЯ ИЗ РАСКЛАДКИ (−4..+4). Дизайнер ставит её в конструкторе на
     * гекс, и действует она ТОЛЬКО на ЛИЦО ВЕРХНЕГО тайла:
     * <ul>
     *   <li>оборот верхнего тайла — напечатанный {@link #backKelium};</li>
     *   <li>нижний тайл стопки ×2 — напечатанный целиком, обе стороны.</li>
     * </ul>
     * Поэтому правка хранится отдельно, а не вплавляется в {@link #faceKelium}:
     * вплавленная, она возвращалась вместе с лицом и второму тайлу тоже.
     */
    public int topFaceDelta = 0;

    public SpawnTile(boolean isStart, int faceKelium, int backKelium, int stack) {
        this.isStart = isStart;
        this.faceKelium = faceKelium;
        this.backKelium = backKelium;
        this.kelium = faceKelium;
        this.stack = Math.max(1, stack);
    }

    /** Келемий на лице ВЕРХНЕГО тайла — с правкой раскладки. */
    public int topFaceKelium() {
        return Math.max(0, faceKelium + topFaceDelta);
    }

    /** Применить правку раскладки: она ложится на лицо верхнего тайла. */
    public void applyTopFaceDelta(int delta) {
        topFaceDelta = delta;
        if (!flipped) {
            kelium = topFaceKelium();
        }
    }

    /** Точная копия тайла (для копии состояния при просчёте вперёд). */
    public SpawnTile copy() {
        SpawnTile t = new SpawnTile(isStart, faceKelium, backKelium, stack);
        t.kelium = kelium;
        t.flipped = flipped;
        t.stack = stack;
        t.topFaceDelta = topFaceDelta;
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
            // ВЕРХНИЙ ТАЙЛ УШЁЛ — вместе с ним уходит и правка раскладки:
            // она принадлежала его лицу, а не гексу. Нижний тайл ложится
            // напечатанным.
            topFaceDelta = 0;
            kelium = faceKelium;
            return true;
        }
        return false;
    }
}
