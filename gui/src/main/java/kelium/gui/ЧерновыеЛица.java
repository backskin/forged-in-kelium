package kelium.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import kelium.dataio.GameConfig;

/**
 * ЧЕРНОВЫЕ ЛИЦА КАРТ 2.0 (30.09.2026) — пока дизайнер не нарисовал настоящие.
 *
 * <p>Карта, собранная программой, приходит без картинки, и в окне партии она
 * рисовалась бы запасным видом. Здесь лицо рисуется в размер печатных
 * ({@code 600×933} задание, {@code 600×389} арсенал) и в той же раскладке:
 * задание — верх, название, требование, награда, «дополнительно»; арсенал —
 * верх и срабатывание. Цвет полосы — значок развилки карты. Текст — ровно тот,
 * что печатает сама карта (из её записи), ничего не дописывается.
 *
 * <p>Кладёт картинки туда, где игра ищет лица ({@code textures/card/<колода>/<id>.png}),
 * и только если настоящего лица там нет.
 *
 * <p>Запуск: {@code kelium.gui.ЧерновыеЛица [свод]} (по умолчанию 1.47.0).
 */
public final class ЧерновыеЛица {

    private ЧерновыеЛица() {
    }

    private static final Color ТЕКСТ = new Color(0x20, 0x24, 0x2B);
    private static final Color БУМАГА = new Color(0xF4, 0xF1, 0xEA);

    /** Цвет развилки — полоса карты. */
    static Color цвет(String значок) {
        if (значок == null) {
            return new Color(0x6B, 0x72, 0x80);
        }
        return switch (значок) {
            case "extract" -> new Color(0x2E, 0x8B, 0x57);
            case "power" -> new Color(0xE0, 0xA1, 0x1B);
            case "supply" -> new Color(0x3B, 0x6E, 0xC4);
            case "command" -> new Color(0xC0, 0x39, 0x2B);
            case "develop" -> new Color(0x8E, 0x44, 0xAD);
            default -> new Color(0x6B, 0x72, 0x80);
        };
    }

    static String развилка(String значок) {
        if (значок == null) {
            return "";
        }
        return switch (значок) {
            case "extract" -> "Добыча";
            case "power" -> "Питание";
            case "supply" -> "Снабжение";
            case "command" -> "Командование";
            case "develop" -> "Развитие";
            default -> "";
        };
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String свод = args.length > 0 ? args[0] : "1.47.0";
        GameConfig cfg = GameConfig.build(свод, 4, 1L, null, null);
        Path корень = GameConfig.texturesRoot().resolve("card");
        int n = 0;
        for (Map<String, Object> e : cfg.content.get("objectives").entries) {
            String id = String.valueOf(e.get("id"));
            if (!id.startsWith("z2_")) {
                continue;
            }
            Path ф = корень.resolve("objective").resolve(id + ".png");
            ImageIO.write(задание(e), "png", ф.toFile());
            n++;
        }
        for (Map<String, Object> e : cfg.content.get("arsenal").entries) {
            String id = String.valueOf(e.get("id"));
            if (!id.startsWith("a8_")) {
                continue;
            }
            Path ф = корень.resolve("arsenal").resolve(id + ".png");
            ImageIO.write(арсенал(e), "png", ф.toFile());
            n++;
        }
        System.out.println("черновых лиц: " + n + " → " + корень);
    }

    @SuppressWarnings("unchecked")
    static BufferedImage задание(Map<String, Object> e) {
        int w = 600;
        int h = 933;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = кисть(img);
        String значок = e.get("значок") != null ? String.valueOf(e.get("значок"))
            : e.get("язык") instanceof Map<?, ?> я && я.get("значок") != null
                ? String.valueOf(я.get("значок")) : null;
        Color ц = цвет(значок);
        фон(g, w, h, ц);
        int y = 24;
        // верх
        String верх = e.get("top") instanceof Map<?, ?> t && t.get("label") != null
            ? String.valueOf(t.get("label")) : null;
        if (верх != null) {
            g.setColor(new Color(255, 255, 255, 215));
            g.fill(new RoundRectangle2D.Double(20, y, w - 40, 150, 26, 26));
            g.setColor(ТЕКСТ);
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.drawString("сжечь:", 40, y + 34);
            y = абзац(g, верх, new Font("SansSerif", Font.BOLD, 26), 40, y + 44, w - 80, 3) + 20;
            y = Math.max(y, 200);
        }
        // название
        g.setColor(ц);
        g.fillRect(0, y, w, 64);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        String имя = String.valueOf(e.getOrDefault("name", e.get("id")));
        g.drawString(имя, (w - g.getFontMetrics().stringWidth(имя)) / 2, y + 45);
        y += 90;
        String условие = e.get("requirement") instanceof Map<?, ?> r ? String.valueOf(r.get("условие")) : "";
        g.setColor(ТЕКСТ);
        y = абзац(g, условие, new Font("SansSerif", Font.PLAIN, 32), 40, y, w - 80, 7) + 24;
        // награда
        String награда = словаНаграды(e.get("base_reward"));
        g.setColor(new Color(255, 255, 255, 225));
        g.fill(new RoundRectangle2D.Double(24, y, w - 48, 110, 30, 30));
        g.setColor(ц);
        g.setStroke(new BasicStroke(4f));
        g.draw(new RoundRectangle2D.Double(24, y, w - 48, 110, 30, 30));
        g.setColor(ТЕКСТ);
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.drawString("Награда:", 44, y + 40);
        абзац(g, награда, new Font("SansSerif", Font.PLAIN, 26), 44, y + 50, w - 90, 2);
        y += 136;
        // дополнительно
        if (e.get("enhanced") instanceof Map<?, ?> en && en.get("условие") != null) {
            g.setColor(ц);
            g.fillRect(0, y, w, 46);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 30));
            g.drawString("дополнительно", (w - g.getFontMetrics().stringWidth("дополнительно")) / 2, y + 34);
            y += 66;
            g.setColor(ТЕКСТ);
            y = абзац(g, String.valueOf(en.get("условие")), new Font("SansSerif", Font.PLAIN, 28),
                40, y, w - 80, 3) + 10;
            g.setFont(new Font("SansSerif", Font.BOLD, 24));
            абзац(g, "→ " + словаНаграды(e.get("special_reward")), new Font("SansSerif", Font.BOLD, 24),
                40, y, w - 80, 2);
        }
        // значок развилки
        g.setColor(ц);
        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        String р = развилка(значок);
        g.drawString(р, 30, h - 30);
        g.setColor(new Color(0x80, 0x80, 0x80));
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        String черн = "черновое лицо · " + e.get("id");
        g.drawString(черн, w - 30 - g.getFontMetrics().stringWidth(черн), h - 30);
        g.dispose();
        return img;
    }

    static BufferedImage арсенал(Map<String, Object> e) {
        int w = 600;
        int h = 389;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = кисть(img);
        Color ц = цвет(e.get("значок") == null ? null : String.valueOf(e.get("значок")));
        фон(g, w, h, ц);
        String верх = e.get("top") instanceof Map<?, ?> t && t.get("label") != null
            ? String.valueOf(t.get("label")) : "";
        g.setColor(new Color(255, 255, 255, 215));
        g.fill(new RoundRectangle2D.Double(16, 14, w - 32, 86, 22, 22));
        g.setColor(ТЕКСТ);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("сжечь:", 32, 40);
        абзац(g, верх, new Font("SansSerif", Font.BOLD, 22), 32, 46, w - 64, 2);
        g.setColor(ц);
        g.fillRect(0, 112, w, 50);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        String имя = String.valueOf(e.getOrDefault("name", e.get("id")));
        g.drawString(имя, (w - g.getFontMetrics().stringWidth(имя)) / 2, 147);
        g.setColor(ТЕКСТ);
        String описание = String.valueOf(e.getOrDefault("описание", ""));
        абзац(g, описание, new Font("SansSerif", Font.PLAIN, 25), 32, 180, w - 64, 5);
        g.setColor(ц);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString(развилка(e.get("значок") == null ? null : String.valueOf(e.get("значок"))), 24, h - 16);
        g.setColor(new Color(0x80, 0x80, 0x80));
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        String черн = "черновое лицо · " + e.get("id");
        g.drawString(черн, w - 24 - g.getFontMetrics().stringWidth(черн), h - 16);
        g.dispose();
        return img;
    }

    private static Graphics2D кисть(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static void фон(Graphics2D g, int w, int h, Color ц) {
        g.setPaint(new GradientPaint(0, 0, БУМАГА, 0, h, new Color(
            (БУМАГА.getRed() + ц.getRed()) / 2, (БУМАГА.getGreen() + ц.getGreen()) / 2,
            (БУМАГА.getBlue() + ц.getBlue()) / 2)));
        g.fill(new RoundRectangle2D.Double(0, 0, w, h, 36, 36));
        g.setColor(ц);
        g.setStroke(new BasicStroke(8f));
        g.draw(new RoundRectangle2D.Double(4, 4, w - 8, h - 8, 32, 32));
    }

    /** Абзац с переносом по словам; возвращает нижнюю границу текста. */
    private static int абзац(Graphics2D g, String текст, Font шрифт, int x, int y, int ширина, int строк) {
        g.setFont(шрифт);
        FontMetrics fm = g.getFontMetrics();
        List<String> линии = new ArrayList<>();
        StringBuilder стр = new StringBuilder();
        for (String слово : текст.split(" ")) {
            String проба = стр.isEmpty() ? слово : стр + " " + слово;
            if (fm.stringWidth(проба) > ширина && !стр.isEmpty()) {
                линии.add(стр.toString());
                стр = new StringBuilder(слово);
            } else {
                стр = new StringBuilder(проба);
            }
        }
        if (!стр.isEmpty()) {
            линии.add(стр.toString());
        }
        int yy = y + fm.getAscent();
        for (int i = 0; i < линии.size() && i < строк; i++) {
            g.drawString(линии.get(i), x, yy);
            yy += fm.getHeight();
        }
        return yy - fm.getAscent();
    }

    /** Награда словами — из записи награды каталога. */
    static String словаНаграды(Object o) {
        if (!(o instanceof Map<?, ?> m) || m.isEmpty()) {
            return "нет";
        }
        List<String> части = new ArrayList<>();
        if (m.get("action") != null) {
            String[] ab = String.valueOf(m.get("action")).split("\\|");
            List<String> в = new ArrayList<>();
            for (String a : ab) {
                в.add(ветка(a));
            }
            части.add(String.join(" или ", в));
        }
        if (m.get("coin") instanceof Number n) {
            части.add(n + " мон.");
        }
        if (m.get("spec_actions") instanceof Number n) {
            части.add(n.intValue() == 1 ? "спец-действие" : n + " спец-действия");
        }
        if (m.get("arsenal") instanceof Number n) {
            части.add(n.intValue() == 1 ? "карта арсенала" : n + " карты арсенала");
        }
        if (m.get("objective_card") instanceof Number n) {
            части.add(n + " карта задания");
        }
        return части.isEmpty() ? String.valueOf(m) : String.join(" и ", части);
    }

    private static String ветка(String код) {
        return switch (код) {
            case "mining" -> "«Добыть»";
            case "build_miner" -> "«Построить добытчик»";
            case "energy_swap" -> "«Переложить энергию»";
            case "build_plant" -> "«Построить энергостанцию»";
            case "assembly" -> "«Выпустить»";
            case "build_military" -> "«Построить военное здание»";
            case "movement" -> "«Манёвр»";
            case "combat" -> "«Бой»";
            case "market" -> "«Рынок»";
            case "science" -> "«Наука»";
            default -> код;
        };
    }
}
