package kelium.gui.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;

import kelium.gui.HotSeatWindow;
import kelium.report.ReplayRecord;

/**
 * ПАРТИЯ У ДРУГА В НАСТОЯЩЕМ ОКНЕ (29.09.2026) — вместо прототипа
 * {@link NetClientWindow}. Друг видит то же окно партии, что хост: стол,
 * планшеты, вопросы на поле, Рынок и Науку планшетом, подсказки словами.
 *
 * <p>Окно открывается по первой записи от хоста (в её шапке — правила, цвета
 * и названия карт); что пришло раньше, ждёт. Связь оборвалась — сам
 * переподключается и получает запись заново.
 */
final class RemoteGame implements HotSeatWindow.Remote {

    private final NetClient client;
    private final int seat;
    private final List<String> names;
    private final Runnable onClose;

    private volatile HotSeatWindow window;
    private final List<Runnable> waiting = new ArrayList<>();
    private volatile boolean finished;
    private volatile boolean reconnecting;

    RemoteGame(NetClient client, int seat, List<String> names, Runnable onClose) {
        this.client = client;
        this.seat = seat;
        this.names = new ArrayList<>(names);
        this.onClose = onClose;
    }

    /** Окно появится с первой записью от хоста. */
    void show() {
    }

    /** Окно партии (для прогонщиков); null — записи ещё не было. */
    HotSeatWindow window() {
        return window;
    }

    /** Сделать сейчас, если окно есть, иначе — когда появится. */
    private void later(java.util.function.Consumer<HotSeatWindow> job) {
        synchronized (waiting) {
            HotSeatWindow w = window;
            if (w == null) {
                waiting.add(() -> job.accept(window));
                return;
            }
        }
        job.accept(window);
    }

    // ==================== от хоста ====================

    void record(ReplayRecord part, boolean reset, int from) {
        if (window == null && reset) {
            SwingUtilities.invokeLater(() -> {
                if (window != null) {
                    window.remoteRecord(part, true, from);
                    return;
                }
                HotSeatWindow w = HotSeatWindow.openRemote(part, seat, names, this);
                List<Runnable> jobs;
                synchronized (waiting) {
                    window = w;
                    jobs = new ArrayList<>(waiting);
                    waiting.clear();
                }
                for (Runnable r : jobs) {
                    r.run();
                }
            });
            return;
        }
        later(w -> w.remoteRecord(part, reset, from));
    }

    void decide(Map<String, Object> q) {
        later(w -> w.remoteDecide(q));
    }

    void over(Map<String, Object> r) {
        finished = true;
        later(w -> w.remoteOver(r));
    }

    /** Строка чата от хоста — в окно. */
    void chatLine(String line) {
        later(w -> w.remoteChat(line));
    }

    void paused(int who, String whoName, boolean waitingForHim) {
        later(w -> w.remotePaused(who, whoName, waitingForHim));
    }

    void resumed(int who, String how) {
        later(w -> w.remoteResumed(who, how));
    }

    void closed() {
        finished = true;
        later(w -> w.remoteEnded("Хост закрыл партию", "Партия окончена без итога."));
    }

    void rejected(String reason) {
        finished = true;
        String r = reason == null || reason.isEmpty() ? ""
            : Character.toUpperCase(reason.charAt(0)) + reason.substring(1) + ".";
        later(w -> w.remoteEnded("Вы вне партии", r));
    }

    void disconnected() {
        if (finished || reconnecting) {
            return;
        }
        reconnecting = true;
        later(w -> w.remoteConnection(false));
        Thread t = new Thread(() -> {
            for (int i = 0; i < 200 && !finished; i++) {
                try {
                    Thread.sleep(3000);
                    client.reconnect();
                    reconnecting = false;
                    later(w -> w.remoteConnection(true));
                    return;
                } catch (Exception e) {
                    // хост ещё не вернулся — пробуем дальше
                }
            }
            reconnecting = false;
        }, "net-reconnect");
        t.setDaemon(true);
        t.start();
    }

    // ==================== к хосту (HotSeatWindow.Remote) ====================

    @Override
    public void answer(int seq, int index) {
        client.answer(seq, index);
    }

    @Override
    public void undo(int seq, boolean all) {
        client.undo(seq, all);
    }

    /** Сообщение друга в чат — хосту. */
    @Override
    public void chat(String text) {
        client.chat(text);
    }

    @Override
    public void resync() {
        client.resync();
    }

    @Override
    public void leave() {
        finished = true;
        client.close();
        if (onClose != null) {
            onClose.run();
        }
    }
}
