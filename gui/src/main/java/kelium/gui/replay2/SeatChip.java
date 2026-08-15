package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;

/**
 * SeatChip — ПЛАШКА ЦВЕТА МЕСТА с текстом в обводке.
 *
 * <p>Зачем. Цвет места сначала показывали просто цветным шрифтом — и он сливался:
 * синий текст на тёмном фоне тонет, жёлто-зелёный на светлом выцветает, а сравнить
 * его с цветом жетона на поле почти невозможно (замечание дизайнера 13.08.2026:
 * «цветной шрифт сливается с чёрным и белым фонами»). Плашка решает сразу два дела:
 * цвет виден пятном той же краски, что на поле, а текст читается всегда, потому что
 * он белый и с тёмной обводкой.
 *
 * <p>Обводка рисуется по контуру глифов, а не тенью: тень на цветной подложке
 * превращается в грязь, а контур держит букву на любом цвете.
 */
public final class SeatChip extends JComponent {

    private static final long serialVersionUID = 1L;

    private final int seat;
    private String text;
    private int fontSize = 12;
    private boolean strong = true;

    public SeatChip(int seat, String text) {
        this.seat = seat;
        this.text = text;
        setOpaque(false);
        setFont(Theme.font(fontSize, Font.BOLD));
    }

    public void setText(String text) {
        this.text = text;
        revalidate();
        repaint();
    }

    /** Размер подписи внутри плашки. */
    public void setFontSize(int size) {
        this.fontSize = size;
        setFont(Theme.font(size, Font.BOLD));
        revalidate();
        repaint();
    }

    /** Приглушённая плашка: для неактивных мест (при 2–3 игроках). */
    public void setStrong(boolean strong) {
        this.strong = strong;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text) + Theme.px(14),
            fm.getHeight() + Theme.px(6));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintChip(g, seat, text, getFont(), 0, 0, getWidth(), getHeight(), strong);
        g.dispose();
    }

    /**
     * Нарисовать плашку прямо на полотне — этим пользуются полосы игроков и другие
     * панели, которые рисуют себя сами.
     *
     * @return ширина нарисованной плашки
     */
    public static int paintChip(Graphics2D g, int seat, String text, Font font,
                                int x, int y, int w, int h, boolean strong) {
        Color base = Theme.seat(seat);
        g.setColor(strong ? base : Theme.alpha(base, 0.45));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        // тонкая тёмная кромка: плашка не растворяется на светлой теме
        g.setColor(Theme.alpha(Theme.darken(base, 0.35), strong ? 0.9 : 0.4));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, w - 1, h - 1, h, h));

        g.setFont(font);
        java.awt.FontMetrics fm = g.getFontMetrics();
        float tx = x + (w - fm.stringWidth(text)) / 2f;
        float ty = y + (h + fm.getAscent() - fm.getDescent()) / 2f;
        outlined(g, text, font, tx, ty);
        return w;
    }

    /**
     * Белая надпись на цветной плашке. ЧЁРНОЙ ОБВОДКИ БОЛЬШЕ НЕТ — она резала глаз
     * (замечание дизайнера 13.08.2026). Плашка и так даёт нужный контраст: белые
     * буквы на насыщенном цвете читаются и на светлой, и на тёмной теме.
     */
    static void outlined(Graphics2D g, String text, Font font, float x, float y) {
        java.awt.font.GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), text);
        Shape outline = gv.getOutline(x, y);
        g.setColor(Color.WHITE);
        g.fill(new Area(outline));
    }

    /** Ширина плашки под такой текст — чтобы разместить её в рисуемой панели. */
    public static int widthFor(Graphics2D g, String text, Font font) {
        return g.getFontMetrics(font).stringWidth(text) + Theme.px(14);
    }
}
