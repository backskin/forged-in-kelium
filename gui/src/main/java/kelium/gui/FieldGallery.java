package kelium.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import kelium.gui.replay2.Theme;

/**
 * ГАЛЕРЕЯ ПОЛЕЙ — выбор раскладки по картинке, а не по имени (просьба
 * дизайнера 25.09.2026: «меню выбора игрового поля, где был бы предпросмотр
 * отрендеренный… не только название, но и предпросмотр»).
 *
 * <p>Лежит поверх окна «Штаба» во весь его размер: сетка плиток, в каждой —
 * поле, собранное и нарисованное так же, как в партии, и подпись. Картинки
 * рисуются в фоне по одной и появляются по мере готовности. Щелчок по
 * плитке выбирает поле, щелчок мимо или Esc — закрывает без выбора.
 */
public final class FieldGallery extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Плитка: подпись, пояснение и картинка (null — ещё рисуется). */
    public static final class Tile {
        final String title;
        final String note;
        volatile BufferedImage thumb;
        volatile boolean failed;

        public Tile(String title, String note) {
            this.title = title;
            this.note = note;
        }
    }

    private final List<Tile> tiles = new ArrayList<>();
    private final List<Rectangle> rects = new ArrayList<>();
    private IntConsumer onPick = i -> { };
    private int selected = -1;
    private int hover = -1;
    private int scroll;
    private int contentH;
    private Thread painter;

    public FieldGallery() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int h = tileAt(e.getX(), e.getY());
                if (h != hover) {
                    hover = h;
                    setCursor(h >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int h = tileAt(e.getX(), e.getY());
                close();
                if (h >= 0) {
                    onPick.accept(h);
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int max = Math.max(0, contentH - getHeight());
                scroll = Math.max(0, Math.min(max, scroll + e.getWheelRotation() * Theme.px(60)));
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            "close");
        getActionMap().put("close", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isVisible()) {
                    close();
                }
            }
        });
    }

    /**
     * Открыть галерею. {@code render} рисует картинку плитки по её номеру (в
     * фоновом потоке); {@code onPick} получает номер выбранной плитки.
     */
    public void open(List<Tile> list, int current, Function<Integer, BufferedImage> render,
                     IntConsumer onPick) {
        stopPainter();
        tiles.clear();
        tiles.addAll(list);
        this.selected = current;
        this.onPick = onPick == null ? i -> { } : onPick;
        this.hover = -1;
        this.scroll = 0;
        setVisible(true);
        requestFocusInWindow();
        repaint();
        List<Tile> mine = List.copyOf(tiles);
        painter = new Thread(() -> {
            for (int i = 0; i < mine.size() && !Thread.currentThread().isInterrupted(); i++) {
                Tile t = mine.get(i);
                if (t.thumb != null) {
                    continue;
                }
                try {
                    t.thumb = render.apply(i);
                    t.failed = t.thumb == null;
                } catch (RuntimeException e) {
                    t.failed = true;
                }
                SwingUtilities.invokeLater(this::repaint);
            }
        }, "галерея полей");
        painter.setDaemon(true);
        painter.start();
    }

    public void close() {
        stopPainter();
        setVisible(false);
    }

    private void stopPainter() {
        if (painter != null) {
            painter.interrupt();
            painter = null;
        }
    }

    private int tileAt(int x, int y) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).contains(x, y + scroll)) {
                return i;
            }
        }
        return -1;
    }

    /** Сколько плиток готово (для тестов и снимков). */
    public int readyCount() {
        int n = 0;
        for (Tile t : tiles) {
            if (t.thumb != null || t.failed) {
                n++;
            }
        }
        return n;
    }

    public int tileCount() {
        return tiles.size();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int w = getWidth();
        int h = getHeight();
        // затемнение всего окна: галерея — отдельный шаг, а не часть панели
        g.setColor(Theme.alpha(Color.BLACK, 0.62));
        g.fillRect(0, 0, w, h);

        int pad = Theme.px(28);
        int top = Theme.px(64);
        g.setColor(Color.WHITE);
        g.setFont(Theme.font(20, Font.BOLD));
        g.drawString("Выберите поле", pad, Theme.px(40));
        g.setFont(Theme.font(12, Font.PLAIN));
        g.setColor(new Color(255, 255, 255, 190));
        g.drawString("щелчок по полю — выбрать · мимо или Esc — закрыть", pad + Theme.px(180),
            Theme.px(40));

        int gap = Theme.px(18);
        int want = Theme.px(300);
        int cols = Math.max(1, (w - 2 * pad + gap) / (want + gap));
        int tw = (w - 2 * pad - (cols - 1) * gap) / cols;
        int imgH = (int) Math.round(tw * 0.68);
        int th = imgH + Theme.px(52);
        rects.clear();
        for (int i = 0; i < tiles.size(); i++) {
            int cx = pad + (i % cols) * (tw + gap);
            int cy = top + (i / cols) * (th + gap);
            rects.add(new Rectangle(cx, cy, tw, th));
        }
        contentH = tiles.isEmpty() ? 0 : top + ((tiles.size() - 1) / cols + 1) * (th + gap) + pad;

        g.clipRect(0, top - Theme.px(8), w, h - top + Theme.px(8));
        g.translate(0, -scroll);
        for (int i = 0; i < tiles.size(); i++) {
            paintTile(g, tiles.get(i), rects.get(i), imgH, i == hover, i == selected);
        }
        g.dispose();
    }

    private void paintTile(Graphics2D g, Tile t, Rectangle r, int imgH, boolean hot,
                           boolean chosen) {
        int rad = Theme.px(14);
        RoundRectangle2D box = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, rad, rad);
        g.setColor(Theme.alpha(Color.BLACK, 0.35));
        g.fill(new RoundRectangle2D.Double(r.x + 2, r.y + 4, r.width, r.height, rad, rad));
        g.setColor(hot ? Theme.hover() : Theme.panel());
        g.fill(box);
        // картинка поля — на тёмной подложке стола, чтобы светлые гексы читались
        int ip = Theme.px(8);
        Rectangle img = new Rectangle(r.x + ip, r.y + ip, r.width - 2 * ip, imgH - ip);
        g.setColor(new Color(0x14303C));
        g.fill(new RoundRectangle2D.Double(img.x, img.y, img.width, img.height, rad - 4, rad - 4));
        BufferedImage th = t.thumb;
        if (th != null) {
            double k = Math.min(img.width / (double) th.getWidth(),
                img.height / (double) th.getHeight());
            int dw = (int) Math.round(th.getWidth() * k);
            int dh = (int) Math.round(th.getHeight() * k);
            g.drawImage(th, img.x + (img.width - dw) / 2, img.y + (img.height - dh) / 2,
                dw, dh, null);
        } else {
            g.setFont(Theme.italic());
            g.setColor(new Color(0x9FBCC9));
            String s = t.failed ? "поле не собралось" : "рисуется…";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, img.x + (img.width - fm.stringWidth(s)) / 2,
                img.y + img.height / 2);
        }
        // подпись
        g.setFont(Theme.font(14, Font.BOLD));
        g.setColor(Theme.ink());
        int ty = r.y + imgH + Theme.px(20);
        g.drawString(clip(g, t.title, r.width - 2 * ip), r.x + ip + Theme.px(2), ty);
        if (t.note != null) {
            g.setFont(Theme.font(11, Font.PLAIN));
            g.setColor(Theme.ink3());
            g.drawString(clip(g, t.note, r.width - 2 * ip), r.x + ip + Theme.px(2),
                ty + Theme.px(18));
        }
        if (chosen || hot) {
            g.setColor(chosen ? Theme.accent() : Theme.alpha(Theme.accent(), 0.6));
            g.setStroke(new BasicStroke(Theme.pxf(chosen ? 3 : 2)));
            g.draw(box);
        }
    }

    private static String clip(Graphics2D g, String s, int w) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= w) {
            return s;
        }
        String e = "…";
        int n = s.length();
        while (n > 0 && fm.stringWidth(s.substring(0, n) + e) > w) {
            n--;
        }
        return s.substring(0, n) + e;
    }
}
