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
import kelium.engine.BlockStamp;
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
        final java.util.Set<String> запретные = new java.util.LinkedHashSet<>();
        /** Подпись под гексом: чем этот гекс в сцене примечателен. */
        final Map<String, String> подписи = new LinkedHashMap<>();
        /** Чем накрыт гекс: {блок, сторона, q, r} внутри картонки. */
        final Map<String, Object[]> картон = new LinkedHashMap<>();
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
        // В sideOwner попадают ТОЛЬКО здания: войска painter рассаживает сам
        // по свободным сторонам, а «занятая» сторона выталкивает их с гекса.
        с.состояния.get("h0_0").sideOwner = new int[]{
            цу.uid, цу.uid, -1, казарма.uid, казарма.uid, -1};
        с.состояния.get("h0_0").ownerTint = 0;
        с.состояния.get("h0_0").ownerBuilt = true;

        ReplayRecord.Tok чужаяП = жетон("infantry", 1, "h1_0", false);
        ReplayRecord.Tok чужаяТ = жетон("vehicle", 1, "h1_0", false);
        с.жетоны.add(чужаяП);
        с.жетоны.add(чужаяТ);


        ReplayRecord.Tok авиация = жетон("aircraft", 0, "h1_-1", false);
        с.жетоны.add(авиация);
        return с;
    }

    /**
     * Что лежит на поле: НАСТОЯЩИЙ ПЕЧАТНЫЙ БЛОК, а на нём тайл зарождения,
     * нейтральные постройки и один запретный гекс.
     *
     * <p>Раньше гексы этой сцены рисовались схемой — белым шестиугольником с
     * дорисованными значками. Дизайнер отбил это прямо (16.09.2026): «хотел бы
     * чтобы тут был блок поля, а не нарисованное тобой». Поэтому сцена берёт
     * сторону настоящей картонки из набора ({@code data/blocks}) и кладёт её
     * рисунок ({@code data/textures/block}) — круг энергии и печатный контейнер
     * на ней НАПЕЧАТАНЫ, а не дорисованы.
     */
    private static Сцена поле() {
        uid = 1;
        Сцена с = new Сцена();
        BlockStamp.Face грань = грань("Б1", "A");
        List<int[]> клетки = new ArrayList<>();
        for (BlockStamp.Cell c : грань.cells()) {
            клетки.add(new int[]{c.q(), c.r()});
        }
        // ЗАПРЕТНЫЙ ГЕКС — не часть картонки: он лежит рядом, за её краем.
        int[] запретный = {-1, 1};
        клетки.add(запретный);
        с.клетки = клетки.toArray(new int[0][]);
        for (int[] qr : с.клетки) {
            гекс(с, qr[0], qr[1]);
        }
        for (BlockStamp.Cell c : грань.cells()) {
            с.картон.put("h" + c.q() + "_" + c.r(),
                new Object[]{грань.blockId(), грань.side(), c.q(), c.r()});
        }
        с.запретные.add("h" + запретный[0] + "_" + запретный[1]);

        ReplayRecord.Spawn sp = new ReplayRecord.Spawn();
        sp.kelium = 4;
        sp.start = false;
        с.состояния.get("h0_1").spawn = sp;

        // ПОСТРОЙКА НА ДВА СЕКТОРА ЗАДАЁТСЯ ТРЕМЯ УГЛАМИ, А НЕ ДВУМЯ
        // (Hex.NeutralBuilding: «одинарное = 2 угла/одно ребро, двойное =
        // 3 угла/два ребра»). С двумя углами силуэт считался по ОДНОМУ ребру, а
        // рисунок клался большой — постройка садилась поперёк границы гексов
        // (поймано дизайнером 16.09.2026).
        ReplayRecord.Neutral нейтр = new ReplayRecord.Neutral();
        нейтр.big = true;
        нейтр.corners.add(1);
        нейтр.corners.add(2);
        нейтр.corners.add(3);
        нейтр.hp = 2;
        нейтр.hpMax = 2;
        с.состояния.get("h1_0").neutrals.add(нейтр);

        // НЕЙТРАЛЬНАЯ ПОСТРОЙКА РИСУЕТСЯ ОТ ДВУХ УГЛОВ: с одним FieldPainter
        // её молча пропускает (paintNeutral: corners.size() < 2).
        ReplayRecord.Neutral малый = new ReplayRecord.Neutral();
        малый.corners.add(4);
        малый.corners.add(5);
        малый.hp = 1;
        малый.hpMax = 1;
        с.состояния.get("h1_1").neutrals.add(малый);

        // ЖИВОЙ СТОЛ, А НЕ ПУСТОЙ КАРТОН (просьба дизайнера 16.09.2026):
        // здания и войска ТРЁХ игроков, по одному жетону каждого рода. Полоса
        // называется «что лежит на поле», и войска на ней лежат тоже.
        населить(с, 0, "h0_0", "command_center", 2, new int[]{4, 5}, "infantry");
        населить(с, 1, "h2_0", "factory", 2, new int[]{1, 2}, "vehicle");
        населить(с, 3, "h1_-1", "airbase", 3, new int[]{2, 3, 4}, "aircraft");
        ReplayRecord.Tok вышка = жетон("tower", 3, "h1_-1", false);
        с.жетоны.add(вышка);
        return с;
    }

    /**
     * Поставить на гекс здание игрока и один его жетон войск.
     *
     * @param стороны какие стороны гекса занимает здание — их след и есть
     *                стенка, по ней же считается зона стройки
     */
    private static void населить(Сцена с, int seat, String hex, String здание,
                                 int ячеекЭнергии, int[] стороны, String род) {
        ReplayRecord.Tok b = жетон(здание, seat, hex, true);
        b.energySlots = ячеекЭнергии;
        b.energyPlaced = ячеекЭнергии;
        с.жетоны.add(b);
        int[] владельцы = new int[]{-1, -1, -1, -1, -1, -1};
        for (int s : стороны) {
            владельцы[s] = b.uid;
        }
        с.состояния.get(hex).sideOwner = владельцы;
        с.состояния.get(hex).ownerTint = seat;
        с.состояния.get(hex).ownerBuilt = true;
        с.жетоны.add(жетон(род, seat, hex, false));
    }

    /**
     * ДОБЫЧА ТРЕМЯ ДОБЫТЧИКАМИ СРАЗУ (просьба дизайнера 16.09.2026). Одно
     * действие, три разных исхода — и все три на одной картинке:
     * <ul>
     *   <li>запитанный добытчик у тайла берёт келемий;</li>
     *   <li>незапитанный стоит рядом с тем же тайлом и не берёт ничего;</li>
     *   <li>запитанный вдали от тайла берёт КОНТЕЙНЕР — он напечатан на его
     *       собственном гексе.</li>
     * </ul>
     * Добытчики разных игроков: правило одно на всех, и по цвету видно, что
     * дело не в игроке, а в энергии и в том, к чему добытчик примыкает.
     */
    private static Сцена добыча() {
        uid = 1;
        Сцена с = new Сцена();
        BlockStamp.Face грань = грань("Б1", "A");
        List<int[]> клетки = new ArrayList<>();
        for (BlockStamp.Cell c : грань.cells()) {
            клетки.add(new int[]{c.q(), c.r()});
        }
        с.клетки = клетки.toArray(new int[0][]);
        for (int[] qr : с.клетки) {
            гекс(с, qr[0], qr[1]);
        }
        for (BlockStamp.Cell c : грань.cells()) {
            с.картон.put("h" + c.q() + "_" + c.r(),
                new Object[]{грань.blockId(), грань.side(), c.q(), c.r()});
        }

        ReplayRecord.Spawn sp = new ReplayRecord.Spawn();
        sp.kelium = 3;
        sp.start = false;
        с.состояния.get("h0_1").spawn = sp;

        // Стороны гекса, обращённые к тайлу (0,1): для (0,0) это сторона 5
        // (вниз), для (1,1) — сторона 3 (влево-вверх).
        добытчик(с, 0, "h0_0", 5, true);
        добытчик(с, 1, "h1_1", 3, false);
        добытчик(с, 3, "h2_0", 4, true);

        // НОМЕРА, А НЕ ПОДПИСИ. Подписи под гексами налезали на поле и
        // обрезались; номер же занимает один кружок, а пояснение к нему стоит
        // в тексте главы — так же, как выноски на прочих рисунках книги.
        с.подписи.put("h0_0", "1");
        с.подписи.put("h1_1", "2");
        с.подписи.put("h2_0", "3");
        return с;
    }

    /** Добытчик игрока: занимает одну сторону гекса и, может быть, запитан. */
    private static void добытчик(Сцена с, int seat, String hex, int сторона,
                                 boolean запитан) {
        ReplayRecord.Tok b = жетон("miner", seat, hex, true);
        b.level = 1;
        b.energySlots = 2;
        b.energyPlaced = запитан ? 2 : 0;
        с.жетоны.add(b);
        int[] владельцы = new int[]{-1, -1, -1, -1, -1, -1};
        владельцы[сторона] = b.uid;
        с.состояния.get(hex).sideOwner = владельцы;
    }

    /** Сторона печатного блока из набора: она же источник правды о печати. */
    private static BlockStamp.Face грань(String блок, String сторона) {
        for (BlockStamp.Face f : BlockStamp.faces(GameConfig.resolveDataRoot(null))) {
            if (f.blockId().equals(блок) && f.side().equals(сторона)) {
                return f;
            }
        }
        throw new IllegalStateException("нет стороны блока " + блок + "-" + сторона);
    }

    public static void main(String[] args) throws Exception {
        Theme.apply(false);
        String имя = args.length > 0 ? args[0] : "база";
        Path out = Path.of(args.length > 1 ? args[1] : имя + ".png");
        double size = args.length > 2 ? Double.parseDouble(args[2]) : 190;
        Textures.useFolder(GameConfig.resolveDataRoot(null).resolve("textures"));

        Сцена с = switch (имя) {
            case "поле" -> поле();
            case "добыча" -> добыча();
            default -> база();
        };
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
            Object[] к = с.картон.get(id);
            if (к != null) {
                hi.block = (String) к[0];
                hi.blockSide = (String) к[1];
                hi.blockQ = (Integer) к[2];
                hi.blockR = (Integer) к[3];
                hi.blockRot = 0;
            }
            if (с.запретные.contains(id)) {
                hi.kind = "FORBIDDEN";
            }
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
        // КРОМКА СЛОЖЕННОГО ПОЛЯ — поверх всего: на светлой полосе книги край
        // стола иначе не читается вовсе (замечание дизайнера 16.09.2026).
        ОбводкаПоля.нарисовать(g, java.util.Arrays.asList(с.клетки), size, cx0, cy0);
        подписатьГексы(g, с, size, cx0, cy0);
        g.dispose();
        return обрезать(img);
    }

    /**
     * НОМЕРА НА ГЕКСАХ — те же кружки-выноски, что и на прочих рисунках книги.
     * Сцена с тремя добытчиками без них не читается: видно три одинаковых
     * жетона, а чем они отличаются — нет. Пояснение к номеру стоит в тексте.
     */
    private static void подписатьГексы(Graphics2D g, Сцена с, double size,
                                       double cx0, double cy0) {
        if (с.подписи.isEmpty()) {
            return;
        }
        double r = size * 0.19;
        Font шрифт = new Font("Tektur", Font.BOLD, (int) Math.round(r * 1.25));
        g.setFont(шрифт);
        for (var e : с.подписи.entrySet()) {
            String[] чч = e.getKey().substring(1).split("_");
            double[] c = FieldGeometry.hexCenter(Integer.parseInt(чч[0]),
                Integer.parseInt(чч[1]), size);
            // Кружок — у верхней кромки своего гекса, над жетонами.
            double x = cx0 + c[0];
            double y = cy0 + c[1] - size * 0.52;
            g.setColor(new java.awt.Color(0x18, 0x6C, 0x24));
            g.fill(new java.awt.geom.Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            g.setColor(new java.awt.Color(0xF7, 0xF1, 0xE1));
            int w = g.getFontMetrics().stringWidth(e.getValue());
            g.drawString(e.getValue(), (int) Math.round(x - w / 2.0),
                (int) Math.round(y + r * 0.62));
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
