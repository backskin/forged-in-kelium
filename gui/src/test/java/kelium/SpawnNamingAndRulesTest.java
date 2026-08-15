package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.gui.LayoutEditor;

/**
 * МАЛОЕ и БОЛЬШОЕ ЗАРОЖДЕНИЕ (решение дизайнера 12.08.2026).
 *
 * <p>Названия: «малое зарождение» — лицо 3 / оборот 2 (прежний «стартовый тайл»,
 * путался со стартом игрока); «большое зарождение» — лицо 4 / оборот 3.
 *
 * <p>Требование «у каждого старта обязательно соседнее малое зарождение» СНЯТО.
 * Раскладки без соседних малых зарождений и вовсе без малых зарождений законны —
 * остаётся только ЛЁГКОЕ замечание (жёлтый уровень 1), но не ошибка (уровень 2).
 */
class SpawnNamingAndRulesTest {

    private static LayoutEditor.Model field(int width, int height) {
        LayoutEditor.Model m = new LayoutEditor.Model();
        for (int r = 0; r < height; r++) {
            for (int q = 0; q < width; q++) {
                m.hexes.put(LayoutEditor.Model.key(q, r), new LayoutEditor.LHex(q, r));
            }
        }
        return m;
    }

    private static void start(LayoutEditor.Model m, int q, int r, int seat) {
        LayoutEditor.LHex h = m.get(q, r);
        h.content = "player_start";
        h.seat = seat;
    }

    private static LayoutEditor.Model twoStarts() {
        LayoutEditor.Model m = field(9, 4);
        start(m, 0, 0, 0);
        start(m, 7, 3, 1);
        return m;
    }

    private static boolean any(List<LayoutEditor.Issue> issues, int level, String part) {
        return issues.stream().anyMatch(i -> i.level() == level && i.text().contains(part));
    }

    @Test
    void layoutWithoutAnySmallSpawnIsLegalWithALightNote() {
        LayoutEditor.Model m = twoStarts();
        m.get(3, 1).content = "kelium_tile";       // только большие зарождения
        m.get(5, 2).content = "kelium_tile";
        List<LayoutEditor.Issue> issues = LayoutEditor.validate(m);

        assertFalse(any(issues, 2, "зарожден"),
            "отсутствие малых зарождений больше НЕ ошибка: " + issues);
        assertTrue(any(issues, 1, "Малых зарождений нет вовсе"),
            "должно остаться лёгкое замечание: " + issues);
    }

    @Test
    void startWithoutAdjacentSmallSpawnIsOnlyAWarning() {
        LayoutEditor.Model m = twoStarts();
        m.get(4, 1).content = "spawn_start";  // малое зарождение ДАЛЕКО от стартов
        m.get(3, 2).content = "kelium_tile";
        List<LayoutEditor.Issue> issues = LayoutEditor.validate(m);

        assertFalse(any(issues, 2, "малого зарождения"),
            "соседство малого зарождения со стартом больше не обязательно: " + issues);
        assertTrue(any(issues, 1, "нет соседнего малого зарождения"),
            "но лёгкое замечание остаётся: " + issues);
    }

    /** ПКМ в режиме зарождений снимает тайл (просьба дизайнера 12.08.2026). */
    @Test
    void spawnToolsRemoveTileOnRightClick() {
        LayoutEditor.Model m = twoStarts();
        LayoutEditor.LHex h = m.get(3, 1);
        h.content = "spawn_start";
        h.stack = 2;
        h.keliumDelta = 2;

        assertTrue(LayoutEditor.clearSpawn(h), "ПКМ снимает зарождение");
        assertTrue("normal".equals(h.content), "гекс стал обычным: " + h.content);
        assertTrue(h.stack == 1 && h.keliumDelta == 0,
            "стопка и правка келемия сброшены вместе с тайлом");

        assertFalse(LayoutEditor.clearSpawn(h),
            "на гексе без зарождения снимать нечего");
    }

    @Test
    void validatorSpeaksTheNewNames() {
        // В журнале замечаний тайлы зовутся по-новому: «малое» и «большое».
        LayoutEditor.Model m = twoStarts();
        m.get(3, 1).content = "kelium_tile";
        List<LayoutEditor.Issue> issues = LayoutEditor.validate(m);
        assertTrue(issues.stream().anyMatch(i -> i.text().contains("больших зарождений")
                || i.text().contains("Больших зарождений")),
            "большие зарождения названы по-новому: " + issues);
        assertTrue(issues.stream().anyMatch(i -> i.text().contains("алых зарождений")
                || i.text().contains("малого зарождения")),
            "малые зарождения названы по-новому: " + issues);
    }
}
