package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.step.Летопись;
import kelium.engine.step.Позиция;
import kelium.engine.step.Шаг;

/**
 * ПОШАГОВЫЙ API ОБЯЗАН ВОСПРОИЗВОДИТЬ ПАРТИЮ ТОЧНО (23.09.2026).
 *
 * <p>Партия играется случайными, но воспроизводимыми ходами. Через каждые
 * несколько решений запоминается позиция из летописи, чьё решение, какие
 * варианты и срез стола. После партии каждая позиция проигрывается заново
 * ({@link Шаг#достать}) — и обязана дать того же игрока, те же варианты и тот
 * же стол. Если движок где-то недетерминирован, это ловится здесь.
 */
class ПошаговыйApiTest {

    /** Случайный, но воспроизводимый игрок. */
    private static final class Случайный extends Agent {
        private final Random r;

        Случайный(int seat, long seed) {
            super(seat, "случайный#" + seat);
            this.r = new Random(seed);
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            return options.get(r.nextInt(options.size()));
        }
    }

    private record Отметка(Позиция позиция, int место, List<String> варианты, String срез) {
    }

    /** Снаружи летописи: запоминает каждую N-ю развилку до того, как её запишут. */
    private static final class Наблюдатель extends Agent {
        private final Agent внутри;
        private final Летопись летопись;
        private final List<Отметка> отметки;
        private final int[] счёт;

        Наблюдатель(Agent внутри, Летопись летопись, List<Отметка> отметки, int[] счёт) {
            super(внутри.seat, внутри.name);
            this.внутри = внутри;
            this.летопись = летопись;
            this.отметки = отметки;
            this.счёт = счёт;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            Позиция п = летопись.сейчас();
            if (п != null && счёт[0]++ % 7 == 0) {
                отметки.add(new Отметка(п, seat, подписи(options), срез(state)));
            }
            return внутри.choose(state, options, ctx);
        }
    }

    private static List<String> подписи(List<Choice> options) {
        List<String> out = new ArrayList<>();
        for (Choice c : options) {
            out.add(c.kind() + ":" + c.label());
        }
        return out;
    }

    private static String срез(GameState s) {
        StringBuilder b = new StringBuilder("р" + s.round + "к" + s.circle);
        for (PlayerState p : s.players) {
            b.append('|').append(p.resources.coin()).append(',').append(p.resources.kelium())
                .append(',').append(p.resources.ammo()).append(',').append(p.resources.trophy())
                .append(',').append(p.unitsOnField().size()).append(',')
                .append(p.buildingsOnField().size()).append(',').append(p.objectiveHand);
        }
        return b.toString();
    }

    @Test
    void позицияВоспроизводитсяТочно() {
        for (long seed : new long[]{77L, 78L}) {
            GameState s = Setup.buildGame(GameConfig.build(4, seed));
            Летопись летопись = new Летопись();
            List<Agent> простые = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                простые.add(new Случайный(i, seed * 13 + i));
            }
            List<Agent> записанные = летопись.подключить(s, простые);
            List<Отметка> отметки = new ArrayList<>();
            int[] счёт = {0};
            List<Agent> agents = new ArrayList<>();
            for (Agent a : записанные) {
                agents.add(new Наблюдатель(a, летопись, отметки, счёт));
            }
            GameEngine.playGame(s, agents, null);
            assertTrue(отметки.size() > 50, "мало развилок: " + отметки.size());
            for (Отметка о : отметки) {
                Шаг.Развилка р = Шаг.достать(о.позиция());
                String где = "сид " + seed + ", " + о.позиция().откуда() + ", решение "
                    + о.позиция().глубина();
                assertFalse(р.конец(), где + ": повтор дошёл до конца партии");
                assertEquals(о.место(), р.место(), где + ": не тот игрок");
                assertEquals(о.варианты(), подписи(р.варианты()), где + ": не те варианты");
                assertEquals(о.срез(), срез(р.стол()), где + ": не тот стол");
            }
        }
    }
}
