package kelium.gui.replay2;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import kelium.dataio.GameConfig;
import kelium.report.FieldGeometry;
import kelium.report.FieldPainter;
import kelium.report.Java2DCanvas;
import kelium.report.ReplayRecord;
import kelium.report.Textures;

/**
 * КУСОК ПОЛЯ ДЛЯ ИЛЛЮСТРАЦИИ — без выносок и подписей, просто вид сверху.
 *
 * <p>Там, где в книге стояли заглушки под художественную графику, поле честнее
 * показать самим движком: сектора, стенки и посадка жетонов тогда не могут
 * разойтись с игрой (просьба дизайнера 15.09.2026).
 *
 * <p>Сцены:
 * <ul>
 *   <li>{@code база} — гекс с базой игрока за стенкой и чужие войска рядом;</li>
 *   <li>{@code поле} — что лежит на поле: тайл зарождения, нейтральная
 *       постройка, круг энергии, печатный контейнер, недоступный гекс.</li>
 * </ul>
 *
 * <p>Запуск: {@code java -cp gui/target/kelium-runner.jar
 * kelium.gui.replay2.СнимокПоля <сцена> <куда.png> [радиус гекса]}
 */
public final class СнимокПоля {

    private СнимокПоля() {
    }

    private static int uid = 1;

    private static final class Сцена {
        int[][] клетки;
        final Map<String, ReplayRecord.HexState> состояния = new LinkedHashMap<>();
        final List<ReplayRecord.Tok> жетоны = new ArrayList<>();
    }

    private static ReplayRecord.Tok жетон(String тип, int seat, String hex, boolean здание) {
        ReplayRecord.Tok t = new ReplayRecord.Tok();
        t.uid = uid++;
        t.owner = seat;
        t.type = тип;
        t.hexId = hex;
        t.building = здание;
        t.hp = здание ? 3 : 1;
        return t;
    }

    private static ReplayRecord.HexState гекс(Сцена с, int q, int r) {
        ReplayRecord.HexState h = new ReplayRecord.HexState();
        h.id = "h" + q + "_" + r;
        с.состояния.put(h.id, h);
        return h;
    }

    /** Гекс с базой игрока: ЦУ, казарма и добытчик; рядом чужие войска. */
    private static Сцена база() {
        uid = 1;
        Сцена с = new Сцена();
        с.клетки = new int[][]{{0, 0}, {1, 0}, {1, -1}};
        for (int[] qr : с.клетки) {
            гекс(с, qr[0], qr[1]);
        }
        ReplayRecord.Tok цу = жетон("command_center", 0, "h0_0", true);
        цу.energySlots = 1;
        цу.energyPlaced = 1;
        ReplayRecord.Tok казарма = жетон("barracks", 0, "h0_0", true);
        казарма.energySlots = 1;
        казарма.energyPlaced = 1;
        ReplayRecord.Tok вышка = жетон("tower", 0, "h0_0", false);
        с.жетоны.add(цу);
        с.жетоны.add(казарма);
        с.жетоны.add(вышка);
        с.состояния.get("h0_0").sideOwner = new int[]{
            цу.uid, цу.uid, вышка.uid, казарма.uid, казарма.uid, -1};
        с.состояния.get("h0_0").ownerTint = 0;
        с.состояния.get("h0_0").ownerBuilt = true;

        ReplayRecord.Tok чужаяП = жетон("infantry", 1, "h1_0", false);
        ReplayRecord.Tok чужаяТ = жетон("vehicle", 1, "h1_0", false);
        с.жетоны.add(чужаяП);
        с.жетоны.add(чужаяТ);
        с.состояния.get("h1_0").sideOwner = new int[]{
            -1, -1, чужаяП.uid, чужаяТ.uid, чужаяТ.uid, -1};

        ReplayRecord.Tok авиация = жетон("aircraft", 0, "h1_-1", false);
        с.жетоны.add(авиация);
        return с;
    }

    /** Что лежит на поле: тайл, нейтральная постройка, круг энергии, контейнер. */
    private static Сцена поле() {
        uid = 1;
        Сцена с = new Сцена();
        с.клетки = new int[][]{{0, 0}, {1, 0}, {1, -1}, {2, -1}};
        for (int[] qr : с.клетки) {
            гекс(с, qr[0], qr[1]);
        }
        ReplayRecord.Spawn sp = new ReplayRecord.Spawn();
        sp.kelium = 4;
        sp.start = false;
        с.состояния.get("h0_0").spawn = sp;

        ReplayRecord.Neutral нейтр = new ReplayRecord.Neutral();
        нейтр.big = true;
        нейтр.corners.add(1);
        нейтр.corners.add(2);
        нейтр.hp = 2;
        нейтр.hpMax = 2;
        с.состояния.get("h1_0").neutrals.add(нейтр);
        с.состояния.get("h1_0").energyCell = 0;

        с.состояния.get("h1_-1").containerCell = 1;
        // НЕЙТРАЛЬНАЯ ПОСТРОЙКА РИСУЕТСЯ ОТ ДВУХ УГЛОВ: с одним FieldPainter
        // её молча пропускает (paintNeutral: corners.size() < 2).
        ReplayRecord.Neutral малый = new ReplayRecord.Neutral();
        малый.corners.add(4);
        малый.corners.add(5);
        малый.hp = 1;
        малый.hpMax = 1;
        с.состояния.get("h2_-1").neutrals.add(малый);
        с.состояния.get("h2_-1").energyCell = 2;
        return с;
    }

    public static void main(String[] args) throws Exception {
        Theme.apply(false);
        String имя = args.length > 0 ? args[0] : "база";
        Path out = Path.of(args.length > 1 ? args[1] : имя + ".png");
        double size = args.length > 2 ? Double.parseDouble(args[2]) : 190;
        Textures.useFolder(GameConfig.resolveDataRoot(null).resolve("textures"));

        Сцена с = "поле".equals(имя) ? поле() : база();
        BufferedImage img = нарисовать(size, с);
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        System.out.println("[поле] " + имя + " " + out.toAbsolutePath() + " "
            + img.getWidth() + "x" + img.getHeight());
    }

    private static BufferedImage нарисовать(double size, Сцена с) {
        double minx = Double.MAX_VALUE;
        double miny = Double.MAX_VALUE;
        double maxx = -Double.MAX_VALUE;
        double maxy = -Double.MAX_VALUE;
        for (int[] qr : с.клетки) {
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            minx = Math.min(minx, c[0] - size);
            maxx = Math.max(maxx, c[0] + size);
            miny = Math.min(miny, c[1] - Math.sqrt(3) / 2 * size);
            maxy = Math.max(maxy, c[1] + Math.sqrt(3) / 2 * size);
        }
        int поле = (int) Math.round(size * 0.14);
        BufferedImage img = new BufferedImage(
            (int) Math.round(maxx - minx) + поле * 2,
            (int) Math.round(maxy - miny) + поле * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FieldPainter.dark = false;
        FieldPainter.showCardboard = true;
        Font мелкий = new Font("Tektur Narrow", Font.PLAIN, (int) Math.round(size * 0.16));

        double cx0 = поле - minx;
        double cy0 = поле - miny;
        for (int[] qr : с.клетки) {
            String id = "h" + qr[0] + "_" + qr[1];
            ReplayRecord.HexInfo hi = new ReplayRecord.HexInfo();
            hi.id = id;
            hi.q = qr[0];
            hi.r = qr[1];
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            boolean[] соседи = new boolean[6];
            for (int s = 0; s < 6; s++) {
                int[] d = kelium.core.Field.AXIAL_DIRS[s];
                соседи[s] = с.состояния.containsKey(
                    "h" + (qr[0] + d[0]) + "_" + (qr[1] + d[1]));
            }
            List<ReplayRecord.Tok> свои = new ArrayList<>();
            for (ReplayRecord.Tok tk : с.жетоны) {
                if (id.equals(tk.hexId)) {
                    свои.add(tk);
                }
            }
            FieldPainter.paintHex(new Java2DCanvas(g, 1, мелкий), size, hi,
                с.состояния.get(id), свои, cx0 + c[0], cy0 + c[1], false, соседи);
        }
        g.dispose();
        return обрезать(img);
    }

    private static BufferedImage обрезать(BufferedImage im) {
        int x0 = im.getWidth();
        int y0 = im.getHeight();
        int x1 = 0;
        int y1 = 0;
        for (int y = 0; y < im.getHeight(); y++) {
            for (int x = 0; x < im.getWidth(); x++) {
                if ((im.getRGB(x, y) >>> 24) > 8) {
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 <= x0 || y1 <= y0) {
            return im;
        }
        int поле = 8;
        x0 = Math.max(0, x0 - поле);
        y0 = Math.max(0, y0 - поле);
        x1 = Math.min(im.getWidth() - 1, x1 + поле);
        y1 = Math.min(im.getHeight() - 1, y1 + поле);
        return im.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }
}
