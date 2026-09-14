package kelium.gui.replay2;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import kelium.core.GameState;
import kelium.core.Hex;
import kelium.dataio.ContentLibrary;
import kelium.dataio.GameConfig;
import kelium.engine.Modules;
import kelium.engine.Setup;
import kelium.gui.GameRecorder;
import kelium.report.FieldGeometry;
import kelium.report.FieldPainter;
import kelium.report.Java2DCanvas;
import kelium.report.ReplayRecord;
import kelium.report.Textures;

/**
 * СНИМОК СТОЛА — подготовленная партия, снятая сверху, как она лежит на столе.
 *
 * <p>Зачем. Книге правил нужен разворот «подготовка к игре»: не схема из
 * пунктирных прямоугольников, а картинка настоящего стола, по которой видно,
 * где что лежит. Рисовать её руками значит перерисовывать после каждой правки
 * правил, поэтому она СОБИРАЕТСЯ ДВИЖКОМ: партия действительно готовится
 * ({@link Setup#buildGame}), а потом рисуется теми же классами, что рисуют поле
 * и планшеты в проигрывателе. Разошлись правила — разошлась и картинка, и это
 * видно сразу.
 *
 * <p><b>Масштаб честный.</b> Все размеры заданы В МИЛЛИМЕТРАХ ПЕЧАТИ: гекс 90 мм
 * по ширине, планшет войск 300×80, хранилища 156×90, планшет науки 265, рынка
 * 195, карты 56×87, 68×44 и 34×34. Поэтому на картинке компоненты соотносятся
 * друг с другом так же, как на настоящем столе.
 *
 * <p>Запуск:
 * <pre>
 *   java -cp gui/target/kelium-runner.jar kelium.gui.replay2.СнимокСтола \
 *        &lt;куда.png&gt; [ширина в точках] [раскладка.kmap] [сид]
 * </pre>
 */
public final class СнимокСтола {

    private СнимокСтола() {
    }

    // ==================== стол в миллиметрах ====================

    private static final double СТОЛ_Ш = 1500;
    private static final double СТОЛ_В = 978;
    /** Сколько сукна оставить вокруг разложенной игры. */
    private static final double ПОЛЯ_ММ = 34;

    /**
     * РАДИУС ГЕКСА НА ПЕЧАТИ. Жетон гекса зарождения уходит в типографию как
     * 90×82 мм — это ровно описанная окружность 45 мм у гекса «плашмя вверх».
     */
    private static final double ГЕКС_R = 45.3;
    private static final double ВОЙСКА_Ш = 300;      // печатный планшет войск
    /**
     * ОБЩИЕ ПЛАНШЕТЫ ПРИСТАВЛЯЮТСЯ К ПОЛЮ ВПРИТЫК (замысел дизайнера).
     *
     * <p>Планшет науки и планшет рынка нарочно вырезаны ГЕКСАМИ того же размера,
     * что и поле: они встают к нему вплотную, продолжая его сетку. Поэтому у них
     * не задана ширина в миллиметрах — она считается из радиуса гекса на самой
     * печати, и планшет всегда точно ложится на соседние клетки поля.
     *
     * <p>Радиус найден по якорям печати: середины треков науки отстоят друг от
     * друга на 1,5R = 810 точек, значит R = 540 при полотне 2693×1866. У рынка
     * полотно 1890×1890 — это ровно 3,5R по ширине, тот же R.
     */
    private static final double АРТ_R = 540;
    /** Центр правой доли планшета науки на его картинке. */
    private static final double[] НАУКА_ПРАВАЯ = {2160, 933};
    /** Центр левой доли планшета рынка на его картинке. */
    private static final double[] РЫНОК_ЛЕВАЯ = {540, 945};
    private static final double КАРТА_ЗАДАНИЕ_Ш = 56;   // 56×87
    private static final double КАРТА_АРСЕНАЛ_Ш = 68;   // 68×44
    private static final double КАРТА_КОНТЕЙНЕР_Ш = 34; // 34×34
    private static final double КАРТА_РЫНКА_Ш = 87;    // 87×56, лежит боком

    /** Цвета мест в том же порядке, что гнёзда краски: 0 синий, 1 красный… */
    private static final String[] ЦВЕТ_ПРИКАЗОВ =
        {"back_blue", "back_scarlet", "back_green", "back_yellow"};

    private static double k;                 // точек в миллиметре

    /**
     * ЯКОРЯ ВЫНОСОК: номер шага подготовки → точка на картинке (в её точках).
     * Пишутся рядом с PNG отдельным файлом, и вёрстка книги ставит по ним
     * кружки 1…18 — не подбирая координаты руками по картинке.
     */
    private static final java.util.Map<String, int[]> ЯКОРЯ =
        new java.util.LinkedHashMap<>();

    private static void якорь(String номер, double xМм, double yМм) {
        ЯКОРЯ.put(номер, new int[]{px(xМм), px(yМм)});
    }

    private static void якорьТ(String номер, double x, double y) {
        ЯКОРЯ.put(номер, new int[]{(int) Math.round(x), (int) Math.round(y)});
    }

    private static int px(double мм) {
        return (int) Math.round(мм * k);
    }

    // ==================== запуск ====================

    public static void main(String[] args) throws Exception {
        Theme.apply(false);
        Path out = Path.of(args.length > 0 ? args[0] : "стол.png");
        int ширина = args.length > 1 ? Integer.parseInt(args[1]) : 4500;
        Path root = GameConfig.resolveDataRoot(null);
        Path раскладка = args.length > 2 && !args[2].isBlank()
            ? Path.of(args[2])
            : root.resolve("scenarios/new/сценарий 3 игрока 1.kmap");
        long сид = args.length > 3 ? Long.parseLong(args[3]) : 7L;
        if (args.length > 4 && !args[4].isBlank()) {
            сукно = args[4];
        }

        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 3, сид,
            root, null, null, null, раскладка);
        GameState st = Setup.buildGame(cfg);
        // ПОДГОТОВКА ДОИГРЫВАЕТСЯ ДО КОНЦА: глухие жетоны раздаются на шаге 14
        // подготовки, карта рынка первого раунда вскрывается на шаге 6. В
        // движке и то и другое делает GameEngine первым делом партии — здесь
        // вызывается то же самое, но без единого хода.
        Modules.раздатьГлухиеЖетоны(st, null);
        if (st.marketActive == null && st.decks.get("market") != null) {
            st.marketActive = st.decks.get("market").draw(st.rng);
        }
        ReplayRecord rec = GameRecorder.setupOnly(cfg, st, сид,
            List.of("human", "human", "human"));

        Session session = new Session();
        session.setContent(ContentLibrary.forRuleset(cfg.ruleset, root));
        session.setRecord(rec);
        session.seek(0);

        BufferedImage img = нарисовать(session, rec, st, ширина);
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(img, "png", out.toFile());
        String таб = "\t";
        StringBuilder sb = new StringBuilder("# номер" + таб + "x" + таб
            + "y (точки картинки " + img.getWidth() + "×" + img.getHeight() + ")\n");
        for (var e : ЯКОРЯ.entrySet()) {
            sb.append(e.getKey()).append(таб).append(e.getValue()[0])
                .append(таб).append(e.getValue()[1]).append('\n');
        }
        Path якоря = out.resolveSibling(out.getFileName().toString()
            .replaceFirst("\\.png$", "") + "-якоря.txt");
        Files.writeString(якоря, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("[стол] " + out.toAbsolutePath() + " "
            + img.getWidth() + "×" + img.getHeight());
        System.out.println("[стол] якоря: " + якоря.toAbsolutePath());
    }

    // ==================== сама картинка ====================

    private static BufferedImage нарисовать(Session session, ReplayRecord rec,
                                            GameState st, int ширина) {
        k = ширина / СТОЛ_Ш;
        int высота = px(СТОЛ_В);
        // ВСЁ РИСУЕТСЯ НА ПРОЗРАЧНОМ СЛОЕ, а сукно подкладывается потом. Так
        // картинку можно обрезать ровно по компонентам: пустое сукно по краям
        // ничего не рассказывает, а места на развороте занимает.
        BufferedImage слой = new BufferedImage(ширина, высота, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = слой.createGraphics();
        качество(g);

        // РАССАДКА НА ТРОИХ: один игрок с ближнего края стола, двое напротив.
        // Их планшеты повёрнуты к ним, то есть к нам вверх ногами, — ровно так
        // это и выглядит, когда смотришь на стол со своего места.
        поле(g, st, 750, 470);
        планшетНауки(g);
        планшетРынка(g, st);
        double арсX = наука[0] + наука[2] * 0.26;
        double арсY = наука[1] + наука[3] + px(46);
        double задX = наука[0] + наука[2] * 0.30;
        double задY = наука[1] - px(56);
        колодыАрсенала(g, арсX, арсY);
        колодыЗаданий(g, задX, задY);
        якорьТ("4", наука[0] + наука[2] * 0.5, наука[1] + наука[3] * 0.30);
        якорьТ("5", арсX - px(64), арсY);
        якорьТ("6", рынок[0] + рынок[2] * 0.40, рынок[1] + рынок[3] * 0.5);
        якорьТ("7", задX, задY);

        зонаИгрока(g, session, 0, 750, 806, 0);
        double фишX = зона0[0] + зона0[2] + px(56);
        double фишY = зона0[1] + зона0[3] * 0.3;
        фишкаПервогоТ(g, фишX, фишY);
        якорьТ("10", фишX, фишY);
        зонаИгрока(g, session, 1, 420, 162, 180);
        зонаИгрока(g, session, 2, 1082, 162, 180);

        double запX = наука[0] + наука[2] * 0.22;
        double запY = наука[1] + наука[3] + px(156);
        общийЗапасТ(g, запX, запY);
        double мешX = рынок[0] + рынок[2] * 1.45;
        double мешY = рынок[1] + рынок[3] + px(150);
        мешочкиТ(g, мешX, мешY);
        якорьТ("9", запX, запY);
        якорьТ("8", мешX, мешY);


        g.dispose();
        return наСукно(слой);
    }

    /**
     * ОБРЕЗАТЬ ПО КОМПОНЕНТАМ И ПОЛОЖИТЬ НА СУКНО. Рамка берётся по тому, что
     * нарисовано, плюс поля; потом она растягивается до пропорции разворота —
     * так на картинке нет ни обрезанных краёв, ни гектаров пустого стола.
     */
    private static BufferedImage наСукно(BufferedImage слой) {
        int x0 = слой.getWidth();
        int y0 = слой.getHeight();
        int x1 = 0;
        int y1 = 0;
        for (int y = 0; y < слой.getHeight(); y++) {
            for (int x = 0; x < слой.getWidth(); x++) {
                if ((слой.getRGB(x, y) >>> 24) > 10) {
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 <= x0 || y1 <= y0) {
            x0 = 0;
            y0 = 0;
            x1 = слой.getWidth() - 1;
            y1 = слой.getHeight() - 1;
        }
        int поле = px(ПОЛЯ_ММ);
        x0 -= поле;
        y0 -= поле;
        x1 += поле;
        y1 += поле;
        double нужно = СТОЛ_Ш / СТОЛ_В;
        double w = x1 - x0;
        double h = y1 - y0;
        if (w / h < нужно) {
            double добавка = (h * нужно - w) / 2;
            x0 -= добавка;
            x1 += добавка;
        } else {
            double добавка = (w / нужно - h) / 2;
            y0 -= добавка;
            y1 += добавка;
        }
        int ш = (int) Math.round(x1 - x0);
        int в = (int) Math.round(y1 - y0);
        BufferedImage img = new BufferedImage(ш, в, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        качество(g);
        стол(g, ш, в);
        g.drawImage(слой, (int) Math.round(-x0), (int) Math.round(-y0), null);
        g.dispose();
        for (int[] точка : ЯКОРЯ.values()) {
            точка[0] -= (int) Math.round(x0);
            точка[1] -= (int) Math.round(y0);
        }
        return img;
    }

    private static void качество(Graphics2D g) {
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
            RenderingHints.VALUE_STROKE_PURE);
    }

    /**
     * ПОВЕРХНОСТЬ СТОЛА. Картон у игры светлый, и подложка нужна тёмная, иначе
     * компоненты в неё проваливаются. По умолчанию дерево: книга печатается на
     * кремовой бумаге, и тёплый стол ложится в её палитру, а тёмно-серое сукно
     * (ключ {@code cloth}) даёт больший контраст. {@code light} — светлый стол,
     * оставлен для сравнения.
     */
    static String сукно = "wood";

    private static void стол(Graphics2D g, int w, int h) {
        Color верх;
        Color низ;
        Color блик;
        switch (сукно) {
            case "wood" -> {
                верх = new Color(0x6B4A2C);
                низ = new Color(0x3A2616);
                блик = new Color(255, 236, 200, 26);
            }
            case "light" -> {
                верх = new Color(0xC9BFA6);
                низ = new Color(0x8E8571);
                блик = new Color(255, 255, 255, 34);
            }
            default -> {
                верх = new Color(0x3E4440);
                низ = new Color(0x1F2326);
                блик = new Color(255, 255, 255, 16);
            }
        }
        g.setPaint(new java.awt.GradientPaint(0, 0, верх, w, h, низ));
        g.fillRect(0, 0, w, h);
        g.setPaint(new java.awt.RadialGradientPaint(
            new java.awt.geom.Point2D.Double(w / 2.0, h * 0.45), (float) (w * 0.60),
            new float[]{0.45f, 1f},
            new Color[]{блик, new Color(0, 0, 0, 130)}));
        g.fillRect(0, 0, w, h);
    }

    // ==================== поле ====================

    private static void поле(Graphics2D g, GameState st, double cxМм, double cyМм) {
        double size = ГЕКС_R * k;
        List<ReplayRecord.HexInfo> infos = new ArrayList<>();
        double minx = Double.MAX_VALUE;
        double miny = Double.MAX_VALUE;
        double maxx = -Double.MAX_VALUE;
        double maxy = -Double.MAX_VALUE;
        for (Hex h : st.field.hexes.values()) {
            int[] qr = FieldGeometry.parseQR(h.id);
            if (qr == null) {
                continue;
            }
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            minx = Math.min(minx, c[0]);
            maxx = Math.max(maxx, c[0]);
            miny = Math.min(miny, c[1]);
            maxy = Math.max(maxy, c[1]);
            infos.add(ReplayRecord.HexInfo.of(h));
        }
        int w = (int) Math.ceil(maxx - minx + 3 * size);
        int h = (int) Math.ceil(maxy - miny + 3 * size);
        BufferedImage слой = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg = слой.createGraphics();
        качество(gg);
        FieldPainter.dark = false;
        FieldPainter.showCardboard = true;
        FieldPainter.showBlocks = false;
        FieldPainter.paintField(new Java2DCanvas(gg, 1, Theme.body()), size, infos,
            ReplayRecord.snapshotOf(st, null), 1.5 * size - minx, 1.5 * size - miny, false);
        gg.dispose();
        int x0 = px(cxМм) - w / 2;
        int y0 = px(cyМм) - h / 2;
        положить(g, слой, x0, y0, w, h, 3.0);

        // ЯКОРЯ НА ПОЛЕ — по настоящим гексам, а не на глаз. Смещение то же, с
        // каким поле нарисовано: (1,5·size − min) внутри слоя плюс угол слоя.
        double dx = x0 + 1.5 * size - minx;
        double dy = y0 + 1.5 * size - miny;
        полеРазмер = size;
        полеDX = dx;
        полеDY = dy;
        крайПоля(st, size);
        якорьТ("1", px(cxМм) + w * 0.18, px(cyМм) - h * 0.06);
        for (Hex hex : st.field.hexes.values()) {
            int[] qr = FieldGeometry.parseQR(hex.id);
            if (qr == null) {
                continue;
            }
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            double hx = c[0] + dx;
            double hy = c[1] + dy;
            if (hex.hasSpawnTile() && !ЯКОРЯ.containsKey("2")) {
                якорьТ("2", hx, hy);
            }
            if (hex.hasNeutral() && !ЯКОРЯ.containsKey("3")) {
                // Нейтрал лежит НЕ в центре гекса, а полосой по своим сторонам:
                // выноска должна смотреть на сам картон, а не на пустую середину.
                List<Integer> углы = hex.neutrals.get(0).corners;
                double sx = 0;
                double sy = 0;
                for (int сторона : углы) {
                    double a = Math.toRadians(FieldGeometry.edgeAngle(сторона));
                    sx += Math.cos(a);
                    sy += Math.sin(a);
                }
                double норма = Math.hypot(sx, sy);
                double сдвиг = норма < 1e-6 ? 0 : size * 0.62 / норма;
                якорьТ("3", hx + sx * сдвиг, hy + sy * сдвиг);
            }
        }
        // ЦУ и пехота первого игрока: их видно на его стартовом гексе.
        for (ReplayRecord.Tok тк : ReplayRecord.snapshotOf(st, null).tokens) {
            if (тк.hexId == null || тк.owner != 0) {
                continue;
            }
            int[] qr = FieldGeometry.parseQR(тк.hexId);
            if (qr == null) {
                continue;
            }
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            if ("command_center".equals(тк.type)) {
                якорьТ("17", c[0] + dx, c[1] + dy - size * 0.45);
            } else if ("infantry".equals(тк.type)) {
                якорьТ("18", c[0] + dx, c[1] + dy + size * 0.45);
            }
        }
    }

    /** Геометрия уже нарисованного поля — по ней приставляются общие планшеты. */
    private static double полеРазмер;
    private static double полеDX;
    private static double полеDY;
    private static int[] крайЛево;
    private static int[] крайПраво;
    /** Куда встали общие планшеты — по ним раскладываются колоды и запас. */
    private static int[] наука;
    private static int[] рынок;
    /** Личная зона ближнего игрока: x, y, ширина, высота в точках. */
    private static int[] зона0;

    /**
     * КРАЙНИЕ КЛЕТКИ ПОЛЯ слева и справа — те, к которым приставляются общие
     * планшеты. Из крайнего столбца берётся клетка, ближняя к середине поля:
     * приставленный к ней планшет не свесится за край стола.
     */
    private static void крайПоля(GameState st, double size) {
        int minQ = Integer.MAX_VALUE;
        int maxQ = Integer.MIN_VALUE;
        List<int[]> все = new ArrayList<>();
        double sumY = 0;
        for (Hex h : st.field.hexes.values()) {
            int[] qr = FieldGeometry.parseQR(h.id);
            if (qr == null) {
                continue;
            }
            все.add(qr);
            minQ = Math.min(minQ, qr[0]);
            maxQ = Math.max(maxQ, qr[0]);
            sumY += FieldGeometry.hexCenter(qr[0], qr[1], size)[1];
        }
        double серединаY = все.isEmpty() ? 0 : sumY / все.size();
        крайЛево = ближе(все, minQ, серединаY, size);
        крайПраво = ближе(все, maxQ, серединаY, size);
    }

    private static int[] ближе(List<int[]> все, int q, double серединаY, double size) {
        int[] лучший = null;
        double лучшее = Double.MAX_VALUE;
        for (int[] qr : все) {
            if (qr[0] != q) {
                continue;
            }
            double d = Math.abs(FieldGeometry.hexCenter(qr[0], qr[1], size)[1] - серединаY);
            if (d < лучшее) {
                лучшее = d;
                лучший = qr;
            }
        }
        return лучший == null ? new int[]{q, 0} : лучший;
    }

    /** Точка поля по осевым координатам — в точках уже нарисованной картинки. */
    private static double[] клетка(int q, int r) {
        double[] c = FieldGeometry.hexCenter(q, r, полеРазмер);
        return new double[]{c[0] + полеDX, c[1] + полеDY};
    }

    // ==================== общие планшеты ====================

    private static void планшетНауки(Graphics2D g) {
        BufferedImage art = Textures.board("science", null);
        if (art == null || крайЛево == null) {
            return;
        }
        double s = полеРазмер / АРТ_R;
        int w = (int) Math.round(art.getWidth() * s);
        int h = (int) Math.round(art.getHeight() * s);
        // Правая доля планшета встаёт в клетку слева-сверху от края поля.
        double[] цель = клетка(крайЛево[0] - 1, крайЛево[1]);
        int x = (int) Math.round(цель[0] - НАУКА_ПРАВАЯ[0] * s);
        int y = (int) Math.round(цель[1] - НАУКА_ПРАВАЯ[1] * s);
        наука = new int[]{x, y, w, h};
        положить(g, art, x, y, w, h, 2.2);
        // Карты супер-арсенала на вершинах треков — по якорям печати.
        BoardAnchors.Science sci = BoardAnchors.science();
        BufferedImage карта = Textures.card("deck_super_arsenal");
        if (sci == null || карта == null) {
            return;
        }
        double sk = w / (double) sci.artW();
        for (var e : sci.tracks().entrySet()) {
            var t = e.getValue();
            положить(g, карта, x + (int) Math.round(t.cx() * sk),
                y + (int) Math.round(t.cy() * sk),
                (int) Math.round(t.cw() * sk), (int) Math.round(t.ch() * sk), 1.2);
        }
    }

    private static void планшетРынка(Graphics2D g, GameState st) {
        BufferedImage art = Textures.board("market", null);
        if (art == null || крайПраво == null) {
            return;
        }
        double s = полеРазмер / АРТ_R;
        int w = (int) Math.round(art.getWidth() * s);
        int h = (int) Math.round(art.getHeight() * s);
        // Левая доля планшета встаёт в клетку справа-снизу от края поля.
        double[] цель = клетка(крайПраво[0] + 1, крайПраво[1]);
        int x = (int) Math.round(цель[0] - РЫНОК_ЛЕВАЯ[0] * s);
        int y = (int) Math.round(цель[1] - РЫНОК_ЛЕВАЯ[1] * s);
        рынок = new int[]{x, y, w, h};
        положить(g, art, x, y, w, h, 2.2);
        int[] рамка = BoardAnchors.marketCard();
        BufferedImage лицо = Textures.card("market_face", "deck_market");
        if (рамка != null && лицо != null) {
            double sk = w / (double) art.getWidth();
            положить(g, лицо, x + (int) Math.round(рамка[0] * sk),
                y + (int) Math.round(рамка[1] * sk),
                (int) Math.round(рамка[2] * sk), (int) Math.round(рамка[3] * sk), 1.2);
        }
        // Колода рынка рубашкой вверх — рядом с планшетом, откуда её и открывают.
        BufferedImage рубашка = Textures.card("deck_market");
        if (рубашка != null) {
            int кw = px(КАРТА_РЫНКА_Ш);
            int кh = (int) Math.round(кw * рубашка.getHeight() / (double) рубашка.getWidth());
            стопка(g, рубашка, x + w / 2 - кw / 2, y + h + px(14), кw, кh, 5);
        }
    }

    private record Колода(String тег, double ширинаМм) {
    }

    /** Колоды заданий, контейнеров и супер-заданий — общим местом стола. */
    private static void колодыЗаданий(Graphics2D g, double cx, double cy) {
        колодыТ(g, cx, cy, List.of(
            new Колода("deck_objectives", КАРТА_ЗАДАНИЕ_Ш),
            new Колода("deck_objectives_start", КАРТА_ЗАДАНИЕ_Ш),
            new Колода("deck_super_objectives", КАРТА_ЗАДАНИЕ_Ш)));
    }

    /** Колоды арсенала — «рядом с планшетом науки» (подготовка, шаг 5). */
    private static void колодыАрсенала(Graphics2D g, double cx, double cy) {
        колодыТ(g, cx, cy, List.of(
            new Колода("deck_arsenal", КАРТА_АРСЕНАЛ_Ш),
            new Колода("deck_arsenal_start", КАРТА_АРСЕНАЛ_Ш),
            new Колода("deck_containers", КАРТА_КОНТЕЙНЕР_Ш)));
    }

    /**
     * Колоды общего стола стопками: карта, а под ней край второй и третьей.
     * Координаты — В ТОЧКАХ картинки: колоды привязаны к планшетам, а те встают
     * к полю и заранее не знают, где окажутся в миллиметрах стола.
     */
    private static void колодыТ(Graphics2D g, double cx, double cy, List<Колода> список) {
        double зазор = 16;
        double всего = -зазор;
        List<BufferedImage> арт = new ArrayList<>();
        for (Колода к : список) {
            BufferedImage a = Textures.card(к.тег());
            арт.add(a);
            if (a != null) {
                всего += к.ширинаМм() + зазор;
            }
        }
        double x = cx - px(всего) / 2.0;
        for (int i = 0; i < список.size(); i++) {
            BufferedImage a = арт.get(i);
            if (a == null) {
                continue;      // печати ещё нет — на столе пусто, врать не будем
            }
            Колода к = список.get(i);
            int w = px(к.ширинаМм());
            int h = (int) Math.round(w * a.getHeight() / (double) a.getWidth());
            стопка(g, a, (int) Math.round(x), (int) Math.round(cy) - h / 2, w, h, 4);
            x += px(к.ширинаМм() + зазор);
        }
    }

    /** Стопка карт: нижние листы выглядывают на волосок. */
    private static void стопка(Graphics2D g, BufferedImage art, int x, int y,
                               int w, int h, int листов) {
        int сдвиг = Math.max(1, px(0.6));
        for (int i = листов - 1; i >= 0; i--) {
            положить(g, art, x + i * сдвиг, y + i * сдвиг, w, h, i == листов - 1 ? 1.6 : 0);
        }
    }

    // ==================== зоны игроков ====================

    /**
     * ЛИЧНАЯ ЗОНА ИГРОКА: сцепка печатных планшетов, а перед ней рука приказов.
     * Первый игрок сидит снизу, второй слева, третий справа, и всё повёрнуто к
     * своему хозяину — как на столе.
     */
    private static void зонаИгрока(Graphics2D g, Session session, int seat,
                                   double cx, double cy, double угол) {
        BoardSheet лист = new BoardSheet(session, seat);
        double пропорция = лист.пропорцияСцепки();
        if (пропорция <= 0) {
            return;
        }
        int ширина = (int) Math.round(px(ВОЙСКА_Ш) * ширинаСцепкиКВойскам(seat));
        int высота = (int) Math.round(ширина / пропорция);
        BufferedImage холст = new BufferedImage(ширина, высота, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg = холст.createGraphics();
        качество(gg);
        PrintedBoards.подписиЗапаса = false;    // на столе цифр «3 из 4» нет
        лист.печатнаяСцепка(gg, 0, 0, ширина);
        PrintedBoards.подписиЗапаса = true;
        gg.dispose();

        Graphics2D t = (Graphics2D) g.create();
        качество(t);
        t.translate(px(cx), px(cy));
        t.rotate(Math.toRadians(угол));
        положить(t, холст, -холст.getWidth() / 2, -холст.getHeight() / 2,
            холст.getWidth(), холст.getHeight(), 2.6);
        рукаПриказов(t, seat, холст.getHeight());
        ресурсыИгрока(t, session, seat, холст.getWidth(), холст.getHeight());
        t.dispose();

        // ЯКОРЯ ЛИЧНОЙ ЗОНЫ снимаются с ближнего игрока: он смотрит на нас, и
        // выноска к его планшету читается без поворота головы.
        if (seat == 0 && угол == 0) {
            double лx = px(cx) - холст.getWidth() / 2.0;
            double лy = px(cy) - холст.getHeight() / 2.0;
            зона0 = new int[]{(int) Math.round(лx), (int) Math.round(лy),
                холст.getWidth(), холст.getHeight()};
            якорьТ("11", лx + холст.getWidth() * 0.14, лy + холст.getHeight() * 0.62);
            якорьТ("12", лx + холст.getWidth() * 0.13, лy + холст.getHeight() * 0.035);
            якорьТ("13", px(cx) + холст.getWidth() * 0.60, лy + холст.getHeight() * 1.02);
            якорьТ("14", лx + холст.getWidth() * 0.965, лy + холст.getHeight() * 0.74);
            якорьТ("15", px(cx) - px(КАРТА_ЗАДАНИЕ_Ш) * 0.9,
                px(cy) + холст.getHeight() / 2.0 + px(14) + px(КАРТА_ЗАДАНИЕ_Ш) * 0.78);
            якорьТ("16", px(cx) + px(КАРТА_ЗАДАНИЕ_Ш) * 2.3,
                px(cy) + холст.getHeight() / 2.0 + px(14) + px(КАРТА_ЗАДАНИЕ_Ш) * 0.78);
        }
    }

    /**
     * СТАРТОВЫЕ РЕСУРСЫ ИГРОКА (подготовка, шаг 13) — монетами и кубиками рядом
     * с его планшетом, как они и лежат у руки. Сколько чего, спрашивается у
     * движка, а не пишется сюда числом.
     */
    private static void ресурсыИгрока(Graphics2D g, Session session, int seat,
                                      int ширина, int высота) {
        var f = session.frame();
        if (f == null || f.snapshot == null || seat >= f.snapshot.players.size()) {
            return;
        }
        ReplayRecord.Player p = f.snapshot.players.get(seat);
        double x = ширина / 2.0 + px(26);
        double y = высота / 2.0 + px(16);
        for (int i = 0; i < p.coin; i++) {
            MarkIcons.paint(g, "COIN", x + (i % 3) * px(17), y + (i / 3) * px(17),
                px(15), null);
        }
        for (int i = 0; i < p.kelium; i++) {
            MarkIcons.paint(g, "KELIUM", x + px(56), y + i * px(15), px(13), null);
        }
        for (int i = 0; i < p.ammo; i++) {
            MarkIcons.paint(g, "AMMO", x + px(76), y + i * px(15), px(13), null);
        }
    }

    /** Отношение ширины сцепки к ширине одного планшета войск. */
    private static double ширинаСцепкиКВойскам(int seat) {
        var с = PrintedBoards.сцепка(seat);
        BufferedImage вой = Textures.board("troop-p1", "troop-A");
        if (с == null || вой == null) {
            return 1;
        }
        return с.ширина() / вой.getWidth();
    }

    /**
     * РУКА ПРИКАЗОВ — пять карт рубашкой вверх веером перед планшетом, плюс
     * карта супер-задания и начальное задание рядом: подготовка, шаги 15 и 16.
     */
    private static void рукаПриказов(Graphics2D g, int seat, int высотаСцепки) {
        BufferedImage back = Textures.card("orders/"
            + ЦВЕТ_ПРИКАЗОВ[Math.floorMod(FieldGeometry.seatColor(seat), 4)],
            "orders/back");
        if (back == null) {
            return;
        }
        int w = px(КАРТА_ЗАДАНИЕ_Ш);
        int h = (int) Math.round(w * back.getHeight() / (double) back.getWidth());
        int y = высотаСцепки / 2 + px(14) + h / 2;
        double шаг = w * 0.66;
        for (int i = 0; i < 5; i++) {
            Graphics2D c = (Graphics2D) g.create();
            качество(c);
            c.translate(-шаг * 2 + шаг * i, y);
            c.rotate(Math.toRadians((i - 2) * 6));
            положить(c, back, -w / 2, -h / 2, w, h, 1.4);
            c.dispose();
        }
        // Справа от руки — начальное задание и супер-задание рубашкой вверх
        // (подготовка, шаги 15 и 16), слева — две памятки игрока.
        int x = (int) Math.round(шаг * 2 + w * 0.78);
        for (String тег : new String[]{"deck_objectives_start", "deck_super_objectives"}) {
            BufferedImage карта = Textures.card(тег, "deck_objectives");
            if (карта != null) {
                положить(g, карта, x, y - h / 2, w, h, 1.4);
                x += w + px(8);
            }
        }
        int xп = (int) Math.round(-шаг * 2 - w * 0.78);
        for (String тег : new String[]{"aid_actions", "aid_round"}) {
            BufferedImage п = Textures.card(тег);
            if (п != null) {
                int пh = (int) Math.round(w * п.getHeight() / (double) п.getWidth());
                положить(g, п, xп - w, y - пh / 2, w, пh, 1.4);
                xп -= w + px(8);
            }
        }
    }

    // ==================== общий запас ====================

    /**
     * ОБЩИЙ ЗАПАС (подготовка, шаг 9): монеты, кубики келемия, боеприпасов,
     * трофеев и энергии и жетоны модулей хранилища — кучками, как их и
     * вываливают на стол, а не ровными рядами.
     */
    private static void общийЗапасТ(Graphics2D g, double cx, double cy) {
        double шаг = px(74);
        double строка = px(66);
        кучка(g, "COIN", cx - шаг, cy - строка / 2, 7, 15);
        кучка(g, "KELIUM", cx, cy - строка / 2, 8, 13);
        кучка(g, "AMMO", cx + шаг, cy - строка / 2, 8, 13);
        кучка(g, "TROPHY", cx - шаг / 2, cy + строка / 2, 7, 13);
        кубики(g, cx + шаг / 2, cy + строка / 2, 8, 13,
            new Color(0xF2C51F), new Color(0x8A6E0B));
        // Жетоны модулей хранилища — печатные, лежат тем же запасом.
        BufferedImage жет = Textures.module("mod_store_energy");
        if (жет != null) {
            int w = px(30);
            int h = (int) Math.round(w * жет.getHeight() / (double) жет.getWidth());
            for (int i = 0; i < 4; i++) {
                положить(g, жет, (int) Math.round(cx + шаг * 1.5) + i * px(7),
                    (int) Math.round(cy - строка / 2) + i * px(9) - h / 2, w, h, 1.2);
            }
        }
    }

    /** Общий сид «россыпи»: кучки должны быть одинаковыми от запуска к запуску. */
    private static final java.util.Random РОССЫПЬ = new java.util.Random(4242);

    /** Кучка фишек: россыпью, слегка внахлёст. */
    private static void кучка(Graphics2D g, String код, double cx, double cy,
                              int сколько, double размер) {
        РОССЫПЬ.setSeed(код.hashCode());
        for (int i = 0; i < сколько; i++) {
            double dx = (РОССЫПЬ.nextDouble() - 0.5) * px(размер) * 1.9;
            double dy = (РОССЫПЬ.nextDouble() - 0.5) * px(размер) * 1.4;
            MarkIcons.paint(g, код, cx + dx, cy + dy, px(размер), null);
        }
    }

    /** То же для кубика своего цвета — энергии в наборе значков нет. */
    private static void кубики(Graphics2D g, double cx, double cy, int сколько,
                               double ребро, Color тело, Color кант) {
        РОССЫПЬ.setSeed(777);
        for (int i = 0; i < сколько; i++) {
            double dx = (РОССЫПЬ.nextDouble() - 0.5) * px(ребро) * 1.9;
            double dy = (РОССЫПЬ.nextDouble() - 0.5) * px(ребро) * 1.4;
            double x = cx + dx - px(ребро) / 2.0;
            double y = cy + dy - px(ребро) / 2.0;
            RoundRectangle2D r = new RoundRectangle2D.Double(x, y, px(ребро), px(ребро),
                px(ребро) * 0.22, px(ребро) * 0.22);
            g.setColor(тело);
            g.fill(r);
            g.setColor(кант);
            g.setStroke(new BasicStroke((float) Math.max(1, px(0.7))));
            g.draw(r);
        }
    }

    /** Два мешочка модулей: красный для боя, синий для сборки. */
    private static void мешочкиТ(Graphics2D g, double cx, double cy) {
        мешочек(g, cx - px(42), cy, new Color(0x9C2B21), new Color(0x63170F));
        мешочек(g, cx + px(42), cy, new Color(0x25538C), new Color(0x14304F));
    }

    /**
     * КИСЕТ С ЖЕТОНАМИ — вид сверху: мятое тканевое тело, собранная горловина и
     * шнурок с двумя хвостами. Печати у мешочков нет, поэтому это единственная
     * вещь на картинке, нарисованная кодом, а не снятая с компонента.
     */
    private static void мешочек(Graphics2D g, double cx, double cy,
                                Color тело, Color тень) {
        double ш = 78;
        double в = 92;
        double x = cx - px(ш) / 2.0;
        double y = cy - px(в) / 2.0;
        Path2D мешок = new Path2D.Double();
        мешок.moveTo(x + px(ш) * 0.30, y + px(в) * 0.30);
        мешок.curveTo(x - px(ш) * 0.12, y + px(в) * 0.58,
            x + px(ш) * 0.02, y + px(в) * 0.99, x + px(ш) * 0.50, y + px(в) * 0.99);
        мешок.curveTo(x + px(ш) * 0.98, y + px(в) * 0.99,
            x + px(ш) * 1.12, y + px(в) * 0.58, x + px(ш) * 0.70, y + px(в) * 0.30);
        мешок.closePath();
        тень(g, мешок, 2.6);
        g.setPaint(new java.awt.RadialGradientPaint(
            new java.awt.geom.Point2D.Double(x + px(ш) * 0.38, y + px(в) * 0.52),
            (float) px(ш * 0.78), new float[]{0f, 1f},
            new Color[]{светлее(тело, 1.25), тень}));
        g.fill(мешок);
        // складки ткани
        g.setColor(new Color(0, 0, 0, 46));
        g.setStroke(new BasicStroke((float) px(0.9), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 4; i++) {
            double ф = 0.24 + i * 0.17;
            Path2D складка = new Path2D.Double();
            складка.moveTo(x + px(ш) * ф, y + px(в) * 0.36);
            складка.quadTo(x + px(ш) * (ф - 0.05), y + px(в) * 0.70,
                x + px(ш) * (ф + 0.03), y + px(в) * 0.95);
            g.draw(складка);
        }
        // горловина: сборка и шнурок
        java.awt.geom.Ellipse2D горло = new Ellipse2D.Double(x + px(ш) * 0.26,
            y + px(в) * 0.10, px(ш) * 0.48, px(в) * 0.30);
        g.setPaint(new java.awt.GradientPaint(
            (float) горло.getX(), (float) горло.getY(), светлее(тело, 1.05),
            (float) горло.getMaxX(), (float) горло.getMaxY(), тень));
        g.fill(горло);
        g.setColor(светлее(тело, 0.85));
        g.setStroke(new BasicStroke((float) px(1.1)));
        g.draw(горло);
        g.setColor(new Color(0xD9CCA8));
        g.setStroke(new BasicStroke((float) px(1.8), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(x + px(ш) * 0.24, y + px(в) * 0.24,
            px(ш) * 0.52, px(в) * 0.16));
        Path2D хвост = new Path2D.Double();
        хвост.moveTo(x + px(ш) * 0.24, y + px(в) * 0.30);
        хвост.quadTo(x + px(ш) * 0.10, y + px(в) * 0.30,
            x + px(ш) * 0.06, y + px(в) * 0.20);
        g.draw(хвост);
        Path2D хвост2 = new Path2D.Double();
        хвост2.moveTo(x + px(ш) * 0.76, y + px(в) * 0.30);
        хвост2.quadTo(x + px(ш) * 0.90, y + px(в) * 0.30,
            x + px(ш) * 0.94, y + px(в) * 0.20);
        g.draw(хвост2);
    }

    private static Color светлее(Color c, double k) {
        return new Color(
            Math.min(255, (int) Math.round(c.getRed() * k)),
            Math.min(255, (int) Math.round(c.getGreen() * k)),
            Math.min(255, (int) Math.round(c.getBlue() * k)));
    }

    /** Фишка первого игрока — круглый жетон со звездой. */
    private static void фишкаПервогоТ(Graphics2D g, double cx, double cy) {
        double d = 44;
        Ellipse2D e = new Ellipse2D.Double(cx - px(d) / 2.0, cy - px(d) / 2.0,
            px(d), px(d));
        тень(g, e, 2.0);
        g.setPaint(new java.awt.GradientPaint((float) e.getX(), (float) e.getY(),
            new Color(0xF2D46A), (float) e.getMaxX(), (float) e.getMaxY(),
            new Color(0xC79B22)));
        g.fill(e);
        g.setColor(new Color(0x6F5410));
        g.setStroke(new BasicStroke((float) px(1.4)));
        g.draw(e);
        g.setColor(new Color(0x6F5410));
        g.fill(звезда(cx, cy, px(d) * 0.34, px(d) * 0.15));
    }

    private static Path2D звезда(double cx, double cy, double r1, double r2) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(-90 + i * 36);
            double r = i % 2 == 0 ? r1 : r2;
            double x = cx + r * Math.cos(a);
            double y = cy + r * Math.sin(a);
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    // ==================== тени ====================

    /**
     * ПОЛОЖИТЬ КОМПОНЕНТ НА СТОЛ: сначала размытая тень по его силуэту, потом
     * он сам. Без тени картон выглядит наклейкой, а не предметом.
     *
     * @param мм насколько мягкая и далёкая тень (миллиметры стола); 0 — без тени
     */
    private static void положить(Graphics2D g, BufferedImage art, int x, int y,
                                 int w, int h, double мм) {
        if (мм > 0) {
            BufferedImage s = силуэт(art);
            Graphics2D gg = (Graphics2D) g.create();
            gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.42f));
            int р = px(мм);
            gg.drawImage(s, x - р / 2, y + р / 3, w + р, h + р, null);
            gg.dispose();
        }
        g.drawImage(art, x, y, w, h, null);
    }

    private static void тень(Graphics2D g, java.awt.Shape s, double мм) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.translate(px(мм) * 0.4, px(мм) * 0.6);
        gg.setColor(new Color(0, 0, 0, 90));
        gg.fill(s);
        gg.dispose();
    }

    private static final java.util.Map<BufferedImage, BufferedImage> СИЛУЭТЫ =
        new java.util.WeakHashMap<>();

    /**
     * РАЗМЫТЫЙ ЧЁРНЫЙ СИЛУЭТ картинки. Размывается уменьшением и обратным
     * растягиванием: свёртка по картинке в четыре мегапикселя стоила бы секунд,
     * а разницы на мягкой тени не видно.
     */
    private static BufferedImage силуэт(BufferedImage art) {
        return СИЛУЭТЫ.computeIfAbsent(art, src -> {
            int w = Math.max(4, src.getWidth() / 12);
            int h = Math.max(4, src.getHeight() / 12);
            BufferedImage мал = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = мал.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.setComposite(AlphaComposite.SrcIn);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
            g.dispose();
            return мал;
        });
    }
}
