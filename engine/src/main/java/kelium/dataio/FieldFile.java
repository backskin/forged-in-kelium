package kelium.dataio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FieldFile — СОБСТВЕННЫЙ ФОРМАТ ФАЙЛА РАСКЛАДКИ.
 *
 * <p>Раскладка поля лежит в файле {@code .kmap} (решение дизайнера 13.08.2026).
 * Внутри — тот же YAML, что и раньше: формат не менялся, менялось только имя, и
 * это сделано намеренно. Своё расширение даёт три вещи, которых у {@code .yaml}
 * не было:
 *
 * <ul>
 *   <li>файл видно в проводнике: сразу понятно, что это поле, а не «какой-то
 *       конфиг»;</li>
 *   <li>его можно связать с конструктором через «Открыть с помощью…», и двойной
 *       щелчок будет открывать поле;</li>
 *   <li>диалог открытия показывает только раскладки, а не все YAML подряд.</li>
 * </ul>
 *
 * <p>СТАРЫЕ ФАЙЛЫ ЧИТАЮТСЯ ТОЖЕ. Раскладки, нарисованные до этой правки, лежат в
 * {@code .yaml}, и заставлять дизайнера переименовывать их руками — потерять
 * работу на ровном месте. Поэтому открывается и то, и другое, а сохраняется
 * всегда в {@code .kmap}.
 *
 * <p>Всё знание о расширении собрано здесь. Раньше строка {@code "yaml"} была
 * рассыпана по диалогам, по загрузчику сценариев и по подписям — и разошлась бы
 * при первой же правке.
 */
public final class FieldFile {

    private FieldFile() {
    }

    /** Расширение файла раскладки без точки. */
    public static final String EXT = "kmap";

    /** То же с точкой — для сравнения имён и склейки. */
    public static final String DOT_EXT = "." + EXT;

    /** Что читаем: своё расширение и старое, в порядке предпочтения. */
    public static final List<String> READ_EXTS = List.of(EXT, "yaml", "yml");

    /** Человеческое имя формата — для заголовков окон и подсказок. */
    public static final String TITLE = "раскладка поля";

    /** Это файл раскладки (по имени)? Понимает и старые .yaml. */
    public static boolean isField(Path p) {
        if (p == null || p.getFileName() == null) {
            return false;
        }
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String e : READ_EXTS) {
            if (n.endsWith("." + e)) {
                return true;
            }
        }
        return false;
    }

    /** Имя файла без расширения — из него берётся id раскладки. */
    public static String baseName(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /** Дописать своё расширение, если его нет (для сохранения). */
    public static Path withExt(Path p) {
        String n = p.getFileName().toString();
        if (n.toLowerCase(Locale.ROOT).endsWith(DOT_EXT)) {
            return p;
        }
        // у старого файла расширение ЗАМЕНЯЕМ, а не дописываем вторым
        for (String e : READ_EXTS) {
            if (n.toLowerCase(Locale.ROOT).endsWith("." + e)) {
                return p.resolveSibling(n.substring(0, n.length() - e.length() - 1) + DOT_EXT);
            }
        }
        return p.resolveSibling(n + DOT_EXT);
    }

    /**
     * Все раскладки в папке: сперва свои, потом старые. Порядок важен — если
     * рядом лежат {@code поле.kfield} и {@code поле.yaml}, берётся своё.
     */
    public static List<Path> list(Path dir) {
        List<Path> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        for (String ext : READ_EXTS) {
            try (var s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)
                        .endsWith("." + ext))
                    // По-человечески: «Поле 2» раньше «Поле 10» (VersionOrder).
                    .sorted((a, b) -> VersionOrder.compare(
                        a.getFileName().toString(), b.getFileName().toString()))
                    .forEach(out::add);
            } catch (java.io.IOException e) {
                return out;
            }
        }
        return out;
    }
}
