package kelium.engine;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.dataio.Ctx;

/**
 * TokenContainers — СТАРЫЙ механизм контейнеров: ЖЕТОНЫ НА ГЕКСАХ.
 *
 * <p>Включается версией правил ({@code containers.mode: tokens}, ruleset
 * 1.6.0-c1 от 12.08.2026) — временный откат, чтобы проверить, насколько сильно
 * «Контейнеры 2.0» (печатные на блоках) меняют игру. Правила режима:
 *
 * <ul>
 *   <li><b>подготовка:</b> по одному жетону на КАЖДЫЙ полностью пустой гекс —
 *       без жетонов игроков, без нейтральных зданий, без тайла зарождения.
 *       Печатная раскладка контейнеров из файлов поля ИГНОРИРУЕТСЯ;</li>
 *   <li><b>каждое Обновление:</b> жетон появляется на каждом гексе, где нет
 *       жетонов игроков и нет тайла зарождения. Нейтральное здание не мешает;</li>
 *   <li><b>сбор:</b> только ВОЙСКОМ, вошедшим на гекс. Здание не собирает;</li>
 *   <li><b>стройка на гексе с жетоном СЖИГАЕТ его</b> — контейнер теряется.</li>
 * </ul>
 *
 * <p>В основном режиме ({@code printed}) все методы ничего не делают, поэтому
 * вызовы можно ставить безусловно.
 */
public final class TokenContainers {

    private TokenContainers() {
    }

    /** Включён ли старый режим жетонов. */
    public static boolean enabled(GameState s) {
        return "tokens".equals(String.valueOf(
            Ctx.rules(s).get("containers.mode", "printed")));
    }

    /**
     * РАСКЛАДКА ПРИ ПОДГОТОВКЕ: жетон на каждый ПОЛНОСТЬЮ пустой гекс.
     * Печатные ячейки при этом гасятся — иначе на поле были бы оба механизма.
     *
     * @return сколько жетонов легло
     */
    public static int layoutAtSetup(GameState s) {
        if (!enabled(s)) {
            return 0;
        }
        int laid = 0;
        for (Hex h : s.field.hexes.values()) {
            h.containerCell = -1;               // печатных контейнеров в этом режиме нет
            h.containerTokens = 0;
            if (isCompletelyEmpty(h)) {
                h.containerTokens = 1;
                laid++;
            }
        }
        return laid;
    }

    /**
     * РАСКЛАДКА В ОБНОВЛЕНИЕ: жетон на каждый гекс без жетонов игроков и без
     * тайла зарождения. Нейтральные здания не мешают. Уже лежащий жетон не
     * удваивается — на гексе он один.
     *
     * @return сколько новых жетонов легло
     */
    public static int layoutOnRefresh(GameState s) {
        if (!enabled(s)) {
            return 0;
        }
        int laid = 0;
        for (Hex h : s.field.hexes.values()) {
            if (h.hasSpawnTile() || !h.groundTokens.isEmpty() || h.airToken != null) {
                continue;
            }
            if (h.containerTokens == 0) {
                h.containerTokens = 1;
                laid++;
            }
        }
        return laid;
    }

    /**
     * ВОЙСКО ВОШЛО НА ГЕКС — забирает жетон контейнера, если он там лежит.
     *
     * @return сколько контейнеров получено (0 или 1)
     */
    public static int onUnitEntered(GameState s, PlayerState p, String hexId) {
        if (!enabled(s) || hexId == null) {
            return 0;
        }
        Hex h = s.field.get(hexId);
        if (h == null || h.containerTokens <= 0) {
            return 0;
        }
        h.containerTokens = 0;
        return Storage.addContainersCapped(s, p, 1);
    }

    /**
     * СТРОЙКА НА ГЕКСЕ СЖИГАЕТ жетон: контейнер не достаётся никому.
     *
     * @return true, если жетон сгорел
     */
    public static boolean onBuildingPlaced(GameState s, BuildingToken b) {
        if (!enabled(s) || b == null || b.hexId == null) {
            return false;
        }
        Hex h = s.field.get(b.hexId);
        if (h == null || h.containerTokens <= 0) {
            return false;
        }
        h.containerTokens = 0;
        return true;
    }

    /** Полностью пустой гекс: ни жетонов, ни нейтралов, ни тайла зарождения. */
    private static boolean isCompletelyEmpty(Hex h) {
        return !h.hasSpawnTile() && !h.hasNeutral()
            && h.groundTokens.isEmpty() && h.airToken == null;
    }

    /** Сколько жетонов контейнеров сейчас лежит на поле (для замеров). */
    public static int onField(GameState s) {
        int n = 0;
        for (Hex h : s.field.hexes.values()) {
            n += h.containerTokens;
        }
        return n;
    }
}
