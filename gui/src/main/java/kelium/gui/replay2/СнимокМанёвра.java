package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
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
 * МАНЁВР ОДНИМ КАДРОМ — пример к главе 8 (замечание дизайнера 02.10.2026:
 * «нет вообще примера движения»).
 *
 * <p>Выбран один гекс (пунктир). С него сначала уходит пехота, потом на него
 * приходят техника с соседнего гекса и пехота издалека — в обход гекса, где
 * стоят чужие войска: сквозь них наземные войска не проходят. Вышка остаётся:
 * её скорость 0.
 *
 * <p>Запуск: {@code java -cp gui/target/kelium-runner.jar
 * kelium.gui.replay2.СнимокМанёвра <куда.png> [радиус гекса]}
 */
public final class СнимокМанёвра {

    private СнимокМанёвра() {
    }

    /** Выбранный гекс и гексы вокруг: откуда приходят и куда уходят. */
    private static final int[] ЦЕЛЬ = {0, 0};
    private static final int[][] КЛЕТКИ = {{0, 0}, {-1, 1}, {0, -1}, {1, -1}, {2, -1}, {1, 0}};
    private static final Color ХОД = new Color(0x1F5FA8);

    private static int uid = 1;

    private static ReplayRecord.Tok жетон(String тип, int seat, String hex) {
        ReplayRecord.Tok t = new ReplayRecord.Tok();
        t.uid = uid++;
        t.owner = seat;
        t.type = тип;
        t.hexId = hex;
        t.hp = 1;
        return t;
    }

    public static void main(String[] args) throws Exception {
        Theme.apply(false);
        Path out = Path.of(args.length > 0 ? args[0] : "манёвр.png");
        double size = args.length > 1 ? Double.parseDouble(args[1]) : 150;
        Textures.useFolder(GameConfig.resolveDataRoot(null).resolve("textures"));

        Map<String, ReplayRecord.HexState> состояния = new LinkedHashMap<>();
        for (int[] qr : КЛЕТКИ) {
            ReplayRecord.HexState h = new ReplayRecord.HexState();
            h.id = "h" + qr[0] + "_" + qr[1];
            состояния.put(h.id, h);
        }
        List<ReplayRecord.Tok> жетоны = new ArrayList<>();
        // Выбранный гекс: наша вышка (остаётся) и пехота (уходит).
        жетоны.add(жетон("tower", 0, "h0_0"));
        // Пехота, которая уйдёт, — нарисована уже на новом месте.
        жетоны.add(жетон("infantry", 0, "h-1_1"));
        // Пришедшие: техника с соседнего гекса и пехота через гекс.
        жетоны.add(жетон("vehicle", 0, "h0_0"));
        жетоны.add(жетон("infantry", 0, "h0_0"));
        // Чужая пехота закрывает короткий путь.
        жетоны.add(жетон("infantry", 1, "h1_0"));
        BufferedImage img = нарисовать(size, состояния, жетоны);
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        System.out.println("[манёвр] " + out.toAbsolutePath() + " "
            + img.getWidth() + "x" + img.getHeight());
    }

    private static BufferedImage нарисовать(double size,
            Map<String, ReplayRecord.HexState> состояния, List<ReplayRecord.Tok> жетоны) {
        double minx = Double.MAX_VALUE;
        double miny = Double.MAX_VALUE;
        double maxx = -Double.MAX_VALUE;
        double maxy = -Double.MAX_VALUE;
        for (int[] qr : КЛЕТКИ) {
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            minx = Math.min(minx, c[0] - size);
            maxx = Math.max(maxx, c[0] + size);
            miny = Math.min(miny, c[1] - Math.sqrt(3) / 2 * size);
            maxy = Math.max(maxy, c[1] + Math.sqrt(3) / 2 * size);
        }
        int поле = (int) Math.round(size * 0.22);
        int шапка = (int) Math.round(size * 0.52);
        int низ = (int) Math.round(size * 0.30);
        BufferedImage img = new BufferedImage(
            (int) Math.round(maxx - minx) + поле * 2,
            (int) Math.round(maxy - miny) + шапка + низ, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FieldPainter.dark = false;
        FieldPainter.showCardboard = false;
        Font мелкий = new Font("Tektur Narrow", Font.PLAIN, (int) Math.round(size * 0.185));

        double cx0 = поле - minx;
        double cy0 = шапка - miny;
        for (int[] qr : КЛЕТКИ) {
            String id = "h" + qr[0] + "_" + qr[1];
            ReplayRecord.HexInfo hi = new ReplayRecord.HexInfo();
            hi.id = id;
            hi.q = qr[0];
            hi.r = qr[1];
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            boolean[] соседи = new boolean[6];
            for (int s = 0; s < 6; s++) {
                int[] d = kelium.core.Field.AXIAL_DIRS[s];
                соседи[s] = состояния.containsKey(
                    "h" + (qr[0] + d[0]) + "_" + (qr[1] + d[1]));
            }
            List<ReplayRecord.Tok> свои = new ArrayList<>();
            for (ReplayRecord.Tok tk : жетоны) {
                if (id.equals(tk.hexId)) {
                    свои.add(tk);
                }
            }
            FieldPainter.paintHex(new Java2DCanvas(g, 1, мелкий), size, hi,
                состояния.get(id), свои, cx0 + c[0], cy0 + c[1], false, соседи);
        }

        double[] цель = FieldGeometry.hexCenter(ЦЕЛЬ[0], ЦЕЛЬ[1], size);
        обвести(g, cx0 + цель[0], cy0 + цель[1], size);
        // 1 — пехота уходит; 2 — техника приходит с соседнего; 3 — пехота в обход
        путь(g, cx0, cy0, size, new int[][]{{0, 0}, {-1, 1}}, "1");
        путь(g, cx0, cy0, size, new int[][]{{0, -1}, {0, 0}}, "2");
        путь(g, cx0, cy0, size, new int[][]{{2, -1}, {1, -1}, {0, 0}}, "3");
        g.dispose();
        return обрезать(img);
    }

    /** Пунктирная обводка ВЫБРАННОГО гекса. */
    private static void обвести(Graphics2D g, double cx, double cy, double size) {
        Path2D шестиугольник = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60.0 * i);
            double x = cx + size * 0.94 * Math.cos(a);
            double y = cy + size * 0.94 * Math.sin(a);
            if (i == 0) {
                шестиугольник.moveTo(x, y);
            } else {
                шестиугольник.lineTo(x, y);
            }
        }
        шестиугольник.closePath();
        g.setStroke(new BasicStroke((float) (size * 0.055), BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND, 10f,
            new float[]{(float) (size * 0.17), (float) (size * 0.11)}, 0f));
        g.setColor(ХОД);
        g.draw(шестиугольник);
    }

    /**
     * Путь жетона по центрам гексов: ломаная, на конце — стрелка, у начала —
     * номер шага (порядок из правил: сначала вывести, потом ввести).
     */
    private static void путь(Graphics2D g, double cx0, double cy0, double size,
            int[][] гексы, String номер) {
        double[][] т = new double[гексы.length][];
        for (int i = 0; i < гексы.length; i++) {
            double[] c = FieldGeometry.hexCenter(гексы[i][0], гексы[i][1], size);
            т[i] = new double[]{cx0 + c[0], cy0 + c[1]};
        }
        // концы отодвинуты от центров, чтобы не закрывать жетоны
        double отступ = size * 0.42;
        double[] a = сдвиг(т[0], т[1], отступ);
        double[] z = сдвиг(т[т.length - 1], т[т.length - 2], отступ);
        Path2D линия = new Path2D.Double();
        линия.moveTo(a[0], a[1]);
        for (int i = 1; i < т.length - 1; i++) {
            линия.lineTo(т[i][0], т[i][1]);
        }
        double[] пред = т[т.length - 2];
        double dx = z[0] - (т.length > 2 ? пред[0] : a[0]);
        double dy = z[1] - (т.length > 2 ? пред[1] : a[1]);
        double len = Math.hypot(dx, dy);
        double ux = dx / len;
        double uy = dy / len;
        double остриё = size * 0.26;
        линия.lineTo(z[0] - ux * остриё * 0.5, z[1] - uy * остриё * 0.5);
        g.setStroke(new BasicStroke((float) (size * 0.075), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        g.setColor(ХОД);
        g.draw(линия);
        Path2D нос = new Path2D.Double();
        нос.moveTo(z[0], z[1]);
        нос.lineTo(z[0] - ux * остриё + uy * остриё * 0.55, z[1] - uy * остриё - ux * остриё * 0.55);
        нос.lineTo(z[0] - ux * остриё - uy * остриё * 0.55, z[1] - uy * остриё + ux * остриё * 0.55);
        нос.closePath();
        g.fill(нос);
        // номер шага — кружок у начала пути
        double r = size * 0.15;
        g.setColor(ХОД);
        g.fill(new java.awt.geom.Ellipse2D.Double(a[0] - r, a[1] - r, 2 * r, 2 * r));
        g.setColor(Color.WHITE);
        g.setFont(new Font("Tektur Narrow", Font.BOLD, (int) Math.round(size * 0.22)));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(номер, (float) (a[0] - fm.stringWidth(номер) / 2.0),
            (float) (a[1] + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
    }

    private static double[] сдвиг(double[] от, double[] к, double на) {
        double dx = к[0] - от[0];
        double dy = к[1] - от[1];
        double len = Math.hypot(dx, dy);
        return new double[]{от[0] + dx / len * на, от[1] + dy / len * на};
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
