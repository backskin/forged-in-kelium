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

    /** Есть ли печатный планшет обоих видов для этой стороны. */
    static boolean available(String side) {
        return troopArt(side) != null && storageArt(side) != null;
    }

    private static BufferedImage troopArt(String side) {
        return Textures.board("troop-" + key(side), "troop-A");
    }

    private static BufferedImage storageArt(String side) {
        return Textures.board("storage-" + key(side), "storage-A");
    }

    private static String key(String side) {
        if (side == null || side.isBlank()) {
            return "A";
        }
        return side.trim().toUpperCase(java.util.Locale.ROOT)
            .replace('А', 'A').replace('Б', 'B');
    }

    /** Высота планшета войск при такой ширине (0 — картинки нет). */
    static int troopHeight(String side, int width) {
        return height(troopArt(side), width);
    }

    /** Высота планшета хранилища при такой ширине (0 — картинки нет). */
    static int storageHeight(String side, int width) {
        return height(storageArt(side), width);
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
        BufferedImage art = troopArt(p.side);
        if (art == null) {
            return;
        }
        double k = width / (double) art.getWidth();
        int h = (int) Math.round(art.getHeight() * k);
        g.drawImage(art, x, y, width, h, null);
        for (BoardAnchors.Column c : BoardAnchors.troop(p.side)) {
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
        String side = p.storageSide == null || p.storageSide.isBlank()
            ? p.side : p.storageSide;
        BufferedImage art = storageArt(side);
        if (art == null) {
            return;
        }
        double k = width / (double) art.getWidth();
        int h = (int) Math.round(art.getHeight() * k);
        g.drawImage(art, x, y, width, h, null);
        // Рамки ячеек КАЖДОГО складского здания: по ним ляжет сам жетон, если он
        // ещё на планшете (см. ниже, жетонПоверхЯчеек).
        Map<String, Rectangle> зоны = new java.util.LinkedHashMap<>();
        int seen = 0;
        int lastLevel = -1;
        String lastGroup = "";
        for (BoardAnchors.Cell c : BoardAnchors.storage(side)) {
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
                зоны.merge(key, box, PrintedBoards::объединить);
            } else if (has != 0) {
                cube(g, box, has);
            }
        }
        for (var e : зоны.entrySet()) {
            жетонПоверхЯчеек(g, e.getKey(), e.getValue(), p.seat);
        }
    }

    private static Rectangle объединить(Rectangle a, Rectangle b) {
        return a.union(b);
    }

    /**
     * ЖЕТОН ЗДАНИЯ ПОВЕРХ СВОИХ ПЕЧАТНЫХ ЯЧЕЕК — так, как он лежит на столе.
     *
     * <p>Пока здание не построено, его жетон лежит на планшете и закрывает собой
     * ячейки хранилища. Показывать это крестиком было и скучно, и неправдиво:
     * игрок за столом видит НАСТОЯЩИЙ силуэт добытчика или энергостанции.
     *
     * <p>ПОВОРОТ выбирается по форме места: если рамка ячеек лежит вдоль, а
     * силуэт вытянут поперёк (или наоборот), жетон кладётся на 90°. Плюс
     * небольшой наклон в 8° — жетон на столе никогда не лежит идеально ровно, и
     * именно наклон отличает «положили» от «нарисовали».
     */
    private static void жетонПоверхЯчеек(Graphics2D g, String key, Rectangle box, int seat) {
        String code = key.startsWith("miner") ? "miner" : "power_plant";
        kelium.report.FieldGeometry.Shape sh;
        try {
            sh = kelium.report.FieldGeometry.buildingByCode(code);
        } catch (RuntimeException e) {
            return;
        }
        boolean боком = (box.width >= box.height) != (sh.vbW() >= sh.vbH());
        double уголГрад = (боком ? 90 : 0) + 8;
        double угол = Math.toRadians(уголГрад);
        // Габарит силуэта ПОСЛЕ поворота — иначе повёрнутый жетон вылезает за
        // свои ячейки ровно на столько, на сколько его развернули.
        double cos = Math.abs(Math.cos(угол));
        double sin = Math.abs(Math.sin(угол));
        double wRot = sh.vbW() * cos + sh.vbH() * sin;
        double hRot = sh.vbW() * sin + sh.vbH() * cos;
        double k = Math.min(box.width * 0.94 / wRot, box.height * 0.94 / hRot);
        AffineTransform at = new AffineTransform();
        at.translate(box.getCenterX(), box.getCenterY());
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
            case 'D' -> "DEBRIS";
            default -> "AMMO";
        }, cx, cy, s, switch (has) {
            case 'K' -> Theme.kelium();
            case 'D' -> Theme.debris();
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
