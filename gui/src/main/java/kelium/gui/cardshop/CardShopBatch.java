package kelium.gui.cardshop;

import java.io.File;

/**
 * Нарисовать карты мастерской без окна: {@code CardShopBatch <папка или .kcard> [папка вывода]}.
 * Каждая {@code X.kcard} становится {@code X.png} — так выпуски и проверки
 * рисуются тем же кодом, что и окно.
 */
public final class CardShopBatch {

    private CardShopBatch() {
    }

    public static void main(String[] args) throws Exception {
        File in = new File(args[0]);
        File out = args.length > 1 ? new File(args[1]) : null;
        File[] specs = in.isDirectory() ? in.listFiles((d, n) -> n.endsWith(".kcard"))
            : new File[] {in};
        int ok = 0;
        for (File f : specs == null ? new File[0] : specs) {
            File dir = out != null ? out : f.getParentFile();
            dir.mkdirs();
            File png = new File(dir, f.getName().replace(".kcard", ".png"));
            try {
                CardShop.renderFile(f, png);
                ok++;
            } catch (Exception e) {
                System.out.println(f.getName() + ": " + e.getMessage());
            }
        }
        System.out.println("нарисовано " + ok);
    }
}
