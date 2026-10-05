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

    private static ReplayRecord.Tok жетон(String тип, int seat, String hex, int... стороны) {
        ReplayRecord.Tok t = new ReplayRecord.Tok();
        t.uid = uid++;
        t.owner = seat;
        t.type = тип;
        t.hexId = hex;
        t.hp = 1;
        if (стороны.length > 0) {
            t.sides = new ArrayList<>();
            for (int st : стороны) {
                t.sides.add(st);
            }
        }
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
        // СТАРЫЕ МЕСТА — ЖЕТОНЫ, НОВЫЕ — ПОЛУПРОЗРАЧНЫЙ СЛЕД (дизайнер 04.10.2026):
        // рисунок показывает, как всё лежит до манёвра и куда жетоны придут.
        List<ReplayRecord.Tok> жетоны = new ArrayList<>();
        жетоны.add(жетон("tower", 0, "h0_0", 1));            // остаётся
        жетоны.add(жетон("infantry", 0, "h0_0", 4));         // 1 — уходит
        жетоны.add(жетон("vehicle", 0, "h0_-1", 3, 4));      // 2 — придёт с соседнего
        жетоны.add(жетон("infantry", 0, "h2_-1", 0));        // 3 — придёт в обход
        жетоны.add(жетон("infantry", 1, "h1_0", 2));         // чужая пехота закрывает путь
        List<ReplayRecord.Tok> след = new ArrayList<>();
        след.add(жетон("infantry", 0, "h-1_1", 1));
        след.add(жетон("vehicle", 0, "h0_0", 5, 0));
        след.add(жетон("infantry", 0, "h0_0", 2));
        BufferedImage пусто = нарисовать(size, состояния, List.of(), false);
        // ПУТЬ ОТ ЖЕТОНА ДО ЖЕТОНА (дизайнер 05.10.2026: «вести стрелки прямо от
        // исконного места жетона вплоть до его конечного положения, а не просто
        // указывать на гекс»). Где именно лёг жетон, решает FieldPainter —
        // поэтому каждый жетон рисуется отдельно и ищется по отличию от пустого поля.
        ходы.clear();
        ходы.add(new Ход(центр(size, состояния, пусто, жетоны.get(1)),
            центр(size, состояния, пусто, след.get(0)), new int[][]{{0, 0}, {-1, 1}}, "1"));
        ходы.add(new Ход(центр(size, состояния, пусто, жетоны.get(2)),
            центр(size, состояния, пусто, след.get(1)), new int[][]{{0, -1}, {0, 0}}, "2", true));
        ходы.add(new Ход(центр(size, состояния, пусто, жетоны.get(3)),
            центр(size, состояния, пусто, след.get(2)), new int[][]{{2, -1}, {1, -1}, {0, 0}}, "3"));
        BufferedImage основа = нарисовать(size, состояния, жетоны, true);
        BufferedImage призраки = нарисовать(size, состояния, след, false);
        // где «призраки» отличаются от пустого поля — там след: подмешать на 45%
        for (int y = 0; y < основа.getHeight(); y++) {
            for (int x = 0; x < основа.getWidth(); x++) {
                int e = пусто.getRGB(x, y);
                int c = призраки.getRGB(x, y);
                if (Math.abs(((e >> 16) & 255) - ((c >> 16) & 255)) + Math.abs(((e >> 8) & 255) - ((c >> 8) & 255))
                        + Math.abs((e & 255) - (c & 255)) < 18) {
                    continue;
                }
                int a = основа.getRGB(x, y);
                double k = 0.45;
                int r = (int) (((a >> 16) & 255) * (1 - k) + ((c >> 16) & 255) * k);
                int gg = (int) (((a >> 8) & 255) * (1 - k) + ((c >> 8) & 255) * k);
                int bb = (int) ((a & 255) * (1 - k) + (c & 255) * k);
                основа.setRGB(x, y, (a & 0xFF000000) | (r << 16) | (gg << 8) | bb);
            }
        }
        BufferedImage img = обрезать(основа);
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        System.out.println("[манёвр] " + out.toAbsolutePath() + " "
            + img.getWidth() + "x" + img.getHeight());
    }

    private static BufferedImage нарисовать(double size,
            Map<String, ReplayRecord.HexState> состояния, List<ReplayRecord.Tok> жетоны,
            boolean стрелки) {
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
        FieldPainter.книжнаяТолщина = true;
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

        if (!стрелки) {
            g.dispose();
            return img;
        }
        double[] цель = FieldGeometry.hexCenter(ЦЕЛЬ[0], ЦЕЛЬ[1], size);
        обвести(g, cx0 + цель[0], cy0 + цель[1], size);
        // 1 — пехота уходит; 2 — техника приходит с соседнего; 3 — пехота в обход
        for (Ход х : ходы) {
            путь(g, cx0, cy0, size, х);
        }
        g.dispose();
        return img;
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

    /** Ход жетона: откуда (центр жетона), куда (центр следа), через какие гексы. */
    private record Ход(double[] от, double[] до, int[][] гексы, String номер, boolean черезНебо) {
        Ход(double[] от, double[] до, int[][] гексы, String номер) {
            this(от, до, гексы, номер, false);
        }
    }

    private static final List<Ход> ходы = new ArrayList<>();
    private static final Color ОРЕОЛ = new Color(0xF7, 0xF1, 0xE1, 230);

    /** Центр жетона на картинке: рисуем его одного и ищем отличие от пустого поля. */
    private static double[] центр(double size, Map<String, ReplayRecord.HexState> состояния,
            BufferedImage пусто, ReplayRecord.Tok tk) {
        BufferedImage один = нарисовать(size, состояния, List.of(tk), false);
        double sx = 0;
        double sy = 0;
        long n = 0;
        for (int y = 0; y < один.getHeight(); y++) {
            for (int x = 0; x < один.getWidth(); x++) {
                int e = пусто.getRGB(x, y);
                int c = один.getRGB(x, y);
                if (Math.abs(((e >> 16) & 255) - ((c >> 16) & 255)) + Math.abs(((e >> 8) & 255) - ((c >> 8) & 255))
                        + Math.abs((e & 255) - (c & 255)) > 40) {
                    sx += x;
                    sy += y;
                    n++;
                }
            }
        }
        return n == 0 ? new double[]{0, 0} : new double[]{sx / n, sy / n};
    }

    /**
     * Путь жетона ломаной: от центра жетона на старом месте через середины
     * общих сторон гексов (и центры гексов, через которые он проходит) до
     * центра следа. Линия тонкая, со светлым ореолом, как выноски на развороте
     * подготовки (дизайнер 05.10.2026).
     */
    private static void путь(Graphics2D g, double cx0, double cy0, double size, Ход х) {
        List<double[]> т = new ArrayList<>();
        т.add(х.от());
        for (int i = 0; i < х.гексы().length - 1; i++) {
            double[] p = FieldGeometry.hexCenter(х.гексы()[i][0], х.гексы()[i][1], size);
            double[] q = FieldGeometry.hexCenter(х.гексы()[i + 1][0], х.гексы()[i + 1][1], size);
            if (i > 0) {
                т.add(new double[]{cx0 + p[0], cy0 + p[1]});
            }
            double mx = cx0 + (p[0] + q[0]) / 2;
            double my = cy0 + (p[1] + q[1]) / 2;
            if (х.черезНебо() && i == х.гексы().length - 2) {
                // середину стороны занимает чужой след — идём ближе к левому
                // углу этой стороны
                double ex = q[0] - p[0];
                double ey = q[1] - p[1];
                double el = Math.hypot(ex, ey);
                double px = -ey / el * size * 0.32;
                double py = ex / el * size * 0.32;
                if (px > 0) {
                    px = -px;
                    py = -py;
                }
                mx += px;
                my += py;
            }
            т.add(new double[]{mx, my});
        }
        if (х.черезНебо()) {
            // через пустое небо в середине гекса — не перечёркивать чужие следы
            int[] к = х.гексы()[х.гексы().length - 1];
            double[] c = FieldGeometry.hexCenter(к[0], к[1], size);
            т.add(new double[]{cx0 + c[0], cy0 + c[1]});
        }
        т.add(х.до());
        // начало и конец — у края жетона, а не в самой середине картинки
        double[] a = сдвиг(т.get(0), т.get(1), size * 0.12);
        double[] z = т.get(т.size() - 1);
        double[] пред = т.get(т.size() - 2);
        double dx = z[0] - пред[0];
        double dy = z[1] - пред[1];
        double len = Math.hypot(dx, dy);
        double ux = dx / len;
        double uy = dy / len;
        z = new double[]{z[0] - ux * size * 0.10, z[1] - uy * size * 0.10};
        double остриё = size * 0.17;
        Path2D линия = new Path2D.Double();
        линия.moveTo(a[0], a[1]);
        for (int i = 1; i < т.size() - 1; i++) {
            линия.lineTo(т.get(i)[0], т.get(i)[1]);
        }
        линия.lineTo(z[0] - ux * остриё * 0.6, z[1] - uy * остриё * 0.6);
        Path2D нос = new Path2D.Double();
        нос.moveTo(z[0], z[1]);
        нос.lineTo(z[0] - ux * остриё + uy * остриё * 0.55, z[1] - uy * остриё - ux * остриё * 0.55);
        нос.lineTo(z[0] - ux * остриё - uy * остриё * 0.55, z[1] - uy * остриё + ux * остриё * 0.55);
        нос.closePath();
        float толщ = (float) (size * 0.034);
        // ореол
        g.setColor(ОРЕОЛ);
        g.setStroke(new BasicStroke(толщ * 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(линия);
        g.draw(нос);
        g.fill(нос);
        g.setColor(ХОД);
        g.setStroke(new BasicStroke(толщ, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(линия);
        g.fill(нос);
        // точка на старом месте
        double rт = size * 0.045;
        g.setColor(ОРЕОЛ);
        g.fill(new java.awt.geom.Ellipse2D.Double(a[0] - rт * 1.8, a[1] - rт * 1.8, rт * 3.6, rт * 3.6));
        g.setColor(ХОД);
        g.fill(new java.awt.geom.Ellipse2D.Double(a[0] - rт, a[1] - rт, 2 * rт, 2 * rт));
        // номер шага — у первого излома, сбоку от линии
        double[] p0 = т.get(0);
        double[] p1 = т.get(1);
        double ddx = p1[0] - p0[0];
        double ddy = p1[1] - p0[1];
        double dl = Math.hypot(ddx, ddy);
        double вбок = size * 0.20;
        double[] м = {(p0[0] + p1[0]) / 2 + ddy / dl * вбок, (p0[1] + p1[1]) / 2 - ddx / dl * вбок};
        double r = size * 0.13;
        g.setColor(ОРЕОЛ);
        g.fill(new java.awt.geom.Ellipse2D.Double(м[0] - r * 1.25, м[1] - r * 1.25, r * 2.5, r * 2.5));
        g.setColor(ХОД);
        g.fill(new java.awt.geom.Ellipse2D.Double(м[0] - r, м[1] - r, 2 * r, 2 * r));
        g.setColor(Color.WHITE);
        g.setFont(new Font("Tektur Narrow", Font.BOLD, (int) Math.round(size * 0.19)));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(х.номер(), (float) (м[0] - fm.stringWidth(х.номер()) / 2.0),
            (float) (м[1] + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
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
