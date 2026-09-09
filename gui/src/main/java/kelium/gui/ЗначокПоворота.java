package kelium.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;

import javax.swing.Icon;

import kelium.gui.replay2.Theme;

/**
 * ЗНАЧОК ПОВОРОТА — круговая стрелка по часовой или против.
 *
 * <p>ПОЧЕМУ НАРИСОВАН, А НЕ ВЗЯТ ЭМОДЗИ. Эмодзи в Swing выводились пустыми
 * квадратами (скриншот дизайнера 12.08.2026), поэтому в проекте их не
 * используют вовсе. Значок рисуется Graphics2D: он ложится в любой размер
 * интерфейса и красится по теме вместе с текстом кнопки.
 *
 * <p>Дуга разомкнута сверху, наконечник стоит на её конце — направление читается
 * с одного взгляда, а не по подписи.
 */
public final class ЗначокПоворота implements Icon {

    private final boolean поЧасовой;
    private final int сторона;

    public ЗначокПоворота(boolean поЧасовой) {
        this(поЧасовой, Theme.px(15));
    }

    public ЗначокПоворота(boolean поЧасовой, int сторона) {
        this.поЧасовой = поЧасовой;
        this.сторона = Math.max(10, сторона);
    }

    @Override
    public int getIconWidth() {
        return сторона;
    }

    @Override
    public int getIconHeight() {
        return сторона;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        // Цвет — от самой кнопки: значок обязан темнеть и светлеть вместе с
        // подписью, иначе в одной из тем он пропадает.
        Color краска = c == null ? Theme.ink() : c.getForeground();
        g.setColor(краска);

        double поле = сторона * 0.12;
        double d = сторона - 2 * поле;
        double cx = x + поле + d / 2;
        double cy = y + поле + d / 2;
        double r = d / 2;
        float толщина = (float) Math.max(1.4, сторона * 0.12);
        g.setStroke(new BasicStroke(толщина, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // ДУГА РАЗОМКНУТА СНИЗУ: там встаёт наконечник, и разрыв показывает,
        // откуда стрелка пошла. Углы Arc2D идут ПРОТИВ часовой от «трёх часов».
        //
        // ПЕРЕВЁРНУТО НА 180° 08.09.2026 (замечание дизайнера: «не нравится, что
        // они как будто бы перевёрнуты»). Разрыв и наконечник были СВЕРХУ, и
        // стрелка читалась как ведущая назад; снизу ход по часовой очевиден.
        double начало = (поЧасовой ? 110 : 70) + 180;
        double размах = поЧасовой ? -300 : 300;
        g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, начало, размах,
            Arc2D.OPEN));

        // Наконечник на конце дуги, повёрнутый по касательной.
        double конецГрад = начало + размах;
        double конецРад = Math.toRadians(конецГрад);
        double hx = cx + r * Math.cos(конецРад);
        double hy = cy - r * Math.sin(конецРад);      // экранная y растёт вниз
        // Касательная в конце дуги: для хода против часовой это (-sin, -cos),
        // для хода по часовой — обратная ей.
        double tx = -Math.sin(конецРад);
        double ty = -Math.cos(конецРад);
        if (поЧасовой) {
            tx = -tx;
            ty = -ty;
        }
        double длина = сторона * 0.30;
        double ширина = сторона * 0.20;
        // Нормаль к касательной — на неё разводятся усы наконечника.
        double nx = -ty;
        double ny = tx;
        Path2D.Double нак = new Path2D.Double();
        нак.moveTo(hx + tx * длина * 0.5, hy + ty * длина * 0.5);
        нак.lineTo(hx - tx * длина * 0.5 + nx * ширина, hy - ty * длина * 0.5 + ny * ширина);
        нак.lineTo(hx - tx * длина * 0.5 - nx * ширина, hy - ty * длина * 0.5 - ny * ширина);
        нак.closePath();
        g.fill(нак);
        g.dispose();
    }
}
