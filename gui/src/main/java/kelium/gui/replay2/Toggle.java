package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * ТУМБЛЕР ВКЛ/ВЫКЛ — переключатель в духе телефонного (просьба дизайнера
 * 17.08.2026: «два таких красивых тумблера как с айфона»).
 *
 * <p>Зачем не {@code JCheckBox}. Галочка читается как «одна из настроек в
 * списке»; тумблер читается как «модуль игры включён или выключен целиком».
 * Дополнения — именно второе: партия с супер заданиями и без них это две разные
 * игры, а не одна с галочкой.
 *
 * <p>Ползунок ездит АНИМИРОВАННО, за пару кадров: мгновенный скачок на такой
 * мелкой детали читается как мигание, и непонятно, куда именно щёлкнул.
 *
 * <p>Компонент сам за собой следит по теме: цвета берутся из {@link Theme} в
 * момент отрисовки, поэтому смена темы на лету его не ломает.
 */
public final class Toggle extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Ширина и высота дорожки в логических точках (масштабируются темой). */
    private static final int TRACK_W = 38;
    private static final int TRACK_H = 22;

    private final String label;
    private boolean on;
    private Consumer<Boolean> onChange = v -> { };

    /** Положение ползунка 0..1 — не булево, потому что он ездит плавно. */
    private double slide;
    private Timer animation;

    public Toggle(String label, boolean on, String tip) {
        this.label = label;
        this.on = on;
        this.slide = on ? 1.0 : 0.0;
        setOpaque(false);
        setFont(Theme.body());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(Ui2.tip(tip));

        animation = new Timer(16, e -> {
            double target = this.on ? 1.0 : 0.0;
            double step = 0.22;
            if (Math.abs(slide - target) <= step) {
                slide = target;
                animation.stop();
            } else {
                slide += slide < target ? step : -step;
            }
            repaint();
        });

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    setSelected(!Toggle.this.on);
                    onChange.accept(Toggle.this.on);
                }
            }
        });
    }

    /** Что делать при переключении. Обработчик НЕ зовётся из {@link #setSelected}. */
    public Toggle onChange(Consumer<Boolean> handler) {
        this.onChange = handler == null ? v -> { } : handler;
        return this;
    }

    public boolean isSelected() {
        return on;
    }

    /** Переключить программно — обработчик при этом не срабатывает. */
    public void setSelected(boolean value) {
        if (on == value) {
            return;
        }
        on = value;
        animation.start();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int w = Theme.px(TRACK_W) + Theme.px(8);
        var fm = getFontMetrics(getFont());
        return new Dimension(w + fm.stringWidth(label) + Theme.px(4),
            Math.max(Theme.px(TRACK_H), fm.getHeight()) + Theme.px(4));
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int tw = Theme.px(TRACK_W);
        int th = Theme.px(TRACK_H);
        int ty = (getHeight() - th) / 2;

        // ДОРОЖКА. Включённый тумблер красится акцентом темы, выключенный —
        // цветом рамки: разница читается и на глаз, и в чёрно-белой печати
        // снимка, где акцент всё равно темнее.
        Color trackOn = Theme.accent();
        Color trackOff = Theme.border();
        g.setColor(mix(trackOff, trackOn, slide));
        g.fillRoundRect(0, ty, tw, th, th, th);
        if (!isEnabled()) {
            // Недоступный тумблер приглушён целиком, а не только текстом: иначе
            // он выглядит рабочим и по нему щёлкают.
            g.setColor(new Color(Theme.panel().getRed(), Theme.panel().getGreen(),
                Theme.panel().getBlue(), 150));
            g.fillRoundRect(0, ty, tw, th, th, th);
        }

        // ПОЛЗУНОК
        int pad = Theme.px(2);
        int d = th - pad * 2;
        int x = (int) Math.round(pad + slide * (tw - pad * 2 - d));
        g.setColor(isEnabled() ? Color.WHITE : Theme.panel());
        g.fillOval(x, ty + pad, d, d);
        g.setColor(new Color(0, 0, 0, 40));
        g.setStroke(new BasicStroke(1f));
        g.drawOval(x, ty + pad, d, d);

        // ПОДПИСЬ
        g.setFont(getFont());
        g.setColor(isEnabled() ? Theme.ink() : Theme.ink3());
        var fm = g.getFontMetrics();
        g.drawString(label, tw + Theme.px(8),
            (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }

    /** Линейная смесь двух цветов — дорожка перекрашивается вместе с ездой. */
    private static Color mix(Color a, Color b, double t) {
        double k = Math.max(0, Math.min(1, t));
        return new Color(
            (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
            (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }

    @Override
    public void setFont(Font f) {
        super.setFont(f);
        revalidate();
    }
}
