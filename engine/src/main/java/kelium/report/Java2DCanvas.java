package kelium.report;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java2DCanvas — вывод {@link FieldCanvas} на {@link Graphics2D} (окно приложения).
 *
 * <p>Контекст рисования уже отмасштабирован на {@code zoom}, поэтому толщина
 * линий, приходящая в ЭКРАННЫХ пикселях, здесь делится на масштаб: иначе на
 * мелком масштабе контуры исчезают, а на крупном становятся толще силуэта.
 */
public final class Java2DCanvas implements FieldCanvas {

    /**
     * ТОЛЩИНА ОБВОДКИ ЦИФР на жетонах — доля от размера шрифта.
     *
     * <p>Было 0.28, и дизайнер справедливо назвал это «слишком жирно»: обводка
     * толщиной больше четверти кегля съедает саму цифру, особенно у единицы и
     * семёрки. 0.16 читается на любом цвете жетона и не заплывает.
     */
    private static final double OUTLINE_WEIGHT = 0.16;


    private final Graphics2D g;
    private final double zoom;
    private final Font baseFont;
    private double alpha = 1;

    /**
     * @param zoom масштаб, уже применённый к {@code g} (для пересчёта толщины линий)
     */
    public Java2DCanvas(Graphics2D g, double zoom, Font baseFont) {
        this.g = g;
        this.zoom = Math.max(0.01, zoom);
        this.baseFont = baseFont != null ? baseFont : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    // Разбор цвета кэшируется: за кадр он вызывается сотни раз.
    private static final Map<String, Color> COLORS = new ConcurrentHashMap<>();

    /** Цвет из строки {@code "#rrggbb"}; {@code "none"} — прозрачный. */
    public static Color color(String hex) {
        if (hex == null || "none".equals(hex)) {
            return null;
        }
        return COLORS.computeIfAbsent(hex, h -> {
            String body = h.startsWith("#") ? h.substring(1) : h;
            if (body.length() == 8) {                 // #rrggbbaa
                return new Color((int) Long.parseLong(body, 16), true);
            }
            return new Color(Integer.parseInt(body, 16));
        });
    }

    private BasicStroke pen(double screenPx) {
        return new BasicStroke((float) (screenPx / zoom), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    }

    private void fillAndStroke(Shape s, String fill, String stroke, double width) {
        Color f = color(fill);
        if (f != null) {
            java.awt.Composite old = g.getComposite();
            if (alpha < 1) {
                g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) alpha));
            }
            g.setColor(f);
            g.fill(s);
            g.setComposite(old);
        }
        Color st = color(stroke);
        if (st != null && width > 0) {
            g.setColor(st);
            g.setStroke(pen(width));
            g.draw(s);
        }
    }

    @Override
    public void alpha(double value) {
        this.alpha = value;
    }

    /**
     * Обрезки СТОПКОЙ: они вкладываются друг в друга (зона внутри жетона, штрихи
     * внутри зоны), поэтому прежнюю область надо помнить для каждой, а не одну.
     */
    private final java.util.Deque<java.awt.Shape> clipStack = new java.util.ArrayDeque<>();

    @Override
    public void clipTo(double[][] points) {
        pushClip();
        Path2D p = new Path2D.Double();
        for (int i = 0; i < points.length; i++) {
            if (i == 0) {
                p.moveTo(points[i][0], points[i][1]);
            } else {
                p.lineTo(points[i][0], points[i][1]);
            }
        }
        p.closePath();
        g.clip(p);
    }

    @Override
    public void clipToShape(FieldGeometry.Shape shape, double cx, double cy, double rotDeg,
                            double k, double anchorX, double anchorY) {
        pushClip();
        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(Math.toRadians(rotDeg));
        at.scale(k, k);
        at.translate(-anchorX, -anchorY);
        g.clip(at.createTransformedShape(shape.path()));
    }

    /** Запомнить область до новой обрезки (null тоже значим — «без обрезки»). */
    private void pushClip() {
        clipStack.push(g.getClip() == null ? NO_CLIP : g.getClip());
    }

    /** Метка «обрезки не было»: в стопке нельзя хранить null. */
    private static final java.awt.Shape NO_CLIP = new java.awt.Rectangle();

    @Override
    public void clipOff() {
        if (clipStack.isEmpty()) {
            return;
        }
        java.awt.Shape was = clipStack.pop();
        g.setClip(was == NO_CLIP ? null : was);
    }

    @Override
    public void polygon(double[][] points, String fill, String stroke, double strokeWidth) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < points.length; i++) {
            if (i == 0) {
                p.moveTo(points[i][0], points[i][1]);
            } else {
                p.lineTo(points[i][0], points[i][1]);
            }
        }
        p.closePath();
        fillAndStroke(p, fill, stroke, strokeWidth);
    }

    @Override
    public void shape(FieldGeometry.Shape shape, double cx, double cy, double rotDeg,
                      double k, double anchorX, double anchorY,
                      String fill, String stroke, double strokeWidth) {
        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(Math.toRadians(rotDeg));
        at.scale(k, k);
        at.translate(-anchorX, -anchorY);
        fillAndStroke(at.createTransformedShape(shape.path()), fill, stroke, strokeWidth);
    }

    @Override
    public void image(java.awt.image.BufferedImage img, double cx, double cy, double rotDeg,
                      double k, double anchorX, double anchorY) {
        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(Math.toRadians(rotDeg));
        at.scale(k, k);
        at.translate(-anchorX, -anchorY);
        Object was = g.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, at, null);
        if (was != null) {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, was);
        }
    }

    @Override
    public void roundRect(double x, double y, double w, double h, double radius,
                          String fill, String stroke, double strokeWidth) {
        fillAndStroke(new RoundRectangle2D.Double(x, y, w, h, radius * 2, radius * 2),
            fill, stroke, strokeWidth);
    }

    @Override
    public void circle(double cx, double cy, double r, String fill, String stroke,
                       double strokeWidth) {
        fillAndStroke(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r),
            fill, stroke, strokeWidth);
    }

    /**
     * Минимальный размер буквы на ЭКРАНЕ. При вписывании большого поля в
     * невысокое окно масштаб падает до трети, и подписи превращались в
     * нечитаемые три пикселя.
     */
    private static final double MIN_TEXT_PX = 8.5;

    private Font font(double worldSize, boolean bold) {
        double size = worldSize;
        if (size * zoom < MIN_TEXT_PX) {
            size = MIN_TEXT_PX / zoom;
        }
        return baseFont.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) Math.max(4, size));
    }

    @Override
    public void text(String text, double cx, double baselineY, double size,
                     boolean bold, String fill) {
        Font f = font(size, bold);
        Font old = g.getFont();
        g.setFont(f);
        Rectangle2D b = g.getFontMetrics().getStringBounds(text, g);
        Color c = color(fill);
        g.setColor(c != null ? c : Color.BLACK);
        g.drawString(text, (float) (cx - b.getWidth() / 2), (float) baselineY);
        g.setFont(old);
    }

    @Override
    public void outlinedText(String text, double cx, double baselineY, double size,
                             String fill, String outline) {
        Font f = font(size, true);
        Rectangle2D b = f.getStringBounds(text, g.getFontRenderContext());
        float x = (float) (cx - b.getWidth() / 2);
        java.awt.font.GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), text);
        Shape shape = gv.getOutline(x, (float) baselineY);
        Color oc = color(outline);
        if (oc != null) {
            g.setColor(oc);
            g.setStroke(new BasicStroke((float) (f.getSize2D() * OUTLINE_WEIGHT),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shape);
        }
        Color fc = color(fill);
        g.setColor(fc != null ? fc : Color.WHITE);
        g.fill(new Area(shape));
    }

    @Override
    public void outlinedTextRotated(String text, double cx, double cy, double size,
                                    String fill, String outline, double rotDeg) {
        AffineTransform was = g.getTransform();
        g.translate(cx, cy);
        g.rotate(Math.toRadians(rotDeg));
        // рисуем вокруг начала координат: середина надписи как раз в точке поворота
        Font f = font(size, true);
        Rectangle2D b = f.getStringBounds(text, g.getFontRenderContext());
        float x = (float) (-b.getWidth() / 2);
        float y = (float) (size * 0.36);
        java.awt.font.GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), text);
        Shape shape = gv.getOutline(x, y);
        Color oc = color(outline);
        if (oc != null) {
            g.setColor(oc);
            g.setStroke(new BasicStroke((float) (f.getSize2D() * OUTLINE_WEIGHT),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shape);
        }
        Color fc = color(fill);
        g.setColor(fc != null ? fc : Color.WHITE);
        g.fill(new Area(shape));
        g.setTransform(was);
    }
}
