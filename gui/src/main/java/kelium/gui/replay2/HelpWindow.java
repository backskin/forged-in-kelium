package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.geom.Rectangle2D;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.ElementIterator;
import javax.swing.text.Highlighter;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
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
 * <p>Окно бывает в двух видах:
 * <ul>
 *   <li><b>справочник разбора партии</b> ({@link #show}) — книга правил, пересказ
 *       правил с числами, приборы разбора, каталог карт; цвета — действующей темы;</li>
 *   <li><b>справочник правил</b> ({@link #showRules}) — только главы книги правил
 *       ({@link RulesMarkdown}), всегда в тёмной палитре стола: его открывают из
 *       окна партии и из «Штаба» (заказ дизайнера 25.09.2026).</li>
 * </ul>
 *
 * <p><b>Поиск</b> — по тексту всех страниц, без учёта регистра, «е» и «ё» не
 * различаются. Найденные места идут списком с окрестностью слова; щелчок
 * открывает страницу на этом месте, и все вхождения слова на ней подсвечены.
 *
 * <p>Содержание собирает {@link HelpBook}, картинки готовит {@link HelpShots}.
 * Окно каждого вида открывается одно: второй вызов поднимает уже открытое.
 */
public final class HelpWindow {

    /** Вид окна. */
    enum Mode { APP, RULES }

    /** Тот же раздел файла настроек, что у главного окна. */
    private static final String PREF_NODE = "replay2";

    /**
     * ШАГИ УВЕЛИЧЕНИЯ ТЕКСТА СТАТЬИ, в процентах от обычного. Ступеньки, а не
     * плавный ход: кегль в html пересчитывается в целые пункты, и мелкий шаг
     * половину нажатий не давал бы никакой разницы на экране.
     */
    private static final int[] ZOOMS = {80, 90, 100, 115, 130, 150, 175, 200, 240};

    /** Сколько найденных мест показывать списком: дальше список не читают. */
    private static final int MAX_HITS = 400;

    /** Открытые окна — чтобы F1 поднимал их, а не плодил копии. */
    private static JFrame open;
    private static HelpWindow instance;
    private static JFrame rulesOpen;
    private static HelpWindow rulesInstance;

    private final Mode mode;
    private final List<HelpBook.Section> roots;
    private final kelium.dataio.AppSettings prefs =
        kelium.dataio.AppSettings.of(PREF_NODE);
    private Pal pal;

    private JFrame frame;
    private JPanel top;
    private JTree tree;
    private JEditorPane article;
    private JScrollPane articleScroll;
    private JScrollPane treeScroll;
    private JScrollPane hitScroll;
    private JSplitPane split;
    private JTextField search;
    private JPanel left;
    private JPanel hitsPanel;
    private JLabel hitCaption;
    private JLabel searchCaption;
    private JList<Hit> hitList;
    private final DefaultListModel<Hit> hits = new DefaultListModel<>();
    private JButton zoomOut;
    private JButton zoomIn;
    private JLabel zoomLabel;

    private HelpBook.Section current;
    /** Какая страница сейчас набрана в статье (ключ {@link HelpBook.Section#page}). */
    private String shownPage;
    /** Смещение в тексте статьи, к которому прокрутить после набора; −1 — нет. */
    private int pendingOffset = -1;
    /** Нормализованная строка поиска; пустая — поиска нет. */
    private String query = "";
    private final List<Object> marks = new ArrayList<>();

    private int pictureWidth = Theme.px(620);
    /** Текущее увеличение статьи в процентах; помнится между запусками. */
    private int zoom = 100;
    /** Размеры картинок: файл читается один раз, а не на каждую перерисовку. */
    private final java.util.Map<String, int[]> sizes = new java.util.HashMap<>();

    /** Указатель для поиска: текст каждой страницы, набранной так же, как в статье. */
    private List<Page> index;
    private long indexStamp;
    private final javax.swing.Timer searchDelay;

    private HelpWindow(Mode mode, List<HelpBook.Section> roots) {
        this.mode = mode;
        this.roots = roots;
        this.pal = mode == Mode.RULES ? Theme.asTable(Pal::now) : Pal.now();
        this.zoom = nearestZoom(prefs.getInt(prefZoom(), 100));
        this.searchDelay = new javax.swing.Timer(160, e -> runSearch());
        this.searchDelay.setRepeats(false);
        this.figureDelay = new javax.swing.Timer(500, e -> {
            if (current != null && current.file != null && frame != null
                    && frame.isDisplayable()) {
                render(true);
            }
        });
        this.figureDelay.setRepeats(false);
    }

    private javax.swing.Timer figureDelay;
    private final Runnable figuresReady = () -> {
        if (!this.figureDelay.isRunning()) {
            this.figureDelay.start();
        }
    };

    private String prefZoom() {
        return mode == Mode.RULES ? "rulesZoom" : "helpZoom";
    }

    private String prefDivider() {
        return mode == Mode.RULES ? "rulesDivider" : "helpDivider";
    }

    /** Показать справочник разбора партии. Второй вызов поднимает уже открытое окно. */
    public static void show(Window owner, Session session) {
        if (raise(open)) {
            return;
        }
        HelpWindow w = new HelpWindow(Mode.APP, HelpBook.of(session).sections());
        instance = w;
        open = w.build(owner, "Справочник — разбор партии «Кристаллы Раздора»");
    }

    /**
     * ПОКАЗАТЬ СПРАВОЧНИК ПРАВИЛ — главы книги правил с поиском. Открывается из
     * окна партии (кнопка «Правила», F1), из «Штаба» и из разбора партии. Всегда
     * в палитре стола, из какого бы окна его ни позвали.
     */
    public static void showRules(Window owner) {
        if (raise(rulesOpen)) {
            return;
        }
        List<HelpBook.Section> chapters = RulesMarkdown.chapterSections(RulesMarkdown.bookDir());
        if (chapters.isEmpty()) {
            chapters = List.of(RulesMarkdown.tree());
        }
        HelpWindow w = new HelpWindow(Mode.RULES, chapters);
        rulesInstance = w;
        rulesOpen = w.build(owner, "Правила — «Келемий. Кристалл раздора»");
    }

    private static boolean raise(JFrame f) {
        if (f != null && f.isDisplayable()) {
            f.setVisible(true);
            f.toFront();
            f.requestFocus();
            return true;
        }
        return false;
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
     * Перекрасить открытый справочник разбора при смене темы. Статья пересобирается
     * целиком: её цвета вписаны в html, и одной перерисовкой их не сменить.
     * Справочник правил в палитре стола от темы не зависит.
     */
    public static void restyle() {
        if (instance == null || open == null || !open.isDisplayable()) {
            return;
        }
        instance.pal = Pal.now();
        instance.paintAll();
        instance.render(true);
        open.repaint();
    }

    /**
     * ЗАКРЫТЬ СПРАВОЧНИКИ, если они открыты. Нужно при смене масштаба интерфейса:
     * окно собрано в прежних размерах, и оставлять его рядом с пересобранным
     * главным — значит держать на экране две разные вёрстки.
     */
    public static void closeIfOpen() {
        if (open != null) {
            open.dispose();
            open = null;
            instance = null;
        }
        if (rulesOpen != null) {
            rulesOpen.dispose();
            rulesOpen = null;
            rulesInstance = null;
        }
    }

    private JFrame build(Window owner, String title) {
        JFrame f = new JFrame(title);
        frame = f;
        // Своей иконки у справочника нет — берёт иконку разбора партии: это тот
        // же справочник, что открывается оттуда по F1 (просьба дизайнера
        // 14.08.2026: заменить дефолтную чашку Java хоть чем-то узнаваемым).
        kelium.gui.Ui.setAppIcon(f, "replay2");
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        f.getContentPane().setLayout(new BorderLayout());
        f.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                searchDelay.stop();
                figureDelay.stop();
                RulesMarkdown.Figures.removeListener(figuresReady);
                if (f == open) {
                    open = null;
                    instance = null;
                }
                if (f == rulesOpen) {
                    rulesOpen = null;
                    rulesInstance = null;
                }
            }
        });

        search = new JTextField();
        search.setFont(Theme.body());
        search.putClientProperty("JTextField.placeholderText",
            "слово или часть слова, например «трофей»");
        search.putClientProperty("JTextField.showClearButton", true);
        search.setToolTipText(Ui2.tip("Поиск по тексту всех разделов: регистр не важен, "
            + "«е» и «ё» не различаются. Enter — к следующему месту, Shift+Enter — к "
            + "предыдущему, Esc — стереть."));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchDelay.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchDelay.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchDelay.restart();
            }
        });
        bindKey(search, JComponent.WHEN_FOCUSED, "ENTER", "nextHit", () -> stepHit(1));
        bindKey(search, JComponent.WHEN_FOCUSED, "shift ENTER", "prevHit", () -> stepHit(-1));
        bindKey(search, JComponent.WHEN_FOCUSED, "DOWN", "toHits", () -> {
            if (!query.isEmpty() && !hits.isEmpty()) {
                hitList.requestFocusInWindow();
                if (hitList.getSelectedIndex() < 0) {
                    hitList.setSelectedIndex(0);
                }
            }
        });
        top = new JPanel(new BorderLayout(Theme.px(8), 0));
        top.setBorder(BorderFactory.createEmptyBorder(Theme.px(8), Theme.px(12),
            Theme.px(8), Theme.px(12)));
        searchCaption = new JLabel("ПОИСК");
        searchCaption.setFont(Theme.caption());
        top.add(searchCaption, BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);
        top.add(zoomBox(), BorderLayout.EAST);
        f.add(top, BorderLayout.NORTH);

        tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("справочник")));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setFont(Theme.body());
        tree.setRowHeight(Theme.px(24));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> showSelected());
        tree.setCellRenderer(new NodeRenderer());

        article = new JEditorPane("text/html", "");
        article.setEditable(false);
        article.setFont(Theme.body());
        article.addHyperlinkListener(this::onLink);
        articleScroll = new JScrollPane(article);
        articleScroll.setBorder(null);
        articleScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(16));
        // Ширина тела статьи задана явно (см. page), поэтому боковая прокрутка не
        // нужна никогда; а html-вид Swing любит просить на пару точек больше окна.
        articleScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // КАРТИНКА ПО ШИРИНЕ СТАТЬИ. Снимки сняты с приложения и шириной больше
        // окна: вставленные как есть, они уводят статью в боковую прокрутку, и
        // текст уезжает за край. Поэтому при каждом изменении ширины статья
        // пересобирается с новым размером картинки.
        articleScroll.getViewport().addComponentListener(
            new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    // Запас на поля статьи и полосу прокрутки: без него тело шире
                    // окна на пару точек, и снизу вылезает боковая прокрутка.
                    int w = articleScroll.getViewport().getWidth() - Theme.px(60);
                    // СТРОКА КНИГИ НЕ ДЛИННЕЕ ЧИТАЕМОЙ. Во всю ширину большого окна
                    // строка правил уходит за сотню знаков, и глаз теряет начало
                    // следующей; справочник правил держит колонку, как книга.
                    if (mode == Mode.RULES) {
                        w = Math.min(w, Theme.px(720));
                    }
                    if (Math.abs(w - pictureWidth) > Theme.px(16)) {
                        pictureWidth = w;
                        render(true);
                    }
                }
            });

        treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(null);

        hitList = new JList<>(hits);
        hitList.setCellRenderer(new HitCell());
        hitList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hitList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && hitList.getSelectedValue() != null) {
                openHit(hitList.getSelectedValue());
            }
        });
        hitScroll = new JScrollPane(hitList);
        hitScroll.setBorder(null);
        hitCaption = new JLabel(" ");
        hitCaption.setFont(Theme.caption());
        hitCaption.setBorder(BorderFactory.createEmptyBorder(Theme.px(8), Theme.px(12),
            Theme.px(8), Theme.px(12)));
        hitsPanel = new JPanel(new BorderLayout());
        hitsPanel.add(hitCaption, BorderLayout.NORTH);
        hitsPanel.add(hitScroll, BorderLayout.CENTER);

        left = new JPanel(new CardLayout());
        left.add(treeScroll, "tree");
        left.add(hitsPanel, "hits");
        left.setPreferredSize(new Dimension(Theme.px(300), Theme.px(560)));

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, articleScroll);
        split.setDividerLocation(prefs.getInt(prefDivider(), Theme.px(300)));
        split.setDividerSize(Theme.px(6));
        split.setResizeWeight(0);
        split.setBorder(null);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
            e -> prefs.putInt(prefDivider(), split.getDividerLocation()));
        f.add(split, BorderLayout.CENTER);

        JComponent root = (JComponent) f.getContentPane();
        bindKey(root, JComponent.WHEN_IN_FOCUSED_WINDOW, "ESCAPE", "close", () -> {
            if (!search.getText().isEmpty()) {
                search.setText("");
            } else {
                f.dispose();
            }
        });
        bindKey(root, JComponent.WHEN_IN_FOCUSED_WINDOW, "control F", "find", () -> {
            search.requestFocusInWindow();
            search.selectAll();
        });
        bindKey(root, JComponent.WHEN_IN_FOCUSED_WINDOW, "F3", "nextHitF3", () -> stepHit(1));
        bindKey(root, JComponent.WHEN_IN_FOCUSED_WINDOW, "shift F3", "prevHitF3",
            () -> stepHit(-1));
        bindZoomKeys(root);
        paintAll();
        // Рисунки книги собираются в фоне; готов очередной — глава, если она
        // открыта, пересобирается (не чаще раза в полсекунды).
        RulesMarkdown.Figures.addListener(figuresReady);
        List<Path> files = new ArrayList<>();
        for (HelpBook.Section r : roots) {
            for (HelpBook.Section s : r.flatten()) {
                if (s.file != null && s.anchor < 0) {
                    files.add(s.file);
                }
            }
        }
        RulesMarkdown.Figures.warm(files);
        fillTree();
        f.setSize(Theme.px(1100), Theme.px(760));
        f.setLocationRelativeTo(owner);
        kelium.gui.Offscreen.show(f);
        return f;
    }

    // ==================== краски ====================

    /**
     * КРАСКИ ОКНА. Справочник правил снимает их из палитры стола один раз и красит
     * себя сам, не спрашивая действующую тему: «Штаб» светлый, а справочник у
     * игры один — тёмный, как стол (25.09.2026: «никаких белых плашек»). Поэтому
     * здесь всё, что Swing иначе взял бы из оформления, выставлено явно.
     */
    static final class Pal {
        Color bg;
        Color panel;
        Color tile;
        Color hover;
        Color border;
        Color divider;
        Color ink;
        Color ink2;
        Color ink3;
        Color accent;
        Color important;

        static Pal now() {
            Pal p = new Pal();
            p.bg = Theme.bg();
            p.panel = Theme.panel();
            p.tile = Theme.tile();
            p.hover = Theme.hover();
            p.border = Theme.border();
            p.divider = Theme.divider();
            p.ink = Theme.ink();
            p.ink2 = Theme.ink2();
            p.ink3 = Theme.ink3();
            p.accent = Theme.accent();
            p.important = Theme.bad();
            return p;
        }
    }

    /** Разложить краски по всем частям окна (при сборке и при смене темы). */
    private void paintAll() {
        frame.getContentPane().setBackground(pal.bg);
        top.setBackground(pal.panel);
        top.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, pal.border),
            BorderFactory.createEmptyBorder(Theme.px(8), Theme.px(12), Theme.px(8),
                Theme.px(12))));
        searchCaption.setForeground(pal.ink3);
        search.setBackground(pal.tile);
        search.setForeground(pal.ink);
        search.setCaretColor(pal.ink);
        search.setSelectionColor(Theme.alpha(pal.accent, 0.45));
        search.setSelectedTextColor(pal.ink);
        search.putClientProperty("FlatLaf.style", "background:" + css(pal.tile)
            + ";foreground:" + css(pal.ink) + ";borderColor:" + css(pal.border)
            + ";focusedBorderColor:" + css(pal.accent)
            + ";placeholderForeground:" + css(pal.ink3));
        zoomLabel.setForeground(pal.ink2);
        for (JButton b : new JButton[]{zoomOut, zoomIn}) {
            b.putClientProperty("FlatLaf.style", "toolbar.hoverBackground:" + css(pal.hover)
                + ";toolbar.pressedBackground:" + css(pal.border));
            b.repaint();
        }
        tree.setBackground(pal.panel);
        tree.setForeground(pal.ink);
        tree.putClientProperty("FlatLaf.style", "selectionBackground:" + css(pal.hover)
            + ";selectionForeground:" + css(pal.ink)
            + ";selectionInactiveBackground:" + css(pal.hover)
            + ";selectionInactiveForeground:" + css(pal.ink)
            + ";showCellFocusIndicator:false"
            + ";icon.expandedColor:" + css(pal.ink3)
            + ";icon.collapsedColor:" + css(pal.ink3));
        article.setBackground(pal.panel);
        article.setForeground(pal.ink);
        article.setSelectionColor(Theme.alpha(pal.accent, 0.35));
        hitList.setBackground(pal.panel);
        hitList.setForeground(pal.ink);
        hitsPanel.setBackground(pal.panel);
        hitCaption.setForeground(pal.ink3);
        left.setBackground(pal.panel);
        for (JScrollPane sp : new JScrollPane[]{articleScroll, treeScroll, hitScroll}) {
            sp.setBackground(pal.panel);
            sp.getViewport().setBackground(pal.panel);
            String bar = "track:" + css(pal.panel) + ";thumb:" + css(pal.divider)
                + ";hoverThumbColor:" + css(pal.ink3) + ";pressedThumbColor:" + css(pal.ink3)
                + ";hoverTrackColor:" + css(pal.panel);
            sp.getVerticalScrollBar().putClientProperty("FlatLaf.style", bar);
            sp.getHorizontalScrollBar().putClientProperty("FlatLaf.style", bar);
            sp.getVerticalScrollBar().setBackground(pal.panel);
            sp.getHorizontalScrollBar().setBackground(pal.panel);
        }
        split.setBackground(pal.bg);
        split.putClientProperty("FlatLaf.style", "background:" + css(pal.bg)
            + ";gripColor:" + css(pal.ink3));
        if (split.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui) {
            ui.getDivider().setBackground(pal.bg);
        }
        frame.getRootPane().putClientProperty("JRootPane.titleBarBackground", pal.panel);
        frame.getRootPane().putClientProperty("JRootPane.titleBarForeground", pal.ink);
    }

    /** Оформление главы книги правил в красках этого окна. */
    private RulesMarkdown.Style style(boolean prepareFigures) {
        return style(pal, zoom, Math.max(Theme.px(280), pictureWidth), prepareFigures);
    }

    private static RulesMarkdown.Style style(Pal p, int zoom, int width, boolean prepare) {
        return new RulesMarkdown.Style(css(p.ink), css(p.ink2), css(p.ink3), css(p.tile),
            css(p.panel), css(p.border), css(p.accent), css(p.important),
            Math.max(10, (int) Math.round(Theme.px(20) * zoom / 100.0)), width, prepare);
    }

    /** Оформление главы в красках действующей темы — для статей вне окна (тесты). */
    static RulesMarkdown.Style liveStyle(boolean prepareFigures) {
        return style(Pal.now(), 100, Theme.px(620), prepareFigures);
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
        zoomLabel = new JLabel(zoom + "%");
        zoomLabel.setFont(Theme.mono(12, Font.PLAIN));
        zoomLabel.setToolTipText(Ui2.tip("Насколько крупнее обычного набран текст "
            + "статьи. Щелчок по числу возвращает обычный размер."));
        zoomLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        zoomLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                setZoom(100);
            }
        });
        zoomOut = zoomButton(false, "Мельче текст статьи (Ctrl и минус)", () -> stepZoom(-1));
        zoomIn = zoomButton(true, "Крупнее текст статьи (Ctrl и плюс)", () -> stepZoom(1));
        box.add(zoomOut);
        box.add(zoomLabel);
        box.add(zoomIn);
        return box;
    }

    private JButton zoomButton(boolean plus, String tip, Runnable action) {
        JButton b = new JButton(new ZoomIcon(plus, Theme.px(16)));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setToolTipText(Ui2.tip(tip));
        b.setFocusable(false);
        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        Dimension d = new Dimension(Theme.px(28), Theme.px(28));
        b.setPreferredSize(d);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Лупа с плюсом или минусом — рисованная, краской окна. */
    private final class ZoomIcon implements Icon {
        private final boolean plus;
        private final int size;

        ZoomIcon(boolean plus, int size) {
            this.plus = plus;
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);
            double k = size / 16.0;
            g.setColor(pal.ink2);
            g.setStroke(new BasicStroke((float) (1.6 * k), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Ellipse2D.Double(1.5 * k, 1.5 * k, 10 * k, 10 * k));
            g.draw(new java.awt.geom.Line2D.Double(10 * k, 10 * k, 14.5 * k, 14.5 * k));
            g.draw(new java.awt.geom.Line2D.Double(4 * k, 6.5 * k, 9 * k, 6.5 * k));
            if (plus) {
                g.draw(new java.awt.geom.Line2D.Double(6.5 * k, 4 * k, 6.5 * k, 9 * k));
            }
            g.dispose();
        }
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
        prefs.putInt(prefZoom(), v);
        if (zoomLabel != null) {
            zoomLabel.setText(v + "%");
        }
        render(true);
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
        int w = JComponent.WHEN_IN_FOCUSED_WINDOW;
        bindKey(root, w, "control PLUS", "zoomIn", () -> stepZoom(1));
        bindKey(root, w, "control ADD", "zoomInPad", () -> stepZoom(1));
        bindKey(root, w, "control EQUALS", "zoomInEq", () -> stepZoom(1));
        bindKey(root, w, "control MINUS", "zoomOut", () -> stepZoom(-1));
        bindKey(root, w, "control SUBTRACT", "zoomOutPad", () -> stepZoom(-1));
        bindKey(root, w, "control 0", "zoomReset", () -> setZoom(100));
    }

    private static void bindKey(JComponent root, int when, String stroke, String name,
                                Runnable action) {
        root.getInputMap(when).put(KeyStroke.getKeyStroke(stroke), name);
        root.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ==================== дерево ====================

    private void fillTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("справочник");
        for (HelpBook.Section s : roots) {
            root.add(node(s));
        }
        tree.setModel(new DefaultTreeModel(root));
        // Справочник правил открывается с развёрнутыми главами первого уровня:
        // оглавление видно сразу, как в книге.
        if (mode == Mode.RULES) {
            for (int i = tree.getRowCount() - 1; i >= 0; i--) {
                tree.expandRow(i);
            }
            for (int i = tree.getRowCount() - 1; i >= 0; i--) {
                TreePath p = tree.getPathForRow(i);
                if (p.getPathCount() > 2) {
                    tree.collapsePath(p);
                }
            }
        }
        if (tree.getRowCount() > 0) {
            tree.setSelectionRow(0);
        } else {
            article.setText(page("Ничего нет",
                "<p>Разделов нет: книга правил не найдена рядом с каталогом данных.</p>",
                null));
        }
    }

    private static DefaultMutableTreeNode node(HelpBook.Section s) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(s);
        for (HelpBook.Section c : s.children) {
            n.add(node(c));
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
        pendingOffset = -1;
        render(false);
    }

    /** Выделить раздел в дереве (после щелчка по ссылке «глава N»). */
    private boolean selectInTree(HelpBook.Section s) {
        Object root = tree.getModel().getRoot();
        if (!(root instanceof DefaultMutableTreeNode r)) {
            return false;
        }
        java.util.Enumeration<?> e = r.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) e.nextElement();
            if (n.getUserObject() == s) {
                TreePath p = new TreePath(n.getPath());
                tree.setSelectionPath(p);
                tree.scrollPathToVisible(p);
                return true;
            }
        }
        return false;
    }

    private final class NodeRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row,
                                                      boolean focus) {
            super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, false);
            setIcon(null);
            setOpaque(false);
            setBackgroundNonSelectionColor(pal.panel);
            setBackgroundSelectionColor(pal.hover);
            setBorderSelectionColor(null);
            int depth = 0;
            if (value instanceof DefaultMutableTreeNode n) {
                depth = n.getLevel();
            }
            // Глава — жирно и главными чернилами, её разделы — вторыми, мельче.
            setFont(depth <= 1 ? Theme.font(13, Font.BOLD) : Theme.body());
            setForeground(sel ? pal.ink : depth <= 1 ? pal.ink : pal.ink2);
            return this;
        }
    }

    // ==================== статья ====================

    /** Html страницы раздела — ровно так, как её показывает статья. */
    private String pageHtml(HelpBook.Section s, boolean prepareFigures) {
        if (s.file != null) {
            return page(null, RulesMarkdown.render(s.file, style(prepareFigures)), null);
        }
        // У разворота карты название печатает сам разворот — плакатным шрифтом, как
        // на карте. Второй заголовок сверху был бы тем же словом дважды.
        String title = s.id.startsWith("card-") ? null : s.title;
        return page(title, s.html(), picture(s.id));
    }

    /**
     * Пересобрать открытую статью. {@code force} — набрать заново даже ту же
     * страницу (сменились ширина, лупа или краски); иначе та же страница только
     * прокручивается к разделу.
     */
    private void render(boolean force) {
        if (current == null) {
            return;
        }
        boolean same = current.page.equals(shownPage);
        int keep = force && same && pendingOffset < 0 ? topOffset() : -1;
        if (force || !same) {
            article.setText(pageHtml(current, true));
            shownPage = current.page;
            article.setCaretPosition(0);
        }
        int anchor = current.anchor;
        int target = pendingOffset;
        SwingUtilities.invokeLater(() -> {
            article.revalidate();
            mark();
            if (target >= 0) {
                scrollTo(target, false);
            } else if (keep >= 0) {
                scrollTo(keep, true);
            } else if (anchor >= 0) {
                int[] heads = headings(article.getDocument());
                scrollTo(anchor < heads.length ? heads[anchor] : 0, true);
            } else {
                article.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
            }
        });
    }

    /** Смещение в тексте, которое сейчас у верхнего края статьи. */
    private int topOffset() {
        Rectangle v = article.getVisibleRect();
        if (v.height <= 0) {
            return -1;
        }
        return article.viewToModel2D(new java.awt.Point(v.x + Theme.px(12), v.y + 1));
    }

    private void scrollTo(int offset, boolean atTop) {
        try {
            Rectangle2D r = article.modelToView2D(Math.max(0, Math.min(offset,
                article.getDocument().getLength())));
            if (r == null) {
                return;
            }
            Rectangle v = article.getVisibleRect();
            int h = Math.max(1, v.height);
            int y = (int) r.getY() - (atTop ? Theme.px(8) : h / 3);
            article.scrollRectToVisible(new Rectangle(0, Math.max(0, y), 1, h));
        } catch (BadLocationException e) {
            // смещение из прошлой редакции главы — остаёмся где были
        }
    }

    /** Начала заголовков «##»/«###» в тексте статьи — по порядку. */
    static int[] headings(Document doc) {
        List<Integer> out = new ArrayList<>();
        ElementIterator it = new ElementIterator(doc);
        Element e;
        while ((e = it.next()) != null) {
            Object n = e.getAttributes().getAttribute(StyleConstants.NameAttribute);
            if (n == HTML.Tag.H2 || n == HTML.Tag.H3) {
                out.add(e.getStartOffset());
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private void onLink(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
            return;
        }
        String href = e.getDescription();
        if (href == null || !href.startsWith("book:")) {
            return;
        }
        int n;
        try {
            n = Integer.parseInt(href.substring(5).trim());
        } catch (NumberFormatException ex) {
            return;
        }
        for (HelpBook.Section r : roots) {
            for (HelpBook.Section s : r.flatten()) {
                if (s.file != null && s.anchor < 0 && RulesMarkdown.chapterNumber(s.file) == n) {
                    if (!query.isEmpty() || !selectInTree(s)) {
                        current = s;
                        pendingOffset = -1;
                        render(false);
                    }
                    return;
                }
            }
        }
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
            // Книгу правил читают подолгу, как книгу, — кегль на ступень крупнее.
            .append(Theme.uiFamily()).append(";font-size:")
            .append(scaled(mode == Mode.RULES ? 12 : 11))
            .append("pt;margin:").append(Theme.px(12)).append("px ").append(Theme.px(16))
            .append("px;width:").append(Math.max(Theme.px(280), pictureWidth))
            .append("px;color:").append(css(pal.ink)).append("'>");
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

    // ==================== поиск ====================

    /** Страница указателя: раздел, который её показывает, и её текст. */
    record Page(HelpBook.Section section, String title, String text, String norm,
                int[] heads, String[] headTexts) {
    }

    /** Найденное место: страница, смещение в её тексте, где это и окрестность. */
    record Hit(Page page, int offset, int length, String where, String before, String match,
               String after) {
    }

    /**
     * К ОДНОМУ ВИДУ: строчные, «ё» как «е». Длина строки не меняется, поэтому
     * смещение в нормализованном тексте — это смещение и в самой статье.
     */
    static String norm(String s) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) {
            char ch = Character.toLowerCase(c[i]);
            c[i] = ch == 'ё' ? 'е' : ch;
        }
        return new String(c);
    }

    private void runSearch() {
        String q = norm(search.getText().trim());
        if (q.length() < 2) {
            query = "";
            hits.clear();
            ((CardLayout) left.getLayout()).show(left, "tree");
            mark();
            return;
        }
        query = q;
        List<Hit> found = find(q);
        hits.clear();
        Set<String> pages = new HashSet<>();
        int total = 0;
        for (Hit h : found) {
            total++;
            pages.add(h.page().section().page);
            if (hits.size() < MAX_HITS) {
                hits.addElement(h);
            }
        }
        hitCaption.setText(total == 0 ? "НИЧЕГО НЕ НАЙДЕНО"
            : "НАЙДЕНО " + total + " · " + inSections(pages.size())
                + (total > MAX_HITS ? " · ПОКАЗАНЫ ПЕРВЫЕ " + MAX_HITS : ""));
        ((CardLayout) left.getLayout()).show(left, "hits");
        mark();
    }

    /** «в 1 разделе», «в 21 разделе», «в 5 разделах». */
    private static String inSections(int n) {
        boolean one = n % 10 == 1 && n % 100 != 11;
        return "В " + n + (one ? " РАЗДЕЛЕ" : " РАЗДЕЛАХ");
    }

    /** Окончания, которые поиск отбрасывает у слова: «трофей» находит и «трофеи». */
    private static final String[] ENDINGS = {
        "ами", "ями", "ого", "его", "ому", "ему", "ыми", "ими", "ась", "ись",
        "ов", "ев", "ей", "ой", "ий", "ый", "ая", "яя", "ое", "ее", "ую", "юю",
        "ам", "ям", "ах", "ях", "ом", "ем", "ым", "им",
        "а", "я", "о", "е", "ы", "и", "у", "ю", "ь", "й"};

    /**
     * ЧТО ИСКАТЬ НА САМОМ ДЕЛЕ. Одно слово от четырёх букв ищется без окончания:
     * правила склоняют слова («трофей», «трофеи», «трофея», «трофеев»), и поиск
     * буква в букву находил бы только одну форму из пяти. Основа не короче
     * четырёх букв — иначе «бой» нашёл бы полкниги. Несколько слов — ищутся
     * как написаны.
     */
    static String needle(String q) {
        if (q.length() < 4 || !q.chars().allMatch(Character::isLetter)) {
            return q;
        }
        for (String e : ENDINGS) {
            if (q.endsWith(e) && q.length() - e.length() >= 4) {
                return q.substring(0, q.length() - e.length());
            }
        }
        return q;
    }

    /**
     * Следующее вхождение: {начало, конец} или null. Найденная основа
     * дотягивается до конца слова — подсвечивается слово целиком.
     */
    static int[] next(String norm, String q, int from) {
        String n = needle(q);
        int at = norm.indexOf(n, from);
        if (at < 0) {
            return null;
        }
        int end = at + n.length();
        if (!n.equals(q) || q.chars().allMatch(Character::isLetter)) {
            while (end < norm.length() && Character.isLetterOrDigit(norm.charAt(end))) {
                end++;
            }
        }
        return new int[]{at, end};
    }

    /** Все вхождения строки во всех страницах, по порядку разделов. */
    List<Hit> find(String q) {
        List<Hit> out = new ArrayList<>();
        for (Page p : index()) {
            int from = 0;
            int[] m;
            while ((m = next(p.norm(), q, from)) != null) {
                out.add(hit(p, m[0], m[1] - m[0]));
                from = Math.max(m[1], m[0] + 1);
            }
        }
        return out;
    }

    private static Hit hit(Page p, int at, int len) {
        String where = p.title();
        for (int i = p.heads().length - 1; i >= 0; i--) {
            if (p.heads()[i] <= at) {
                where = p.title() + " · " + p.headTexts()[i];
                break;
            }
        }
        // ОКРЕСТНОСТЬ — В ПРЕДЕЛАХ СВОЕЙ СТРОКИ. Соседний абзац или ячейка таблицы
        // в окрестности читаются как одна фраза и сбивают с толку.
        String t = p.text();
        int lineStart = t.lastIndexOf('\n', Math.max(0, at - 1)) + 1;
        int lineEnd = t.indexOf('\n', at + len);
        if (lineEnd < 0) {
            lineEnd = t.length();
        }
        int a = Math.max(lineStart, at - 60);
        int b = Math.min(lineEnd, at + len + 90);
        String before = t.substring(a, at).replaceAll("\\s+", " ").stripLeading();
        String after = t.substring(at + len, b).replaceAll("\\s+", " ").stripTrailing();
        return new Hit(p, at, len, where, (a > lineStart ? "…" : "") + before,
            t.substring(at, at + len), after + (b < lineEnd ? "…" : ""));
    }

    /**
     * УКАЗАТЕЛЬ. Страница набирается тем же {@link #pageHtml} и разбирается тем же
     * html-разборщиком, что и в статье, — поэтому смещение найденного слова в
     * указателе совпадает со смещением в открытой статье, и подсветка встаёт ровно
     * на него. Правка главы на диске сбрасывает указатель.
     */
    private List<Page> index() {
        long stamp = bookStamp();
        if (index != null && stamp == indexStamp) {
            return index;
        }
        List<Page> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (HelpBook.Section r : roots) {
            for (HelpBook.Section s : r.flatten()) {
                if (!seen.add(s.page)) {
                    continue;
                }
                try {
                    out.add(pageOf(s));
                } catch (RuntimeException | java.io.IOException | BadLocationException e) {
                    // страница не разобралась — ищем по остальным
                }
            }
        }
        index = out;
        indexStamp = stamp;
        return out;
    }

    private Page pageOf(HelpBook.Section s)
            throws java.io.IOException, BadLocationException {
        HTMLEditorKit kit = new HTMLEditorKit();
        HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
        doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
        kit.read(new StringReader(pageHtml(s, false)), doc, 0);
        String text = doc.getText(0, doc.getLength());
        int[] heads = headings(doc);
        String[] names = new String[heads.length];
        ElementIterator it = new ElementIterator(doc);
        Element e;
        int k = 0;
        while ((e = it.next()) != null && k < heads.length) {
            Object n = e.getAttributes().getAttribute(StyleConstants.NameAttribute);
            if (n == HTML.Tag.H2 || n == HTML.Tag.H3) {
                names[k++] = text.substring(e.getStartOffset(),
                    Math.min(text.length(), e.getEndOffset())).trim();
            }
        }
        return new Page(s, s.title, text, norm(text), heads, names);
    }

    /** Отпечаток файлов глав: время правки каждого. Сменился — указатель заново. */
    private long bookStamp() {
        long h = 17;
        for (HelpBook.Section r : roots) {
            for (HelpBook.Section s : r.flatten()) {
                if (s.file != null && s.anchor < 0) {
                    try {
                        h = h * 31 + Files.getLastModifiedTime(s.file).toMillis();
                    } catch (java.io.IOException e) {
                        h = h * 31;
                    }
                }
            }
        }
        return h;
    }

    private void openHit(Hit h) {
        current = h.page().section();
        pendingOffset = h.offset();
        render(false);
        pendingOffset = -1;
    }

    /** К следующему (или предыдущему) найденному месту. */
    private void stepHit(int dir) {
        if (query.isEmpty() || hits.isEmpty()) {
            return;
        }
        int i = hitList.getSelectedIndex();
        int n = hits.size();
        int next = i < 0 ? (dir > 0 ? 0 : n - 1) : ((i + dir) % n + n) % n;
        hitList.setSelectedIndex(next);
        hitList.ensureIndexIsVisible(next);
    }

    /** Подсветить в открытой статье все вхождения строки поиска. */
    private void mark() {
        Highlighter hl = article.getHighlighter();
        for (Object m : marks) {
            hl.removeHighlight(m);
        }
        marks.clear();
        if (query.isEmpty()) {
            return;
        }
        Document doc = article.getDocument();
        String text;
        try {
            text = norm(doc.getText(0, doc.getLength()));
        } catch (BadLocationException e) {
            return;
        }
        Hit sel = hitList.getSelectedValue();
        int strong = sel != null && sel.page().section().page.equals(shownPage) ? sel.offset() : -1;
        Highlighter.HighlightPainter soft =
            new DefaultHighlighter.DefaultHighlightPainter(Theme.alpha(pal.accent, 0.30));
        Highlighter.HighlightPainter hard =
            new DefaultHighlighter.DefaultHighlightPainter(Theme.alpha(pal.accent, 0.70));
        int from = 0;
        int[] m;
        while ((m = next(text, query, from)) != null) {
            try {
                marks.add(hl.addHighlight(m[0], m[1], m[0] == strong ? hard : soft));
            } catch (BadLocationException e) {
                break;
            }
            from = Math.max(m[1], m[0] + 1);
        }
    }

    /**
     * СТРОКА НАЙДЕННОГО МЕСТА: сверху где (глава · раздел) тихим цветом, снизу
     * окрестность со словом жирным. Окрестность режется по ширине строки с
     * многоточием так, чтобы само слово оставалось видно всегда.
     */
    private final class HitCell extends JComponent implements ListCellRenderer<Hit> {
        private static final long serialVersionUID = 1L;
        private Hit hit;
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(JList<? extends Hit> list, Hit value,
                                                      int idx, boolean isSelected,
                                                      boolean focus) {
            hit = value;
            selected = isSelected;
            setToolTipText(null);
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Theme.px(200), Theme.px(46));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(selected ? pal.hover : pal.panel);
            g.fillRect(0, 0, w, h);
            if (selected) {
                g.setColor(pal.accent);
                g.fillRect(0, 0, Theme.px(3), h);
            }
            g.setColor(pal.border);
            g.fillRect(0, h - 1, w, 1);
            if (hit == null) {
                g.dispose();
                return;
            }
            int pad = Theme.px(12);
            int avail = w - pad * 2;
            Font small = Theme.font(11, Font.PLAIN);
            g.setFont(small);
            FontMetrics fs = g.getFontMetrics();
            int y1 = Theme.px(6) + fs.getAscent();
            g.setColor(pal.ink3);
            g.drawString(clipRight(hit.where(), fs, avail), pad, y1);

            Font body = Theme.font(12.5, Font.PLAIN);
            Font bold = Theme.font(12.5, Font.BOLD);
            FontMetrics fb = g.getFontMetrics(body);
            FontMetrics fm = g.getFontMetrics(bold);
            int y2 = y1 + fs.getDescent() + Theme.px(4) + fb.getAscent();
            String match = hit.match();
            int mw = fm.stringWidth(match);
            int rest = Math.max(0, avail - mw);
            // Слово стоит на трети строки: слева немного разгона, справа больше.
            String before = clipLeft(hit.before(), fb, Math.min(fb.stringWidth(hit.before()),
                rest / 3));
            int bw = fb.stringWidth(before);
            String after = clipRight(hit.after(), fb, Math.max(0, avail - bw - mw));
            int x = pad;
            g.setFont(body);
            g.setColor(pal.ink2);
            g.drawString(before, x, y2);
            x += bw;
            g.setFont(bold);
            g.setColor(pal.ink);
            g.drawString(match, x, y2);
            x += mw;
            g.setFont(body);
            g.setColor(pal.ink2);
            g.drawString(after, x, y2);
            g.dispose();
        }
    }

    /** Обрезать справа по ширине, с многоточием. */
    static String clipRight(String s, FontMetrics fm, int width) {
        if (fm.stringWidth(s) <= width) {
            return s;
        }
        int n = s.length();
        while (n > 0 && fm.stringWidth(s.substring(0, n) + "…") > width) {
            n--;
        }
        return n == 0 ? "" : s.substring(0, n) + "…";
    }

    /** Обрезать слева по ширине, с многоточием в начале. */
    static String clipLeft(String s, FontMetrics fm, int width) {
        if (fm.stringWidth(s) <= width) {
            return s;
        }
        int n = 0;
        while (n < s.length() && fm.stringWidth("…" + s.substring(n)) > width) {
            n++;
        }
        return n >= s.length() ? "" : "…" + s.substring(n);
    }

    // ==================== для снимков и тестов ====================

    /** Открытый справочник правил (для снимков окна). */
    static JFrame rulesFrameForTest() {
        return rulesOpen;
    }

    static HelpWindow rulesForTest() {
        return rulesInstance;
    }

    /** Набрать строку поиска и сразу искать, без задержки набора. */
    void searchForTest(String text) {
        search.setText(text);
        searchDelay.stop();
        runSearch();
    }

    /** Выбрать найденное место по номеру. */
    void pickHitForTest(int i) {
        if (i >= 0 && i < hits.size()) {
            hitList.setSelectedIndex(i);
        }
    }

    int hitCountForTest() {
        return hits.size();
    }

    /** Выбрать раздел дерева по номеру строки. */
    void selectRowForTest(int row) {
        tree.setSelectionRow(row);
    }

    int highlightCountForTest() {
        return marks.size();
    }
}
