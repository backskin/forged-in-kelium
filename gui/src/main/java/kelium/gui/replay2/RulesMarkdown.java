package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import kelium.dataio.GameConfig;

/**
 * RulesMarkdown — КНИГА ПРАВИЛ В ЦИФРОВОЙ ВЕРСИИ: главы из markdown в справочник.
 *
 * <p>Заказ дизайнера 25.09.2026: «финальный свод всех правил иметь в качестве
 * справочника в цифровой версии игры, чтобы можно было подсмотреть, почитать, по
 * главам разобрать, по ключевым словам найти». Свод — это главы книги правил
 * ({@code rules/Книга правил — черновик/NN — *.md}): по ним же верстается печатная
 * книга ({@code tools/книга/md2page.py}), и другого текста правил, который
 * дизайнер правит, нет.
 *
 * <p><b>Текст читается с диска при каждом открытии главы</b>, а не зашивается в
 * программу: поправили главу — открыли её снова, и справочник уже новый.
 *
 * <p>Разметка — то же узкое подмножество, что понимает сборщик вёрстки:
 * заголовки {@code # / ## / ###}, абзацы, списки, таблицы, врезки {@code > **Важно!**},
 * {@code **жирный**}, {@code [иконка: имя]} (картинка из {@code rules/иконки-экспорт}
 * или {@code rules/иконки}), {@code [[рисунок]]} (картинка вёрстки
 * {@code tools/книга/_рисунок.svg} — растр с выносками; выноски дорисовываются
 * здесь же), {@code :фазы: … :конец:}. Вёрсточные пометки ({@code <!-- стр -->},
 * {@code :надвое:}, {@code :крупно:}, заглушки иллюстраций) в справочник не
 * попадают: они про полосу бумаги, а не про правила.
 */
public final class RulesMarkdown {

    private RulesMarkdown() {
    }

    // ==================== где лежит книга ====================

    /** Системное свойство: явный путь к папке с главами (для особых сборок). */
    public static final String PROP_DIR = "kelium.rules";

    private static final Pattern CHAPTER_FILE = Pattern.compile("(\\d\\d) — .+\\.md");

    /**
     * Папка с главами книги правил или {@code null}, если её нет.
     *
     * <p>Порядок поиска: свойство {@value #PROP_DIR}; рядом с каталогом данных
     * ({@code <корень>/rules/Книга правил…} — так лежит проект, и на машине
     * разработчика exe смотрит в рабочую папку, как и за data); внутри каталога
     * данных ({@code data/rules/…}) — на случай сборки, куда книгу положили в data.
     */
    public static Path bookDir() {
        String prop = System.getProperty(PROP_DIR);
        if (prop != null && !prop.isBlank() && Files.isDirectory(Path.of(prop))) {
            return Path.of(prop);
        }
        Path data = GameConfig.resolveDataRoot(null).toAbsolutePath().normalize();
        List<Path> roots = new ArrayList<>();
        if (data.getParent() != null) {
            roots.add(data.getParent().resolve("rules"));
        }
        roots.add(data.resolve("rules"));
        for (Path r : roots) {
            Path found = findBook(r);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Path findBook(Path rules) {
        if (!Files.isDirectory(rules)) {
            return null;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rules, "Книга правил*")) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    return p;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /** Главы книги по порядку (без плана книги «00» и служебных файлов). */
    public static List<Path> chapters(Path dir) {
        List<Path> out = new ArrayList<>();
        if (dir == null) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.md")) {
            for (Path p : ds) {
                Matcher m = CHAPTER_FILE.matcher(p.getFileName().toString());
                if (m.matches() && !"00".equals(m.group(1))) {
                    out.add(p);
                }
            }
        } catch (IOException e) {
            return out;
        }
        out.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return out;
    }

    /** Номер главы по имени файла («04 — Основы игры.md» → 4). */
    public static int chapterNumber(Path file) {
        Matcher m = CHAPTER_FILE.matcher(file.getFileName().toString());
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }

    static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            return null;
        }
    }

    // ==================== дерево разделов ====================

    /** Заголовок главы из первой строки «# …»; нет её — имя файла. */
    static String chapterTitle(String md, Path file) {
        if (md != null) {
            for (String ln : md.split("\n")) {
                if (ln.startsWith("# ")) {
                    return ln.substring(2).trim();
                }
            }
        }
        String n = file.getFileName().toString();
        return n.substring(0, n.length() - 3);
    }

    /** «Глава 4. Основы игры» → «4. Основы игры»: в дереве слово «Глава» лишнее. */
    static String shortTitle(String title) {
        Matcher m = Pattern.compile("Глава (\\d+)\\.\\s*(.+)").matcher(title);
        return m.matches() ? m.group(1) + ". " + m.group(2) : title;
    }

    /**
     * Разделы книги: по узлу на главу, внутри — её «##», внутри них — «###».
     * Все узлы главы показывают одну и ту же страницу — главу целиком; узел
     * раздела лишь прокручивает её к своему заголовку.
     */
    public static List<HelpBook.Section> chapterSections(Path dir) {
        List<HelpBook.Section> out = new ArrayList<>();
        for (Path f : chapters(dir)) {
            String md = read(f);
            String page = "book-" + String.format("%02d", chapterNumber(f));
            HelpBook.Section ch = new HelpBook.Section(page, shortTitle(chapterTitle(md, f)),
                null, page, -1, f);
            out.add(ch);
            if (md == null) {
                continue;
            }
            HelpBook.Section h2 = null;
            int ordinal = 0;
            for (String ln : md.split("\n")) {
                boolean two = ln.startsWith("## ");
                boolean three = ln.startsWith("### ");
                if (!two && !three) {
                    continue;
                }
                String t = plainInline(ln.substring(two ? 3 : 4).trim());
                HelpBook.Section s = new HelpBook.Section(page + "-" + ordinal, t, null, page,
                    ordinal, f);
                ordinal++;
                if (two || h2 == null) {
                    ch.children.add(s);
                    if (two) {
                        h2 = s;
                    }
                } else {
                    h2.children.add(s);
                }
            }
        }
        return out;
    }

    /** Ветка «Книга правил» для общего справочника разбора партии. */
    static HelpBook.Section tree() {
        Path dir = bookDir();
        HelpBook.Section root = new HelpBook.Section("book", "Книга правил",
            () -> intro(dir));
        root.children.addAll(chapterSections(dir));
        return root;
    }

    private static String intro(Path dir) {
        HelpBook.Html h = new HelpBook.Html();
        if (dir == null) {
            h.p("<i>Книга правил не найдена: рядом с каталогом данных нет папки "
                + "«rules/Книга правил…».</i>");
            return h.done();
        }
        h.p("Главы книги правил — те же, по которым верстается печатная книга. Текст "
            + "читается прямо из файлов глав, поэтому правка главы видна здесь, как "
            + "только главу откроют снова.");
        h.p("Поиск сверху ищет по тексту всех глав: без учёта регистра, «е» и «ё» "
            + "не различаются. Щелчок по найденному месту открывает главу на нём.");
        h.note("Папка глав: " + HelpBook.esc(dir.toString()));
        return h.done();
    }

    // ==================== markdown → html ====================

    /** Краски и размеры, в которых набирается глава. */
    public record Style(String ink, String ink2, String ink3, String tile, String panel,
                        String border, String accent, String important, int iconPx,
                        int figureWidth, boolean prepareFigures) {
    }

    /** Глава целиком, html-телом (без обёртки {@code <html><body>}). */
    public static String render(Path file, Style st) {
        String md = read(file);
        if (md == null) {
            return "<p><i>Главу не прочитать: " + HelpBook.esc(file.toString()) + "</i></p>";
        }
        return new Renderer(st, file).chapter(md);
    }

    /** Строка разметки без разметки — для названий в дереве. */
    static String plainInline(String s) {
        return s.replace("**", "").replaceAll("\\[иконка:\\s*([^\\]]+)\\]", "")
            .replaceAll("\\s+", " ").trim();
    }

    private static final class Renderer {

        private final Style st;
        private final Path file;
        private final StringBuilder sb = new StringBuilder();

        Renderer(Style st, Path file) {
            this.st = st;
            this.file = file;
        }

        String chapter(String md) {
            String[] lines = md.split("\n", -1);
            int i = 0;
            while (i < lines.length) {
                String ln = lines[i];
                String t = ln.trim();
                if (t.isEmpty() || t.equals("---") || t.startsWith("<!--")
                        || t.equals(":надвое:") || t.equals(":крупно:") || t.equals(":вовсю:")
                        || t.startsWith(":добор:") || t.startsWith(":заглавная:")) {
                    i++;
                    continue;
                }
                if (ln.startsWith("# ")) {
                    title(ln.substring(2).trim());
                    i++;
                    continue;
                }
                if (ln.startsWith("## ")) {
                    sb.append("<h2 style='font-size:135%;margin:18px 0 4px 0;color:")
                        .append(st.ink()).append("'>").append(inline(ln.substring(3).trim()))
                        .append("</h2>");
                    i++;
                    continue;
                }
                if (ln.startsWith("### ")) {
                    sb.append("<h3 style='font-size:112%;margin:12px 0 2px 0;color:")
                        .append(st.ink2()).append("'>").append(inline(ln.substring(4).trim()))
                        .append("</h3>");
                    i++;
                    continue;
                }
                if (t.equals(":фазы:")) {
                    i = phases(lines, i + 1);
                    continue;
                }
                if (ln.startsWith(">")) {
                    i = quote(lines, i);
                    continue;
                }
                if (t.matches("\\[\\[[^\\]]+\\]\\]")) {
                    figure(t.substring(2, t.length() - 2).trim());
                    i++;
                    continue;
                }
                if (ln.startsWith("|")) {
                    i = table(lines, i);
                    continue;
                }
                if (ln.startsWith("[")) {
                    i = bracket(lines, i);
                    continue;
                }
                if (ln.matches("\\s*(-|\\d+\\.)\\s+.*")) {
                    i = list(lines, i);
                    continue;
                }
                i = paragraph(lines, i);
            }
            return sb.toString();
        }

        private void title(String title) {
            Matcher m = Pattern.compile("(Глава \\d+)\\.\\s*(.+)").matcher(title);
            if (m.matches()) {
                sb.append("<div style='font-size:85%;font-weight:bold;color:").append(st.ink3())
                    .append(";margin:0'>").append(esc(m.group(1).toUpperCase(Locale.ROOT)))
                    .append("</div>");
                title = m.group(2);
            }
            sb.append("<div style='font-family:").append(Theme.displayFamily())
                .append(";font-size:170%;margin:0 0 6px 0;color:").append(st.ink()).append("'>")
                .append(inline(title)).append("</div>");
        }

        private int paragraph(String[] lines, int i) {
            List<String> p = new ArrayList<>();
            p.add(lines[i].trim());
            i++;
            while (i < lines.length && !lines[i].isBlank()
                    && !lines[i].matches("(#|>|\\||-\\s|\\d+\\.\\s|\\[|---|<!--|:[а-яё]+:).*")) {
                p.add(lines[i].trim());
                i++;
            }
            sb.append("<p style='margin:6px 0'>").append(inline(String.join(" ", p)))
                .append("</p>");
            return i;
        }

        private int list(String[] lines, int i) {
            boolean ordered = !lines[i].trim().startsWith("-");
            List<String> items = new ArrayList<>();
            while (i < lines.length) {
                Matcher mm = Pattern.compile("(-|\\d+\\.)\\s+(.*)").matcher(lines[i]);
                if (mm.matches() && mm.group(1).equals("-") != ordered) {
                    items.add(mm.group(2));
                } else if (lines[i].startsWith("  ") && !items.isEmpty() && !lines[i].isBlank()) {
                    String cont = lines[i].trim();
                    Matcher sub = Pattern.compile("(-|\\d+\\.)\\s+(.*)").matcher(cont);
                    if (sub.matches()) {
                        // Вложенный пункт — отдельной строкой с тире внутри того же.
                        items.set(items.size() - 1, items.get(items.size() - 1) + "\n– "
                            + sub.group(2));
                    } else {
                        items.set(items.size() - 1, items.get(items.size() - 1) + " " + cont);
                    }
                } else {
                    break;
                }
                i++;
            }
            String tag = ordered ? "ol" : "ul";
            sb.append('<').append(tag).append(" style='margin:4px 0 6px 22px'>");
            for (String it : items) {
                sb.append("<li style='margin:3px 0'>")
                    .append(inline(it).replace("\n", "<br>")).append("</li>");
            }
            sb.append("</").append(tag).append('>');
            return i;
        }

        private int quote(String[] lines, int i) {
            List<String> q = new ArrayList<>();
            while (i < lines.length && lines[i].startsWith(">")) {
                q.add(lines[i].substring(1).trim());
                i++;
            }
            String text = String.join(" ", q).trim();
            Matcher m = Pattern.compile("\\*\\*(.+?)\\*\\*\\s*(.*)").matcher(text);
            String label = "Пример";
            String rest = text;
            if (m.matches()) {
                label = m.group(1).replaceAll("\\.$", "");
                rest = m.group(2);
            }
            boolean important = label.startsWith("Важно");
            String bar = important ? st.important() : st.accent();
            // ВРЕЗКА — ТАБЛИЦЕЙ ИЗ ДВУХ ЯЧЕЕК: полоса слева и текст. Рамки из CSS
            // Swing рисует через раз, а таблицу с цветом ячеек — всегда.
            sb.append("<table width='100%' cellspacing='0' cellpadding='0' style='margin:8px 0'>"
                    + "<tr><td width='4' bgcolor='").append(bar).append("'></td>")
                .append("<td bgcolor='").append(st.tile()).append("' style='padding:6px 10px'>")
                .append("<div style='font-size:85%;font-weight:bold;color:").append(bar)
                .append("'>").append(esc(label.toUpperCase(Locale.ROOT))).append("</div>")
                .append("<div style='margin-top:2px'>").append(inline(rest)).append("</div>")
                .append("</td></tr></table>");
            return i;
        }

        private int table(String[] lines, int i) {
            List<String[]> rows = new ArrayList<>();
            while (i < lines.length && lines[i].startsWith("|")) {
                String row = lines[i].trim();
                row = row.substring(1, row.endsWith("|") && row.length() > 1
                    ? row.length() - 1 : row.length());
                String[] cells = row.split("\\|", -1);
                for (int k = 0; k < cells.length; k++) {
                    cells[k] = cells[k].trim();
                }
                if (!String.join("", cells).matches("[-: ]*")) {
                    rows.add(cells);
                }
                i++;
            }
            if (rows.isEmpty()) {
                return i;
            }
            sb.append("<table cellspacing='1' cellpadding='5' bgcolor='").append(st.border())
                .append("' style='margin:6px 0'>");
            for (int r = 0; r < rows.size(); r++) {
                sb.append("<tr>");
                for (String c : rows.get(r)) {
                    if (r == 0) {
                        String h = c.isEmpty() ? c
                            : Character.toUpperCase(c.charAt(0)) + c.substring(1);
                        sb.append("<th align='left' valign='top' bgcolor='").append(st.tile())
                            .append("' style='color:").append(st.ink2()).append("'>")
                            .append(inline(h)).append("</th>");
                    } else {
                        sb.append("<td valign='top' bgcolor='").append(st.panel()).append("'>")
                            .append(inline(c)).append("</td>");
                    }
                }
                sb.append("</tr>");
            }
            sb.append("</table>");
            return i;
        }

        private int phases(String[] lines, int i) {
            sb.append("<table width='100%' cellspacing='6' cellpadding='6' style='margin:6px 0'><tr>");
            while (i < lines.length && !lines[i].trim().equals(":конец:")) {
                String ln = lines[i].trim();
                if (!ln.isEmpty()) {
                    int bar = ln.indexOf('|');
                    String name = bar < 0 ? ln : ln.substring(0, bar).trim();
                    String text = bar < 0 ? "" : ln.substring(bar + 1).trim();
                    sb.append("<td valign='top' bgcolor='").append(st.tile()).append("'><b>")
                        .append(inline(name)).append("</b><br>").append(inline(text))
                        .append("</td>");
                }
                i++;
            }
            sb.append("</tr></table>");
            return i + 1;
        }

        /** Строка в квадратных скобках: значок с подписью, заглушка или пометка. */
        private int bracket(String[] lines, int i) {
            StringBuilder q = new StringBuilder(lines[i].trim());
            while (!q.toString().endsWith("]") && i + 1 < lines.length && !lines[i + 1].isBlank()
                    && !lines[i + 1].matches("(#|>|\\||-|<!--).*")) {
                i++;
                q.append(' ').append(lines[i].trim());
            }
            i++;
            String all = q.toString();
            if (!all.endsWith("]")) {
                // Не скобка-строка, а абзац, начатый значком: «[иконка: …] текст».
                sb.append("<p style='margin:6px 0'>").append(inline(all)).append("</p>");
                return i;
            }
            String inner = all.substring(1, all.length() - 1).trim();
            String low = inner.toLowerCase(Locale.ROOT);
            if (low.startsWith("иконка:") && inner.indexOf(']') < 0) {
                String[] nameCap = inner.substring(7).split("—", 2);
                String cap = nameCap.length > 1 ? nameCap[1].trim() : nameCap[0].trim();
                sb.append("<p style='margin:6px 0'>").append(icon(nameCap[0], st.iconPx() * 2))
                    .append("&nbsp; ").append(inline(cap)).append("</p>");
                return i;
            }
            if (low.startsWith("иллюстрация") || low.startsWith("художественный текст")) {
                // Места под будущие рисунки и текст о мире — вёрстка, не правило.
                return i;
            }
            sb.append("<p style='margin:6px 0;color:").append(st.ink3()).append("'><i>[")
                .append(inline(inner)).append("]</i></p>");
            return i;
        }

        private void figure(String name) {
            Path src = figureSource(file, name);
            if (src == null) {
                sb.append("<p style='margin:6px 0;color:").append(st.ink3())
                    .append("'><i>[рисунок «").append(esc(name))
                    .append("» — смотрите в печатной книге]</i></p>");
                return;
            }
            Figure f = st.prepareFigures() ? Figures.readyOrRequest(src) : Figures.planned(src);
            if (f == null) {
                // РИСУНОК ЕЩЁ СОБИРАЕТСЯ в фоне: пока — пустое место того же рода
                // (картинка, а не текст, чтобы смещения поиска не поехали), потом
                // окно пересоберёт главу само.
                sb.append("<p align='center' style='margin:8px 0'><img src='")
                    .append(Figures.placeholder().toUri()).append("' width='")
                    .append(Math.max(1, st.figureWidth() / 2)).append("' height='")
                    .append(Theme.px(140)).append("'></p>");
                return;
            }
            sb.append("<p align='center' style='margin:8px 0'><img src='")
                .append(f.png().toUri()).append('\'');
            if (f.width() > 0 && f.height() > 0) {
                int boxW = f.wide() ? st.figureWidth() : Math.min(st.figureWidth(), Theme.px(560));
                int w = (int) Math.round(boxW * f.share());
                double k = Math.min(1.0, Math.min(w / (double) f.width(),
                    Theme.px(560) / (double) f.height()));
                sb.append(" width='").append(Math.max(1, (int) Math.round(f.width() * k)))
                    .append("' height='").append(Math.max(1, (int) Math.round(f.height() * k)))
                    .append('\'');
            }
            sb.append("></p>");
        }

        // ---------- строка ----------

        private static final Pattern ICON = Pattern.compile("\\[иконка:\\s*([^\\]]+)\\]");
        private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
        private static final Pattern ITALIC = Pattern.compile("(?<![\\*\\w])\\*([^*\\s][^*]*?)\\*(?!\\*)");
        private static final Pattern CODE = Pattern.compile("`([^`]+)`");
        private static final Pattern CHAPTER_REF = Pattern.compile(
            "(глав[аеуыой]{1,2})\\s+(\\d{1,2})");

        String inline(String s) {
            String out = esc(s);
            out = BOLD.matcher(out).replaceAll("<b>$1</b>");
            out = ITALIC.matcher(out).replaceAll("<i>$1</i>");
            out = CODE.matcher(out).replaceAll("$1");
            // Ссылки «(глава 8)» — щелчком в эту главу.
            out = CHAPTER_REF.matcher(out).replaceAll(
                "<a href='book:$2' style='color:" + st.accent() + "'>$1 $2</a>");
            Matcher m = ICON.matcher(out);
            StringBuilder b = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(b, Matcher.quoteReplacement(icon(m.group(1), st.iconPx())));
            }
            m.appendTail(b);
            return b.toString();
        }

        /** Значок по имени; нет файла — имя в скобках тихим цветом. */
        private String icon(String name, int px) {
            String key = name.split("—")[0].trim().toLowerCase(Locale.ROOT).replace(' ', '-');
            Path p = iconFile(file, key);
            if (p == null) {
                return "<span style='color:" + st.ink3() + "'>[" + key + "]</span>";
            }
            int[] wh = Figures.size(p);
            int w = px;
            int h = px;
            if (wh != null && wh[0] > 0 && wh[1] > 0) {
                w = (int) Math.max(1, Math.round(px * wh[0] / (double) wh[1]));
                if (w > px * 3) {
                    w = px * 3;
                    h = (int) Math.max(1, Math.round(w * wh[1] / (double) wh[0]));
                }
            }
            return "<img src='" + p.toUri() + "' width='" + w + "' height='" + h
                + "' align='middle'>";
        }
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ==================== значки и рисунки ====================

    /** {@code rules/} — родитель папки книги. */
    private static Path rulesRoot(Path chapter) {
        Path book = chapter.getParent();
        return book == null ? null : book.getParent();
    }

    private static final Map<String, java.util.Optional<Path>> ICON_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    static Path iconFile(Path chapter, String key) {
        Path rules = rulesRoot(chapter);
        if (rules == null) {
            return null;
        }
        String ck = rules + "|" + key;
        java.util.Optional<Path> known = ICON_CACHE.get(ck);
        if (known != null) {
            return known.orElse(null);
        }
        Path found = null;
        for (String dir : new String[]{"иконки-экспорт", "иконки"}) {
            Path p = rules.resolve(dir).resolve(key + ".png");
            if (Files.isRegularFile(p)) {
                found = p;
                break;
            }
        }
        ICON_CACHE.put(ck, java.util.Optional.ofNullable(found));
        return found;
    }

    /** Файл рисунка вёрстки: {@code <корень>/tools/книга/_имя.svg} или рядом с книгой. */
    static Path figureSource(Path chapter, String name) {
        Path rules = rulesRoot(chapter);
        List<Path> dirs = new ArrayList<>();
        if (rules != null && rules.getParent() != null) {
            dirs.add(rules.getParent().resolve("tools").resolve("книга"));
        }
        if (chapter.getParent() != null) {
            dirs.add(chapter.getParent().resolve("рисунки"));
        }
        for (Path d : dirs) {
            Path p = d.resolve("_" + name + ".svg");
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    /** Готовый рисунок: png в кэше, его размер и доля ширины колонки из вёрстки. */
    record Figure(Path png, int width, int height, double share, boolean wide) {
    }

    /**
     * РИСУНКИ ВЁРСТКИ. Файл {@code _имя.svg} — это кусок html вёрстки: растровая
     * картинка (base64) и поверх неё svg с выносками — линии, кружки, буквы А, Б,
     * В. Swing не рисует ни svg, ни data-адреса, поэтому картинка собирается здесь
     * один раз: растр плюс выноски, в png во временной папке. Имя файла в кэше
     * включает время правки источника — перерисовали рисунок, собрался новый.
     */
    static final class Figures {

        private Figures() {
        }

        private static final Pattern IMG = Pattern.compile(
            "data:image/(?:png|jpeg|jpg);base64,([A-Za-z0-9+/=]+)");
        private static final Pattern VIEWBOX = Pattern.compile("viewBox=\"([\\d.\\s-]+)\"");
        private static final Pattern WIDTH = Pattern.compile("class=\"рис\" style=\"width:([\\d.]+)%");
        private static final Pattern ELEMENT = Pattern.compile(
            "<(line|polyline|circle|text)\\b([^>]*)>(?:([^<]*)</text>)?");
        private static final Pattern ATTR = Pattern.compile("([\\w-]+)=\"([^\"]*)\"");
        private static final int MAX_W = 1400;

        private static final Map<String, int[]> SIZES =
            new java.util.concurrent.ConcurrentHashMap<>();
        /** Собранные рисунки: файл в кэше → размер и доля колонки. */
        private static final Map<Path, Figure> READY = new java.util.concurrent.ConcurrentHashMap<>();
        private static final Set<Path> QUEUED = java.util.concurrent.ConcurrentHashMap.newKeySet();
        /** Кому сказать «рисунок готов» (на потоке Swing). */
        private static final List<Runnable> LISTENERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();
        /**
         * СБОРКА РИСУНКОВ — В ФОНЕ, ПО ОДНОМУ. Рисунок главы 4 — это несколько
         * мегабайт base64 на каждый; собрать все сразу на потоке Swing значило бы
         * заморозить окно на десяток секунд при первом открытии главы.
         */
        private static final java.util.concurrent.ExecutorService WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "rules-figures");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });

        static void addListener(Runnable r) {
            LISTENERS.add(r);
        }

        static void removeListener(Runnable r) {
            LISTENERS.remove(r);
        }

        /** Готовый рисунок или null — тогда он поставлен в очередь на сборку. */
        static Figure readyOrRequest(Path src) {
            Path out = cacheFile(src);
            Figure f = READY.get(out);
            if (f != null) {
                return f;
            }
            if (Files.isRegularFile(out)) {
                f = prepare(src);
                READY.put(out, f);
                return f;
            }
            request(src);
            return null;
        }

        /** Поставить рисунок в очередь (повторная просьба ничего не добавляет). */
        static void request(Path src) {
            Path out = cacheFile(src);
            if (READY.containsKey(out) || !QUEUED.add(out)) {
                return;
            }
            WORKER.submit(() -> {
                try {
                    READY.put(out, prepare(src));
                } finally {
                    QUEUED.remove(out);
                }
                javax.swing.SwingUtilities.invokeLater(() -> LISTENERS.forEach(Runnable::run));
            });
        }

        /** Заранее собрать все рисунки глав — пока читают первую. */
        static void warm(List<Path> chapters) {
            for (Path ch : chapters) {
                String md = read(ch);
                if (md == null) {
                    continue;
                }
                Matcher m = Pattern.compile("(?m)^\\[\\[([^\\]]+)\\]\\]\\s*$").matcher(md);
                while (m.find()) {
                    Path src = figureSource(ch, m.group(1).trim());
                    if (src != null && !Files.isRegularFile(cacheFile(src))) {
                        request(src);
                    }
                }
            }
        }

        private static Path placeholder;

        /** Прозрачная точка — место под рисунок, который ещё собирается. */
        static synchronized Path placeholder() {
            if (placeholder != null && Files.isRegularFile(placeholder)) {
                return placeholder;
            }
            Path p = Path.of(System.getProperty("java.io.tmpdir"), "kelium-rules-figures",
                "_пусто.png");
            try {
                Files.createDirectories(p.getParent());
                if (!Files.isRegularFile(p)) {
                    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png",
                        p.toFile());
                }
            } catch (IOException e) {
                // нет места под точку — Swing покажет значок «нет картинки», и только
            }
            placeholder = p;
            return p;
        }

        static int[] size(Path p) {
            String k = p.toString();
            int[] c = SIZES.get(k);
            if (c != null) {
                return c;
            }
            try (var in = ImageIO.createImageInputStream(p.toFile())) {
                var readers = ImageIO.getImageReaders(in);
                if (readers.hasNext()) {
                    var r = readers.next();
                    try {
                        r.setInput(in);
                        c = new int[]{r.getWidth(0), r.getHeight(0)};
                    } finally {
                        r.dispose();
                    }
                }
            } catch (IOException | RuntimeException e) {
                c = null;
            }
            if (c != null) {
                SIZES.put(k, c);
            }
            return c;
        }

        private static Path cacheFile(Path src) {
            long stamp;
            long len;
            try {
                stamp = Files.getLastModifiedTime(src).toMillis();
                len = Files.size(src);
            } catch (IOException e) {
                stamp = 0;
                len = 0;
            }
            String n = src.getFileName().toString().replaceAll("\\.svg$", "");
            return Path.of(System.getProperty("java.io.tmpdir"), "kelium-rules-figures",
                n + "-" + Long.toHexString(stamp) + "-" + Long.toHexString(len) + ".png");
        }

        /** Куда ляжет рисунок — без сборки (для поиска: тексту картинка не нужна). */
        static Figure planned(Path src) {
            return new Figure(cacheFile(src), 0, 0, 1.0, false);
        }

        /** Собрать рисунок (или взять из кэша) и узнать его размер. */
        static Figure prepare(Path src) {
            Path out = cacheFile(src);
            String html = null;
            double share = 1.0;
            boolean wide = false;
            try {
                html = Files.readString(src, StandardCharsets.UTF_8);
                wide = html.contains("рисунок-во-всю");
                Matcher w = WIDTH.matcher(html);
                if (w.find()) {
                    share = Math.max(0.2, Math.min(1.0, Double.parseDouble(w.group(1)) / 100.0));
                }
                if (!Files.isRegularFile(out)) {
                    BufferedImage img = compose(html);
                    if (img != null) {
                        Files.createDirectories(out.getParent());
                        Path tmp = out.resolveSibling(out.getFileName() + ".part");
                        ImageIO.write(img, "png", tmp.toFile());
                        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IOException | RuntimeException e) {
                // Рисунок не собрался — статья всё равно откроется, без него.
            }
            int[] wh = Files.isRegularFile(out) ? size(out) : null;
            return new Figure(out, wh == null ? 0 : wh[0], wh == null ? 0 : wh[1], share, wide);
        }

        private static BufferedImage compose(String html) throws IOException {
            Matcher im = IMG.matcher(html);
            if (!im.find()) {
                return null;
            }
            BufferedImage raster = ImageIO.read(new ByteArrayInputStream(
                Base64.getDecoder().decode(im.group(1))));
            if (raster == null) {
                return null;
            }
            double k = Math.min(1.0, MAX_W / (double) raster.getWidth());
            int w = (int) Math.round(raster.getWidth() * k);
            int h = (int) Math.round(raster.getHeight() * k);
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(raster, 0, 0, w, h, null);
            int svg = html.indexOf("<svg", im.end());
            Matcher vb = svg < 0 ? null : VIEWBOX.matcher(html);
            if (vb != null && vb.find(svg)) {
                String[] v = vb.group(1).trim().split("\\s+");
                double sx = w / Double.parseDouble(v[2]);
                double sy = h / Double.parseDouble(v[3]);
                int end = html.indexOf("</svg>", svg);
                callouts(g, html.substring(svg, end < 0 ? html.length() : end), sx, sy);
            }
            g.dispose();
            return out;
        }

        /** Выноски поверх растра: цвет — келемий вёрстки, буквы — белым по нему. */
        private static void callouts(Graphics2D g, String svg, double sx, double sy) {
            Color ink = Theme.darken(Theme.kelium(), 0.45);
            Color paper = Color.WHITE;
            double s = Math.min(sx, sy);
            Matcher m = ELEMENT.matcher(svg);
            while (m.find()) {
                Map<String, String> a = new HashMap<>();
                Matcher am = ATTR.matcher(m.group(2));
                while (am.find()) {
                    a.put(am.group(1), am.group(2));
                }
                float sw = (float) (num(a.get("stroke-width"), 4) * s);
                String cls = a.getOrDefault("class", "");
                switch (m.group(1)) {
                    case "line" -> {
                        g.setColor(ink);
                        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                        g.draw(new Line2D.Double(num(a.get("x1"), 0) * sx, num(a.get("y1"), 0) * sy,
                            num(a.get("x2"), 0) * sx, num(a.get("y2"), 0) * sy));
                    }
                    case "polyline" -> {
                        String[] pts = a.getOrDefault("points", "").trim().split("[\\s]+");
                        Path2D.Double p = new Path2D.Double();
                        boolean first = true;
                        for (String pt : pts) {
                            String[] xy = pt.split(",");
                            if (xy.length != 2) {
                                continue;
                            }
                            double x = Double.parseDouble(xy[0]) * sx;
                            double y = Double.parseDouble(xy[1]) * sy;
                            if (first) {
                                p.moveTo(x, y);
                                first = false;
                            } else {
                                p.lineTo(x, y);
                            }
                        }
                        g.setColor(ink);
                        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                        g.draw(p);
                    }
                    case "circle" -> {
                        double r = num(a.get("r"), 4) * s;
                        Ellipse2D.Double c = new Ellipse2D.Double(num(a.get("cx"), 0) * sx - r,
                            num(a.get("cy"), 0) * sy - r, r * 2, r * 2);
                        g.setColor(ink);
                        g.fill(c);
                        if (cls.contains("номер")) {
                            g.setColor(paper);
                            g.setStroke(new BasicStroke(Math.max(1f, sw)));
                            g.draw(c);
                        }
                    }
                    default -> {
                        String text = m.group(3) == null ? "" : m.group(3).trim();
                        if (text.isEmpty()) {
                            break;
                        }
                        float fs = (float) (num(a.get("font-size"), 40) * s);
                        Font f = Theme.narrow(12, Font.BOLD).deriveFont(fs);
                        g.setFont(f);
                        FontMetrics fm = g.getFontMetrics();
                        double x = num(a.get("x"), 0) * sx - fm.stringWidth(text) / 2.0;
                        double y = num(a.get("y"), 0) * sy + (fm.getAscent() - fm.getDescent()) / 2.0;
                        g.setColor(paper);
                        g.drawString(text, (float) x, (float) y);
                    }
                }
            }
        }

        private static double num(String s, double dflt) {
            if (s == null) {
                return dflt;
            }
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return dflt;
            }
        }
    }
}
