package kelium.report;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mips — ПИРАМИДА УМЕНЬШЕННЫХ КОПИЙ КАРТИНКИ (мипмапы).
 *
 * <p>ЗАЧЕМ. Жетон нарисован в 736 пикселей шириной, а на поле его рисуют в 68 —
 * и при отъезде камеры в 20. Java2D умеет только «ближайший» и «билинейный»
 * фильтры, а билинейный берёт ЧЕТЫРЕ исходных пикселя на один экранный: при
 * сжатии в десять раз в дело идёт четыре пикселя из сотни, а остальные девяносто
 * шесть просто выбрасываются. Отсюда рябь, дрожь и «мыло» на мелком масштабе —
 * то, что глаз читает как «нарисовано ближайшим соседом» (замечание дизайнера
 * 08.09.2026).
 *
 * <p>КАК ЛЕЧИТСЯ. Тем же приёмом, что в играх: заранее строится цепочка копий,
 * каждая вдвое меньше предыдущей. Уменьшение вдвое билинейным фильтром — это
 * усреднение по четырём соседям, то есть НИ ОДИН пиксель не теряется. Рисуя,
 * берём уровень, который уже близок к нужному размеру, и досжимаем его
 * билинейно совсем чуть-чуть. Получается та же чистая картинка, что даёт
 * трилинейная фильтрация; анизотропная в Java2D недоступна вовсе, а при наших
 * поворотах на 60° она бы и не понадобилась.
 *
 * <p>Копии живут в слабой карте по САМОЙ картинке: текстуры {@link Textures}
 * долгоживущие, поэтому цепочка строится один раз, а если картинку выкинут —
 * уйдёт и цепочка.
 */
public final class Mips {

    /** Ниже этого не мельчим: восемь пикселей — уже не картинка. */
    private static final int МИНИМУМ = 8;

    private static final Map<BufferedImage, List<BufferedImage>> CACHE = new WeakHashMap<>();

    private Mips() {
    }

    /**
     * Уровень пирамиды под нужную ЭКРАННУЮ ширину.
     *
     * <p>Берётся самый мелкий уровень, который ещё НЕ МЕНЬШЕ нужного: досжать
     * его билинейно — честно, а растягивать обратно значило бы терять резкость
     * там, где картинка и так крупная.
     *
     * @param targetW сколько пикселей на экране займёт вся картинка
     */
    static synchronized BufferedImage forWidth(BufferedImage src, double targetW) {
        if (src == null || targetW <= 0 || src.getWidth() <= МИНИМУМ
                || targetW >= src.getWidth() * 0.7) {
            return src;                 // крупный показ — рисуем сам исходник
        }
        List<BufferedImage> chain = CACHE.get(src);
        if (chain == null) {
            chain = построить(src);
            CACHE.put(src, chain);
        }
        BufferedImage best = src;
        for (BufferedImage lvl : chain) {
            if (lvl.getWidth() >= targetW) {
                best = lvl;
            } else {
                break;
            }
        }
        return best;
    }

    /**
     * НАРИСОВАТЬ КАРТИНКУ В ПРЯМОУГОЛЬНИК СГЛАЖЕННО — уровнем пирамиды под
     * экранный размер и билинейно (жалоба дизайнера 26.09.2026: стол игрока
     * «пиксельный, пестрящий» — большие картинки ужимались в разы одним шагом,
     * и билинейная выборка из четырёх соседей давала рябь). Масштаб самого
     * экрана (HiDPI) учитывается: уровень берётся под настоящие пиксели.
     */
    public static void draw(java.awt.Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
        if (img == null || w <= 0 || h <= 0) {
            return;
        }
        double device = Math.abs(g.getTransform().getScaleX());
        BufferedImage lvl = forWidth(img, w * (device > 0 ? device : 1));
        Object was = g.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(lvl, x, y, w, h, null);
        if (was != null) {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, was);
        }
    }

    /** То же для картинки под матрицей (поворот, наклон): уровень под её масштаб. */
    public static void draw(java.awt.Graphics2D g, BufferedImage img,
                            java.awt.geom.AffineTransform at) {
        if (img == null) {
            return;
        }
        double k = Math.hypot(at.getScaleX(), at.getShearY());
        double device = Math.abs(g.getTransform().getScaleX());
        BufferedImage lvl = forWidth(img, img.getWidth() * k * (device > 0 ? device : 1));
        java.awt.geom.AffineTransform use = new java.awt.geom.AffineTransform(at);
        if (lvl != img) {
            double s = img.getWidth() / (double) lvl.getWidth();
            use.scale(s, s);
        }
        Object was = g.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(lvl, use, null);
        if (was != null) {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, was);
        }
    }

    private static List<BufferedImage> построить(BufferedImage src) {
        List<BufferedImage> out = new ArrayList<>();
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= МИНИМУМ && cur.getHeight() / 2 >= 1) {
            int w = Math.max(1, cur.getWidth() / 2);
            int h = Math.max(1, cur.getHeight() / 2);
            BufferedImage next = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(cur, 0, 0, w, h, null);
            g.dispose();
            out.add(next);
            cur = next;
        }
        return out;
    }

    /** Забыть пирамиды — при смене папки текстур. */
    static synchronized void forget() {
        CACHE.clear();
    }
}
