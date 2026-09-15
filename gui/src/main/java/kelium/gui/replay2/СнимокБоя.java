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

        BufferedImage img = нарисовать(size, состояния, жетоны);
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        System.out.println("[бой] " + out.toAbsolutePath() + " "
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
        for (int[] qr : КЛЕТКИ) {
            if (qr[0] == ЦЕЛЬ[0] && qr[1] == ЦЕЛЬ[1]) {
                continue;
            }
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            выстрел(g, cx0 + c[0], cy0 + c[1], cx0 + цель[0], cy0 + цель[1], size);
        }
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
        g.setColor(new Color(0xB03A2E));
        g.draw(шестиугольник);
    }

    /** Стрелка атаки от соседнего гекса к выбранному. */
    private static void выстрел(Graphics2D g, double x0, double y0,
            double x1, double y1, double size) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        double отступ = size * 0.62;
        double ax = x0 + dx / len * отступ;
        double ay = y0 + dy / len * отступ;
        double bx = x1 - dx / len * отступ;
        double by = y1 - dy / len * отступ;
        g.setStroke(new BasicStroke((float) (size * 0.075), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xB03A2E));
        double остриё = size * 0.26;
        g.drawLine((int) Math.round(ax), (int) Math.round(ay),
            (int) Math.round(bx - dx / len * остриё * 0.5),
            (int) Math.round(by - dy / len * остриё * 0.5));
        double ux = dx / len;
        double uy = dy / len;
        Path2D нос = new Path2D.Double();
        нос.moveTo(bx, by);
        нос.lineTo(bx - ux * остриё + uy * остриё * 0.55,
            by - uy * остриё - ux * остриё * 0.55);
        нос.lineTo(bx - ux * остриё - uy * остриё * 0.55,
            by - uy * остриё + ux * остриё * 0.55);
        нос.closePath();
        g.fill(нос);
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
