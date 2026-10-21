package kelium.gui.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

import kelium.report.Json;

/**
 * ОДИН СОКЕТ КАК ЛЕНТА СООБЩЕНИЙ: строка JSON = сообщение.
 *
 * <p>Формат нарочно самый простой, какой бывает: текст, по строке на
 * сообщение, UTF-8. Его видно в логе, его можно прочитать глазами и послать
 * руками через telnet, когда стол не поднимается, — а это ровно то, чего не
 * хватает, когда «по сети не работает, а почему — не видно».
 */
final class Линия implements AutoCloseable {

    private final Socket сокет;
    private final BufferedReader чтение;
    private final PrintWriter письмо;
    private volatile boolean закрыта;

    Линия(Socket сокет) throws IOException {
        this.сокет = сокет;
        this.чтение = new BufferedReader(
            new InputStreamReader(сокет.getInputStream(), StandardCharsets.UTF_8));
        this.письмо = new PrintWriter(
            new OutputStreamWriter(сокет.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /** Отправить сообщение. Обрыв связи здесь НЕ бросается: его увидит чтение. */
    void отправить(Map<String, Object> сообщение) {
        письмо.println(Json.write(сообщение));
    }

    /**
     * Читать сообщения, пока жив сокет, и отдавать их разборщику. Вызывается в
     * своём потоке; возврат означает, что связь кончилась.
     */
    @SuppressWarnings("unchecked")
    void читать(Consumer<Map<String, Object>> разбор) {
        try {
            String строка;
            while ((строка = чтение.readLine()) != null) {
                Object v = Json.parse(строка);
                if (v instanceof Map<?, ?> m) {
                    разбор.accept((Map<String, Object>) m);
                }
            }
        } catch (IOException | RuntimeException e) {
            if (!закрыта) {
                // Обрыв связи — обычное дело, а не поломка: тот, кто читает,
                // узнает об этом возвратом из метода и скажет игроку сам.
                return;
            }
        }
    }

    String адрес() {
        return String.valueOf(сокет.getRemoteSocketAddress());
    }

    @Override
    public void close() {
        закрыта = true;
        try {
            сокет.close();
        } catch (IOException ignore) {
            // закрываем, чего уж тут
        }
    }
}
