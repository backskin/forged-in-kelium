package kelium.gui.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import kelium.report.Json;

/**
 * Живой канал одного места за сетевым столом — переживает переподключение.
 *
 * <p>Заказ (§4.4) требует: «если игрок теряет соединение посреди партии,
 * партия не должна разваливаться». {@link kelium.core.InteractiveAgent} и без
 * этого класса уже не разваливается (он просто блокируется в ожидании ответа
 * независимо от того, жив ли сокет) — а вот НОВОЕ подключение того же места
 * должно получить ТЕКУЩУЮ точку решения заново, если та не была отвечена: это
 * и есть {@code catchUp}.
 */
final class SeatChannel {

    final int seat;
    private final Consumer<Map<String, Object>> onMessage;
    private final Supplier<Map<String, Object>> catchUp;
    private volatile PrintWriter out;

    SeatChannel(int seat, Consumer<Map<String, Object>> onMessage,
                Supplier<Map<String, Object>> catchUp) {
        this.seat = seat;
        this.onMessage = onMessage;
        this.catchUp = catchUp;
    }

    /**
     * Подключить (или переподключить) канал к готовым потокам сокета.
     *
     * <p>ВАЖНО: принимает уже созданные {@link BufferedReader}/{@link PrintWriter},
     * а не сам {@link Socket} — если это первое подключение, они созданы вызывающим
     * кодом сразу после accept() и могли уже прочитать вперёд ({@code join} и
     * следующая за ним команда — из одного TCP-пакета); заново оборачивать
     * {@code socket.getInputStream()} здесь означало бы потерять эти байты.
     *
     * <p>Точку решения на переподключении ({@code catchUp}) НЕ шлёт сам — этим
     * занимается {@link #sendCatchUp()}, вызываемый ПОСЛЕ ответа на "join": иначе
     * повторная точка решения могла обогнать этот ответ в потоке и клиент,
     * ожидающий строго "ответ на join, затем decision", прочитал бы их не в том
     * порядке.
     */
    void attach(BufferedReader in, PrintWriter writer) {
        out = writer;
        Thread t = new Thread(() -> readLoop(in), "seat-" + seat + "-reader");
        t.setDaemon(true);
        t.start();
    }

    /** Досказать текущую (неотвеченную) точку решения — после ack на "join". */
    void sendCatchUp() {
        Map<String, Object> resume = catchUp.get();
        if (resume != null) {
            send(resume);
        }
    }

    private void readLoop(BufferedReader in) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Object parsed;
                try {
                    parsed = Json.parse(line);
                } catch (RuntimeException bad) {
                    send(Map.of("type", "error", "message", "не разобрал JSON"));
                    continue;
                }
                if (parsed instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mm = (Map<String, Object>) m;
                    onMessage.accept(mm);
                }
            }
        } catch (IOException disconnected) {
            // Отключение — штатная ситуация: место просто ждёт переподключения,
            // сама партия (см. InteractiveAgent) от этого не останавливается.
        } finally {
            out = null;
        }
    }

    /** Отправить сообщение; тихо теряется, если место сейчас не подключено. */
    void send(Map<String, Object> msg) {
        PrintWriter o = out;
        if (o == null) {
            return;
        }
        synchronized (this) {
            o.println(Json.write(msg));
            if (o.checkError()) {
                out = null;
            }
        }
    }
}
