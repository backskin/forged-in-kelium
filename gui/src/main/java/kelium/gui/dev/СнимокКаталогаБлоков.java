package kelium.gui.dev;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import kelium.gui.BlockCatalogPanel;

/**
 * СНИМОК КАТАЛОГА МОДУЛЕЙ ПОЛЯ — без живого окна.
 *
 * <p>Каталог показывает все двадцать сторон картонных модулей. С печатным артом
 * (см. {@code data/textures/block}) проверять надо ровно одно: садится ли
 * картинка на гексы — не съехала, не повернулась не туда, не вылезла за
 * карточку. Мышью это проверять нельзя (правило 30.08.2026: прогонщики не
 * забирают фокус), поэтому панель собирается в памяти и рисуется в PNG.
 *
 * <p>Запуск: {@code kelium.gui.dev.СнимокКаталогаБлоков [папка] [ширина] [высота] [поворот]}
 */
public final class СнимокКаталогаБлоков {

    private СнимокКаталогаБлоков() {
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        int w = args.length > 1 ? Integer.parseInt(args[1]) : 1600;
        int h = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
        int поворот = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        // ВЕРСИЯ НАБОРА ПО УМОЛЧАНИЮ — та, по которой нарисован печатный арт:
        // каталог сам открывается на другой, а смотреть надо на картон.
        String версия = args.length > 4 ? args[4] : "5.0.0";
        if (!dir.exists() && !dir.mkdirs()) {
            System.out.println("не смог создать папку " + dir);
            return;
        }
        kelium.dataio.Locations.applyDataFolder();
        java.nio.file.Path данные = kelium.dataio.GameConfig.resolveDataRoot(null);

        final BufferedImage[] box = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            kelium.gui.replay2.Theme.apply(false);
            BlockCatalogPanel panel = new BlockCatalogPanel(данные);
            if (версия != null && !panel.показатьВерсию(версия)) {
                System.out.println("нет версии набора " + версия);
            }
            for (int i = 0; i < поворот; i++) {
                panel.повернуть(true);
            }
            // Компонент вне окна не раскладывается сам: размеры задаём руками
            // сверху вниз, иначе printAll нарисует пустоту (тот же приём, что в
            // СнимокСборки — там же и объяснено, почему без окна).
            javax.swing.JPanel корень = new javax.swing.JPanel(new java.awt.BorderLayout());
            корень.add(panel);
            корень.setSize(w, h);
            panel.setSize(w, h);
            for (int i = 0; i < 3; i++) {
                разложить(корень);
            }
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            var g = img.createGraphics();
            g.setColor(kelium.gui.replay2.Theme.bg());
            g.fillRect(0, 0, w, h);
            panel.printAll(g);
            g.dispose();
            box[0] = img;
        });
        File out = new File(dir, "каталог-модулей" + (поворот == 0 ? "" : "-п" + поворот)
            + ".png");
        ImageIO.write(box[0], "png", out);
        System.out.println("снято: " + out.getAbsolutePath());
    }

    /** Разложить дерево компонентов сверху вниз: окна нет, само не разложится. */
    private static void разложить(java.awt.Component c) {
        c.doLayout();
        if (c instanceof java.awt.Container k) {
            for (java.awt.Component ch : k.getComponents()) {
                разложить(ch);
            }
        }
    }
}
