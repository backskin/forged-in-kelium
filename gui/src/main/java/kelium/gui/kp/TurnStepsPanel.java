package kelium.gui.kp;

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
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * ШАГИ ХОДА — рисованная лента-таймлайн (концепт §5, вид — по духу Into the
 * Breach): вертикальная нить с узлами. Замок — необратимый шаг («запёкся»),
 * закрашенный узел цвета игрока — точка отката: наведение подсвечивает строку
 * и показывает «⟲ сюда», клик мгновенно возвращает партию к моменту ПЕРЕД этим
 * шагом (обратимое — без подтверждений, по концепту). Кольцо — текущая точка
 * решения. Никаких emoji — замок и стрелка отката нарисованы фигурами
 * (правило скилла: системные глифы на чужой машине превращаются в квадраты).
 */
public final class TurnStepsPanel extends JComponent {

    public enum Kind { LOCKED, UNDOABLE, INFO, CURRENT }

    public record Row(String text, Kind kind, Runnable onUndo) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int seat;
    private int hoverIdx = -1;

    public TurnStepsPanel() {
        setOpaque(false);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = rowAt(e.getY());
                if (i != hoverIdx) {
                    hoverIdx = i;
                    setCursor(i >= 0 && rows.get(i).kind() == Kind.UNDOABLE
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverIdx = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int i = rowAt(e.getY());
                if (i >= 0 && rows.get(i).kind() == Kind.UNDOABLE
                        && rows.get(i).onUndo() != null) {
                    rows.get(i).onUndo().run();
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    private int rowH() {
        return Theme.px(30);
    }

    private int rowAt(int y) {
        int i = (y - Theme.px(4)) / rowH();
        return i >= 0 && i < rows.size() ? i : -1;
    }

    public void setRows(int seat, List<Row> newRows) {
        this.seat = seat;
        rows.clear();
        rows.addAll(newRows);
        hoverIdx = -1;
        revalidate();
        repaint();
    }

    /** Строки (для прогонщиков/тестов). */
    public List<Row> rows() {
        return List.copyOf(rows);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = rowAt(e.getY());
        if (i < 0) {
            return null;
        }
        return switch (rows.get(i).kind()) {
            case UNDOABLE -> "Кликните — партия вернётся к моменту ПЕРЕД этим шагом";
            case LOCKED -> "Необратимый шаг: откат к нему и раньше недоступен";
            case CURRENT -> "Сейчас решается";
            default -> "Точка отката станет кликабельной на выборе действия";
        };
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(230), Theme.px(8) + rows.size() * rowH());
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int railX = Theme.px(16);
        int r = Theme.px(8);

        // нить таймлайна — под узлами
        if (rows.size() > 1) {
            g.setColor(Theme.divider());
            g.setStroke(new BasicStroke(Theme.pxf(1.4)));
            int y0 = Theme.px(4) + rowH() / 2;
            int y1 = Theme.px(4) + (rows.size() - 1) * rowH() + rowH() / 2;
            g.drawLine(railX, y0, railX, y1);
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int top = Theme.px(4) + i * rowH();
            int cy = top + rowH() / 2;
            boolean hovered = i == hoverIdx && row.kind() == Kind.UNDOABLE;

            if (hovered) {
                g.setColor(Theme.hover());
                g.fillRoundRect(Theme.px(2), top + Theme.px(1),
                    getWidth() - Theme.px(6), rowH() - Theme.px(2), Theme.px(8), Theme.px(8));
            }

            // узел
            switch (row.kind()) {
                case LOCKED -> {
                    g.setColor(Theme.divider());
                    g.fillOval(railX - r, cy - r, 2 * r, 2 * r);
                }
                case UNDOABLE -> {
                    g.setColor(Theme.seat(seat));
                    g.fillOval(railX - r, cy - r, 2 * r, 2 * r);
                    g.setColor(Theme.seatStroke(seat));
                    g.drawOval(railX - r, cy - r, 2 * r, 2 * r);
                }
                case CURRENT -> {
                    g.setColor(Theme.bg());
                    g.fillOval(railX - r, cy - r, 2 * r, 2 * r);
                    g.setColor(Theme.accent());
                    g.setStroke(new BasicStroke(Theme.pxf(2.2)));
                    g.drawOval(railX - r, cy - r, 2 * r, 2 * r);
                }
                default -> {
                    g.setColor(Theme.tile());
                    g.fillOval(railX - r, cy - r, 2 * r, 2 * r);
                    g.setColor(Theme.border());
                    g.drawOval(railX - r, cy - r, 2 * r, 2 * r);
                }
            }
            // номер в узле
            g.setFont(Theme.mono(9.5, Font.BOLD));
            String n = String.valueOf(i + 1);
            var nfm = g.getFontMetrics();
            g.setColor(row.kind() == Kind.UNDOABLE ? Color.WHITE
                : row.kind() == Kind.CURRENT ? Theme.accent() : Theme.ink3());
            g.drawString(n, railX - nfm.stringWidth(n) / 2,
                cy + (nfm.getAscent() - nfm.getDescent()) / 2);

            // текст
            boolean current = row.kind() == Kind.CURRENT;
            g.setFont(Theme.font(11.5, current ? Font.BOLD : Font.PLAIN));
            g.setColor(row.kind() == Kind.LOCKED ? Theme.ink3()
                : current ? Theme.ink() : Theme.ink2());
            var fm = g.getFontMetrics();
            int textX = railX + r + Theme.px(8);
            int rightPad = row.kind() == Kind.LOCKED || hovered ? Theme.px(52) : Theme.px(8);
            g.drawString(KpButton.ellipsize(row.text(), fm, getWidth() - textX - rightPad),
                textX, cy + (fm.getAscent() - fm.getDescent()) / 2);

            // правые значки — нарисованные
            if (row.kind() == Kind.LOCKED) {
                paintLock(g, getWidth() - Theme.px(20), cy);
            } else if (hovered) {
                paintUndoArrow(g, getWidth() - Theme.px(44), cy);
                g.setFont(Theme.font(9.5, Font.BOLD));
                g.setColor(Theme.accent());
                g.drawString("сюда", getWidth() - Theme.px(34),
                    cy + (g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
            }
        }
        g.dispose();
    }

    /** Замочек: корпус + дужка, фигурами. */
    private void paintLock(Graphics2D g, int cx, int cy) {
        int w = Theme.px(9);
        int h = Theme.px(7);
        g.setColor(Theme.ink3());
        g.fillRoundRect(cx - w / 2, cy - h / 2 + Theme.px(2), w, h, 2, 2);
        g.setStroke(new BasicStroke(Theme.pxf(1.6)));
        g.drawArc(cx - w / 2 + Theme.px(1), cy - h, w - Theme.px(2), h, 0, 180);
    }

    /** Стрелка отката: дуга против часовой с наконечником. */
    private void paintUndoArrow(Graphics2D g, int cx, int cy) {
        int r = Theme.px(6);
        g.setColor(Theme.accent());
        g.setStroke(new BasicStroke(Theme.pxf(1.8), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(cx - r, cy - r, 2 * r, 2 * r, -40, 250);
        // наконечник у конца дуги (слева-сверху)
        double a = Math.toRadians(210);
        int ax = (int) Math.round(cx + r * Math.cos(a));
        int ay = (int) Math.round(cy - r * Math.sin(a));
        var tip = new java.awt.geom.Path2D.Double();
        tip.moveTo(ax - Theme.px(3), ay - Theme.px(4));
        tip.lineTo(ax + Theme.px(3), ay);
        tip.lineTo(ax - Theme.px(3), ay + Theme.px(3));
        tip.closePath();
        g.fill(tip);
    }
}
