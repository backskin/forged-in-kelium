package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;

import kelium.report.ReplayRecord;
import kelium.report.Textures;

/**
 * ПЕЧАТНЫЕ ПЛАНШЕТЫ ИГРОКА — те самые, что лежат на столе.
 *
 * <p>Просьба дизайнера 25.08.2026: «мне очень нравится, как выглядит планшет,
 * хочу, чтобы ты сделал такой же». Поэтому здесь не рисованная копия, а САМА
 * картинка компонента из типографии, а поверх неё кладётся живое: кубики склада
 * в напечатанные ячейки, жетоны модулей в свои рамки. Куда именно класть —
 * говорит {@link BoardAnchors} (координаты сняты с этой же картинки).
 *
 * <p>Нет картинки своей стороны — {@link #available} отвечает «нет», и планшет
 * показывается прежним рисованным видом {@link BoardSheet}. Печать не источник
 * правил: где она расходится с движком, играется движок.
 */
final class PrintedBoards {

    private PrintedBoards() {
    }

    /**
     * ПЛАНШЕТ ВЫБИРАЕТСЯ ПО ЦВЕТУ ИГРОКА, А НЕ ПО СТОРОНЕ.
     *
     * <p>Сторон «А» и «Б» больше нет (решение дизайнера 09.09.2026): асимметрия
     * планшетами упразднена. Вместо неё у каждого игрока СВОЙ планшет своего
     * цвета — с его портретом и его палитрой, чтобы за столом было видно, где
     * чьё хозяйство. Печать на всех четырёх одна и та же.
     *
     * <p>Старые планшеты сторон остаются запасным вариантом: по ним читаются
     * записи прошлых партий, где цветных планшетов ещё не было.
     */
    private static String цвет(int seat) {
        return "p" + (ПЛАНШЕТ_ПО_ЦВЕТУ[kelium.report.FieldGeometry.seatColor(seat)]);
    }

    /**
     * ЦВЕТОВОЕ ГНЕЗДО → НОМЕР ПЛАНШЕТА В ПАПКЕ.
     *
     * <p>Нумерация у двух наборов печати РАЗНАЯ, и это не описка, а факт: у
     * жетонов войск {@code p1} синий, {@code p2} красный, {@code p3} зелёный,
     * {@code p4} жёлтый; у планшетов игроков {@code p1} красный, {@code p2}
     * зелёный, {@code p3} синий, {@code p4} песочный. Связывать их по номеру
     * нельзя — тогда у синего игрока был бы красный планшет. Связываем по
     * ЦВЕТУ: гнездо 0 (синее) берёт планшет p3 и так далее.
     */
    private static final int[] ПЛАНШЕТ_ПО_ЦВЕТУ = {3, 1, 2, 4};

    /** Есть ли печатный планшет обоих видов для этого игрока. */
    static boolean available(int seat) {
        return troopArt(seat) != null && storageArt(seat) != null;
    }

    private static BufferedImage troopArt(int seat) {
        return Textures.board("troop-" + цвет(seat), "troop-A");
    }

    private static BufferedImage storageArt(int seat) {
        return Textures.board("storage-" + цвет(seat), "storage-A");
    }

    /** Высота планшета войск при такой ширине (0 — картинки нет). */
    static int troopHeight(int seat, int width) {
        return height(troopArt(seat), width);
    }

    /** Высота планшета хранилища при такой ширине (0 — картинки нет). */
    static int storageHeight(int seat, int width) {
        return height(storageArt(seat), width);
    }

    /** Якоря планшета войск этого игрока (с откатом на планшет стороны А). */
    private static java.util.List<BoardAnchors.Column> troopCols(int seat) {
        var cols = BoardAnchors.troop(цвет(seat));
        return cols.isEmpty() ? BoardAnchors.troop("A") : cols;
    }

    /** Якоря планшета хранилища этого игрока (с откатом на планшет стороны А). */
    private static java.util.List<BoardAnchors.Cell> storageCells(int seat) {
        var cells = BoardAnchors.storage(цвет(seat));
        return cells.isEmpty() ? BoardAnchors.storage("A") : cells;
    }

    private static int height(BufferedImage art, int width) {
        return art == null ? 0 : (int) Math.round(width * art.getHeight()
            / (double) art.getWidth());
    }

    // ==================== планшет войск ====================

    /**
     * ПЛАНШЕТ ВОЙСК с жетонами модулей в напечатанных рамках.
     *
     * <p>В рамке спец-атаки лежит красный жетон, если игрок его туда положил;
     * пусто — там показана цель, по которой род бьёт ПО ДВИЖКУ. Это важнее
     * напечатанного значка: играется движок, и если художник ещё не перерисовал
     * ячейку после смены правил, игрок увидит настоящее правило, а не старое.
     */
    static void paintTroop(Graphics2D g, int x, int y, int width,
                           ReplayRecord.Player p, kelium.core.TroopSide troop,
                           Map<Rectangle, Object[]> spots) {
        BufferedImage art = troopArt(p.seat);
        if (art == null) {
            return;
        }
        double k = width / (double) art.getWidth();
        int h = (int) Math.round(art.getHeight() * k);
        g.drawImage(art, x, y, width, h, null);
        for (BoardAnchors.Column c : troopCols(p.seat)) {
            paintTroopAttack(g, x, y, k, c, p, troop, spots);
            paintTroopAssembly(g, x, y, k, c, p, spots);
        }
    }

    private static void paintTroopAttack(Graphics2D g, int x, int y, double k,
                                         BoardAnchors.Column c, ReplayRecord.Player p,
                                         kelium.core.TroopSide troop,
                                         Map<Rectangle, Object[]> spots) {
        ReplayRecord.Module m = p.redPlaced.get(c.unit());
        Rectangle box = scale(x, y, k, c.ax(), c.ay(), c.aw(), c.ah());
        if (m != null) {
            // Жетон НАКРЫВАЕТ напечатанную цель целиком — так он и лежит на столе.
            int side = (int) Math.round(Math.min(box.width, box.height) * 0.62);
            int sx = box.x + (box.width - side) / 2;
            int sy = box.y + (box.height - side) / 2;
            shade(g, box);
            ModuleSlot.paint(g, m, ModuleSlot.red(), sx, sy, side);
            spots.put(new Rectangle(sx, sy, side, side),
                new Object[]{m, Boolean.TRUE, Names.unit(c.unit())});
            return;
        }
        if (troop == null || !troop.dualCell()) {
            return;
        }
        kelium.core.Target t = troop.specializedTarget(kelium.core.UnitType.fromCode(c.unit()));
        if (t == null) {
            return;
        }
        // Настоящая цель — узкой плашкой у нижней кромки рамки: не закрывает
        // печатный рисунок, но говорит, по кому род бьёт на самом деле.
        chip(g, box, "→ " + targetName(t), Theme.accent());
    }

    private static void paintTroopAssembly(Graphics2D g, int x, int y, double k,
                                           BoardAnchors.Column c, ReplayRecord.Player p,
                                           Map<Rectangle, Object[]> spots) {
        ReplayRecord.Module m = p.bluePlaced.get(c.building());
        if (m == null) {
            return;
        }
        Rectangle box = scale(x, y, k, c.bx(), c.by(), c.bw(), c.bh());
        int side = (int) Math.round(Math.min(box.width, box.height) * 0.74);
        int sx = box.x + (box.width - side) / 2;
        int sy = box.y + (box.height - side) / 2;
        shade(g, box);
        ModuleSlot.paint(g, m, ModuleSlot.blue(), sx, sy, side);
        spots.put(new Rectangle(sx, sy, side, side),
            new Object[]{m, Boolean.FALSE,
                kelium.gui.GameRecorder.buildingName(c.building())});
    }

    // ==================== планшет хранилища ====================

    /**
     * ПЛАНШЕТ ХРАНИЛИЩА с кубиками в напечатанных ячейках.
     *
     * <p>{@code fill} — что лежит в ячейках каждого складского здания
     * («miner-3» → массив 'K'/'A'/'D'/0), {@code base} — две центральные ячейки,
     * открытые всегда, {@code covered} — здания, чей жетон лежит на планшете и
     * СВОИМИ БОКАМИ накрывает эти ячейки (значит, они не в игре).
     */
    static void paintStorage(Graphics2D g, int x, int y, int width, ReplayRecord.Player p,
                             Map<String, char[]> fill, char[] base, Set<String> covered) {
        BufferedImage art = storageArt(p.seat);
        if (art == null) {
            return;
        }
        double k = width / (double) art.getWidth();
        int h = (int) Math.round(art.getHeight() * k);
        g.drawImage(art, x, y, width, h, null);
        // Рамки ячеек КАЖДОГО складского здания: по ним ляжет сам жетон, если он
        // ещё на планшете (см. ниже, жетонПоверхЯчеек).
        Map<String, java.util.List<Rectangle>> зоны = new java.util.LinkedHashMap<>();
        int seen = 0;
        int lastLevel = -1;
        String lastGroup = "";
        for (BoardAnchors.Cell c : storageCells(p.seat)) {
            if (!c.group().equals(lastGroup) || c.level() != lastLevel) {
                lastGroup = c.group();
                lastLevel = c.level();
                seen = 0;
            }
            Rectangle box = scale(x, y, k, c.x(), c.y(), c.w(), c.h());
            char has;
            boolean open;
            if ("base".equals(c.group())) {
                open = true;
                has = base != null && seen < base.length ? base[seen] : 0;
            } else {
                String key = ("miner".equals(c.group()) ? "miner-" : "plant-") + c.level();
                open = !covered.contains(key);
                char[] arr = fill.get(key);
                has = open && arr != null && seen < arr.length ? arr[seen] : 0;
            }
            seen++;
            if (!open) {
                // ЯЧЕЙКА НАКРЫТА СВОИМ ЖЕТОНОМ — и жетон мы сейчас на неё и
                // положим, поэтому здесь только запоминаем рамки. Раньше на
                // месте накрытых ячеек стоял серый крестик, а сам жетон
                // рисовался ОТДЕЛЬНОЙ группой ниже: одно и то же здание было на
                // листе дважды (жалоба дизайнера 02.09.2026).
                String key = ("miner".equals(c.group()) ? "miner-" : "plant-") + c.level();
                зоны.computeIfAbsent(key, ключ -> new java.util.ArrayList<>()).add(box);
            } else if (has != 0) {
                cube(g, box, has);
            }
        }
        // ПРЯМОУГОЛЬНИК ПЛАНШЕТА: от его середины считается, по какому крылу
        // лежит жетон, а по краям — чтобы картонка не свисала с планшета.
        Rectangle лист = new Rectangle(x, y, width, h);
        // РАЗМЕР ОДИН НА ВСЕ ЖЕТОНЫ, И ЕГО ЗАДАЁТ САМОЕ ТРЕБОВАТЕЛЬНОЕ НАКРЫТИЕ.
        // Картонки добытчика и станции одинаковые, значит и на планшете они
        // обязаны выглядеть одинаково; а закрыть должны каждая свои ячейки. Один
        // размер, посчитанный по худшему случаю, даёт и то, и другое.
        double[] надо = {0, 0};
        for (var e : зоны.entrySet()) {
            double[] своё = габаритНакрытия(e.getValue(), лист);
            надо[0] = Math.max(надо[0], своё[0]);
            надо[1] = Math.max(надо[1], своё[1]);
        }
        for (var e : зоны.entrySet()) {
            жетонПоверхЯчеек(g, e.getKey(), e.getValue(), p.seat, лист, надо);
        }
    }

    /**
     * СКОЛЬКО НАДО НАКРЫТЬ у этого уровня: вдоль жетона и поперёк.
     *
     * <p>Считается тем же способом, каким жетон потом и ляжет, — иначе общий
     * размер оказался бы посчитан по одной геометрии, а положен по другой.
     */
    private static double[] габаритНакрытия(java.util.List<Rectangle> ячейки,
                                            Rectangle лист) {
        double угол = наклон(ячейки, лист);
        double cos = Math.cos(угол);
        double sin = Math.sin(угол);
        double вдольМин = Double.MAX_VALUE;
        double вдольМакс = -Double.MAX_VALUE;
        double поперёкМин = Double.MAX_VALUE;
        double поперёкМакс = -Double.MAX_VALUE;
        for (Rectangle r : ячейки) {
            for (int i = 0; i < 4; i++) {
                double px = (i == 0 || i == 3) ? r.getMinX() : r.getMaxX();
                double py = (i < 2) ? r.getMinY() : r.getMaxY();
                double u = px * cos + py * sin;
                double v = -px * sin + py * cos;
                вдольМин = Math.min(вдольМин, u);
                вдольМакс = Math.max(вдольМакс, u);
                поперёкМин = Math.min(поперёкМин, v);
                поперёкМакс = Math.max(поперёкМакс, v);
            }
        }
        return new double[]{вдольМакс - вдольМин, поперёкМакс - поперёкМин};
    }

    /**
     * НАКЛОН ЖЕТОНА. Есть две ячейки — по линии между ними: они напечатаны
     * вдоль кромки своего крыла, и накрыть их можно только положив картонку по
     * этой линии. Ячейка одна — линии нет, и наклон берётся от геометрии
     * планшета: поперёк радиуса из его середины, то есть параллельно ближней
     * кромке шестиугольника.
     */
    private static double наклон(java.util.List<Rectangle> ячейки, Rectangle лист) {
        if (ячейки.size() > 1) {
            Rectangle a = ячейки.get(0);
            Rectangle b = ячейки.get(ячейки.size() - 1);
            return Math.atan2(b.getCenterY() - a.getCenterY(),
                b.getCenterX() - a.getCenterX());
        }
        double cx = ячейки.get(0).getCenterX();
        double cy = ячейки.get(0).getCenterY();
        return Math.atan2(cy - лист.getCenterY(), cx - лист.getCenterX()) + Math.PI / 2;
    }

    /**
     * МАСШТАБ ЖЕТОНА, ЧТОБЫ НАКРЫТЬ ПЕЧАТНЫЕ ЯЧЕЙКИ ЦЕЛИКОМ.
     *
     * <p>Картонка вытянута (жетон здания в два сектора), поэтому мало сравнить
     * её ширину с длиной ряда ячеек: узкая сторона обязана накрыть их глубину.
     * Берётся тот масштаб, которого хватает по ОБОИМ измерениям, плюс поля —
     * на столе картонка выступает за печать, а не совпадает с ней по кромке.
     */
    private static double масштабПодНакрытие(double texW, double texH,
                                             double вдоль, double поперёк) {
        double поля = 1.05;
        return Math.max(вдоль / texW, поперёк / texH) * поля;
    }

    /**
     * ПОДВИНУТЬ ЖЕТОН ВНУТРЬ ПЛАНШЕТА — НО ТОЛЬКО ВДОЛЬ САМОГО ЖЕТОНА.
     *
     * <p>Печатные ячейки нижних уровней прижаты к кромке планшета, а картонка
     * шире их ряда — положенная строго по ячейкам, она свешивается за лист. На
     * столе игрок подвинет её рукой, и ячейки всё равно останутся накрыты:
     * вдоль картонки у них есть запас. Поэтому сдвиг разрешён ТОЛЬКО по длинной
     * оси жетона и ТОЛЬКО в пределах этого запаса — накрытие ячеек важнее
     * ровного края (заказ дизайнера 09.09.2026: «почему ты стесняешься закрыть
     * полностью всю ячейку?»).
     */
    private static double[] вЛист(double cx, double cy, double w, double h,
                                  double угол, Rectangle лист, double надоВдоль) {
        double cos = Math.abs(Math.cos(угол));
        double sin = Math.abs(Math.sin(угол));
        double полШир = (w * cos + h * sin) / 2;
        double полВыс = (w * sin + h * cos) / 2;
        // Планшет — шестиугольник, поэтому запас от кромки берётся щедрый: у
        // наклонных крыльев угол листа срезан.
        double запас = Math.min(лист.width, лист.height) * 0.02;
        double надоX = Math.max(лист.getMinX() + полШир + запас,
            Math.min(лист.getMaxX() - полШир - запас, cx)) - cx;
        double надоY = Math.max(лист.getMinY() + полВыс + запас,
            Math.min(лист.getMaxY() - полВыс - запас, cy)) - cy;
        // Из нужного сдвига берём только составляющую вдоль жетона и режем её
        // по запасу: дальше поедут уже сами ячейки, а этого нельзя.
        double вдоль = надоX * Math.cos(угол) + надоY * Math.sin(угол);
        double слабина = Math.max(0, (w - надоВдоль) / 2);
        вдоль = Math.max(-слабина, Math.min(слабина, вдоль));
        return new double[]{cx + вдоль * Math.cos(угол), cy + вдоль * Math.sin(угол)};
    }

    /** Уровень здания из ключа вида {@code miner-3}; {@code null} — без уровня. */
    private static Integer уровеньИз(String key) {
        int i = key.indexOf('-');
        if (i < 0) {
            return null;
        }
        try {
            return Integer.valueOf(key.substring(i + 1));
        } catch (NumberFormatException неЧисло) {
            return null;
        }
    }

    /**
     * ЖЕТОН ЗДАНИЯ ПОВЕРХ СВОИХ ПЕЧАТНЫХ ЯЧЕЕК — так, как он лежит на столе.
     *
     * <p>Пока здание не построено, его жетон лежит на планшете и закрывает собой
     * ячейки хранилища. Показывать это крестиком было и скучно, и неправдиво:
     * игрок за столом видит НАСТОЯЩИЙ силуэт добытчика или энергостанции.
     *
     * <p>ПОВОРОТ — ПО КРЫЛУ ПЛАНШЕТА, РАЗМЕР — ЧТОБЫ ЯЧЕЙКА БЫЛА НАКРЫТА
     * ЦЕЛИКОМ. Печать хранилища разложена по шести крыльям шестиугольника, и
     * картонка ложится ВДОЛЬ своего крыла: у нижних крыльев прямо, у верхних и
     * боковых — с наклоном кромки. Поэтому угол считается не по форме пятна
     * ячеек, а по направлению от середины планшета: жетон лежит поперёк
     * радиуса, то есть параллельно ближней кромке — как его и кладут рукой.
     *
     * <p>Ширина берётся такой, чтобы печатные ячейки этого уровня ушли под
     * жетон ПОЛНОСТЬЮ (замечание дизайнера 09.09.2026: «почему ты стесняешься
     * закрыть полностью всю ячейку?»). Считается по габаритам ячеек в осях
     * самого жетона: сколько они занимают вдоль картонки и сколько поперёк —
     * и берётся то, чего не хватает, с небольшим запасом на поля. Накрытая
     * ячейка и означает «здание ещё не построено»: пустой рамке под жетоном
     * взяться откуда.
     */
    private static void жетонПоверхЯчеек(Graphics2D g, String key,
                                         java.util.List<Rectangle> ячейки,
                                         int seat, Rectangle лист, double[] надо) {
        if (ячейки.isEmpty()) {
            return;
        }
        String code = key.startsWith("miner") ? "miner" : "power_plant";
        int уровень = уровеньИз(key);

        double cx = 0;
        double cy = 0;
        for (Rectangle r : ячейки) {
            cx += r.getCenterX();
            cy += r.getCenterY();
        }
        cx /= ячейки.size();
        cy /= ячейки.size();

        double угол = наклон(ячейки, лист);
        // Картонка не лежит вверх ногами: та же линия, но читаемой стороной.
        while (угол > Math.PI / 2) {
            угол -= Math.PI;
        }
        while (угол < -Math.PI / 2) {
            угол += Math.PI;
        }

        // Габариты ячеек В ОСЯХ ЖЕТОНА — сколько накрыть вдоль и сколько поперёк.
        double cos = Math.cos(угол);
        double sin = Math.sin(угол);
        double вдольМин = Double.MAX_VALUE;
        double вдольМакс = -Double.MAX_VALUE;
        double поперёкМин = Double.MAX_VALUE;
        double поперёкМакс = -Double.MAX_VALUE;
        for (Rectangle r : ячейки) {
            for (int i = 0; i < 4; i++) {
                double px = (i == 0 || i == 3) ? r.getMinX() : r.getMaxX();
                double py = (i < 2) ? r.getMinY() : r.getMaxY();
                double u = (px - cx) * cos + (py - cy) * sin;
                double v = -(px - cx) * sin + (py - cy) * cos;
                вдольМин = Math.min(вдольМин, u);
                вдольМакс = Math.max(вдольМакс, u);
                поперёкМин = Math.min(поперёкМин, v);
                поперёкМакс = Math.max(поперёкМакс, v);
            }
        }
        // Жетон кладётся серединой на середину накрытого — иначе край ячейки
        // остаётся торчать с одной стороны.
        cx += (вдольМин + вдольМакс) / 2 * cos - (поперёкМин + поперёкМакс) / 2 * sin;
        cy += (вдольМин + вдольМакс) / 2 * sin + (поперёкМин + поперёкМакс) / 2 * cos;
        // Свои ячейки накрыть обязательно, но размер берётся общий на планшет.
        double надоВдоль = Math.max(вдольМакс - вдольМин, надо[0]);
        double надоПоперёк = Math.max(поперёкМакс - поперёкМин, надо[1]);
        double своёВдоль = вдольМакс - вдольМин;

        // ПЕЧАТНЫЙ ЖЕТОН, ЕСЛИ ОН ЕСТЬ. На столе на планшете лежит та же
        // картонка, что потом встанет на поле, — и узнаётся она по рисунку, а не
        // по цветному силуэту. Силуэт остаётся для тех жетонов, которых художник
        // ещё не рисовал.
        var найдено = Textures.found(code, уровень, seat);
        if (найдено != null && найдено.image() != null) {
            BufferedImage tex = найдено.image();
            double k = масштабПодНакрытие(tex.getWidth(), tex.getHeight(),
                надоВдоль, надоПоперёк);
            double[] место = вЛист(cx, cy, tex.getWidth() * k, tex.getHeight() * k,
                угол, лист, своёВдоль);
            cx = место[0];
            cy = место[1];
            AffineTransform at = new AffineTransform();
            at.translate(cx, cy);
            at.rotate(угол);
            at.scale(k, k);
            at.translate(-tex.getWidth() / 2.0, -tex.getHeight() / 2.0);
            java.awt.Composite было = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, 0.30f));
            g.setColor(java.awt.Color.BLACK);
            AffineTransform тень = new AffineTransform(at);
            тень.preConcatenate(AffineTransform.getTranslateInstance(
                Math.max(1.5, k * 8), Math.max(1.5, k * 8)));
            g.drawImage(tex, тень, null);
            g.setComposite(было);
            g.drawImage(tex, at, null);
            return;
        }
        kelium.report.FieldGeometry.Shape sh;
        try {
            sh = kelium.report.FieldGeometry.buildingByCode(code);
        } catch (RuntimeException e) {
            return;
        }
        // Силуэт — для жетонов, которых художник ещё не рисовал; считается по
        // тому же правилу, иначе разнобой вернётся.
        double k = масштабПодНакрытие(sh.vbW(), sh.vbH(), надоВдоль, надоПоперёк);
        double[] место = вЛист(cx, cy, sh.vbW() * k, sh.vbH() * k, угол, лист, своёВдоль);
        cx = место[0];
        cy = место[1];
        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(угол);
        at.scale(k, k);
        at.translate(-sh.vbW() / 2.0, -sh.vbH() / 2.0);
        java.awt.Shape path = at.createTransformedShape(sh.path());

        // Тень под жетоном: без неё силуэт читается как печать, а не как
        // положенный сверху картонный жетон.
        AffineTransform тень = new AffineTransform();
        тень.translate(Math.max(1.5, k * 6), Math.max(1.5, k * 6));
        g.setColor(Theme.alpha(java.awt.Color.BLACK, 0.28));
        g.fill(тень.createTransformedShape(path));
        g.setColor(Theme.seat(seat));
        g.fill(path);
        g.setColor(Theme.seatStroke(seat));
        g.setStroke(new BasicStroke(Math.max(1.2f, (float) (k * 3))));
        g.draw(path);
    }

    /** Кубик ресурса в напечатанной ячейке: тем же значком, что и везде. */
    private static void cube(Graphics2D g, Rectangle box, char has) {
        double s = Math.min(box.width, box.height) * 0.66;
        double cx = box.x + box.width / 2.0;
        double cy = box.y + box.height / 2.0;
        g.setColor(Theme.alpha(Color.WHITE, 0.9));
        g.fill(new java.awt.geom.Ellipse2D.Double(cx - s * 0.62, cy - s * 0.62,
            s * 1.24, s * 1.24));
        MarkIcons.paint(g, switch (has) {
            case 'K' -> "KELIUM";
            case 'D' -> "TROPHY";
            default -> "AMMO";
        }, cx, cy, s, switch (has) {
            case 'K' -> Theme.kelium();
            case 'D' -> Theme.trophy();
            default -> Theme.ink2();
        });
    }

    // ==================== мелочи рисования ====================

    private static Rectangle scale(int x, int y, double k, int bx, int by, int bw, int bh) {
        return new Rectangle(x + (int) Math.round(bx * k), y + (int) Math.round(by * k),
            (int) Math.round(bw * k), (int) Math.round(bh * k));
    }

    /** Лёгкая подложка под жетон: печать под ним всё равно не читается. */
    private static void shade(Graphics2D g, Rectangle box) {
        g.setColor(Theme.alpha(Theme.paper(), 0.72));
        g.fill(new RoundRectangle2D.Double(box.x, box.y, box.width, box.height,
            box.width * 0.16, box.width * 0.16));
    }

    /** Узкая плашка с подписью у нижней кромки рамки. */
    private static void chip(Graphics2D g, Rectangle box, String text, Color colour) {
        g.setFont(Theme.font(Math.max(8, (int) (box.height * 0.13)), Font.BOLD));
        var fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        int pad = Math.max(3, box.width / 28);
        int w = Math.min(box.width - pad * 2, tw + pad * 3);
        int h = fm.getHeight() + pad;
        int cx = box.x + (box.width - w) / 2;
        int cy = box.y + box.height - h - pad;
        g.setColor(Theme.alpha(Theme.paper(), 0.92));
        g.fill(new RoundRectangle2D.Double(cx, cy, w, h, h * 0.5, h * 0.5));
        g.setColor(Theme.alpha(colour, 0.75));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Double(cx, cy, w, h, h * 0.5, h * 0.5));
        g.setColor(colour);
        g.drawString(text, cx + (w - tw) / 2, cy + h - pad / 2 - fm.getDescent());
    }

    private static String targetName(kelium.core.Target t) {
        return switch (t) {
            case INFANTRY -> "пехота";
            case VEHICLE -> "техника";
            case AIRCRAFT -> "авиация";
            case BUILDINGS_TOWERS -> "здания";
        };
    }
}
