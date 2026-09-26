package kelium.gui.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import kelium.report.ReplayRecord;

import static kelium.gui.net.NetProtocol.msg;

/**
 * КЛИЕНТ СЕТЕВОГО СТОЛА — компьютер друга.
 *
 * <p>Движка здесь нет. Приходят: лобби, кадры записи партии (уже вырезанные
 * под это место), вопросы своему месту; уходят: готовность, чат и номер
 * выбранного варианта. Всё, что приходит, отдаётся {@link Listener} на потоке
 * провода — окно само переносит это на свой поток.
 *
 * <p>Токен места, выданный хостом, запоминается: при обрыве
 * {@link #reconnect()} садит на то же место, и хост досылает всю запись и
 * висящий вопрос.
 */
public final class NetClient {

    /** Что приходит от хоста. Все методы — на потоке провода. */
    public interface Listener {
        default void welcome(int seat, int players, String hostName) { }

        default void reject(String reason) { }

        default void lobby(Map<String, Object> lobby) { }

        default void chat(String from, String text) { }

        default void start(int seat, List<String> names) { }

        /** Кусок записи: {@code reset} — это вся запись заново, иначе продолжение. */
        default void record(ReplayRecord part, boolean reset, int from) { }

        default void decide(Map<String, Object> question) { }

        default void over(Map<String, Object> result) { }

        default void error(String text) { }

        default void disconnected() { }
    }

    private final String name;
    private final Listener listener;
    private volatile Wire wire;
    private volatile String token;
    private volatile int seat = -1;
    private volatile String host;
    private volatile int port;
    private volatile boolean closing;
    /** Сырые строки от хоста — для проверки утечек. */
    public volatile Consumer<String> rawTap;

    public NetClient(String name, Listener listener) {
        this.name = name;
        this.listener = listener;
    }

    /** Подключиться к хосту по адресу. */
    public void connect(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 5000);
        Wire w = new Wire(s);
        wire = w;
        w.start("client", this::onMessage, () -> {
            if (wire == w && !closing) {
                listener.disconnected();
            }
        }, line -> {
            Consumer<String> tap = rawTap;
            if (tap != null) {
                tap.accept(line);
            }
        });
        w.send(msg(NetProtocol.HELLO, "proto", NetProtocol.VERSION, "app", NetProtocol.appVersion(),
            "content", ContentHash.current(), "name", name, "token", token));
    }

    /** Снова на то же место после обрыва. */
    public void reconnect() throws IOException {
        Wire old = wire;
        if (old != null) {
            old.close();
        }
        connect(host, port);
    }

    /** «ADDRESS:PORT» → подключение; порт по умолчанию, если не указан. */
    public void connect(String address) throws IOException {
        String a = address.trim();
        int colon = a.lastIndexOf(':');
        if (colon > 0 && a.indexOf(':') == colon) {
            connect(a.substring(0, colon), Integer.parseInt(a.substring(colon + 1).trim()));
        } else {
            connect(a, NetProtocol.DEFAULT_PORT);
        }
    }

    private void onMessage(Map<String, Object> m) {
        switch (NetProtocol.type(m)) {
            case NetProtocol.WELCOME -> {
                seat = NetProtocol.i(m, "seat", -1);
                token = NetProtocol.s(m, "token");
                listener.welcome(seat, NetProtocol.i(m, "players", 0), NetProtocol.s(m, "host"));
            }
            case NetProtocol.REJECT -> {
                closing = true;
                listener.reject(NetProtocol.s(m, "reason"));
            }
            case NetProtocol.LOBBY -> listener.lobby(m);
            case NetProtocol.CHAT -> listener.chat(NetProtocol.s(m, "from"), NetProtocol.s(m, "text"));
            case NetProtocol.START -> {
                List<String> names = new ArrayList<>();
                if (m.get("names") instanceof List<?> l) {
                    for (Object o : l) {
                        names.add(String.valueOf(o));
                    }
                }
                listener.start(NetProtocol.i(m, "seat", seat), names);
            }
            case NetProtocol.RECORD -> {
                if (m.get("chunk") instanceof Map<?, ?> c) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunk = (Map<String, Object>) c;
                    ReplayRecord part = ReplayRecord.fromJsonMap(chunk);
                    listener.record(part, Boolean.TRUE.equals(m.get("reset")),
                        NetProtocol.i(m, "from", 0));
                }
            }
            case NetProtocol.DECIDE -> listener.decide(m);
            case NetProtocol.OVER -> listener.over(m);
            case NetProtocol.ERROR -> listener.error(NetProtocol.s(m, "text"));
            case NetProtocol.BYE -> closing = true;
            default -> { }
        }
    }

    /**
     * Приложить кусок к записи. Возвращает запись, которую теперь показывать;
     * null — кусок не стыкуется (надо попросить {@link #resync()}).
     */
    public static ReplayRecord merge(ReplayRecord into, ReplayRecord part, boolean reset, int from) {
        if (reset || into == null) {
            return reset ? part : null;
        }
        if (from != into.frames.size()) {
            return null;
        }
        into.frames.addAll(part.frames);
        into.orderPlays.clear();
        into.orderPlays.addAll(part.orderPlays);
        return into;
    }

    public int seat() {
        return seat;
    }

    public void ready(boolean ready) {
        send(msg(NetProtocol.READY, "ready", ready));
    }

    public void takeSeat(int s) {
        send(msg(NetProtocol.SEAT, "seat", s));
    }

    public void chat(String text) {
        send(msg(NetProtocol.CHAT, "text", text));
    }

    /** Ответ на вопрос {@code seq}: номер варианта. */
    public void answer(int seq, int index) {
        send(msg(NetProtocol.ANSWER, "seq", seq, "i", index));
    }

    public void resync() {
        send(msg(NetProtocol.RESYNC));
    }

    public void close() {
        closing = true;
        send(msg(NetProtocol.BYE));
        Wire w = wire;
        if (w != null) {
            w.close();
        }
    }

    public long receivedBytes() {
        Wire w = wire;
        return w == null ? 0 : w.receivedBytes();
    }

    private void send(Map<String, Object> m) {
        Wire w = wire;
        if (w != null) {
            w.send(m);
        }
    }
}
