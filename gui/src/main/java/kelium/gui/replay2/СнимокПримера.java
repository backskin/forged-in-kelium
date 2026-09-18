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
 * <p>Один игрок вскрыл карту «Разработка / Инфраструктура» и разыгрывает три
 * доступных действия подряд: Снаряжение, Смену энергии и Добычу. Кадры
 * показывают поле ДО и ПОСЛЕ каждого действия, так что видно, что именно
 * изменилось.
 *
 * <p>ПРИМЕР ПРО ЭНЕРГИЮ (просьба дизайнера 16.09.2026). Ход начинается с того,
 * что оба кубика игрока лежат на источниках — на ЦУ и на энергостанции, — а
 * добытчик стоит мёртвый. Смена энергии перекладывает их на добытчик: он
 * оживает, а ЦУ гаснет. Это и есть главное про энергию: её не тратят, её
 * перетягивают, и всегда чего-то не хватает.
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

    /**
     * ЧЕТЫРЕ ГЕКСА. Слева база: ЦУ и энергостанция; рядом добытчик, над ним
     * тайл зарождения; справа пустой гекс.
     *
     * <p>Расстановка выверена по правилам, а не подобрана для красоты: на тайле
     * зарождения стоять нельзя (глава 4), а добытчик берёт келемий с тайла НА
     * СОСЕДНЕМ гексе за своей стенкой (глава 8).
     */
    private static final int[][] КЛЕТКИ = {{0, 0}, {1, 0}, {2, 0}, {1, -1}};
    /** Сторона гекса (1,0), обращённая к тайлу (1,-1): по ней стоит стенка. */
    private static final int СТЕНКА_К_ТАЙЛУ = 2;

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
        // действия, открытый чужой — одно; четырёх действий из трёх разных
        // приказов в один ход не собрать (глава 5).
        String[] подписи = {
            "Ход начинается",
            "1 · Снаряжение",
            "2 · Смена энергии",
            "3 · Добыча",
        };
        String[] пояснения = {
            "Вскрыта карта «Разработка / Инфраструктура». Чужой приказ открыт: "
                + "Инфраструктуру в этом круге уже вскрыл сосед. Оба кубика "
                + "энергии лежат на источниках — на ЦУ и на энергостанции; "
                + "добытчик не запитан, обе его ячейки пусты.",
            "Выбранный приказ: ЦУ запитан своим кубиком и ставит на свой гекс "
                + "вышку.",
            "Чужой приказ: оба кубика уходят с ЦУ и энергостанции в ячейки "
                + "добытчика. Теперь запитан он, а ЦУ погас: энергию не тратят, "
                + "её перетягивают.",
            "Выбранный приказ: запитанный добытчик берёт келемий с тайла "
                + "зарождения на соседнем гексе — прямо за своей стенкой.",
        };

        BufferedImage img = нарисовать(size, подписи, пояснения);
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        System.out.println("[пример] " + out.toAbsolutePath() + " "
            + img.getWidth() + "x" + img.getHeight());
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
        // Тайл зарождения занимает ВЕСЬ гекс: на нём никто не стоит.
        ReplayRecord.Spawn sp = new ReplayRecord.Spawn();
        sp.kelium = шаг >= 4 ? 2 : 3;      // шаг 4 — Добыча
        sp.start = true;
        с.состояния.get("h1_-1").spawn = sp;

        // ЭНЕРГИЯ ПЕРЕЕЗЖАЕТ НА ТРЕТЬЕМ ШАГЕ. До Смены энергии кубики лежат на
        // источниках: один в ячейке ЦУ, один на энергостанции (energyIdle —
        // кубик лежит на источнике, а не в ячейке потребителя). После — оба
        // в двух ячейках добытчика, и оба источника пусты.
        boolean переложено = шаг >= 3;

        // ЦУ и энергостанция на своём гексе; добытчик — на соседнем с тайлом,
        // СТЕНКОЙ К НЕМУ.
        ReplayRecord.Tok цу = жетон("command_center", 0, "h0_0", true);
        цу.energySlots = 1;
        цу.energyPlaced = переложено ? 0 : 1;
        ReplayRecord.Tok станция = жетон("power_plant", 0, "h0_0", true);
        станция.level = 1;
        станция.energyIdle = переложено ? 0 : 1;
        ReplayRecord.Tok добытчик = жетон("miner", 0, "h1_0", true);
        добытчик.level = 1;
        // ЯЧЕЕК ЭНЕРГИИ У ДОБЫТЧИКА №1 ДВЕ (data/boards: miners[0].energy_slots):
        // одним кубиком его не запитать, нужны оба.
        добытчик.energySlots = 2;
        добытчик.energyPlaced = переложено ? 2 : 0;
        с.состояния.get("h0_0").sideOwner = new int[]{
            цу.uid, цу.uid, станция.uid, -1, -1, -1};
        int[] стороны = new int[]{-1, -1, -1, -1, -1, -1};
        стороны[СТЕНКА_К_ТАЙЛУ] = добытчик.uid;   // добытчик занимает 1 сектор
        с.состояния.get("h1_0").sideOwner = стороны;
        с.жетоны.add(цу);
        с.жетоны.add(станция);
        с.жетоны.add(добытчик);

        // Вышка появляется со второго кадра: её делает запитанный ЦУ Снаряжением.
        if (шаг >= 2) {
            с.жетоны.add(жетон("tower", 0, "h0_0", false));
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
        int полеH = (int) Math.round(maxy - miny);
        int клW = (int) Math.round(maxx - minx) + полеW * 2;
        int кол = 2;
        int кадров = подписи.length;
        int стр = (кадров + кол - 1) / кол;

        // МЕСТО ПОД ПОЯСНЕНИЕ СЧИТАЕТСЯ, А НЕ ПРИКИДЫВАЕТСЯ. Раньше низ кадра
        // был зашит в 0,66 радиуса гекса — на трёх строках хватало, а на
        // четырёх подпись уезжала под заголовок следующего кадра (замечено
        // 16.09.2026). Теперь высота берётся по самому длинному пояснению.
        Font мелкий = new Font("Tektur Narrow", Font.PLAIN, (int) Math.round(size * 0.185));
        int строк = 1;
        BufferedImage мерка = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D мг = мерка.createGraphics();
        мг.setFont(мелкий);
        for (String п : пояснения) {
            строк = Math.max(строк, перенос(мг, п, 0, 0, клW - полеW * 2, false));
        }
        int шаг = мг.getFontMetrics().getHeight();
        мг.dispose();
        int низ = (int) Math.round(size * 0.30) + строк * шаг
            + (int) Math.round(size * 0.16);
        int клH = шапка + полеH + низ;

        BufferedImage img = new BufferedImage(кол * клW, стр * клH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FieldPainter.dark = false;
        FieldPainter.showCardboard = false;

        Font шапкаШ = new Font("Tektur", Font.BOLD, (int) Math.round(size * 0.235));
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
                oy + шапка + полеH + (int) Math.round(size * 0.30), клW - полеW * 2,
                true);
        }
        g.dispose();
        return обрезать(img);
    }

    /** Подпись в несколько строк по ширине клетки. */
    /**
     * Разложить текст по строкам заданной ширины.
     *
     * @param рисовать {@code false} — только посчитать строки, ничего не рисуя
     * @return сколько вышло строк
     */
    private static int перенос(Graphics2D g, String текст, int x, int y, int ширина,
                               boolean рисовать) {
        StringBuilder строка = new StringBuilder();
        int шаг = g.getFontMetrics().getHeight();
        int строк = 0;
        for (String слово : текст.split(" ")) {
            String проба = строка.isEmpty() ? слово : строка + " " + слово;
            if (g.getFontMetrics().stringWidth(проба) > ширина && !строка.isEmpty()) {
                if (рисовать) {
                    g.drawString(строка.toString(), x, y);
                }
                y += шаг;
                строк++;
                строка = new StringBuilder(слово);
            } else {
                строка = new StringBuilder(проба);
            }
        }
        if (!строка.isEmpty()) {
            if (рисовать) {
                g.drawString(строка.toString(), x, y);
            }
            строк++;
        }
        return строк;
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
