package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.engine.Scoring;
import kelium.support.Fix;

/**
 * ЖЕТОНЫ ПОБЕДНЫХ ОЧКОВ — золотые звёзды (компонент, 16.08.2026).
 *
 * <p>Очко приходит двумя разными путями, и разница физическая. Одни очки игрок
 * ПОЛУЧАЕТ ЖЕТОНОМ прямо в партии — выработал оборот тайла зарождения, выполнил
 * задание с очками, сдал первую часть супер задания, — и они уже не отнимаются.
 * Другие считаются по столу только в конце — с 1.35.0 это шаги треков,
 * установленный арсенал и множитель супер задания. За них жетонов не выдают,
 * потому что до конца партии их ещё можно потерять.
 *
 * <p>Тест сторожит именно эту границу: звезда обязана появляться там, где очко
 * выдано насовсем, и НЕ появляться там, где очко пока предварительное.
 */
class StarTokensTest {

    /**
     * Ресурсы на руках звёзд не дают. С 1.35.0 они не дают и очков: монеты и
     * трофеи убраны из базового подсчёта и переехали множителями в супер задания
     * «Казна» (s5_13) и «Склад лома» (s5_14). Граница, которую сторожит тест, от
     * этого не изменилась.
     */
    @Test
    void pointsCountedFromResourcesGiveNoStars() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        int before = Scoring.starTokens(s, 0);

        p.resources.add(Resource.COIN, 50);
        p.resources.add(Resource.TROPHY, 30);
        Map<String, Integer> vp = Scoring.scorePlayer(s, 0);
        assertEquals(0, vp.get("coins") + vp.get("trophy"),
            "с 1.35.0 монеты и трофеи базовых очков не дают — они на картах супер заданий");
        assertEquals(before, Scoring.starTokens(s, 0),
            "но жетонов за них не выдают: до конца партии эти очки можно потерять");
    }

    /** Выработанный ОБОРОТ тайла зарождения — звезда, и сразу. */
    @Test
    void aFinishedSpawnTileBackHandsOutAStar() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        int before = Scoring.starTokens(s, 0);
        p.claimedNormalTiles += 1;
        assertEquals(before + 1, Scoring.starTokens(s, 0),
            "последний келемий с оборота большого тайла = 1 жетон победного очка");
    }

    /** Задание, награда которого — очки, тоже выдаёт звезду. */
    @Test
    void anObjectiveThatPaysPointsHandsOutStars() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        int before = Scoring.starTokens(s, 0);
        p.objectiveCardVp += 2;
        assertEquals(before + 2, Scoring.starTokens(s, 0),
            "два очка от задания — две звезды перед игроком");
    }

    /**
     * Звёзды — ЧАСТЬ итога, а не отдельный счёт. Иначе на столе лежало бы одно,
     * а в отчёте стояло другое: ровно тот сорт расхождения, который в этом
     * движке уже случался.
     */
    @Test
    void starsAreCountedInsideTheTotalNotBesideIt() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        int totalBefore = Scoring.scorePlayer(s, 0).get("total");
        p.claimedNormalTiles += 1;
        p.objectiveCardVp += 3;
        int totalAfter = Scoring.scorePlayer(s, 0).get("total");
        assertEquals(totalBefore + 4, totalAfter,
            "четыре новые звезды обязаны прибавить ровно четыре очка к итогу");
        assertTrue(Scoring.starTokens(s, 0) >= 4, "и все четыре — на столе");
    }

    /**
     * Жетон уничтожения ЦУ и золотой модуль звездой НЕ дублируются: они сами по
     * себе физические жетоны, и второй жетон за то же очко за столом означал бы
     * двойной счёт.
     */
    @Test
    void componentsThatAreAlreadyTokensAreNotDoubledWithStars() {
        assertTrue(!Scoring.STAR_TOKEN_SOURCES.contains("cu_tokens"),
            "жетон уничтожения ЦУ — сам себе жетон");
        assertTrue(!Scoring.STAR_TOKEN_SOURCES.contains("gold_modules"),
            "золотой модуль — сам себе жетон");
        assertTrue(!Scoring.STAR_TOKEN_SOURCES.contains("tech"),
            "шаги треков видны фишкой на планшете, звёзд за них не выдают");
    }
}
