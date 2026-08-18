package kelium.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import kelium.core.GameState;
import kelium.core.Resource;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.ObjectiveCard;

/**
 * ДОГОВОР КАЖДОЙ КАРТЫ ЗАДАНИЯ — проверка НА ВСЕХ картах каталога сразу.
 *
 * <p>Заказ дизайнера 15.08.2026: «на каждую карту нужен такой тест». Тест
 * порождается по числу карт: добавили карту в данные — появилась и проверка, и
 * забыть про неё нельзя.
 *
 * <p>Что проверяется у каждой карты:
 *
 * <ol>
 *   <li><b>на пустом столе задание НЕ выполнено.</b> Это и есть отрицательная
 *       проверка «состояние не подходит — не выполняется». Партия только началась,
 *       игрок ничего не сделал: любая карта, которая при этом уже выполнена,
 *       выполняется даром — а такие в каталоге были;</li>
 *   <li><b>прогресс лежит в границах 0..1</b> и на пустом столе не равен единице;</li>
 *   <li><b>карта говорит, чего не хватает</b> — непустой строкой, иначе бот не
 *       сможет поставить задание целью;</li>
 *   <li><b>усиленное требование не легче обычного.</b> Логическая ловушка, в
 *       которую легко попасть: если усиленное выполнено, а обычное нет — карта
 *       сломана, потому что усиление по определению строже.</li>
 * </ol>
 */
class ObjectiveContractTest {

    private static GameState freshGame() {
        return Setup.buildGame(GameConfig.build(4, 12_345L));
    }

    private static List<ObjectiveCard> objectives() {
        GameConfig cfg = GameConfig.build(4, 1L);
        CardRegistry.bindAll("objectives", cfg.content.get("objectives").entries);
        List<ObjectiveCard> out = new ArrayList<>();
        for (Card c : CardRegistry.all()) {
            if (c instanceof ObjectiveCard o) {
                out.add(o);
            }
        }
        return out;
    }

    @TestFactory
    List<DynamicTest> каждаяКартаСоблюдаетДоговор() {
        GameState s = freshGame();
        List<DynamicTest> tests = new ArrayList<>();
        for (ObjectiveCard card : objectives()) {
            tests.add(DynamicTest.dynamicTest(card.id() + " " + card.name(), () -> {
                TestCardContext ctx = new TestCardContext(s, 0);

                // КАРТА-ЖЕРТВА — ОСОБЫЙ СЛУЧАЙ. Её satisfied() трюивально
                // истинно по замыслу (тот же смысл, что у старого предиката
                // sacrifice_paid): настоящий гейт — не условие, а возможность
                // заплатить, и её проверяет и списывает движок отдельно
                // (Objectives.canPaySacrifice, по записи sacrifice в данных
                // карты), а не satisfied(). Проверка «не выполнено даром» здесь
                // была бы проверкой не того контракта.
                boolean жертва = card instanceof kelium.cards.objectives.ЗаданиеВКоде зк
                    && зк.лицо().природа() == kelium.cards.objectives.Лицо.Природа.ЖЕРТВА;

                // 1. На пустом столе выполнено быть не должно — кроме карт-жертв.
                if (!жертва) {
                    assertFalse(card.satisfied(ctx),
                        "задание " + card.id() + " («" + card.name() + "») выполнено на "
                            + "ПУСТОМ столе — значит оно выполняется даром");
                }

                // 2. Прогресс в границах и не единица (для жертв это условие не
                // применимо: прогресс у них двоичный по устройству оплаты).
                double p = card.progress(ctx);
                assertTrue(p >= 0.0 && p <= 1.0,
                    "прогресс вне границ 0..1: " + p);
                if (!жертва) {
                    assertTrue(p < 1.0,
                        "прогресс равен единице при невыполненном задании");
                }

                // 3. Карта объясняет, чего не хватает — кроме карт-жертв: там
                // условие тривиально истинно, и needed() законно пуст ("готово").
                if (!жертва) {
                    String need = card.needed(ctx);
                    assertNotNull(need, "карта не сказала, чего не хватает");
                    assertFalse(need.isBlank(), "пустое объяснение, чего не хватает");
                    assertFalse("готово".equals(need),
                        "карта говорит «готово», хотя условие не выполнено");
                }

                // 4. Усиленное не легче обычного.
                assertFalse(card.satisfiedEnhanced(ctx) && !card.satisfied(ctx),
                    "усиленное требование выполнено, а обычное нет — усиление "
                        + "не может быть легче обычной ветки");
            }));
        }
        return tests;
    }

    /**
     * НАГРАДА ВЫДАЁТСЯ РОВНО ТА, ЧТО НАПИСАНА НА КАРТЕ.
     *
     * <p>Вторая половина заказа: мало проверить, что задание выполнилось — надо
     * убедиться, что игрок получил обещанное. Проверяем на ПУСТОМ складе, где
     * места заведомо хватает: вопрос вместимости — предмет отдельного теста.
     */
    @TestFactory
    List<DynamicTest> каждаяКартаВыдаётОбещанноеСРовноТемиЧислами() {
        List<DynamicTest> tests = new ArrayList<>();
        for (ObjectiveCard card : objectives()) {
            tests.add(DynamicTest.dynamicTest(card.id() + " награда", () -> {
                GameState s = freshGame();
                TestCardContext ctx = new TestCardContext(s, 0);
                // Склад расчищаем: иначе часть награды честно упрётся в потолок,
                // и тест поймает вместимость вместо награды.
                s.player(0).resources.setKelium(0);
                s.player(0).resources.setAmmo(0);

                card.reward(ctx, false);

                Object node = card.data().get("base_reward");
                if (!(node instanceof Map<?, ?> reward)) {
                    return;                  // у карты нет базовой награды — нечего сверять
                }
                check(ctx, reward, "coin", Resource.COIN, card);
                check(ctx, reward, "ammo", Resource.AMMO, card);
                check(ctx, reward, "debris", Resource.DEBRIS, card);
                check(ctx, reward, "kelium", Resource.KELIUM, card);
            }));
        }
        return tests;
    }

    /**
     * СКОЛЬКО ДОЛЖНО ЛЕЧЬ НА СКЛАД — правило дизайнера: «есть место — кладёт,
     * места нет — не получает».
     *
     * <p>Поэтому сверяем не с обещанием, а с {@code min(обещано, вместимость)}.
     * Требовать точного равенства было бы неверно: стартовый склад держит ВСЕГО
     * ДВА кубика, и карта, обещающая три боеприпаса, честно отдаёт два. Это не
     * ошибка выдачи, а работа склада — и именно её тест обязан подтверждать.
     *
     * <p>Отдельный вопрос — стоит ли печатать на карте награду, которая заведомо
     * не помещается в ранний склад. Это к дизайнеру, а не к движку: список таких
     * карт даёт стенд {@code kelium.ObjectiveRewards}.
     */
    private static void check(TestCardContext ctx, Map<?, ?> reward, String key,
                              Resource res, ObjectiveCard card) {
        int promised = reward.get(key) instanceof Number n ? n.intValue() : 0;
        int got = ctx.granted.getOrDefault(res, 0);
        int room = switch (res) {
            case AMMO -> kelium.engine.Storage.ammoMax(ctx.state(), ctx.me());
            case KELIUM -> kelium.engine.Storage.keliumMax(ctx.state(), ctx.me());
            case DEBRIS -> kelium.engine.Storage.debrisMax(ctx.state(), ctx.me());
            case COIN -> Integer.MAX_VALUE;       // монеты склад не занимают
        };
        int expected = Math.min(promised, Math.max(0, room));
        assertEquals(expected, got,
            "карта " + card.id() + " («" + card.name() + "») обещает " + promised
                + " " + key + ", на складе место под " + room + ", значит лечь должно "
                + expected + ", а легло " + got);
    }

    /**
     * УСИЛЕННАЯ НАГРАДА ИДЁТ ДОПОЛНИТЕЛЬНО К БАЗОВОЙ, а не вместо неё.
     *
     * <p>Тонкость, на которой легко потерять половину награды: в данных базовая
     * лежит в {@code base_reward}, усиленная — в {@code special_reward}, и по
     * правилам игрока, выполнившего усиленное требование, полагается и то и
     * другое.
     */
    @Test
    void усиленнаяНаградаНеЗаменяетБазовую() {
        for (ObjectiveCard card : objectives()) {
            if (!(card.data().get("base_reward") instanceof Map<?, ?> base)
                    || !(card.data().get("special_reward") instanceof Map<?, ?>)) {
                continue;
            }
            int promisedCoin = base.get("coin") instanceof Number n ? n.intValue() : 0;
            if (promisedCoin == 0) {
                continue;
            }
            GameState s = freshGame();
            TestCardContext ctx = new TestCardContext(s, 0);
            card.reward(ctx, true);
            assertTrue(ctx.granted.getOrDefault(Resource.COIN, 0) >= promisedCoin,
                "карта " + card.id() + " при УСИЛЕННОМ выполнении не выдала базовую "
                    + "награду: обещано минимум " + promisedCoin + " монет, выдано "
                    + ctx.granted.getOrDefault(Resource.COIN, 0));
        }
    }
}
