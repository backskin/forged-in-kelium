package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

import javax.swing.JComponent;

import kelium.report.ReplayRecord;

/**
 * Timeline — ЛЕНТА ВРЕМЕНИ: структура партии целиком, а не абстрактный ползунок.
 *
 * <p>Почему не ползунок. В 1.0 перемотка была тонкой линией: 720 шагов, и по ней
 * нельзя понять ни где раунды, ни где бои — а прыгают именно к ним. Здесь четыре
 * дорожки сверху вниз:
 *
 * <ol>
 *   <li><b>раунды</b> — чередующиеся полосы с подписями, границы кругов тоньше;</li>
 *   <li><b>чей ход</b> — по строке на место, цветом места (на узком окне одна
 *       строка, цвет прямо на ней);</li>
 *   <li><b>события</b> — векторные значки: бой, стройка, контейнер, задание,
 *       уничтожение; высота штриха = весомость;</li>
 *   <li><b>накал</b> — площадная кривая: урон, уничтожения и смена очков.</li>
 * </ol>
 *
 * <p>Значки рисуются фигурами, а не символами шрифта: скрещённые мечи в системном
 * шрифте Windows не отрисовываются — этот урок в проекте уже выучен дважды.
 *
 * <p>Взаимодействие: щелчок и протаскивание — перемотка; наведение — ЛУПА с
 * увеличенным участком ±25 шагов; {@code Ctrl+колесо} — увеличение ленты до 20×,
 * тогда она прокручивается за бегунком.
 */
public final class Timeline extends JComponent {

    private static final long serialVersionUID = 1L;

    private final Session session;
    /** Увеличение ленты: 1 — вся партия видна целиком. */
    private double scale = 1.0;
    /** Первый видимый кадр при увеличении. */
    private int offset;
    private int hoverFrame = -1;
    private boolean dragging;

    public Timeline(Session session) {
        this.session = session;
        setOpaque(true);
        setFont(Theme.body());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        session.whenFrameChanged(s -> {
            followPlayhead();
            repaint();
        });
        session.whenRecordChanged(s -> {
            scale = 1.0;
            offset = 0;
            repaint();
        });

        java.awt.event.MouseAdapter mouse = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                dragging = true;
                session.seek(frameAt(e.getX()));
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                dragging = false;
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hoverFrame = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                hoverFrame = frameAt(e.getX());
                repaint();
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                hoverFrame = frameAt(e.getX());
                if (dragging) {
                    session.seek(hoverFrame);
                }
                repaint();
            }
        });
        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                double next = scale * (e.getWheelRotation() < 0 ? 1.25 : 0.8);
                setScale(next, frameAt(e.getX()));
            } else {
                // колесо без Ctrl — листаем шагами, это привычнее прокрутки ленты
                session.stepBy(e.getWheelRotation() < 0 ? -1 : 1);
            }
        });
    }

    /** Увеличить ленту, удерживая указанный кадр под курсором. */
    private void setScale(double next, int anchorFrame) {
        double clamped = Math.max(1.0, Math.min(20.0, next));
        if (clamped == scale) {
            return;
        }
        scale = clamped;
        int visible = visibleCount();
        offset = Math.max(0, Math.min(Math.max(0, session.frameCount() - visible),
            anchorFrame - visible / 2));
        repaint();
    }

    /** Сколько кадров видно при текущем увеличении. */
    private int visibleCount() {
        int n = Math.max(1, session.frameCount());
        return (int) Math.max(2, Math.round(n / scale));
    }

    /** При увеличении лента едет за бегунком, чтобы он не уходил за край. */
    private void followPlayhead() {
        if (scale <= 1.0) {
            offset = 0;
            return;
        }
        int visible = visibleCount();
        int cur = session.cursor();
        if (cur < offset + visible / 8) {
            offset = Math.max(0, cur - visible / 8);
        } else if (cur > offset + visible - visible / 8) {
            offset = Math.min(Math.max(0, session.frameCount() - visible),
                cur - visible + visible / 8);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(600), Theme.px(Theme.H_TIMELINE));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(Theme.px(280), Theme.px(Theme.H_TIMELINE_TIGHT));
    }

    // ==================== перевод координат ====================
    private int pad() {
        return Theme.px(8);
    }

    private int trackWidth() {
        return Math.max(1, getWidth() - 2 * pad());
    }

    /** Кадр под координатой X. */
    private int frameAt(int x) {
        int n = Math.max(1, session.frameCount());
        int visible = visibleCount();
        double k = (x - pad()) / (double) trackWidth();
        int idx = offset + (int) Math.round(k * (visible - 1));
        return Math.max(0, Math.min(n - 1, idx));
    }

    /** Координата X кадра. */
    private double xOf(int frame) {
        int visible = Math.max(1, visibleCount() - 1);
        return pad() + trackWidth() * (frame - offset) / (double) visible;
    }

    // ==================== отрисовка ====================
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Лента живёт в одной полосе с пультом — и цвет у них должен быть один.
        g.setColor(Theme.panel());
        g.fillRect(0, 0, getWidth(), getHeight());

        if (!session.hasRecord()) {
            g.setColor(Theme.alpha(Theme.tile(), 0.7));
            g.fill(new RoundRectangle2D.Double(pad(), Theme.px(6), trackWidth(),
                getHeight() - Theme.px(12), Theme.R_TILE * 2, Theme.R_TILE * 2));
            return;
        }

        boolean tight = getHeight() < Theme.px(Theme.H_TIMELINE - 6);
        int y = Theme.px(2);
        int hRounds = Theme.px(13);
        int hSeats = tight ? Theme.px(7) : Theme.px(5) * session.record().players;
        int hMarks = Theme.px(tight ? 13 : 17);
        int hHeat = Math.max(Theme.px(8), getHeight() - y - hRounds - hSeats - hMarks
            - Theme.px(4));

        paintRounds(g, y, hRounds);
        paintSeats(g, y + hRounds, hSeats, tight);
        paintMarks(g, y + hRounds + hSeats, hMarks);
        paintHeat(g, y + hRounds + hSeats + hMarks, hHeat);
        paintBookmarks(g, y, hRounds);
        paintPlayhead(g);
        if (hoverFrame >= 0 && !dragging) {
            paintMagnifier(g, hoverFrame);
        }
        g.dispose();
    }

    /** Дорожка раундов: чередующиеся полосы и подписи Р1, Р2… */
    private void paintRounds(Graphics2D g, int y, int h) {
        int rounds = Math.max(1, session.roundCount());
        g.setFont(Theme.font(10, Font.BOLD));
        for (int r = 1; r <= rounds; r++) {
            int from = session.roundStartFrame(r);
            int to = r < rounds ? session.roundStartFrame(r + 1) : session.frameCount() - 1;
            if (from < 0) {
                continue;
            }
            double x1 = xOf(from);
            double x2 = xOf(to);
            if (x2 < pad() || x1 > getWidth() - pad()) {
                continue;
            }
            g.setColor(r % 2 == 0 ? Theme.tile() : Theme.alpha(Theme.tile(), 0.55));
            g.fill(new RoundRectangle2D.Double(x1, y, Math.max(1, x2 - x1), h,
                Theme.R_TILE, Theme.R_TILE));
            g.setColor(Theme.ink3());
            String s = "Р" + r;
            if (x2 - x1 > g.getFontMetrics().stringWidth(s) + Theme.px(6)) {
                g.drawString(s, (float) (x1 + Theme.px(3)), y + h - Theme.px(3));
            }
        }
    }

    /** Дорожки «чей ход»: по строке на место, цветом места. */
    private void paintSeats(Graphics2D g, int y, int h, boolean tight) {
        int players = session.record().players;
        int rowH = tight ? h : Math.max(Theme.px(3), h / Math.max(1, players));
        List<ReplayRecord.Frame> frames = session.record().frames;
        int visible = visibleCount();
        int step = Math.max(1, visible / Math.max(1, trackWidth()));
        for (int i = offset; i < Math.min(frames.size(), offset + visible); i += step) {
            Integer act = frames.get(i).snapshot == null ? null : frames.get(i).snapshot.active;
            if (act == null) {
                continue;
            }
            double x = xOf(i);
            double w = Math.max(1, trackWidth() / (double) visible * step + 0.5);
            int row = tight ? 0 : act;
            g.setColor(Theme.alpha(Theme.seat(act), tight ? 0.85 : 0.9));
            g.fill(new java.awt.geom.Rectangle2D.Double(x, y + row * rowH, w,
                Math.max(2, rowH - 1)));
        }
    }

    /**
     * Дорожка событий: значки фигурами, высота — весомость.
     *
     * <p>Значков за партию бывает под тысячу, а пикселей — тысяча четыреста. Если
     * рисовать всё, дорожка превращается в сплошную полосу и перестаёт что-либо
     * говорить. Поэтому на каждый пиксель оставляем только САМОЕ ВЕСОМОЕ событие:
     * бой и уничтожение всегда важнее стройки и контейнера.
     */
    private void paintMarks(Graphics2D g, int y, int h) {
        int base = y + h - Theme.px(2);
        java.util.Map<Integer, Session.Mark> best = new java.util.LinkedHashMap<>();
        for (Session.Mark m : session.marks()) {
            double x = xOf(m.frame());
            if (x < pad() - 2 || x > getWidth() - pad() + 2) {
                continue;
            }
            int slot = (int) Math.round(x / Theme.px(10));
            Session.Mark was = best.get(slot);
            if (was == null || rank(m) > rank(was)) {
                best.put(slot, m);
            }
        }
        for (Session.Mark m : best.values()) {
            double x = xOf(m.frame());
            double weight = Math.min(1.0, m.weight() / 2.0);
            double len = Theme.px(5) + (h - Theme.px(7)) * weight;
            switch (m.kind()) {
                case COMBAT -> {
                    g.setColor(Theme.damage());
                    g.setStroke(new BasicStroke(Theme.px(2)));
                    g.drawLine((int) x, base, (int) x, (int) (base - len));
                }
                case DESTROY -> {
                    g.setColor(Theme.damage());
                    g.setStroke(new BasicStroke(Theme.px(2)));
                    g.drawLine((int) x, base, (int) x, (int) (base - len));
                    int r = Theme.px(3);
                    g.fillOval((int) x - r / 2, (int) (base - len) - r, r, r);
                }
                // Стройка, контейнеры и задания случаются постоянно — они рисуются
                // мелко и приглушённо, чтобы не заслонять бои.
                case BUILD -> {
                    g.setColor(Theme.alpha(m.seat() >= 0 ? Theme.seat(m.seat())
                        : Theme.ink2(), 0.6));
                    triangle(g, x, base, Theme.px(3));
                }
                case CONTAINER -> {
                    g.setColor(Theme.alpha(Theme.container(), 0.6));
                    diamond(g, x, base - Theme.px(2), Theme.px(2));
                }
                case OBJECTIVE -> {
                    g.setColor(Theme.alpha(Theme.points(), 0.65));
                    diamond(g, x, base - Theme.px(3), Theme.px(3));
                }
                case SUPER -> {
                    g.setColor(Theme.points());
                    star(g, x, base - Theme.px(6), Theme.px(6));
                }
                default -> {
                    g.setColor(Theme.ink3());
                    g.drawLine((int) x, base, (int) x, base - Theme.px(4));
                }
            }
        }
    }

    /** Насколько событие важно показать, если на пиксель их несколько. */
    private static int rank(Session.Mark m) {
        return switch (m.kind()) {
            case SUPER -> 5;
            case DESTROY -> 4;
            case COMBAT -> 3;
            case OBJECTIVE -> 2;
            case CONTAINER -> 1;
            default -> 0;
        };
    }

    /** Кривая накала: где партия была горячей. */
    private void paintHeat(Graphics2D g, int y, int h) {
        double[] heat = session.heat();
        if (heat.length == 0 || h < Theme.px(6)) {
            return;
        }
        int visible = visibleCount();
        Path2D p = new Path2D.Double();
        p.moveTo(pad(), y + h);
        for (int i = offset; i < Math.min(heat.length, offset + visible); i++) {
            double x = xOf(i);
            double v = y + h - h * heat[i];
            p.lineTo(x, v);
        }
        p.lineTo(getWidth() - pad(), y + h);
        p.closePath();
        g.setColor(Theme.alpha(Theme.accent(), 0.30));
        g.fill(p);
        g.setColor(Theme.alpha(Theme.accent(), 0.75));
        g.setStroke(new BasicStroke(1f));
        g.draw(p);
    }

    /** Закладки — флажки над дорожкой раундов. */
    private void paintBookmarks(Graphics2D g, int y, int h) {
        for (int f : session.bookmarks()) {
            double x = xOf(f);
            if (x < pad() || x > getWidth() - pad()) {
                continue;
            }
            g.setColor(Theme.points());
            Path2D flag = new Path2D.Double();
            flag.moveTo(x, y);
            flag.lineTo(x + Theme.px(7), y + Theme.px(3));
            flag.lineTo(x, y + Theme.px(6));
            flag.closePath();
            g.fill(flag);
        }
    }

    /** Бегунок: линия во всю высоту и пузырёк с номером шага. */
    private void paintPlayhead(Graphics2D g) {
        double x = xOf(session.cursor());
        g.setColor(Theme.ink());
        g.setStroke(new BasicStroke(Theme.px(2)));
        g.drawLine((int) x, Theme.px(1), (int) x, getHeight() - Theme.px(2));
        g.setColor(Theme.accent());
        int r = Theme.px(4);
        g.fillOval((int) x - r, Theme.px(1), 2 * r, 2 * r);
    }

    /**
     * ЛУПА: увеличенный участок ±25 шагов с подписями событий. Точный прыжок в
     * плотном месте становится возможным без увеличения всей ленты.
     */
    private void paintMagnifier(Graphics2D g, int frame) {
        int span = 25;
        int from = Math.max(0, frame - span);
        int to = Math.min(session.frameCount() - 1, frame + span);
        if (to <= from) {
            return;
        }
        int w = Theme.px(220);
        int h = Theme.px(46);
        int x = (int) Math.max(pad(), Math.min(getWidth() - pad() - w, xOf(frame) - w / 2.0));
        int y = -h - Theme.px(6);
        // рисуем ВЫШЕ ленты — поэтому переносим начало координат окна
        g.translate(0, h + Theme.px(6));
        g.setColor(Theme.alpha(Color.BLACK, 0.25));
        g.fill(new RoundRectangle2D.Double(x + 1, y + 2, w, h, Theme.R_PANEL * 2,
            Theme.R_PANEL * 2));
        g.setColor(Theme.panel());
        g.fill(new RoundRectangle2D.Double(x, y, w, h, Theme.R_PANEL * 2, Theme.R_PANEL * 2));
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, Theme.R_PANEL * 2, Theme.R_PANEL * 2));

        // мелкая дорожка участка
        int inner = w - Theme.px(12);
        for (Session.Mark m : session.marks()) {
            if (m.frame() < from || m.frame() > to) {
                continue;
            }
            double mx = x + Theme.px(6) + inner * (m.frame() - from) / (double) (to - from);
            g.setColor(switch (m.kind()) {
                case COMBAT, DESTROY -> Theme.damage();
                case BUILD -> m.seat() >= 0 ? Theme.seat(m.seat()) : Theme.ink2();
                case CONTAINER -> Theme.container();
                default -> Theme.points();
            });
            g.fillRect((int) mx, y + Theme.px(22), Math.max(1, Theme.px(2)), Theme.px(8));
        }
        double px = x + Theme.px(6) + inner * (frame - from) / (double) (to - from);
        g.setColor(Theme.accent());
        g.drawLine((int) px, y + Theme.px(18), (int) px, y + Theme.px(34));

        ReplayRecord.Frame f = session.frame(frame);
        g.setFont(Theme.font(11, Font.BOLD));
        g.setColor(Theme.ink());
        g.drawString("шаг " + (frame + 1) + " · Р" + f.round
            + (f.circle > 0 ? " круг " + f.circle : ""), x + Theme.px(6), y + Theme.px(14));
        g.setFont(Theme.font(10, Font.PLAIN));
        g.setColor(Theme.ink2());
        String log = f.log == null ? "" : f.log.trim();
        g.drawString(clip(g, log, w - Theme.px(12)), x + Theme.px(6), y + h - Theme.px(5));
        g.translate(0, -h - Theme.px(6));
    }

    // ==================== фигуры ====================
    private void triangle(Graphics2D g, double x, double base, double s) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, base - 2 * s);
        p.lineTo(x - s, base);
        p.lineTo(x + s, base);
        p.closePath();
        g.fill(p);
    }

    private void diamond(Graphics2D g, double x, double y, double s) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y - s);
        p.lineTo(x + s, y);
        p.lineTo(x, y + s);
        p.lineTo(x - s, y);
        p.closePath();
        g.fill(p);
    }

    private void star(Graphics2D g, double x, double y, double r) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(36.0 * i - 90);
            double rr = i % 2 == 0 ? r : r * 0.45;
            double px = x + rr * Math.cos(a);
            double py = y + rr * Math.sin(a);
            if (i == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        p.closePath();
        g.fill(p);
    }

    private static String clip(Graphics2D g, String s, int width) {
        if (s == null) {
            return "";
        }
        if (g.getFontMetrics().stringWidth(s) <= width) {
            return s;
        }
        int n = s.length();
        while (n > 1 && g.getFontMetrics().stringWidth(s.substring(0, n) + "…") > width) {
            n--;
        }
        return s.substring(0, n) + "…";
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        if (!session.hasRecord()) {
            return null;
        }
        return Ui2.tip("Лента времени: раунды, чей ход, события и накал партии.\n"
            + "Щелчок или протаскивание — перемотка. Колесо — шаг. "
            + "Ctrl+колесо — увеличить ленту.");
    }
}
