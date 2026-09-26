package kelium.gui.net;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ПРОТОКОЛ СЕТЕВОЙ ПАРТИИ — строки JSON, одна строка на сообщение, поле
 * {@code t} — тип. Подробно — «design-docs/СЕТЬ — архитектура сетевой игры».
 *
 * <p>Модель — АВТОРИТЕТНЫЙ ХОСТ: движок живёт только у того, кто создал стол.
 * Клиент получает кадры стола, вырезанные под его место ({@link Redact}), и
 * вопросы своему месту; отвечает только НОМЕРОМ варианта. Сид партии клиенту
 * не отдаётся до её конца: сид и свод — это порядок всех колод.
 */
public final class NetProtocol {

    private NetProtocol() {
    }

    /** Версия протокола: разные версии за один стол не садятся. */
    public static final int VERSION = 1;

    /** Порт по умолчанию — вне занятых известными программами. */
    public static final int DEFAULT_PORT = 47100;

    // ---- вход и лобби ----
    public static final String HELLO = "hello";
    public static final String WELCOME = "welcome";
    public static final String REJECT = "reject";
    public static final String LOBBY = "lobby";
    public static final String SEAT = "seat";
    public static final String READY = "ready";
    public static final String CHAT = "chat";
    public static final String BYE = "bye";
    public static final String START = "start";

    // ---- партия ----
    public static final String RECORD = "record";
    public static final String RESYNC = "resync";
    public static final String DECIDE = "decide";
    public static final String ANSWER = "answer";
    public static final String OVER = "over";
    public static final String ERROR = "error";

    /** Сообщение: тип и пары «ключ, значение». */
    public static Map<String, Object> msg(String type, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", type);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** Тип сообщения. */
    public static String type(Map<String, Object> m) {
        Object t = m.get("t");
        return t == null ? "" : String.valueOf(t);
    }

    /** Версия приложения — отпечаток пака кода, если игра собрана паками. */
    public static String appVersion() {
        String v = System.getProperty("kelium.app.version");
        return v == null || v.isBlank() ? "dev" : v;
    }

    static int i(Map<String, Object> m, String key, int dflt) {
        return m.get(key) instanceof Number n ? n.intValue() : dflt;
    }

    static String s(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
