package kelium.gui.kp;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * РАСКРЫТЫЕ КАРТЫ — рука игрока, разложенная перед ним крупно.
 *
 * <p>Просьба дизайнера 25.09.2026: «у игрока должна быть зона — красивая, с
 * картами, которая вскрывается, раскрывается, которую можно посмотреть». На
 * столе рука лежит веером или стопкой; щелчок по ней раскладывает карты во
 * весь экран поверх поля: каждая — своим печатным лицом, под ней то, что с
 * ней можно сделать сейчас (выполнить, усилить, сжечь, установить). Действий
 * нет — карты просто смотрят. Щелчок мимо карт или Esc — сложить обратно.
 */
public final class CardSpread extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Карта раскладки: лицо, имя, пометка под лицом и доступные действия. */
    public record Card(String id, BufferedImage face, String name, String note,
                       List<FieldBubbles.Opt> actions) {
    }

    private String title = "";
    private String subtitle = "";
    private final List<Card> cards = new ArrayList<>();
    private final Anim anim = new Anim();
    private final Map<Rectangle, FieldBubbles.Opt> chips = new LinkedHashMap<>();
    private final List<Rectangle> cardRects = new ArrayList<>();
    private Point mouse;
    private Color accent = Theme.accent();
    private Runnable onClose = () -> { };

    public CardSpread() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = e.getPoint();
                boolean hand = chipAt(mouse) != null;
                setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                FieldBubbles.Opt o = chipAt(e.getPoint());
                if (o != null) {
                    close();
                    if (o.pick() != null) {
                        o.pick().run();
                    }
                    return;
                }
                for (Rectangle r : cardRects) {
                    if (r.contains(e.getPoint())) {
                        return;           // по карте — ничего, смотрят
                    }
                }
                close();                  // мимо карт — сложить
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // слой глотает нажатия: поле под ним не таскается
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public boolean isOpen() {
        return isVisible() && anim.value() > 0.01;
    }

    /** Разложить карты. */
    public void open(String title, String subtitle, List<Card> newCards, Color accent,
                     Runnable onClose) {
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.accent = accent == null ? Theme.accent() : accent;
        this.onClose = onClose == null ? () -> { } : onClose;
        cards.clear();
        cards.addAll(newCards);
        setVisible(true);
        anim.snap(0);
        anim.play(1, 180, v -> repaint(), null);
    }

    public void close() {
        if (!isVisible()) {
            return;
        }
        Runnable after = onClose;
        onClose = () -> { };
        anim.play(0, 130, v -> repaint(), () -> {
            setVisible(false);
            after.run();
        });
    }

    private FieldBubbles.Opt chipAt(Point p) {
        if (p == null) {
            return null;
        }
        for (Map.Entry<Rectangle, FieldBubbles.Opt> e : chips.entrySet()) {
            if (e.getKey().contains(p)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Все действия раскладки — для прогонщиков и тестов. */
    public List<FieldBubbles.Opt> actionsForTest() {
        List<FieldBubbles.Opt> out = new ArrayList<>();
        for (Card c : cards) {
            out.addAll(c.actions());
        }
        return out;
    }

    // ==================== рисование ====================

    @Override
    protected void paintComponent(Graphics g0) {
        double a = anim.value();
        if (a <= 0.01 || cards.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setComposite(AlphaComposite.SrcOver.derive((float) (0.62 * a)));
        g.setColor(new Color(0x0C, 0x10, 0x16));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setComposite(AlphaComposite.SrcOver.derive((float) a));
        chips.clear();
        cardRects.clear();

        int w = getWidth();
        int h = getHeight();
        int n = cards.size();
        int maxActs = 0;
        for (Card c : cards) {
            maxActs = Math.max(maxActs, c.actions().size());
        }
        // КНОПКИ ПОД КАРТОЙ — КРУПНЫЕ: карта большая, и мелкий текст под ней
        // не читался (замечание дизайнера 25.09.2026).
        int chipH = Theme.px(52);
        int actsH = maxActs == 0 ? Theme.px(24) : maxActs * (chipH + Theme.px(6)) + Theme.px(8);
        int headH = Theme.px(70);
        // ПРОПОРЦИЯ КАЖДОЙ КАРТЫ — С ЕЁ ЛИЦА: задания и приказы стоят,
        // арсенал лежит (замечание дизайнера 25.09.2026: «ты кукожишь карты»).
        double[] ratio = new double[n];
        for (int i = 0; i < n; i++) {
            BufferedImage f = cards.get(i).face();
            ratio[i] = f == null || f.getHeight() == 0 ? 0.643
                : f.getWidth() / (double) f.getHeight();
        }
        // РАЗМЕР — ПО МЕСТУ: одна строка, если влезает, иначе две-три
        int gap = Theme.px(18);
        int rows = 1;
        int cardH;
        int perRow;
        while (true) {
            perRow = (n + rows - 1) / rows;
            double widest = 0;
            for (int r = 0; r < rows; r++) {
                double s = 0;
                for (int i = r * perRow; i < Math.min(n, (r + 1) * perRow); i++) {
                    s += ratio[i];
                }
                widest = Math.max(widest, s);
            }
            int byW = (int) ((w - Theme.px(80) - gap * (perRow - 1)) / Math.max(0.1, widest));
            int byH = (int) ((h - headH - Theme.px(30)) / (double) rows - actsH);
            cardH = Math.min(Theme.px(430), Math.min(byW, byH));
            if (cardH >= Theme.px(220) || rows >= 3) {
                break;
            }
            rows++;
        }
        int blockH = rows * (cardH + actsH) + (rows - 1) * gap;
        int top = Math.max(headH, (h - blockH) / 2 + Theme.px(20));
        int slide = (int) Math.round((1 - a) * Theme.px(30));

        g.setFont(Theme.font(20, Font.BOLD));
        g.setColor(Color.WHITE);
        FontMetrics tf = g.getFontMetrics();
        g.drawString(title, (w - tf.stringWidth(title)) / 2, top - Theme.px(34));
        if (!subtitle.isEmpty()) {
            g.setFont(Theme.font(12, Font.PLAIN));
            g.setColor(new Color(255, 255, 255, 200));
            FontMetrics sf = g.getFontMetrics();
            g.drawString(subtitle, (w - sf.stringWidth(subtitle)) / 2, top - Theme.px(14));
        }

        for (int row = 0; row * perRow < n; row++) {
            int from = row * perRow;
            int to = Math.min(n, from + perRow);
            int rowW = (to - from - 1) * gap;
            for (int i = from; i < to; i++) {
                rowW += (int) Math.round(cardH * ratio[i]);
            }
            int x = (w - rowW) / 2;
            int y = top + row * (cardH + actsH + gap) + slide;
            for (int i = from; i < to; i++) {
                int cw = (int) Math.round(cardH * ratio[i]);
                paintCard(g, cards.get(i), x, y, cw, cardH, chipH);
                x += cw + gap;
            }
        }
        g.dispose();
    }

    private void paintCard(Graphics2D g, Card c, int x, int y, int w, int h, int chipH) {
        Rectangle r = new Rectangle(x, y, w, h);
        boolean hot = mouse != null && r.contains(mouse);
        int lift = hot ? Theme.px(8) : 0;
        r.y -= lift;
        cardRects.add(r);
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.x, r.y, w, h, w * 0.07, w * 0.07);
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(new RoundRectangle2D.Double(r.x + 4, r.y + 7, w, h, w * 0.07, w * 0.07));
        if (c.face() != null) {
            java.awt.Shape clip = g.getClip();
            g.clip(shape);
            kelium.report.Mips.draw(g, c.face(), r.x, r.y, w, h);
            g.setClip(clip);
        } else {
            g.setColor(Theme.panel());
            g.fill(shape);
            g.setColor(Theme.ink());
            g.setFont(Theme.font(15, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            int ty = r.y + Theme.px(34);
            for (String line : CardTile.wrap(c.name(), fm, w - Theme.px(24), 4)) {
                g.drawString(line, r.x + Theme.px(12), ty);
                ty += fm.getHeight();
            }
        }
        g.setColor(c.actions().isEmpty() ? new Color(255, 255, 255, 60) : accent);
        g.setStroke(new BasicStroke(c.actions().isEmpty() ? 1f : Theme.pxf(2.4)));
        g.draw(shape);
        int cy = r.y + h + Theme.px(8) + lift;
        if (c.note() != null) {
            g.setFont(Theme.font(13, Font.BOLD));
            g.setColor(new Color(255, 255, 255, 220));
            FontMetrics nf = g.getFontMetrics();
            String s = FieldBubbles.clip(nf, c.note(), w);
            g.drawString(s, x + (w - nf.stringWidth(s)) / 2, cy + nf.getAscent());
            cy += nf.getHeight() + Theme.px(4);
        }
        for (FieldBubbles.Opt o : c.actions()) {
            Rectangle cr = new Rectangle(x, cy, w, chipH);
            boolean hc = mouse != null && cr.contains(mouse);
            RoundRectangle2D rr = new RoundRectangle2D.Double(cr.x, cr.y, cr.width, cr.height,
                Theme.px(14), Theme.px(14));
            Color fill = o.tone() == 2 ? new Color(255, 255, 255, hc ? 70 : 40)
                : hc ? Theme.lighten(accent, 0.15) : accent;
            g.setColor(fill);
            g.fill(rr);
            g.setFont(Theme.font(16, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            String l = FieldBubbles.clip(fm, o.label(), w - Theme.px(16));
            if (o.sub() == null) {
                g.drawString(l, cr.x + (w - fm.stringWidth(l)) / 2,
                    cr.y + (chipH + fm.getAscent()) / 2 - Theme.px(2));
            } else {
                g.drawString(l, cr.x + (w - fm.stringWidth(l)) / 2, cr.y + Theme.px(3) + fm.getAscent());
                g.setFont(Theme.font(12, Font.PLAIN));
                FontMetrics sm = g.getFontMetrics();
                String s = FieldBubbles.clip(sm, o.sub(), w - Theme.px(16));
                g.setColor(new Color(255, 255, 255, 210));
                g.drawString(s, cr.x + (w - sm.stringWidth(s)) / 2, cr.y + chipH - Theme.px(5));
            }
            chips.put(cr, o);
            cy += chipH + Theme.px(6);
        }
    }
}
