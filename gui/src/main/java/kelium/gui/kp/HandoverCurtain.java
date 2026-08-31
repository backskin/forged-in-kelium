package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * ШТОРКА ПЕРЕДАЧИ УСТРОЙСТВА — для нескольких живых игроков за одним экраном.
 *
 * <p>За столом рука соседа лежит рубашкой к тебе. На одном компьютере её
 * прячет только эта шторка: пока ход не принял тот, кому он адресован, экран
 * закрыт целиком — ни поля, ни руки, ни ленты. Поэтому она НЕПРОЗРАЧНА и лежит
 * выше всего остального, включая модальные окна: полупрозрачная штора,
 * сквозь которую видно чужие карты, не прячет ничего.
 *
 * <p>Шторка ещё и глотает мышь и клавиатуру: пока она поднята, ткнуть во
 * что-нибудь под ней нельзя.
 */
public final class HandoverCurtain extends JComponent {

    private String seatName = "";
    private Color seatColor = Theme.ink();
    private String reason = "";
    private Runnable onReady;
    private boolean hoverBtn;
    private final Anim anim = new Anim();

    public HandoverCurtain() {
        setOpaque(false);
        setVisible(false);
        setFocusable(true);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean h = button().contains(e.getPoint());
                if (h != hoverBtn) {
                    hoverBtn = h;
                    setCursor(h ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (button().contains(e.getPoint())) {
                    ready();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // шторка глотает всё, что под ней
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        // Пробел и Enter — то же, что нажать кнопку: устройство передают из рук
        // в руки, и тыкать мышью в кнопку каждый раз утомительно.
        registerKeyboardAction(e -> ready(),
            javax.swing.KeyStroke.getKeyStroke("SPACE"), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(e -> ready(),
            javax.swing.KeyStroke.getKeyStroke("ENTER"), WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Поднять шторку.
     *
     * @param seatName  чей ход — так, как игрока зовут за этим столом
     * @param seatColor цвет его места
     * @param reason    зачем его зовут («ваш ход», «по вам ударили» и т. п.)
     */
    public void raise(String seatName, Color seatColor, String reason, Runnable onReady) {
        this.seatName = seatName;
        this.seatColor = seatColor;
        this.reason = reason == null ? "" : reason;
        this.onReady = onReady;
        hoverBtn = false;
        setVisible(true);
        requestFocusInWindow();
        anim.snap(0);
        anim.play(1, 160, v -> repaint(), null);
    }

    /** Опустить шторку без ответа (партию закрыли). */
    public void drop() {
        onReady = null;
        setVisible(false);
        repaint();
    }

    /** Принять ход — то же, что нажать кнопку (нужно прогонщикам и тестам). */
    public void ready() {
        if (!isVisible()) {
            return;
        }
        Runnable r = onReady;
        onReady = null;
        setVisible(false);
        repaint();
        if (r != null) {
            r.run();
        }
    }

    public boolean raised() {
        return isVisible();
    }

    private Rectangle button() {
        int w = Theme.px(300);
        int h = Theme.px(64);
        return new Rectangle((getWidth() - w) / 2, getHeight() / 2 + Theme.px(30), w, h);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        // ПЛОТНАЯ ЗАЛИВКА, без прозрачности: сквозь шторку не должно быть видно
        // ничего — ни поля, ни чужой руки.
        g.setColor(Theme.panel());
        g.fillRect(0, 0, w, h);

        // Полоса цвета места сверху и снизу — чтобы издалека было видно, кого зовут.
        g.setColor(seatColor);
        g.fillRect(0, 0, w, Theme.px(6));
        g.fillRect(0, h - Theme.px(6), w, Theme.px(6));

        double a = anim.value();
        int lift = (int) Math.round((1 - a) * Theme.px(18));

        g.setFont(Theme.font(13, Font.BOLD));
        g.setColor(Theme.ink3());
        String top = "ПЕРЕДАЙТЕ УСТРОЙСТВО";
        var f0 = g.getFontMetrics();
        g.drawString(top, (w - f0.stringWidth(top)) / 2, h / 2 - Theme.px(86) + lift);

        g.setFont(Theme.font(38, Font.BOLD));
        g.setColor(seatColor);
        var f1 = g.getFontMetrics();
        g.drawString(seatName, (w - f1.stringWidth(seatName)) / 2, h / 2 - Theme.px(30) + lift);

        if (!reason.isEmpty()) {
            g.setFont(Theme.font(15, Font.PLAIN));
            g.setColor(Theme.ink2());
            var f2 = g.getFontMetrics();
            g.drawString(reason, (w - f2.stringWidth(reason)) / 2, h / 2 + Theme.px(2) + lift);
        }

        g.setFont(Theme.font(11.5, Font.PLAIN));
        g.setColor(Theme.ink3());
        String warn = "Остальные — не подглядывайте: на экране рука игрока";
        var f3 = g.getFontMetrics();
        g.drawString(warn, (w - f3.stringWidth(warn)) / 2, h / 2 + Theme.px(120));

        Rectangle b = button();
        b.y += lift;
        g.setColor(hoverBtn ? Theme.alpha(seatColor, 0.9) : seatColor);
        g.fillRoundRect(b.x, b.y, b.width, b.height, Theme.px(10), Theme.px(10));
        g.setColor(Theme.alpha(Color.BLACK, 0.25));
        g.setStroke(new BasicStroke(Theme.pxf(1.4)));
        g.drawRoundRect(b.x, b.y, b.width, b.height, Theme.px(10), Theme.px(10));

        g.setFont(Theme.font(18, Font.BOLD));
        g.setColor(Color.WHITE);
        var f4 = g.getFontMetrics();
        String label = "Я на месте";
        g.drawString(label, b.x + (b.width - f4.stringWidth(label)) / 2,
            b.y + (b.height + f4.getAscent() - f4.getDescent()) / 2 - Theme.px(6));
        g.setFont(Theme.font(11, Font.PLAIN));
        g.setColor(new Color(255, 255, 255, 200));
        var f5 = g.getFontMetrics();
        String hint = "или пробел";
        g.drawString(hint, b.x + (b.width - f5.stringWidth(hint)) / 2,
            b.y + b.height - Theme.px(9));
        g.dispose();
    }
}
