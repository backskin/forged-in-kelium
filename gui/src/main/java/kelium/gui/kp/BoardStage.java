package kelium.gui.kp;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * СЦЕНА ПЛАНШЕТА (дизайнер 28.09.2026) — печатный планшет крупно, поверх поля,
 * без подложки и полос прокрутки.
 *
 * <ul>
 *   <li><b>Решение Рынка или Науки:</b> поле уходит в тень, планшет стоит
 *       спереди, рядом — крупные кнопки вариантов. Наведение на кнопку
 *       подсвечивает её место на планшете; щелчок по подсвеченному месту
 *       выбирает так же, как кнопка. «Что покупаю и за что» видно на самом
 *       планшете, а не угадывается по строке.</li>
 *   <li><b>Просмотр</b> (кнопки «Научный отдел», «Рынок», «Памятка» слева):
 *       планшет выезжает слева и лежит поверх поля; щелчок мимо закрывает.</li>
 * </ul>
 *
 * <p>Места вариантов задаются в пикселях самой картинки планшета — сцена
 * пересчитывает их под свой масштаб.
 */
public final class BoardStage extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Рисует доску с живым состоянием в прямоугольник экрана. */
    public interface Painter {
        void paint(Graphics2D g, int x, int y, int w, int h);
    }

    /** Одна доска: размер картинки (для пропорций и мест) и чем её рисовать. */
    public record Board(int artW, int artH, Painter painter) {
    }

    /**
     * Вариант решения: подпись, пояснение, место на доске {@code board}
     * (в пикселях её картинки, или null — только кнопкой), вид кнопки
     * (0 — обычная, 2 — отказ), что сделать при выборе.
     */
    public record Opt(String label, String sub, int board, Shape area, int tone, Runnable pick) {
    }

    private List<Board> boards = List.of();
    private String title;
    private String hint;
    private List<Opt> opts = List.of();
    private Color seat = Theme.accent();
    private boolean decision;
    private Runnable onClose;

    /** Где что легло на последней отрисовке. */
    private final Map<Rectangle, Opt> chipHits = new LinkedHashMap<>();
    private final Map<Shape, Opt> areaHits = new LinkedHashMap<>();
    private Rectangle boardsBox;
    private Opt hover;

    private final Anim slide = new Anim();

    public BoardStage() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Opt h = at(e.getX(), e.getY());
                if (h != hover) {
                    hover = h;
                    repaint();
                }
                setCursor(Cursor.getPredefinedCursor(h != null || !decision
                    ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hover != null) {
                    hover = null;
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                Opt o = at(e.getX(), e.getY());
                if (o != null && o.pick() != null) {
                    o.pick().run();
                    return;
                }
                // просмотр закрывается щелчком мимо планшета и по нему
                if (!decision) {
                    close();
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    /** Отложенное закрытие: сделки подряд не мигают сценой. */
    private final javax.swing.Timer closer = new javax.swing.Timer(160, e -> close());

    {
        closer.setRepeats(false);
    }

    /** Закрыть, если за миг не придёт следующий вопрос на сцену. */
    public void closeSoon() {
        if (isVisible()) {
            closer.restart();
        }
    }

    /** Показ решения: доски, вопрос, варианты. */
    public void decide(List<Board> boards, String title, String hint, List<Opt> opts, Color seat) {
        closer.stop();
        boolean was = deciding();
        this.boards = List.copyOf(boards);
        this.title = title;
        this.hint = hint;
        this.opts = List.copyOf(opts);
        this.seat = seat == null ? Theme.accent() : seat;
        this.decision = true;
        this.hover = null;
        this.onClose = null;
        appear(was);
    }

    /** Просмотр досок по кнопке; {@code onClose} — снять выделение кнопки. */
    public void browse(List<Board> boards, String title, Runnable onClose) {
        closer.stop();
        this.boards = List.copyOf(boards);
        // шапки нет: какая доска открыта, видно по нажатой кнопке слева, а
        // подпись поверх поля наезжала на карточку вопроса
        this.title = null;
        this.hint = null;
        this.opts = List.of();
        this.seat = Theme.accent();
        this.decision = false;
        this.hover = null;
        this.onClose = onClose;
        appear(false);
    }

    /** Сейчас идёт просмотр (а не решение). */
    public boolean browsing() {
        return isVisible() && !decision && !ending;
    }

    /** Сейчас показано решение. */
    public boolean deciding() {
        return isVisible() && decision && !ending;
    }

    /** Сцена уже уезжает: её варианты ничего не решают. */
    private boolean ending;

    private void appear(boolean already) {
        ending = false;
        setVisible(true);
        if (already) {
            repaint();
            return;
        }
        slide.snap(0);
        slide.play(1, 170, v -> repaint(), null);
    }

    /** Убрать сцену (решение принято или просмотр закрыт). */
    public void close() {
        closer.stop();
        if (!isVisible() || ending) {
            return;
        }
        ending = true;
        hover = null;
        Runnable after = onClose;
        onClose = null;
        slide.play(0, 130, v -> repaint(), () -> {
            setVisible(false);
            boards = List.of();
            opts = List.of();
        });
        if (after != null) {
            after.run();
        }
    }

    /** Варианты решения на сцене (пусто, если сцена не решает) — для тестов. */
    public List<Opt> optsForTest() {
        return deciding() && !closer.isRunning() ? opts : List.of();
    }

    /** Варианты на сцене — для прогонщиков и тестов. */
    public List<String> labelsForTest() {
        List<String> out = new ArrayList<>();
        for (Opt o : opts) {
            out.add(o.label());
        }
        return out;
    }

    private Opt at(int x, int y) {
        if (ending) {
            return null;     // сцена уезжает — её кнопки уже ничего не решают
        }
        for (Map.Entry<Rectangle, Opt> e : chipHits.entrySet()) {
            if (e.getKey().contains(x, y)) {
                return e.getValue();
            }
        }
        for (Map.Entry<Shape, Opt> e : areaHits.entrySet()) {
            if (e.getKey().contains(x, y)) {
                return e.getValue();
            }
        }
        return null;
    }

    @Override
    public boolean contains(int x, int y) {
        // сцена ловит мышь целиком, пока видна: поле под тенью не щёлкается
        return isVisible();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (boards.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double k = slide.value();
        int w = getWidth();
        int h = getHeight();
        chipHits.clear();
        areaHits.clear();

        // тень на поле: при решении глубже — всё внимание на планшет
        g.setColor(new Color(0x06, 0x12, 0x18, (int) Math.round((decision ? 170 : 150) * k)));
        g.fillRect(0, 0, w, h);
        g.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, Math.min(1, k))));

        int pad = Theme.px(18);
        int gap = Theme.px(16);
        // шапка: вопрос и пояснение
        Font tf = Theme.font(20, Font.BOLD);
        Font hf = Theme.font(14, Font.PLAIN);
        FontMetrics tm = getFontMetrics(tf);
        FontMetrics hm = getFontMetrics(hf);
        int chipsW = decision && !opts.isEmpty() ? Math.min(Theme.px(380), w / 3) : 0;
        int textMax = w - 2 * pad - chipsW;
        List<String> tl = title == null ? List.of() : FieldBubbles.wrap(tm, title, textMax, 2);
        List<String> hl = hint == null ? List.of() : FieldBubbles.wrap(hm, hint, textMax, 3);
        int headH = tl.size() * tm.getHeight() + hl.size() * hm.getHeight();
        int slideX = (int) Math.round((1 - k) * -Theme.px(60));

        // место под доски
        int areaX = pad;
        int areaY = pad + headH + (headH > 0 ? Theme.px(10) : 0);
        int areaW = w - 2 * pad - (chipsW > 0 ? chipsW + gap : 0);
        // просмотр: под доской строка «щелчок — закрыть»
        int footH = decision ? 0 : hm.getHeight() + Theme.px(6);
        int areaH = h - areaY - pad - footH;
        double sumAsp = 0;
        for (Board b : boards) {
            sumAsp += b.artW() / (double) b.artH();
        }
        int bh = (int) Math.min(areaH, (areaW - gap * (boards.size() - 1)) / sumAsp);
        int totalW = (int) Math.round(bh * sumAsp) + gap * (boards.size() - 1);
        int bx = areaX + Math.max(0, (areaW - totalW) / 2) + slideX;
        int by = areaY + Math.max(0, (areaH - bh) / 2);
        boardsBox = new Rectangle(bx, by, totalW, bh);

        // шапка — над досками, по их левому краю
        int ty = by - (headH > 0 ? Theme.px(10) : 0) - headH + tm.getAscent();
        g.setFont(tf);
        for (String s : tl) {
            g.setColor(Color.WHITE);
            g.drawString(s, bx, ty);
            ty += tm.getHeight();
        }
        g.setFont(hf);
        g.setColor(Theme.ink2());
        ty += hm.getAscent() - tm.getAscent();
        for (String s : hl) {
            g.drawString(s, bx, ty);
            ty += hm.getHeight();
        }

        // доски и места вариантов на них
        int x = bx;
        List<AffineTransform> toScreen = new ArrayList<>();
        for (Board b : boards) {
            int bw = (int) Math.round(bh * b.artW() / (double) b.artH());
            // только сама картонка: у досок фигурный край, и любая прямоугольная
            // тень под ней читалась как подложка
            b.painter().paint(g, x, by, bw, bh);
            AffineTransform t = new AffineTransform();
            t.translate(x, by);
            t.scale(bw / (double) b.artW(), bh / (double) b.artH());
            toScreen.add(t);
            x += bw + gap;
        }
        if (!decision) {
            g.setFont(hf);
            g.setColor(Theme.ink2());
            String s = "щелчок — закрыть";
            g.drawString(s, bx + (totalW - hm.stringWidth(s)) / 2,
                by + bh + Theme.px(6) + hm.getAscent());
        }
        if (decision) {
            for (Opt o : opts) {
                if (o.area() == null || o.board() < 0 || o.board() >= toScreen.size()) {
                    continue;
                }
                Shape s = toScreen.get(o.board()).createTransformedShape(o.area());
                areaHits.put(s, o);
                kelium.gui.replay2.TokenSilhouettes.glowShape(g, s, seat, o == hover);
            }
        }

        // кнопки вариантов — столбцом справа от досок
        if (chipsW > 0) {
            Font cf = Theme.font(16, Font.BOLD);
            Font sf = Theme.font(13, Font.PLAIN);
            FontMetrics cm = getFontMetrics(cf);
            FontMetrics sm = getFontMetrics(sf);
            int cx = Math.min(w - pad - chipsW, boardsBox.x + boardsBox.width + gap);
            int chipGap = Theme.px(8);
            List<int[]> sizes = new ArrayList<>();
            int total = 0;
            for (Opt o : opts) {
                List<String> ll = FieldBubbles.wrap(cm, o.label(), chipsW - Theme.px(28), 2);
                List<String> sl = o.sub() == null || o.sub().isBlank() ? List.of()
                    : FieldBubbles.wrap(sm, o.sub(), chipsW - Theme.px(28), 2);
                int ch = Theme.px(14) + ll.size() * cm.getHeight() + sl.size() * sm.getHeight();
                sizes.add(new int[]{ch, ll.size(), sl.size()});
                total += ch + chipGap;
            }
            int cy = Math.max(by, by + (bh - total) / 2);
            for (int i = 0; i < opts.size(); i++) {
                Opt o = opts.get(i);
                int ch = sizes.get(i)[0];
                boolean hot = o == hover;
                RoundRectangle2D r = new RoundRectangle2D.Double(cx, cy, chipsW, ch,
                    Theme.px(12), Theme.px(12));
                Color fill = o.tone() == 2 ? (hot ? Theme.hover() : Theme.tile())
                    : hot ? Theme.lighten(seat, 0.10) : Theme.alpha(Theme.panel(), 0.96);
                g.setColor(fill);
                g.fill(r);
                g.setColor(o.tone() == 2 ? Theme.border() : seat);
                g.setStroke(new BasicStroke(Theme.pxf(hot ? 2.6 : 1.6)));
                g.draw(r);
                if (o.area() != null && o.tone() != 2) {
                    // значок «место на планшете» — кнопка связана с местом доски
                    g.setColor(seat);
                    g.fillOval(cx + Theme.px(8), cy + ch / 2 - Theme.px(4), Theme.px(8), Theme.px(8));
                }
                int lx = cx + Theme.px(22);
                int ly = cy + Theme.px(7) + cm.getAscent();
                g.setFont(cf);
                g.setColor(hot && o.tone() != 2 ? Color.WHITE : o.tone() == 2 ? Theme.ink2() : Theme.ink());
                for (String s : FieldBubbles.wrap(cm, o.label(), chipsW - Theme.px(28), 2)) {
                    g.drawString(s, lx, ly);
                    ly += cm.getHeight();
                }
                if (o.sub() != null && !o.sub().isBlank()) {
                    g.setFont(sf);
                    g.setColor(hot && o.tone() != 2 ? new Color(255, 255, 255, 220) : Theme.ink2());
                    ly += sm.getAscent() - cm.getAscent();
                    for (String s : FieldBubbles.wrap(sm, o.sub(), chipsW - Theme.px(28), 2)) {
                        g.drawString(s, lx, ly);
                        ly += sm.getHeight();
                    }
                }
                chipHits.put(new Rectangle(cx, cy, chipsW, ch), o);
                cy += ch + chipGap;
            }
        }
        g.dispose();
    }

    /** Прямоугольник с доли картинки — удобство для мест вариантов. */
    public static Shape box(double x, double y, double w, double h) {
        return new RoundRectangle2D.Double(x, y, w, h, Math.min(w, h) * 0.18, Math.min(w, h) * 0.18);
    }

    /** Картинка, вписанная как доска (памятка и другие листы без живого состояния). */
    public static Board image(BufferedImage img) {
        return new Board(img.getWidth(), img.getHeight(),
            (g, x, y, w, h) -> kelium.report.Mips.draw(g, img, x, y, w, h));
    }
}
