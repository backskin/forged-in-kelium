# ЗАКАЗ — масштабирование интерфейса KeliumReplay2

**Дата:** 14.08.2026
**Жалоба дизайнера:** на 27″ при 100 % масштабе Windows приложение выглядит нормально,
на 15″ при 125 % — всё слишком крупное, верхняя панель настроек не влезает и обрезается.

Ниже — разбор причины и полный план правки. Код не менялся; это инструкция для того,
кто будет править (правки затрагивают файлы, которые может держать другой компьютер).

---

## 1. Корень зла — ДВОЙНОЕ масштабирование

Файл: `java-sim/src/main/java/kelium/gui/replay2/Theme.java`, строки ~43–68.

```java
private static final double SCALE = computeScale();

private static double computeScale() {
    int dpi = Toolkit.getDefaultToolkit().getScreenResolution();   // 120 при 125 %
    return Math.max(1.0, Math.min(2.0, dpi / 96.0));               // → 1.25
}

public static int px(int design) { return (int) Math.round(design * SCALE); }
```

**Java 9 и старше сама масштабирует окна Swing под DPI Windows.** Она растягивает всю
графику и отдаёт коду «логические» пиксели. То есть при 125 % системного масштаба:

| что | множитель |
|---|---|
| Java применяет сама (незаметно для кода) | ×1,25 |
| `Theme.px()` применяет ещё раз | ×1,25 |
| **итог на экране** | **×1,56** |

Отсюда и «слишком крупное»: на ноутбуке всё нарисовано в полтора раза больше
задуманного. На мониторе 27″ при 100 % `SCALE = 1.0`, второго умножения нет — потому
там и «вроде сойдёт».

Проверить на месте (без сборки, любым JShell/тестом):
`GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
.getDefaultConfiguration().getDefaultTransform().getScaleX()` — если вернёт `1.25`,
Java уже масштабирует и `Theme.px()` обязан работать в ЛОГИЧЕСКИХ единицах.

### Правка 1 — спрашивать у Java, сколько она уже сделала

Заменить блок «масштаб экрана» в `Theme.java` на три раздельных множителя:

```java
// Вёрстка рассчитана на такое окно (в точках сетки) — от него считается автоподбор.
private static final int DESIGN_W = 1500;
private static final int DESIGN_H = 950;

/** Насколько надо добавить СВЕРХ того, что Java уже нарисовала сама. Обычно 1.0. */
private static final double DPI = dpiFactor();
/** Автоподбор под РАЗМЕР рабочего стола: 1.0 — просторно, меньше — тесно. */
private static final double AUTO = autoScale();

/** Ручной множитель: 0 — «авто», иначе доля (1.0 = 100 %). */
private static double userScale;
/** Итог, которым множатся все размеры. */
private static double scale = DPI * AUTO;

private static double dpiFactor() {
    try {
        if (GraphicsEnvironment.isHeadless()) {
            return 1.0;
        }
        int dpi = java.awt.Toolkit.getDefaultToolkit().getScreenResolution();
        double asked = Math.max(1.0, dpi / 96.0);          // сколько просит система
        double done = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice().getDefaultConfiguration()
            .getDefaultTransform().getScaleX();            // сколько уже делает Java
        if (done <= 0) {
            done = 1.0;
        }
        return Math.max(1.0, Math.min(2.0, asked / done));
    } catch (RuntimeException | Error e) {
        return 1.0;
    }
}

private static double autoScale() {
    try {
        if (GraphicsEnvironment.isHeadless()) {
            return 1.0;
        }
        java.awt.Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        // Считаем в ТОЧКАХ ВЁРСТКИ: экранные пиксели делим на поправку DPI, иначе
        // на среде без своего масштабирования сравнение шло бы в другой единице.
        double w = b.width / DPI;
        double h = b.height / DPI;
        if (w <= 0 || h <= 0) {
            return 1.0;
        }
        // НИКОГДА НЕ РАСТЯГИВАЕМ: на большом мониторе размеры остаются авторскими.
        return Math.max(0.7, Math.min(1.0, Math.min(w / DESIGN_W, h / DESIGN_H)));
    } catch (RuntimeException | Error e) {
        return 1.0;
    }
}

/** Ручной множитель: доля (1.0 = 100 %) или 0 — вернуться к автоподбору. */
public static void setUserScale(double value) {
    userScale = value <= 0 ? 0 : Math.max(0.6, Math.min(2.0, value));
    scale = DPI * (userScale == 0 ? AUTO : userScale);
}

public static double userScale() {
    return userScale;
}

/** Что действует сейчас, без поправки на DPI (её человек не выбирал). */
public static double effectiveScale() {
    return userScale == 0 ? AUTO : userScale;
}

/** Что предлагает автоподбор — показать в меню рядом с пунктом «Авто». */
public static double autoScaleValue() {
    return AUTO;
}

public static int px(int design) {
    return (int) Math.round(design * scale);
}

public static float pxf(double design) {
    return (float) (design * scale);
}
```

**Что это даёт по пунктам заказа:**

* **(1)** DPI больше не удваивается: чем крупнее масштаб ОС, тем МЕНЬШЕ добавляет
  приложение. На 27″ 100 % ничего не меняется (`DPI = 1.0`, `AUTO = 1.0`) — вид тот же,
  к которому дизайнер привык.
* Дополнительно автоподбор ужимает вёрстку на ТЕСНОМ экране: 1920×1080 при 125 % — это
  1536×864 логических точки, `AUTO ≈ 0,91`. Верхняя панель после этого влезает.

**Проверка после правки** (числа для 1920×1080 при 125 % Windows):

| было | стало |
|---|---|
| эффективно ×1,56 | эффективно ×0,91 |
| панель настроек обрезана | панель влезает целиком |

---

## 2. Настройка в меню «Вид → Масштаб интерфейса»

Файл: `Replay2Gui.java`, метод `menuBar()`, ~строка 350 (после пункта «Светлая или
тёмная тема»).

### 2.1. Пункт меню

```java
view.add(scaleMenu());
```

```java
/** Шаги масштаба словами. 0 — «как подойдёт экрану» (автоподбор). */
private static final double[][] SCALE_STEPS = {
    {0,    0}, {0.75, 0}, {0.85, 0}, {1.00, 0}, {1.15, 0}, {1.30, 0},
};
private static final String[] SCALE_WORDS = {
    "Авто — под экран", "Очень мелкий", "Мелкий", "Обычный", "Крупный", "Очень крупный",
};

private JMenu scaleMenu() {
    JMenu m = new JMenu("Масштаб интерфейса");
    javax.swing.ButtonGroup g = new javax.swing.ButtonGroup();
    double now = Theme.userScale();
    for (int i = 0; i < SCALE_STEPS.length; i++) {
        double v = SCALE_STEPS[i][0];
        String pct = v == 0
            ? " (" + Math.round(Theme.autoScaleValue() * 100) + " %)"
            : "  ·  " + Math.round(v * 100) + " %";
        javax.swing.JRadioButtonMenuItem mi =
            new javax.swing.JRadioButtonMenuItem(SCALE_WORDS[i] + pct);
        mi.setSelected(Math.abs(now - v) < 0.005);
        mi.addActionListener(e -> applyScale(v));
        g.add(mi);
        m.add(mi);
    }
    m.addSeparator();
    m.add(item("Свой…", null, this::askScale));
    m.addSeparator();
    m.add(item("Крупнее интерфейс", "control shift EQUALS",
        () -> applyScale(nudge(+1))));
    m.add(item("Мельче интерфейс", "control shift MINUS",
        () -> applyScale(nudge(-1))));
    return m;
}

/** Шаг «крупнее/мельче» на 5 % от того, что действует сейчас. */
private double nudge(int dir) {
    return Math.max(0.6, Math.min(2.0,
        Math.round((Theme.effectiveScale() + dir * 0.05) * 100) / 100.0));
}
```

**Важно:** сочетания `control EQUALS` и `control MINUS` уже заняты масштабом ПОЛЯ
(`field.zoomBy`). Для интерфейса взят `control shift`, иначе одна клавиша делала бы два
разных дела.

### 2.2. Окно «Свой…» — ползунок с точной настройкой и видимым числом

Новый файл `java-sim/src/main/java/kelium/gui/replay2/ScaleDialog.java`:

```java
package kelium.gui.replay2;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * ScaleDialog — СВОЙ МАСШТАБ ИНТЕРФЕЙСА: ползунок для «на глаз» и поле с числом
 * для «ровно столько же, как в прошлый раз».
 *
 * <p>Число видно всегда и в двух видах — проценты и коэффициент. Проценты понятны
 * без объяснений, коэффициент совпадает с тем, что лежит в файле настроек: увидел
 * ×1,15 — знаешь, что искать в kelium.cfg.
 */
public final class ScaleDialog {

    private ScaleDialog() {
    }

    /** Показать окно. Вернёт долю (1.0 = 100 %) или null, если человек передумал. */
    public static Double show(java.awt.Window owner, double current) {
        int start = (int) Math.round(Math.max(0.6, Math.min(2.0, current)) * 100);

        JSlider slider = new JSlider(60, 200, start);
        slider.setMajorTickSpacing(20);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setPreferredSize(new java.awt.Dimension(Theme.px(360),
            slider.getPreferredSize().height));

        JSpinner spin = new JSpinner(new SpinnerNumberModel(start, 60, 200, 1));
        spin.setFont(Theme.mono(14, java.awt.Font.BOLD));

        JLabel coeff = new JLabel();
        coeff.setFont(Theme.body());
        Runnable sync = () -> coeff.setText("коэффициент  ×"
            + String.format(java.util.Locale.forLanguageTag("ru"), "%.2f",
                ((Number) spin.getValue()).intValue() / 100.0));

        // Ползунок и поле — ОДНО И ТО ЖЕ ЧИСЛО: правка в любом из них видна в другом.
        boolean[] busy = {false};
        slider.addChangeListener(e -> {
            if (!busy[0]) {
                busy[0] = true;
                spin.setValue(slider.getValue());
                busy[0] = false;
                sync.run();
            }
        });
        spin.addChangeListener(e -> {
            if (!busy[0]) {
                busy[0] = true;
                slider.setValue(((Number) spin.getValue()).intValue());
                busy[0] = false;
                sync.run();
            }
        });
        sync.run();

        JPanel box = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(14) + ", gapy " + Theme.px(8), "[grow,fill]"));
        box.add(Ui2.label("Общий размер всего интерфейса: шрифтов, плиток, отступов."),
            "wrap");
        box.add(slider, "wrap");
        JPanel row = new JPanel(new net.miginfocom.swing.MigLayout("insets 0, gapx "
            + Theme.px(8)));
        row.setOpaque(false);
        row.add(spin);
        row.add(Ui2.label("%"));
        row.add(coeff, "gapx " + Theme.px(16));
        box.add(row, "wrap");
        box.add(Ui2.label("Обычный — 100 %. Экран приложения пересобирается сразу."),
            "wrap");

        int ok = javax.swing.JOptionPane.showConfirmDialog(owner, box,
            "Свой масштаб интерфейса", javax.swing.JOptionPane.OK_CANCEL_OPTION,
            javax.swing.JOptionPane.PLAIN_MESSAGE);
        return ok == javax.swing.JOptionPane.OK_OPTION
            ? ((Number) spin.getValue()).intValue() / 100.0 : null;
    }
}
```

Вызов в `Replay2Gui`:

```java
private void askScale() {
    Double v = ScaleDialog.show(frame, Theme.effectiveScale());
    if (v != null) {
        applyScale(v);
    }
}
```

### 2.3. Применение на ходу — ПЕРЕСБОРКА ОКНА

Размеры запечены в компоненты при создании (`Theme.px()` вызывается в конструкторах,
в строках раскладки MigLayout, в `setPreferredSize`). Менять их «на лету» нечем —
надо собрать окно заново. Партию при этом терять нельзя:

```java
private void applyScale(double value) {
    if (Math.abs(Theme.userScale() - value) < 0.005) {
        return;
    }
    settings.putDouble(Theme.SCALE_KEY, value);   // value == 0 → «авто»
    Theme.setUserScale(value);
    Theme.apply(Theme.isDark());                  // перечитать шрифты в UIManager
    rebuildForScale();
}

/**
 * ПЕРЕСБОРКА ОКНА ПОД НОВЫЙ МАСШТАБ. Собираем новое окно, переносим в него запись
 * и место в ней, и только потом закрываем старое: между двумя окнами не должно
 * быть мгновения, когда живых окон нет — иначе Swing завершит программу.
 */
private void rebuildForScale() {
    ReplayRecord rec = session.record();
    int cursor = session.cursor();
    boolean dark = Theme.isDark();
    HelpWindow.closeIfOpen();          // справочник тоже собран в старом масштабе
    JFrame old = frame;
    Replay2Gui gui = new Replay2Gui();
    gui.show();
    if (rec != null) {
        gui.session.setRecord(rec);
        gui.loadRules(rec);
        gui.session.seek(cursor);
    }
    old.dispose();
    gui.say("Масштаб интерфейса — " + Math.round(Theme.effectiveScale() * 100) + " %"
        + (Theme.userScale() == 0 ? " (подобран под экран)." : "."));
}
```

В `HelpWindow` добавить:

```java
/** Закрыть справочник, если он открыт: при смене масштаба его надо собрать заново. */
public static void closeIfOpen() {
    if (open != null) {
        open.dispose();
        open = null;
        instance = null;
    }
}
```

**Осторожно при пересборке:**
* `frame.setDefaultCloseOperation(EXIT_ON_CLOSE)` — `dispose()` программу НЕ закрывает
  (выход бывает только от закрытия окна человеком), но новое окно обязано появиться
  раньше, чем исчезнет старое.
* Запомненные `winW`/`winH` хранятся в ЭКРАННЫХ пикселях. После смены масштаба их
  разумно пересчитать: `winW * новый / старый`, иначе окно останется прежней ширины,
  а содержимое в нём — другого размера.
* `Theme.apply()` обязательно вызвать заново: он кладёт `defaultFont` и `ScrollBar.width`
  в `UIManager`, а те уже посчитаны через `px()`.

---

## 3. Настройки в файле вместо реестра

Сейчас всё лежит в `Preferences.userRoot().node("kelium/replay2")` — это ветка реестра
Windows. Её не видно глазами, не перенести на другую машину, не починить блокнотом.

Использующие места: `Replay2Gui.java:62, 97`, `HelpWindow.java:63`, `HelpApp.java:30`.
Ключи: `dark`, `spoilerFree`, `drawerWidth`, `winW`, `winH`, `winX`, `winY`,
`helpDivider`, `helpZoom` — плюс новый `ui.scale`.

Новый файл `java-sim/src/main/java/kelium/dataio/AppSettings.java`:

```java
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
 * <p>Файл один на все программы проекта:
 * <pre>%APPDATA%\Kelium\kelium.cfg</pre>
 *
 * <p>Ключи разложены по РАЗДЕЛАМ ({@code раздел.ключ}), раздел выбирает приложение —
 * поэтому проигрыватель, конструктор и справочник не путают свои настройки.
 *
 * <p>Место файла можно задать двумя способами:
 * <ul>
 *   <li>{@code -Dkelium.settings=путь\к\файлу.cfg} — на один запуск;</li>
 *   <li>файл {@code kelium.cfg} РЯДОМ С ПРОГРАММОЙ — тогда настройки переносные:
 *       скопировал папку на флешку и получил ту же настройку на другой машине.</li>
 * </ul>
 *
 * <p>Пишется с задержкой: перетаскивание окна шлёт десятки записей в секунду, и
 * бить по диску на каждую незачем. При выходе всё дописывается принудительно.
 *
 * <p>СТАРЫЕ НАСТРОЙКИ НЕ ТЕРЯЮТСЯ: пока файла нет, содержимое прежней ветки реестра
 * переносится в него при первом запуске.
 */
public final class AppSettings {

    private static final String FILE_NAME = "kelium.cfg";
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

    /** Где лежит файл — для строки «о приложении». */
    public static Path location() {
        load();
        return file;
    }

    public String get(String key, String def) {
        synchronized (LOCK) {
            return DATA.getProperty(section + "." + key, def);
        }
    }

    public void put(String key, String value) {
        synchronized (LOCK) {
            String k = section + "." + key;
            if (value == null) {
                DATA.remove(k);
            } else if (value.equals(DATA.getProperty(k))) {
                return;                       // не изменилось — и писать нечего
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
            // Запятая тоже понимается: файл могли править руками в русской раскладке.
            return Double.parseDouble(get(key, String.valueOf(def)).trim()
                .replace(',', '.'));
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

    public void remove(String key) {
        put(key, null);
    }

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
        Path portable = Paths.get("").toAbsolutePath().resolve(FILE_NAME);
        if (Files.isRegularFile(portable)) {
            return portable;                  // переносной вариант сильнее домашнего
        }
        String appData = System.getenv("APPDATA");
        Path dir = appData != null && !appData.isBlank()
            ? Paths.get(appData, "Kelium")
            : Paths.get(System.getProperty("user.home"), ".config", "kelium");
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
                // реестр недоступен — начнём с настроек по умолчанию
            }
        }
    }

    /** Записать на диск, если что-то поменялось. */
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
                try (OutputStream out = Files.newOutputStream(file)) {
                    DATA.store(new java.io.OutputStreamWriter(out,
                            java.nio.charset.StandardCharsets.UTF_8),
                        "Настройки приложений «Кристаллы Раздора». "
                        + "Файл можно править блокнотом и переносить между машинами.");
                }
                dirty = false;
            } catch (IOException e) {
                System.out.println("[НАСТРОЙКИ] не записал " + file + ": "
                    + e.getMessage());
            }
        }
    }
}
```

### Что поменять в вызывающих местах

`Theme.java` — добавить ключ и загрузчик:

```java
/** Ключ настройки масштаба — один на все окна приложения. */
public static final String SCALE_KEY = "ui.scale";

/** Взять масштаб из настроек. Вызывать ДО сборки первого окна. */
public static void loadScale(kelium.dataio.AppSettings settings) {
    setUserScale(settings.getDouble(SCALE_KEY, 0));
}
```

`Replay2Gui.java`:

```java
// было: private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);
private final AppSettings settings = AppSettings.of("replay2");
```

Дальше по файлу заменить `prefs.` → `settings.` — имена методов те же
(`getInt/putInt/getBoolean/putBoolean`), правки механические: строки 392, 395, 496, 501,
560, 901, 903, 905, 906, 917, 918, 923, 924.

В `main()` (строка ~97):

```java
AppSettings s = AppSettings.of("replay2");
Theme.loadScale(s);                                   // ← ДО Theme.apply
String forced = System.getProperty("kelium.theme", "");
Theme.apply(forced.isBlank() ? s.getBoolean("dark", true) : !"light".equals(forced));
```

То же самое в `HelpApp.java:30` и `HelpWindow.java:63` (раздел там тот же — `replay2`,
чтобы справочник и проигрыватель были одного масштаба и одной темы).

Полезно добавить путь к файлу в «Справка → О приложении»:

```java
+ "\n\nНастройки хранятся в файле: " + kelium.dataio.AppSettings.location()
```

---

## 4. Отдельно: верхняя панель настроек всё равно длинная

Даже после правки масштаба первая строка `SetupPanel` — одна жёсткая строка MigLayout
(`SetupPanel.java:102–217`, спецификация колонок в `cols()`, строка 455). Она не
переносится: не влезло — обрезалось. На узком окне (или если человек поставит крупный
масштаб руками) обрежется снова.

Надёжное лечение — сделать первую строку ПЕРЕНОСИМОЙ: собрать каждую настройку в свою
маленькую группу («игроков: [4]», «сид: [777] 🎲», «правила: […]», «задания: […]»,
«арсенал: […]», «поле: […] 🔀», «другая сборка», «стол…») и разложить группы потоком с
переносом (`FlowLayout`, умеющий считать высоту при переносе, — обычный `WrapLayout`).
Тогда на узком окне строка станет двумя, а не обрежется.

`sizegroup setup`, который сейчас держит четыре списка одной ширины, при этом заменяется
на явную `setPreferredSize(new Dimension(Theme.px(150), …))` у тех же четырёх списков —
вид сохранится.

Это правка на ~60 строк в одном файле и делать её можно отдельно от пунктов 1–3.

---

## 5. Порядок работ и проверка

1. `Theme.java` — множители (пункт 1). Собрать, посмотреть на 15″ 125 %: должно стать
   заметно мельче и панель должна влезть.
2. `AppSettings.java` + замена `Preferences` (пункт 3). Проверить, что после первого
   запуска появился `%APPDATA%\Kelium\kelium.cfg` и в нём лежат прежние `replay2.winW`
   и `replay2.dark`.
3. `ScaleDialog.java` + меню + `rebuildForScale()` (пункт 2). Проверить: выбрал 130 %,
   окно пересобралось, партия и место в ней на месте; перезапустил — масштаб сохранился.
4. `SetupPanel` — перенос строки (пункт 4), если после первых трёх ещё будет тесно.

Сборка exe: `powershell -ExecutionPolicy Bypass -File java-sim\make-exe.ps1`.

**Грабли сборки (попались 14.08.2026):** папка проекта лежит в Яндекс.Диске, и когда
клиент Диска не запущен, Maven падает с «The cloud file provider is not running» на
файлах в `target\`. Лечится удалением `java-sim\target` целиком перед сборкой либо
запуском Яндекс.Диска.
