package kelium.agents;

import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;

/**
 * Соперник В ПРОСЧЁТЕ, который вскрывает приказы ПО СВОИМ ПРИВЫЧКАМ.
 *
 * <p>Нужен только внутри доигрывания: там за соперников играют модели, и от того,
 * какой приказ модель вскроет, зависит, сколько ходов заблокируется совпадением.
 * Раньше модель выбирала приказ той же формулой, что и сам бот, — то есть бот
 * считал, что все за столом думают как он, и совпадения в просчёте случались не
 * так, как в жизни.
 *
 * <p>Все прочие решения (действия, цели боя, стройка) отдаются обычной логике: гадать
 * о них бессмысленно, их видно на столе по ходу дела.
 */
public final class HabitAgent extends Agent {

    private final Agent base;
    private final OrderHabits habits;
    private final Random rng;

    public HabitAgent(Agent base, OrderHabits habits, int seat, Random rng) {
        super(seat, "привычки#" + seat);
        this.base = base;
        this.habits = habits;
        this.rng = rng;
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        String kind = context != null ? String.valueOf(context.getOrDefault("kind", "")) : "";
        if ("reveal_order".equals(kind) && habits != null) {
            Choice guess = habits.pick(seat, options, rng);
            if (guess != null) {
                return guess;
            }
        }
        return base.choose(state, options, context);
    }

    @Override
    public void observeEvent(Map<String, Object> event) {
        base.observeEvent(event);
    }

    @Override
    public void observePublicEvent(Map<String, Object> event) {
        base.observePublicEvent(event);
    }
}
