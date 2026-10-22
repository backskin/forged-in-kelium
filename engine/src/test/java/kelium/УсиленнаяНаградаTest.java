package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.Objectives;
import kelium.engine.Setup;

/**
 * СТОРОЖ ПРАВИЛА «УСИЛЕННАЯ НАГРАДА — ВМЕСТО БАЗОВОЙ» (дизайнер, 16.09.2026).
 *
 * <p>Выполнив усиленное требование, игрок получает ОДНУ награду на выбор:
 * базовую или усиленную. Прежде награды складывались, и усиление было чистой
 * прибавкой — брать его было выгодно всегда, а решения на карте не стояло.
 *
 * <p>Почему сторож нужен именно здесь. Сложение возвращается одной строкой и
 * ничего не ломает: партия идёт, тесты зелёные, просто задания платят вдвое.
 * Заметить это можно только по замерам через неделю — или вот этой проверкой.
 */
class УсиленнаяНаградаTest {

    private static GameState игра(long seed) {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, seed, null, null));
    }

    /** Все карты заданий, у которых есть и усиление, и обе награды. */
    private static List<String> сРазвилкой(GameState s) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> c : Ctx.cards(s, "objectives").entries) {
            if (c.get("enhanced") instanceof Map
                    && c.get("base_reward") instanceof Map<?, ?> b && !b.isEmpty()
                    && c.get("special_reward") instanceof Map<?, ?> sp && !sp.isEmpty()) {
                out.add(String.valueOf(c.get("id")));
            }
        }
        return out;
    }

    @Test
    void развилкаВКолодеЕсть() {
        GameState s = игра(7L);
        assertFalse(сРазвилкой(s).isEmpty(),
            "в колоде нет ни одной карты с усилением и двумя наградами — "
                + "сторожить нечего, проверь каталог");
    }

    /**
     * ОБЫЧНОЕ ВЫПОЛНЕНИЕ НЕ ВЫДАЁТ УСИЛЕННУЮ НАГРАДУ НИКОГДА — независимо от
     * того, выполнено усиленное требование или нет. Это и есть та половина
     * правила, которую ломает возврат сложения.
     */
    @Test
    void базовоеВыполнениеНеДаётУсиленнойНаграды() {
        GameState s = игра(11L);
        PlayerState p = s.player(0);
        List<String> плохие = new ArrayList<>();
        for (String cid : сРазвилкой(s)) {
            p.objectiveHand.clear();
            p.objectiveHand.add(cid);
            Map<String, Object> дано = Objectives.playObjective(s, 0, s.journal, cid, ev -> { });
            Object особая = дано.get("special");
            if (особая instanceof Map<?, ?> m && !m.isEmpty()) {
                плохие.add(cid + " -> " + m);
            }
        }
        assertTrue(плохие.isEmpty(),
            "обычное выполнение выдало усиленную награду: " + плохие);
    }

    /**
     * УСИЛЕННОЕ ВЫПОЛНЕНИЕ НЕ ВЫДАЁТ БАЗОВУЮ, КОГДА УСИЛЕНИЕ СОСТОЯЛОСЬ.
     *
     * <p>Если усиление не состоялось (требование не выполнено в этом состоянии),
     * карта честно платит базовой наградой — игрок не должен остаться ни с чем.
     * Обе награды сразу не выдаются ни в одном из двух случаев, и проверяется
     * именно это.
     */
    @Test
    void обеНаградыСразуНеВыдаютсяНикогда() {
        GameState s = игра(13L);
        PlayerState p = s.player(0);
        List<String> плохие = new ArrayList<>();
        for (String cid : сРазвилкой(s)) {
            p.objectiveHand.clear();
            p.objectiveHand.add(cid);
            Map<String, Object> дано = Objectives.playObjective(
                s, 0, s.journal, cid, ev -> { }, true);
            boolean база = дано.get("base") instanceof Map<?, ?> b && !b.isEmpty();
            boolean особая = дано.get("special") instanceof Map<?, ?> m && !m.isEmpty();
            if (база && особая) {
                плохие.add(cid + " -> база " + дано.get("base")
                    + " и усиленная " + дано.get("special"));
            }
        }
        assertTrue(плохие.isEmpty(),
            "усиленное выполнение выдало ОБЕ награды: " + плохие);
    }
}
