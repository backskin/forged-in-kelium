package kelium.gui.dev;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import kelium.gui.replay2.Replay2Gui;
import kelium.report.ReplayRecord;

/**
 * РЕЖИМ РАЗРАБОТЧИКА — показать РУЧНОЕ состояние игры в проигрывателе.
 *
 * <p>ЗАЧЕМ ОН ЕСТЬ. Проигрыватель умел открывать только сыгранную партию, поэтому
 * посмотреть на конкретное состояние — гекс, забитый жетонами четырёх видов;
 * подбитую авиабазу на жёлтом секторе; счётчик супероружия на трёх ячейках с
 * повторяющимися символами — можно было только одним способом: играть партии,
 * пока такое не сложится само. Нужное сочетание не складывалось за сотню
 * прогонов, и проверять отрисовку было нечем.
 *
 * <p>Теперь состояние собирается кодом ({@link Scene}), движок снимает с него тот
 * же снимок, что и с настоящей партии ({@link ReplayRecord#snapshotOf}), а
 * проигрыватель показывает его как запись из одного кадра. Ни одной особой ветки
 * в проигрывателе для этого не нужно: он не знает и не должен знать, откуда взялся
 * снимок.
 *
 * <p>Запуск:
 * <pre>
 * java -cp &lt;класспас&gt; kelium.gui.dev.DevMode                 # список сцен
 * java -cp &lt;класспас&gt; kelium.gui.dev.DevMode полный-гекс      # показать в окне
 * java -cp &lt;класспас&gt; kelium.gui.dev.DevMode полный-гекс снимок
 * java -cp &lt;класспас&gt; kelium.gui.dev.DevMode супероружие снимок player
 * java -cp &lt;класспас&gt; kelium.gui.dev.DevMode рынок снимок boards [файл.png]
 * </pre>
 *
 * <p>Экраны: {@code field} (по умолчанию) · {@code boards} — наука и рынок ·
 * {@code supers} — супер-задания · {@code results} — итоги · {@code player} —
 * личная зона игрока в правом ящике.
 *
 * <p>Сцены живут в {@link DevScenes} — добавить новую значит дописать туда один
 * метод. Именно так этим и надо пользоваться: сцена — это несколько строк, а не
 * файл настроек.
 */
public final class DevMode {

    private DevMode() {
    }

    /** Куда падают снимки по умолчанию. */
    private static final Path SHOTS = Path.of("gui", "target", "dev-shots");

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        if (args.length == 0) {
            System.out.println("СЦЕНЫ РЕЖИМА РАЗРАБОТЧИКА:");
            for (String name : DevScenes.names()) {
                System.out.println("  " + name + "\t— " + DevScenes.about(name));
            }
            System.out.println();
            System.out.println("показать в окне:  DevMode <сцена>");
            System.out.println("снять картинку:   DevMode <сцена> снимок [экран] [файл.png]");
            System.out.println("  экраны: field · boards · supers · results · player");
            System.out.println("все картинки:     DevMode всё снимок");
            return;
        }
        boolean shot = args.length > 1 && args[1].startsWith("сним");
        if ("всё".equals(args[0]) || "все".equals(args[0])) {
            for (String name : DevScenes.names()) {
                Path out = SHOTS.resolve(name + ".png");
                shoot(DevScenes.build(name), out, null);
                System.out.println("снято: " + out.toAbsolutePath());
            }
            System.exit(0);
        }
        Scene scene = DevScenes.build(args[0]);
        if (scene == null) {
            System.out.println("нет такой сцены: " + args[0]
                + " (список — запуск без аргументов)");
            return;
        }
        if (shot) {
            // Третий аргумент — ЭКРАН: field (по умолчанию), boards (наука и
            // рынок), supers (супер-задания), results (итоги), player (личная
            // зона игрока в правом ящике).
            String stage = args.length > 2 ? args[2] : null;
            Path out = args.length > 3 ? Path.of(args[3])
                : SHOTS.resolve(args[0] + (stage == null ? "" : "-" + stage) + ".png");
            shoot(scene, out, stage);
            System.out.println("снято: " + out.toAbsolutePath());
            System.exit(0);
        }
        show(scene);
    }

    /**
     * Показать сцену в окне проигрывателя.
     *
     * <p>Окно поднимается тем же путём, что и обычно, и получает готовую запись —
     * поэтому в проигрывателе работает всё, что работает для настоящей партии:
     * планшеты, ящик справа, экран итогов, переключение темы.
     */
    public static void show(Scene scene) {
        kelium.dataio.Locations.applyDataFolder();
        applyTheme();
        SwingUtilities.invokeLater(() -> {
            try {
                Replay2Gui gui = raise();
                setRecord(gui, scene.record());
                frameOf(gui).setTitle("РЕЖИМ РАЗРАБОТЧИКА · " + scene.titleText());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Снять сцену в PNG, ничего не показывая человеку.
     *
     * @param stage какой экран открыть перед снимком: {@code null} или
     *     {@code field} — поле; {@code boards} — наука и рынок; {@code supers} —
     *     супер-задания; {@code results} — итоги; {@code player} — личная зона
     *     игрока в правом ящике. Без этого снять можно было только поле, а
     *     половина того, что надо разглядывать (счётчик супероружия, планшет
     *     рынка), живёт на других экранах.
     */
    public static void shoot(Scene scene, Path out, String stage) throws Exception {
        kelium.dataio.Locations.applyDataFolder();
        applyTheme();
        final Replay2Gui[] box = new Replay2Gui[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                box[0] = raise();
                setRecord(box[0], scene.record());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        JFrame frame = frameOf(box[0]);
        SwingUtilities.invokeAndWait(() -> {
            frame.setSize(1920, 1200);
            frame.validate();
        });
        // Дать Swing разложить содержимое: снимок, взятый сразу, выходит пустым.
        Thread.sleep(700);
        if (stage != null && !"field".equals(stage)) {
            openStage(box[0], stage);
            Thread.sleep(500);
        }
        BufferedImage img = new BufferedImage(frame.getWidth(), frame.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> frame.paint(img.createGraphics()));
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        ImageIO.write(img, "png", new File(out.toString()));
    }

    /**
     * ТЕМА СЦЕНЫ — СВЕТЛАЯ ПО УМОЛЧАНИЮ.
     *
     * <p>Режим разработчика существует, чтобы РАЗГЛЯДЫВАТЬ отрисовку: обводку
     * цифр, полоски прочности, символы на ячейках. На светлом это видно лучше, а
     * на снимке для ревью — заметно лучше. Тёмная включается запуском
     * {@code -Dkelium.theme=dark}.
     */
    private static void applyTheme() {
        String forced = System.getProperty("kelium.theme", "light");
        kelium.gui.replay2.Theme.apply("dark".equals(forced));
    }

    // ==================================================================
    //  ШВЫ В ПРОИГРЫВАТЕЛЬ
    // ==================================================================
    //  Отражение здесь — не лень, а граница: показ сцены нужен ТОЛЬКО режиму
    //  разработчика, и открывать ради него публичный вход в проигрыватель значит
    //  предлагать этот вход всем остальным. Тот же приём уже применяет
    //  Replay2Shot, которым снимаются картинки для ревью.

    private static Replay2Gui raise() throws Exception {
        Replay2Gui gui = new Replay2Gui();
        Method show = Replay2Gui.class.getDeclaredMethod("show");
        show.setAccessible(true);
        show.invoke(gui);
        return gui;
    }

    private static void setRecord(Replay2Gui gui, ReplayRecord rec) throws Exception {
        Field sf = Replay2Gui.class.getDeclaredField("session");
        sf.setAccessible(true);
        Object session = sf.get(gui);
        Method set = session.getClass().getMethod("setRecord", ReplayRecord.class);
        set.invoke(session, rec);
        Method loadRules = Replay2Gui.class.getDeclaredMethod("loadRules", ReplayRecord.class);
        loadRules.setAccessible(true);
        loadRules.invoke(gui, rec);
    }

    /** Открыть нужный экран или ящик перед снимком. */
    private static void openStage(Replay2Gui gui, String stage) throws Exception {
        if ("player".equals(stage)) {
            Method open = Replay2Gui.class.getDeclaredMethod("openDrawer",
                kelium.gui.replay2.Drawer.View.class);
            open.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    open.invoke(gui, kelium.gui.replay2.Drawer.View.PLAYER);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            return;
        }
        Method show = Replay2Gui.class.getDeclaredMethod("showStage", String.class);
        show.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                show.invoke(gui, stage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static JFrame frameOf(Replay2Gui gui) throws Exception {
        Field ff = Replay2Gui.class.getDeclaredField("frame");
        ff.setAccessible(true);
        return (JFrame) ff.get(gui);
    }

    /** Имена сцен — для сообщений об ошибке и подсказок. */
    static List<String> sceneNames() {
        return DevScenes.names();
    }
}
