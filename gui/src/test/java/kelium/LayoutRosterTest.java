package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.gui.LayoutEditor;

/**
 * Правила конструктора, заданные дизайнером 12.08.2026:
 *
 * <ol>
 *   <li>состав определяется ЧИСЛОМ расставленных стартов, отдельной настройки нет;</li>
 *   <li>стартов должно быть не меньше двух;</li>
 *   <li>между стартами — не меньше ТРЁХ гексов расстояния (два гекса между ними),
 *       иначе это КРАСНОЕ замечание;</li>
 *   <li>у каждого игрока должен быть наземный путь до каждого другого; путь рвут
 *       нейтральные здания, тайлы зарождения и запретные гексы — это ЖЁЛТОЕ
 *       замечание.</li>
 * </ol>
 */
class LayoutRosterTest {

    /** Прямоугольная заготовка поля width×height обычных гексов. */
    private static LayoutEditor.Model field(int width, int height) {
        LayoutEditor.Model m = new LayoutEditor.Model();
        for (int r = 0; r < height; r++) {
            for (int q = 0; q < width; q++) {
                m.hexes.put(LayoutEditor.Model.key(q, r), new LayoutEditor.LHex(q, r));
            }
        }
        return m;
    }

    private static LayoutEditor.LHex at(LayoutEditor.Model m, int q, int r) {
        return m.get(q, r);
    }

    private static void start(LayoutEditor.Model m, int q, int r, int seat) {
        LayoutEditor.LHex h = at(m, q, r);
        h.content = "player_start";
        h.seat = seat;
    }

    private static boolean has(List<LayoutEditor.Issue> issues, int level, String part) {
        return issues.stream().anyMatch(i -> i.level() == level && i.text().contains(part));
    }

    @Test
    void rosterIsTheNumberOfPlacedStarts() {
        LayoutEditor.Model m = field(8, 4);
        assertEquals(0, m.players(), "без стартов состава нет");
        start(m, 0, 0, 0);
        assertEquals(1, m.players());
        start(m, 6, 3, 1);
        assertEquals(2, m.players());
        start(m, 3, 0, 2);
        assertEquals(3, m.players(), "третий флажок — раскладка на троих");
    }

    @Test
    void singleStartIsAnError() {
        LayoutEditor.Model m = field(8, 4);
        start(m, 0, 0, 0);
        assertTrue(has(LayoutEditor.validate(m), 2, "минимум два"),
            "с одним стартом раскладка не игровая");
    }

    @Test
    void startsCloserThanThreeHexesAreAnError() {
        LayoutEditor.Model m = field(8, 4);
        start(m, 0, 0, 0);
        start(m, 2, 0, 1);      // расстояние 2 — мало
        List<LayoutEditor.Issue> issues = LayoutEditor.validate(m);
        assertTrue(has(issues, 2, "не меньше 3"),
            "между стартами должно быть не меньше трёх гексов");

        LayoutEditor.Model ok = field(8, 4);
        start(ok, 0, 0, 0);
        start(ok, 3, 0, 1);      // ровно 3 — годится
        assertFalse(has(LayoutEditor.validate(ok), 2, "не меньше 3"));
        assertTrue(has(LayoutEditor.validate(ok), 0, "разнесены на 3+"));
    }

    @Test
    void twoFreeNeighboursAtTheStartAreEnough() {
        // Порог понижен дизайнером с трёх до двух: старт в углу поля с двумя
        // обычными соседями годится, с одним — нет.
        LayoutEditor.Model m = field(5, 2);
        start(m, 0, 0, 0);
        start(m, 4, 1, 1);
        assertFalse(has(LayoutEditor.validate(m), 2, "нужно ≥2"),
            "у угловых стартов по два обычных соседа — этого достаточно");

        // Отрезаем углу всё, кроме одного соседа: остаётся один — уже мало.
        at(m, 1, 0).content = "forbidden";
        assertTrue(has(LayoutEditor.validate(m), 2, "нужно ≥2"),
            "с одним свободным соседом разворачиваться негде");
    }

    @Test
    void neutralWallsCutTheGroundPathAndThatIsAWarning() {
        // Узкий коридор: 5 гексов в ряд, старты по краям. Путь есть.
        LayoutEditor.Model m = field(5, 1);
        start(m, 0, 0, 0);
        start(m, 4, 0, 1);
        assertTrue(has(LayoutEditor.validate(m), 0, "наземный путь до каждого"),
            "в пустом коридоре путь обязан находиться");

        // Перекрываем стенку между гексами 2 и 3 нейтральным зданием.
        // Сторона 0 — направление {+1,0}; ребро стороны 0 начинается в углу 2.
        at(m, 2, 0).neutrals.add(new LayoutEditor.Neutral(false, 2));
        List<LayoutEditor.Issue> issues = LayoutEditor.validate(m);
        assertTrue(has(issues, 1, "не связаны НАЗЕМНЫМ путём"),
            "перекрытая нейтралом стенка рвёт путь — и это ЖЁЛТОЕ замечание");
        assertFalse(has(issues, 2, "не связаны НАЗЕМНЫМ путём"),
            "разрыв пути — предупреждение, а не ошибка");
    }
}
