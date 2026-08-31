package kelium.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * Агент для НАСТОЯЩЕГО игрока — человека за hot-seat-клиентом или за сетевым
 * клиентом. В отличие от {@code HumanLikeAgent} (который бот, симулирующий
 * человеческий шум), этот класс сам ничего не решает: {@link #choose} блокирует
 * поток движка и ждёт, пока внешний код (UI-поток hot-seat или обработчик
 * сетевого сообщения) не вызовет {@link #submitChoice}/{@link #submitIndex}.
 *
 * <p>Движок ({@code GameEngine.playGame}) — синхронный и однопоточный, поэтому
 * его обязательно запускать на ОТДЕЛЬНОМ потоке от того, что читает ввод игрока
 * (консоль, окно, сетевой сокет) — иначе блокировка в {@link #choose} остановит
 * весь процесс.
 */
public final class InteractiveAgent extends Agent {

    /** Одна точка решения, ожидающая ответа игрока. */
    public record PendingDecision(GameState state, List<Choice> options, Map<String, Object> context) {
    }

    private final Consumer<PendingDecision> onDecision;
    private final Consumer<Map<String, Object>> onPublicEvent;
    private final BlockingQueue<Choice> answer = new ArrayBlockingQueue<>(1);
    private volatile PendingDecision pending;
    /** Партию закрыли, не доиграв: ожидание ответа надо размотать. */
    private volatile boolean aborted;
    /** Метка «ответа не будет» — ею разблокируется ожидание при закрытии. */
    private static final Choice POISON = new Choice("aborted", null, "партия закрыта");

    /**
     * @param onDecision    вызывается в потоке движка при каждом {@link #choose}
     *                      — на его основе UI/сеть должны показать игроку опции.
     * @param onPublicEvent необязательная зацепка на ВСЕ события за столом (для
     *                       живого обновления экрана других игроков между их ходами).
     */
    public InteractiveAgent(int seat, String name, Consumer<PendingDecision> onDecision,
                             Consumer<Map<String, Object>> onPublicEvent) {
        super(seat, name);
        this.onDecision = onDecision;
        this.onPublicEvent = onPublicEvent;
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        if (aborted) {
            throw new GameAborted("Игрок #" + seat + " закрыл партию");
        }
        pending = new PendingDecision(state, options, context);
        onDecision.accept(pending);
        try {
            Choice picked = answer.take();
            pending = null;
            if (aborted || picked == POISON) {
                throw new GameAborted("Игрок #" + seat + " закрыл партию");
            }
            return picked;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание выбора игрока #" + seat + " прервано", e);
        }
    }

    /**
     * ЗАКРЫТЬ ПАРТИЮ, НЕ ДОИГРАВ. Ожидание ответа размыкается меткой, и поток
     * движка выходит из {@link #choose} исключением {@link GameAborted} —
     * не заканчивая партию и ничего не решая за игрока.
     */
    public void abort() {
        aborted = true;
        answer.offer(POISON);
    }

    /** Закрыта ли партия этим игроком. */
    public boolean aborted() {
        return aborted;
    }

    @Override
    public void observePublicEvent(Map<String, Object> event) {
        if (onPublicEvent != null) {
            onPublicEvent.accept(event);
        }
    }

    /** Текущая точка решения, если движок сейчас ждёт этого игрока; иначе null. */
    public PendingDecision pending() {
        return pending;
    }

    /**
     * Ответ по индексу в списке {@code options}, полученном в последнем
     * {@link PendingDecision} — самый безопасный способ: сервер не доверяет
     * клиенту НИЧЕГО, кроме номера уже проверенной движком опции.
     */
    public void submitIndex(int index) {
        PendingDecision current = pending;
        if (current == null) {
            throw new IllegalStateException("Игрок #" + seat + ": сейчас нет точки решения");
        }
        if (index < 0 || index >= current.options().size()) {
            throw new IllegalArgumentException("Игрок #" + seat + ": индекс " + index
                    + " вне диапазона 0.." + (current.options().size() - 1));
        }
        submitChoice(current.options().get(index));
    }

    /** Ответ самим объектом {@link Choice} — должен быть ТЕМ ЖЕ, что был предложен движком. */
    public void submitChoice(Choice choice) {
        PendingDecision current = pending;
        if (current == null) {
            throw new IllegalStateException("Игрок #" + seat + ": сейчас нет точки решения");
        }
        if (!current.options().contains(choice)) {
            throw new IllegalArgumentException("Игрок #" + seat + ": выбор не входит в предложенные опции");
        }
        if (!answer.offer(choice)) {
            throw new IllegalStateException("Игрок #" + seat + ": предыдущий ответ ещё не забран движком");
        }
    }
}
