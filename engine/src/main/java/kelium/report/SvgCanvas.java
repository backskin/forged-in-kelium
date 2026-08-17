package kelium.report;

import java.util.Locale;

/**
 * SvgCanvas — вывод {@link FieldCanvas} в текст SVG (картинки в отчётах).
 *
 * <p>Печатает ровно тот же синтаксис, что печатал прежний рендер: тесты
 * геометрии разбирают строку {@code transform} по частям, и менять её форму
 * нельзя.
 */
public final class SvgCanvas implements FieldCanvas {

    /**
     * ТОЛЩИНА ОБВОДКИ ЦИФР — доля от кегля. То же значение, что в
     * {@link Java2DCanvas}: картинка отчёта и живое окно обязаны выглядеть
     * одинаково, иначе скриншот перестаёт что-либо доказывать.
     */
    private static final double OUTLINE_WEIGHT = 0.16;


    private final StringBuilder sb;
    private double alpha = 1;

    public SvgCanvas(StringBuilder sb) {
        this.sb = sb;
    }

    private String op() {
        return alpha >= 1 ? "" : String.format(Locale.ROOT, " opacity='%.2f'", alpha);
    }

    @Override
    public void alpha(double value) {
        this.alpha = value;
    }

    /** Сколько обрезок уже объявлено — из этого получается их уникальное имя. */
    private int clips;

    @Override
    public void clipTo(double[][] points) {
        StringBuilder pts = new StringBuilder();
        for (double[] p : points) {
            pts.append(String.format(Locale.ROOT, "%.1f,%.1f ", p[0], p[1]));
        }
        clips++;
        sb.append(String.format(Locale.ROOT,
            "<clipPath id='clip%d'><polygon points='%s'/></clipPath>%n"
            + "<g clip-path='url(#clip%d)'>%n", clips, pts.toString().trim(), clips));
    }

    @Override
    public void clipToShape(FieldGeometry.Shape shape, double cx, double cy, double rotDeg,
                            double k, double anchorX, double anchorY) {
        clips++;
        sb.append(String.format(Locale.ROOT,
            "<clipPath id='clip%d'><path d='%s' transform='translate(%.1f,%.1f) "
            + "rotate(%.1f) scale(%.5f) translate(%.1f,%.1f)'/></clipPath>%n"
            + "<g clip-path='url(#clip%d)'>%n",
            clips, shape.d(), cx, cy, rotDeg, k, -anchorX, -anchorY, clips));
    }

    @Override
    public void clipOff() {
        sb.append("</g>\n");
    }

    @Override
    public void polygon(double[][] points, String fill, String stroke, double strokeWidth) {
        StringBuilder pts = new StringBuilder();
        for (double[] p : points) {
            pts.append(String.format(Locale.ROOT, "%.1f,%.1f ", p[0], p[1]));
        }
        sb.append(String.format(Locale.ROOT,
            "<polygon points='%s' fill='%s' stroke='%s' stroke-width='%.1f' "
            + "stroke-linejoin='round'%s/>%n",
            pts.toString().trim(), fill, stroke, strokeWidth, op()));
    }

    @Override
    public void shape(FieldGeometry.Shape shape, double cx, double cy, double rotDeg,
                      double k, double anchorX, double anchorY,
                      String fill, String stroke, double strokeWidth) {
        sb.append(String.format(Locale.ROOT,
            "<g transform='translate(%.1f,%.1f) rotate(%.1f) scale(%.5f) "
            + "translate(%.1f,%.1f)'><path d='%s' fill='%s' stroke='%s' "
            + "stroke-width='%.0f' stroke-linejoin='round'%s/></g>%n",
            cx, cy, rotDeg, k, -anchorX, -anchorY, shape.d(), fill, stroke,
            Math.max(30, strokeWidth / k), op()));
    }

    /**
     * Текстура в SVG — картинкой, вшитой в сам файл (data-URI). Отдельные PNG рядом
     * с отчётом заводить нельзя: картинки уезжают в документы по одной, и ссылка на
     * соседний файл там сразу рвётся.
     */
    @Override
    public void image(java.awt.image.BufferedImage img, double cx, double cy, double rotDeg,
                      double k, double anchorX, double anchorY) {
        String data;
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            data = java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (java.io.IOException e) {
            return;                    // картинка не упаковалась — просто её не будет
        }
        sb.append(String.format(Locale.ROOT,
            "<g transform='translate(%.1f,%.1f) rotate(%.1f) scale(%.5f) "
            + "translate(%.1f,%.1f)'><image width='%d' height='%d' "
            + "xlink:href='data:image/png;base64,%s'%s/></g>%n",
            cx, cy, rotDeg, k, -anchorX, -anchorY, img.getWidth(), img.getHeight(),
            data, op()));
    }

    @Override
    public void roundRect(double x, double y, double w, double h, double radius,
                          String fill, String stroke, double strokeWidth) {
        sb.append(String.format(Locale.ROOT,
            "<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' rx='%.1f' fill='%s' "
            + "stroke='%s' stroke-width='%.1f'%s/>%n",
            x, y, w, h, radius, fill, stroke, strokeWidth, op()));
    }

    @Override
    public void circle(double cx, double cy, double r, String fill, String stroke,
                       double strokeWidth) {
        sb.append(String.format(Locale.ROOT,
            "<circle cx='%.1f' cy='%.1f' r='%.1f' fill='%s' stroke='%s' "
            + "stroke-width='%.1f'%s/>%n", cx, cy, r, fill, stroke, strokeWidth, op()));
    }

    @Override
    public void text(String text, double cx, double baselineY, double size,
                     boolean bold, String fill) {
        sb.append(String.format(Locale.ROOT,
            "<text x='%.1f' y='%.1f' text-anchor='middle' font-size='%.1f'%s fill='%s'>%s</text>%n",
            cx, baselineY, size, bold ? " font-weight='bold'" : "", fill, esc(text)));
    }

    @Override
    public void outlinedText(String text, double cx, double baselineY, double size,
                             String fill, String outline) {
        sb.append(String.format(Locale.ROOT,
            "<text x='%.1f' y='%.1f' text-anchor='middle' font-size='%.1f' font-weight='bold' "
            + "fill='%s' stroke='%s' stroke-width='%.1f' paint-order='stroke' "
            + "style='paint-order:stroke'>%s</text>%n",
            cx, baselineY, size, fill, outline, size * OUTLINE_WEIGHT, esc(text)));
    }

    @Override
    public void outlinedTextRotated(String text, double cx, double cy, double size,
                                    String fill, String outline, double rotDeg) {
        sb.append(String.format(Locale.ROOT,
            "<g transform='translate(%.1f,%.1f) rotate(%.1f)'>"
            + "<text x='0' y='%.1f' text-anchor='middle' font-size='%.1f' "
            + "font-weight='bold' fill='%s' stroke='%s' stroke-width='%.1f' "
            + "paint-order='stroke' style='paint-order:stroke'>%s</text></g>%n",
            cx, cy, rotDeg, size * 0.36, size, fill, outline, size * OUTLINE_WEIGHT, esc(text)));
    }

    /** Экранировать текст для XML. */
    public static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
