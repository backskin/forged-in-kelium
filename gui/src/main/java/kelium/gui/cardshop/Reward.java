package kelium.gui.cardshop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * НАГРАДА КАРТЫ ЗАДАНИЯ — основная и усиленная (заказ дизайнера 28.09.2026):
 * сначала ВИД — действие (иконка в кольце) или ресурсы (иконки без кольца,
 * со счётом), потом ВЫБОР — одно, одно из двух (через косую черту) или всё
 * вместе.
 *
 * <p>В файле карты хранится словарём:
 * {@code {вид: действие|ресурсы, выбор: одно|одно из двух|всё вместе,
 * позиции: [{иконка: "34", сколько: 1}, …]}}. Прежняя запись строкой
 * («{34} {32}», «{1}{1} {25}») читается по-старому.
 */
public final class Reward {

    public static final String ACTION = "действие";
    public static final String RESOURCES = "ресурсы";
    public static final String ONE = "одно";
    public static final String EITHER = "одно из двух";
    public static final String ALL = "всё вместе";

    /** Одна позиция: иконка и сколько штук. */
    public record Item(String icon, int count) {
    }

    public String kind = ACTION;
    public String choice = ONE;
    public final List<Item> items = new ArrayList<>();

    public boolean isEmpty() {
        return items.isEmpty();
    }

    // ==================== чтение и запись ====================

    @SuppressWarnings("unchecked")
    public static Reward of(Object raw, boolean main) {
        Reward r = new Reward();
        if (raw instanceof Map<?, ?> m) {
            r.kind = String.valueOf(((Map<String, Object>) m).getOrDefault("вид", ACTION));
            r.choice = String.valueOf(((Map<String, Object>) m).getOrDefault("выбор", ONE));
            if (m.get("позиции") instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> im) {
                        String ic = String.valueOf(im.get("иконка")).replaceAll("[{}]", "").trim();
                        int n = im.get("сколько") instanceof Number num ? num.intValue() : 1;
                        if (!ic.isEmpty() && !"null".equals(ic)) {
                            r.items.add(new Item(ic, Math.max(1, n)));
                        }
                    }
                }
            }
            return r;
        }
        // прежняя запись строкой фишек
        List<String> icons = new ArrayList<>();
        for (String t : CardAssets.tokens(raw == null ? "" : String.valueOf(raw))) {
            if (t.startsWith("{")) {
                icons.add(CardAssets.key(t.substring(1, t.length() - 1)));
            }
        }
        if (main && icons.size() <= 2) {
            r.kind = ACTION;
            r.choice = icons.size() == 2 ? EITHER : ONE;
            for (String ic : icons) {
                r.items.add(new Item(ic, 1));
            }
        } else {
            r.kind = RESOURCES;
            r.choice = ALL;
            for (String ic : icons) {
                int last = r.items.size() - 1;
                if (last >= 0 && r.items.get(last).icon().equals(ic)) {
                    r.items.set(last, new Item(ic, r.items.get(last).count() + 1));
                } else {
                    r.items.add(new Item(ic, 1));
                }
            }
        }
        return r;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("вид", kind);
        m.put("выбор", choice);
        List<Map<String, Object>> l = new ArrayList<>();
        for (Item it : items) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("иконка", it.icon());
            im.put("сколько", it.count());
            l.add(im);
        }
        m.put("позиции", l);
        return m;
    }

    // ==================== рисование ====================

    /**
     * Нарисовать блок центром в (cx, cy) — пиксели карты; {@code width} —
     * сколько места по ширине, {@code size} — сторона кольца или иконки.
     */
    /** Высота иконок награды при размере size: действие — кольцо, ресурсы — 0,8 размера. */
    double iconHeight(double size) {
        return ACTION.equals(kind) ? size : size * 0.8;
    }

    void draw(CardCanvas k, double cx, double cy, double width, double size) {
        if (items.isEmpty()) {
            return;
        }
        boolean either = EITHER.equals(choice) && items.size() == 2;
        if (ACTION.equals(kind)) {
            double ring = size;
            double gap = either ? 40 : 14;
            int n = items.size();
            double total = n * ring + (n - 1) * gap;
            if (total > width) {
                ring = (width - (n - 1) * gap) / n;
                total = width;
            }
            double x = cx - total / 2 + ring / 2;
            for (int i = 0; i < n; i++) {
                k.put("25", x, cy, ring, ring);
                k.put(items.get(i).icon(), x, cy - ring * 0.02, ring * 0.945, ring * 0.945);
                if (either && i == 0) {
                    slash(k, x + ring / 2 + gap / 2, cy, ring);
                }
                x += ring + gap;
            }
            return;
        }
        // РЕСУРСЫ: у каждой позиции своя стопка иконок (повторы внахлёст)
        double icon = size * 0.8;
        double gap = either ? 44 : 10;
        double[] w = new double[items.size()];
        double total;
        while (true) {
            total = 0;
            for (int i = 0; i < items.size(); i++) {
                w[i] = stackWidth(items.get(i), icon);
                total += w[i];
            }
            total += gap * (items.size() - 1);
            if (total <= width || icon < 30) {
                break;
            }
            icon -= 2;
        }
        double x = cx - total / 2;
        for (int i = 0; i < items.size(); i++) {
            drawStack(k, items.get(i), x, cy, icon);
            x += w[i];
            if (either && i == 0) {
                slash(k, x + gap / 2, cy, icon * 1.1);
            }
            x += gap;
        }
    }

    private static int shown(Item it) {
        return Math.min(it.count(), 4);
    }

    private static double stackWidth(Item it, double icon) {
        int n = shown(it);
        double w = icon + (n - 1) * icon * 0.56;
        if (it.count() > 4) {
            w += icon * 0.7;          // «×7» после стопки
        }
        return w;
    }

    private static void drawStack(CardCanvas k, Item it, double x, double cy, double icon) {
        int n = shown(it);
        for (int j = 0; j < n; j++) {
            k.put(it.icon(), x + icon / 2 + j * icon * 0.56, cy, icon, icon);
        }
        if (it.count() > 4) {
            double tx = x + icon + (n - 1) * icon * 0.56 + 6;
            k.text("×" + it.count(), k.font("TekturNarrow-Bold.ttf", icon * 0.55), tx * 2, cy * 2,
                'l', Color.WHITE, new Color(45, 45, 50), 4 * 2);
        }
    }

    /** Косая черта «одно из двух» — как на печати: тёмная подложка и красная линия. */
    private static void slash(CardCanvas k, double x, double cy, double h) {
        double dx = h * 0.11;
        double dy = h * 0.43;
        k.g.setStroke(new BasicStroke((float) (h * 0.1 * 2), BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER));
        k.g.setColor(new Color(40, 30, 30));
        k.g.draw(new Line2D.Double((x + dx) * 2, (cy - dy) * 2, (x - dx) * 2, (cy + dy) * 2));
        k.g.setStroke(new BasicStroke((float) (h * 0.064 * 2), BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER));
        k.g.setColor(new Color(214, 60, 44));
        k.g.draw(new Line2D.Double((x + dx) * 2, (cy - dy + 2) * 2, (x - dx) * 2,
            (cy + dy - 2) * 2));
    }
}
