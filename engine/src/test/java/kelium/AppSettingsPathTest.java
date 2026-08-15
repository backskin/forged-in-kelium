package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * НАСТРОЙКИ ДОЛЖНЫ ПЕРЕЖИВАТЬ ЗАПИСЬ И ЧТЕНИЕ — особенно windows-пути.
 *
 * <p>Что случилось 14.08.2026. Настройки писались сырым текстом, а читались через
 * {@link Properties#load}, который разворачивает экранирование. Путь
 * {@code ...\scenarios\new} возвращался как {@code ...scenarios}, перевод строки,
 * {@code ew}: папка раскладок дизайнера пропадала из библиотеки, и прогон падал
 * с {@code InvalidPathException}. Одна буква {@code n} в имени папки.
 *
 * <p>Тест проверяет именно этот круг: записали — прочитали — получили то же
 * самое. Берём заведомо злые случаи: {@code \n}, {@code \t}, {@code \U}, знак
 * равенства, решётка в начале, кириллица (её ломать тоже нельзя — файл затевался
 * ради чтения глазами).
 */
class AppSettingsPathTest {

    private static String escape(String s, boolean key) throws Exception {
        Method m = Class.forName("kelium.dataio.AppSettings")
            .getDeclaredMethod("escape", String.class, boolean.class);
        m.setAccessible(true);
        return (String) m.invoke(null, s, key);
    }

    private static String roundTrip(String value) throws Exception {
        String line = escape("paths.dir", true) + "=" + escape(value, false) + "\n";
        Properties p = new Properties();
        p.load(new java.io.StringReader(line));
        return p.getProperty("paths.dir");
    }

    @Test
    void windowsPathsSurviveSaveAndLoad() throws Exception {
        String[] evil = {
            "C:\\Users\\backskin\\Yandex.Disk\\Forged in Kelium\\simulator\\data\\scenarios\\new",
            "C:\\temp\\table\\report",
            "D:\\раскладки\\новые поля",
            "C:\\a=b\\c:d",
            "#не комментарий",
        };
        for (String s : evil) {
            assertEquals(s, roundTrip(s), "путь испортился при записи и чтении: " + s);
        }
    }

    @Test
    void realSettingsFileIsReadableIfPresent() throws Exception {
        Path f = kelium.dataio.AppSettings.location();
        if (f == null || !Files.isRegularFile(f)) {
            return;                            // на чистой машине файла ещё нет
        }
        Properties p = new Properties();
        try (var in = Files.newInputStream(f)) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        for (String k : p.stringPropertyNames()) {
            String v = p.getProperty(k);
            assertEquals(-1, v.indexOf('\n'),
                "в настройке " + k + " оказался перевод строки — значит запись "
                + "снова не экранирует обратный слэш");
        }
    }
}
