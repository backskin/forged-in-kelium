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

    /**
     * Нарисовать значок с центром в {@code (cx, cy)}, вписанный в квадрат
     * {@code size × size}.
     *
     * <p>Коды: {@code COIN, KELIUM, AMMO, TROPHY, DEBRIS, CONTAINER, BUILDING, UNIT,
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
            // КЕЛЕМИЙ — кристалл: ромб с гранью, как на тайлах зарождения
            case "KELIUM" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, cy - r);
                p.lineTo(cx + r * 0.78, cy);
                p.lineTo(cx, cy + r);
                p.lineTo(cx - r * 0.78, cy);
                p.closePath();
                g.fill(p);
                g.setColor(Theme.alpha(Color.WHITE, 0.35));
                Path2D face = new Path2D.Double();
                face.moveTo(cx, cy - r);
                face.lineTo(cx + r * 0.78, cy);
                face.lineTo(cx, cy);
                face.closePath();
                g.fill(face);
            }
            // БОЕПРИПАСЫ — ЧЁРНЫЙ ПАТРОН НА КРАСНОМ КВАДРАТЕ (просьба дизайнера
            // 13.08.2026). Прежняя серая «гильза» без подложки читалась как
            // случайная палочка; красный квадрат делает значок узнаваемым сразу.
            case "AMMO" -> {
                g.setColor(new java.awt.Color(0xD1, 0x2B, 0x2B));
                g.fill(new RoundRectangle2D.Double(cx - r * 0.95, cy - r * 0.95,
                    r * 1.9, r * 1.9, r * 0.45, r * 0.45));
                g.setColor(new java.awt.Color(0x8A, 0x14, 0x14));
                g.setStroke(new BasicStroke((float) Math.max(1, size * 0.07)));
                g.draw(new RoundRectangle2D.Double(cx - r * 0.95, cy - r * 0.95,
                    r * 1.9, r * 1.9, r * 0.45, r * 0.45));
                // сам патрон: остроконечная пуля и корпус гильзы
                g.setColor(new java.awt.Color(0x14, 0x14, 0x14));
                double bw = r * 0.52;
                double tip = r * 0.58;
                Path2D bullet = new Path2D.Double();
                bullet.moveTo(cx, cy - r * 0.72);
                bullet.lineTo(cx + bw / 2, cy - r * 0.72 + tip);
                bullet.lineTo(cx + bw / 2, cy + r * 0.72);
                bullet.lineTo(cx - bw / 2, cy + r * 0.72);
                bullet.lineTo(cx - bw / 2, cy - r * 0.72 + tip);
                bullet.closePath();
                g.fill(bullet);
                // ПОЯСКА ГИЛЬЗЫ НЕТ намеренно: на 14 пикселях красная полоска
                // разрезала патрон надвое, и значок читался как восклицательный знак.
                g.setColor(colour);
            }
            // ТРОФЕЙ — ЧЁРНЫЙ КВАДРАТ С ШЕСТЕРЁНКОЙ В ЦЕНТРЕ (эталон дизайнера).
            // Раньше это были два разных значка на два разных термина — кубок
            // «TROPHY» для трофейного жетона и шестерёнка «DEBRIS» для валюты,
            // в которую он превращается. Термины слиты в один («трофей»,
            // правка 30.08.2026), кубок нигде не вызывался — убран как мёртвый
            // код, а не как рабочая альтернатива.
            // Цвета у него собственные, как у монеты: трофей узнаётся именно
            // чёрным кубиком, а не цветом текста рядом. Корпус чёрный в обеих
            // темах — это и есть чёрный кубик со стола. А вот НУТРО И ОБВОДКА
            // зависят от темы: на тёмном фоне серая шестерёнка сливается с
            // подложкой, поэтому там она белая и обводка белая.
            // Зубцы — восемь трапеций по окружности; на 12 пикселях их ещё видно,
            // ниже значок читается как круг в квадрате, и это допустимо.
            case "DEBRIS" -> {
                Color body = new Color(0x14, 0x14, 0x14);
                Color gear = Theme.isDark() ? Color.WHITE : new Color(0x9A, 0x9A, 0x9A);
                g.setColor(body);
                g.fill(new RoundRectangle2D.Double(cx - r * 0.92, cy - r * 0.92, r * 1.84,
                    r * 1.84, r * 0.28, r * 0.28));
                g.setColor(Theme.alpha(Color.WHITE, Theme.isDark() ? 0.85 : 0.22));
                g.setStroke(new BasicStroke(Theme.isDark() ? 1.4f : 1f));
                g.draw(new RoundRectangle2D.Double(cx - r * 0.92, cy - r * 0.92, r * 1.84,
                    r * 1.84, r * 0.28, r * 0.28));
                g.setColor(gear);
                double rim = r * 0.44;
                double tooth = r * 0.60;
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(45.0 * i);
                    double half = Math.toRadians(11.0);
                    Path2D t = new Path2D.Double();
                    t.moveTo(cx + rim * Math.cos(a - half), cy + rim * Math.sin(a - half));
                    t.lineTo(cx + tooth * Math.cos(a - half * 0.6),
                        cy + tooth * Math.sin(a - half * 0.6));
                    t.lineTo(cx + tooth * Math.cos(a + half * 0.6),
                        cy + tooth * Math.sin(a + half * 0.6));
                    t.lineTo(cx + rim * Math.cos(a + half), cy + rim * Math.sin(a + half));
                    t.closePath();
                    g.fill(t);
                }
                g.fill(new Ellipse2D.Double(cx - rim, cy - rim, rim * 2, rim * 2));
                // ступица — дырка цветом корпуса, чтобы шестерёнка не слиплась в диск
                g.setColor(body);
                g.fill(new Ellipse2D.Double(cx - r * 0.17, cy - r * 0.17, r * 0.34, r * 0.34));
                g.setColor(colour);
            }
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
