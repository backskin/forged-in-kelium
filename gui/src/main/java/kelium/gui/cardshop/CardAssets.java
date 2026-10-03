package kelium.gui.cardshop;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * ГДЕ ЛЕЖИТ ТО, ИЗ ЧЕГО МАСТЕРСКАЯ СОБИРАЕТ КАРТЫ: пустые шаблоны и иконки
 * дизайнера на Диске, шрифты — вшитые в программу.
 *
 * <p>Шаблоны — «Общие компоненты/шаблоны карт», иконки — «Общие
 * компоненты/экспорт-иконки» («все иконки-N.png» по номеру и файлы со своими
 * именами, например «военное здание.png»). Папку Диска можно переназначить
 * переменной {@code KELIUM_DISK}.
 */
public final class CardAssets {

    public final File disk;
    public final File common;
    public final File templates;
    public final File icons;

    private final Map<String, BufferedImage> iconCache = new HashMap<>();
    private final Map<String, BufferedImage> templateCache = new HashMap<>();
    private final Map<String, Font> fontCache = new HashMap<>();

    public CardAssets() {
        String env = System.getenv("KELIUM_DISK");
        disk = env != null && !env.isBlank() ? new File(env)
            : new File(System.getProperty("user.home"), "Yandex.Disk" + File.separator
                + "Forged in Kelium");
        common = new File(disk, "Общие компоненты");
        File t = new File(common, "шаблоны карт");
        templates = t.isDirectory() ? t : new File(common, "задания-шаблоны");
        icons = new File(common, "экспорт-иконки");
    }

    /** Все иконки папки: ключ — номер («25») или имя файла без .png. */
    public Map<String, File> iconFiles() {
        Map<String, File> out = new TreeMap<>((a, b) -> {
            boolean na = a.matches("\\d+");
            boolean nb = b.matches("\\d+");
            if (na && nb) {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            }
            if (na != nb) {
                return na ? -1 : 1;
            }
            return a.compareTo(b);
        });
        File[] fs = icons.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (fs == null) {
            return out;
        }
        Pattern num = Pattern.compile("все иконки-(\\d+)\\.png", Pattern.CASE_INSENSITIVE);
        for (File f : fs) {
            Matcher m = num.matcher(f.getName());
            out.put(m.matches() ? m.group(1) : f.getName().substring(0, f.getName().length() - 4),
                f);
        }
        return out;
    }

    /** Иконка по ключу (номер или имя), обрезанная по непрозрачному; null — нет такой. */
    public BufferedImage icon(String key) {
        key = key.trim();
        if (iconCache.containsKey(key)) {
            return iconCache.get(key);
        }
        File f = key.matches("\\d+") ? new File(icons, "все иконки-" + key + ".png")
            : new File(icons, key + ".png");
        BufferedImage im = null;
        if (f.isFile()) {
            try {
                im = cropAlpha(toArgb(ImageIO.read(f)));
            } catch (Exception e) {
                im = null;
            }
        }
        iconCache.put(key, im);
        return im;
    }

    /** Пустой шаблон по имени файла в папке шаблонов; null — такого нет. */
    public BufferedImage template(String file) {
        if (templateCache.containsKey(file)) {
            return templateCache.get(file);
        }
        File f = new File(templates, file);
        if (!f.isFile()) {
            // прежние папки дизайнера — запасные
            for (String alt : List.of("задания-шаблоны", "арсенал-шаблоны")) {
                File g = new File(new File(common, alt), file);
                if (g.isFile()) {
                    f = g;
                }
            }
        }
        BufferedImage im = null;
        if (f.isFile()) {
            try {
                im = toArgb(ImageIO.read(f));
            } catch (Exception e) {
                im = null;
            }
        }
        templateCache.put(file, im);
        return im;
    }

    /** Шаблон слоями: фон и поверх подложка (любой из двух может отсутствовать). */
    public BufferedImage layered(String bg, String over) {
        String key = bg + "|" + over;
        if (templateCache.containsKey(key)) {
            return templateCache.get(key);
        }
        BufferedImage b = bg == null ? null : template(bg);
        BufferedImage o = over == null ? null : template(over);
        BufferedImage out = null;
        if (b != null || o != null) {
            BufferedImage base = b != null ? b : o;
            out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            if (b != null) {
                g.drawImage(b, 0, 0, null);
            }
            if (o != null) {
                g.drawImage(o, 0, 0, null);
            }
            g.dispose();
        }
        templateCache.put(key, out);
        return out;
    }

    public boolean hasTemplate(String file) {
        return template(file) != null;
    }

    /** Шрифт из вшитых (gui/resources/fonts) нужного размера в пикселях. */
    public Font font(String file, double px) {
        Font base = fontCache.get(file);
        if (base == null) {
            try (InputStream in = CardAssets.class.getResourceAsStream("/fonts/" + file)) {
                base = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception e) {
                base = new Font(Font.SANS_SERIF, Font.BOLD, 12);
            }
            fontCache.put(file, base);
        }
        return base.deriveFont((float) px);
    }

    // ==================== картинки ====================

    static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    static BufferedImage cropAlpha(BufferedImage im) {
        int w = im.getWidth();
        int h = im.getHeight();
        int x0 = w;
        int y0 = h;
        int x1 = -1;
        int y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((im.getRGB(x, y) >>> 24) != 0) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 < 0) {
            return im;
        }
        return im.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    /** Качественное масштабирование: уменьшение ступенями по половине, затем бикубика. */
    static BufferedImage scale(BufferedImage src, int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) {
            cur = draw(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        }
        return draw(cur, w, h);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Вписать в прямоугольник w×h с сохранением пропорций. */
    static BufferedImage fit(BufferedImage im, double w, double h) {
        double k = Math.min(w / im.getWidth(), h / im.getHeight());
        return scale(im, (int) Math.round(im.getWidth() * k), (int) Math.round(im.getHeight() * k));
    }

    /** Ключи иконок, которые мастерская знает по короткому имени (как у рисовальщика). */
    public static final Map<String, String> ALIAS = new HashMap<>();
    static {
        String[][] a = {
            // номера выгрузки 03.10.2026
            {"coin", "1"}, {"монета", "1"}, {"ammo", "3"}, {"боеприпас", "3"}, {"kel", "5"},
            {"келемий", "5"}, {"gear", "8"}, {"trophy", "9"}, {"трофей", "9"}, {"box", "13"},
            {"контейнер", "13"}, {"arsenal", "15"}, {"star", "22"}, {"heart", "23"},
            {"ring", "25"}, {"spec", "26"}, {"спец", "26"}, {"inf", "28"}, {"cell", "29"},
            {"military", "30"}, {"build_mode", "32"}, {"стройка", "32"},
            {"swap", "34"}, {"питание", "34"}, {"assembly", "35"}, {"снабжение", "35"},
            {"supply", "35"}, {"mining", "36"}, {"добыча", "36"}, {"command", "37"},
            {"командование", "37"}, {"movement", "38"}, {"combat", "39"}, {"бой", "39"},
            {"develop", "40"}, {"развитие", "40"}, {"market", "41"}, {"рынок", "41"},
            {"science", "42"}, {"наука", "42"}, {"science_track", "43"}, {"order", "44"},
            {"mod_attack", "45"}, {"mod_assembly", "46"}, {"gild", "48"}, {"troops", "54"},
            {"modules_move", "57"}, {"troops_plus", "69"}, {"buildings", "75"},
            {"reaction", "78"}, {"реакция", "78"}, {"arrow", "81"}, {"container_cell", "85"},
            {"приказ", "92"},
        };
        for (String[] p : a) {
            ALIAS.put(p[0], p[1]);
        }
    }

    /** Ключ иконки файла по тому, что написано в фигурных скобках. */
    public static String key(String token) {
        String t = token.trim();
        return ALIAS.getOrDefault(t, t);
    }

    /** Разбить строку фишек «{25} {1}{1} =1» на фишки: иконки и слова. */
    public static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        Matcher m = Pattern.compile("\\{([^}]+)\\}|(\\S+)").matcher(s);
        while (m.find()) {
            out.add(m.group(1) != null ? "{" + m.group(1) + "}" : m.group(2));
        }
        return out;
    }
}
