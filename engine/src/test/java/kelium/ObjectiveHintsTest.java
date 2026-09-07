package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitType;
import kelium.dataio.Ctx;
import kelium.engine.ObjectiveHints;
import kelium.support.Fix;

/**
 * ИНДИКАТОРЫ ЗАДАНИЙ (заказ дизайнера 17.08.2026).
 *
 * <p>Проверяется ровно то, ради чего они заведены, и ничего сверх: горит ли
 * «ГОТОВО» тогда и только тогда, когда карту можно закрыть СПЕЦ-действием прямо
 * сейчас; загорается ли «ДОСТИЖИМО» вместе с ИНСТРУКЦИЕЙ, когда до выполнения
 * остаётся одно действие этого хода; гаснет ли инструкция, когда нужного
 * действия в приказе нет; считается ли цена награды так, что бот может сравнить
 * две готовые карты между собой.
 *
 * <p>Тесты намеренно строят сцену руками, а не гоняют партию: индикатор — это
 * функция от состояния и журнала, и проверять её надо на известном состоянии.
 */
class ObjectiveHintsTest {

    /**
     * СВОД С ПРЕДИКАТНЫМИ ЗАДАНИЯМИ (objectives 1.11.0), а не свод по умолчанию.
     *
     * <p>В действующем каталоге условия заданий проверяет САМА КАРТА
     * ({@code checked_by: card}), то есть класс из модуля {@code cards}. Модуля
     * этого на класспасе тестов движка нет и быть не должно — связь между ними
     * только через ServiceLoader во время работы. Поэтому здесь проверяется
     * ветка индикаторов «условие задано предикатом»; ветка «условие в карте»
     * сторожится в модуле карт, где класс доступен.
     */
    private static final String СВОД = "1.26.0";

    /** Все действия игры — сцена, где приказ ничего не ограничивает. */
    private static final Set<String> ВСЕ = Set.of("assembly", "mining", "build",
        "energy_swap", "movement", "combat", "market", "science");

    private static ObjectiveHints.Hint hint(GameState s, String cid,
                                            Set<String> actions, int left) {
        return ObjectiveHints.forCard(s, 0, s.journal, cid, actions, left);
    }

    /** Положить карту в руку игрока 0 (и убрать всё остальное). */
    private static void hand(GameState s, String cid) {
        PlayerState p = s.player(0);
        p.objectiveHand.clear();
        p.objectiveHand.add(cid);
    }

    // ==================================================================
    //  ГОТОВО
    // ==================================================================

    @Test
    void готовоГоритТолькоКогдаТребованиеВыполнено() {
        GameState s = Fix.game(СВОД, 4, 7L);
        Fix.turn(s, 0);
        hand(s, "n3");   // «Патроны»: имей не меньше 3 боеприпасов
        PlayerState p = s.player(0);

        p.resources.setAmmo(0);
        ObjectiveHints.Hint cold = hint(s, "n3", ВСЕ, 2);
        assertNotNull(cold);
        assertFalse(cold.ready(), "боеприпасов нет — «ГОТОВО» гореть не должно");

        p.resources.setAmmo(3);
        ObjectiveHints.Hint warm = hint(s, "n3", ВСЕ, 2);
        assertTrue(warm.ready(), "три боеприпаса есть — карта играется прямо сейчас");
        assertFalse(warm.reachable(),
            "«ДОСТИЖИМО» — про то, что ещё НЕ готово; на готовой карте оно не горит");
    }

    @Test
    void уНачальнойКартыПотолокРавенБазовойНаграде() {
        GameState s = Fix.game(СВОД, 4, 7L);
        Fix.turn(s, 0);
        hand(s, "n3");
        s.player(0).resources.setAmmo(3);
        ObjectiveHints.Hint h = hint(s, "n3", ВСЕ, 2);
        // У начальных заданий усиления нет ни у одного (правило каталога 10.0),
        // значит «усиленно готово» горит вместе с базовым, а потолок совпадает.
        assertTrue(h.enhancedReady(), "у начальной карты усиления нет — потолок достигнут сразу");
        assertEquals(h.maxValue(), h.value(), 1e-9);
    }

    // ==================================================================
    //  ДОСТИЖИМО + ИНСТРУКЦИЯ
    // ==================================================================

    @Test
    void достижимоДаётИнструкциюСНазваниемДействия() {
        GameState s = Fix.game(СВОД, 4, 7L);
        Fix.turn(s, 0);
        hand(s, "n3");
        s.player(0).resources.setAmmo(0);

        ObjectiveHints.Hint h = hint(s, "n3", ВСЕ, 2);
        assertTrue(h.reachable(), "боеприпасы добираются Сборкой — путь есть");
        assertFalse(h.plans().isEmpty(), "путь есть — значит есть и план");
        ObjectiveHints.Plan plan = h.plans().get(0);
        assertFalse(plan.steps().isEmpty());
        assertTrue(ВСЕ.contains(plan.steps().get(0).action()),
            "шаг плана называет действие игры, а не выдумку: " + plan.steps().get(0));
        assertTrue(plan.summary().contains(plan.steps().get(0).action()),
            "строка-инструкция называет то же действие: " + plan.summary());
    }

    @Test
    void безНужногоДействияВПриказеПланаНет() {
        GameState s = Fix.game(СВОД, 4, 7L);
        Fix.turn(s, 0);
        hand(s, "n3");
        s.player(0).resources.setAmmo(0);

        // Боеприпасы даёт Сборка и Рынок. Вскрыт приказ, где нет ни того, ни
        // другого — врать про достижимость нельзя.
        ObjectiveHints.Hint h = hint(s, "n3", Set.of("combat", "movement"), 2);
        assertFalse(h.reachable(),
            "нужного действия в приказе нет — «ДОСТИЖИМО» гореть не должно");
        assertTrue(h.plans().isEmpty());
    }

    @Test
    void планДлиннееОставшихсяДействийНеПредлагается() {
        GameState s = Fix.game(СВОД, 4, 7L);
        Fix.turn(s, 0);
        hand(s, "o21");   // «Первая кровь»: в этот ход уничтожить 2 жетона
        // Бой — действие штучное: два уничтожения это, как правило, два боя.
        // Действие осталось одно — плана быть не должно.
        ObjectiveHints.Hint tight = hint(s, "o21", ВСЕ, 1);
        assertTrue(tight.plans().isEmpty(),
            "на одно оставшееся действие двухшаговый план не предлагается");

        ObjectiveHints.Hint roomy = hint(s, "o21", ВСЕ, 2);
        assertFalse(roomy.plans().isEmpty(), "на два действия план помещается");
        assertEquals(2, roomy.plans().get(0).steps().size(),
            "уничтожить двоих — два боя, значит два шага");
    }

    @Test
    void инструкцияСокращаетсяПоМереВыполнения() {
        GameState s = Fix.game(СВОД, 4, 7L);
        Fix.turn(s, 0);
        hand(s, "o21");
        int доБоя = hint(s, "o21", ВСЕ, 2).plans().get(0).steps().size();
        // Один жетон уже снят в этот ход — остаётся один шаг.
        s.journal.of(0).enemyTokensDestroyed = 1;
        int послеБоя = hint(s, "o21", ВСЕ, 2).plans().get(0).steps().size();
        assertEquals(доБоя - 1, послеБоя,
            "журнал хода учтён: инструкция стала короче на выполненное");
    }

    // ==================================================================
    //  ЦЕНА НАГРАДЫ
    // ==================================================================

    @Test
    void ценаНаградыСчитаетсяПоДаннымКарты() {
        // 3 монеты = 3.0; 2 трофея = 3.0; карта задания = 2.0.
        assertEquals(3.0, ObjectiveHints.rewardValue(Map.of("coin", 3)), 1e-9);
        assertEquals(5.0, ObjectiveHints.rewardValue(Map.of("trophy", 2, "objective_card", 1)), 1e-9);
        // «module: attack» — не число: один жетон, но самый дорогой в игре.
        assertTrue(ObjectiveHints.rewardValue(Map.of("module", "attack")) >= 5.0);
        assertEquals(0.0, ObjectiveHints.rewardValue(null), 1e-9);
    }

    @Test
    void готоваяКартаОцениваетсяДорожеНеготовой() {
        GameState s = Fix.game(СВОД, 4, 7L);
        Fix.turn(s, 0);
        PlayerState p = s.player(0);
        p.objectiveHand.clear();
        p.objectiveHand.add("n3");    // выполнима: три боеприпаса
        p.objectiveHand.add("o21");   // не выполнима: боя не было
        p.resources.setAmmo(3);

        List<ObjectiveHints.Hint> hints = ObjectiveHints.forHand(s, 0, s.journal, ВСЕ, 2);
        assertEquals(2, hints.size());
        assertEquals("n3", hints.get(0).cardId(),
            "готовая карта идёт первой — бот читает список сверху");
        assertTrue(hints.get(0).value() > hints.get(1).value(),
            "у неготовой карты цена сейчас нулевая: играть её нечем");
    }

    // ==================================================================
    //  ВЕСЬ КАТАЛОГ
    // ==================================================================

    @Test
    void ниОднаКартаКаталогаНеЛомаетИндикаторы() {
        GameState s = Fix.game(СВОД, 4, 7L);
        Fix.turn(s, 0);
        PlayerState p = s.player(0);
        StringBuilder broken = new StringBuilder();
        for (String cid : Ctx.cards(s, "objectives").ids()) {
            p.objectiveHand.clear();
            p.objectiveHand.add(cid);
            try {
                ObjectiveHints.Hint h = hint(s, cid, ВСЕ, 2);
                assertNotNull(h, cid);
                // Инвариант: готовая карта не бывает одновременно «достижимой» —
                // это два РАЗНЫХ индикатора, и бот различает их по смыслу.
                assertFalse(h.ready() && h.reachable(), cid + ": горят оба индикатора сразу");
            } catch (RuntimeException e) {
                broken.append(cid).append(" — ").append(e).append('\n');
            }
        }
        assertEquals("", broken.toString(), "индикаторы падают на картах:\n" + broken);
    }

    @Test
    void укрытоеВойскоПоднимаетГотовностьЗасады() {
        GameState s = Fix.game(СВОД, 4, 7L);
        Fix.turn(s, 0);
        hand(s, "o07");   // «Засада»
        assertFalse(hint(s, "o07", ВСЕ, 2).ready());

        // Своя казарма на гексе, соседнем с гексом жетона противника, и в ней
        // своя пехота — ровно то, что требует карта.
        String enemyHex = s.player(1).startHex;
        String near = Fix.freeNeighbour(s, enemyHex);
        if (near == null) {
            return;   // на этой раскладке сцены не построить — проверять нечего
        }
        var barracks = Fix.building(s, 0, BuildingType.BARRACKS, near, null);
        Fix.power(barracks);
        Fix.unit(s, 1, UnitType.INFANTRY, enemyHex);
        Fix.unit(s, 0, UnitType.INFANTRY, near);
        assertTrue(hint(s, "o07", ВСЕ, 2).ready(),
            "войско в своём военном здании у гекса противника — «ГОТОВО» должно гореть");
    }
}
