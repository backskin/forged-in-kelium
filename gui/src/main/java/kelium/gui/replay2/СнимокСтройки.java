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
 * КОНТРПРИМЕРЫ СТРОЙКИ — полоса из четырёх кадров: два «нельзя» и два «можно».
 *
 * <p>Так правила стройки объясняют в настоящих сводах: сначала показывают
 * ошибки, потом верные случаи (просьба дизайнера 15.09.2026). Раньше на этом
 * месте стояла заглушка под иллюстрацию, и зона стройки не объяснялась ничем.
 *
 * <p>Кадры рисует {@link FieldPainter} — тот же, что и настоящую партию,
 * поэтому сектора и посадка жетонов не могут разойтись с игрой.
 *
 * <p>Запуск: {@code java -cp gui/target/kelium-runner.jar
 * kelium.gui.replay2.СнимокСтройки <куда.png> [радиус гекса]}
 */
public final class СнимокСтройки {

    private СнимокСтройки() {
    }

    /** Два гекса в ряд: слева база игрока, справа соседний гекс. */
    private static final int[][] КЛЕТКИ = {{0, 0}, {1, 0}};
    /** Сторона гекса (0,0), обращённая к соседу (1,0) — Field.AXIAL_DIRS[0]. */
    private static final int К_СОСЕДУ = 0;

    private static int uid = 1;

    private static final class Сцена {
        final Map<String, ReplayRecord.HexState> состояния = new LinkedHashMap<>();
        final List<ReplayRecord.Tok> жетоны = new ArrayList<>();
        String перечеркнуть;        // id гекса, на котором строить нельзя
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

    /**
     * ЧЕТЫРЕ КАДРА. 1–2 — как нельзя, 3–4 — как можно.
     *
     * <p>Зона стройки — гексы со своими зданиями и соседний гекс, к которому
     * здание обращено стенкой (глава 4). На тайле зарождения не строят вовсе.
     */
    private static Сцена кадр(int n) {
        uid = 1;
        Сцена с = new Сцена();
        for (int[] qr : КЛЕТКИ) {
            ReplayRecord.HexState h = new ReplayRecord.HexState();
            h.id = "h" + qr[0] + "_" + qr[1];
            с.состояния.put(h.id, h);
        }
        int[] свои = new int[]{-1, -1, -1, -1, -1, -1};
        // ЦУ занимает два сектора; в кадре 1 они отвёрнуты от соседа, иначе —
        // обращены к нему стенкой.
        int первый = (n == 1) ? 3 : К_СОСЕДУ;
        ReplayRecord.Tok цу = жетон("command_center", 0, "h0_0", true);
        цу.energySlots = 1;
        цу.energyPlaced = 1;
        свои[первый] = цу.uid;
        свои[(первый + 1) % 6] = цу.uid;
        с.жетоны.add(цу);

        if (n == 2) {
            ReplayRecord.Spawn sp = new ReplayRecord.Spawn();
            sp.kelium = 3;
            sp.start = true;
            с.состояния.get("h1_0").spawn = sp;
        }

        String гдеКазарма = (n == 3) ? "h0_0" : "h1_0";
        ReplayRecord.Tok казарма = жетон("barracks", 0, гдеКазарма, true);
        if (гдеКазарма.equals("h0_0")) {
            int старт = (первый + 3) % 6;       // свободные сектора напротив ЦУ
            свои[старт] = казарма.uid;
            свои[(старт + 1) % 6] = казарма.uid;
        } else {
            int[] соседние = new int[]{-1, -1, -1, -1, -1, -1};
            int старт = 3;                      // сторона, смотрящая на базу
            соседние[старт] = казарма.uid;
            соседние[(старт + 1) % 6] = казарма.uid;
            с.состояния.get("h1_0").sideOwner = соседние;
        }
        с.жетоны.add(казарма);
        с.состояния.get("h0_0").sideOwner = свои;
        if (n <= 2) {
            с.перечеркнуть = "h1_0";
        }
        return с;
    }

    public static void main(String[] args) throws Exception {
        Theme.apply(false);
        Path out = Path.of(args.length > 0 ? args[0] : "стройка.png");
        double size = args.length > 1 ? Double.parseDouble(args[1]) : 140;
        Textures.useFolder(GameConfig.resolveDataRoot(null).resolve("textures"));

        String[] подписи = {"нельзя", "нельзя", "можно", "можно"};
        String[] пояснения = {
            "Гекс не в зоне стройки: ЦУ обращено к нему не стенкой, "
                + "а границей секторов.",
            "На тайле зарождения не строят: он занимает весь гекс.",
            "На своём гексе: у казармы есть два свободных сектора подряд.",
            "На соседнем гексе, к которому ЦУ обращено стенкой.",
        };
        BufferedImage img = нарисовать(size, подписи, пояснения);
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        System.out.println("[стройка] " + out.toAbsolutePath() + " "
            + img.getWidth() + "x" + img.getHeight());
    }

    private static BufferedImage нарисовать(double size, String[] подписи, String[] пояснения) {
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
        int полеW = (int) Math.round(size * 0.20);
        int шапка = (int) Math.round(size * 0.52);
        int низ = (int) Math.round(size * 0.92);
        int полеH = (int) Math.round(maxy - miny);
        int клW = (int) Math.round(maxx - minx) + полеW * 2;
        int клH = шапка + полеH + низ;
        BufferedImage img = new BufferedImage(подписи.length * клW, клH,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FieldPainter.dark = false;
        FieldPainter.showCardboard = false;

        Font шапкаШ = new Font("Tektur", Font.BOLD, (int) Math.round(size * 0.235));
        Font мелкий = new Font("Tektur Narrow", Font.PLAIN, (int) Math.round(size * 0.185));
        for (int k = 0; k < подписи.length; k++) {
            int ox = k * клW;
            Сцена с = кадр(k + 1);
            Color цвет = с.перечеркнуть != null
                ? new Color(0xB03A2E) : new Color(0x186C24);

            g.setFont(шапкаШ);
            g.setColor(цвет);
            int пw = g.getFontMetrics().stringWidth(подписи[k])
                + (int) Math.round(size * 0.62);
            int пh = (int) Math.round(size * 0.34);
            Path2D плашка = new Path2D.Double();
            плашка.moveTo(ox + полеW, size * 0.05);
            плашка.lineTo(ox + полеW + пw, size * 0.05);
            плашка.lineTo(ox + полеW + пw - пh * 0.55, size * 0.05 + пh);
            плашка.lineTo(ox + полеW, size * 0.05 + пh);
            плашка.closePath();
            g.fill(плашка);
            g.setColor(new Color(0xF7F1E1));
            g.drawString(подписи[k], (int) Math.round(ox + полеW + size * 0.16),
                (int) Math.round(size * 0.05 + пh * 0.76));

            double cx0 = ox + полеW - minx;
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
                if (id.equals(с.перечеркнуть)) {
                    крест(g, cx0 + c[0], cy0 + c[1], size * 0.52);
                }
            }

            g.setFont(мелкий);
            g.setColor(new Color(0x2A2318));
            перенос(g, пояснения[k], ox + полеW,
                шапка + полеH + (int) Math.round(size * 0.30), клW - полеW * 2);
        }
        g.dispose();
        return обрезать(img);
    }

    /** Красный запрещающий знак поверх гекса, на котором строить нельзя. */
    private static void крест(Graphics2D g, double cx, double cy, double r) {
        g.setStroke(new BasicStroke((float) (r * 0.20), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xB03A2E));
        g.drawOval((int) Math.round(cx - r), (int) Math.round(cy - r),
            (int) Math.round(r * 2), (int) Math.round(r * 2));
        double d = r * 0.70;
        g.drawLine((int) Math.round(cx - d), (int) Math.round(cy - d),
            (int) Math.round(cx + d), (int) Math.round(cy + d));
    }

    private static void перенос(Graphics2D g, String текст, int x, int y, int ширина) {
        StringBuilder строка = new StringBuilder();
        int шаг = g.getFontMetrics().getHeight();
        for (String слово : текст.split(" ")) {
            String проба = строка.isEmpty() ? слово : строка + " " + слово;
            if (g.getFontMetrics().stringWidth(проба) > ширина && !строка.isEmpty()) {
                g.drawString(строка.toString(), x, y);
                y += шаг;
                строка = new StringBuilder(слово);
            } else {
                строка = new StringBuilder(проба);
            }
        }
        if (!строка.isEmpty()) {
            g.drawString(строка.toString(), x, y);
        }
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
