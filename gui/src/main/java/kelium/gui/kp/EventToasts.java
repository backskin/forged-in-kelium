package kelium.gui.kp;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.Timer;

import kelium.gui.replay2.Theme;

/**
 * ВСПЛЫВАЮЩИЕ ПЛАШКИ СОБЫТИЙ (заказ дизайнера 27.09.2026: «многое происходит
 * молча: забрал контейнер, добытчик добыл, получил трофей…»). Плашка —
 * полупрозрачная, с печатной иконкой ресурса или лицом вскрытой карты и
 * короткой подписью словами; плавно появляется, чуть поднимается и
 * увеличивается, держится около двух секунд и уходит.
 *
 * <p>Несколько событий подряд встают СТОПКОЙ одно над другим, а лишние ждут
 * в очереди — друг на друга не ложатся. Свои события — крупно над зоной
 * игрока, события соперников — скромнее, у правого верхнего края поля.
 *
 * <p>Мышь плашки не ловят: под ними поле живёт как обычно.
 */
public final class EventToasts extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Плашка: картинка (или null), подпись, краска места, своё ли событие. */
    public record Toast(BufferedImage img, String text, Color seat, boolean own) {
    }

    private static final int IN_MS = 240;
    private static final int HOLD_MS = 1700;
    private static final int OUT_MS = 320;
    private static final int MAX_SHOWN = 3;

    private final Deque<Toast> waiting = new ArrayDeque<>();
    private final List<Toast> shown = new ArrayList<>();
    private final List<Long> started = new ArrayList<>();
    private final Timer tick;
    /** Нижняя кромка места под свои плашки (над зоной игрока), в точках компонента. */
    private int ownBottom = -1;

    public EventToasts() {
        setOpaque(false);
        tick = new Timer(25, e -> step());
        tick.setCoalesce(true);
    }

    /** Где кончается поле над зоной игрока — свои плашки встают выше. */
    public void setOwnBottom(int y) {
        this.ownBottom = y;
    }

    /** Поставить плашку в очередь. */
    public void push(Toast t) {
        if (t == null || t.text() == null || t.text().isBlank()) {
            return;
        }
        waiting.add(t);
        if (waiting.size() > 12) {
            waiting.pollFirst();     // не копить хвост на быстрой цепочке событий
        }
        step();
        if (!tick.isRunning()) {
            tick.start();
        }
    }

    /** Плашки на экране и в очереди — для прогонщиков и тестов. */
    public List<String> textsForTest() {
        List<String> out = new ArrayList<>();
        for (Toast t : shown) {
            out.add(t.text());
        }
        for (Toast t : waiting) {
            out.add(t.text());
        }
        return out;
    }

    /** Сейчас что-то показано или ждёт. */
    public boolean busy() {
        return !shown.isEmpty() || !waiting.isEmpty();
    }

    private void step() {
        long now = System.currentTimeMillis();
        for (int i = shown.size() - 1; i >= 0; i--) {
            if (now - started.get(i) > IN_MS + HOLD_MS + OUT_MS) {
                shown.remove(i);
                started.remove(i);
            }
        }
        while (!waiting.isEmpty() && countOwn(waiting.peekFirst().own()) < MAX_SHOWN) {
            Toast t = waiting.pollFirst();
            shown.add(t);
            // стопка появляется лесенкой, а не разом
            long at = now;
            for (int i = 0; i < shown.size() - 1; i++) {
                if (shown.get(i).own() == t.own()) {
                    at = Math.max(at, started.get(i) + 140);
                }
            }
            started.add(at);
        }
        if (shown.isEmpty() && waiting.isEmpty()) {
            tick.stop();
        }
        // пока все плашки неподвижны (держатся) — перерисовка не нужна
        boolean moving = shown.size() != painted.size();
        for (int i = 0; i < shown.size(); i++) {
            long age = now - started.get(i);
            moving |= age < IN_MS + 20 || age > IN_MS + HOLD_MS - 20;
        }
        if (!moving) {
            return;
        }
        // ПЕРЕРИСОВКА ТОЛЬКО ТАМ, ГДЕ ПЛАШКИ: компонент во всё окно, и repaint()
        // целиком перерисовывал бы под собой поле каждые 16 мс
        for (java.awt.Rectangle r : painted) {
            repaint(r);
        }
        painted.clear();
        for (int i = 0; i < shown.size(); i++) {
            painted.add(areaOf(shown.get(i).own()));
        }
    }

    /** Где были плашки на прошлом такте — там надо стереть. */
    private final List<java.awt.Rectangle> painted = new ArrayList<>();

    /** Полоса, в которой живут плашки своих или чужих событий (с запасом на подъём). */
    private java.awt.Rectangle areaOf(boolean own) {
        int h = (own ? Theme.px(70) : Theme.px(46)) + Theme.px(8);
        int span = h * MAX_SHOWN + Theme.px(40);
        if (own) {
            int bottom = ownBottom > 0 ? ownBottom : getHeight() - Theme.px(160);
            int w = Theme.px(640);
            return new java.awt.Rectangle((getWidth() - w) / 2, bottom - Theme.px(10) - span, w,
                span);
        }
        int w = Theme.px(460);
        return new java.awt.Rectangle(getWidth() - w - Theme.px(10), 0, w, span);
    }

    private int countOwn(boolean own) {
        int n = 0;
        for (Toast t : shown) {
            if (t.own() == own) {
                n++;
            }
        }
        return n;
    }

    @Override
    public boolean contains(int x, int y) {
        return false;                // мышь — полю под плашками
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (shown.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        long now = System.currentTimeMillis();
        int ownSlot = 0;
        int otherSlot = 0;
        for (int i = 0; i < shown.size(); i++) {
            Toast t = shown.get(i);
            long age = now - started.get(i);
            if (age < 0) {
                if (t.own()) {
                    ownSlot++;
                } else {
                    otherSlot++;
                }
                continue;
            }
            double a;
            double lift;
            double grow;
            if (age < IN_MS) {
                double k = ease(age / (double) IN_MS);
                a = k;
                lift = (1 - k) * Theme.px(16);
                grow = 0.9 + 0.1 * k;
            } else if (age < IN_MS + HOLD_MS) {
                // пока держится — стоит неподвижно: перерисовывать под ней поле
                // каждый такт незачем (замер 27.09.2026 — ~30 мс на кадр поля)
                a = 1;
                lift = -Theme.px(6);
                grow = 1.03;
            } else {
                double k = ease((age - IN_MS - HOLD_MS) / (double) OUT_MS);
                a = 1 - k;
                lift = -Theme.px(6) - k * Theme.px(14);
                grow = 1.03;
            }
            paintToast(g, t, t.own() ? ownSlot++ : otherSlot++, a, lift, grow);
        }
        g.dispose();
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private void paintToast(Graphics2D g0, Toast t, int slot, double a, double lift, double grow) {
        Graphics2D g = (Graphics2D) g0.create();
        boolean own = t.own();
        Font f = Theme.font(own ? 17 : 13, Font.BOLD);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int img = own ? Theme.px(46) : Theme.px(30);
        int pad = own ? Theme.px(12) : Theme.px(8);
        int textW = Math.min(fm.stringWidth(t.text()), Theme.px(own ? 520 : 360));
        String text = FieldBubbles.clip(fm, t.text(), textW);
        int w = pad * 3 + img + textW;
        int h = img + pad * 2;
        int gap = Theme.px(8);
        int x;
        int y;
        if (own) {
            int bottom = ownBottom > 0 ? ownBottom : getHeight() - Theme.px(160);
            x = (getWidth() - w) / 2;
            y = bottom - Theme.px(24) - h - slot * (h + gap);
        } else {
            x = getWidth() - w - Theme.px(24);
            y = Theme.px(16) + slot * (h + gap);
        }
        y += (int) Math.round(lift);
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        g.translate(cx, cy);
        g.scale(grow, grow);
        g.translate(-cx, -cy);
        g.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, Math.min(1, a))));
        RoundRectangle2D box = new RoundRectangle2D.Double(x, y, w, h, h * 0.5, h * 0.5);
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(x + 2, y + 4, w, h, h * 0.5, h * 0.5));
        g.setColor(Theme.alpha(Theme.bg(), own ? 0.84 : 0.78));
        g.fill(box);
        Color seat = t.seat() == null ? Theme.accent() : t.seat();
        g.setColor(Theme.alpha(seat, 0.95));
        g.setStroke(new BasicStroke(Theme.pxf(own ? 2.2 : 1.6)));
        g.draw(box);
        if (t.img() != null) {
            double k = Math.min(img / (double) t.img().getWidth(), img / (double) t.img().getHeight());
            int dw = (int) Math.round(t.img().getWidth() * k);
            int dh = (int) Math.round(t.img().getHeight() * k);
            kelium.report.Mips.draw(g, t.img(), x + pad + (img - dw) / 2, y + pad + (img - dh) / 2,
                dw, dh);
        }
        g.setColor(Color.WHITE);
        g.drawString(text, x + pad * 2 + img, y + (h + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }
}
