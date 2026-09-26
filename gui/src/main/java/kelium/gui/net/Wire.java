package kelium.gui.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import kelium.report.Json;

/**
 * ПРОВОД — одно соединение: строки JSON туда и обратно.
 *
 * <p>Читает на своём потоке и отдаёт каждое сообщение в {@code onMessage}; пишет
 * под замком, так что слать можно с любого потока. Обрыв — один вызов
 * {@code onClose}. Транспорт спрятан здесь нарочно: ретранслятор с кодом
 * комнаты (WebSocket) заменит только этот класс, протокол останется тем же.
 */
public final class Wire {

    private final Socket socket;
    private final BufferedWriter out;
    private final BufferedReader in;
    private volatile boolean closed;
    private final AtomicLong sentBytes = new AtomicLong();
    private final AtomicLong receivedBytes = new AtomicLong();

    public Wire(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
            StandardCharsets.UTF_8));
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
            StandardCharsets.UTF_8));
        Thread w = new Thread(this::writeLoop, "net-write-" + socket.getPort());
        w.setDaemon(true);
        w.start();
    }

    /**
     * Начать читать. {@code rawTap} — необязательный наблюдатель за сырыми
     * строками (проверка утечек в тестах смотрит ровно то, что пришло по сети).
     */
    public void start(String name, Consumer<Map<String, Object>> onMessage, Runnable onClose,
                      Consumer<String> rawTap) {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    receivedBytes.addAndGet(line.length() + 1L);
                    if (rawTap != null) {
                        rawTap.accept(line);
                    }
                    Object parsed;
                    try {
                        parsed = Json.parse(line);
                    } catch (RuntimeException bad) {
                        continue;          // битая строка — не повод рвать связь
                    }
                    if (parsed instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mm = (Map<String, Object>) m;
                        try {
                            onMessage.accept(mm);
                        } catch (RuntimeException e) {
                            System.err.println("[сеть] ошибка разбора «" + NetProtocol.type(mm)
                                + "»: " + e);
                        }
                    }
                }
            } catch (IOException ignored) {
                // обрыв — штатно
            } finally {
                hardClose();
                if (onClose != null) {
                    onClose.run();
                }
            }
        }, "net-" + name);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Отправить; на оборванной связи — тихо false. Не блокирует: строка ложится
     * в очередь, пишет её свой поток. Иначе медленный читатель на том конце
     * (или встречная запись) мог бы заклинить поток движка хоста, держащий
     * замок стола, — взаимная блокировка через полные буферы сокетов.
     */
    public boolean send(Map<String, Object> msg) {
        if (closed) {
            return false;
        }
        outbox.add(Json.write(msg));
        return true;
    }

    private final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>();

    private void writeLoop() {
        try {
            while (!closed || !outbox.isEmpty()) {
                String line = outbox.poll(200, TimeUnit.MILLISECONDS);
                if (line == null) {
                    continue;
                }
                out.write(line);
                out.write('\n');
                sentBytes.addAndGet(line.length() + 1L);
                if (outbox.isEmpty()) {
                    out.flush();
                }
            }
        } catch (IOException | InterruptedException e) {
            closed = true;
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // и так закрыт
            }
        }
    }

    /**
     * Закрыть: уже поставленное в очередь (отказ, «пока») ещё уходит, потом
     * сокет закрывается. Через секунду — закрывается в любом случае.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                // закрываем сразу
            }
            hardClose();
        }, "net-close");
        t.setDaemon(true);
        t.start();
    }

    /** Порвать сразу (тот конец уже ушёл). */
    void hardClose() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // и так закрыт
        }
    }

    public boolean closed() {
        return closed;
    }

    public long sentBytes() {
        return sentBytes.get();
    }

    public long receivedBytes() {
        return receivedBytes.get();
    }

    public String remote() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }
}
