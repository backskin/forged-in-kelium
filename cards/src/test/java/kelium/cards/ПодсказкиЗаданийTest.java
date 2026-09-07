package kelium.cards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.ObjectiveHints;
import kelium.engine.Setup;

/**
 * ИНДИКАТОРЫ ЗАДАНИЙ РАБОТАЮТ НА КАРТАХ, КОТОРЫЕ ПРОВЕРЯЮТ СЕБЯ САМИ.
 *
 * <p>Зачем этот сторож нужен ИМЕННО ЗДЕСЬ. Условия действующего каталога заданий
 * проверяет класс карты ({@code checked_by: card}), а не предикат из данных.
 * Класс живёт в модуле {@code cards}, и на класспасе тестов движка его нет —
 * связь между модулями только через ServiceLoader во время работы. Поэтому в
 * тестах движка ветка «условие в карте» проверяться не может в принципе: там
 * реестр карт пуст, индикатор гаснет, и тест сказал бы «сломано», хотя в игре
 * всё работает.
 *
 * <p>Что проверяется: у карты, чьё условие ВЫПОЛНЕНО, индикатор «ГОТОВО» горит;
 * у той же карты при невыполненном условии — не горит, зато есть человеческая
 * строка «чего не хватает». Это ровно то, на что смотрят и бот при наведении, и
 * проигрыватель.
 */
class ПодсказкиЗаданийTest {

    /** Все действия игры — сцена, где приказ ничего не ограничивает. */
    private static final Set<String> ВСЕ = Set.of("assembly", "mining", "build",
        "energy_swap", "movement", "combat", "market", "science");

    private static GameState партия() {
        GameState s = Setup.buildGame(GameConfig.build(4, 7L));
        List<Agent> agents = new java.util.ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(new ПервыйВариант(seat));
        }
        GameEngine.bind(s, agents);
        s.round = 1;
        s.circle = 1;
        return s;
    }

    /** Агент-заглушка: сцена строится руками, выбирать ему ничего не нужно. */
    private static final class ПервыйВариант extends Agent {
        ПервыйВариант(int seat) {
            super(seat, "первый вариант");
        }

        @Override
        public kelium.core.Choice choose(GameState state, List<kelium.core.Choice> options,
                                         Map<String, Object> context) {
            return options.get(0);
        }
    }

    private static ObjectiveHints.Hint подсказка(GameState s, String cid) {
        return ObjectiveHints.forCard(s, 0, s.journal, cid, ВСЕ, 2);
    }

    @Test
    void готовоГоритНаКартеСУсловиемВКоде() {
        GameState s = партия();
        PlayerState p = s.player(0);
        p.objectiveHand.clear();
        p.objectiveHand.add("n3");          // «Патроны»: имей не меньше 3 боеприпасов

        p.resources.setAmmo(0);
        ObjectiveHints.Hint холодная = подсказка(s, "n3");
        assertNotNull(холодная, "подсказка по карте руки обязана быть");
        assertFalse(холодная.ready(), "боеприпасов нет — «ГОТОВО» гореть не должно");
        assertFalse(холодная.needed().isBlank(),
            "карта в коде обязана сказать, чего не хватает");

        p.resources.setAmmo(3);
        ObjectiveHints.Hint тёплая = подсказка(s, "n3");
        assertNotNull(тёплая);
        assertTrue(тёплая.ready(),
            "три боеприпаса есть — карта играется прямо сейчас; если «ГОТОВО» не "
                + "горит, значит индикаторы не видят условий карт в коде, и бот "
                + "перестал понимать, какие задания он может закрыть");
    }
}
