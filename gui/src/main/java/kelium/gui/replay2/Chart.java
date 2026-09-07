package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;

/**
 * Chart — ГРАФИК ПАРТИИ: один показатель у всех игроков по раундам.
 *
 * <p>Правило, записанное в заказе: <b>один график — один вопрос.</b> Поэтому здесь
 * нет легенды из восьми линий и второй оси: показатель выбирается переключателем, а
 * на полотне ровно по одной линии на игрока.
 *
 * <p>Курсор графика связан с показом: вертикальная черта стоит на том раунде, где
 * сейчас стоит проигрыватель, а щелчок по полотну перематывает партию к началу
 * этого раунда — «на четвёртом раунде синяя кривая ушла вверх, покажи мне это место».
 */
public final class Chart extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Что можно построить. Ключ — для {@link Session#seriesByRound}. */
    public enum Metric {
        VP("vp", "победные очки"),
        KELIUM("kelium", "келемий"),
        COIN("coin", "монеты"),
        TROPHY("trophy", "трофеи"),
        TECH("tech", "шаги науки");

        public final String key;
        public final String label;

        Metric(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private final Session session;
    private Metric metric = Metric.VP;
    private int hoverRound = -1;

    public Chart(Session session) {
        this.session = session;
        setOpaque(true);
        setFont(Theme.body());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        session.whenFrameChanged(s -> repaint());
        session.whenRecordChanged(s -> repaint());
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int r = roundAt(e.getX());
                if (r != hoverRound) {
                    hoverRound = r;
                    repaint();
                }
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hoverRound = -1;
                repaint();
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int r = roundAt(e.getX());
                int frame = session.roundStartFrame(r);
                if (frame >= 0) {
                    session.seek(frame);
                }
            }
        });
    }

    public Metric metric() {
        return metric;
    }

    public void setMetric(Metric m) {
        metric = m;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(320), Theme.px(200));
    }

    private int pad() {
        return Theme.px(28);
    }

    private int rounds() {
        return Math.max(1, session.roundCount());
    }

    private int roundAt(int x) {
        int n = rounds();
        double k = (x - pad()) / (double) Math.max(1, getWidth() - pad() - Theme.px(10));
        return Math.max(1, Math.min(n, 1 + (int) Math.round(k * (n - 1))));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.panel());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (!session.hasRecord()) {
            g.setFont(Theme.body());
            g.setColor(Theme.ink3());
            g.drawString("график появится, когда будет запись", Theme.px(12), Theme.px(24));
            g.dispose();
            return;
        }

        int n = rounds();
        int left = pad();
        int right = getWidth() - Theme.px(10);
        int top = Theme.px(14);
        int bottom = getHeight() - Theme.px(22);

        int max = 1;
        int players = session.record().players;
        int[][] series = new int[players][];
        for (int seat = 0; seat < players; seat++) {
            series[seat] = session.seriesByRound(seat, metric.key);
            for (int v : series[seat]) {
                max = Math.max(max, v);
            }
        }

        // ---- сетка: только по значению, четыре линии, без рамки
        g.setFont(Theme.font(12, Font.PLAIN));
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 4; i++) {
            int v = (int) Math.round(max * i / 4.0);
            double y = bottom - (bottom - top) * i / 4.0;
            g.setColor(Theme.alpha(Theme.border(), 0.8));
            g.drawLine(left, (int) y, right, (int) y);
            g.setColor(Theme.ink3());
            g.drawString(String.valueOf(v), Theme.px(6), (float) (y + Theme.px(3)));
        }

        // ---- подписи раундов
        for (int r = 1; r <= n; r++) {
            double x = xOf(r, left, right, n);
            g.setColor(Theme.ink3());
            g.drawString("Р" + r, (float) (x - Theme.px(5)), bottom + Theme.px(14));
        }

        // ---- где стоит показ
        int curRound = Math.max(1, session.frame().round);
        double cx = xOf(Math.min(curRound, n), left, right, n);
        g.setColor(Theme.alpha(Theme.accent(), 0.9));
        g.setStroke(new BasicStroke(Theme.px(2)));
        g.drawLine((int) cx, top, (int) cx, bottom);

        // ---- линии игроков
        for (int seat = 0; seat < players; seat++) {
            int[] s = series[seat];
            Path2D p = new Path2D.Double();
            for (int r = 1; r <= n; r++) {
                int v = r - 1 < s.length ? s[r - 1] : 0;
                double x = xOf(r, left, right, n);
                double y = bottom - (bottom - top) * v / (double) max;
                if (r == 1) {
                    p.moveTo(x, y);
                } else {
                    p.lineTo(x, y);
                }
            }
            g.setColor(Theme.seatInk(seat));
            g.setStroke(new BasicStroke(Theme.px(2), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            g.draw(p);
            // точки только на видимых раундах — иначе линия превращается в пунктир
            for (int r = 1; r <= n; r++) {
                int v = r - 1 < s.length ? s[r - 1] : 0;
                double x = xOf(r, left, right, n);
                double y = bottom - (bottom - top) * v / (double) max;
                double rr = Theme.pxf(2.5);
                g.fill(new Ellipse2D.Double(x - rr, y - rr, 2 * rr, 2 * rr));
            }
        }

        // ---- пузырёк с числами под курсором мыши
        if (hoverRound > 0) {
            paintBubble(g, hoverRound, series, left, right, top, bottom, max, n);
        }
        g.dispose();
    }

    private static double xOf(int round, int left, int right, int n) {
        return n <= 1 ? left : left + (right - left) * (round - 1) / (double) (n - 1);
    }

    private void paintBubble(Graphics2D g, int round, int[][] series, int left, int right,
                             int top, int bottom, int max, int n) {
        double x = xOf(round, left, right, n);
        g.setColor(Theme.alpha(Theme.ink(), 0.25));
        g.setStroke(new BasicStroke(1f));
        g.drawLine((int) x, top, (int) x, bottom);

        int players = series.length;
        int w = Theme.px(112);
        int h = Theme.px(16) + players * Theme.px(14);
        int bx = (int) Math.min(right - w, Math.max(left, x + Theme.px(8)));
        int by = top + Theme.px(4);
        g.setColor(Theme.alpha(Theme.bg(), 0.96));
        g.fill(new RoundRectangle2D.Double(bx, by, w, h, Theme.R_PANEL * 2, Theme.R_PANEL * 2));
        g.setColor(Theme.border());
        g.draw(new RoundRectangle2D.Double(bx, by, w, h, Theme.R_PANEL * 2, Theme.R_PANEL * 2));
        g.setFont(Theme.font(12, Font.BOLD));
        g.setColor(Theme.ink3());
        g.drawString("РАУНД " + round, bx + Theme.px(6), by + Theme.px(12));
        g.setFont(Theme.mono(12.5, Font.BOLD));
        for (int seat = 0; seat < players; seat++) {
            int v = round - 1 < series[seat].length ? series[seat][round - 1] : 0;
            int y = by + Theme.px(14) + (seat + 1) * Theme.px(13);
            g.setColor(Theme.seatInk(seat));
            g.fillRect(bx + Theme.px(6), y - Theme.px(7), Theme.px(6), Theme.px(6));
            g.drawString("Игрок " + (seat + 1) + "   " + v, bx + Theme.px(16), y);
        }
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        return Ui2.tip(metric.label + " по раундам. Щелчок — перемотка к началу этого "
            + "раунда. Показатель выбирается кнопками сверху.");
    }
}
