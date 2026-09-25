package kelium.gui;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import kelium.report.Textures;

/**
 * ПЕЧАТНАЯ ГРАФИКА КАРТ ПО ИДЕНТИФИКАТОРУ — одно место на всё приложение.
 *
 * <p>Окно живой партии и проигрыватель показывают одни и те же карты: задания
 * (обычные, начальные, супер), арсенал (обычный, начальный, супер), приказы,
 * контейнеры, рынок. Лицо ищется по ТОЧНОМУ идентификатору карты в папке своей
 * колоды ({@code card/<колода>/<id>.png}, см. {@link Textures#cardFace}), поэтому
 * новая карта (например рынок {@code m4_01}) подхватывается сама, как только
 * её картинка разложена, — без таблиц и без номеров в коде.
 *
 * <p>ПРОПОРЦИЯ — ВСЕГДА С КАРТИНКИ. Задание и приказ стоячие (≈0,643), арсенал
 * и рынок лежачие (≈1,54), контейнер квадратный. Числом её здесь не задают:
 * {@link #aspect} читает её у самой картинки, {@link #fit} вписывает карту в
 * место, не сплющивая.
 *
 * <p>Нет картинки — методы отдают {@code null}, и вызывающий рисует прежний
 * рисованный вид: печать не источник правил.
 */
public final class CardArt {

    private CardArt() {
    }

    /** Папки лиц в порядке поиска для карты неизвестной колоды. */
    private static final List<String> ВСЕ_КОЛОДЫ = List.of("objective", "objective_start",
        "objective_super", "arsenal", "arsenal_start", "arsenal_super", "market", "container");

    /** Печатное лицо любой карты по id — задания, арсенал, рынок, контейнер. */
    public static BufferedImage face(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (String deck : ВСЕ_КОЛОДЫ) {
            BufferedImage img = Textures.cardFace(deck, id);
            if (img != null) {
                return img;
            }
        }
        return null;
    }

    /**
     * Лицо карты из НАЗВАННОГО набора записи ({@code objectives},
     * {@code super_objectives}, {@code arsenal}, {@code super_arsenal},
     * {@code containers}, {@code market}). Сначала ищется в папках этого набора,
     * потом — везде: начальные карты лежат в своей папке, а в записи идут в
     * общем наборе.
     */
    public static BufferedImage face(String set, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (String deck : folders(set)) {
            BufferedImage img = Textures.cardFace(deck, id);
            if (img != null) {
                return img;
            }
        }
        return face(id);
    }

    /** Папки лиц набора записи. */
    private static List<String> folders(String set) {
        if (set == null) {
            return List.of();
        }
        return switch (set) {
            case "objectives", "objective" -> List.of("objective", "objective_start");
            case "super_objectives", "super", "objective_super" -> List.of("objective_super");
            case "arsenal" -> List.of("arsenal", "arsenal_start", "arsenal_super");
            case "super_arsenal", "arsenal_super" -> List.of("arsenal_super");
            case "containers", "container" -> List.of("container");
            case "market" -> List.of("market");
            default -> List.of();
        };
    }

    /** Лицо карты арсенала любого вида: обычный, начальный, супер. */
    public static BufferedImage arsenal(String id) {
        return face("arsenal", id);
    }

    /** Лицо карты рынка по id ({@code m3_03}, {@code m4_07} …). */
    public static BufferedImage market(String id) {
        return id == null ? null : Textures.cardFace("market", id);
    }

    /**
     * РУБАШКА НАБОРА. У каждой колоды своя: задания, начальные задания,
     * супер-задания, арсенал, начальный и супер-арсенал, контейнеры, рынок,
     * приказы. Для конкретной карты арсенала вид {@code "arsenal:<id>"} выбирает
     * рубашку по тому, из какой колоды карта.
     */
    public static BufferedImage back(String kind) {
        if (kind == null) {
            return null;
        }
        if (kind.startsWith("arsenal:")) {
            String id = kind.substring("arsenal:".length());
            String deck = Textures.cardFace("arsenal_start", id) != null ? "deck_arsenal_start"
                : Textures.cardFace("arsenal_super", id) != null ? "deck_super_arsenal"
                : "deck_arsenal";
            return Textures.card(deck, "deck_arsenal", "deck");
        }
        if (kind.startsWith("objective:")) {
            String id = kind.substring("objective:".length());
            return Textures.cardFace("objective_start", id) != null
                ? Textures.card("deck_objectives_start", "deck_objectives", "deck")
                : Textures.card("deck_objectives", "deck");
        }
        return switch (kind) {
            case "arsenal" -> Textures.card("deck_arsenal", "deck");
            case "super_arsenal", "arsenal_super" -> Textures.card("deck_super_arsenal",
                "deck_arsenal", "deck");
            case "objective", "objectives" -> Textures.card("deck_objectives", "deck");
            case "super", "super_objectives" -> Textures.card("deck_super_objectives",
                "deck_objectives", "deck");
            case "containers", "container" -> Textures.card("deck_containers", "deck");
            case "market" -> Textures.card("deck_market", "deck");
            case "orders" -> Textures.card("deck_orders", "deck");
            default -> Textures.card("deck_" + kind, "deck");
        };
    }

    /**
     * ПЕЧАТНОЕ ЛИЦО ПРИКАЗА. Обычная карта — по своему id ({@code blue_infra}).
     * БЕЗОПАСНОСТЬ пронумерована по месту ({@code security_1}…), а напечатана в
     * цвете колоды игрока — её рисунок ищется по ЦВЕТУ; «red» и «scarlet» — одна
     * колода под двумя именами.
     */
    public static BufferedImage order(String id, String colour) {
        if (id == null || id.isBlank()) {
            return null;
        }
        if (id.startsWith("security")) {
            String c = colour == null ? "" : colour;
            return Textures.orderCard(c.isEmpty() ? null : "security_" + c,
                alias(c) == null ? null : "security_" + alias(c), "security");
        }
        return Textures.orderCard(id);
    }

    /** Рубашка колоды приказов этого цвета; нет своей — общая. */
    public static BufferedImage orderBack(String colour) {
        String c = colour == null ? "" : colour;
        BufferedImage b = Textures.orderCard(c.isEmpty() ? null : "back_" + c,
            alias(c) == null ? null : "back_" + alias(c), "back");
        return b != null ? b : Textures.card("deck_orders", "deck");
    }

    private static String alias(String colour) {
        return "red".equals(colour) ? "scarlet" : "scarlet".equals(colour) ? "red" : null;
    }

    /** Лицо любой карты игрока: сперва колоды по id, потом приказы. */
    public static BufferedImage any(String id, String orderColour) {
        BufferedImage f = face(id);
        return f != null ? f : order(id, orderColour);
    }

    /** Трофейная сторона уничтоженного жетона — печатью, без цвета места. */
    public static BufferedImage trophy(String type, Integer level, int value) {
        return Textures.trophySide(type, level, value);
    }

    // ------------------------------------------------------------------
    //  ГЕОМЕТРИЯ
    // ------------------------------------------------------------------

    /** Отношение ширины к высоте — у самой картинки. */
    public static double aspect(BufferedImage img) {
        return img == null || img.getHeight() <= 0 ? 1.0
            : img.getWidth() / (double) img.getHeight();
    }

    /**
     * Вписать картинку в место, НЕ СПЛЮЩИВАЯ: по центру, целиком, в своей
     * пропорции.
     */
    public static Rectangle fit(BufferedImage img, double x, double y, double w, double h) {
        double a = aspect(img);
        double fw = w;
        double fh = fw / a;
        if (fh > h) {
            fh = h;
            fw = fh * a;
        }
        return new Rectangle((int) Math.round(x + (w - fw) / 2),
            (int) Math.round(y + (h - fh) / 2), (int) Math.round(fw), (int) Math.round(fh));
    }

    /**
     * Нарисовать карту в прямоугольник со скруглёнными углами. Прямоугольник
     * должен быть уже в пропорции картинки ({@link #fit}); качество
     * масштабирования — билинейное, чтобы мелкий текст печати не рассыпался.
     */
    public static void draw(Graphics2D g, BufferedImage img, Rectangle r, double radius) {
        if (img == null || r.width <= 0 || r.height <= 0) {
            return;
        }
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        Shape clip = g.getClip();
        if (radius > 0) {
            g.clip(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height,
                radius * 2, radius * 2));
        }
        g.drawImage(img, r.x, r.y, r.width, r.height, null);
        g.setClip(clip);
        if (hint != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
        }
    }

    /** Вписать и нарисовать; возвращает, где карта легла. */
    public static Rectangle drawFit(Graphics2D g, BufferedImage img, double x, double y,
                                    double w, double h, double radius) {
        Rectangle r = fit(img, x, y, w, h);
        draw(g, img, r, radius);
        return r;
    }
}
