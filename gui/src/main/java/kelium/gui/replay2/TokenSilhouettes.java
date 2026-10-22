package kelium.gui.replay2;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * СИЛУЭТЫ ЖЕТОНОВ НА ПЕЧАТНЫХ ПЛАНШЕТАХ — для подсветки ПО ФОРМЕ детали
 * (просьба дизайнера 26.09.2026: «выделение не прямоугольниками, а по форме
 * элемента; сзади подложка градиентом в прозрачность и рамка по его форме»).
 *
 * <p>Пока рисуется стол живой партии, планшеты записывают сюда, какая
 * картинка какой детали и под какой матрицей легла. Подсветка берёт ровно этот
 * силуэт: свечение позади, контур по краске, и поверх снова сам жетон.
 */
public final class TokenSilhouettes {

    private TokenSilhouettes() {
    }

    /** Картинка детали и её матрица на экране. */
    public record Entry(BufferedImage img, AffineTransform at) {
    }

    /** Детали последней отрисовки стола: ключ зоны щелчка → силуэт. */
    public static final Map<String, Entry> LAST = new LinkedHashMap<>();

    /** Пишется ли сейчас (только во время рисования стола живой партии). */
    static boolean recording;
    /**
     * Куда пишутся силуэты, пока {@link #recording}: стол живой партии — в
     * {@link #LAST}, увеличенный планшет — в свою карту (иначе он затирал бы
     * силуэты стола под собой).
     */
    static Map<String, Entry> into = LAST;

    static void put(String key, BufferedImage img, AffineTransform at) {
        if (recording && img != null && at != null) {
            into.put(key, new Entry(img, new AffineTransform(at)));
        }
    }

    /**
     * ПОПАЛА ЛИ ТОЧКА ЭКРАНА НА САМ ЖЕТОН — по непрозрачным пикселям его
     * картинки, а не по охвату: повёрнутый жетон крыла охватом накрывает
     * соседние жетоны (жалоба дизайнера 27.09.2026).
     */
    public static boolean contains(Entry e, double x, double y) {
        try {
            java.awt.geom.Point2D p = e.at().inverseTransform(
                new java.awt.geom.Point2D.Double(x, y), null);
            int px = (int) Math.floor(p.getX());
            int py = (int) Math.floor(p.getY());
            if (px < 0 || py < 0 || px >= e.img().getWidth() || py >= e.img().getHeight()) {
                return false;
            }
            return (e.img().getRGB(px, py) >>> 24) > 40;
        } catch (java.awt.geom.NoninvertibleTransformException ex) {
            return false;
        }
    }

    /**
     * МЯГКОЕ СВЕЧЕНИЕ ВОКРУГ ФОРМЫ — для деталей без картинки-силуэта (ячейки,
     * карты, гексы): несколько расширяющихся обводок всё прозрачнее, по краю —
     * контур той же формы. Размер — ровно по детали, без прямоугольных рамок.
     *
     * @param strong наведён курсор — свечение ярче и шире
     */
    public static void glowShape(Graphics2D g0, java.awt.Shape shape, Color c, boolean strong) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int passes = 6;
        float reach = Theme.pxf(strong ? 12 : 8);
        for (int i = passes; i >= 1; i--) {
            float wdt = reach * i / passes * 2;
            g.setColor(Theme.alpha(c, (strong ? 0.16 : 0.11) * (1.0 - (i - 1) / (double) passes)));
            g.setStroke(new java.awt.BasicStroke(wdt, java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
            g.draw(shape);
        }
        g.setColor(Theme.alpha(c, strong ? 0.16 : 0.07));
        g.fill(shape);
        // контур в два цвета: снаружи краска места, по самой кромке — светлая
        // линия, чтобы рамка не сливалась с деталью той же краски
        g.setColor(c);
        g.setStroke(new java.awt.BasicStroke(Theme.pxf(strong ? 5 : 3.6),
            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.setColor(Theme.alpha(Color.WHITE, 0.9));
        g.setStroke(new java.awt.BasicStroke(Theme.pxf(strong ? 1.8 : 1.3),
            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.dispose();
    }

    private static final Map<BufferedImage, Map<Integer, BufferedImage>> TINTS =
        new WeakHashMap<>();

    /** Силуэт картинки одним цветом (альфа сохраняется). */
    private static BufferedImage tint(BufferedImage src, Color c) {
        return TINTS.computeIfAbsent(src, k -> new java.util.HashMap<>())
            .computeIfAbsent(c.getRGB(), k -> {
                BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = out.createGraphics();
                g.drawImage(src, 0, 0, null);
                g.setComposite(AlphaComposite.SrcIn);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue()));
                g.fillRect(0, 0, src.getWidth(), src.getHeight());
                g.dispose();
                return out;
            });
    }

    /**
     * ПОДСВЕТКА ПО ФОРМЕ: мягкое свечение цвета {@code c} расходится от силуэта
     * в прозрачность, по краю — контур той же формы, поверх — сам жетон.
     *
     * @param strong наведён курсор — свечение ярче и шире
     */
    /** Силуэт, сдвинутый во все стороны на {@code t} (пикселей картинки), — рамка по форме. */
    private static void ring(Graphics2D g, BufferedImage s, AffineTransform base, double t) {
        for (int i = 0; i < 16; i++) {
            double ang = Math.PI * 2 * i / 16;
            AffineTransform at = new AffineTransform(base);
            at.translate(Math.cos(ang) * t, Math.sin(ang) * t);
            g.drawImage(s, at, null);
        }
    }

    public static void glow(Graphics2D g0, Entry e, Color c, boolean strong) {
        Graphics2D g = (Graphics2D) g0.create();
        BufferedImage s = tint(e.img(), c);
        double w = e.img().getWidth();
        double h = e.img().getHeight();
        double scr = Math.hypot(e.at().getScaleX(), e.at().getShearY());
        // свечение: несколько увеличенных копий силуэта от середины, всё
        // прозрачнее — градиент в прозрачность без размытия пикселей
        int passes = 7;
        double reach = (strong ? 22 : 14) / Math.max(0.0001, scr);   // в пикселях картинки
        for (int i = passes; i >= 1; i--) {
            double grow = reach * i / passes;
            double kx = (w + 2 * grow) / w;
            double ky = (h + 2 * grow) / h;
            AffineTransform at = new AffineTransform(e.at());
            at.translate(w / 2, h / 2);
            at.scale(kx, ky);
            at.translate(-w / 2, -h / 2);
            float a = (float) ((strong ? 0.30 : 0.20) * (1.0 - (i - 1) / (double) passes));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.drawImage(s, at, null);
        }
        // КОНТУР ПО ФОРМЕ В ДВА ЦВЕТА (27.09.2026): снаружи краска места,
        // вплотную к жетону — светлая кромка. Одной краской места рамка
        // сливалась с жетонами той же фракции, и наведение было не видно.
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        ring(g, s, e.at(), (strong ? 6.0 : 4.5) / Math.max(0.0001, scr));
        ring(g, tint(e.img(), Color.WHITE), e.at(), (strong ? 2.8 : 2.0) / Math.max(0.0001, scr));
        // сам жетон — поверх свечения и рамки
        kelium.report.Mips.draw(g, e.img(), e.at());
        g.dispose();
    }
}
