package kelium.gui.replay2;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import kelium.dataio.GameConfig;
import kelium.report.FieldGeometry;
import kelium.report.FieldPainter;
import kelium.report.Java2DCanvas;
import kelium.report.ReplayRecord;
import kelium.report.Textures;

/**
 * СНИМОК ЖЕТОНОВ НА ГЕКСЕ — картинка для главы «Основы игры».
 *
 * <p>Показывает, как встаёт на гекс каждый род войск и здание: пехота и вышка
 * в один сектор, техника в два соседних, авиация в небо, здание — в столько
 * секторов подряд, сколько на нём изображено.
 *
 * <p>Рисуется ТЕМ ЖЕ {@link FieldPainter}, что и поле в проигрывателе: гекс,
 * сектора и посадка жетонов не могут разойтись с игрой. Состояние собирается
 * руками — целая партия для одного гекса не нужна.
 *
 * <p>Запуск: {@code java -cp gui/target/kelium-runner.jar
 * kelium.gui.replay2.СнимокЖетонов <куда.png> [радиус гекса в точках]}
 */
public final class СнимокЖетонов {

    private СнимокЖетонов() {
    }

    /** Одна клетка картинки: подпись и что на гексе стоит. */
    private record Клетка(String подпись, List<ReplayRecord.Tok> жетоны, int[] стороны) {
    }

    private static int uid = 1;

    private static ReplayRecord.Tok войско(String тип, int seat) {
        ReplayRecord.Tok t = new ReplayRecord.Tok();
        t.uid = uid++;
        t.owner = seat;
        t.type = тип;
        t.hexId = "h0_0";
        t.hp = "tower".equals(тип) ? 2 : 1;
        return t;
    }

    private static ReplayRecord.Tok здание(String тип, int seat, int ячеек) {
        ReplayRecord.Tok t = new ReplayRecord.Tok();
        t.uid = uid++;
        t.owner = seat;
        t.building = true;
        t.type = тип;
        t.hexId = "h0_0";
        t.hp = 3;
        t.energySlots = ячеек;
        t.energyPlaced = ячеек;
        return t;
    }

    public static void main(String[] args) throws Exception {
        Theme.apply(false);
        Path out = Path.of(args.length > 0 ? args[0] : "войска-на-гексе.png");
        double size = args.length > 1 ? Double.parseDouble(args[1]) : 150;
        Textures.useFolder(GameConfig.resolveDataRoot(null).resolve("textures"));

        List<Клетка> клетки = new ArrayList<>();
        клетки.add(new Клетка("пехота", List.of(войско("infantry", 0)), null));
        клетки.add(new Клетка("техника", List.of(войско("vehicle", 0)), null));
        клетки.add(new Клетка("авиация", List.of(войско("aircraft", 0)), null));
        клетки.add(new Клетка("вышка", List.of(войско("tower", 0)), null));
        // Здание занимает столько секторов подряд, сколько на нём изображено.
        ReplayRecord.Tok казарма = здание("barracks", 0, 1);
        клетки.add(new Клетка("здание на 2 сектора", List.of(казарма),
            new int[]{казарма.uid, казарма.uid, -1, -1, -1, -1}));
        ReplayRecord.Tok цу = здание("command_center", 0, 1);
        клетки.add(new Клетка("гекс целиком", List.of(цу,
            войско("infantry", 0), войско("vehicle", 0), войско("aircraft", 0)),
            new int[]{цу.uid, цу.uid, -1, -1, -1, -1}));

        BufferedImage img = нарисовать(клетки, size);
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        System.out.println("[жетоны] " + out.toAbsolutePath() + " "
            + img.getWidth() + "×" + img.getHeight());
    }

    private static BufferedImage нарисовать(List<Клетка> клетки, double size) {
        int колонок = 3;
        int строк = (клетки.size() + колонок - 1) / колонок;
        double ш = 2 * size;                       // ширина гекса «плашмя вверх»
        double в = Math.sqrt(3) * size;
        int зазорX = (int) Math.round(size * 0.34);
        int подпись = (int) Math.round(size * 0.40);
        int клW = (int) Math.round(ш) + зазорX;
        int клH = (int) Math.round(в) + подпись + (int) Math.round(size * 0.20);
        BufferedImage img = new BufferedImage(колонок * клW, строк * клH,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FieldPainter.dark = false;
        FieldPainter.showCardboard = false;      // одиночный гекс, а не кусок поля

        Font шрифт = new Font("Tektur Narrow", Font.BOLD, (int) Math.round(size * 0.21));
        for (int i = 0; i < клетки.size(); i++) {
            Клетка к = клетки.get(i);
            double cx = (i % колонок) * клW + ш / 2 + зазорX / 2.0;
            double cy = (i / колонок) * клH + в / 2 + size * 0.10;

            ReplayRecord.HexInfo hi = new ReplayRecord.HexInfo();
            hi.id = "h0_0";
            hi.q = 0;
            hi.r = 0;
            ReplayRecord.HexState st = new ReplayRecord.HexState();
            st.id = "h0_0";
            if (к.стороны() != null) {
                st.sideOwner = к.стороны();
            }
            FieldPainter.paintHex(new Java2DCanvas(g, 1, шрифт), size, hi, st,
                к.жетоны(), cx, cy, false);
            границыСекторов(g, cx, cy, size);

            g.setFont(шрифт);
            g.setColor(new Color(0x2A2318));
            int w = g.getFontMetrics().stringWidth(к.подпись());
            g.drawString(к.подпись(), (int) Math.round(cx - w / 2.0),
                (int) Math.round(cy + в / 2 + подпись * 0.72));
        }
        g.dispose();
        return обрезать(img);
    }

    /**
     * ГРАНИЦЫ МЕЖДУ СЕКТОРАМИ — шесть лучей от воздушной ячейки к вершинам.
     * На печатном гексе они нарисованы, а рисовальщик поля их не чертит: там
     * границу видно по самим жетонам. Здесь же вся картинка про то, сколько
     * секторов занимает жетон, и без линий её не прочесть.
     */
    private static void границыСекторов(Graphics2D g, double cx, double cy, double size) {
        g.setColor(new Color(0x2A2318));
        g.setStroke(new java.awt.BasicStroke((float) (size * 0.012)));
        for (int s = 0; s < 6; s++) {
            double a = Math.toRadians(FieldGeometry.edgeAngle(s) + 30);
            double r0 = size * 0.30;
            double r1 = size * 0.965;
            g.draw(new java.awt.geom.Line2D.Double(cx + r0 * Math.cos(a), cy + r0 * Math.sin(a),
                cx + r1 * Math.cos(a), cy + r1 * Math.sin(a)));
        }
    }

    /** Убрать пустые поля по краям — картинка идёт в книгу как есть. */
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
        int поле = 6;
        x0 = Math.max(0, x0 - поле);
        y0 = Math.max(0, y0 - поле);
        x1 = Math.min(im.getWidth() - 1, x1 + поле);
        y1 = Math.min(im.getHeight() - 1, y1 + поле);
        return im.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }
}
