package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Map;

/**
 * ГЛИФЫ ВОСЬМИ ДЕЙСТВИЙ — рисованные фигурами (никаких emoji/шрифтовых
 * символов: на чужой машине они превращаются в квадраты — правило скилла).
 * Различаются СИЛУЭТОМ, не только цветом: молоток-стройка, кирка-добыча,
 * стрелки-манёвр, перекрестие-бой, весы-рынок, колба-наука,
 * шестерня-сборка, молния-энергия.
 */
public final class ActionIcons {

    private ActionIcons() {
    }

    /** Категория приказа → её два действия (порядок печатный). */
    public static final Map<String, List<String>> CATEGORY_ACTIONS = Map.of(
        "infrastructure", List.of("build", "energy_swap"),
        "development", List.of("assembly", "mining"),
        "operation", List.of("movement", "combat"),
        "acquisitions", List.of("market", "science"));

    /** Русское имя категории приказа. */
    public static String categoryRu(String cat) {
        return switch (cat) {
            case "infrastructure" -> "ИНФРАСТРУКТУРА";
            case "development" -> "РАЗРАБОТКА";
            case "operation" -> "ОПЕРАЦИЯ";
            case "acquisitions" -> "ПРИОБРЕТЕНИЯ";
            default -> cat == null ? "" : cat.toUpperCase(java.util.Locale.ROOT);
        };
    }

    /** Цвет колоды приказов по имени из данных. */
    public static Color deckColor(String deck) {
        return switch (deck == null ? "" : deck) {
            case "red", "scarlet" -> new Color(0xC75450);
            case "green" -> new Color(0x4E9E5F);
            case "blue" -> new Color(0x4A8ACD);
            case "yellow" -> new Color(0xC9A23B);
            case "security" -> new Color(0x6F7986);
            default -> new Color(0x8B93A0);
        };
    }

    /** Нарисовать глиф действия с центром (cx,cy), вписанный в size×size. */
    public static void paint(Graphics2D g, String action, double cx, double cy,
                             double size, Color color) {
        double r = size / 2;
        g.setColor(color);
        g.setStroke(new BasicStroke((float) (size * 0.12),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (action) {
            case "build" -> {                       // молоток
                g.draw(new Line2D.Double(cx - r * 0.5, cy + r * 0.8, cx + r * 0.4, cy - r * 0.3));
                Path2D head = new Path2D.Double();
                head.moveTo(cx + r * 0.05, cy - r * 0.75);
                head.lineTo(cx + r * 0.85, cy + r * 0.05);
                head.lineTo(cx + r * 0.45, cy + r * 0.4);
                head.lineTo(cx - r * 0.3, cy - r * 0.4);
                head.closePath();
                g.fill(head);
            }
            case "mining" -> {                      // кирка
                g.draw(new Line2D.Double(cx - r * 0.6, cy + r * 0.8, cx + r * 0.5, cy - r * 0.5));
                Path2D blade = new Path2D.Double();
                blade.moveTo(cx - r * 0.4, cy - r * 0.85);
                blade.quadTo(cx + r * 0.5, cy - r * 0.95, cx + r * 0.95, cy - r * 0.15);
                blade.quadTo(cx + r * 0.45, cy - r * 0.5, cx - r * 0.4, cy - r * 0.85);
                blade.closePath();
                g.fill(blade);
            }
            case "movement" -> {                    // двойная стрелка
                g.draw(new Line2D.Double(cx - r * 0.8, cy, cx + r * 0.45, cy));
                Path2D tip = new Path2D.Double();
                tip.moveTo(cx + r * 0.85, cy);
                tip.lineTo(cx + r * 0.25, cy - r * 0.5);
                tip.lineTo(cx + r * 0.25, cy + r * 0.5);
                tip.closePath();
                g.fill(tip);
                Path2D tip2 = new Path2D.Double();
                tip2.moveTo(cx + r * 0.2, cy);
                tip2.lineTo(cx - r * 0.4, cy - r * 0.5);
                tip2.lineTo(cx - r * 0.4, cy + r * 0.5);
                tip2.closePath();
                g.fill(tip2);
            }
            case "combat" -> {                      // перекрестие
                g.draw(new Ellipse2D.Double(cx - r * 0.55, cy - r * 0.55, r * 1.1, r * 1.1));
                g.draw(new Line2D.Double(cx, cy - r * 0.95, cx, cy - r * 0.35));
                g.draw(new Line2D.Double(cx, cy + r * 0.35, cx, cy + r * 0.95));
                g.draw(new Line2D.Double(cx - r * 0.95, cy, cx - r * 0.35, cy));
                g.draw(new Line2D.Double(cx + r * 0.35, cy, cx + r * 0.95, cy));
                g.fill(new Ellipse2D.Double(cx - r * 0.13, cy - r * 0.13, r * 0.26, r * 0.26));
            }
            case "market" -> {                      // весы
                g.draw(new Line2D.Double(cx, cy - r * 0.85, cx, cy + r * 0.7));
                g.draw(new Line2D.Double(cx - r * 0.7, cy - r * 0.55, cx + r * 0.7, cy - r * 0.55));
                g.draw(new Line2D.Double(cx - r * 0.9, cy + r * 0.75, cx + r * 0.9, cy + r * 0.75));
                g.draw(new java.awt.geom.Arc2D.Double(cx - r * 0.95, cy - r * 0.45,
                    r * 0.55, r * 0.5, 180, 180, java.awt.geom.Arc2D.OPEN));
                g.draw(new java.awt.geom.Arc2D.Double(cx + r * 0.4, cy - r * 0.45,
                    r * 0.55, r * 0.5, 180, 180, java.awt.geom.Arc2D.OPEN));
            }
            case "science" -> {                     // колба
                Path2D flask = new Path2D.Double();
                flask.moveTo(cx - r * 0.22, cy - r * 0.85);
                flask.lineTo(cx - r * 0.22, cy - r * 0.15);
                flask.lineTo(cx - r * 0.7, cy + r * 0.7);
                flask.quadTo(cx, cy + r * 1.0, cx + r * 0.7, cy + r * 0.7);
                flask.lineTo(cx + r * 0.22, cy - r * 0.15);
                flask.lineTo(cx + r * 0.22, cy - r * 0.85);
                g.draw(flask);
                g.fill(new Ellipse2D.Double(cx - r * 0.35, cy + r * 0.25, r * 0.7, r * 0.5));
            }
            case "assembly" -> {                    // шестерня
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    g.draw(new Line2D.Double(cx + Math.cos(a) * r * 0.5, cy + Math.sin(a) * r * 0.5,
                        cx + Math.cos(a) * r * 0.9, cy + Math.sin(a) * r * 0.9));
                }
                g.draw(new Ellipse2D.Double(cx - r * 0.5, cy - r * 0.5, r, r));
                g.fill(new Ellipse2D.Double(cx - r * 0.18, cy - r * 0.18, r * 0.36, r * 0.36));
            }
            case "energy_swap" -> {                 // молния
                Path2D bolt = new Path2D.Double();
                bolt.moveTo(cx + r * 0.35, cy - r * 0.95);
                bolt.lineTo(cx - r * 0.45, cy + r * 0.15);
                bolt.lineTo(cx - r * 0.02, cy + r * 0.15);
                bolt.lineTo(cx - r * 0.35, cy + r * 0.95);
                bolt.lineTo(cx + r * 0.45, cy - r * 0.15);
                bolt.lineTo(cx + r * 0.02, cy - r * 0.15);
                bolt.closePath();
                g.fill(bolt);
            }
            default -> g.draw(new Ellipse2D.Double(cx - r * 0.5, cy - r * 0.5, r, r));
        }
    }
}
