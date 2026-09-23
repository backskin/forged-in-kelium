package kelium.engine.step;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;

/**
 * ЛЕТОПИСЬ НАСТОЯЩЕЙ ПАРТИИ (23.09.2026): в начале каждого круга — точная копия
 * стола, после неё — номер каждого решения каждого игрока. Из этого в любой миг
 * собирается {@link Позиция} — текущая, ровно та, в которой бота спрашивают.
 *
 * <p>Подключается к столу до начала партии: оборачивает агентов (чтобы видеть
 * все решения) и вешает крючок начала круга. Боту, который думает перебором,
 * летопись отдаёт позицию; остальным она не мешает.
 */
public final class Летопись {

    private GameState снимок;
    private final List<Integer> ходы = new ArrayList<>();

    /**
     * Подключить к столу. Возвращает обёрнутых агентов — ими и надо играть.
     */
    public List<Agent> подключить(GameState стол, List<Agent> агенты) {
        стол.circleStartHook = s -> {
            снимок = s.exactCopy();
            ходы.clear();
        };
        List<Agent> обёрнутые = new ArrayList<>();
        for (Agent a : агенты) {
            обёрнутые.add(new Писец(a, this));
        }
        return обёрнутые;
    }

    /**
     * Позиция в миг текущего вопроса (решение ещё не принято). {@code null} —
     * до первого круга (подготовка партии): там перезапуск невозможен.
     */
    public Позиция сейчас() {
        return снимок == null ? null : new Позиция(снимок, ходы);
    }

    private void записать(int вариант) {
        if (снимок != null) {
            ходы.add(вариант);
        }
    }

    /** Обёртка агента: пропускает решение и записывает его номер. */
    private static final class Писец extends Agent {
        private final Agent агент;
        private final Летопись летопись;

        Писец(Agent агент, Летопись летопись) {
            super(агент.seat, агент.name);
            this.агент = агент;
            this.летопись = летопись;
        }

        /** Настоящий агент — если ему нужна летопись (бот перебора). */
        public Agent агент() {
            return агент;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            Choice c = агент.choose(state, options, ctx);
            int i = options.indexOf(c);
            if (i < 0) {
                // агент вернул равный, но другой объект — ищем по содержимому
                for (int k = 0; k < options.size(); k++) {
                    if (options.get(k).equals(c)) {
                        i = k;
                        break;
                    }
                }
            }
            летопись.записать(i);
            return c;
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            агент.observeEvent(event);
        }

        @Override
        public void observePublicEvent(Map<String, Object> event) {
            агент.observePublicEvent(event);
        }
    }
}
