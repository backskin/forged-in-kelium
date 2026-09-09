package kelium;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * ИМЕНА ФАЙЛОВ РЕПОЗИТОРИЯ ОБЯЗАНЫ СУЩЕСТВОВАТЬ НА WINDOWS.
 *
 * <p>Поймано на живом: 09.09.2026 в репозиторий уехал черновик с ДВОЕТОЧИЕМ в
 * имени. В NTFS двоеточие — разделитель альтернативных потоков данных, такого
 * пути там просто не бывает. На Windows-машине падала любая выкладка этого
 * коммита («invalid path»), и рабочая копия переставала переключаться между
 * ветками — то есть один markdown-файл ломал работу целиком.
 *
 * <p>Чинить приходилось в обход рабочей копии, через временный индекс. Второй
 * раз этого делать не надо, поэтому проверка живёт здесь.
 *
 * <p>Проверяется весь список файлов под версией — именно он уезжает на другие
 * машины. Незакоммиченный мусор в рабочей папке никого не касается и в проверку
 * не попадает.
 */
class ИменаФайловTest {

    /** Знаки, которых в имени файла на Windows быть не может. */
    private static final Set<Character> ЗАПРЕЩЁННЫЕ = Set.of(
        '<', '>', ':', '"', '|', '?', '*');

    /** Имена устройств DOS: файл с таким именем на Windows не создаётся. */
    private static final Set<String> УСТРОЙСТВА = Set.of(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    @Test
    void путиГодятсяДляWindows() throws Exception {
        List<String> пути = подВерсией();
        assumeTrue(!пути.isEmpty(), "git недоступен — проверять нечего");

        List<String> беда = new ArrayList<>();
        // Пути, различающиеся только регистром: на Windows и macOS это ОДИН
        // файл, и один из двух молча затрёт другой при выкладке.
        Map<String, List<String>> поНижнемуРегистру = new HashMap<>();

        for (String путь : пути) {
            poНижнему(поНижнемуРегистру, путь);
            for (String сегмент : путь.split("/")) {
                for (char c : сегмент.toCharArray()) {
                    if (ЗАПРЕЩЁННЫЕ.contains(c)) {
                        беда.add("знак «" + c + "» — " + путь);
                    } else if (c < 32) {
                        беда.add("управляющий символ — " + путь);
                    }
                }
                if (сегмент.endsWith(".") || сегмент.endsWith(" ")) {
                    // Windows молча срезает хвостовую точку и пробел, и путь
                    // перестаёт совпадать с тем, что записано в git.
                    беда.add("кончается точкой или пробелом — " + путь);
                }
                String доТочки = сегмент.contains(".")
                    ? сегмент.substring(0, сегмент.indexOf('.')) : сегмент;
                if (УСТРОЙСТВА.contains(доТочки.toUpperCase(java.util.Locale.ROOT))) {
                    беда.add("имя устройства DOS — " + путь);
                }
            }
        }
        for (var e : поНижнемуРегистру.entrySet()) {
            if (e.getValue().size() > 1) {
                беда.add("совпадают без учёта регистра — " + String.join(" и ", e.getValue()));
            }
        }

        assertTrue(беда.isEmpty(),
            "эти пути не существуют на Windows, выкладка коммита там упадёт:\n  "
                + String.join("\n  ", беда));
    }

    private static void poНижнему(Map<String, List<String>> куда, String путь) {
        куда.computeIfAbsent(путь.toLowerCase(java.util.Locale.ROOT),
            k -> new ArrayList<>()).add(путь);
    }

    /** Список файлов под версией. Пустой, если git недоступен. */
    private static List<String> подВерсией() throws Exception {
        Path корень = кореньРепозитория();
        if (корень == null) {
            return List.of();
        }
        // -z: имена через \0, иначе git экранирует кириллицу в \NNN и проверять
        // становится нечего.
        Process p = new ProcessBuilder("git", "ls-files", "-z")
            .directory(корень.toFile()).redirectErrorStream(false).start();
        String вывод = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : вывод.split("\0")) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static Path кореньРепозитория() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve(".git")) || Files.isRegularFile(p.resolve(".git"))) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }
}
