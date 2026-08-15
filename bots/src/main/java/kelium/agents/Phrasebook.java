package kelium.agents;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.yaml.snakeyaml.Yaml;

import kelium.dataio.GameConfig;

/**
 * Phrasebook — СЛОВАРЬ РЕПЛИК БОТОВ: что бот говорит, объясняя свой выбор.
 *
 * <p>Реплики лежат данными, а не в коде: {@code data/phrases/bot_phrases.<ver>.yaml}.
 * Дизайнер правит их руками, не трогая сборку. Ключ описывает СИТУАЦИЮ:
 * <pre>
 *   &lt;вид решения&gt;.&lt;что именно&gt;.&lt;насколько полезно&gt;
 *   действие.добыча.сильно      стройка.завод.норма      бой.цу.убью
 * </pre>
 * На каждую ситуацию — несколько вариантов, берётся случайный, поэтому один и
 * тот же ход в разных партиях звучит по-разному.
 *
 * <p>Если точного ключа нет, ищется более общий: сначала отбрасывается оценка
 * полезности, потом уточнение. Так словарь можно дописывать постепенно, и бот
 * при этом никогда не замолкает без причины.
 */
public final class Phrasebook {

    private Phrasebook() {
    }

    /** Ключ → варианты реплик. Читается один раз на процесс. */
    private static volatile Map<String, List<String>> phrases;
    /** Откуда прочитан словарь (для диагностики и тестов). */
    private static volatile Path source;

    /** Загруженный словарь (при первом обращении читает файл). */
    public static Map<String, List<String>> all() {
        Map<String, List<String>> p = phrases;
        if (p == null) {
            synchronized (Phrasebook.class) {
                if (phrases == null) {
                    phrases = load();
                }
                p = phrases;
            }
        }
        return p;
    }

    /** Файл, из которого прочитан словарь; null — файла не нашлось. */
    public static Path source() {
        all();
        return source;
    }

    /** Перечитать словарь с диска (после ручной правки файла). */
    public static void reload() {
        synchronized (Phrasebook.class) {
            phrases = null;
            source = null;
        }
        all();
    }

    /**
     * Взять случайную реплику для ситуации. Если точного ключа нет, ключ
     * укорачивается справа («бой.цу.убью» → «бой.цу» → «бой»).
     *
     * @return реплика или null, если в словаре нет вообще ничего подходящего
     */
    public static String pick(String key, Random rng) {
        Map<String, List<String>> map = all();
        String k = key;
        while (k != null && !k.isEmpty()) {
            List<String> list = map.get(k);
            if (list != null && !list.isEmpty()) {
                return list.get(rng == null ? 0 : rng.nextInt(list.size()));
            }
            int dot = k.lastIndexOf('.');
            k = dot < 0 ? null : k.substring(0, dot);
        }
        return null;
    }

    /**
     * То же, но с подстановками: {@code {гекс}}, {@code {n}} и прочие фигурные
     * скобки заменяются значениями. Пары идут подряд: имя, значение, имя, …
     */
    public static String pick(String key, Random rng, String... vars) {
        String phrase = pick(key, rng);
        if (phrase == null) {
            return null;
        }
        for (int i = 0; i + 1 < vars.length; i += 2) {
            phrase = phrase.replace("{" + vars[i] + "}", String.valueOf(vars[i + 1]));
        }
        return phrase;
    }

    /** Есть ли в словаре точный ключ (без укорачивания) — для тестов. */
    public static boolean hasExact(String key) {
        return all().containsKey(key);
    }

    // ==================== чтение файла ====================
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> load() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Path file = findFile();
        source = file;
        if (file == null) {
            return out;                      // словаря нет — боты просто молчат
        }
        Map<String, Object> data;
        try (InputStream in = Files.newInputStream(file)) {
            data = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return out;
        }
        Object section = data == null ? null : data.get("phrases");
        if (!(section instanceof Map<?, ?> m)) {
            return out;
        }
        for (Map.Entry<?, ?> e : ((Map<Object, Object>) m).entrySet()) {
            if (!(e.getValue() instanceof List<?> list)) {
                continue;
            }
            List<String> variants = new ArrayList<>();
            for (Object o : list) {
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) {
                    variants.add(s);
                }
            }
            if (!variants.isEmpty()) {
                out.put(String.valueOf(e.getKey()).trim(), variants);
            }
        }
        return out;
    }

    /** Самый свежий файл {@code bot_phrases.*.yaml} в каталоге данных. */
    private static Path findFile() {
        Path dir = GameConfig.resolveDataRoot(null).resolve("phrases");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var s = Files.list(dir)) {
            return s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("bot_phrases.") && n.endsWith(".yaml");
            }).max(java.util.Comparator.comparing(p -> p.getFileName().toString()))
              .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
