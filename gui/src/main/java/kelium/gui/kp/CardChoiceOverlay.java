package kelium.gui.kp;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * ЦЕРЕМОНИЯ ВЫБОРА КАРТЫ (запрос дизайнера 24.08: «в начале круга всплывают
 * все пять карт, и мы выбираем») — затемнение + КРУПНЫЕ лица карт приказов
 * веером по центру. Наведение приподнимает карту и показывает под веером её
 * печатное описание («как играется»); клик выбирает. Появление — как у
 * модалки (проявление + лёгкий подъезд карт).
 */
public final class CardChoiceOverlay extends JComponent {

    public record Card(String id, OrderCardFace.Info face, String title,
                        String description, Runnable onPick) {
    }

    private String title = "";
    private String subtitle = "";
    private final List<Card> cards = new ArrayList<>();
    private final Anim anim = new Anim();
    private int hoverIdx = -1;

    /** Навести на карту — для прогонщиков и тестов. */
    public void hoverForTest(int i) {
        hoverIdx = i;
        repaint();
    }

    public CardChoiceOverlay() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = cardAt(e.getPoint());
                if (i != hoverIdx) {
                    hoverIdx = i;
                    setCursor(i >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
                if (i < 0) {
                    pass(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int i = cardAt(e.getPoint());
                if (i >= 0 && !passing) {
                    cards.get(i).onPick().run();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Нажатие МИМО КАРТ — начало перетаскивания поля под шторкой
                // (жалоба дизайнера 27.09.2026: пока открыт выбор карты, поле
                // нельзя было ни сдвинуть, ни приблизить).
                passing = cardAt(e.getPoint()) < 0;
                if (passing) {
                    pass(e);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (passing) {
                    pass(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (passing) {
                    pass(e);
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        // колесо мимо карт — масштаб поля под шторкой
        addMouseWheelListener(e -> {
            if (cardAt(e.getPoint()) < 0) {
                pass(e);
            }
        });
    }

    /** Поле под шторкой: ему уходят нажатия, перетаскивание и колесо мимо карт. */
    private java.util.function.Supplier<java.awt.Component> passTo = () -> null;
    /** Идёт перетаскивание, начатое мимо карт, — его ведёт поле. */
    private boolean passing;

    public void setPassThrough(java.util.function.Supplier<java.awt.Component> c) {
        this.passTo = c == null ? () -> null : c;
    }

    /** Переслать событие мыши полю — в его координатах. */
    private void pass(MouseEvent e) {
        java.awt.Component c = passTo.get();
        if (c == null || !c.isShowing()) {
            return;
        }
        java.awt.Point p = javax.swing.SwingUtilities.convertPoint(this, e.getPoint(), c);
        if (!passing && !new Rectangle(c.getSize()).contains(p)) {
            return;
        }
        MouseEvent out = e instanceof java.awt.event.MouseWheelEvent w
            ? new java.awt.event.MouseWheelEvent(c, w.getID(), w.getWhen(), w.getModifiersEx(),
                p.x, p.y, w.getXOnScreen(), w.getYOnScreen(), w.getClickCount(), false,
                w.getScrollType(), w.getScrollAmount(),
                w.getWheelRotation(), w.getPreciseWheelRotation())
            : new MouseEvent(c, e.getID(), e.getWhen(), e.getModifiersEx(), p.x, p.y,
                e.getClickCount(), false, e.getButton());
        c.dispatchEvent(out);
    }

    /**
     * ВЫБРАННАЯ КАРТА — для разбора партии: там церемонию показывают уже
     * решённой, и выбранная карта отмечена, остальные пригашены. −1 — выбора
     * ещё нет (живая партия).
     */
    private int picked = -1;

    public void setPicked(int index) {
        picked = index;
        repaint();
    }

    public void open(String title, String subtitle, List<Card> newCards) {
        this.title = title;
        this.subtitle = subtitle == null ? "" : subtitle;
        cards.clear();
        cards.addAll(newCards);
        hoverIdx = -1;
        picked = -1;
        setVisible(true);
        anim.snap(0);
        anim.play(1, 180, v -> repaint(), null);
    }

    public void close() {
        anim.play(0, 120, v -> repaint(), () -> setVisible(false));
    }

    /** Карты (для прогонщиков/тестов). */
    public List<Card> cards() {
        return List.copyOf(cards);
    }

    // ==================== геометрия ====================

    private int cardW() {
        int n = Math.max(1, cards.size());
        int fit = (getWidth() - Theme.px(80)) / n + Theme.px(30);
        // КАРТЫ ПОМЕНЬШЕ (жалоба дизайнера 27.09.2026: «слишком крупные»): все
        // карты занимают около половины высоты поля, а не почти всё окно.
        // Мелкий текст читается в описании под веером.
        boolean printed = !cards.isEmpty() && art.apply(cards.get(0).id()) != null;
        int max = printed ? Theme.px(170) : Theme.px(140);
        int byHeight = (int) (getHeight() * 0.36 * ratio());
        return Math.max(Theme.px(80), Math.min(Math.min(max, byHeight), fit));
    }

    private int cardH() {
        return (int) Math.round(cardW() / ratio());
    }

    /**
     * Ширина к высоте — С ПЕЧАТНОГО ЛИЦА первой карты (карты одной колоды
     * одного формата); печати нет — пропорция карты приказа 661×1028.
     */
    private double ratio() {
        if (!cards.isEmpty()) {
            java.awt.image.BufferedImage img = art.apply(cards.get(0).id());
            if (img != null && img.getHeight() > 0) {
                return img.getWidth() / (double) img.getHeight();
            }
        }
        return 661 / 1028.0;
    }

    /** Печатное лицо карты по id (null — рисуем сами). */
    private java.util.function.Function<String, java.awt.image.BufferedImage> art = id -> null;

    public void setArt(java.util.function.Function<String, java.awt.image.BufferedImage> art) {
        this.art = art == null ? id -> null : art;
    }

    private Rectangle cardRect(int i) {
        int n = cards.size();
        int w = cardW();
        int step = n <= 1 ? 0
            : Math.min(w + Theme.px(10), (getWidth() - Theme.px(80) - w) / Math.max(1, n - 1));
        int total = w + step * (n - 1);
        int x0 = (getWidth() - total) / 2;
        int y = getHeight() / 2 - cardH() / 2 - Theme.px(30);
        int lift = i == hoverIdx ? Theme.px(18) : 0;
        return new Rectangle(x0 + i * step, y - lift, w, cardH());
    }

    private int cardAt(java.awt.Point p) {
        // проверяем С ПРАВА НАЛЕВО: правые карты веера лежат поверх левых
        for (int i = cards.size() - 1; i >= 0; i--) {
            if (cardRect(i).contains(p)) {
                return i;
            }
        }
        return -1;
    }

    // ==================== рисование ====================

    @Override
    protected void paintComponent(Graphics g0) {
        double a = anim.value();
        if (a <= 0.01) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // затемнение легче прежнего: поле под шторкой видно и его можно двигать
        g.setComposite(AlphaComposite.SrcOver.derive((float) (0.38 * a)));
        g.setColor(new Color(0x10, 0x14, 0x1A));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setComposite(AlphaComposite.SrcOver.derive((float) a));

        g.setFont(Theme.font(18, Font.BOLD));
        g.setColor(Color.WHITE);
        var fm = g.getFontMetrics();
        // Заголовок и подзаголовок стоят ВЫШЕ подъёма наведённой карты (18px),
        // иначе поднятая карта закрывала подпись (замечено на приёмке).
        g.drawString(title, (getWidth() - fm.stringWidth(title)) / 2,
            getHeight() / 2 - cardH() / 2 - Theme.px(74));
        if (!subtitle.isEmpty()) {
            g.setFont(Theme.font(12, Font.PLAIN));
            g.setColor(new Color(255, 255, 255, 200));
            var f2 = g.getFontMetrics();
            g.drawString(subtitle, (getWidth() - f2.stringWidth(subtitle)) / 2,
                getHeight() / 2 - cardH() / 2 - Theme.px(54));
        }

        int slide = (int) Math.round((1 - a) * Theme.px(24));
        for (int i = 0; i < cards.size(); i++) {
            if (i == hoverIdx) {
                continue;    // наведённая рисуется последней — поверх веера
            }
            paintCard(g, i, slide);
        }
        if (hoverIdx >= 0 && hoverIdx < cards.size()) {
            paintCard(g, hoverIdx, slide);
            paintDescription(g, cards.get(hoverIdx));
        } else if (picked >= 0 && picked < cards.size()) {
            paintDescription(g, cards.get(picked));
        }
        g.dispose();
    }

    /** Лица карт, сжатые и скруглённые под размер раскладки. */
    private final java.util.Map<java.awt.image.BufferedImage, java.awt.image.BufferedImage> sprites =
        new java.util.IdentityHashMap<>();

    private java.awt.image.BufferedImage sprite(java.awt.image.BufferedImage img, int w, int h,
                                                double dpr) {
        int pw = (int) Math.round(w * dpr);
        int ph = (int) Math.round(h * dpr);
        java.awt.image.BufferedImage got = sprites.get(img);
        if (got != null && got.getWidth() == pw && got.getHeight() == ph) {
            return got;
        }
        if (sprites.size() > 24) {
            sprites.clear();
        }
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(pw, ph,
            java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
        java.awt.Graphics2D gg = out.createGraphics();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gg.setClip(new java.awt.geom.RoundRectangle2D.Double(0, 0, pw, ph,
            Theme.px(10) * dpr, Theme.px(10) * dpr));
        kelium.report.Mips.draw(gg, img, 0, 0, pw, ph);
        gg.dispose();
        sprites.put(img, out);
        return out;
    }

    private void paintCard(Graphics2D g, int i, int slide) {
        Rectangle r = cardRect(i);
        r.y += slide;
        Card c = cards.get(i);
        g.setColor(new Color(0, 0, 0, 100));
        g.fillRoundRect(r.x + 3, r.y + 5, r.width, r.height, Theme.px(10), Theme.px(10));
        java.awt.image.BufferedImage img = art.apply(c.id());
        if (img != null) {
            // ПЕЧАТНОЕ ЛИЦО КАРТЫ — готовой картинкой под этот размер (29.09.2026:
            // сжатие с бикубикой через скруглённую маску на каждом кадре давало
            // ~2 ГБ мусора за партию и дёргало сборку мусора)
            double dpr = Math.max(1.0, g.getTransform().getScaleX());
            java.awt.image.BufferedImage spr = sprite(img, r.width, r.height, dpr);
            java.awt.geom.AffineTransform was = g.getTransform();
            java.awt.geom.Point2D at = was.transform(new java.awt.geom.Point2D.Double(r.x, r.y), null);
            g.setTransform(java.awt.geom.AffineTransform.getTranslateInstance(
                Math.round(at.getX()), Math.round(at.getY())));
            g.drawImage(spr, 0, 0, null);
            g.setTransform(was);
        } else if (c.face() != null) {
            OrderCardFace.paint(g, c.face(), r.x, r.y, r.width, r.height, false);
        } else {
            g.setColor(Theme.paper());
            g.fillRoundRect(r.x, r.y, r.width, r.height, Theme.px(10), Theme.px(10));
            g.setColor(Theme.border());
            g.drawRoundRect(r.x, r.y, r.width, r.height, Theme.px(10), Theme.px(10));
            g.setFont(Theme.font(12, Font.BOLD));
            g.setColor(Theme.ink());
            var fm = g.getFontMetrics();
            int ty = r.y + Theme.px(24);
            for (String line : CardTile.wrap(c.title(), fm, r.width - Theme.px(16), 4)) {
                g.drawString(line, r.x + Theme.px(8), ty);
                ty += fm.getHeight();
            }
        }
        if (picked >= 0 && i != picked) {
            g.setColor(new Color(0x10, 0x14, 0x1A, 120));
            g.fillRoundRect(r.x, r.y, r.width, r.height, Theme.px(10), Theme.px(10));
        }
        if (i == hoverIdx || i == picked) {
            g.setColor(Theme.accent());
            g.setStroke(new BasicStroke(Theme.pxf(i == picked ? 4.0 : 2.4)));
            g.drawRoundRect(r.x - 1, r.y - 1, r.width + 2, r.height + 2,
                Theme.px(11), Theme.px(11));
        }
        if (i == picked) {
            String tag = "ВЫБРАНО";
            g.setFont(Theme.font(13, Font.BOLD));
            var fm = g.getFontMetrics();
            int tw = fm.stringWidth(tag) + Theme.px(18);
            int th = fm.getHeight() + Theme.px(6);
            int tx = r.x + (r.width - tw) / 2;
            int ty = r.y - th / 2;
            g.setColor(Theme.accent());
            g.fillRoundRect(tx, ty, tw, th, th, th);
            g.setColor(new Color(0x10, 0x14, 0x1A));
            g.drawString(tag, tx + Theme.px(9), ty + Theme.px(3) + fm.getAscent());
        }
    }

    /** Печатное описание карты («как играется») — под веером, на тёмном. */
    private void paintDescription(Graphics2D g, Card c) {
        if (c.description() == null || c.description().isBlank()) {
            return;
        }
        int w = Math.min(Theme.px(680), getWidth() - Theme.px(60));
        int x = (getWidth() - w) / 2;
        int y = getHeight() / 2 + cardH() / 2 + Theme.px(4);
        g.setFont(Theme.font(12, Font.PLAIN));
        var fm = g.getFontMetrics();
        // ТЕКСТ КАРТЫ ИДЁТ АБЗАЦАМИ: печатный текст, награда, усиленная,
        // утиль. Слепить их в один ком нельзя — «НАГРАДА» прочтётся
        // продолжением условия.
        List<String> lines = new ArrayList<>();
        int влезет = Math.max(4, (getHeight() - y - Theme.px(40)) / fm.getHeight());
        for (String абзац : c.description().split(String.valueOf((char) 10))) {
            if (абзац.isBlank()) {
                continue;
            }
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.addAll(CardTile.wrap(абзац, fm, w - Theme.px(24), 12));
            if (lines.size() >= влезет) {
                break;
            }
        }
        if (lines.size() > влезет) {
            lines = new ArrayList<>(lines.subList(0, влезет));
        }
        int h = lines.size() * fm.getHeight() + Theme.px(30);
        g.setColor(new Color(0x1B, 0x1F, 0x26, 235));
        g.fillRoundRect(x, y, w, h, Theme.px(10), Theme.px(10));
        g.setFont(Theme.font(11, Font.BOLD));
        g.setColor(new Color(255, 255, 255, 210));
        g.drawString(c.title(), x + Theme.px(12), y + Theme.px(16));
        g.setFont(Theme.font(12, Font.PLAIN));
        g.setColor(new Color(255, 255, 255, 235));
        int ty = y + Theme.px(30) + fm.getAscent() - fm.getHeight();
        for (String line : lines) {
            ty += fm.getHeight();
            g.drawString(line, x + Theme.px(12), ty);
        }
    }
}
