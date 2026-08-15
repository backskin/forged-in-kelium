package kelium.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/**
 * TransportIcons — РИСОВАННЫЕ ПИКТОГРАММЫ ПУЛЬТА проигрывателя.
 *
 * <p>Почему не символы шрифта. Пульт был подписан знаками {@code ⏮ ◀ ▶ ⏭ « »
 * ⚔◀ ⚔▶}: в системном шрифте Windows скрещённых мечей нет, и Java подставляла
 * похожий глиф — кнопка «к бою» читалась как «X◀». Хуже того, «играть» и «шаг
 * вперёд» оказывались ОДНИМ И ТЕМ ЖЕ треугольником, то есть два самых частых
 * действия были неразличимы. Ровно та же беда уже случалась с палитрой
 * конструктора — см. {@link ToolIcons}.
 *
 * <p>Поэтому: рисуем сами, векторно, любого размера. Форма и цвет несут смысл:
 * <ul>
 *   <li>зелёный треугольник — играть, две полосы — пауза (как в любом плеере);</li>
 *   <li>шаг — треугольник СО СТОЙКОЙ, поэтому его не спутать с «играть»;</li>
 *   <li>синий — прыжки по РАУНДАМ, красный со вспышкой — прыжки по БОЯМ;</li>
 *   <li>к началу и к концу — двойной треугольник со стойкой.</li>
 * </ul>
 */
public final class TransportIcons {

    private TransportIcons() {
    }

    /** Цвет обычной кнопки — тёмно-серый, а не чёрный: мягче на светлой теме. */
    private static final Color INK = new Color(0x2B2B2B);
    private static final Color PLAY = new Color(0x2E7D32);
    private static final Color ROUND = new Color(0x2C62A8);
    private static final Color BATTLE = new Color(0xC0392B);
    private static final Color SPARK = new Color(0xE8A33D);
    private static final Color DICE = new Color(0x6D4C41);

    /**
     * Пиктограмма по коду. Коды: {@code PLAY, PAUSE, REPLAY, STEP_FWD,
     * STEP_BACK, TO_START, TO_END, ROUND_PREV, ROUND_NEXT, BATTLE_PREV,
     * BATTLE_NEXT, DICE, SHUFFLE}.
     *
     * @param size сторона квадратной коробки в пикселях
     */
    public static Icon of(String code, int size) {
        return new Ico(code, size);
    }

    private static final class Ico implements Icon {
        private final String code;
        private final int size;

        Ico(String code, int size) {
            this.code = code;
            this.size = size;
        }

        @Override public int getIconWidth() {
            return size;
        }

        @Override public int getIconHeight() {
            return size;
        }

        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);
            // Рисунок задан в квадрате 100×100 и масштабируется под размер кнопки:
            // одна геометрия на все размеры, включая компактный пульт.
            double k = size / 100.0;
            g.scale(k, k);
            if (c != null && !c.isEnabled()) {
                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.35f));
            }
            draw(g, code);
            g.dispose();
        }
    }

    private static void draw(Graphics2D g, String code) {
        switch (code) {
            case "PLAY" -> triangle(g, 30, 50, 46, PLAY, true);
            case "PAUSE" -> {
                g.setColor(INK);
                g.fill(new RoundRectangle2D.Double(30, 25, 14, 50, 4, 4));
                g.fill(new RoundRectangle2D.Double(56, 25, 14, 50, 4, 4));
            }
            // «Партия кончилась — показать сначала»: круговая стрелка
            case "REPLAY" -> {
                g.setColor(PLAY);
                g.setStroke(new BasicStroke(11f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Arc2D.Double(24, 24, 52, 52, 60, 250,
                    java.awt.geom.Arc2D.OPEN));
                Path2D tip = new Path2D.Double();
                tip.moveTo(72, 14);
                tip.lineTo(86, 40);
                tip.lineTo(56, 40);
                tip.closePath();
                g.fill(tip);
            }
            // ШАГ — треугольник со стойкой: «доехать до следующего события и встать»
            case "STEP_FWD" -> {
                triangle(g, 26, 50, 40, INK, true);
                bar(g, 68);
            }
            case "STEP_BACK" -> {
                triangle(g, 74, 50, 40, INK, false);
                bar(g, 26);
            }
            case "TO_START" -> {
                triangle(g, 84, 50, 34, INK, false);
                triangle(g, 56, 50, 34, INK, false);
                bar(g, 22);
            }
            case "TO_END" -> {
                triangle(g, 16, 50, 34, INK, true);
                triangle(g, 44, 50, 34, INK, true);
                bar(g, 78);
            }
            // РАУНД — стойка (граница раунда) и стрелка к ней
            case "ROUND_PREV" -> {
                bar(g, 22, ROUND);
                chevron(g, 62, 50, 26, ROUND, false);
                chevron(g, 84, 50, 26, ROUND, false);
            }
            case "ROUND_NEXT" -> {
                chevron(g, 16, 50, 26, ROUND, true);
                chevron(g, 38, 50, 26, ROUND, true);
                bar(g, 78, ROUND);
            }
            // БОЙ — вспышка удара и стрелка в нужную сторону
            case "BATTLE_PREV" -> {
                burst(g, 66, 50, 30);
                chevron(g, 26, 50, 26, BATTLE, false);
            }
            case "BATTLE_NEXT" -> {
                burst(g, 34, 50, 30);
                chevron(g, 74, 50, 26, BATTLE, true);
            }
            // КУБИК — «случайный сид». Рисунок нарочно крупный и с большими точками:
            // на кнопке 18 пикселей мелкая крапинка сливается в пятно.
            case "DICE" -> {
                g.setColor(new Color(0xFBF7F0));
                g.fill(new RoundRectangle2D.Double(8, 8, 84, 84, 20, 20));
                g.setColor(DICE);
                g.setStroke(new BasicStroke(9f));
                g.draw(new RoundRectangle2D.Double(8, 8, 84, 84, 20, 20));
                pip(g, 30, 30);
                pip(g, 50, 50);
                pip(g, 70, 70);
            }
            // ПЕРЕТАСОВАТЬ — две стрелки в разные стороны, одна над другой. Скрещённые
            // дуги на маленькой кнопке читались как «X» (то есть «закрыть»).
            case "SHUFFLE" -> {
                g.setColor(INK);
                g.setStroke(new BasicStroke(10f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.drawLine(14, 32, 66, 32);
                g.drawLine(86, 68, 34, 68);
                triangle(g, 88, 32, 34, INK, true);
                triangle(g, 12, 68, 34, INK, false);
            }
            // ТЕМА — круг из двух половин: тёплая солнечная и тёмная ночная с
            // серпом. Читается и на 16 пикселях, и это единственный цветной значок
            // в верхней строке — глаз находит его сразу.
            case "THEME" -> {
                Ellipse2D circle = new Ellipse2D.Double(12, 12, 76, 76);
                java.awt.Shape old = g.getClip();
                g.clip(circle);
                g.setColor(new Color(0xFFC94D));
                g.fillRect(0, 0, 50, 100);
                g.setColor(new Color(0x28304A));
                g.fillRect(50, 0, 50, 100);
                g.setColor(new Color(0xE8ECF5));
                g.fill(new Ellipse2D.Double(58, 30, 30, 30));
                g.setColor(new Color(0x28304A));
                g.fill(new Ellipse2D.Double(52, 26, 30, 30));
                g.setColor(new Color(0xFFF3C4));
                g.fill(new Ellipse2D.Double(20, 34, 22, 22));
                g.setClip(old);
                g.setColor(new Color(0x1B1F26));
                g.setStroke(new BasicStroke(6f));
                g.draw(circle);
            }
            // ЛУПА — крупнее и мельче текст. Ручка идёт вправо-вниз, как на всех
            // лупах, а знак внутри стекла толстый: на кнопке 16 пикселей тонкий
            // штрих пропадает совсем.
            case "ZOOM_IN" -> lens(g, true);
            case "ZOOM_OUT" -> lens(g, false);
            // МЕСТА ЗА СТОЛОМ — четыре цветных кружка по сторонам светлого стола
            // (просьба дизайнера 14.08.2026: кнопка настройки игроков делила
            // значок со «сборкой из блоков» — рисунок никак не намекал на людей
            // за столом). Цвета — те же четыре, что у мест на поле.
            case "SEATS" -> {
                g.setColor(new Color(0xE7E2D6));
                g.fill(new Ellipse2D.Double(26, 26, 48, 48));
                g.setColor(new Color(0xBDB6A4));
                g.setStroke(new BasicStroke(3f));
                g.draw(new Ellipse2D.Double(26, 26, 48, 48));
                Color[] seat = {new Color(0x3b82d0), new Color(0xe07038),
                    new Color(0x3f9e60), new Color(0xb04a96)};
                double[][] at = {{50, 12}, {88, 50}, {50, 88}, {12, 50}};
                for (int i = 0; i < 4; i++) {
                    g.setColor(seat[i]);
                    g.fill(new Ellipse2D.Double(at[i][0] - 13, at[i][1] - 13, 26, 26));
                    g.setColor(new Color(0xFFFFFF));
                    g.setStroke(new BasicStroke(3f));
                    g.draw(new Ellipse2D.Double(at[i][0] - 13, at[i][1] - 13, 26, 26));
                }
            }
            default -> {
                g.setColor(INK);
                g.fill(new Ellipse2D.Double(42, 42, 16, 16));
            }
        }
    }

    /** Лупа со знаком внутри: {@code plus} — крупнее, иначе мельче. */
    private static void lens(Graphics2D g, boolean plus) {
        g.setColor(INK);
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(66, 66, 90, 90);
        g.setStroke(new BasicStroke(9f));
        g.draw(new Ellipse2D.Double(12, 12, 58, 58));
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(26, 41, 56, 41);
        if (plus) {
            g.drawLine(41, 26, 41, 56);
        }
    }

    /** Треугольник-указатель: {@code cx} — вершина, {@code side} — высота коробки. */
    private static void triangle(Graphics2D g, double cx, double cy, double side,
                                 Color colour, boolean right) {
        double h = side;
        double w = side * 0.78;
        Path2D p = new Path2D.Double();
        if (right) {
            p.moveTo(cx + w / 2, cy);
            p.lineTo(cx - w / 2, cy - h / 2);
            p.lineTo(cx - w / 2, cy + h / 2);
        } else {
            p.moveTo(cx - w / 2, cy);
            p.lineTo(cx + w / 2, cy - h / 2);
            p.lineTo(cx + w / 2, cy + h / 2);
        }
        p.closePath();
        g.setColor(colour);
        g.fill(p);
    }

    /** Стойка — вертикальная планка, в которую «упирается» шаг. */
    private static void bar(Graphics2D g, double x) {
        bar(g, x, INK);
    }

    private static void bar(Graphics2D g, double x, Color colour) {
        g.setColor(colour);
        g.fill(new RoundRectangle2D.Double(x - 5, 24, 10, 52, 4, 4));
    }

    /** Уголок-стрелка (галочка набок) — мягче сплошного треугольника. */
    private static void chevron(Graphics2D g, double cx, double cy, double side,
                                Color colour, boolean right) {
        g.setColor(colour);
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double w = side * 0.6;
        double h = side * 0.8;
        Path2D p = new Path2D.Double();
        if (right) {
            p.moveTo(cx - w / 2, cy - h);
            p.lineTo(cx + w / 2, cy);
            p.lineTo(cx - w / 2, cy + h);
        } else {
            p.moveTo(cx + w / 2, cy - h);
            p.lineTo(cx - w / 2, cy);
            p.lineTo(cx + w / 2, cy + h);
        }
        g.draw(p);
    }

    /** Вспышка удара — та же звёздочка, что рисуется на поле в бою. */
    private static void burst(Graphics2D g, double cx, double cy, double r) {
        Path2D star = new Path2D.Double();
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(30.0 * i);
            double rr = i % 2 == 0 ? r : r * 0.44;
            double px = cx + rr * Math.cos(a);
            double py = cy + rr * Math.sin(a);
            if (i == 0) {
                star.moveTo(px, py);
            } else {
                star.lineTo(px, py);
            }
        }
        star.closePath();
        g.setColor(SPARK);
        g.fill(star);
        g.setColor(BATTLE);
        g.setStroke(new BasicStroke(5f));
        g.draw(star);
    }

    private static void pip(Graphics2D g, double cx, double cy) {
        g.setColor(DICE);
        g.fill(new Ellipse2D.Double(cx - 9, cy - 9, 18, 18));
    }
}
