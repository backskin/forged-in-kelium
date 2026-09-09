package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * MarkIcons — ЗНАЧКИ СМЫСЛОВ, нарисованные фигурами.
 *
 * <p>Здесь собрано то, из-за чего интерфейс 1.0 пестрел пустыми квадратами. Значки
 * ресурсов и показателей просились подписать символами вроде {@code ◇ ◈ ◆ ♦ ▣ ⚙ ⚔ ★},
 * но системный шрифт Windows большинства из них не содержит: Java подставляет чужой
 * глиф или рисует квадрат. Проверено дважды — на палитре конструктора и на пульте
 * проигрывателя.
 *
 * <p>Поэтому: рисуем сами, любого размера и любым цветом. Фигуры выбраны так, чтобы
 * различаться силуэтом, а не только цветом — интерфейс должен читаться и в оттенках
 * серого.
 */
public final class MarkIcons {

    private MarkIcons() {
    }

    /** Кубик келемия — классический зелёный, как на планшетах и на поле. */
    private static final Color КЕЛЕМИЙ = new Color(0x2E, 0xA8, 0x4C);
    /** Кубик боеприпаса — красный. */
    private static final Color БОЕПРИПАС = new Color(0xD1, 0x2B, 0x2B);
    /** Кубик трофея — чёрный. */
    private static final Color ТРОФЕЙ = new Color(0x1E, 0x1E, 0x1E);

    /**
     * ОБЪЁМНЫЙ КУБИК: лицевая грань, светлая верхняя и тёмная боковая — как
     * настоящий кубик на столе. Заказ дизайнера 09.09.2026: ресурсы показываются
     * кубиками, а не картинками кристалла, патрона и шестерёнки.
     */
    private static void кубик(Graphics2D g, double cx, double cy, double size, Color цвет) {
        double s = size * 0.94;
        double d = Math.max(2, s * 0.26);          // глубина «объёма»
        double лицо = s - d;
        double x = cx - s / 2;
        double y = cy - s / 2;
        // верхняя грань
        Path2D верх = new Path2D.Double();
        верх.moveTo(x, y + d);
        верх.lineTo(x + d, y);
        верх.lineTo(x + s, y);
        верх.lineTo(x + лицо, y + d);
        верх.closePath();
        g.setColor(цвет.brighter());
        g.fill(верх);
        // правая боковая
        Path2D бок = new Path2D.Double();
        бок.moveTo(x + лицо, y + d);
        бок.lineTo(x + s, y);
        бок.lineTo(x + s, y + лицо);
        бок.lineTo(x + лицо, y + s);
        бок.closePath();
        g.setColor(цвет.darker());
        g.fill(бок);
        // лицо
        g.setColor(цвет);
        g.fill(new java.awt.geom.Rectangle2D.Double(x, y + d, лицо, лицо));
        // Кромка: на тёмной теме чёрный кубик без неё пропадает.
        g.setColor(Theme.isDark() ? Theme.alpha(Color.WHITE, 0.55)
            : new Color(0x22, 0x22, 0x22, 160));
        g.setStroke(new BasicStroke(Math.max(1f, (float) (size * 0.05))));
        g.draw(new java.awt.geom.Rectangle2D.Double(x, y + d, лицо, лицо));
    }

    /**
     * Нарисовать значок с центром в {@code (cx, cy)}, вписанный в квадрат
     * {@code size × size}.
     *
     * <p>Коды: {@code COIN, KELIUM, AMMO, TROPHY, TROPHY, CONTAINER, BUILDING, UNIT,
     * CARD, ARSENAL, SUPER, ORDER_DONE, ORDER_LEFT, UP, DOWN, SEAT}.
     */
    public static void paint(Graphics2D g, String code, double cx, double cy, double size,
                             Color colour) {
        double r = size / 2;
        g.setColor(colour);
        switch (code) {
            // МОНЕТА — ЗОЛОТАЯ, с тёмно-золотой обводкой и знаком доллара внутри.
            // Прежний «круг с точкой» читался как что угодно, только не деньги
            // (просьба дизайнера 13.08.2026). Цвет у неё собственный: монета
            // узнаётся именно золотом, а не цветом текста рядом.
            case "COIN" -> {
                java.awt.Color face = new java.awt.Color(0xE8, 0xB3, 0x2A);
                java.awt.Color edge = new java.awt.Color(0x8A, 0x63, 0x08);
                // ОБОДОК — ВТОРОЙ КРУГ, А НЕ ОБВОДКА. Толстая линия по окружности
                // на значке в 14 пикселей ложится неровно, и монета выглядела
                // помятой (замечание дизайнера 13.08.2026). Две заливки дают
                // ровный круг при любом размере.
                double d = r * 1.86;
                g.setColor(edge);
                g.fill(new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d));
                double inner = d * 0.80;
                g.setColor(face);
                g.fill(new Ellipse2D.Double(cx - inner / 2, cy - inner / 2, inner, inner));
                java.awt.Font was = g.getFont();
                g.setFont(was.deriveFont(java.awt.Font.BOLD, (float) (size * 0.62)));
                java.awt.FontMetrics fm = g.getFontMetrics();
                g.setColor(edge);
                g.drawString("$", (float) (cx - fm.stringWidth("$") / 2.0),
                    (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
                g.setFont(was);
                g.setColor(colour);
            }
            // КЕЛЕМИЙ — ЗЕЛЁНЫЙ ОБЪЁМНЫЙ КУБИК. Был кристалл-ромб, и дизайнер
            // велел так не делать (09.09.2026): «на месте кристаллов, патронов,
            // шестерёнок — просто ставь те же объёмные кубики зелёные, красные,
            // чёрные». На столе это и есть кубики, и все они одной формы;
            // различает их цвет, а не силуэт.
            case "KELIUM" -> кубик(g, cx, cy, size, КЕЛЕМИЙ);
            // БОЕПРИПАСЫ — КРАСНЫЙ ОБЪЁМНЫЙ КУБИК (тот же заказ 09.09.2026).
            case "AMMO" -> кубик(g, cx, cy, size, БОЕПРИПАС);
            // УНИЧТОЖЕННЫЙ ЖЕТОН — кубок: чаша и ножка. Не трофей: трофей ниже,
            // это чёрный кубик хранилища.
            case "DESTROYED" -> {
                Path2D cup = new Path2D.Double();
                cup.moveTo(cx - r * 0.72, cy - r * 0.8);
                cup.lineTo(cx + r * 0.72, cy - r * 0.8);
                cup.lineTo(cx + r * 0.3, cy + r * 0.25);
                cup.lineTo(cx - r * 0.3, cy + r * 0.25);
                cup.closePath();
                g.fill(cup);
                g.fill(new RoundRectangle2D.Double(cx - r * 0.14, cy + r * 0.2, r * 0.28,
                    r * 0.5, r * 0.2, r * 0.2));
                g.fill(new RoundRectangle2D.Double(cx - r * 0.6, cy + r * 0.66, r * 1.2,
                    r * 0.3, r * 0.2, r * 0.2));
            }
            // ТРОФЕЙ — ЧЁРНЫЙ ОБЪЁМНЫЙ КУБИК. Была шестерёнка в чёрном
            // квадрате; тот же заказ 09.09.2026 убрал и её. Кубик со стола
            // чёрный в обеих темах, поэтому на тёмном фоне ему добавлена
            // светлая кромка — иначе он сливается с подложкой.
            case "TROPHY" -> кубик(g, cx, cy, size, ТРОФЕЙ);
            // КОНТЕЙНЕР — коробка с крышкой
            case "CONTAINER" -> {
                g.setStroke(new BasicStroke((float) Math.max(1, size * 0.13)));
                g.draw(new RoundRectangle2D.Double(cx - r * 0.86, cy - r * 0.7, r * 1.72,
                    r * 1.5, r * 0.3, r * 0.3));
                g.drawLine((int) (cx - r * 0.86), (int) (cy - r * 0.2),
                    (int) (cx + r * 0.86), (int) (cy - r * 0.2));
            }
            // ЗДАНИЕ — гекс: на поле здания и стоят на гексах
            case "BUILDING" -> {
                Path2D p = new Path2D.Double();
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(60.0 * i - 90 + kelium.report.FieldGeometry.TILT);
                    double px = cx + r * 0.92 * Math.cos(a);
                    double py = cy + r * 0.92 * Math.sin(a);
                    if (i == 0) {
                        p.moveTo(px, py);
                    } else {
                        p.lineTo(px, py);
                    }
                }
                p.closePath();
                g.fill(p);
            }
            // ВОЙСКА — острие: направленный треугольник
            case "UNIT" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, cy - r * 0.95);
                p.lineTo(cx + r * 0.85, cy + r * 0.8);
                p.lineTo(cx, cy + r * 0.35);
                p.lineTo(cx - r * 0.85, cy + r * 0.8);
                p.closePath();
                g.fill(p);
            }
            // КАРТА — прямоугольник со скруглением
            case "CARD" -> {
                g.setStroke(new BasicStroke((float) Math.max(1, size * 0.13)));
                g.draw(new RoundRectangle2D.Double(cx - r * 0.62, cy - r * 0.9, r * 1.24,
                    r * 1.8, r * 0.3, r * 0.3));
            }
            // АРСЕНАЛ — щит
            case "ARSENAL" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, cy - r * 0.95);
                p.curveTo(cx + r * 0.9, cy - r * 0.8, cx + r * 0.85, cy + r * 0.3, cx,
                    cy + r * 0.98);
                p.curveTo(cx - r * 0.85, cy + r * 0.3, cx - r * 0.9, cy - r * 0.8, cx,
                    cy - r * 0.95);
                p.closePath();
                g.fill(p);
            }
            // СУПЕР-ЗАДАНИЕ — звезда
            case "SUPER" -> {
                Path2D p = new Path2D.Double();
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(36.0 * i - 90);
                    double rr = i % 2 == 0 ? r : r * 0.45;
                    double px = cx + rr * Math.cos(a);
                    double py = cy + rr * Math.sin(a);
                    if (i == 0) {
                        p.moveTo(px, py);
                    } else {
                        p.lineTo(px, py);
                    }
                }
                p.closePath();
                g.fill(p);
            }
            // СЫГРАННЫЙ и ОСТАВШИЙСЯ приказ — залитый и пустой круг
            case "ORDER_DONE" -> g.fill(new Ellipse2D.Double(cx - r * 0.7, cy - r * 0.7,
                r * 1.4, r * 1.4));
            case "ORDER_LEFT" -> {
                g.setStroke(new BasicStroke((float) Math.max(1, size * 0.16)));
                g.draw(new Ellipse2D.Double(cx - r * 0.62, cy - r * 0.62, r * 1.24, r * 1.24));
            }
            // РОСТ и ПАДЕНИЕ — треугольники дельты
            case "UP" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, cy - r * 0.8);
                p.lineTo(cx + r * 0.8, cy + r * 0.6);
                p.lineTo(cx - r * 0.8, cy + r * 0.6);
                p.closePath();
                g.fill(p);
            }
            case "DOWN" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, cy + r * 0.8);
                p.lineTo(cx + r * 0.8, cy - r * 0.6);
                p.lineTo(cx - r * 0.8, cy - r * 0.6);
                p.closePath();
                g.fill(p);
            }
            default -> g.fill(new Ellipse2D.Double(cx - r * 0.5, cy - r * 0.5, r, r));
        }
    }

    /**
     * ЖЕТОН МЕСТА: залитый круг цветом места с НОМЕРОМ внутри. Кружочки ①②③④ в
     * системном шрифте тоже не рисуются, а номер места нужен постоянно.
     */
    public static void seat(Graphics2D g, int seat, double cx, double cy, double size) {
        double r = size / 2;
        g.setColor(Theme.seat(seat));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, size, size));
        g.setColor(Color.WHITE);
        Font f = Theme.font(Math.max(8, (int) Math.round(size * 0.62)), Font.BOLD);
        g.setFont(f);
        String s = String.valueOf(seat + 1);
        double w = g.getFontMetrics().stringWidth(s);
        double asc = g.getFontMetrics().getAscent();
        g.drawString(s, (float) (cx - w / 2), (float) (cy + asc / 2 - size * 0.06));
    }
}
