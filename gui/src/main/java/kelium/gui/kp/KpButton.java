package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

import kelium.gui.replay2.MarkIcons;
import kelium.gui.replay2.Theme;

/**
 * ПЛИТКА-КНОПКА «Командного пункта» — рисованная, а не стандартная Swing.
 *
 * <p>Замечание дизайнера (24.08.2026): стоковые JButton «появляются и теряются,
 * как будто мы в 2001-м». Правильные элементы игры ПОСТОЯННЫ и рисованы в её
 * стиле — как весь replay2. Одна плитка обслуживает три роли: плитка действия
 * (панель действий, всегда на экране), широкая плашка варианта (панель
 * решений), большая главная кнопка («Завершить ход», {@link #primary}).
 *
 * <p>Состояния — по концепту §2: доступное — чернила и рука-курсор; сыгранное —
 * галка и тихие чернила; недоступное — пунктирная рамка, место СОХРАНЯЕТСЯ
 * (скилл: «недоступное не схлопывается»), причина — в подсказке; активное —
 * рамка акцентом.
 */
public final class KpButton extends JComponent {

    public enum State { AVAILABLE, ACTIVE, PLAYED, DISABLED }

    private String title;
    private String sub;
    private String icon;
    private State state = State.AVAILABLE;
    private boolean primary;
    private Runnable onClick;
    private boolean hover;
    private boolean pressed;

    public KpButton(String title, String sub, String icon) {
        this.title = title;
        this.sub = sub;
        this.icon = icon;
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (clickable()) {
                    pressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean fire = pressed && clickable() && contains(e.getPoint());
                pressed = false;
                repaint();
                if (fire && onClick != null) {
                    onClick.run();
                }
            }
        };
        addMouseListener(m);
    }

    public boolean clickable() {
        return state == State.AVAILABLE || state == State.ACTIVE;
    }

    /** Для прогонщиков и тестов — то же, что клик мышью. */
    public void click() {
        if (clickable() && onClick != null) {
            onClick.run();
        }
    }

    public KpButton primary(boolean on) {
        primary = on;
        repaint();
        return this;
    }

    public KpButton onClick(Runnable r) {
        onClick = r;
        return this;
    }

    public void setState(State s) {
        state = s;
        setCursor(clickable() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            : Cursor.getDefaultCursor());
        repaint();
    }

    public State state() {
        return state;
    }

    public void setTexts(String title, String sub) {
        this.title = title;
        this.sub = sub;
        repaint();
    }

    public String title() {
        return title;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int arc = Theme.px(10);

        Color bg;
        Color line;
        Color inkTitle;
        boolean dashed = false;
        if (primary) {
            boolean on = clickable();
            bg = on ? (pressed ? Theme.darken(Theme.accent(), 0.12)
                : hover ? Theme.lighten(Theme.accent(), 0.10) : Theme.accent())
                : Theme.tile();
            line = on ? Theme.accent() : Theme.border();
            // Текст на заливке акцентом: тёмный на светлом акценте тёмной темы,
            // белый на насыщенном акценте светлой.
            inkTitle = on ? (Theme.isDark() ? new Color(0x0D, 0x14, 0x20) : Color.WHITE)
                : Theme.ink3();
        } else {
            switch (state) {
                case ACTIVE -> {
                    bg = Theme.hover();
                    line = Theme.accent();
                    inkTitle = Theme.ink();
                }
                case AVAILABLE -> {
                    bg = pressed ? Theme.hover() : hover ? Theme.hover() : Theme.tile();
                    line = hover ? Theme.accent() : Theme.border();
                    inkTitle = Theme.ink();
                }
                case PLAYED -> {
                    bg = Theme.tile();
                    line = Theme.divider();
                    inkTitle = Theme.ink3();
                }
                default -> {
                    bg = Theme.tile();
                    line = Theme.border();
                    inkTitle = Theme.ink3();
                    dashed = true;
                }
            }
        }
        g.setColor(bg);
        g.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g.setColor(line);
        g.setStroke(dashed
            ? new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[]{Theme.px(4), Theme.px(3)}, 0f)
            : new BasicStroke(state == State.ACTIVE ? Theme.pxf(1.8) : 1f));
        g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

        int x = Theme.px(10);
        if (icon != null) {
            double s = Math.min(h * 0.42, Theme.px(16));
            MarkIcons.paint(g, icon, x + s / 2, h / 2.0, s,
                state == State.DISABLED ? Theme.ink3() : Theme.ink2());
            x += (int) s + Theme.px(8);
        }
        boolean twoLines = sub != null && !sub.isBlank() && h >= Theme.px(40);
        g.setFont(Theme.font(primary ? 14 : 12.5, Font.BOLD));
        g.setColor(inkTitle);
        var fm = g.getFontMetrics();
        String t = ellipsize(title, fm, w - x - Theme.px(8));
        int titleY = twoLines
            ? h / 2 - Theme.px(2)
            : (h + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(t, x, titleY);
        if (twoLines) {
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(state == State.PLAYED ? Theme.kelium()
                : primary && clickable()
                    ? (Theme.isDark() ? new Color(0x0D, 0x14, 0x20, 190)
                        : new Color(255, 255, 255, 210))
                    : Theme.ink3());
            var fm2 = g.getFontMetrics();
            g.drawString(ellipsize(sub, fm2, w - x - Theme.px(8)), x,
                h / 2 + fm2.getAscent() + Theme.px(1));
        }
        g.dispose();
    }

    static String ellipsize(String s, java.awt.FontMetrics fm, int maxW) {
        if (s == null) {
            return "";
        }
        if (fm.stringWidth(s) <= maxW) {
            return s;
        }
        String e = "…";
        int i = s.length();
        while (i > 1 && fm.stringWidth(s.substring(0, i) + e) > maxW) {
            i--;
        }
        return s.substring(0, i) + e;
    }
}
