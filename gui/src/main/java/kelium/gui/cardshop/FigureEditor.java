package kelium.gui.cardshop;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * РЕДАКТОР ФИГУРЫ: сетка гексов (центр и два кольца), щелчок по сектору
 * закрашивает его или снимает заливку. Повернуть — на 60°; отражения нет и не
 * будет: фигуру за столом можно только крутить.
 */
final class FigureEditor extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int RADIUS = 2;

    private Figure fig;
    private final Consumer<Figure> onChange;
    private final Grid grid = new Grid();
    private final JLabel info = new JLabel();

    FigureEditor(Figure start, Consumer<Figure> onChange) {
        super(new BorderLayout(0, 6));
        this.fig = start;
        this.onChange = onChange;
        setOpaque(false);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setOpaque(false);
        JButton turn = new JButton("Повернуть ⟲");
        turn.setFocusable(false);
        turn.addActionListener(e -> {
            fig = fig.turned();
            fire();
        });
        JButton clear = new JButton("Очистить");
        clear.setFocusable(false);
        clear.addActionListener(e -> {
            fig = new Figure();
            fire();
        });
        info.setForeground(Style.INK2);
        bar.add(turn);
        bar.add(clear);
        bar.add(info);
        add(grid, BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);
        updateInfo();
    }

    private void fire() {
        updateInfo();
        grid.repaint();
        onChange.accept(fig);
    }

    private void updateInfo() {
        info.setText(fig.isEmpty() ? "щёлкайте по секторам — закрашенные надо занять"
            : "секторов: " + fig.count() + " · можно поворачивать, отражать нельзя");
    }

    /** Сетка гексов с секторами. */
    private final class Grid extends JComponent {
        private static final long serialVersionUID = 1L;

        Grid() {
            setPreferredSize(new Dimension(420, 330));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    pick(e.getX(), e.getY());
                }
            });
        }

        private double hexSize() {
            return Math.min(getWidth() / ((2 * RADIUS + 1) * 1.5 + 0.6),
                getHeight() / ((2 * RADIUS + 1) * Math.sqrt(3) + 0.3));
        }

        private boolean inGrid(int q, int r) {
            return Figure.dist(q, r, 0, 0) <= RADIUS;
        }

        private void pick(int mx, int my) {
            double s = hexSize();
            double cx0 = getWidth() / 2.0;
            double cy0 = getHeight() / 2.0;
            for (int q = -RADIUS; q <= RADIUS; q++) {
                for (int r = -RADIUS; r <= RADIUS; r++) {
                    if (!inGrid(q, r)) {
                        continue;
                    }
                    double[] c = Figure.center(q, r, s);
                    double x = cx0 + c[0];
                    double y = cy0 + c[1];
                    if (Figure.hex(x, y, s).contains(mx, my)) {
                        // сектор i лежит между углами −60·i и 60 − 60·i (ось y вниз)
                        double ang = Math.toDegrees(Math.atan2(my - y, mx - x));
                        int i = Math.floorMod((int) Math.floor(-ang / 60.0) + 1, 6);
                        fig.toggle(q, r, i);
                        fire();
                        return;
                    }
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x14171d));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            double s = hexSize();
            double cx0 = getWidth() / 2.0;
            double cy0 = getHeight() / 2.0;
            for (int q = -RADIUS; q <= RADIUS; q++) {
                for (int r = -RADIUS; r <= RADIUS; r++) {
                    if (!inGrid(q, r)) {
                        continue;
                    }
                    double[] c = Figure.center(q, r, s);
                    double x = cx0 + c[0];
                    double y = cy0 + c[1];
                    g.setColor(new Color(0x2a2f38));
                    g.fill(Figure.hex(x, y, s * 0.97));
                    for (int i = 0; i < 6; i++) {
                        if (fig.filled(q, r, i)) {
                            g.setColor(Style.ACCENT);
                            g.fill(Figure.sector(x, y, s * 0.97, i));
                        }
                    }
                    g.setColor(new Color(0x4a5160));
                    g.setStroke(new BasicStroke(1f));
                    for (int i = 0; i < 6; i++) {
                        double a = Math.toRadians(60.0 * i);
                        g.draw(new java.awt.geom.Line2D.Double(x, y, x + s * 0.97 * Math.cos(a),
                            y + s * 0.97 * Math.sin(a)));
                    }
                    g.setColor(new Color(0x8a93a3));
                    g.setStroke(new BasicStroke(2f));
                    g.draw(Figure.hex(x, y, s * 0.97));
                }
            }
            g.dispose();
        }
    }
}
