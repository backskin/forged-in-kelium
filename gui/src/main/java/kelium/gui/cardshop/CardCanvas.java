package kelium.gui.cardshop;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ХОЛСТ КАРТЫ В ДВОЙНОМ РАЗМЕРЕ — те же приёмы, что у рисовальщика
 * {@code tools/gen_cards_from_blanks.py}: координаты задаются в пикселях
 * печатной карты, рисуется вдвое крупнее и в конце уменьшается.
 */
final class CardCanvas {

    static final int K = 2;
    static final Color WHITE = Color.WHITE;

    final CardAssets a;
    final BufferedImage im;
    final Graphics2D g;
    final FontRenderContext frc;

    CardCanvas(CardAssets a, BufferedImage template) {
        this.a = a;
        BufferedImage big = CardAssets.doubled(template, K);
        this.im = new BufferedImage(big.getWidth(), big.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D cg = im.createGraphics();
        cg.drawImage(big, 0, 0, null);
        cg.dispose();
        this.g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        frc = g.getFontRenderContext();
    }

    /** Карта холста — для своих элементов при завершении. */
    CardSpec card;
    Color freeInk = new Color(40, 40, 44);

    BufferedImage finish() {
        if (card != null) {
            CardRender.freeElements(this, card);
        }
        g.dispose();
        LAST_REGIONS.set(new java.util.LinkedHashMap<>(regions));
        return CardAssets.scale(im, im.getWidth() / K, im.getHeight() / K);
    }

    // ==================== элементы карты ====================
    //
    // ЭЛЕМЕНТ (дизайнер 05.10.2026: «на макете жмякнуть — и выделилась часть
    // панели»): часть карты со своей областью — верх, название, условие,
    // награда… Его можно сдвинуть, увеличить, сделать прозрачнее, скрыть,
    // поменять подложку под текстом. Правки — в поле карты «раскладка»
    // ({id: {dx, dy, масштаб, прозрачность, скрыт, подложка}}), поверх правок
    // каталога («_раскладка_типа»).

    /** Области элементов последней нарисованной карты (пиксели карты). */
    static final ThreadLocal<java.util.Map<String, Rectangle2D>> LAST_REGIONS = new ThreadLocal<>();

    private final java.util.Map<String, Rectangle2D> regions = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, java.util.Map<?, ?>> layout = new java.util.HashMap<>();
    private AffineTransform savedT;
    private java.awt.Composite savedC;
    private String current;

    /** Взять правки раскладки карты (каталог, потом сама карта). */
    void layout(CardSpec c) {
        for (String key : new String[] {"_раскладка_типа", "раскладка"}) {
            if (c.fields.get(key) instanceof java.util.Map<?, ?> m) {
                for (var e : m.entrySet()) {
                    if (e.getValue() instanceof java.util.Map<?, ?> st) {
                        java.util.Map<Object, Object> merged = new java.util.HashMap<>();
                        java.util.Map<?, ?> had = layout.get(String.valueOf(e.getKey()));
                        if (had != null) {
                            merged.putAll(had);
                        }
                        merged.putAll(st);
                        layout.put(String.valueOf(e.getKey()), merged);
                    }
                }
            }
        }
    }

    private double num(java.util.Map<?, ?> st, String k, double def) {
        return st != null && st.get(k) instanceof Number n ? n.doubleValue() : def;
    }

    /**
     * Начать элемент id с областью (x, y, w, h) в пикселях карты: дальше всё
     * рисуется сдвинутым, увеличенным вокруг центра области и с прозрачностью
     * элемента. false — элемент скрыт (рисовать не нужно); end() — всё равно.
     */
    boolean begin(String id, double x, double y, double w, double h) {
        java.util.Map<?, ?> st = layout.get(id);
        double dx = num(st, "dx", 0);
        double dy = num(st, "dy", 0);
        double s = num(st, "масштаб", 100) / 100.0;
        double alpha = num(st, "прозрачность", 0) / 100.0;
        boolean hidden = st != null && Boolean.TRUE.equals(st.get("скрыт"));
        double cx = x + w / 2;
        double cy = y + h / 2;
        regions.put(id, new Rectangle2D.Double(cx + dx - w * s / 2, cy + dy - h * s / 2, w * s, h * s));
        current = id;
        savedT = g.getTransform();
        savedC = g.getComposite();
        g.translate((cx + dx) * K, (cy + dy) * K);
        g.scale(s, s);
        g.translate(-cx * K, -cy * K);
        if (alpha > 0) {
            g.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, 1 - alpha)));
        }
        return !hidden;
    }

    void end() {
        if (savedT != null) {
            g.setTransform(savedT);
            g.setComposite(savedC);
        }
        savedT = null;
        current = null;
    }

    /** Непрозрачность белой подложки под текстом текущего элемента (0..255). */
    /** Кегль текста текущего элемента, множитель (поле «кегль», %, по умолчанию 100). */
    double size() {
        java.util.Map<?, ?> st = current == null ? null : layout.get(current);
        return Math.max(0.3, num(st, "кегль", 100) / 100.0);
    }

    /** Междустрочный интервал текущего элемента, множитель (поле «интервал», %). */
    double leading() {
        java.util.Map<?, ?> st = current == null ? null : layout.get(current);
        return Math.max(0.5, num(st, "интервал", 100) / 100.0);
    }

    /** Шаг иконок в стопке текущего элемента, доля ширины иконки (поле «шаг», %, 78). */
    double spacing() {
        java.util.Map<?, ?> st = current == null ? null : layout.get(current);
        return Math.max(0.1, num(st, "шаг", 78) / 100.0);
    }

    /** Ширина букв текущего элемента, множитель (поле «ширина_букв», %, по умолчанию 100). */
    double letterWidth() {
        java.util.Map<?, ?> st = current == null ? null : layout.get(current);
        return Math.max(0.3, num(st, "ширина_букв", 100) / 100.0);
    }

    int plate(int defPercent) {
        java.util.Map<?, ?> st = current == null ? null : layout.get(current);
        double p = num(st, "подложка", defPercent);
        return (int) Math.round(Math.max(0, Math.min(100, p)) * 2.55);
    }

    // ==================== шрифты и текст ====================

    Font font(String file, double px) {
        return a.font(file, px * K);
    }

    /** Ширина строки в пикселях холста. */
    double len(String s, Font f) {
        return f.getStringBounds(s, frc).getWidth();
    }

    /** Смещение базовой линии от «середины» строки (якорь m у PIL). */
    double midBaseline(Font f) {
        var lm = f.getLineMetrics("Нg", frc);
        return (lm.getAscent() - lm.getDescent()) / 2.0;
    }

    /**
     * Текст с обводкой. Координаты — пиксели холста; {@code ax}: 'l' 'm' 'r',
     * по вертикали всегда середина строки (как якоря lm/mm/rm у PIL).
     */
    void text(String s, Font f, double x, double y, char ax, Color fill, Color stroke,
              double strokePx) {
        if (s == null || s.isEmpty()) {
            return;
        }
        double w = len(s, f);
        double x0 = ax == 'm' ? x - w / 2 : ax == 'r' ? x - w : x;
        double base = y + midBaseline(f);
        GlyphVector gv = f.createGlyphVector(frc, s);
        Shape sh = gv.getOutline((float) x0, (float) base);
        if (stroke != null && strokePx > 0) {
            g.setColor(stroke);
            g.setStroke(new BasicStroke((float) (strokePx * 2), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            g.draw(sh);
        }
        g.setColor(fill);
        g.fill(sh);
    }

    /** Строка, сжатая по ширине, центром в (cx, cy) — пиксели карты. */
    void squeezed(String s, Font f, double cx, double cy, Color fill, Color stroke, double obv,
                  double squeeze, double maxW) {
        GlyphVector gv = f.createGlyphVector(frc, s);
        Shape sh = gv.getOutline(0, 0);
        Rectangle2D b = sh.getBounds2D();
        double pad = obv * K + 4;
        int w = (int) Math.ceil(b.getWidth() + pad * 2);
        int h = (int) Math.ceil(b.getHeight() + pad * 2);
        BufferedImage layer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D lg = layer.createGraphics();
        lg.setRenderingHints(g.getRenderingHints());
        lg.translate(pad - b.getX(), pad - b.getY());
        lg.setColor(stroke);
        lg.setStroke(new BasicStroke((float) (obv * K * 2), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        lg.draw(sh);
        lg.setColor(fill);
        lg.fill(sh);
        lg.dispose();
        double nw = w * squeeze;
        if (maxW > 0 && nw > maxW * K) {
            nw = maxW * K;
        }
        BufferedImage sc = CardAssets.scale(layer, (int) Math.round(nw), h);
        // центр по высоте — по середине заглавных
        Rectangle2D cap = f.createGlyphVector(frc, "Н").getOutline(0, 0).getBounds2D();
        double mid = pad - b.getY() + (cap.getY() + cap.getHeight() / 2);
        g.drawImage(sc, (int) (cx * K - sc.getWidth() / 2.0), (int) (cy * K - mid), null);
    }

    // ==================== иконки ====================

    BufferedImage icon(String token) {
        return a.icon(CardAssets.key(token));
    }

    /** Иконку центром в точку (пиксели карты), вписанную в w×h (пиксели карты). */
    void put(String token, double cx, double cy, double w, double h) {
        BufferedImage ic = icon(token);
        if (ic == null) {
            missing(cx, cy, w, h, token);
            return;
        }
        double s = CardAssets.lookScale(token);
        double[] sh = CardAssets.lookShift(token);
        BufferedImage f = CardAssets.fit(ic, w * K * s, h * K * s);
        g.drawImage(f, (int) ((cx + sh[0] * w) * K - f.getWidth() / 2.0),
            (int) ((cy + sh[1] * h) * K - f.getHeight() / 2.0), null);
    }

    void putImage(BufferedImage f, double cx, double cy) {
        g.drawImage(f, (int) (cx * K - f.getWidth() / 2.0), (int) (cy * K - f.getHeight() / 2.0),
            null);
    }

    /** Нет такой иконки — розовый квадрат с её именем: видно сразу, а не пустое место. */
    void missing(double cx, double cy, double w, double h, String token) {
        g.setColor(new Color(255, 0, 170, 160));
        g.fillRect((int) ((cx - w / 2) * K), (int) ((cy - h / 2) * K), (int) (w * K),
            (int) (h * K));
        text("?" + token, a.font("Tektur-Bold.ttf", 22), cx * K, cy * K, 'm', WHITE,
            Color.BLACK, 2);
    }

    // ==================== строка со словами и иконками ====================

    /** Кусок строки: слово (обычное/жирное) или иконка. */
    record Part(char kind, String value) {
    }

    private static final Pattern PARTS = Pattern.compile("(\\*\\*[^*]+\\*\\*|\\{[^}]+\\})");

    static List<Part> parts(String text) {
        List<Part> out = new ArrayList<>();
        Matcher m = PARTS.matcher(text);
        int at = 0;
        while (true) {
            boolean found = m.find();
            String plain = text.substring(at, found ? m.start() : text.length());
            for (String w : plain.trim().split("\\s+")) {
                if (!w.isEmpty()) {
                    out.add(new Part('t', w));
                }
            }
            if (!found) {
                break;
            }
            String t = m.group(1);
            if (t.startsWith("**")) {
                for (String w : t.substring(2, t.length() - 2).trim().split("\\s+")) {
                    if (!w.isEmpty()) {
                        out.add(new Part('b', w));
                    }
                }
            } else {
                out.add(new Part('i', t.substring(1, t.length() - 1)));
            }
            at = m.end();
        }
        return out;
    }

    static boolean sticks(Part p) {
        return p.kind() != 'i' && !p.value().isEmpty() && ",.;:!?»)".indexOf(p.value().charAt(0)) >= 0;
    }

    /** Как рисовать иконку в строке: размер в долях кегля и перекраска. */
    interface InlineIcons {
        BufferedImage make(String token, double px);
    }

    /**
     * Одна строка: слова и иконки. {@code x, y} — пиксели карты; якорь 'm' — центр,
     * 'r' — правый край, 'l' — левый. Возвращает ширину в пикселях карты.
     */
    double line(String text, Font f, Font fb, double x, double y, char ax, Color fill,
                Color stroke, double strokeW, InlineIcons icons, double px, boolean draw) {
        List<Part> ps = parts(text);
        double space = len(" ", f);
        List<Object> made = new ArrayList<>();
        List<Double> widths = new ArrayList<>();
        for (Part p : ps) {
            if (p.kind() == 'i') {
                BufferedImage ic = icons.make(p.value(), px);
                made.add(ic);
                widths.add(ic == null ? px * K : (double) ic.getWidth());
            } else {
                Font ff = p.kind() == 'b' ? fb : f;
                made.add(ff);
                widths.add(len(p.value(), ff));
            }
        }
        double total = 0;
        for (int i = 0; i < ps.size(); i++) {
            if (i > 0 && !sticks(ps.get(i))) {
                total += space;
            }
            total += widths.get(i);
        }
        if (draw) {
            double cx = x * K - (ax == 'm' ? total / 2 : ax == 'r' ? total : 0);
            for (int i = 0; i < ps.size(); i++) {
                if (i > 0 && !sticks(ps.get(i))) {
                    cx += space;
                }
                Part p = ps.get(i);
                if (p.kind() == 'i') {
                    BufferedImage ic = (BufferedImage) made.get(i);
                    if (ic == null) {
                        missing((cx + widths.get(i) / 2) / K, y, px, px, p.value());
                    } else {
                        g.drawImage(ic, (int) cx, (int) (y * K - ic.getHeight() / 2.0), null);
                    }
                } else {
                    text(p.value(), (Font) made.get(i), cx, y * K, 'l', fill, stroke,
                        strokeW * K);
                }
                cx += widths.get(i);
            }
        }
        return total / K;
    }

    // ==================== перекраска ====================

    /** Значок слота верха: бордовый → сланцево-синий, как на печати дизайнера. */
    static BufferedImage slate(BufferedImage ic) {
        int[] black = {25, 32, 60};
        int[] mid = {143, 153, 179};
        int[] white = {255, 255, 255};
        BufferedImage out = new BufferedImage(ic.getWidth(), ic.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ic.getHeight(); y++) {
            for (int x = 0; x < ic.getWidth(); x++) {
                int p = ic.getRGB(x, y);
                int al = p >>> 24;
                int r = (p >> 16) & 255;
                int gg = (p >> 8) & 255;
                int b = p & 255;
                double l = 0.299 * r + 0.587 * gg + 0.114 * b;
                int[] c = new int[3];
                for (int k = 0; k < 3; k++) {
                    c[k] = (int) Math.round(l <= 134 ? black[k] + (mid[k] - black[k]) * l / 134.0
                        : mid[k] + (white[k] - mid[k]) * (l - 134) / 121.0);
                }
                out.setRGB(x, y, (al << 24) | (c[0] << 16) | (c[1] << 8) | c[2]);
            }
        }
        return out;
    }

    /** Чёрную обводку иконки — в цвет обводки текста. */
    static BufferedImage recolorDark(BufferedImage ic, Color c) {
        BufferedImage out = new BufferedImage(ic.getWidth(), ic.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ic.getHeight(); y++) {
            for (int x = 0; x < ic.getWidth(); x++) {
                int p = ic.getRGB(x, y);
                int al = p >>> 24;
                int r = (p >> 16) & 255;
                int gg = (p >> 8) & 255;
                int b = p & 255;
                double dark = 1 - Math.max(r, Math.max(gg, b)) / 255.0;
                if (dark > 0.3) {
                    double t = Math.min(1, (dark - 0.3) / 0.5);
                    r = (int) Math.round(r + (c.getRed() - r) * t);
                    gg = (int) Math.round(gg + (c.getGreen() - gg) * t);
                    b = (int) Math.round(b + (c.getBlue() - b) * t);
                }
                out.setRGB(x, y, (al << 24) | (r << 16) | (gg << 8) | b);
            }
        }
        return out;
    }

    void overlay(BufferedImage layer, int x, int y) {
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(layer, x, y, null);
    }

    AffineTransform none() {
        return new AffineTransform();
    }
}
