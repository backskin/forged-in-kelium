package kelium.gui.net;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameAborted;
import kelium.core.GameState;

/**
 * МЕСТО ЗА СЕТЬЮ — агент в движке хоста, за которым сидит человек на другом
 * компьютере.
 *
 * <p>В точке решения шлёт своему месту вопрос {@code decide} (варианты
 * словами и вид стола его глазами) и ждёт {@code answer} с номером варианта.
 * Кроме номера, клиенту не доверяется ничего: номер проверяется по списку,
 * который движок сам и построил.
 *
 * <p>Связь оборвалась — агент просто ждёт: место не освобождается, а после
 * переподключения хост присылает вопрос заново ({@link #pendingMessage}).
 * Ответ с чужим номером вопроса {@code seq} отбрасывается — так после
 * переподключения не бывает двойного ответа.
 *
 * <p>ОТМЕНА СО СТОРОНЫ КЛИЕНТА ({@link #cancel}): ожидание этого вопроса
 * размыкается исключением {@link GameAborted}, а партию переигрывает тот, кто
 * её ведёт (окно хоста или {@link NetGame}). Агент один на все прогоны
 * партии, поэтому у каждого вопроса своя очередь ответов: ответ на новый
 * вопрос не достанется старому, ещё не вышедшему ожиданию.
 *
 * <p>МЕСТО БОТУ ({@link #useBot}): хост отдал место ушедшего игрока боту —
 * агент дальше отвечает его руками, в том числе на уже висящий вопрос.
 */
public final class RemoteAgent extends Agent {

    private final NetHost host;
    private volatile BlockingQueue<int[]> answers = new LinkedBlockingQueue<>();
    private volatile Map<String, Object> pending;
    private volatile int pendingSeq = -1;
    private volatile int pendingRound;
    private volatile int pendingCircle;
    private volatile int cancelSeq = -1;
    private int undoClaimed = -1;
    private volatile boolean aborted;
    private volatile Agent bot;
    private final AtomicInteger seq = new AtomicInteger();

    RemoteAgent(NetHost host, int seat, String name) {
        super(seat, name);
        this.host = host;
    }

    /** Живой игрок: спец-действие — в меню хода, как у горячего стула; бот — как бот. */
    @Override
    public boolean specInActionMenu() {
        Agent b = bot;
        return b == null || b.specInActionMenu();
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        if (aborted) {
            throw new GameAborted("сетевой стол закрыт");
        }
        Agent b = bot;
        if (b != null) {
            return b.choose(state, options, context);
        }
        // живой кадр перед вопросом: поставленное здание видно сразу
        kelium.gui.GameRecorder.live(state, seat);
        int mySeq = seq.incrementAndGet();
        BlockingQueue<int[]> mine = new LinkedBlockingQueue<>();
        answers = mine;
        pendingRound = state.round;
        pendingCircle = state.circle;
        Map<String, Object> msg = host.decideMessage(seat, mySeq, state, options, context);
        pending = msg;
        pendingSeq = mySeq;
        host.sendDecide(seat, msg);
        try {
            while (true) {
                int[] a = mine.poll(250, TimeUnit.MILLISECONDS);
                if (aborted) {
                    throw new GameAborted("сетевой стол закрыт");
                }
                if (cancelSeq == mySeq) {
                    release(mySeq);
                    throw new GameAborted("решение отменено игроком");
                }
                Agent now = bot;
                if (now != null) {
                    // хост отдал место боту, пока вопрос висел — отвечает бот
                    release(mySeq);
                    return now.choose(state, options, context);
                }
                if (a == null || a[0] != mySeq) {
                    continue;
                }
                if (a[1] < 0 || a[1] >= options.size()) {
                    host.sendError(seat, "вариант " + a[1] + " вне списка 0.." + (options.size() - 1));
                    host.sendDecide(seat, msg);
                    continue;
                }
                release(mySeq);
                return options.get(a[1]);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GameAborted("ожидание ответа прервано");
        }
    }

    /** Вопрос закрыт — если это всё ещё он (новый прогон мог задать следующий). */
    private void release(int mySeq) {
        if (pendingSeq == mySeq) {
            pending = null;
            pendingSeq = -1;
        }
    }

    @Override
    public void observeEvent(Map<String, Object> event) {
        Agent b = bot;
        if (b != null) {
            b.observeEvent(event);
        }
    }

    @Override
    public void observePublicEvent(Map<String, Object> event) {
        Agent b = bot;
        if (b != null) {
            b.observePublicEvent(event);
        }
    }

    /** Ответ пришёл по сети (поток чтения провода). */
    void answer(int answerSeq, int index) {
        answers.offer(new int[]{answerSeq, index});
    }

    /** Вопрос, на который ещё не ответили, — дослать после переподключения. */
    Map<String, Object> pendingMessage() {
        return pending;
    }

    /** Номер висящего вопроса; −1 — вопроса нет. */
    int pendingSeq() {
        return pendingSeq;
    }

    /** Раунд и круг висящего вопроса — до них можно отменять свои решения. */
    int pendingRound() {
        return pendingRound;
    }

    int pendingCircle() {
        return pendingCircle;
    }

    /**
     * Взять отмену для вопроса {@code s}: true — один раз на вопрос (двойной
     * щелчок «Шаг назад» не откатывает дважды по устаревшей ленте).
     */
    synchronized boolean claimUndo(int s) {
        if (s < 0 || s != pendingSeq || undoClaimed == s) {
            return false;
        }
        undoClaimed = s;
        return true;
    }

    /**
     * Разомкнуть ожидание вопроса {@code s}: движок выходит из точки решения
     * исключением. Зовётся ПОСЛЕ того, как ведущий партию переключился на
     * новый прогон, — иначе выход приняли бы за закрытие партии.
     */
    void cancel(int s) {
        cancelSeq = s;
        answers.offer(new int[]{-1, -1});
    }

    /** Отдать место боту: дальше и на висящий вопрос отвечает он. */
    void useBot(Agent b) {
        bot = b;
        answers.offer(new int[]{-1, -1});
    }

    /** Отвечает ли за место бот. */
    boolean botted() {
        return bot != null;
    }

    /** Закрыть стол: ожидание ответа размыкается исключением {@link GameAborted}. */
    public void abort() {
        aborted = true;
        answers.offer(new int[]{-1, -1});
    }
}
