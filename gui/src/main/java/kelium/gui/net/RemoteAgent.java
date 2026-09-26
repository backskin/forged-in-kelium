package kelium.gui.net;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
 */
public final class RemoteAgent extends Agent {

    private final NetHost host;
    private final BlockingQueue<int[]> answers = new LinkedBlockingQueue<>();
    private volatile Map<String, Object> pending;
    private volatile boolean aborted;
    private int seq;

    RemoteAgent(NetHost host, int seat, String name) {
        super(seat, name);
        this.host = host;
    }

    /** Живой игрок: спец-действие — в меню хода, как у горячего стула. */
    @Override
    public boolean specInActionMenu() {
        return true;
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        if (aborted) {
            throw new GameAborted("сетевой стол закрыт");
        }
        // живой кадр перед вопросом: поставленное здание видно сразу
        kelium.gui.GameRecorder.live(state, seat);
        int mySeq = ++seq;
        Map<String, Object> msg = host.decideMessage(seat, mySeq, state, options, context);
        answers.clear();
        pending = msg;
        host.sendDecide(seat, msg);
        try {
            while (true) {
                int[] a = answers.poll(250, TimeUnit.MILLISECONDS);
                if (aborted) {
                    throw new GameAborted("сетевой стол закрыт");
                }
                if (a == null || a[0] != mySeq) {
                    continue;
                }
                if (a[1] < 0 || a[1] >= options.size()) {
                    host.sendError(seat, "вариант " + a[1] + " вне списка 0.." + (options.size() - 1));
                    host.sendDecide(seat, msg);
                    continue;
                }
                pending = null;
                return options.get(a[1]);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GameAborted("ожидание ответа прервано");
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

    /** Закрыть стол: ожидание ответа размыкается исключением {@link GameAborted}. */
    public void abort() {
        aborted = true;
        answers.offer(new int[]{-1, -1});
    }
}
