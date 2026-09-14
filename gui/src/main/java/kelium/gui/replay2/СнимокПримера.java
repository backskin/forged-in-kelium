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
 * СНИМОК ПРИМЕРА ХОДА — четыре кадра одного хода на маленьком поле.
 *
 * <p>Один игрок вскрыл карту и разыгрывает четыре доступных действия подряд:
 * Смена энергии, Добыча, Манёвр, Бой. Кадры показывают поле ДО и ПОСЛЕ каждого
 * действия, так что видно, что именно изменилось.
 *
 * <p>Поле собирается руками — ради примера не нужна целая партия, — но рисуется
 * ТЕМ ЖЕ {@link FieldPainter}, что и настоящая игра: сектора, посадка жетонов,
 * кубики энергии и урона не могут разойтись с правилами.
 *
 * <p>Запуск: {@code java -cp gui/target/kelium-runner.jar
 * kelium.gui.replay2.СнимокПримера <куда.png> [радиус гекса]}
 */
public final class СнимокПримера {

    private СнимокПримера() {
    }

    /** Три гекса в ряд: слева добытчик у зарождения, в середине завод, справа враг. */
    private static final int[][] КЛЕТКИ = {{0, 0}, {1, 0}, {2, 0}, {1, -1}};

    private static int uid = 1;

    private static final class Сцена {
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

    public static void main(String[] args) throws Exception {
        Theme.apply(false);
        Path out = Path.of(args.length > 0 ? args[0] : "пример-хода.png");
        double size = args.length > 1 ? Double.parseDouble(args[1]) : 150;
        Textures.useFolder(GameConfig.resolveDataRoot(null).resolve("textures"));

        // ХОД ЗАКОННЫЙ, А НЕ ПРОСТО КРАСИВЫЙ. Выбранный приказ даёт два своих
        // действия, открытый чужой — одно своё; четыре действия из трёх разных
        // приказов в один ход не собрать (глава 5), и пример не имеет права
        // показывать то, чего правила не разрешают.
        String[] подписи = {
            "Ход начинается",
            "1 · Добыча",
            "2 · Манёвр",
            "3 · Бой",
        };
        String[] пояснения = {
            "Вскрыта карта «Наступление / Разработка». Чужой приказ открыт: "
                + "до этого Разработку уже вскрыл сосед.",
            "Чужой приказ: запитанный добытчик берёт келемий с соседнего зарождения.",
            "Выбранный приказ: техника с гекса завода идёт к чужой пехоте.",
            "Выбранный приказ: техника атакует пехоту — 1 боеприпас, 1 урон, "
                + "жетон уничтожен.",
        };

        BufferedImage img = нарисовать(size, подписи, пояснения);
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        System.out.println("[пример] " + out.toAbsolutePath() + " "
            + img.getWidth() + "×" + img.getHeight());
    }

    /**
     * ЧЕТЫРЕ КАДРА. Состояние пересобирается на каждый кадр целиком: так проще
     * читать, что в нём есть, чем следить за цепочкой правок.
     */
    private static Сцена кадр(int шаг) {
        uid = 1;
        Сцена с = new Сцена();
        for (int[] qr : КЛЕТКИ) {
            ReplayRecord.HexState h = new ReplayRecord.HexState();
            h.id = "h" + qr[0] + "_" + qr[1];
            с.состояния.put(h.id, h);
        }
        // Зарождение на верхнем гексе: с каждым шагом келемия на нём меньше.
        ReplayRecord.Spawn sp = new ReplayRecord.Spawn();
        sp.kelium = шаг >= 2 ? 2 : 3;   // шаг 2 — Добыча
        sp.start = true;
        с.состояния.get("h1_-1").spawn = sp;

        // ЦУ на левом гексе — источник энергии; добытчик рядом с зарождением.
        ReplayRecord.Tok цу = жетон("command_center", 0, "h0_0", true);
        цу.energySlots = 1;
        цу.energyPlaced = 1;
        цу.energyIdle = 0;
        ReplayRecord.Tok добытчик = жетон("miner", 0, "h1_-1", true);
        добытчик.level = 1;
        добытчик.energySlots = 1;
        добытчик.energyPlaced = 1;
        ReplayRecord.Tok завод = жетон("factory", 0, "h1_0", true);
        завод.energySlots = 1;
        завод.energyPlaced = 1;
        с.состояния.get("h0_0").sideOwner = new int[]{цу.uid, цу.uid, -1, -1, -1, -1};
        с.состояния.get("h1_-1").sideOwner = new int[]{-1, -1, -1, добытчик.uid, -1, -1};
        с.состояния.get("h1_0").sideOwner = new int[]{завод.uid, завод.uid, -1, -1, -1, -1};
        с.жетоны.add(цу);
        с.жетоны.add(добытчик);
        с.жетоны.add(завод);

        // Техника игрока: до Манёвра на гексе завода, после — на гексе врага.
        ReplayRecord.Tok техника = жетон("vehicle", 0, шаг >= 3 ? "h2_0" : "h1_0", false);
        с.жетоны.add(техника);
        // Чужая пехота: на последнем шаге уничтожена.
        ReplayRecord.Tok пехота = жетон("infantry", 1, "h2_0", false);
        пехота.damage = шаг >= 4 ? 1 : 0;
        пехота.alive = шаг < 4;
        if (пехота.alive) {
            с.жетоны.add(пехота);
        }
        return с;
    }

    private static BufferedImage нарисовать(double size, String[] подписи, String[] пояснения) {
        // РАМКА КАДРА СЧИТАЕТСЯ ПО САМИМ ГЕКСАМ, а не прикидывается: у четырёх
        // клеток «три в ряд и одна сверху» габарит не выводится из ширины гекса,
        // и на глазок кадры налезали друг на друга.
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
        int полеW = (int) Math.round(size * 0.22);
        int шапка = (int) Math.round(size * 0.52);
        int низ = (int) Math.round(size * 0.66);
        int полеH = (int) Math.round(maxy - miny);
        int клW = (int) Math.round(maxx - minx) + полеW * 2;
        int клH = шапка + полеH + низ;
        int кол = 2;
        int кадров = подписи.length;
        int стр = (кадров + кол - 1) / кол;
        BufferedImage img = new BufferedImage(кол * клW, стр * клH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FieldPainter.dark = false;
        FieldPainter.showCardboard = false;

        Font шапкаШ = new Font("Tektur", Font.BOLD, (int) Math.round(size * 0.235));
        Font мелкий = new Font("Tektur Narrow", Font.PLAIN, (int) Math.round(size * 0.185));
        for (int k = 0; k < кадров; k++) {
            int ox = (k % кол) * клW;
            int oy = (k / кол) * клH;
            Сцена с = кадр(k + 1);

            g.setFont(шапкаШ);
            g.setColor(new Color(0x186C24));
            int пw = g.getFontMetrics().stringWidth(подписи[k])
                + (int) Math.round(size * 0.62);
            int пh = (int) Math.round(size * 0.34);
            Path2D плашка = new Path2D.Double();
            плашка.moveTo(ox + полеW, oy + size * 0.05);
            плашка.lineTo(ox + полеW + пw, oy + size * 0.05);
            плашка.lineTo(ox + полеW + пw - пh * 0.55, oy + size * 0.05 + пh);
            плашка.lineTo(ox + полеW, oy + size * 0.05 + пh);
            плашка.closePath();
            g.fill(плашка);
            g.setFont(шапкаШ);
            g.setColor(new Color(0xF7F1E1));
            g.drawString(подписи[k], (int) Math.round(ox + полеW + size * 0.16),
                (int) Math.round(oy + size * 0.05 + пh * 0.76));

            double cx0 = ox + полеW - minx;
            double cy0 = oy + шапка - miny;
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
            }

            g.setFont(мелкий);
            g.setColor(new Color(0x2A2318));
            перенос(g, пояснения[k], ox + полеW,
                oy + шапка + полеH + (int) Math.round(size * 0.30), клW - полеW * 2);
        }
        g.dispose();
        return обрезать(img);
    }

    /** Подпись в несколько строк по ширине клетки. */
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
