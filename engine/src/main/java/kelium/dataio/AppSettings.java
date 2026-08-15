package kelium.dataio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * AppSettings — НАСТРОЙКИ ПРИЛОЖЕНИЙ В ОБЫЧНОМ ФАЙЛЕ, а не в реестре Windows.
 *
 * <p>Зачем. Раньше окна помнили себя через {@link Preferences} — на Windows это
 * ветка реестра. Настройку нельзя посмотреть глазами, нельзя перенести на другую
 * машину, нельзя починить блокнотом, и её не видно рядом с приложением. Когда
 * приложение «поехало» и перестало находить данные, посмотреть было НЕКУДА
 * (жалоба дизайнера 14.08.2026). Теперь всё лежит одним текстовым файлом:
 *
 * <pre>%APPDATA%\Kelium\kelium.cfg</pre>
 *
 * <p>Файл читается один раз при первом обращении и переписывается сам, с
 * задержкой: окно при перетаскивании шлёт десятки записей в секунду, и бить по
 * диску на каждую было бы расточительно. При выходе всё дописывается
 * принудительно.
 *
 * <p>Где именно лежит файл, можно задать двумя способами:
 * <ul>
 *   <li>{@code -Dkelium.settings=путь\к\файлу.cfg} — на один запуск;</li>
 *   <li>файл {@code kelium.cfg} РЯДОМ С ПРОГРАММОЙ (в рабочей папке) — тогда
 *       настройки переносные: скопировал папку вместе с файлом и получил ту же
 *       настройку на другой машине.</li>
 * </ul>
 *
 * <p>Ключи разложены по РАЗДЕЛАМ: {@code раздел.ключ}. Раздел выбирает
 * приложение ({@code replay2}, {@code app} и т. д.), поэтому один файл держит
 * настройки всех программ проекта и они не путаются.
 *
 * <p>СТАРЫЕ НАСТРОЙКИ НЕ ТЕРЯЮТСЯ: если файла ещё нет, а в реестре лежит прежняя
 * ветка, её содержимое переносится в файл при первом запуске — размер окна, тема
 * и прочее остаются такими, к каким человек привык.
 */
public final class AppSettings {

    /** Имя файла настроек — одно на все приложения проекта. */
    public static final String FILE_NAME = "kelium.cfg";
    /** Ветка реестра версии до файла настроек — из неё делается перенос. */
    private static final String[] LEGACY_NODES = {"kelium/replay2"};

    private static final Properties DATA = new Properties();
    private static final Object LOCK = new Object();
    private static Path file;
    private static boolean loaded;
    private static boolean dirty;

    private final String section;

    private AppSettings(String section) {
        this.section = section;
    }

    /** Настройки одного приложения: {@code AppSettings.of("replay2")}. */
    public static AppSettings of(String section) {
        load();
        return new AppSettings(section);
    }

    /** Где лежит файл настроек — для окна настроек и для сообщений. */
    public static Path location() {
        load();
        return file;
    }

    // ==================== чтение и запись ====================

    public String get(String key, String def) {
        synchronized (LOCK) {
            String v = DATA.getProperty(full(key));
            return v == null || v.isBlank() ? def : v;
        }
    }

    public void put(String key, String value) {
        synchronized (LOCK) {
            String k = full(key);
            if (value == null || value.isBlank()) {
                if (DATA.remove(k) == null) {
                    return;
                }
            } else if (value.equals(DATA.getProperty(k))) {
                return;                       // ничего не изменилось — и писать нечего
            } else {
                DATA.setProperty(k, value);
            }
            dirty = true;
        }
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void putInt(String key, int value) {
        put(key, String.valueOf(value));
    }

    public double getDouble(String key, double def) {
        try {
            // Запятая как разделитель дроби тоже понимается: файл могли править
            // руками в русской раскладке, и из-за одного символа настройка не
            // должна пропадать.
            return Double.parseDouble(get(key, String.valueOf(def)).trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void putDouble(String key, double value) {
        put(key, String.valueOf(value));
    }

    public boolean getBoolean(String key, boolean def) {
        return Boolean.parseBoolean(get(key, String.valueOf(def)).trim());
    }

    public void putBoolean(String key, boolean value) {
        put(key, String.valueOf(value));
    }

    /** Убрать настройку совсем — дальше действует значение по умолчанию. */
    public void remove(String key) {
        put(key, null);
    }

    private String full(String key) {
        return section + "." + key;
    }

    // ==================== файл ====================

    private static void load() {
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            loaded = true;
            file = resolveFile();
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    DATA.load(new java.io.InputStreamReader(in,
                        java.nio.charset.StandardCharsets.UTF_8));
                } catch (IOException e) {
                    System.out.println("[НАСТРОЙКИ] не прочитал " + file + ": "
                        + e.getMessage());
                }
            } else {
                importLegacy();
            }
            // Сохранение с задержкой: писать на каждое движение окна не нужно, но и
            // терять настройку при закрытии нельзя — отсюда таймер и крючок выхода.
            java.util.Timer t = new java.util.Timer("kelium-settings", true);
            t.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    flush();
                }
            }, 1500L, 1500L);
            Runtime.getRuntime().addShutdownHook(new Thread(AppSettings::flush,
                "kelium-settings-flush"));
        }
    }

    private static Path resolveFile() {
        String prop = System.getProperty("kelium.settings", "");
        if (!prop.isBlank()) {
            return Paths.get(prop).toAbsolutePath().normalize();
        }
        // ПЕРЕНОСНОЙ ВАРИАНТ: файл рядом с программой сильнее домашней папки.
        Path portable = Paths.get("").toAbsolutePath().resolve(FILE_NAME);
        if (Files.isRegularFile(portable)) {
            return portable;
        }
        String appData = System.getenv("APPDATA");
        Path dir;
        if (appData != null && !appData.isBlank()) {
            dir = Paths.get(appData, "Kelium");
        } else {
            // не Windows — общепринятое место для настроек
            dir = Paths.get(System.getProperty("user.home"), ".config", "kelium");
        }
        return dir.resolve(FILE_NAME);
    }

    /** Перенести настройки из прежней ветки реестра — один раз, при первом запуске. */
    private static void importLegacy() {
        for (String node : LEGACY_NODES) {
            try {
                if (!Preferences.userRoot().nodeExists(node)) {
                    continue;
                }
                Preferences p = Preferences.userRoot().node(node);
                String section = node.substring(node.lastIndexOf('/') + 1);
                for (String k : p.keys()) {
                    String v = p.get(k, null);
                    if (v != null) {
                        DATA.setProperty(section + "." + k, v);
                    }
                }
                dirty = true;
            } catch (BackingStoreException | RuntimeException e) {
                // реестр недоступен — просто начнём с настроек по умолчанию
            }
        }
    }

    /**
     * ЭКРАНИРОВАНИЕ ОБРАТНОГО СЛЭША — иначе windows-путь разрушается при чтении.
     *
     * <p>Читаем мы через {@link Properties#load}, а он разворачивает
     * экранирование: {@code \n} для него перевод строки, {@code \U} — просто
     * {@code U}. Писали же мы значение как есть, поэтому путь
     * {@code ...\scenarios\new} возвращался как {@code ...scenarios}, перевод
     * строки, {@code ew} — и папка раскладок дизайнера пропадала из библиотеки с
     * падением всего прогона (поймано 14.08.2026). Пишем ровно то, что
     * {@code Properties.load} прочитает обратно без изменений.
     *
     * <p>Кириллицу НЕ трогаем: файл затевался ради того, чтобы его читали
     * глазами, а {@code Properties.load} с чтением в UTF-8 понимает её как есть.
     *
     * @param key ключи требуют строже: в них экранируются ещё {@code =}, {@code :}
     *            и пробелы, потому что там они разделители
     */
    private static String escape(String s, boolean key) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '=', ':', ' ' -> {
                    // В значении это обычные символы, и экранировать их незачем —
                    // кроме пробела в самом начале, который иначе съедается.
                    if (key || i == 0) {
                        b.append('\\');
                    }
                    b.append(c);
                }
                case '#', '!' -> {
                    if (i == 0) {
                        b.append('\\');       // иначе строка станет комментарием
                    }
                    b.append(c);
                }
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    /** Записать настройки на диск, если что-то поменялось. */
    public static void flush() {
        synchronized (LOCK) {
            if (!loaded || !dirty || file == null) {
                return;
            }
            try {
                Path dir = file.getParent();
                if (dir != null) {
                    Files.createDirectories(dir);
                }
                // ПИШЕМ САМИ, А НЕ Properties.store: тот кодирует всё, что не
                // латиница, экранированными кодами вида «бэкслеш-u-число» — и
                // русский путь в файле превращается в нечитаемый набор. А файл
                // затевался ровно ради того, чтобы его открывали блокнотом и
                // читали глазами.
                StringBuilder sb = new StringBuilder();
                sb.append("# Настройки приложений «Кристаллы Раздора».\n")
                  .append("# Файл можно править блокнотом и переносить между машинами.\n")
                  .append("# Раздел replay2 — окно, тема, масштаб; paths — папки.\n\n");
                java.util.List<String> keys = new java.util.ArrayList<>(
                    DATA.stringPropertyNames());
                java.util.Collections.sort(keys);
                for (String k : keys) {
                    sb.append(escape(k, true)).append('=')
                      .append(escape(DATA.getProperty(k), false)).append('\n');
                }
                try (OutputStream out = Files.newOutputStream(file)) {
                    out.write(sb.toString().getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
                }
                dirty = false;
            } catch (IOException e) {
                System.out.println("[НАСТРОЙКИ] не записал " + file + ": " + e.getMessage());
            }
        }
    }
}
