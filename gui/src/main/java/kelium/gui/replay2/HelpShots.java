package kelium.gui.replay2;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JComponent;

import kelium.dataio.ContentLibrary;
import kelium.dataio.GameConfig;
import kelium.gui.BoardsPanel;
import kelium.gui.GameRecorder;
import kelium.gui.SuperObjectivesPanel;
import kelium.report.ReplayRecord;
import kelium.rules.Ruleset;

/**
 * HelpShots — ГЕНЕРАТОР КАРТИНОК СПРАВОЧНИКА.
 *
 * <p>Картинки к статьям <b>снимаются с самого приложения</b>, а не рисуются
 * руками: нарисованная картинка расходится с кодом на первой же правке и потом
 * врёт увереннее, чем текст. Здесь партия проигрывается по-настоящему, приборы
 * ставятся на нужный кадр и отрисовываются в PNG — без окна, прямо в картинку.
 *
 * <p>Пересобрать картинки — одной командой:
 * {@code java -cp <classpath> kelium.gui.replay2.HelpShots}. Файлы кладутся в
 * {@code simulator/data/help/} по именам разделов ({@link HelpBook#shotSections()}),
 * оттуда их и берёт {@link HelpWindow}.
 *
 * <p>Заодно генератор готовит дизайнеру список карт без человеческого описания:
 * тексты — игровой контент, их пишет дизайнер, и ему нужен перечень, что осталось.
 */
public final class HelpShots {

    private HelpShots() {
    }

    /** Картинки снимают на СВЕТЛОЙ теме — так принято во всём приложении. */
    private static final boolean DARK_THEME = false;

    private static final int FIELD_W = 1100;
    private static final int FIELD_H = 760;

    public static void main(String[] args) {
        Theme.apply(DARK_THEME);
        Path out = args.length > 0 && !args[0].isBlank()
            ? Path.of(args[0]) : HelpWindow.helpDir();
        ReplayRecord rec = GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, 777,
            List.of("strat:hawk", "strat:dove", "explorer", "chaos"), null);
        List<String> made = generate(rec, out);
        System.out.println("[справочник] картинок собрано: " + made.size() + " → "
            + out.toAbsolutePath());
        for (String s : made) {
            System.out.println("  " + s);
        }
        Path list = writeMissingList(rec, Path.of("reports"));
        if (list != null) {
            System.out.println("[справочник] список карт без описания: "
                + list.toAbsolutePath());
        }
    }

    /**
     * Собрать картинки всех разделов по этой записи партии. Возвращает имена
     * собранных файлов. Раздел, который снять не удалось (нет данных, пустая
     * запись), молча пропускается: генератор не имеет права падать целиком из-за
     * одной картинки.
     */
    public static List<String> generate(ReplayRecord rec, Path outDir) {
        List<String> made = new ArrayList<>();
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            return made;
        }
        Session session = new Session();
        session.setContent(contentOf(rec));
        session.setRecord(rec);

        int build = frameWith(rec, true);
        int battle = frameWith(rec, false);

        shot(made, outDir, "start", () -> field(session, build, false));
        shot(made, outDir, "setup", () -> setupPanel(session));
        shot(made, outDir, "field", () -> field(session, build, false));
        shot(made, outDir, "tokens", () -> crop(field(session, build, false)));
        shot(made, outDir, "layers", () -> field(session, build, true));
        shot(made, outDir, "marks", () -> field(session, battle, false));
        shot(made, outDir, "strip", () -> strip(session));
        shot(made, outDir, "zone", () -> zone(session, battle));
        shot(made, outDir, "boards", () -> boards(rec));
        shot(made, outDir, "supers", () -> supers(rec));
        shot(made, outDir, "results", () -> results(session));
        shot(made, outDir, "timeline", () -> timeline(session));
        // ВТОРОЙ СПРАВОЧНИК — правила игры. Наглядность ему нужна не меньше, а
        // рисовать нечем, кроме того же приложения: показываем те же приборы,
        // только в статьях про сами правила.
        shot(made, outDir, "rg-basics", () -> field(session, build, false));
        shot(made, outDir, "rg-combat", () -> field(session, battle, false));
        shot(made, outDir, "rg-tech", () -> boards(rec));
        shot(made, outDir, "rg-boards", () -> zone(session, battle));
        shot(made, outDir, "rg-vp", () -> results(session));
        return made;
    }

    /** Что рисуем: поставщик картинки, который вправе вернуть null. */
    private interface Shot {
        BufferedImage make();
    }

    private static void shot(List<String> made, Path dir, String id, Shot shot) {
        try {
            BufferedImage img = shot.make();
            if (img == null) {
                return;
            }
            Path file = dir.resolve(id + ".png");
            ImageIO.write(img, "png", file.toFile());
            made.add(id + ".png");
        } catch (RuntimeException | IOException | Error e) {
            // Раздел без картинки — не беда: статья открывается и без неё.
            System.out.println("[справочник] " + id + ": картинку снять не удалось ("
                + e.getClass().getSimpleName() + ")");
        }
    }

    // ==================== сами приборы ====================

    private static BufferedImage field(Session session, int frame, boolean allLayers) {
        session.seek(frame);
        SceneField f = new SceneField(session);
        if (allLayers) {
            for (SceneField.Layer l : SceneField.Layer.values()) {
                f.setLayer(l, true);
            }
        }
        f.setSize(FIELD_W, FIELD_H);
        f.fitToWindow();
        return paint(f);
    }

    private static BufferedImage strip(Session session) {
        PlayerStrip s = new PlayerStrip(session, 0);
        s.setSize(Theme.px(900), Theme.px(Theme.H_STRIP));
        return paint(s);
    }

    private static BufferedImage timeline(Session session) {
        Timeline t = new Timeline(session);
        t.setSize(Theme.px(1000), Theme.px(Theme.H_TIMELINE));
        return paint(t);
    }

    private static BufferedImage results(Session session) {
        ResultsPanel r = new ResultsPanel(session);
        session.seekEnd();
        r.setSize(Theme.px(1000), Theme.px(640));
        return paint(r);
    }

    /**
     * Личная зона — это ПЛАНШЕТ игрока из разбора 2.0 ({@link BoardSheet}), а не
     * текстовая зона версии 1.0: в справочнике объясняется именно тот вид, который
     * дизайнер видит по кнопке «Личная зона».
     */
    private static BufferedImage zone(Session session, int frame) {
        if (!session.hasRecord()) {
            return null;
        }
        session.seek(frame);
        BoardSheet z = new BoardSheet(session, 0);
        z.setSize(Theme.px(900), Theme.px(660));
        return paint(z);
    }

    private static BufferedImage boards(ReplayRecord rec) {
        if (rec.frames.isEmpty()) {
            return null;
        }
        BoardsPanel b = new BoardsPanel();
        Ruleset rs = rulesetOf(rec);
        ContentLibrary lib = contentOf(rec);
        if (rs == null || lib == null) {
            return null;
        }
        b.setRules(rs, lib);
        b.show(rec, rec.frame(rec.frames.size() - 1).snapshot);
        b.setSize(Theme.px(1000), Theme.px(660));
        layoutTree(b);
        return paint(b);
    }

    private static BufferedImage supers(ReplayRecord rec) {
        if (rec.frames.isEmpty()) {
            return null;
        }
        ContentLibrary lib = contentOf(rec);
        if (lib == null) {
            return null;
        }
        SuperObjectivesPanel p = new SuperObjectivesPanel();
        p.setContent(lib);
        p.show(rec, rec.frame(rec.frames.size() - 1).snapshot);
        p.setSize(Theme.px(1000), Theme.px(620));
        layoutTree(p);
        return paint(p);
    }

    /**
     * Окно настроек — форма из списков и кнопок. Размер берём её собственный
     * желаемый: заданный «на глаз» обрезает правый край, а лишняя высота даёт
     * пустое поле вместо картинки.
     */
    private static BufferedImage setupPanel(Session session) {
        SetupPanel p = new SetupPanel(session, text -> { });
        java.awt.Dimension want = p.getPreferredSize();
        p.setSize(Math.max(want.width, Theme.px(900)),
            Math.max(want.height, Theme.px(200)));
        layoutTree(p);
        return paint(p);
    }

    // ==================== отрисовка ====================

    /** Нарисовать прибор в картинку. Без окна: рисуем прямо в BufferedImage. */
    private static BufferedImage paint(JComponent c) {
        int w = Math.max(1, c.getWidth());
        int h = Math.max(1, c.getHeight());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.bg());
        g.fillRect(0, 0, w, h);
        c.paint(g);
        g.dispose();
        return blank(img) ? null : img;
    }

    /** Пустая картинка (один цвет) в справочник не идёт: она только путает. */
    private static boolean blank(BufferedImage img) {
        int first = img.getRGB(0, 0);
        for (int y = 0; y < img.getHeight(); y += 3) {
            for (int x = 0; x < img.getWidth(); x += 3) {
                if (img.getRGB(x, y) != first) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Середина картинки крупнее — для статьи про жетоны. */
    private static BufferedImage crop(BufferedImage src) {
        if (src == null) {
            return null;
        }
        int w = src.getWidth() / 2;
        int h = src.getHeight() / 2;
        BufferedImage cut = src.getSubimage(src.getWidth() / 4, src.getHeight() / 4, w, h);
        BufferedImage out = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cut, 0, 0, w * 2, h * 2, null);
        g.dispose();
        return out;
    }

    /**
     * Разложить дети по местам. Приборы, рисующие себя сами, в этом не нуждаются,
     * а собранные из кнопок и списков — нуждаются: без окна никто не вызовет
     * раскладку, и всё окажется в точке 0×0.
     */
    private static void layoutTree(Component c) {
        if (c instanceof Container box) {
            box.doLayout();
            for (Component kid : box.getComponents()) {
                layoutTree(kid);
            }
        }
    }

    // ==================== данные записи ====================

    private static Ruleset rulesetOf(ReplayRecord rec) {
        String id = rec != null && rec.ruleset != null && !rec.ruleset.isBlank()
            ? rec.ruleset : GameConfig.DEFAULT_RULESET;
        try {
            return Ruleset.loadById(id,
                GameConfig.resolveDataRoot(null).resolve("rulesets"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ContentLibrary contentOf(ReplayRecord rec) {
        Ruleset rs = rulesetOf(rec);
        if (rs == null) {
            return null;
        }
        try {
            return ContentLibrary.forRuleset(rs, GameConfig.resolveDataRoot(null));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Перечень «вид набора → карта» подзаголовками, чтобы список было видно. */
    private static void appendList(StringBuilder sb, List<String[]> rows) {
        String last = "";
        for (String[] m : rows) {
            if (!m[0].equals(last)) {
                sb.append("\n### ").append(m[0]).append("\n\n");
                last = m[0];
            }
            sb.append("- ").append(m[1]).append('\n');
        }
    }

    /** Кадр со стройкой (или с боем): на пустой записи — нулевой. */
    private static int frameWith(ReplayRecord rec, boolean build) {
        for (int i = 0; i < rec.frames.size(); i++) {
            ReplayRecord.Frame f = rec.frames.get(i);
            if (build && f.highlight != null && !f.highlight.builds.isEmpty()) {
                return i;
            }
            if (!build && f.combat) {
                return i;
            }
        }
        return 0;
    }

    // ==================== список карт без описания ====================

    /**
     * Список «каких описаний не хватает» — дизайнеру. Тексты карт пишет он, и
     * список нужен ему как задание, а не нам как отчёт.
     */
    public static Path writeMissingList(ReplayRecord rec, Path reportsDir) {
        ContentLibrary lib = contentOf(rec);
        List<String[]> missing = HelpCards.missing(lib);
        StringBuilder sb = new StringBuilder();
        sb.append("# Карты без человеческого описания\n\n");
        sb.append("Описание карты живёт в её карточном наборе, ключом `описание` — "
            + "рядом с кодовыми параметрами, в том же файле. Пока ключа нет, разворот "
            + "карты в справочнике и в читалке честно пишет, что текст не написан.\n\n");
        sb.append("## В наборах, по которым идёт игра\n\n");
        if (lib == null) {
            sb.append("Карточные наборы не загрузились — список собрать нечем.\n");
        } else if (missing.isEmpty()) {
            sb.append("Описания написаны у всех карт.\n");
        } else {
            sb.append("Не хватает текстов: **").append(missing.size()).append("**.\n");
            appendList(sb, missing);
        }
        // ВСЕ МОДУЛИ, а не только играющие: каталог справочника показывает и старые,
        // и там честно написано «описание не написано» — список обязан это признавать.
        List<String[]> everywhere = HelpCards.missingByModule(
            GameConfig.resolveDataRoot(null));
        sb.append("\n## Во всех модулях, включая прежние\n\n");
        if (everywhere.isEmpty()) {
            sb.append("Описания написаны везде.\n");
        } else {
            sb.append("Не хватает текстов: **").append(everywhere.size())
                .append("**. Прежние модули — история: по ним не играют, и справочник "
                    + "честно пишет, что описания нет.\n");
            appendList(sb, everywhere);
        }
        try {
            Files.createDirectories(reportsDir);
            Path file = reportsDir.resolve("КАРТЫ-БЕЗ-ОПИСАНИЯ.md");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            return null;
        }
    }
}
