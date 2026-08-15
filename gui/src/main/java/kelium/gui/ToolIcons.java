package kelium.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

import javax.swing.Icon;

/**
 * ToolIcons — РИСОВАННЫЕ ЦВЕТНЫЕ ПИКТОГРАММЫ инструментов конструктора.
 *
 * <p>Почему не эмодзи. Кнопки палитры были подписаны символами вроде 🟢/🚩/⛔, и
 * в системном шрифте Swing они не отрисовались: у дизайнера в интерфейсе вместо
 * иконок стояли пустые квадраты (скриншот 12.08.2026). Цветные эмодзи вообще не
 * задача Swing — он берёт глиф из шрифта, а цветные шрифты (COLR/CBDT) Java2D не
 * поддерживает. Поэтому иконки рисуются векторно: они одинаковы на любой машине,
 * цветные, и говорят на языке самой игры — гексы, флажки, стенки.
 *
 * <p>Иконка выравнена по сетке: одна и та же коробка {@link #SIZE}×{@link #SIZE}
 * для всех инструментов, рисунок внутри центрирован. Поэтому текст кнопок стоит
 * ровным столбцом независимо от того, какая пиктограмма слева.
 */
public final class ToolIcons {

    private ToolIcons() {
    }

    /** Сторона квадратной коробки иконки в пикселях. */
    public static final int SIZE = 20;

    // ЦВЕТА — те же, что на полотне конструктора, чтобы кнопка и поле совпадали.
    private static final Color HEX_FILL = new Color(0xEFEDE4);
    private static final Color HEX_EDGE = new Color(0x8A8778);
    private static final Color SPAWN_SMALL = new Color(0xA5D6A7);
    private static final Color SPAWN_BIG = new Color(0x4C9A52);
    private static final Color SPAWN_EDGE = new Color(0x2E5D33);
    private static final Color FORBIDDEN = new Color(0x3A3A3A);
    private static final Color NEUTRAL = new Color(0xB9A277);
    private static final Color NEUTRAL_EDGE = new Color(0x6B5A33);
    private static final Color SEAT = new Color(0xC0392B);
    private static final Color KELIUM = new Color(0x2FA8A0);
    private static final Color CONTAINER = new Color(0xE8C77B);
    private static final Color CONTAINER_EDGE = new Color(0x6E4E13);
    private static final Color INK = new Color(0x333333);

    /** Пиктограмма по коду инструмента. */
    public static Icon of(String code) {
        return new ToolIcon(code);
    }

    private static final class ToolIcon implements Icon {
        private final String code;

        ToolIcon(String code) {
            this.code = code;
        }

        @Override public int getIconWidth() {
            return SIZE;
        }

        @Override public int getIconHeight() {
            return SIZE;
        }

        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);
            draw(g, code);
            g.dispose();
        }
    }

    private static void draw(Graphics2D g, String code) {
        double cx = SIZE / 2.0;
        double cy = SIZE / 2.0;
        double r = SIZE * 0.44;
        switch (code) {
            case "ADD" -> {
                hex(g, cx, cy, r, HEX_FILL, HEX_EDGE, 1.4f);
                plus(g, cx, cy, r * 0.55, new Color(0x2E7D32));
            }
            case "CLEAR_HEX" -> {
                hex(g, cx, cy, r, HEX_FILL, HEX_EDGE, 1.4f);
                g.setColor(new Color(0x9E9E9E));
                g.setStroke(new BasicStroke(1.8f));
                g.drawLine((int) (cx - r * 0.45), (int) (cy - r * 0.45),
                    (int) (cx + r * 0.45), (int) (cy + r * 0.45));
                g.drawLine((int) (cx - r * 0.45), (int) (cy + r * 0.45),
                    (int) (cx + r * 0.45), (int) (cy - r * 0.45));
            }
            case "PLAYER" -> {
                // ФЛАЖОК старта: древко + полотнище цветом первого места
                hex(g, cx, cy, r, HEX_FILL, HEX_EDGE, 1.0f);
                g.setColor(INK);
                g.setStroke(new BasicStroke(1.6f));
                g.drawLine((int) (cx - r * 0.25), (int) (cy - r * 0.6),
                    (int) (cx - r * 0.25), (int) (cy + r * 0.62));
                Path2D flag = new Path2D.Double();
                flag.moveTo(cx - r * 0.25, cy - r * 0.6);
                flag.lineTo(cx + r * 0.62, cy - r * 0.28);
                flag.lineTo(cx - r * 0.25, cy + r * 0.04);
                flag.closePath();
                g.setColor(SEAT);
                g.fill(flag);
            }
            // ЗАРОЖДЕНИЯ: круг — малое, шестиугольник — большое. Разные и по
            // форме, и по насыщенности, поэтому не путаются ни между собой, ни с
            // флажком старта.
            case "SPAWN_START" -> {
                hex(g, cx, cy, r, HEX_FILL, HEX_EDGE, 1.0f);
                g.setColor(SPAWN_SMALL);
                g.fillOval((int) (cx - r * 0.55), (int) (cy - r * 0.55),
                    (int) (r * 1.1), (int) (r * 1.1));
                g.setColor(SPAWN_EDGE);
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval((int) (cx - r * 0.55), (int) (cy - r * 0.55),
                    (int) (r * 1.1), (int) (r * 1.1));
            }
            case "KELIUM" -> hex(g, cx, cy, r, SPAWN_BIG, SPAWN_EDGE, 1.6f);
            case "STACK" -> {
                // две плашки друг за другом — стопка ×2
                hex(g, cx - r * 0.22, cy - r * 0.18, r * 0.8, SPAWN_SMALL, SPAWN_EDGE, 1.2f);
                hex(g, cx + r * 0.28, cy + r * 0.22, r * 0.8, SPAWN_BIG, SPAWN_EDGE, 1.2f);
            }
            case "KELIUM_DELTA" -> {
                // кристалл келемия и знак ±
                Path2D d = new Path2D.Double();
                d.moveTo(cx - r * 0.15, cy - r * 0.75);
                d.lineTo(cx + r * 0.55, cy);
                d.lineTo(cx - r * 0.15, cy + r * 0.75);
                d.lineTo(cx - r * 0.75, cy);
                d.closePath();
                g.setColor(KELIUM);
                g.fill(d);
                g.setColor(new Color(0x145E5A));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(d);
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (SIZE * 0.5)));
                g.drawString("±", (int) (cx - r * 0.34), (int) (cy + r * 0.38));
            }
            case "CONTAINER" -> {
                // ящик с защёлкой на гексе — так же он выглядит и на поле
                hex(g, cx, cy, r, HEX_FILL, HEX_EDGE, 1.0f);
                int bw = (int) Math.round(r * 1.15);
                int bh = (int) Math.round(r * 0.85);
                int bx = (int) Math.round(cx - bw / 2.0);
                int by = (int) Math.round(cy - bh / 2.0);
                g.setColor(CONTAINER);
                g.fillRoundRect(bx, by, bw, bh, 4, 4);
                g.setColor(CONTAINER_EDGE);
                g.setStroke(new BasicStroke(1.3f));
                g.drawRoundRect(bx, by, bw, bh, 4, 4);
                g.drawLine(bx, (int) Math.round(cy), bx + bw, (int) Math.round(cy));
            }
            case "SETTINGS" -> {
                // три ползунка — узнаваемый значок «настройки», без текста
                g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double[] ys = {cy - r * 0.55, cy, cy + r * 0.55};
                double[] knob = {-0.25, 0.35, -0.1};
                for (int i = 0; i < 3; i++) {
                    g.setColor(new Color(0x9E9E9E));
                    g.drawLine((int) (cx - r * 0.9), (int) ys[i],
                        (int) (cx + r * 0.9), (int) ys[i]);
                    g.setColor(INK);
                    g.fillOval((int) (cx + r * knob[i] - r * 0.16), (int) (ys[i] - r * 0.16),
                        (int) (r * 0.32), (int) (r * 0.32));
                }
            }
            case "FORBIDDEN" -> {
                hex(g, cx, cy, r, FORBIDDEN, new Color(0x111111), 1.2f);
                g.setColor(new Color(0xE57373));
                g.setStroke(new BasicStroke(2.0f));
                g.drawLine((int) (cx - r * 0.42), (int) (cy - r * 0.42),
                    (int) (cx + r * 0.42), (int) (cy + r * 0.42));
                g.drawLine((int) (cx - r * 0.42), (int) (cy + r * 0.42),
                    (int) (cx + r * 0.42), (int) (cy - r * 0.42));
            }
            // НЕЙТРАЛЫ: стенка(и) на рёбрах гекса — ровно так они и лежат на поле
            // гекс поджат: стенка рисуется толстой линией ПО его ребру и иначе
            // вылезала бы за коробку иконки
            // МАЛЫЙ нейтрал — одна стенка, БОЛЬШОЙ — две смежные плюс заливка
            // угла между ними: на кнопке 20×20 разница «одна линия / две линии»
            // читалась плохо, поэтому у большого закрашен весь клин.
            case "NEUTRAL_SMALL" -> {
                hex(g, cx, cy, r * 0.84, HEX_FILL, HEX_EDGE, 1.0f);
                wall(g, cx, cy, r * 0.84, 0);
            }
            case "NEUTRAL_BIG" -> {
                double rr = r * 0.84;
                hex(g, cx, cy, rr, HEX_FILL, HEX_EDGE, 1.0f);
                Path2D wedge = new Path2D.Double();
                wedge.moveTo(cx, cy);
                for (int k = 0; k <= 2; k++) {
                    double a = Math.toRadians(60.0 * k - 90 + kelium.report.FieldGeometry.TILT);
                    wedge.lineTo(cx + rr * ICON_R * Math.cos(a),
                        cy + rr * ICON_R * Math.sin(a));
                }
                wedge.closePath();
                g.setColor(NEUTRAL);
                g.fill(wedge);
                g.setColor(NEUTRAL_EDGE);
                g.setStroke(new BasicStroke(1.0f));
                g.draw(wedge);
                wall(g, cx, cy, rr, 0);
                wall(g, cx, cy, rr, 1);
            }
            case "BLOCKS" -> {
                // сборка из блоков: три сцепленных гекса (радиусы подобраны так,
                // чтобы тройка целиком укладывалась в коробку иконки)
                hex(g, cx - r * 0.42, cy - r * 0.30, r * 0.5, SPAWN_SMALL, SPAWN_EDGE, 1.0f);
                hex(g, cx + r * 0.42, cy - r * 0.30, r * 0.5, HEX_FILL, HEX_EDGE, 1.0f);
                hex(g, cx, cy + r * 0.44, r * 0.5, NEUTRAL, NEUTRAL_EDGE, 1.0f);
            }
            case "PNG" -> {
                // картинка: рамка с «горами»
                g.setColor(new Color(0xF5F5F5));
                g.fillRect((int) (cx - r * 0.85), (int) (cy - r * 0.7),
                    (int) (r * 1.7), (int) (r * 1.4));
                g.setColor(INK);
                g.setStroke(new BasicStroke(1.3f));
                g.drawRect((int) (cx - r * 0.85), (int) (cy - r * 0.7),
                    (int) (r * 1.7), (int) (r * 1.4));
                Path2D hill = new Path2D.Double();
                hill.moveTo(cx - r * 0.75, cy + r * 0.6);
                hill.lineTo(cx - r * 0.15, cy - r * 0.2);
                hill.lineTo(cx + r * 0.35, cy + r * 0.6);
                hill.closePath();
                g.setColor(new Color(0x66BB6A));
                g.fill(hill);
                g.setColor(new Color(0xFDD835));
                g.fillOval((int) (cx + r * 0.3), (int) (cy - r * 0.5),
                    (int) (r * 0.35), (int) (r * 0.35));
            }
            default -> hex(g, cx, cy, r, HEX_FILL, HEX_EDGE, 1.2f);
        }
    }

    /**
     * ПОПРАВКА НА РАЗВОРОТ ПОЛЯ. Гекс «плашмя вверх» шире, чем «остриём вверх»
     * (2r против √3·r), и старые иконки при том же радиусе стали вылезать из своей
     * коробки по горизонтали. Радиус в иконках уменьшаем на √3/2 — ширина остаётся
     * прежней.
     */
    private static final double ICON_R = Math.sqrt(3) / 2;

    /** Шестиугольник «плашмя вверх» — как гексы поля. */
    private static void hex(Graphics2D g, double cx, double cy, double r0,
                            Color fill, Color edge, float stroke) {
        double r = r0 * ICON_R;
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60.0 * i - 90 + kelium.report.FieldGeometry.TILT);
            double px = cx + r * Math.cos(a);
            double py = cy + r * Math.sin(a);
            if (i == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        p.closePath();
        g.setColor(fill);
        g.fill(p);
        g.setColor(edge);
        g.setStroke(new BasicStroke(stroke));
        g.draw(p);
    }

    /** Стенка нейтрала на ребре {@code side} гекса. */
    private static void wall(Graphics2D g, double cx, double cy, double r0, int side) {
        double r = r0 * ICON_R;
        double a1 = Math.toRadians(60.0 * side - 90 + kelium.report.FieldGeometry.TILT);
        double a2 = Math.toRadians(60.0 * (side + 1) - 90 + kelium.report.FieldGeometry.TILT);
        g.setColor(NEUTRAL);
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) Math.round(cx + r * Math.cos(a1)),
            (int) Math.round(cy + r * Math.sin(a1)),
            (int) Math.round(cx + r * Math.cos(a2)),
            (int) Math.round(cy + r * Math.sin(a2)));
        g.setColor(NEUTRAL_EDGE);
        g.setStroke(new BasicStroke(1.0f));
        g.drawLine((int) Math.round(cx + r * Math.cos(a1)),
            (int) Math.round(cy + r * Math.sin(a1)),
            (int) Math.round(cx + r * Math.cos(a2)),
            (int) Math.round(cy + r * Math.sin(a2)));
    }

    private static void plus(Graphics2D g, double cx, double cy, double half, Color colour) {
        g.setColor(colour);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) (cx - half), (int) cy, (int) (cx + half), (int) cy);
        g.drawLine((int) cx, (int) (cy - half), (int) cx, (int) (cy + half));
    }
}
