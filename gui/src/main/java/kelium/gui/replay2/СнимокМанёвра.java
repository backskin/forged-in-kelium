package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
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
 * МАНЁВР ОДНИМ КАДРОМ — куда жетон уходит и куда ему хода нет.
 *
 * <p>Дизайнер 16.09.2026: «нет примера манёвра, пример боя лежит под текстом
 * манёвра», и отдельно — «добавь стрелки на каждой картинке, показывающие ГДЕ
 * и с чем что происходит». Здесь три стрелки на четырёх гексах:
 * <ul>
 *   <li>пехота со скоростью 1 уходит на соседний гекс, где стоят чужие
 *       наземные войска, — им это не мешает;</li>
 *   <li>авиация со скоростью 2 перелетает этот гекс насквозь;</li>
 *   <li>наземному хода нет в гекс, где в небе стоит чужая авиация —
 *       стрелка перечёркнута.</li>
 * </ul>
 *
 * <p>Поле рисует {@link FieldPainter} — тот же, что и настоящую партию.
 * Координаты середин стрелок печатаются в stdout строками «МЕТКА буква x y»,
 * чтобы сборщик рисунка поставил выноски ровно на них, а не по угаданным
 * числам.
 *
 * <p>Запуск: {@code java -cp gui/target/kelium-runner.jar
 * kelium.gui.replay2.СнимокМанёвра <куда.png> [радиус гекса]}
 */
public final class СнимокМанёвра {

    private СнимокМанёвра() {
    }

    private static final int[][] КЛЕТКИ = {{0, 0}, {1, 0}, {2, 0}, {1, -1}};

    private static int uid = 1;

    /** Смещение обрезки прозрачных полей — вычитается из координат выносок. */
    private static int срезX;
    private static int срезY;

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
        Path out = Path.of(args.length > 0 ? args[0] : "manoeuvre.png");
        double size = args.length > 1 ? Double.parseDouble(args[1]) : 150;
        Textures.useFolder(GameConfig.resolveDataRoot(null).resolve("textures"));

        Map<String, ReplayRecord.HexState> состояния = new LinkedHashMap<>();
        for (int[] qr : КЛЕТКИ) {
            ReplayRecord.HexState h = new ReplayRecord.HexState();
            h.id = "h" + qr[0] + "_" + qr[1];
            состояния.put(h.id, h);
        }
        List<ReplayRecord.Tok> жетоны = new ArrayList<>();
        жетоны.add(жетон("infantry", 0, "h0_0"));
        жетоны.add(жетон("aircraft", 0, "h0_0"));
        // ЧУЖОЙ ПЕХОТЫ НА ПУТИ НЕТ. Сначала она тут стояла, и подпись обещала,
        // что наземные друг другу не мешают, — а правило обратное: чужие войска
        // гекс ЗАПИРАЮТ (глава 4, Placement.enemyUnitsLockHex). Картинка учила
        // ходу, которого в игре нет. Осталась только чужая авиация в небе —
        // ровно тот случай, ради которого перечёркнутая стрелка и нарисована.
        жетоны.add(жетон("aircraft", 1, "h1_-1"));

        BufferedImage img = нарисовать(size, состояния, жетоны);
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        System.out.println("[maneuver] " + out.toAbsolutePath() + " "
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

        double[] старт = FieldGeometry.hexCenter(0, 0, size);
        double[] рядом = FieldGeometry.hexCenter(1, 0, size);
        double[] далеко = FieldGeometry.hexCenter(2, 0, size);
        double[] небо = FieldGeometry.hexCenter(1, -1, size);
        // ПЕХОТА и АВИАЦИЯ идут из одного гекса в ОДНУ сторону: прямые стрелки
        // легли бы одна на другую и обе стали бы нечитаемы. Поэтому дальняя
        // (авиация, скорость 2) выгнута дугой в сторону, а ближняя идёт прямо.
        double[] метА = стрелка(g, cx0 + старт[0], cy0 + старт[1],
            cx0 + рядом[0], cy0 + рядом[1], size, new Color(0x2E7D32), false, 0);
        double[] метБ = стрелка(g, cx0 + старт[0], cy0 + старт[1],
            cx0 + далеко[0], cy0 + далеко[1], size, new Color(0x1565C0), false,
            size * 0.78);
        double[] метВ = стрелка(g, cx0 + старт[0], cy0 + старт[1],
            cx0 + небо[0], cy0 + небо[1], size, new Color(0xB03A2E), true, 0);
        g.dispose();
        BufferedImage готово = обрезать(img);
        System.out.println("MARK A " + Math.round(метА[0] - срезX)
            + " " + Math.round(метА[1] - срезY));
        System.out.println("MARK B " + Math.round(метБ[0] - срезX)
            + " " + Math.round(метБ[1] - срезY));
        System.out.println("MARK V " + Math.round(метВ[0] - срезX)
            + " " + Math.round(метВ[1] - срезY));
        return готово;
    }

    /**
     * Стрелка манёвра. Возвращает точку своей середины — на неё встанет выноска.
     *
     * @param перечёркнута крест поверх середины: этого хода нет
     * @param прогиб насколько вынести дугу в сторону от прямой; 0 — прямая
     */
    private static double[] стрелка(Graphics2D g, double x0, double y0,
            double x1, double y1, double size, Color цвет, boolean перечёркнута,
            double прогиб) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        double отступ = size * 0.52;
        double ax = x0 + dx / len * отступ;
        double ay = y0 + dy / len * отступ;
        double bx = x1 - dx / len * отступ;
        double by = y1 - dy / len * отступ;
        // Точка изгиба — на перпендикуляре к середине отрезка.
        double mx = (ax + bx) / 2 - dy / len * прогиб;
        double my = (ay + by) / 2 + dx / len * прогиб;
        g.setStroke(new BasicStroke((float) (size * 0.07), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        g.setColor(цвет);
        java.awt.geom.QuadCurve2D дуга =
            new java.awt.geom.QuadCurve2D.Double(ax, ay, mx * 2 - (ax + bx) / 2,
                my * 2 - (ay + by) / 2, bx, by);
        g.draw(дуга);
        double остриё = size * 0.24;
        // Острие смотрит по касательной в конце дуги, а не по хорде.
        double угол = Math.atan2(by - my, bx - mx);
        if (!перечёркнута) {
            for (int знак = -1; знак <= 1; знак += 2) {
                double a = угол + знак * Math.toRadians(26);
                g.drawLine((int) Math.round(bx), (int) Math.round(by),
                    (int) Math.round(bx - остриё * Math.cos(a)),
                    (int) Math.round(by - остриё * Math.sin(a)));
            }
        }
        if (перечёркнута) {
            // КРЕСТ СТОИТ НА КОНЦЕ, А НЕ ПОСЕРЕДИНЕ: посередине он съедал саму
            // стрелку, и от неё на рисунке не оставалось ничего, кроме креста.
            double к = size * 0.13;
            g.setStroke(new BasicStroke((float) (size * 0.085), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            g.drawLine((int) Math.round(bx - к), (int) Math.round(by - к),
                (int) Math.round(bx + к), (int) Math.round(by + к));
            g.drawLine((int) Math.round(bx - к), (int) Math.round(by + к),
                (int) Math.round(bx + к), (int) Math.round(by - к));
        }
        return new double[]{mx, my};
    }

    /** Обрезать прозрачные поля; смещение запоминается для координат выносок. */
    private static BufferedImage обрезать(BufferedImage src) {
        int x0 = src.getWidth();
        int y0 = src.getHeight();
        int x1 = -1;
        int y1 = -1;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                if ((src.getRGB(x, y) >>> 24) != 0) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 < 0) {
            срезX = 0;
            срезY = 0;
            return src;
        }
        срезX = x0;
        срезY = y0;
        return src.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }
}
