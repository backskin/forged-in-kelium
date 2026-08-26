package kelium.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * {@link InteractiveAgent} со СТЕКОМ точек отката — концепт «Командный пункт»,
 * §5: безопасные действия своего хода игрок пробует в любом порядке и
 * откатывает «до точки»; необратимое (бой, рынок, наука, вскрытие приказа)
 * ЗАПЕКАЕТ всё, что было до него.
 *
 * <p><b>Что безопасно и почему</b> — разобрано по коду движка: Стройка,
 * Добыча, Манёвр, Смена энергии, Сборка не рассылают публичных событий по
 * ходу дела ({@code Actions.java}/{@code Modules.java} не зовут emit), а на
 * обобщённое {@code type=action} ПОСЛЕ действия ни один бот не реагирует
 * ({@code HumanLikeAgent} слушает только {@code combat_hit}/{@code refresh},
 * {@code SearchAgent} — {@code reveal}). Бой шлёт {@code combat_hit} прямо по
 * ходу; Рынок расходует общие ячейки; Наука двигает общие треки и призы
 * очередности — их откат врал бы остальному столу.
 *
 * <p><b>Точка отката</b> снимается ПЕРЕД началом безопасного действия и несёт
 * три вещи: глубокую копию состояния, зафиксированный сид ГСЧ (детерминизм
 * партии по сиду, заказ §3) и ПАМЯТКУ ХОДА {@link TurnUndo} — без неё движок
 * продолжал бы считать отменённое действие сыгранным и игрок терял слот.
 *
 * <p><b>Когда можно откатывать:</b> только пока движок стоит на точке решения
 * вида {@code action} этого же хода — это гарантирует, что код отменяемого
 * действия уже развернул свой стек (см. {@link GameState#restoreFrom}).
 * Вызывающий (окно) обязан это соблюдать.
 */
public final class UndoableAgent extends Agent {

    /** Действия, после которых остаётся точка отката (см. javadoc класса). */
    public static final Set<String> SAFE_ACTIONS = Set.of(
        "build", "mining", "movement", "energy_swap", "assembly");

    /**
     * ВИДЫ РЕШЕНИЙ, ФАКТИЧЕСКИ СОВЕРШАЮЩИЕ необратимое: первый залп боя,
     * занятие ячейки рынка, шаг/обмен науки. Запекание происходит ЗДЕСЬ, а не
     * при выборе действия из меню: игрок, вошедший в Бой и вышедший пасом, не
     * выстрелив, ничего необратимого не сделал — его точки отката живы
     * (уточнение к концепту §5, 24.08.2026 вечер).
     */
    public static final Set<String> COMMIT_KINDS = Set.of(
        "attack", "market_rate", "market_offer", "sci_track", "sci_exchange");

    private record Mark(String label, GameState snapshot, long seed, Object turnMemento) {
    }

    private final GameState state;
    private final InteractiveAgent delegate;
    private final List<Mark> stack = new ArrayList<>();

    public UndoableAgent(int seat, String name, GameState state,
                          Consumer<InteractiveAgent.PendingDecision> onDecision,
                          Consumer<Map<String, Object>> onPublicEvent) {
        super(seat, name);
        this.state = state;
        this.delegate = new InteractiveAgent(seat, name, onDecision, onPublicEvent);
    }

    @Override
    public Choice choose(GameState s, List<Choice> options, Map<String, Object> context) {
        Choice pick = delegate.choose(s, options, context);
        String kind = String.valueOf(context.get("kind"));
        if ("action".equals(kind) && pick.payload() instanceof String actionName
                && SAFE_ACTIONS.contains(actionName)) {
            push(actionName);
        } else if (COMMIT_KINDS.contains(kind) && pick.payload() != null) {
            // Свершилось необратимое (первый залп, ячейка рынка, шаг науки):
            // всё, что было до него, запекается.
            clearCheckpoints();
        } else if ("reveal_order".equals(kind)) {
            // Новый круг — точки прошлого хода недействительны.
            clearCheckpoints();
        }
        return pick;
    }

    private synchronized void push(String label) {
        long seed = state.rng.nextLong();
        state.rng.setSeed(seed);
        Object memento = state.turnUndo == null ? null : state.turnUndo.saveTurn();
        stack.add(new Mark(label, state.deepCopy(seed), seed, memento));
    }

    @Override
    public void observePublicEvent(Map<String, Object> event) {
        delegate.observePublicEvent(event);
    }

    public InteractiveAgent.PendingDecision pending() {
        return delegate.pending();
    }

    public void submitIndex(int index) {
        delegate.submitIndex(index);
    }

    public void submitChoice(Choice choice) {
        delegate.submitChoice(choice);
    }

    /** Подписи точек отката, от старой к новой (имена действий движка). */
    public synchronized List<String> checkpointLabels() {
        List<String> out = new ArrayList<>(stack.size());
        for (Mark m : stack) {
            out.add(m.label());
        }
        return out;
    }

    public synchronized boolean canUndo() {
        return !stack.isEmpty();
    }

    /** Подпись ПОСЛЕДНЕЙ точки (для кнопки «отменить последнее»). */
    public synchronized String undoLabel() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1).label();
    }

    /**
     * ОТКАТИТЬСЯ К ТОЧКЕ {@code index} (0 — самая ранняя): партия возвращается
     * к моменту ПЕРЕД этим действием; эта точка и все после — отбрасываются.
     */
    public synchronized void undoTo(int index) {
        if (index < 0 || index >= stack.size()) {
            throw new IllegalStateException("Место " + seat + ": нет точки отката №" + index);
        }
        Mark m = stack.get(index);
        state.restoreFrom(m.snapshot(), m.seed());
        if (m.turnMemento() != null && state.turnUndo != null) {
            state.turnUndo.restoreTurn(m.turnMemento());
        }
        while (stack.size() > index) {
            stack.remove(stack.size() - 1);
        }
    }

    /** Откатить только ПОСЛЕДНЕЕ безопасное действие. */
    public synchronized void undo() {
        undoTo(stack.size() - 1);
    }

    public synchronized void clearCheckpoints() {
        stack.clear();
    }
}
