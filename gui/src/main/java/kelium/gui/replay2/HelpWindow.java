package kelium.gui.replay2;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import kelium.dataio.GameConfig;

/**
 * HelpWindow — ОКНО СПРАВОЧНИКА: дерево разделов слева, статья справа.
 *
 * <p>Раньше справка была одним текстом в одном окне («Как пользоваться»), и
 * дизайнер в приборах путался: нигде не было написано, что означает мелкая строка
 * под ресурсами и что за кружки рядом с арсеналом. Здесь разделов много, они
 * ищутся строкой сверху, а в статьях стоят картинки, снятые с самого приложения.
 *
 * <p>Содержание собирает {@link HelpBook}, картинки готовит {@link HelpShots}.
 * Окно открывается одно: второй вызов поднимает уже открытое.
 */
public final class HelpWindow {

    /** Тот же раздел файла настроек, что у главного окна. */
    private static final String PREF_NODE = "replay2";
    private static final String PREF_DIVIDER = "helpDivider";
    private static final String PREF_ZOOM = "helpZoom";

    /**
     * ШАГИ УВЕЛИЧЕНИЯ ТЕКСТА СТАТЬИ, в процентах от обычного. Ступеньки, а не
     * плавный ход: кегль в html пересчитывается в целые пункты, и мелкий шаг
     * половину нажатий не давал бы никакой разницы на экране.
     */
    private static final int[] ZOOMS = {80, 90, 100, 115, 130, 150, 175, 200, 240};

    /** Открытое окно — чтобы F1 поднимал его, а не плодил копии. */
    private static JFrame open;
    private static HelpWindow instance;

    private final List<HelpBook.Section> roots;
    private final kelium.dataio.AppSettings prefs =
        kelium.dataio.AppSettings.of(PREF_NODE);

    private JTree tree;
    private javax.swing.JEditorPane article;
    private JTextField search;
    private HelpBook.Section current;
    private int pictureWidth = Theme.px(620);
    /** Текущее увеличение статьи в процентах; помнится между запусками. */
    private int zoom = 100;
    private javax.swing.JLabel zoomLabel;
    /** Размеры картинок: файл читается один раз, а не на каждую перерисовку. */
    private final java.util.Map<String, int[]> sizes = new java.util.HashMap<>();

    private HelpWindow(HelpBook book) {
        this.roots = book.sections();
        this.zoom = nearestZoom(prefs.getInt(PREF_ZOOM, 100));
    }

    /** Показать справочник. Второй вызов поднимает уже открытое окно. */
    public static void show(Window owner, Session session) {
        if (open != null && open.isDisplayable()) {
            open.setVisible(true);
            open.toFront();
            open.requestFocus();
            return;
        }
        HelpWindow w = new HelpWindow(HelpBook.of(session));
        instance = w;
        open = w.build(owner);
    }

    /**
     * Открыть справочник как САМОСТОЯТЕЛЬНОЕ приложение ({@link HelpApp}, свой
     * exe). Отличие одно: закрытие окна закрывает программу — окно здесь не
     * гость главного, а единственное, что есть.
     */
    public static void standalone() {
        show(null, null);
        if (open != null) {
            open.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            open.setTitle("Справочник «Кристаллы Раздора» — правила и все карты");
        }
    }

    /**
     * Перекрасить открытый справочник при смене темы. Статья пересобирается
     * целиком: её цвета вписаны в html, и одной перерисовкой их не сменить.
     */
    public static void restyle() {
        if (instance == null || open == null || !open.isDisplayable()) {
            return;
        }
        instance.article.setBackground(Theme.panel());
        instance.tree.setBackground(Theme.panel());
        open.getContentPane().setBackground(Theme.bg());
        instance.render();
        open.repaint();
    }

    /**
     * ЗАКРЫТЬ СПРАВОЧНИК, если он открыт. Нужно при смене масштаба интерфейса:
     * окно собрано в прежних размерах, и оставлять его рядом с пересобранным
     * главным — значит держать на экране две разные вёрстки.
     */
    public static void closeIfOpen() {
        if (open != null) {
            open.dispose();
            open = null;
            instance = null;
        }
    }

    private JFrame build(Window owner) {
        JFrame f = new JFrame("Справочник — разбор партии «Кристаллы Раздора»");
        // Своей иконки у справочника нет — берёт иконку разбора партии: это тот
        // же справочник, что открывается оттуда по F1 (просьба дизайнера
        // 14.08.2026: заменить дефолтную чашку Java хоть чем-то узнаваемым).
        kelium.gui.Ui.setAppIcon(f, "replay2");
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        f.getContentPane().setLayout(new BorderLayout());
        f.getContentPane().setBackground(Theme.bg());

        search = new JTextField();
        search.setFont(Theme.body());
        search.setToolTipText(Ui2.tip("Поиск по названиям разделов: показываются только "
            + "те, в чьём названии есть эта строка."));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refill();
            }
        });
        JPanel top = new JPanel(new BorderLayout(Theme.px(8), 0));
        top.setBackground(Theme.panel());
        top.setBorder(BorderFactory.createEmptyBorder(Theme.px(8), Theme.px(8),
            Theme.px(8), Theme.px(8)));
        top.add(Ui2.caption("ПОИСК"), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);
        top.add(zoomBox(), BorderLayout.EAST);
        f.add(top, BorderLayout.NORTH);

        tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("справочник")));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setFont(Theme.body());
        tree.setRowHeight(Theme.px(22));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> showSelected());

        article = new javax.swing.JEditorPane("text/html", "");
        article.setEditable(false);
        article.setFont(Theme.body());
        article.setBackground(Theme.panel());
        JScrollPane articleScroll = new JScrollPane(article);
        articleScroll.setBorder(null);
        articleScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(16));
        // КАРТИНКА ПО ШИРИНЕ СТАТЬИ. Снимки сняты с приложения и шириной больше
        // окна: вставленные как есть, они уводят статью в боковую прокрутку, и
        // текст уезжает за край. Поэтому при каждом изменении ширины статья
        // пересобирается с новым размером картинки.
        articleScroll.getViewport().addComponentListener(
            new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    int w = articleScroll.getViewport().getWidth() - Theme.px(40);
                    if (Math.abs(w - pictureWidth) > Theme.px(16)) {
                        pictureWidth = w;
                        render();
                    }
                }
            });

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(null);
        treeScroll.setPreferredSize(new Dimension(Theme.px(280), Theme.px(560)));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll,
            articleScroll);
        split.setDividerLocation(prefs.getInt(PREF_DIVIDER, Theme.px(280)));
        split.setResizeWeight(0);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
            e -> prefs.putInt(PREF_DIVIDER, split.getDividerLocation()));
        f.add(split, BorderLayout.CENTER);

        bindEscape((JComponent) f.getContentPane(), f);
        bindZoomKeys((JComponent) f.getContentPane());
        refill();
        f.setSize(Theme.px(1040), Theme.px(700));
        f.setLocationRelativeTo(owner);
        f.setVisible(true);
        return f;
    }

    // ==================== увеличение статьи ====================

    /**
     * ДВЕ ЛУПЫ И ПРОЦЕНТ. Статьи справочника набраны мелко, а в разворотах карт
     * есть строки и того мельче — дизайнер просил возможность просто увеличить
     * текст, не трогая масштаб всего приложения (13.08.2026). Увеличивается
     * только правая половина: дерево разделов от этого не зависит.
     */
    private JPanel zoomBox() {
        JPanel box = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,
            Theme.px(4), 0));
        box.setOpaque(false);
        zoomLabel = Ui2.label(zoom + "%");
        zoomLabel.setToolTipText(Ui2.tip("Насколько крупнее обычного набран текст "
            + "статьи. Щелчок по числу возвращает обычный размер."));
        zoomLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        zoomLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                setZoom(100);
            }
        });
        box.add(Ui2.iconButton(kelium.gui.TransportIcons.of("ZOOM_OUT", Theme.px(16)),
            Ui2.tip("Мельче текст статьи (Ctrl и минус)"), Theme.px(26),
            () -> stepZoom(-1)));
        box.add(zoomLabel);
        box.add(Ui2.iconButton(kelium.gui.TransportIcons.of("ZOOM_IN", Theme.px(16)),
            Ui2.tip("Крупнее текст статьи (Ctrl и плюс)"), Theme.px(26),
            () -> stepZoom(1)));
        return box;
    }

    /** Соседняя ступенька увеличения; на краях список просто упирается. */
    private void stepZoom(int dir) {
        int i = 0;
        while (i < ZOOMS.length - 1 && ZOOMS[i] < zoom) {
            i++;
        }
        setZoom(ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, i + dir))]);
    }

    private void setZoom(int value) {
        int v = nearestZoom(value);
        if (v == zoom) {
            return;
        }
        zoom = v;
        prefs.putInt(PREF_ZOOM, v);
        if (zoomLabel != null) {
            zoomLabel.setText(v + "%");
        }
        render();
    }

    /** Ближайшая ступенька: в настройках может лежать что угодно от прошлых версий. */
    private static int nearestZoom(int value) {
        int best = 100;
        for (int z : ZOOMS) {
            if (Math.abs(z - value) < Math.abs(best - value)) {
                best = z;
            }
        }
        return best;
    }

    private void bindZoomKeys(JComponent root) {
        // Ctrl и +/− как в браузере. Плюс ловим и на основной клавиатуре (там это
        // shift и равно), и на цифровой — иначе половина нажатий уходит впустую.
        bindKey(root, "control PLUS", "zoomIn", () -> stepZoom(1));
        bindKey(root, "control ADD", "zoomInPad", () -> stepZoom(1));
        bindKey(root, "control EQUALS", "zoomInEq", () -> stepZoom(1));
        bindKey(root, "control MINUS", "zoomOut", () -> stepZoom(-1));
        bindKey(root, "control SUBTRACT", "zoomOutPad", () -> stepZoom(-1));
        bindKey(root, "control 0", "zoomReset", () -> setZoom(100));
    }

    private void bindKey(JComponent root, String stroke, String name, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(stroke), name);
        root.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void bindEscape(JComponent root, JFrame frame) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        root.getActionMap().put("close", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });
    }

    // ==================== дерево ====================

    private void refill() {
        String query = search == null ? "" : search.getText().trim().toLowerCase();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("справочник");
        for (HelpBook.Section s : roots) {
            DefaultMutableTreeNode n = node(s, query);
            if (n != null) {
                root.add(n);
            }
        }
        tree.setModel(new DefaultTreeModel(root));
        // При поиске всё разворачиваем: иначе найденное прячется внутри свёрнутого
        // раздела и кажется, что поиск ничего не нашёл.
        if (!query.isEmpty()) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        }
        if (tree.getRowCount() > 0) {
            tree.setSelectionRow(0);
        } else {
            article.setText(page("Ничего не нашлось",
                "<p>По этой строке разделов нет. Сотри её — вернётся всё содержание.</p>",
                null));
        }
    }

    /** Узел раздела; при поиске — только если он или его дети подходят. */
    private DefaultMutableTreeNode node(HelpBook.Section s, String query) {
        List<DefaultMutableTreeNode> kids = new ArrayList<>();
        for (HelpBook.Section c : s.children) {
            DefaultMutableTreeNode k = node(c, query);
            if (k != null) {
                kids.add(k);
            }
        }
        boolean self = query.isEmpty() || s.title.toLowerCase().contains(query);
        if (!self && kids.isEmpty()) {
            return null;
        }
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(s);
        for (DefaultMutableTreeNode k : kids) {
            n.add(k);
        }
        return n;
    }

    private void showSelected() {
        Object o = tree.getLastSelectedPathComponent();
        if (!(o instanceof DefaultMutableTreeNode n)
                || !(n.getUserObject() instanceof HelpBook.Section s)) {
            return;
        }
        current = s;
        render();
    }

    /** Пересобрать открытую статью (после смены раздела или ширины окна). */
    private void render() {
        if (current == null) {
            return;
        }
        // У разворота карты название печатает сам разворот — плакатным шрифтом, как
        // на карте. Второй заголовок сверху был бы тем же словом дважды.
        String title = current.id.startsWith("card-") ? null : current.title;
        article.setText(page(title, current.html(), picture(current.id)));
        article.setCaretPosition(0);
    }

    // ==================== вид статьи ====================

    private String page(String title, String body, Path image) {
        // ШИРИНА ТЕЛА ЗАДАНА ЯВНО. Без неё длинная таблица или широкая картинка
        // растягивают статью, появляется боковая прокрутка, и текст уезжает за
        // правый край — читать становится нечем.
        // ВЕСЬ КЕГЛЬ СТАТЬИ ТЯНЕТСЯ ЗА ЭТИМ ОДНИМ ЧИСЛОМ: внутри разделов размеры
        // заданы в процентах, поэтому лупа поднимает и заголовки внутри статьи, и
        // подписи в разворотах карт, а не только основной текст.
        StringBuilder sb = new StringBuilder("<html><body style='font-family:")
            .append(Theme.uiFamily()).append(";font-size:").append(scaled(11))
            .append("pt;margin:").append(Theme.px(12)).append("px;width:")
            .append(Math.max(Theme.px(280), pictureWidth)).append("px;color:")
            .append(css(Theme.ink())).append("'>");
        if (title != null) {
            sb.append("<div style='font-family:").append(Theme.displayFamily())
                .append(";font-size:").append(scaled(19))
                .append("pt;margin:0 0 8px 0'>").append(HelpBook.esc(title))
                .append("</div>");
        }
        if (image != null) {
            int[] real = imageSize(image);
            int box = Math.max(Theme.px(280), pictureWidth);
            if (real != null && real[0] > 0 && real[1] > 0) {
                // Вписываем и по ширине, и по ВЫСОТЕ: снимок поля высокий, и без
                // предела по высоте он выдавливает статью за нижний край окна.
                double k = Math.min(1.0, Math.min(box / (double) real[0],
                    Theme.px(300) / (double) real[1]));
                sb.append("<p><img src='").append(image.toUri()).append("' width='")
                    .append((int) Math.round(real[0] * k)).append("' height='")
                    .append((int) Math.round(real[1] * k)).append("'></p>");
            } else {
                sb.append("<p><img src='").append(image.toUri()).append("'></p>");
            }
            sb.append("<p style='color:").append(HelpBook.Html.DIM)
                .append("'>Картинка снята с самого приложения — разойтись с тем, что "
                    + "видно на экране, она не может.</p>");
        }
        sb.append(body);
        sb.append("</body></html>");
        return sb.toString();
    }

    /** Кегль в пунктах с учётом масштаба окна и текущей лупы; меньше 7 не бывает. */
    private int scaled(int pt) {
        return Math.max(7, (int) Math.round(Theme.px(pt) * zoom / 100.0));
    }

    /**
     * Настоящий размер картинки. Нужен, чтобы вписать её по ширине статьи, не
     * растянув: в html-виде Swing width и height независимы, и одна ширина без
     * высоты сплющивает снимок.
     */
    private int[] imageSize(Path file) {
        int[] cached = sizes.get(file.toString());
        if (cached != null) {
            return cached;
        }
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file.toFile());
            if (img == null) {
                return null;
            }
            int[] wh = {img.getWidth(), img.getHeight()};
            sizes.put(file.toString(), wh);
            return wh;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Картинка раздела, если генератор её уже собрал. */
    static Path picture(String id) {
        Path p = helpDir().resolve(id + ".png");
        return Files.isRegularFile(p) ? p : null;
    }

    /** Где лежат картинки справочника. */
    static Path helpDir() {
        return GameConfig.resolveDataRoot(null).resolve("help");
    }

    private static String css(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
