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
 * БОЙ ОДНИМ КАДРОМ — выбранный гекс и атака по нему с двух соседних.
 *
 * <p>Бой описан словами, а показать его было нечем (замечание дизайнера
 * 15.09.2026). Здесь видно главное: гекс выбирают ОДИН, бьют по нему
 * со ВСЕХ соседних гексов, и жетоны никогда не атакуют гекс, на котором
 * стоят сами (глава 9).
 *
 * <p>Поле рисует {@link FieldPainter} — тот же, что и настоящую партию.
 *
 * <p>Запуск: {@code java -cp gui/target/kelium-runner.jar
 * kelium.gui.replay2.СнимокБоя <куда.png> [радиус гекса]}
 */
public final class СнимокБоя {

    private СнимокБоя() {
    }

    /** Выбранный гекс и два соседних, с которых идёт атака. */
    private static final int[] ЦЕЛЬ = {0, 0};
    private static final int[][] КЛЕТКИ = {{0, 0}, {1, 0}, {1, -1}};

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
        Path out = Path.of(args.length > 0 ? args[0] : "бой.png");
        double size = args.length > 1 ? Double.parseDouble(args[1]) : 150;
        Textures.useFolder(GameConfig.resolveDataRoot(null).resolve("textures"));

        Map<String, ReplayRecord.HexState> состояния = new LinkedHashMap<>();
        for (int[] qr : КЛЕТКИ) {
            ReplayRecord.HexState h = new ReplayRecord.HexState();
            h.id = "h" + qr[0] + "_" + qr[1];
            состояния.put(h.id, h);
        }
        List<ReplayRecord.Tok> жетоны = new ArrayList<>();
        // Выбранный гекс: чужие пехота и техника. Зданий на нём нет — иначе
        // наземные атаки шли бы только по зданию (глава 9).
        ReplayRecord.Tok врагП = жетон("infantry", 1, "h0_0");
        ReplayRecord.Tok врагТ = жетон("vehicle", 1, "h0_0");
        врагТ.damage = 1;
        // Соседние гексы: наши войска.
        ReplayRecord.Tok нашаТ = жетон("vehicle", 0, "h1_0");
        ReplayRecord.Tok нашаП = жетон("infantry", 0, "h1_-1");
        ReplayRecord.Tok нашаВ = жетон("tower", 0, "h1_-1");
        жетоны.add(врагП);
        жетоны.add(врагТ);
        жетоны.add(нашаТ);
        жетоны.add(нашаП);
        жетоны.add(нашаВ);
        // Стороны гекса не размечаются: в sideOwner место только зданиям,
        // а войска FieldPainter рассаживает сам по свободным сторонам.

        // КТО КОГО БЬЁТ (дизайнер 05.10.2026: «показать, какое войско кого точно
        // атакует, от начала до конца ломаной линией»). Номера — те же, что у
        // врезок с планшета войск под текстом примера.
        атаки.clear();
        атаки.add(new Атака(центр(size, состояния, жетоны, нашаТ), центр(size, состояния, жетоны, врагП),
            new int[]{1, 0}, "1", 0.0));
        атаки.add(new Атака(центр(size, состояния, жетоны, нашаП), центр(size, состояния, жетоны, врагТ),
            new int[]{1, -1}, "2", -0.22));
        атаки.add(new Атака(центр(size, состояния, жетоны, нашаВ), центр(size, состояния, жетоны, врагТ),
            new int[]{1, -1}, "3", 0.22));
        BufferedImage img = обрезать(нарисовать(size, состояния, жетоны, true));
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        System.out.println("[бой] " + out.toAbsolutePath() + " "
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
        for (Атака а : атаки) {
            выстрел(g, cx0, cy0, size, а);
        }
        g.dispose();
        return img;
    }

    /** Атака: центр атакующего жетона, центр цели, его гекс, номер, сдвиг вдоль стороны. */
    private record Атака(double[] от, double[] до, int[] гекс, String номер, double вдоль) { }

    private static final List<Атака> атаки = new ArrayList<>();
    private static final Color ОРЕОЛ = new Color(0xF7, 0xF1, 0xE1, 230);
    private static final Color АТАКА = new Color(0xB03A2E);

    /**
     * Центр жетона на картинке: сцена как есть против той же сцены, где у этого
     * жетона другой хозяин. Место от цвета не зависит, меняется только его
     * рамка — её середина и есть центр. (Убрать жетон или нарисовать его
     * одного нельзя: FieldPainter рассаживает войска с оглядкой на соседей.)
     */
    private static double[] центр(double size, Map<String, ReplayRecord.HexState> состояния,
            List<ReplayRecord.Tok> все, ReplayRecord.Tok tk) {
        BufferedImage пусто = нарисовать(size, состояния, все, false);
        int был = tk.owner;
        tk.owner = (был + 2) % 4;
        BufferedImage один = нарисовать(size, состояния, все, false);
        tk.owner = был;
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
     * Стрелка атаки ломаной: от атакующего жетона через общую сторону гексов
     * (со сдвигом вдоль неё, чтобы две атаки с одного гекса не слились) до
     * жетона-цели. Тонкая, со светлым ореолом; номер — у начала.
     */
    private static void выстрел(Graphics2D g, double cx0, double cy0, double size, Атака а) {
        double[] p = FieldGeometry.hexCenter(а.гекс()[0], а.гекс()[1], size);
        double[] q = FieldGeometry.hexCenter(ЦЕЛЬ[0], ЦЕЛЬ[1], size);
        double ex = q[0] - p[0];
        double ey = q[1] - p[1];
        double el = Math.hypot(ex, ey);
        double[] м = {cx0 + (p[0] + q[0]) / 2 - ey / el * size * а.вдоль(),
            cy0 + (p[1] + q[1]) / 2 + ex / el * size * а.вдоль()};
        double[] a = сдвиг(а.от(), м, size * 0.10);
        double[] z0 = а.до();
        double dx = z0[0] - м[0];
        double dy = z0[1] - м[1];
        double len = Math.hypot(dx, dy);
        double ux = dx / len;
        double uy = dy / len;
        double[] z = {z0[0] - ux * size * 0.08, z0[1] - uy * size * 0.08};
        double остриё = size * 0.17;
        Path2D линия = new Path2D.Double();
        линия.moveTo(a[0], a[1]);
        линия.lineTo(м[0], м[1]);
        линия.lineTo(z[0] - ux * остриё * 0.6, z[1] - uy * остриё * 0.6);
        Path2D нос = new Path2D.Double();
        нос.moveTo(z[0], z[1]);
        нос.lineTo(z[0] - ux * остриё + uy * остриё * 0.55, z[1] - uy * остриё - ux * остриё * 0.55);
        нос.lineTo(z[0] - ux * остриё - uy * остриё * 0.55, z[1] - uy * остриё + ux * остриё * 0.55);
        нос.closePath();
        float толщ = (float) (size * 0.034);
        g.setColor(ОРЕОЛ);
        g.setStroke(new BasicStroke(толщ * 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(линия);
        g.draw(нос);
        g.fill(нос);
        g.setColor(АТАКА);
        g.setStroke(new BasicStroke(толщ, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(линия);
        g.fill(нос);
        double rт = size * 0.045;
        g.setColor(ОРЕОЛ);
        g.fill(new java.awt.geom.Ellipse2D.Double(a[0] - rт * 1.8, a[1] - rт * 1.8, rт * 3.6, rт * 3.6));
        g.setColor(АТАКА);
        g.fill(new java.awt.geom.Ellipse2D.Double(a[0] - rт, a[1] - rт, 2 * rт, 2 * rт));
        // номер атаки — на середине первого отрезка, сбоку
        double ddx = м[0] - а.от()[0];
        double ddy = м[1] - а.от()[1];
        double dl = Math.hypot(ddx, ddy);
        double вбок = size * 0.17 * (а.вдоль() > 0 ? -1 : 1);
        double[] н = {(а.от()[0] + м[0]) / 2 + ddy / dl * вбок, (а.от()[1] + м[1]) / 2 - ddx / dl * вбок};
        double r = size * 0.12;
        g.setColor(ОРЕОЛ);
        g.fill(new java.awt.geom.Ellipse2D.Double(н[0] - r * 1.25, н[1] - r * 1.25, r * 2.5, r * 2.5));
        g.setColor(АТАКА);
        g.fill(new java.awt.geom.Ellipse2D.Double(н[0] - r, н[1] - r, 2 * r, 2 * r));
        g.setColor(Color.WHITE);
        g.setFont(new Font("Tektur Narrow", Font.BOLD, (int) Math.round(size * 0.18)));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(а.номер(), (float) (н[0] - fm.stringWidth(а.номер()) / 2.0),
            (float) (н[1] + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
    }

    private static double[] сдвиг(double[] от, double[] к, double на) {
        double dx = к[0] - от[0];
        double dy = к[1] - от[1];
        double len = Math.hypot(dx, dy);
        return new double[]{от[0] + dx / len * на, от[1] + dy / len * на};
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
        g.setColor(new Color(0xB03A2E));
        g.draw(шестиугольник);
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
