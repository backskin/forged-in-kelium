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

    static void put(String key, BufferedImage img, AffineTransform at) {
        if (recording && img != null && at != null) {
            LAST.put(key, new Entry(img, new AffineTransform(at)));
        }
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
    public static void glow(Graphics2D g0, Entry e, Color c, boolean strong) {
        Graphics2D g = (Graphics2D) g0.create();
        BufferedImage s = tint(e.img(), c);
        double w = e.img().getWidth();
        double h = e.img().getHeight();
        double scr = Math.hypot(e.at().getScaleX(), e.at().getShearY());
        // свечение: несколько увеличенных копий силуэта от середины, всё
        // прозрачнее — градиент в прозрачность без размытия пикселей
        int passes = 7;
        double reach = (strong ? 16 : 11) / Math.max(0.0001, scr);   // в пикселях картинки
        for (int i = passes; i >= 1; i--) {
            double grow = reach * i / passes;
            double kx = (w + 2 * grow) / w;
            double ky = (h + 2 * grow) / h;
            AffineTransform at = new AffineTransform(e.at());
            at.translate(w / 2, h / 2);
            at.scale(kx, ky);
            at.translate(-w / 2, -h / 2);
            float a = (float) ((strong ? 0.20 : 0.14) * (1.0 - (i - 1) / (double) passes));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.drawImage(s, at, null);
        }
        // контур по форме: силуэт, сдвинутый во все стороны на толщину рамки
        double t = (strong ? 3.2 : 2.4) / Math.max(0.0001, scr);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        for (int i = 0; i < 12; i++) {
            double ang = Math.PI * 2 * i / 12;
            AffineTransform at = new AffineTransform(e.at());
            at.translate(Math.cos(ang) * t, Math.sin(ang) * t);
            g.drawImage(s, at, null);
        }
        // сам жетон — поверх свечения и рамки
        kelium.report.Mips.draw(g, e.img(), e.at());
        g.dispose();
    }
}
