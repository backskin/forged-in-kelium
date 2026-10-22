package kelium.gui.replay2;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import kelium.gui.CardArt;

/**
 * УВЕЛИЧЕНИЕ КАРТЫ ПРИ НАВЕДЕНИИ — печатное лицо крупно, рядом с курсором.
 *
 * <p>Мелкая карта в ряду узнаётся, но не читается: печатный текст условия и
 * наград на ней в два-три пикселя. Навёл — рядом всплывает та же картинка
 * крупно, в своей пропорции (стоячее задание выше, лежачий арсенал шире),
 * увёл — пропала. Окно одно на всё приложение.
 */
public final class CardZoom {

    private CardZoom() {
    }

    private static JWindow window;
    private static BufferedImage shown;

    /** Длинная сторона увеличенной карты в точках вёрстки. */
    private static final int LONG_SIDE = 440;

    /**
     * Показать карту крупно возле области {@code near} компонента {@code owner}:
     * справа от неё, а не влезает — слева; по высоте прижато к экрану.
     */
    public static void show(Component owner, BufferedImage img, Rectangle near) {
        if (img == null || owner == null || !owner.isShowing()) {
            hide();
            return;
        }
        Window top = SwingUtilities.getWindowAncestor(owner);
        if (window == null || window.getOwner() != top) {
            if (window != null) {
                window.dispose();
            }
            window = new JWindow(top);
            window.setFocusableWindowState(false);
            window.setContentPane(new Face());
        }
        int longSide = Theme.px(LONG_SIDE);
        double a = CardArt.aspect(img);
        int w = a >= 1 ? longSide : (int) Math.round(longSide * a);
        int h = a >= 1 ? (int) Math.round(longSide / a) : longSide;
        shown = img;
        Point p = new Point(near.x + near.width, near.y);
        SwingUtilities.convertPointToScreen(p, owner);
        GraphicsConfiguration gc = owner.getGraphicsConfiguration();
        Rectangle screen = gc == null ? new Rectangle(0, 0, 4000, 3000) : gc.getBounds();
        int gap = Theme.px(8);
        int x = p.x + gap;
        if (x + w > screen.x + screen.width) {
            Point l = new Point(near.x, near.y);
            SwingUtilities.convertPointToScreen(l, owner);
            x = l.x - gap - w;
        }
        x = Math.max(screen.x, x);
        int y = Math.max(screen.y, Math.min(p.y, screen.y + screen.height - h));
        window.setBounds(x, y, w, h);
        window.getContentPane().repaint();
        if (!window.isVisible()) {
            window.setVisible(true);
        }
    }

    /** Убрать увеличение. */
    public static void hide() {
        if (window != null && window.isVisible()) {
            window.setVisible(false);
        }
        shown = null;
    }

    private static final class Face extends JComponent {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Theme.px(300), Theme.px(440));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Theme.bg());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (shown != null) {
                CardArt.drawFit(g, shown, 0, 0, getWidth(), getHeight(), 0);
            }
            g.dispose();
        }
    }
}
