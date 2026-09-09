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
        Map<String, java.util.List<java.awt.geom.Point2D>> зоны = new java.util.LinkedHashMap<>();
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
                зоны.computeIfAbsent(key, ключ -> new java.util.ArrayList<>())
                    .add(new java.awt.geom.Point2D.Double(box.getCenterX(), box.getCenterY()));
            } else if (has != 0) {
                cube(g, box, has);
            }
        }
        // ШИРИНА ЖЕТОНА ОДНА НА ВСЕ ЗДАНИЯ и считается от печатной ячейки, а не
        // от накрытого пятна (почему — см. жетонПоверхЯчеек).
        double ячейка = ширинаЯчейки(storageCells(p.seat)) * k;
        for (var e : зоны.entrySet()) {
            жетонПоверхЯчеек(g, e.getKey(), e.getValue(), p.seat, ячейка);
        }
    }

    /** Ширина печатной ячейки хранилища — они все одного размера. */
    private static double ширинаЯчейки(java.util.List<BoardAnchors.Cell> cells) {
        if (cells.isEmpty()) {
            return 0;
        }
        double сумма = 0;
        for (BoardAnchors.Cell c : cells) {
            сумма += c.w();
        }
        return сумма / cells.size();
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
     * <p>РАЗМЕР У ВСЕХ ОДИН, ПОВОРОТ — ПО ЛИНИИ СВОИХ ЯЧЕЕК. Раньше жетон
     * подгонялся под пятно накрытых ячеек и разворачивался по форме этого пятна
     * — и выходил балаган (замечание дизайнера 09.09.2026: «здания на планшете
     * хранилища гомерически смешно по-разному сидят, все маленькие, повороты
     * рандомные»). Причина простая: у первого уровня одна ячейка, у четвёртого
     * две в ряд, у третьего две по диагонали, поэтому пятно каждый раз другой
     * формы и величины. А на столе лежит ОДНА И ТА ЖЕ картонка: все жетоны
     * добытчика и энергостанции одного размера, в два сектора шириной.
     *
     * <p>Поэтому ширина считается от печатной ячейки и одинакова везде, а
     * ячейки задают только КУДА жетон лёг: центр — между ними, поворот — вдоль
     * линии, которая их соединяет. У пары в ряд это прямо, у пары по диагонали
     * (третий уровень) — по её наклону, ровно как картонку положил бы игрок,
     * чтобы накрыть обе ячейки. Одна ячейка — жетон лежит прямо.
     */
    private static void жетонПоверхЯчеек(Graphics2D g, String key,
                                         java.util.List<java.awt.geom.Point2D> ячейки,
                                         int seat, double ячейка) {
        if (ячейки.isEmpty()) {
            return;
        }
        String code = key.startsWith("miner") ? "miner" : "power_plant";
        int уровень = уровеньИз(key);
        // Жетон здания в два сектора: пара ячеек в ряд плюс запас по краям — так
        // картонка заметна на планшете и накрывает свои ячейки с полями.
        double ширина = ячейка > 0 ? ячейка * 2.6 : 0;
        if (ширина <= 0) {
            return;
        }
        double cx = 0;
        double cy = 0;
        for (java.awt.geom.Point2D t : ячейки) {
            cx += t.getX();
            cy += t.getY();
        }
        cx /= ячейки.size();
        cy /= ячейки.size();
        double угол = 0;
        if (ячейки.size() > 1) {
            java.awt.geom.Point2D a = ячейки.get(0);
            java.awt.geom.Point2D b = ячейки.get(ячейки.size() - 1);
            угол = Math.atan2(b.getY() - a.getY(), b.getX() - a.getX());
            // Жетон читается слева направо: линию ячеек берём в ту же сторону,
            // иначе один и тот же наклон даст перевёрнутую картонку.
            if (Math.abs(угол) > Math.PI / 2) {
                угол += угол > 0 ? -Math.PI : Math.PI;
            }
        }
        // ПЕЧАТНЫЙ ЖЕТОН, ЕСЛИ ОН ЕСТЬ. На столе на планшете лежит та же
        // картонка, что потом встанет на поле, — и узнаётся она по рисунку, а не
        // по цветному силуэту. Силуэт остаётся для тех жетонов, которых художник
        // ещё не рисовал.
        var найдено = Textures.found(code, уровень, seat);
        if (найдено != null && найдено.image() != null) {
            BufferedImage tex = найдено.image();
            double k = ширина / tex.getWidth();
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
        // Силуэт — для жетонов, которых художник ещё не рисовал; размер и
        // поворот те же, что у печатных, иначе разнобой вернётся.
        double k = ширина / sh.vbW();
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
