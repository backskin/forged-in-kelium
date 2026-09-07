package kelium.dataio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

/**
 * Один загруженный версионируемый файл контента (например, колода заданий).
 *
 * <p>Контент — данные, описывающие что существует в игре: карты заданий,
 * арсенал, контейнеры, рынок, приказы, супер-задания и стороны досок. Каждый
 * тип — независимый файл {@code <тип>.<версия>.yaml} в data/cards или data/boards.
 * Загрузчик проверяет структуру, но не вникает в смысл полей (это задача движка).
 */
public final class ContentSet {

    /** Ошибка загрузки контента: файл повреждён, отсутствует или нарушает структуру. */
    public static final class ContentError extends RuntimeException {
        public ContentError(String msg) {
            super(msg);
        }
    }

    /** Типы контента и подкаталог, в котором каждый лежит. */
    public static final Map<String, String> ALL_TYPES = new HashMap<>();

    static {
        ALL_TYPES.put("objectives", "cards");
        ALL_TYPES.put("arsenal", "cards");
        ALL_TYPES.put("containers", "cards");
        ALL_TYPES.put("market", "cards");
        ALL_TYPES.put("orders", "cards");
        ALL_TYPES.put("super_objectives", "cards");
        ALL_TYPES.put("super_arsenal", "cards");
        ALL_TYPES.put("boards", "boards");
    }

    public final String type;
    public final String version;
    public final List<Map<String, Object>> entries;
    public final Map<String, Object> raw;
    public final Path sourcePath;

    /**
     * Указатель id → запись. Раньше поиск был линейным перебором, а зовут его
     * десятки тысяч раз за партию (каждая проверка карты в бою, в задании, в
     * пассивке).
     */
    private final Map<String, Map<String, Object>> index = new java.util.HashMap<>();

    public ContentSet(String type, String version, List<Map<String, Object>> entries,
                      Map<String, Object> raw, Path sourcePath) {
        this.type = type;
        this.version = version;
        this.entries = entries;
        this.raw = raw;
        this.sourcePath = sourcePath;
        for (Map<String, Object> e : entries) {
            Object id = e.get("id");
            if (id != null) {
                index.put(String.valueOf(id), e);
            }
        }
    }

    /**
     * Найти запись по её id; бросить ContentError, если такой нет.
     *
     * <p>Именно БРОСИТЬ: отсутствие карты, на которую ссылаются данные, — это
     * ошибка в данных, и она должна быть громкой. Там, где карты может не быть
     * законно (чужая версия контента, необязательная ссылка), нужен
     * {@link #find(String)}.
     */
    public Map<String, Object> byId(String entryId) {
        Map<String, Object> e = index.get(entryId);
        if (e == null) {
            throw new ContentError(type + ": нет записи с id " + entryId);
        }
        return e;
    }

    /** Найти запись по id или вернуть null, если её нет (без исключения). */
    public Map<String, Object> find(String entryId) {
        return entryId == null ? null : index.get(entryId);
    }

    /** Список id всех записей в наборе. */
    public List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> e : entries) {
            out.add((String) e.get("id"));
        }
        return out;
    }

    /** Число записей в наборе контента. */
    public int size() {
        return entries.size();
    }

    /**
     * КАКИЕ ВЕРСИИ НАБОРА ЛЕЖАТ НА ДИСКЕ. Нужно для выбора колоды на партию:
     * дизайнер держит рядом несколько редакций заданий и арсенала и сравнивает их
     * между собой (просьба дизайнера 13.08.2026). Порядок — как в имени файла, по
     * возрастанию номера версии; ничего не нашлось — пустой список, и тогда
     * остаётся та версия, которую называют правила.
     */
    public static List<String> versionsOnDisk(String contentType, Path dataRoot) {
        List<String> out = new ArrayList<>();
        String subdir = ALL_TYPES.get(contentType);
        if (subdir == null) {
            return out;
        }
        Path dir = dataRoot.resolve(subdir);
        if (!Files.isDirectory(dir)) {
            return out;
        }
        String prefix = contentType + ".";
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                .filter(n -> n.startsWith(prefix) && n.endsWith(".yaml"))
                .map(n -> n.substring(prefix.length(), n.length() - ".yaml".length()))
                .forEach(out::add);
        } catch (IOException e) {
            return out;
        }
        out.sort(ContentSet::compareVersions);
        return out;
    }

    /** Сравнение версий — общий порядок программы, см. {@link VersionOrder}. */
    private static int compareVersions(String a, String b) {
        return VersionOrder.compare(a, b);
    }

    /**
     * Загрузить и провалидировать один файл контента заданного типа и версии.
     *
     * <p>Проверяет: тип известен, файл существует и парсится в отображение, есть
     * список верхнего уровня под ключом, совпадающим с типом, у каждой записи —
     * непустой уникальный id.
     */
    @SuppressWarnings("unchecked")
    public static ContentSet load(String contentType, String version, Path dataRoot) {
        if (!ALL_TYPES.containsKey(contentType)) {
            throw new ContentError("неизвестный тип контента " + contentType);
        }
        String subdir = ALL_TYPES.get(contentType);
        Path path = dataRoot.resolve(subdir).resolve(contentType + "." + version + ".yaml");
        if (!Files.exists(path)) {
            throw new ContentError("файл контента не найден: " + path);
        }
        Map<String, Object> data;
        try (InputStream in = Files.newInputStream(path)) {
            data = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ContentError("ошибка чтения " + path + ": " + e.getMessage());
        }
        if (data == null) {
            throw new ContentError(path + " не разобрался в отображение");
        }
        Object entriesObj = data.get(contentType);
        if (entriesObj == null) {
            throw new ContentError(path + " не содержит списка верхнего уровня " + contentType);
        }
        if (!(entriesObj instanceof List<?>)) {
            throw new ContentError(path + ": " + contentType + " должен быть списком");
        }
        List<Map<String, Object>> entries = (List<Map<String, Object>>) entriesObj;
        Set<String> seen = new HashSet<>();
        int i = 0;
        for (Object eo : entries) {
            if (!(eo instanceof Map<?, ?>)) {
                throw new ContentError(path + ": запись #" + i + " не является отображением");
            }
            Map<String, Object> e = (Map<String, Object>) eo;
            Object eid = e.get("id");
            if (eid == null || eid.toString().isEmpty()) {
                throw new ContentError(path + ": у записи #" + i + " нет 'id'");
            }
            if (!seen.add(eid.toString())) {
                throw new ContentError(path + ": дублирующийся id " + eid);
            }
            i++;
        }
        return new ContentSet(contentType, version, entries, data, path);
    }
}
