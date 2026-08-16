package kelium.cards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.engine.ability.Abilities;
import kelium.engine.cards.ArsenalCard;
import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;

/**
 * ДОГОВОР КАЖДОЙ КАРТЫ АРСЕНАЛА — проверка на всех 24 картах набора.
 *
 * <p>У арсенала своя главная беда, отличная от заданий: карту не изымают и не
 * ломают, её просто НЕ СТАВЯТ. Замер 15.08.2026: за партию устанавливается 0.73
 * карты и сжигается 1.09 — то есть карту чаще выбрасывают ради разовой прибавки,
 * чем играют её низом. Причина не в силе карт, а в том, что бот не мог оценить
 * установку: сжечь даёт понятную выгоду сейчас, установка — непонятную потом.
 *
 * <p>Поэтому проверяем не только «работает ли карта», но и то, что она способна
 * ОТВЕТИТЬ НА ВОПРОС «стоит ли она сейчас» — и отвечает по-разному в разных
 * положениях. Оценка, одинаковая всегда, бесполезна ровно так же, как её
 * отсутствие.
 */
class ArsenalContractTest {

    private static GameState freshGame() {
        return Setup.buildGame(GameConfig.build(4, 4_242L));
    }

    private static List<ArsenalCard> arsenal() {
        GameConfig cfg = GameConfig.build(4, 1L);
        CardRegistry.bindAll("arsenal", cfg.content.get("arsenal").entries);
        List<ArsenalCard> out = new ArrayList<>();
        for (Card c : CardRegistry.all()) {
            if (c instanceof ArsenalCard a) {
                out.add(a);
            }
        }
        return out;
    }

    @TestFactory
    List<DynamicTest> каждаяКартаАрсеналаСоблюдаетДоговор() {
        List<DynamicTest> tests = new ArrayList<>();
        for (ArsenalCard card : arsenal()) {
            tests.add(DynamicTest.dynamicTest(card.id() + " " + card.name(), () -> {
                GameState s = freshGame();
                TestCardContext ctx = new TestCardContext(s, 0);

                // 1. У карты есть имя из данных и человеческое описание.
                assertNotNull(card.name(), "карта без имени: " + card.id());
                assertFalse(card.name().equals(card.id()),
                    "карта " + card.id() + " не подхватила имя из данных");
                assertFalse(card.describe().isBlank(),
                    "карта " + card.id() + " не описана");

                // 2. Низ карты называет способность, и она ЕСТЬ в движке.
                //    Именно этой проверки не хватало, когда 24 карты набора 2.0
                //    лежали помеченные как неисполняемые, хотя код был написан.
                // У НИЗА ДВЕ ЗАКОННЫЕ ФОРМЫ (с версии 2.1.0):
                //   способность — карта меняет правило, пока лежит установленной;
                //   scoring — карта-цель, она правил не меняет, а платит очками
                //             в конце партии за накопленную комбинацию.
                // Третьей формы быть не должно: низ, который не делает ни того,
                // ни другого, — это карта-пустышка.
                String pid = card.passiveId();
                boolean goal = card.data().get("bottom") instanceof java.util.Map<?, ?> b
                    && b.get("scoring") instanceof java.util.Map<?, ?>;
                assertTrue(pid != null || goal,
                    "у карты " + card.id() + " («" + card.name() + "») низ не делает "
                        + "ничего: нет ни способности, ни условия на очки");
                if (pid != null) {
                    assertNotNull(Abilities.byId(pid),
                        "карта " + card.id() + " («" + card.name() + "») называет "
                            + "способность " + pid + ", которой нет в реестре движка");
                }

                // 3. Оценка полезности в границах и не бессмысленна.
                double install = card.usefulness(ctx, true);
                double burn = card.usefulness(ctx, false);
                assertTrue(install >= 0.0 && install <= 1.0,
                    "оценка установки вне границ 0..1: " + install);
                assertTrue(burn >= 0.0 && burn <= 1.0,
                    "оценка утиля вне границ 0..1: " + burn);
            }));
        }
        return tests;
    }

    /**
     * ОЦЕНКА ОБЯЗАНА МЕНЯТЬСЯ ОТ ПОЛОЖЕНИЯ.
     *
     * <p>Главная проверка этого набора. Карта, которая всегда стоит одинаково,
     * ничем не лучше рукописного числа — а именно рукописные числа и привели к
     * тому, что арсенал почти не ставят. Проверяем на двух заведомо разных
     * положениях: «всего вдоволь» и «пусто и последний раунд».
     */
    @Test
    void оценкаКартыЗависитОтПоложения() {
        GameState rich = freshGame();
        rich.round = 1;
        rich.player(0).resources.add(kelium.core.Resource.COIN, 20);

        GameState poor = freshGame();
        poor.round = 8;                       // последний раунд: постоянные способности не успеют
        poor.player(0).resources.setAmmo(0);
        poor.player(0).resources.setKelium(0);

        int differing = 0;
        for (ArsenalCard card : arsenal()) {
            double a = card.usefulness(new TestCardContext(rich, 0), true);
            double b = card.usefulness(new TestCardContext(poor, 0), true);
            if (Math.abs(a - b) > 0.01) {
                differing++;
            }
        }
        assertTrue(differing >= arsenal().size() / 2,
            "оценка зависит от положения лишь у " + differing + " карт из "
                + arsenal().size() + " — значит большинство карт оцениваются "
                + "одинаково всегда, и толку от такой оценки нет");
    }
}
