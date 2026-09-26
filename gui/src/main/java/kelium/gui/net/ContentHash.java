package kelium.gui.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;


/**
 * ОТПЕЧАТОК ПРАВИЛ И КАРТ — сверка «у нас одна и та же игра».
 *
 * <p>Партию считает хост, поэтому разные данные у клиента партию не сломают —
 * но у него будут не те тексты карт и не те подсказки. Такого друга за стол
 * лучше не пускать, а сказать словами «обновите игру». Отпечаток — SHA-256
 * по всем файлам сводов, карт, планшетов, модулей, блоков и компонентов
 * (меньше мегабайта, считается за миллисекунды). Картинки не входят — они не
 * меняют смысла; раскладки полей тоже — поле хост присылает в шапке записи, а
 * свои раскладки у каждого свои.
 */
public final class ContentHash {

    private ContentHash() {
    }

    static final List<String> FOLDERS = List.of(
        "rulesets", "cards", "boards", "modules", "blocks", "components");

    private static volatile String cached;

    /** Отпечаток данных этой установки (первые 16 знаков). */
    public static String current() {
        String c = cached;
        if (c == null) {
            c = of(kelium.dataio.GameConfig.resolveDataRoot(null));
            cached = c;
        }
        return c;
    }

    /** Отпечаток папки data. */
    public static String of(Path data) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            List<Path> files = new ArrayList<>();
            for (String f : FOLDERS) {
                Path dir = data.resolve(f);
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> s = Files.walk(dir)) {
                    s.filter(Files::isRegularFile).forEach(files::add);
                }
            }
            files.sort((a, b) -> rel(data, a).compareTo(rel(data, b)));
            for (Path p : files) {
                sha.update(rel(data, p).getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
                sha.update(Files.readAllBytes(p));
            }
            return HexFormat.of().formatHex(sha.digest()).substring(0, 16);
        } catch (IOException | NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    private static String rel(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }
}
