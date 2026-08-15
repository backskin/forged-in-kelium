package kelium.agents;

import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;

/**
 * ForcedAgent — «а что если сыграть вот так»: агент-обёртка, которая ОДИН раз
 * навязывает заданный выбор, а дальше играет как обёрнутый бот.
 *
 * <p>Нужен просчёту вперёд ({@link Lookahead#playOut}). Чтобы узнать цену
 * решения, копию партии доигрывают с этим решением на входе, а всё остальное
 * играется обычной политикой — разница итогов и есть цена решения. Без такой
 * обёртки просчёт оценивал бы не выбранный вариант, а то, что бот выбрал бы сам.
 */
public final class ForcedAgent extends Agent {

    /** Что навязать: вид решения и признак нужного варианта. */
    public record Forced(String kind, Object payload) {
    }

    private final Agent delegate;
    private final Forced forced;
    private boolean spent = false;

    public ForcedAgent(Agent delegate, Forced forced) {
        super(delegate.seat, "forced/" + delegate.name);
        this.delegate = delegate;
        this.forced = forced;
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        if (!spent && forced != null) {
            String kind = context != null ? String.valueOf(context.getOrDefault("kind", "")) : "";
            if (forced.kind().equals(kind)) {
                for (Choice o : options) {
                    if (java.util.Objects.equals(o.payload(), forced.payload())) {
                        spent = true;
                        // Обёрнутому боту тоже даём увидеть решение: у стратега на
                        // выборе приказа пересчитывается план, и без этого вызова
                        // он играл бы ход по плану от прошлого круга.
                        delegate.choose(state, List.of(o), context);
                        return o;
                    }
                }
                // Навязанного варианта в списке нет (обстановка изменилась) —
                // играем свободно, но попытку не тратим.
            }
        }
        return delegate.choose(state, options, context);
    }

    @Override
    public void observeEvent(Map<String, Object> event) {
        delegate.observeEvent(event);
    }
}
