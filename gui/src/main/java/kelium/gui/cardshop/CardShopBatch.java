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
        if (in.isDirectory() && new File(in, "OBJECTIVE.yaml").isFile() || in.getName().endsWith(".yaml")) {
            library(in, out);
            return;
        }
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

    /** Каталоги библиотеки → <out>/<ТИП>/NN.png. */
    private static void library(File in, File out) throws Exception {
        Library lib = new Library(in.isDirectory() ? in.getParentFile() : in.getParentFile().getParentFile());
        CardAssets a = new CardAssets();
        for (CardSpec.Type t : CardSpec.Type.values()) {
            if (in.getName().endsWith(".yaml") && !in.getName().equals(t.name() + ".yaml")) {
                continue;
            }
            java.util.List<CardSpec> l = lib.list(t);
            if (l.isEmpty()) {
                continue;
            }
            File dir = new File(out, t.name());
            dir.mkdirs();
            int ok = 0;
            for (int i = 0; i < l.size(); i++) {
                CardSpec c = l.get(i);
                c.fields.put("_раскладка_типа", lib.layout(t));
                try {
                    javax.imageio.ImageIO.write(CardRender.render(a, c), "png",
                        new File(dir, String.format("%02d.png", i + 1)));
                    ok++;
                } catch (Exception e) {
                    System.out.println(t + " " + (i + 1) + ": " + e.getMessage());
                }
                c.fields.remove("_раскладка_типа");
            }
            System.out.println(t + ": " + ok + " из " + l.size());
        }
    }
}
