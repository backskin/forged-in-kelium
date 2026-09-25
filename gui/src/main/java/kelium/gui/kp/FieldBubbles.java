package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import kelium.gui.replay2.Theme;

/**
 * ВЫБОР НА САМОМ ПОЛЕ — пузыри вариантов у гекса и карточка вопроса над полем.
 *
 * <p>Просьба дизайнера 25.09.2026: «почему все выборы в каких-то кнопках слева
 * снизу? Сделай интерактивно, нарисовано на поле, примыкая прямо к гексу, к
 * элементу на поле — чтобы можно было увеличивать, уменьшать, а оно всё равно
 * отрисовывалось и перемещалось». Поэтому пузырь привязан к ГЕКСУ, а не к
 * месту окна: рисуется в экранных координатах там, где гекс сейчас на экране,
 * и едет вместе с полем при масштабе и перетаскивании. Кегль при этом не
 * растёт с масштабом — текст должен читаться и на мелком поле.
 *
 * <p>Устройство: у каждого гекса-цели свой список вариантов. Вариант один —
 * щелчок по гексу выбирает его сразу; вариантов несколько (две атаки по одной
 * цели, разные отряды на гексе) — щелчок раскрывает пузырь со списком у этого
 * гекса. Варианты без гекса (прекратить бой, пропустить, курс рынка) живут в
 * КАРТОЧКЕ ВОПРОСА — узкой плашке у верхней кромки поля, там же написано, что
 * от игрока ждут.
 */
public final class FieldBubbles {

    /**
     * Вариант выбора.
     *
     * @param label  что сделать — крупно
     * @param sub    пояснение мелко (цена, последствие); {@code null} — нет
     * @param tone   {@code 0} — обычный, {@code 1} — главный (акцент),
     *               {@code 2} — опасный/отказ (приглушённый)
     */
    public record Opt(String label, String sub, int tone, Runnable pick) {

        public static Opt of(String label, Runnable pick) {
            return new Opt(label, null, 0, pick);
        }
    }

    private Map<String, List<Opt>> byHex = Map.of();
    private String openHex;
    private String title;
    private String hint;
    private List<Opt> dock = List.of();
    private Color seatColor = Theme.accent();

    /** Что под курсором было при последней отрисовке. */
    private final Map<Rectangle, Opt> hits = new LinkedHashMap<>();
    private final List<Rectangle> panels = new ArrayList<>();
    private Opt hover;

    /** Задать вопрос: цели на гексах, заголовок, пояснение и варианты без гекса. */
    public void set(Map<String, List<Opt>> byHex, String title, String hint, List<Opt> dock,
                    Color seatColor) {
        this.byHex = byHex == null ? Map.of() : new LinkedHashMap<>(byHex);
        this.title = title;
        this.hint = hint;
        this.dock = dock == null ? List.of() : List.copyOf(dock);
        this.seatColor = seatColor == null ? Theme.accent() : seatColor;
        this.openHex = null;
        this.hover = null;
        // Одна цель с несколькими вариантами — пузырь открыт сразу: щёлкать по
        // гексу, чтобы увидеть единственный список, незачем.
        if (this.byHex.size() == 1) {
            var only = this.byHex.entrySet().iterator().next();
            if (only.getValue().size() > 1) {
                openHex = only.getKey();
            }
        }
    }

    public void clear() {
        set(null, null, null, null, null);
    }

    public boolean active() {
        return !byHex.isEmpty() || !dock.isEmpty() || title != null;
    }

    public java.util.Set<String> hexes() {
        return byHex.keySet();
    }

    /**
     * ЩЕЛЧОК ПО ГЕКСУ-ЦЕЛИ: один вариант — выбрать, несколько — раскрыть пузырь.
     *
     * @return был ли гекс целью
     */
    public boolean clickHex(String hexId) {
        List<Opt> opts = hexId == null ? null : byHex.get(hexId);
        if (opts == null || opts.isEmpty()) {
            openHex = null;
            return false;
        }
        if (opts.size() == 1) {
            opts.get(0).pick().run();
            return true;
        }
        openHex = hexId.equals(openHex) ? null : hexId;
        return true;
    }

    /** Лежит ли точка на пузыре или карточке (там поле не таскают). */
    public boolean covers(Point p) {
        for (Rectangle r : panels) {
            if (r.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /** Вариант под точкой, либо null. */
    public Opt optAt(Point p) {
        for (Map.Entry<Rectangle, Opt> e : hits.entrySet()) {
            if (e.getKey().contains(p)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Обновить наведение; {@code true} — надо перерисовать. */
    public boolean hover(Point p) {
        Opt now = p == null ? null : optAt(p);
        if (now != hover) {
            hover = now;
            return true;
        }
        return false;
    }

    public boolean hovering() {
        return hover != null;
    }

    /** Полный текст варианта под точкой — для подсказки, если строку обрезало. */
    public String tipAt(Point p) {
        Opt o = optAt(p);
        if (o == null) {
            return null;
        }
        return o.sub() == null ? o.label() : o.label() + " — " + o.sub();
    }

    public void closeBubble() {
        openHex = null;
    }

    // ==================== отрисовка ====================

    /**
     * Нарисовать в ЭКРАННЫХ координатах.
     *
     * @param hexScreen центр гекса на экране по его id (null — гекса нет)
     * @param hexRadius радиус гекса на экране в точках
     */
    public void paint(Graphics2D g, int w, int h, Function<String, Point2D> hexScreen,
                      double hexRadius) {
        hits.clear();
        panels.clear();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // МЕТКИ ЧИСЛА ВАРИАНТОВ над гексами с несколькими вариантами: видно
        // заранее, что щелчок откроет список, а не сыграет сразу.
        for (Map.Entry<String, List<Opt>> e : byHex.entrySet()) {
            if (e.getValue().size() < 2 || e.getKey().equals(openHex)) {
                continue;
            }
            Point2D c = hexScreen.apply(e.getKey());
            if (c != null) {
                badge(g, c.getX() + hexRadius * 0.55, c.getY() - hexRadius * 0.62,
                    String.valueOf(e.getValue().size()));
            }
        }
        if (title != null || !dock.isEmpty()) {
            paintDock(g, w);
        }
        if (openHex != null) {
            Point2D c = hexScreen.apply(openHex);
            List<Opt> opts = byHex.get(openHex);
            if (c != null && opts != null) {
                paintBubble(g, w, h, c, hexRadius, opts);
            }
        }
    }

    private void badge(Graphics2D g, double x, double y, String n) {
        int d = Theme.px(20);
        g.setColor(seatColor);
        g.fillOval((int) (x - d / 2.0), (int) (y - d / 2.0), d, d);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Theme.pxf(1.6)));
        g.drawOval((int) (x - d / 2.0), (int) (y - d / 2.0), d, d);
        g.setFont(Theme.font(11, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(n, (float) (x - fm.stringWidth(n) / 2.0),
            (float) (y + fm.getAscent() / 2.0 - Theme.px(1)));
    }

    /** Карточка вопроса у верхней кромки поля: что ждут и варианты без гекса. */
    private void paintDock(Graphics2D g, int w) {
        int pad = Theme.px(12);
        Font tf = Theme.font(14, Font.BOLD);
        Font hf = Theme.font(12, Font.PLAIN);
        Font cf = Theme.font(13, Font.BOLD);
        Font sf = Theme.font(10, Font.PLAIN);
        int maxW = Math.min(w - Theme.px(40), Theme.px(900));

        // ряды фишек-вариантов с переносом
        g.setFont(cf);
        FontMetrics cm = g.getFontMetrics();
        g.setFont(sf);
        FontMetrics sm = g.getFontMetrics();
        int chipH = Theme.px(40);
        int gap = Theme.px(8);
        List<int[]> chips = new ArrayList<>();       // {w}
        for (Opt o : dock) {
            int lw = cm.stringWidth(clip(cm, o.label(), Theme.px(300)));
            int sw = o.sub() == null ? 0 : sm.stringWidth(clip(sm, o.sub(), Theme.px(300)));
            chips.add(new int[]{Math.max(lw, sw) + Theme.px(28)});
        }
        List<List<Integer>> rows = new ArrayList<>();
        int rowW = 0;
        List<Integer> row = new ArrayList<>();
        for (int i = 0; i < chips.size(); i++) {
            int cw = chips.get(i)[0];
            if (!row.isEmpty() && rowW + gap + cw > maxW - pad * 2) {
                rows.add(row);
                row = new ArrayList<>();
                rowW = 0;
            }
            rowW += (row.isEmpty() ? 0 : gap) + cw;
            row.add(i);
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        int widest = 0;
        for (List<Integer> r : rows) {
            int s = 0;
            for (int i : r) {
                s += chips.get(i)[0] + (s == 0 ? 0 : gap);
            }
            widest = Math.max(widest, s);
        }
        g.setFont(tf);
        FontMetrics tm = g.getFontMetrics();
        g.setFont(hf);
        FontMetrics hm = g.getFontMetrics();
        String t = title == null ? "" : clip(tm, title, maxW - pad * 2 - Theme.px(8));
        String hn = hint == null ? null : clip(hm, hint, maxW - pad * 2 - Theme.px(8));
        int textW = Math.max(tm.stringWidth(t), hn == null ? 0 : hm.stringWidth(hn));
        int cardW = Math.min(maxW, Math.max(textW, widest) + pad * 2 + Theme.px(8));
        int cardH = pad + tm.getHeight() + (hn == null ? 0 : hm.getHeight())
            + (rows.isEmpty() ? 0 : Theme.px(8) + rows.size() * chipH + (rows.size() - 1) * gap)
            + pad;
        int x = (w - cardW) / 2;
        int y = Theme.px(10);
        panel(g, x, y, cardW, cardH);
        // полоса цвета места слева — чей вопрос
        g.setColor(seatColor);
        g.fill(new RoundRectangle2D.Double(x, y, Theme.px(5), cardH, Theme.px(4), Theme.px(4)));
        int ty = y + pad + tm.getAscent();
        g.setFont(tf);
        g.setColor(Theme.ink());
        g.drawString(t, x + pad + Theme.px(6), ty);
        if (hn != null) {
            g.setFont(hf);
            g.setColor(Theme.ink2());
            g.drawString(hn, x + pad + Theme.px(6), ty + hm.getHeight());
        }
        int cy = y + pad + tm.getHeight() + (hn == null ? 0 : hm.getHeight()) + Theme.px(8);
        for (List<Integer> r : rows) {
            int rw = 0;
            for (int i : r) {
                rw += chips.get(i)[0] + (rw == 0 ? 0 : gap);
            }
            int cx = x + (cardW - rw) / 2;
            for (int i : r) {
                int cw = chips.get(i)[0];
                chip(g, cx, cy, cw, chipH, dock.get(i), cm, sm);
                cx += cw + gap;
            }
            cy += chipH + gap;
        }
    }

    private void chip(Graphics2D g, int x, int y, int w, int h, Opt o,
                      FontMetrics cm, FontMetrics sm) {
        boolean hot = o == hover;
        Color fill;
        Color ink;
        if (o.tone() == 1) {
            fill = hot ? Theme.lighten(seatColor, 0.12) : seatColor;
            ink = Color.WHITE;
        } else if (o.tone() == 2) {
            fill = hot ? Theme.hover() : Theme.tile();
            ink = Theme.ink2();
        } else {
            fill = hot ? Theme.hover() : Theme.panel();
            ink = Theme.ink();
        }
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, Theme.px(10), Theme.px(10));
        g.setColor(fill);
        g.fill(rr);
        g.setColor(o.tone() == 1 ? Theme.darken(seatColor, 0.2)
            : hot ? seatColor : Theme.border());
        g.setStroke(new BasicStroke(hot ? Theme.pxf(2) : 1f));
        g.draw(rr);
        g.setFont(cm.getFont());
        g.setColor(ink);
        String l = clip(cm, o.label(), w - Theme.px(20));
        if (o.sub() == null) {
            g.drawString(l, x + (w - cm.stringWidth(l)) / 2, y + (h + cm.getAscent()) / 2 - Theme.px(2));
        } else {
            g.drawString(l, x + (w - cm.stringWidth(l)) / 2, y + Theme.px(4) + cm.getAscent());
            g.setFont(sm.getFont());
            g.setColor(o.tone() == 1 ? new Color(255, 255, 255, 210) : Theme.ink3());
            String s = clip(sm, o.sub(), w - Theme.px(20));
            g.drawString(s, x + (w - sm.stringWidth(s)) / 2, y + h - Theme.px(6));
        }
        hits.put(new Rectangle(x, y, w, h), o);
    }

    /** Пузырь со списком вариантов у гекса, с «хвостиком» к гексу. */
    private void paintBubble(Graphics2D g, int w, int h, Point2D c, double hexR, List<Opt> opts) {
        Font lf = Theme.font(13, Font.BOLD);
        Font sf = Theme.font(10, Font.PLAIN);
        g.setFont(lf);
        FontMetrics lm = g.getFontMetrics();
        g.setFont(sf);
        FontMetrics sm = g.getFontMetrics();
        int pad = Theme.px(8);
        int rowH = Theme.px(38);
        int maxRow = Theme.px(340);
        int bw = Theme.px(160);
        for (Opt o : opts) {
            bw = Math.max(bw, Math.min(maxRow, lm.stringWidth(o.label()) + Theme.px(28)));
            if (o.sub() != null) {
                bw = Math.max(bw, Math.min(maxRow, sm.stringWidth(o.sub()) + Theme.px(28)));
            }
        }
        int bh = pad * 2 + opts.size() * rowH + (opts.size() - 1) * Theme.px(4);
        double gapX = hexR * 0.95 + Theme.px(10);
        boolean right = c.getX() + gapX + bw < w - Theme.px(8);
        int x = (int) Math.round(right ? c.getX() + gapX : c.getX() - gapX - bw);
        int y = (int) Math.round(Math.max(Theme.px(8), Math.min(h - bh - Theme.px(8),
            c.getY() - bh / 2.0)));
        x = Math.max(Theme.px(8), Math.min(w - bw - Theme.px(8), x));

        // хвостик от пузыря к гексу
        double tipX = right ? c.getX() + hexR * 0.55 : c.getX() - hexR * 0.55;
        double baseX = right ? x + 1 : x + bw - 1;
        double midY = Math.max(y + Theme.px(14), Math.min(y + bh - Theme.px(14), c.getY()));
        Path2D tail = new Path2D.Double();
        tail.moveTo(baseX, midY - Theme.px(9));
        tail.lineTo(tipX, c.getY());
        tail.lineTo(baseX, midY + Theme.px(9));
        tail.closePath();
        g.setColor(Theme.alpha(Color.BLACK, 0.18));
        g.translate(0, Theme.px(2));
        g.fill(tail);
        g.translate(0, -Theme.px(2));
        g.setColor(Theme.panel());
        g.fill(tail);
        g.setColor(seatColor);
        g.setStroke(new BasicStroke(Theme.pxf(1.6)));
        g.draw(tail);

        panel(g, x, y, bw, bh);
        g.setColor(seatColor);
        g.setStroke(new BasicStroke(Theme.pxf(1.6)));
        g.draw(new RoundRectangle2D.Double(x, y, bw, bh, Theme.px(12), Theme.px(12)));
        int ry = y + pad;
        for (Opt o : opts) {
            boolean hot = o == hover;
            Rectangle r = new Rectangle(x + Theme.px(4), ry, bw - Theme.px(8), rowH);
            if (hot) {
                g.setColor(Theme.seatWash(0, 0) == null ? Theme.hover() : Theme.hover());
                g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height,
                    Theme.px(8), Theme.px(8)));
            }
            // маркер варианта: кружок цвета тона
            int d = Theme.px(8);
            g.setColor(o.tone() == 1 ? seatColor : o.tone() == 2 ? Theme.ink3() : Theme.ink2());
            g.fillOval(r.x + Theme.px(8), r.y + (rowH - d) / 2, d, d);
            int tx = r.x + Theme.px(24);
            int avail = r.width - Theme.px(30);
            g.setFont(lf);
            g.setColor(o.tone() == 2 ? Theme.ink2() : Theme.ink());
            String l = clip(lm, o.label(), avail);
            if (o.sub() == null) {
                g.drawString(l, tx, r.y + (rowH + lm.getAscent()) / 2 - Theme.px(2));
            } else {
                g.drawString(l, tx, r.y + Theme.px(4) + lm.getAscent());
                g.setFont(sf);
                g.setColor(Theme.ink3());
                g.drawString(clip(sm, o.sub(), avail), tx, r.y + rowH - Theme.px(6));
            }
            hits.put(r, o);
            ry += rowH + Theme.px(4);
        }
    }

    /** Плашка с тенью — пузырь и карточка вопроса. */
    private void panel(Graphics2D g, int x, int y, int w, int h) {
        for (int i = 3; i >= 1; i--) {
            g.setColor(Theme.alpha(Color.BLACK, 0.05 * i));
            g.fill(new RoundRectangle2D.Double(x - i + Theme.px(1), y - i + Theme.px(3),
                w + 2 * i, h + 2 * i, Theme.px(14), Theme.px(14)));
        }
        g.setColor(Theme.panel());
        g.fill(new RoundRectangle2D.Double(x, y, w, h, Theme.px(12), Theme.px(12)));
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, Theme.px(12), Theme.px(12)));
        panels.add(new Rectangle(x, y, w, h));
    }

    /** Обрезать строку по ширине с многоточием. */
    static String clip(FontMetrics fm, String s, int width) {
        if (s == null) {
            return "";
        }
        if (fm.stringWidth(s) <= width) {
            return s;
        }
        String dots = "…";
        int n = s.length();
        while (n > 0 && fm.stringWidth(s.substring(0, n) + dots) > width) {
            n--;
        }
        return s.substring(0, n) + dots;
    }
}
