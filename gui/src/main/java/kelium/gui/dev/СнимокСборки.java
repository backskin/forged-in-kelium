package kelium.gui.dev;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import kelium.gui.AssemblyWindow;
import kelium.gui.LayoutEditor;
import kelium.gui.replay2.Theme;

/**
 * СНИМОК ВКЛАДКИ «СБОРКА ИЗ БЛОКОВ» В ДВУХ ТЕМАХ — без живого окна.
 *
 * <p>ЗАЧЕМ. Дизайнер увидел, что полотно сборки не перекрашивается: в тёмной
 * теме оно оставалось белым листом. Проверять это мышью нельзя (правило
 * 30.08.2026: прогонщики не забирают фокус), поэтому вкладка собирается в
 * памяти, рисуется методом {@code printAll} — ровно тем, чем Swing рисует её
 * на экран, — и складывается в два PNG, светлый и тёмный.
 *
 * <p>Полотно берёт краски во время отрисовки, поэтому снимок ловит именно ту
 * ошибку, что видна глазом: если фон или контуры остались печатными, на тёмном
 * снимке будет белое пятно.
 *
 * <p>Запуск: {@code kelium.gui.dev.СнимокСборки [папка] [ширина] [высота]}
 */
public final class СнимокСборки {

    private СнимокСборки() {
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        int w = args.length > 1 ? Integer.parseInt(args[1]) : 1100;
        int h = args.length > 2 ? Integer.parseInt(args[2]) : 700;
        if (!dir.exists() && !dir.mkdirs()) {
            System.out.println("не смог создать папку " + dir);
            return;
        }

        for (boolean метки : new boolean[]{false, true}) {
            BufferedImage в = выгрузка(метки, w, h);
            File файл = new File(dir, "выгрузка-" + (метки ? "картонки" : "контуры") + ".png");
            ImageIO.write(в, "png", файл);
            System.out.println(файл.getName() + "   " + в.getWidth() + "x" + в.getHeight());
            for (boolean dark : new boolean[]{false, true}) {
                BufferedImage img = снимок(dark, метки, w, h);
                File out = new File(dir, "сборка-" + (метки ? "картонки-" : "контуры-")
                    + (dark ? "тёмная" : "светлая") + ".png");
                ImageIO.write(img, "png", out);
                System.out.println(out.getName() + "   фон "
                    + описатьЦвет(img.getRGB(w / 2, h - 60)));
            }
        }
    }

    /**
     * СНИМОК ВЫГРУЗКИ, а не экрана: тот же путь, которым делается PNG.
     *
     * <p>Проверять надо именно его. На экране подписи блоков стоят по краю
     * поля и всегда влезают, а в выгрузке кадр свой, масштаб свой, и подпись
     * может уехать за край — ровно на это дизайнер и жаловался.
     */
    private static BufferedImage выгрузка(boolean метки, int w, int h) throws Exception {
        final BufferedImage[] держатель = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            Theme.apply(false);
            AssemblyWindow вкладка = new AssemblyWindow(поле());
            вкладка.перекрасить();
            вкладка.setSize(900, 600);
            вкладка.doLayout();
            вкладка.refresh();
        });
        Thread.sleep(1200);
        SwingUtilities.invokeAndWait(() -> { });
        final AssemblyWindow[] в = new AssemblyWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            AssemblyWindow вкладка = new AssemblyWindow(поле());
            вкладка.перекрасить();
            вкладка.setSize(900, 600);
            вкладка.doLayout();
            вкладка.refresh();
            в[0] = вкладка;
        });
        Thread.sleep(1200);
        SwingUtilities.invokeAndWait(() -> {
            в[0].показыватьМетки(метки);
            держатель[0] = в[0].renderField(w, h);
        });
        return держатель[0];
    }

    private static BufferedImage снимок(boolean dark, boolean метки, int w, int h)
            throws Exception {
        final AssemblyWindow[] держатель = new AssemblyWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            Theme.apply(dark);
            AssemblyWindow вкладка = new AssemblyWindow(поле());
            вкладка.перекрасить();
            // Компонент вне окна: размер и раскладку задаём руками, иначе
            // printAll нарисует пустоту 0x0.
            JPanel корень = new JPanel(new java.awt.BorderLayout());
            корень.add(вкладка);
            корень.setSize(w, h);
            корень.doLayout();
            вкладка.setSize(w, h);
            вкладка.doLayout();
            вкладка.refresh();
            вкладка.показыватьМетки(метки);
            держатель[0] = вкладка;
        });
        // Сборка считается в SwingWorker. Поле из десяти гексов подбирается за
        // единицы миллисекунд (видно в строке состояния окна), но ждём с запасом.
        Thread.sleep(1500);
        final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = img.createGraphics();
            держатель[0].doLayout();
            // printAll — ровно то, чем Swing рисует компонент на экран: снимок
            // ловит те же краски, что видит глаз, а не печатные краски экспорта.
            держатель[0].printAll(g);
            g.dispose();
        });
        return img;
    }

    /** Небольшое поле: пять гексов подряд плюс запретный — блокам есть что крыть. */
    private static LayoutEditor.Model поле() {
        LayoutEditor.Model m = new LayoutEditor.Model();
        int[][] клетки = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1},
                          {0, 2}, {1, 2}, {3, 0}, {3, 1}};
        for (int[] c : клетки) {
            m.hexes.put(LayoutEditor.Model.key(c[0], c[1]),
                new LayoutEditor.LHex(c[0], c[1]));
        }
        return m;
    }

    private static String описатьЦвет(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
