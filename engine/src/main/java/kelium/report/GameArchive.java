package kelium.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import kelium.core.Agent;

/**
 * GameArchive — «память о партиях»: пишет поток событий партии в файл JSONL
 * (по одной JSON-строке на событие). Формат читается и человеком, и LLM, и
 * пригоден как обучающая выборка для нейросетевого бота (задача #33):
 * последовательность состояние→решение→итог одной игры.
 *
 * <p>Подключается как {@code onEvent}-консьюмер движка (одна запись на событие).
 * Умеет и приём через {@code Agent.observeEvent}: повторные вызовы с тем же
 * объектом события отбрасываются (дедуп по идентичности), чтобы 4 агента,
 * делящих один архив, не дублировали строки.
 */
public final class GameArchive implements Consumer<Map<String, Object>>, AutoCloseable {

    private final Writer out;
    private Map<String, Object> lastEvent = null;

    public GameArchive(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            this.out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("не удалось открыть архив партии: " + file, e);
        }
    }

    /** Записать событие (как {@link Consumer}). */
    @Override
    public void accept(Map<String, Object> event) {
        writeLine(event);
    }

    /**
     * Приём от агента (seat — кто наблюдал). Дедуп по идентичности: если это тот
     * же объект события, что записали в прошлый раз, пропускаем.
     */
    public void record(int seat, Map<String, Object> event) {
        if (event == lastEvent) {
            return;
        }
        writeLine(event);
    }

    private void writeLine(Map<String, Object> event) {
        lastEvent = event;
        try {
            out.write(toJson(event));
            out.write("\n");
        } catch (IOException e) {
            throw new UncheckedIOException("ошибка записи в архив партии", e);
        }
    }

    @Override
    public void close() {
        try {
            out.flush();
            out.close();
        } catch (IOException e) {
            throw new UncheckedIOException("ошибка закрытия архива партии", e);
        }
    }

    // ================= минимальная JSON-сериализация =====================
    @SuppressWarnings("unchecked")
    static String toJson(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String s) {
            return quote(s);
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) m).entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(quote(String.valueOf(e.getKey()))).append(":").append(toJson(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(toJson(list.get(i)));
            }
            return sb.append("]").toString();
        }
        if (v instanceof Object[] arr) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(toJson(arr[i]));
            }
            return sb.append("]").toString();
        }
        return quote(v.toString());
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append("\"").toString();
    }
}
