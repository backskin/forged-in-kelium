package kelium.gui.cardshop;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import javax.swing.JComponent;

/**
 * МАКЕТ КАРТЫ, КОТОРЫЙ МОЖНО ТРОГАТЬ (дизайнер 05.10.2026): наведение
 * обводит часть карты, щелчок выбирает её (и подсвечивает раздел панели),
 * перетаскивание сдвигает. Области частей приходят из отрисовки
 * ({@link CardRender#renderWithRegions}).
 */
final class PreviewPane extends JComponent {
    private static final long serialVersionUID = 1L;

    /** Что делает окно, когда на макете что-то выбрали или сдвинули. */
    interface Listener {
        void selected(String id);

        /** Сдвиг выбранного элемента на (dx, dy) пикселей карты от начала перетаскивания. */
        void dragged(String id, double dx, double dy, boolean done);
    }

    private BufferedImage im;
    private Map<String, Rectangle2D> regions = Map.of();
    private boolean stale;
    private boolean back;
    private String hover;
    private String selected;
    private final Listener listener;
    private double k = 1;
    private int ox;
    private int oy;
    private double pressX;
    private double pressY;
    private boolean dragging;
    private double dragDx;
    private double dragDy;

    PreviewPane(Listener listener) {
        this.listener = listener;
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                String h = back ? null : at(e.getX(), e.getY());
                if (!java.util.Objects.equals(h, hover)) {
                    hover = h;
                    setCursor(Cursor.getPredefinedCursor(h == null ? Cursor.DEFAULT_CURSOR
                        : h.equals(selected) ? Cursor.MOVE_CURSOR : Cursor.HAND_CURSOR));
                    repaint();
                }
            }

            @Override public void mouseExited(MouseEvent e) {
                hover = null;
                repaint();
            }

            @Override public void mousePressed(MouseEvent e) {
                if (back || im == null) {
                    return;
                }
                String h = at(e.getX(), e.getY());
                if (h != null && !h.equals(selected)) {
                    selected = h;
                    listener.selected(h);
                }
                pressX = e.getX();
                pressY = e.getY();
                dragging = h != null;
                dragDx = 0;
                dragDy = 0;
                repaint();
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (!dragging || selected == null) {
                    return;
                }
                dragDx = (e.getX() - pressX) / k;
                dragDy = (e.getY() - pressY) / k;
                listener.dragged(selected, dragDx, dragDy, false);
                repaint();
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (dragging && selected != null && (Math.abs(dragDx) > 0.5 || Math.abs(dragDy) > 0.5)) {
                    listener.dragged(selected, dragDx, dragDy, true);
                }
                dragging = false;
                dragDx = 0;
                dragDy = 0;
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    void set(BufferedImage im, Map<String, Rectangle2D> regions, boolean stale) {
        this.im = im;
        this.regions = regions == null ? Map.of() : regions;
        this.stale = stale;
        this.back = false;
        repaint();
    }

    void showBack(BufferedImage b) {
        this.im = b;
        this.back = true;
        repaint();
    }

    boolean isBack() {
        return back;
    }

    void select(String id) {
        selected = id;
        repaint();
    }

    String selected() {
        return selected;
    }

    /** Самая маленькая область под точкой — так выбирается вложенное, а не фон. */
    private String at(int x, int y) {
        double cx = (x - ox) / k;
        double cy = (y - oy) / k;
        String best = null;
        double area = Double.MAX_VALUE;
        for (var e : regions.entrySet()) {
            Rectangle2D r = e.getValue();
            if (r.contains(cx, cy) && r.getWidth() * r.getHeight() < area) {
                area = r.getWidth() * r.getHeight();
                best = e.getKey();
            }
        }
        return best;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(new Color(0x15181e));
        g.fillRect(0, 0, getWidth(), getHeight());
        if (im == null) {
            g.dispose();
            return;
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        k = Math.min((getWidth() - 48.0) / im.getWidth(), (getHeight() - 48.0) / im.getHeight());
        k = Math.min(k, 1.4);
        int w = (int) (im.getWidth() * k);
        int h = (int) (im.getHeight() * k);
        ox = (getWidth() - w) / 2;
        oy = (getHeight() - h) / 2;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(ox + 6, oy + 10, w, h, 24, 24);
        if (stale) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.35f));
        }
        g.drawImage(im, ox, oy, w, h, null);
        g.setComposite(AlphaComposite.SrcOver);
        if (!back) {
            if (hover != null && !hover.equals(selected)) {
                outline(g, hover, new Color(255, 255, 255, 150), 1.5f, false);
            }
            if (selected != null) {
                outline(g, selected, Style.ACCENT, 2.5f, true);
            }
        }
        g.dispose();
    }

    private void outline(Graphics2D g, String id, Color c, float width, boolean label) {
        Rectangle2D r = regions.get(id);
        if (r == null) {
            return;
        }
        boolean moving = dragging && id.equals(selected);
        double x = ox + (r.getX() + (moving ? dragDx : 0)) * k;
        double y = oy + (r.getY() + (moving ? dragDy : 0)) * k;
        if (moving) {
            g.setColor(new Color(Style.ACCENT.getRed(), Style.ACCENT.getGreen(), Style.ACCENT.getBlue(), 50));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(x - 3, y - 3, r.getWidth() * k + 6,
                r.getHeight() * k + 6, 8, 8));
        }
        g.setColor(c);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
            label ? null : new float[] {5f, 4f}, 0f));
        g.draw(new java.awt.geom.RoundRectangle2D.Double(x - 3, y - 3, r.getWidth() * k + 6,
            r.getHeight() * k + 6, 8, 8));
        if (label) {
            String t = Elements.ru(id);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            int tw = g.getFontMetrics().stringWidth(t) + 12;
            int ly = (int) Math.max(2, y - 22);
            g.fillRoundRect((int) x - 3, ly, tw, 18, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(t, (int) x + 3, ly + 13);
        }
    }
}
