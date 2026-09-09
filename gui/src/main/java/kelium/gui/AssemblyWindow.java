package kelium.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import kelium.gui.replay2.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import kelium.engine.BlockAssembler.Cell;
import kelium.engine.BlockAssembler.Placement;
import kelium.engine.BlockAssembler.Result;
import kelium.gui.LayoutEditor.LHex;
import kelium.gui.LayoutEditor.Model;

/**
 * AssemblyWindow — окно «Сборка поля из блоков».
 *
 * <p>Показывает, как нарисованную раскладку разложить на ФИЗИЧЕСКИЕ куски
 * картона, которые лежат у дизайнера на столе: большие блоки (6 гексов), малые
 * (5 гексов) и чёрные накладки «недоступный гекс». Содержимое гексов рисуется
 * приглушённо, зато границы блоков выделены жирно. Редактировать здесь нельзя —
 * это только просмотр; параметры запаса блоков меняются сверху, и сборка
 * пересчитывается на лету.
 *
 * <p>С 12.08.2026 это не отдельное окно, а <b>вторая вкладка</b> конструктора:
 * в одном окне слева-направо «Конструктор» и «Сборка из блоков», как вкладки
 * браузера. Отсюда же сборка выгружается в PNG.
 */
public final class AssemblyWindow extends JPanel {

    private static final long BUDGET_MS = 4000;

    private final Model model;
    private final JSpinner bigCount = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
    private final JSpinner smallCount = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
    private final JSpinner blackCount = new JSpinner(new SpinnerNumberModel(8, 1, 16, 1));
    private final JLabel status = new JLabel(" ");
    private final View view = new View();

    /**
     * ПЕРЕКРАСИТЬ ПОЛОТНО ПОСЛЕ СМЕНЫ ТЕМЫ.
     *
     * <p>Вся отрисовка читает палитру во время рисования, поэтому хватило бы и
     * одного {@code repaint()}. Отдельный метод нужен из-за ФОНА компонента:
     * {@link Theme#restyleTree} подменяет цвета по палитре, а в светлой теме
     * {@code paper()} и {@code panel()} — одна и та же краска, и по ней не
     * различить, чем компонент был. Полотно конструктора чинится тем же
     * способом, см. {@code LayoutEditor.applyTheme}.
     */
    public void перекрасить() {
        view.setBackground(Theme.paper());
        view.repaint();
        repaint();
    }

    /**
     * САМО ПОЛОТНО — для сторожа темы.
     *
     * <p>Нужен именно он, а не вся вкладка: кнопки и строка состояния — обычные
     * компоненты FlatLaf и перекрашиваются сами, поэтому снимок ВКЛАДКИ
     * различается в двух темах даже тогда, когда полотно осталось белым листом.
     * На этом первая редакция сторожа и прошла вхолостую.
     */
    javax.swing.JPanel полотно() {
        return view;
    }
    private final Timer debounce = new Timer(250, e -> resolve());
    private SwingWorker<List<Result>, Void> worker;

    /**
     * НАЙДЕННЫЕ ВАРИАНТЫ сборки одного и того же поля (все с минимальным числом
     * накладок) и порядок их показа. «Пересобрать» листает варианты по кругу в
     * случайном порядке: каждый показывается один раз, потом порядок
     * перетасовывается заново. Так у кнопки появляется смысл — показать ДРУГУЮ
     * возможную сборку, а не пересчитать ту же самую (просьба дизайнера).
     */
    private final List<Result> variants = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>();
    private int orderPos = -1;
    private final java.util.Random shuffleRng = new java.util.Random(20260812L);

    // ==================================================================
    //  ПОКАЗ КОНКРЕТНЫХ КАРТОНОК (просьба дизайнера 07.09.2026)
    // ==================================================================
    //  Сборка сама по себе показывает только КОНТУРЫ: сборщик работает формами
    //  и не знает, какая картонка легла. Чтобы увидеть поле так, как оно
    //  выйдет на стол, нужен второй режим — с выбранным набором, с печатными
    //  метками на своих местах и с номером каждого блока на выноске.
    private final java.util.Map<String, НаборыБлоков.Версия> наборы =
        НаборыБлоков.загрузить(kelium.dataio.GameConfig.resolveDataRoot(null));
    private final JComboBox<String> контейнерыПикер = new JComboBox<>();
    private final JComboBox<String> энергияПикер = new JComboBox<>();
    private final JComboBox<String> версияПикер = new JComboBox<>();
    private boolean обновляюПикеры;
    /** Зерно раздачи картонок: его и перебрасывает «случайно: только блоки». */
    private long зерноКартонок = 20260907L;
    /** Показывать метки и номера блоков либо только контуры сборки. */
    private final javax.swing.JToggleButton режимМеток =
        new javax.swing.JToggleButton("Блоки с метками");

    public AssemblyWindow(Model model) {
        this.model = model;
        debounce.setRepeats(false);

        JPanel top = new JPanel();
        top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.X_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        top.add(new JLabel("Запас блоков:   больших (6 гексов):"));
        top.add(Box.createHorizontalStrut(4));
        top.add(spinner(bigCount, "Сколько больших блоков есть в наличии (1–10)"));
        top.add(Box.createHorizontalStrut(12));
        top.add(new JLabel("малых (5 гексов):"));
        top.add(Box.createHorizontalStrut(4));
        top.add(spinner(smallCount, "Сколько малых блоков есть в наличии (1–10)"));
        top.add(Box.createHorizontalStrut(12));
        top.add(new JLabel("чёрных накладок:"));
        top.add(Box.createHorizontalStrut(4));
        top.add(spinner(blackCount, "Сколько накладок «недоступный гекс» есть (1–16)"));
        top.add(Box.createHorizontalStrut(16));
        JButton again = new JButton("↻ Другая сборка");
        again.setToolTipText("<html><div style='width:320px'>Показать <b>следующий вариант</b> "
            + "сборки того же поля. Варианты перебираются по кругу в случайном порядке, "
            + "каждый показывается один раз — потом порядок тасуется заново.<br>"
            + "Все варианты используют <b>наименьшее возможное число чёрных накладок</b>."
            + "</div></html>");
        again.addActionListener(e -> nextVariant());
        top.add(again);
        top.add(Box.createHorizontalStrut(10));
        JButton png = new JButton("🖼 Экспорт PNG");
        // ТА ЖЕ КНОПКА, ЧТО НА ВКЛАДКЕ КОНСТРУКТОРА (просьба дизайнера
        // 14.08.2026): раньше отсюда выгружалась только сборка, а с той вкладки
        // — только поле, двумя независимыми действиями. Незачем: за столом
        // дизайнеру нужны обе картинки сразу, форма склейки — в настройках
        // экспорта (шестерёнка на вкладке «Конструктор»).
        png.setToolTipText("<html><div style='width:300px'>Выгрузить <b>поле и сборку из "
            + "блоков</b> — как выбрано в настройках экспорта (раздельно, друг над другом, "
            + "бок о бок или слиянием). Та же кнопка, что на вкладке «Конструктор».</div></html>");
        png.addActionListener(e -> LayoutEditor.exportLayoutPng());
        top.add(png);
        top.add(Box.createHorizontalGlue());

        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));

        // ВТОРОЙ РЯД — выбор набора и режим показа. Отдельной строкой, а не в
        // хвост первой: там уже три счётчика и две кнопки, и всё это в одну
        // строку не влезает на узком окне.
        JPanel второй = new JPanel();
        второй.setLayout(new javax.swing.BoxLayout(второй, javax.swing.BoxLayout.X_AXIS));
        второй.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
        второй.add(new JLabel("Набор блоков:   контейнеры:"));
        второй.add(Box.createHorizontalStrut(4));
        второй.add(контейнерыПикер);
        второй.add(Box.createHorizontalStrut(10));
        второй.add(new JLabel("энергия:"));
        второй.add(Box.createHorizontalStrut(4));
        второй.add(энергияПикер);
        второй.add(Box.createHorizontalStrut(10));
        второй.add(new JLabel("версия:"));
        второй.add(Box.createHorizontalStrut(4));
        второй.add(версияПикер);
        второй.add(Box.createHorizontalStrut(16));

        JButton всёСлучайно = new JButton("⚄ Случайно: всё");
        всёСлучайно.setToolTipText("<html><div style='width:320px'>"
            + "<b>Пересобрать и сборку, и картонки.</b><br>"
            + "Берётся ДРУГОЙ вариант сборки того же поля, и картонки по нему "
            + "раскладываются заново.</div></html>");
        всёСлучайно.addActionListener(e -> {
            зерноКартонок = System.nanoTime();
            nextVariant();
        });
        второй.add(всёСлучайно);
        второй.add(Box.createHorizontalStrut(8));

        JButton толькоБлоки = new JButton("⚄ Случайно: только блоки");
        толькоБлоки.setToolTipText("<html><div style='width:320px'>"
            + "<b>Оставить сборку, поменять картонки.</b><br>"
            + "Форма поля и разбиение на блоки не меняются — меняется только то, "
            + "какой номер блока и какой его стороной лёг в каждое место.<br>"
            + "<i>Так и бывает за столом: контур собрали, а картонки можно "
            + "разложить иначе.</i></div></html>");
        толькоБлоки.addActionListener(e -> {
            зерноКартонок = System.nanoTime();
            пересобратьКартонки();
        });
        второй.add(толькоБлоки);
        второй.add(Box.createHorizontalStrut(12));

        режимМеток.setToolTipText("<html><div style='width:320px'>"
            + "<b>Что показывать.</b><br>"
            + "Выключено — только контуры сборки: как поле разбито на блоки.<br>"
            + "Включено — конкретные картонки выбранного набора: печатные "
            + "контейнеры и жёлтые ячейки на своих местах, повёрнутые вместе с "
            + "блоком, и номер каждого блока на выноске.</div></html>");
        режимМеток.addActionListener(e -> {
            пересобратьКартонки();
            view.repaint();
        });
        второй.add(режимМеток);
        второй.add(Box.createHorizontalGlue());

        собратьПикеры();
        контейнерыПикер.addActionListener(e -> {
            if (!обновляюПикеры) {
                обновитьЭнергию();
            }
        });
        энергияПикер.addActionListener(e -> {
            if (!обновляюПикеры) {
                обновитьВерсии();
            }
        });
        версияПикер.addActionListener(e -> {
            if (!обновляюПикеры) {
                пересобратьКартонки();
                view.repaint();
            }
        });

        JPanel шапка = new JPanel(new java.awt.GridLayout(2, 1));
        шапка.add(top);
        шапка.add(второй);

        setLayout(new BorderLayout());
        add(шапка, BorderLayout.NORTH);
        add(view, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    /**
     * Пересчитать сборку под текущее состояние раскладки. Вызывается, когда
     * пользователь переключается на эту вкладку: поле могло измениться.
     */
    // ==================================================================
    //  ВЫБОР НАБОРА БЛОКОВ
    // ==================================================================

    /**
     * ВЫБОР ПО СОЧЕТАНИЮ, А НЕ ПО ID ФАЙЛА — как в каталоге блоков. Набор и так
     * задаётся двумя числами (сколько контейнеров и сколько энергии), и помнить,
     * какая версия что содержит, дизайнеру незачем.
     */
    private void собратьПикеры() {
        обновляюПикеры = true;
        контейнерыПикер.removeAllItems();
        for (String к : НаборыБлоков.посочетаниям(наборы).keySet()) {
            контейнерыПикер.addItem(к);
        }
        обновляюПикеры = false;
        if (контейнерыПикер.getItemCount() > 0) {
            // ЭТАЛОН ПО УМОЛЧАНИЮ: это тот набор, которым играет действующий
            // свод, и открывать вкладку логично на нём.
            String эталон = String.valueOf(kelium.dataio.GameConfig
                .buildCached(kelium.dataio.GameConfig.DEFAULT_RULESET, 4, 1L, null, null)
                .ruleset.get("content_versions.blocks", ""));
            НаборыБлоков.Версия v = наборы.get(эталон);
            if (v != null) {
                контейнерыПикер.setSelectedItem(v.подписьКонтейнеров());
            }
            обновитьЭнергию();
            if (v != null) {
                энергияПикер.setSelectedItem(v.подписьЭнергии());
                обновитьВерсии();
                версияПикер.setSelectedItem(v.id());
            }
        }
    }

    private void обновитьЭнергию() {
        обновляюПикеры = true;
        энергияПикер.removeAllItems();
        var карта = НаборыБлоков.посочетаниям(наборы)
            .get((String) контейнерыПикер.getSelectedItem());
        if (карта != null) {
            for (String э : карта.keySet()) {
                энергияПикер.addItem(э);
            }
        }
        обновляюПикеры = false;
        обновитьВерсии();
    }

    private void обновитьВерсии() {
        обновляюПикеры = true;
        версияПикер.removeAllItems();
        var карта = НаборыБлоков.посочетаниям(наборы)
            .get((String) контейнерыПикер.getSelectedItem());
        var список = карта == null ? null : карта.get((String) энергияПикер.getSelectedItem());
        if (список != null) {
            for (НаборыБлоков.Версия v : список) {
                версияПикер.addItem(v.id());
            }
        }
        обновляюПикеры = false;
        пересобратьКартонки();
        view.repaint();
    }

    /**
     * ВКЛЮЧИТЬ РЕЖИМ КАРТОНОК ИЗВНЕ — для прогонщика снимков.
     *
     * <p>Проверять вид мышью нельзя (правило 30.08.2026: прогонщики не забирают
     * фокус), поэтому снимок делается в памяти, и ему нужен способ включить
     * режим без нажатия кнопки.
     */
    public void показыватьМетки(boolean включить) {
        режимМеток.setSelected(включить);
        пересобратьКартонки();
    }

    /** Выбранный набор либо {@code null}, если наборов нет вовсе. */
    private НаборыБлоков.Версия выбранныйНабор() {
        String id = (String) версияПикер.getSelectedItem();
        return id == null ? null : наборы.get(id);
    }

    /**
     * Разложить картонки по нынешней сборке. Зовётся при смене сборки, набора
     * или зерна раздачи; сама сборка при этом не пересчитывается.
     */
    private void пересобратьКартонки() {
        if (!режимМеток.isSelected() || !hasResult()) {
            view.картонки = List.of();
            view.версияКартонок = "";
            view.repaint();
            return;
        }
        НаборыБлоков.Версия набор = выбранныйНабор();
        view.картонки = ПривязкаБлоков.привязать(view.result.blocks(), набор, зерноКартонок);
        view.версияКартонок = набор == null ? "" : набор.id();
        view.repaint();
    }

    public void refresh() {
        resolve();
    }

    /**
     * Показать СЛЕДУЮЩИЙ вариант сборки. Порядок случайный, но каждый вариант
     * встречается ровно один раз за круг; когда круг кончился — тасуем заново.
     */
    private void nextVariant() {
        if (variants.size() <= 1) {
            resolve();   // вариантов ещё нет (или он один) — пересчитать
            return;
        }
        if (orderPos < 0 || orderPos + 1 >= order.size()) {
            order.clear();
            for (int i = 0; i < variants.size(); i++) {
                order.add(i);
            }
            java.util.Collections.shuffle(order, shuffleRng);
            orderPos = -1;
        }
        orderPos++;
        showVariant(variants.get(order.get(orderPos)));
    }

    /** Отрисовать выбранный вариант и написать, какой он по счёту. */
    private void showVariant(Result r) {
        view.result = r;
        пересобратьКартонки();
        view.repaint();
        int big = (Integer) bigCount.getValue();
        int small = (Integer) smallCount.getValue();
        int black = (Integer) blackCount.getValue();
        String which = variants.size() > 1
            ? "   ·   вариант " + (orderPos + 1) + " из " + variants.size()
            : "";
        status.setText(describe(r, big, small, black) + which);
    }

    /** Есть ли готовая сборка, которую можно выгрузить (для внешнего вызова). */
    boolean hasResult() {
        return view.result != null && !view.result.blocks().isEmpty();
    }

    /**
     * Ячейки под чёрными накладками «недоступно» — торчат ЗА пределами
     * нарисованного поля (просьба дизайнера 14.08.2026: слияние тоже должно
     * учитывать их в рамке картинки). Пусто, если сборки ещё нет.
     */
    List<Cell> currentBlackCells() {
        return hasResult() ? view.result.blacks() : List.of();
    }

    /** Нарисовать сборку в картинку заданного размера — для внешнего вызова. */
    public java.awt.image.BufferedImage renderField(int w, int h) {
        return view.render(w, h);
    }

    /**
     * ТОЛЬКО СЛОЙ БЛОКОВ (заливка + жирные границы + чёрные накладки), БЕЗ
     * приглушённого содержимого — фон для СЛИЯНИЯ (поправка дизайнера
     * 14.08.2026): блоки лежат ПОЗАДИ настоящего содержимого раскладки, которое
     * рисует поверх само полотно конструктора. {@code fitSize}/{@code fitPanX}/
     * {@code fitPanY} — ОБЩИЕ с полотном конструктора: два слоя рисуются разными
     * холстами и обязаны совпасть пиксель в пиксель.
     */
    java.awt.image.BufferedImage renderBlocksLayer(int w, int h, double fitSize,
                                                    double fitPanX, double fitPanY) {
        return renderBlocksLayer(w, h, fitSize, fitPanX, fitPanY, true);
    }

    /**
     * То же, но с явным выбором раскраски. {@code monochrome} — блоки заливаются
     * БЕЛЫМ с тёмной обводкой, без палитры (правило дизайнера 17.08.2026: в
     * слиянии цвет блоков только мешает читать содержимое поля). Цветная версия
     * осталась для отдельной картинки сборки, где различить блоки — весь смысл.
     */
    java.awt.image.BufferedImage renderBlocksLayer(int w, int h, double fitSize,
                                                    double fitPanX, double fitPanY,
                                                    boolean monochrome) {
        double savedSize = view.size;
        double savedX = view.panX;
        double savedY = view.panY;
        view.size = fitSize;
        view.panX = fitPanX;
        view.panY = fitPanY;
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ExportPaint.FIELD_BG);
        g.fillRect(0, 0, w, h);
        int savedW = view.getWidth();
        int savedH = view.getHeight();
        view.setSize(w, h);
        if (hasResult()) {
            // СЛОЙ ДЛЯ СЛИЯНИЯ — ТОЖЕ ФАЙЛ, а не экран: без этого флага контур
            // блока брался бы из темы, и в тёмной теме выгруженная картинка
            // получила бы светлый контур на белой бумаге.
            ExportPaint.with(() -> view.drawBlocksOnly(g, monochrome));
        }
        view.setSize(savedW, savedH);
        g.dispose();
        view.size = savedSize;
        view.panX = savedX;
        view.panY = savedY;
        return img;
    }

    /**
     * ТОЛЬКО ЧЁРНЫЕ НАКЛАДКИ «недоступный гекс» — отдельный слой для слияния.
     *
     * <p>В порядке слоёв дизайнера (17.08.2026) накладки идут ПОСЛЕ игроков и
     * зданий: физически это картонка, которую кладут поверх собранного поля, и
     * на картинке она обязана лежать так же. Прозрачный фон — слой ложится на
     * уже нарисованное.
     */
    java.awt.image.BufferedImage renderBlackOverlayLayer(int w, int h, double fitSize,
                                                          double fitPanX, double fitPanY) {
        double savedSize = view.size;
        double savedX = view.panX;
        double savedY = view.panY;
        view.size = fitSize;
        view.panX = fitPanX;
        view.panY = fitPanY;
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int savedW = view.getWidth();
        int savedH = view.getHeight();
        view.setSize(w, h);
        if (hasResult()) {
            view.drawBlackOverlays(g);
        }
        view.setSize(savedW, savedH);
        g.dispose();
        view.size = savedSize;
        view.panX = savedX;
        view.panY = savedY;
        return img;
    }

    /**
     * Легенда обозначений сборки (заливка блока, чёрная накладка, границы,
     * приглушённое содержимое) — своя, отдельная от общей легенды раскладки:
     * здесь речь о картоне, а не об игровых обозначениях.
     */
    private List<PngExport.Item> assemblyLegend() {
        // ТОЛЬКО ТО, ЧЕГО НЕ ВИДНО НА ВТОРОМ КАДРЕ (правка дизайнера 17.08.2026).
        // Прежняя легенда объясняла ещё и тайлы зарождения, старты игроков и
        // нейтральные здания — но на картинке сборки они нарисованы бледной
        // тенью, а в полную силу их показывает соседний кадр с раскладкой, где
        // и лежит своя, подробная легенда. Здесь остаётся ровно то, что есть
        // только на этом кадре: два размера картонного блока и накладка.
        List<PngExport.Item> legend = new ArrayList<>();
        int big = view.result.bigUsed();
        int small = view.result.blocks().size() - big;
        if (big > 0) {
            legend.add(PngExport.Item.hex(new Color(0xCBD9EA),
                "большой блок картона — 6 гексов, всего " + big));
        }
        if (small > 0) {
            legend.add(PngExport.Item.hex(new Color(0xE3EAF3),
                "малый блок картона — 5 гексов, всего " + small));
        }
        if (!view.result.blacks().isEmpty()) {
            legend.add(PngExport.Item.hex(new Color(0x1A1A1A),
                "тайл недоступного гекса — " + view.result.blacks().size() + " шт."));
        }
        return legend;
    }

    private String assemblySubtitle() {
        int big = view.result.bigUsed();
        int small = view.result.blocks().size() - big;
        return "Больших блоков (6 гексов): " + big + "   ·   малых (5 гексов): " + small
            + "   ·   чёрных накладок: " + view.result.blacks().size()
            + (view.result.optimal() ? "   ·   накладок минимально возможное число" : "");
    }

    /**
     * Собранная картинка сборки — с ТЕМИ ЖЕ настройками легенды, что и у
     * раскладки (просьба дизайнера 14.08.2026: чекбокс «Общие обозначения» в
     * окне настроек экспорта раньше действовал только на картинку раскладки,
     * теперь гасит легенду и здесь). «Игроки» и «Статистика поля» у сборки не
     * бывает — эти два чекбокса на неё попросту не влияют.
     */
    java.awt.image.BufferedImage exportImage(PngExport.Options options) {
        java.awt.image.BufferedImage field = view.render(1500, 1050);
        PngExport.Content content = PngExport.Content
            .legendOnly(assemblyLegend())
            .filtered(new PngExport.Options(options.generalLegend(), false, false,
                options.layout()));
        return PngExport.compose("Сборка поля из блоков", assemblySubtitle(), field, content);
    }


    private JSpinner spinner(JSpinner sp, String tip) {
        sp.setToolTipText(tip);
        sp.setMaximumSize(new Dimension(kelium.gui.replay2.Theme.px(60),
            kelium.gui.replay2.Theme.px(26)));
        sp.addChangeListener(e -> debounce.restart());
        return sp;
    }

    // ==================== подбор сборки ====================
    private void resolve() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        Set<Cell> playable = new HashSet<>();
        for (LHex h : model.hexes.values()) {
            if (!"forbidden".equals(h.content)) {
                playable.add(new Cell(h.q, h.r));
            }
        }
        int big = (Integer) bigCount.getValue();
        int small = (Integer) smallCount.getValue();
        int black = (Integer) blackCount.getValue();
        status.setText("Подбираю сборку…");
        view.result = null;
        variants.clear();
        order.clear();
        orderPos = -1;
        view.repaint();

        worker = new SwingWorker<>() {
            @Override protected List<Result> doInBackground() {
                // Ищем не одну сборку, а несколько разных — чтобы кнопку
                // «Другая сборка» было чем кормить.
                return kelium.engine.BlockAssembler.solveVariants(playable, big, small, black,
                    BUDGET_MS, 8);
            }

            @Override protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    List<Result> found = get();
                    view.playable = playable;
                    variants.clear();
                    variants.addAll(found);
                    orderPos = 0;
                    order.clear();
                    for (int i = 0; i < variants.size(); i++) {
                        order.add(i);
                    }
                    showVariant(variants.get(0));
                } catch (Exception ex) {
                    status.setText("Сбой подбора: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private String describe(Result r, int big, int small, int black) {
        return switch (r.status()) {
            case OK -> String.format(
                "Собрано: больших %d из %d · малых %d из %d · накладок %d из %d — %s"
                + "   ·   перебор %d шагов за %d мс",
                r.bigUsed(), big, r.smallUsed(), small, r.blacks().size(), black,
                r.optimal() ? "это минимум, меньше не выйдет"
                    : "лучшее за отведённое время (возможно, есть экономнее)",
                r.nodes(), r.millis());
            case IMPOSSIBLE -> "Из такого запаса блоков это поле собрать нельзя "
                + "(перебор исчерпан за " + r.millis() + " мс).";
            case TIMEOUT -> "Не удалось подобрать сборку за " + (BUDGET_MS / 1000)
                + " с — поле слишком сложное. Попробуй добавить блоков или накладок.";
            case EMPTY -> "Поле пустое — собирать нечего.";
        };
    }

    // ==================== холст просмотра ====================
    private final class View extends JPanel {
        Result result;
        /** Уложенные картонки — пусто, если режим меток выключен. */
        List<ПривязкаБлоков.Привязка> картонки = List.of();
        /**
         * ВЕРСИЯ НАБОРА, ПО КОТОРОЙ РАЗДАНЫ КАРТОНКИ. Нужна ровно для одного:
         * печатный арт нарисован под КОНКРЕТНУЮ версию, и класть его на чужую
         * нельзя — на картинке будут одни контейнеры, а играться другие.
         */
        String версияКартонок = "";
        Set<Cell> playable = Set.of();
        double size = 44;
        double panX = 480;
        double panY = 330;
        private int lastX;
        private int lastY;

        View() {
            // ФОН — ИЗ ПАЛИТРЫ, А НЕ КОНСТАНТОЙ. Здесь стоял 0xF7F7F5: цвет не
            // из палитры темы, поэтому Theme.restyleTree его не подменял, и в
            // тёмной теме полотно сборки оставалось белым листом посреди
            // тёмного окна. Theme.paper() подменяется вместе со всем окном.
            setBackground(Theme.paper());
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    lastX = e.getX();
                    lastY = e.getY();
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)
                            || SwingUtilities.isMiddleMouseButton(e)
                            || SwingUtilities.isLeftMouseButton(e)) {
                        panX += e.getX() - lastX;
                        panY += e.getY() - lastY;
                        lastX = e.getX();
                        lastY = e.getY();
                        repaint();
                    }
                }

                @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                    size = Math.max(16, Math.min(120,
                        size * (e.getPreciseWheelRotation() < 0 ? 1.12 : 1 / 1.12)));
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        private double[] center(int q, int r) {
            double[] c = kelium.report.FieldGeometry.hexCenter(q, r, size);
            return new double[]{panX + c[0], panY + c[1]};
        }

        /**
         * Рамка сборки в единицах радиуса гекса: {minX, minY, maxX, maxY}.
         *
         * <p>СЧИТАЕТ И ЧЁРНЫЕ НАКЛАДКИ, не только игровые гексы (баг дизайнера
         * 14.08.2026: «картинка не вписывается»). Физический блок — фиксированная
         * фигура из 5–6 гексов, и она не всегда совпадает с нарисованным полем:
         * лишние ячейки блока, которые торчат за пределы поля, закрываются чёрной
         * накладкой «недоступно» — но раньше в рамку они не входили, и такая
         * накладка могла оказаться за краем картинки или наехать на подпись снизу.
         */
        private double[] unitBounds() {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (Cell c : playable) {
                double[] p = kelium.report.FieldGeometry.hexCenter(c.q(), c.r(), 1);
                minX = Math.min(minX, p[0] - 1);
                maxX = Math.max(maxX, p[0] + 1);
                minY = Math.min(minY, p[1] - 1);
                maxY = Math.max(maxY, p[1] + 1);
            }
            if (result != null) {
                for (Cell c : result.blacks()) {
                    double[] p = kelium.report.FieldGeometry.hexCenter(c.q(), c.r(), 1);
                    minX = Math.min(minX, p[0] - 1);
                    maxX = Math.max(maxX, p[0] + 1);
                    minY = Math.min(minY, p[1] - 1);
                    maxY = Math.max(maxY, p[1] + 1);
                }
            }
            return minX > maxX ? new double[]{-1, -1, 1, 1}
                : new double[]{minX, minY, maxX, maxY};
        }

        /**
         * Нарисовать сборку в картинку заданного размера. Масштаб и сдвиг
         * подбираются так, чтобы поле целиком поместилось с полями по краям;
         * то, что видно на экране (текущий зум и панорама), не трогаем.
         */
        java.awt.image.BufferedImage render(int w, int h) {
            double savedSize = size;
            double savedX = panX;
            double savedY = panY;
            double[] b = unitBounds();
            // ПОЛОСА ПОД ПОДПИСЬ ("жирная линия — граница блока…") — СВОЯ, гексы
            // в неё не заходят. Раньше 60 px запаса делились поровну сверху и
            // снизу, а строку {@link #legend} рисовали ФИКСИРОВАННО у самого
            // низа поверх этого же запаса — на высоких раскладках гекс дотягивался
            // почти до края и подпись перекрывалась содержимым (баг дизайнера
            // 14.08.2026: «картинка не вписывается, перекрывается легендой»).
            int captionH = 28;
            // ПОЛОСЫ ПОД ПОДПИСИ БЛОКОВ — по обоим бокам, ДО подгонки масштаба.
            // Без резерва поле растягивалось на всю ширину кадра, а подписи,
            // которые ставятся за его рамкой, уезжали за край картинки.
            int полосы = картонки.isEmpty() ? 30 : полосаПодписей(h);
            size = Math.max(18, Math.min(90,
                Math.min((w - 2 * полосы) / (b[2] - b[0]),
                    (h - 60 - captionH) / (b[3] - b[1]))));
            panX = w / 2.0 - size * (b[0] + b[2]) / 2;
            panY = (h - captionH) / 2.0 - size * (b[1] + b[3]) / 2;
            java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            kelium.report.Сглаживание.включить(g);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            // ФОН КАДРА ЧУТЬ ТЕМНЕЕ БУМАГИ — общее правило всех видов экспорта
            // (просьба дизайнера 17.08.2026): так кадр с полем читается как
            // отдельный объект на листе.
            g.setColor(ExportPaint.FIELD_BG);
            g.fillRect(0, 0, w, h);
            int savedW = getWidth();
            int savedH = getHeight();
            setSize(w, h);
            ExportPaint.with(() -> paintComponent(g));
            setSize(savedW, savedH);
            g.dispose();
            size = savedSize;
            panX = savedX;
            panY = savedY;
            return img;
        }

        /**
         * КЛЕТКА, СКРУГЛЁННАЯ ТОЛЬКО ПО ВНЕШНЕМУ КОНТУРУ НАБОРА (правка
         * дизайнера 19.08.2026). Скругление каждого угла давало ДЫРКИ на стыках
         * соседних клеток, которых на картоне нет.
         *
         * <p>Для блока «набор» — это сам блок: физически он одна картонка, и
         * мягким должен быть его край, а не швы между его гексами. Для контуров
         * нарисованного поля набор — всё поле.
         *
         * @param набор какие клетки принадлежат той же детали
         */
        private java.awt.geom.Path2D.Double roundedCell(double cx, double cy, double s,
                                                        Cell self, Set<Cell> набор) {
            boolean[] nb = new boolean[6];
            for (int side = 0; side < 6; side++) {
                int[] d = kelium.core.Field.AXIAL_DIRS[side];
                nb[side] = набор.contains(new Cell(self.q() + d[0], self.r() + d[1]));
            }
            return kelium.report.FieldGeometry.outlineRoundedHexPath(cx, cy, s,
                kelium.report.FieldGeometry.TILE_ROUND, nb);
        }

        private Polygon hexPoly(double cx, double cy, double s) {
            Polygon p = new Polygon();
            for (int k = 0; k < 6; k++) {
                double a = Math.toRadians(60 * k - 90
                    + kelium.report.FieldGeometry.TILT);
                p.addPoint((int) Math.round(cx + s * Math.cos(a)),
                    (int) Math.round(cy + s * Math.sin(a)));
            }
            return p;
        }

        // палитра блоков — приглушённая, чтобы границы читались лучше заливки
        private final Color[] palette = {
            new Color(0xCBD9EA), new Color(0xE6D3C2), new Color(0xCFE0CB),
            new Color(0xE2D2E6), new Color(0xD9DCE3), new Color(0xEDE0C0),
            new Color(0xC8DEDF), new Color(0xE7CFCF), new Color(0xD5D8C4),
            new Color(0xDCCBD8)};


        // ==================================================================
        //  КРАСКИ ПОЛОТНА: на экране — тема, в файл — печатная палитра
        // ==================================================================
        //  Правило то же, что у полотна конструктора (LayoutEditor.baseFill):
        //  экспорт уходит в печать, и тёмной темы там не бывает никогда,
        //  поэтому при поднятом ExportPaint.active() краски берутся оттуда.

        /** Заливка блока: в тёмной теме та же палитра, но приглушённая. */
        private Color заливкаБлока(int i, boolean monochrome) {
            if (ExportPaint.active() || monochrome) {
                return ExportPaint.HEX_FILL;
            }
            Color base = palette[i % palette.length];
            return Theme.isDark() ? Theme.darken(base, 0.62) : base;
        }

        /** Внутренний шов между гексами одного блока — едва заметный. */
        private Color шовБлока() {
            if (ExportPaint.active()) {
                return new Color(0x00000022, true);
            }
            return Theme.alpha(Theme.ink(), 0.13);
        }

        /** Внешний контур блока — то, ради чего вся картинка и рисуется. */
        private Color контурБлока(boolean monochrome) {
            if (ExportPaint.active() || monochrome) {
                return ExportPaint.HEX_EDGE;
            }
            return Theme.isDark() ? new Color(0x9AA4AF) : new Color(0x1F2933);
        }

        /** Заливка нарисованного гекса под блоками. */
        private Color заливкаПоля(boolean forbidden) {
            if (ExportPaint.active()) {
                return forbidden ? ExportPaint.FORBIDDEN_FILL : ExportPaint.HEX_FILL;
            }
            if (Theme.isDark()) {
                return forbidden ? new Color(0x1A1F26) : new Color(0x2A313B);
            }
            return forbidden ? new Color(0xE4E4E4) : Color.WHITE;
        }

        /** Контур нарисованного поля:強 — когда сборки нет и поле само себе вид. */
        private Color контурПоля(boolean strong) {
            if (ExportPaint.active()) {
                return ExportPaint.GRID;
            }
            if (Theme.isDark()) {
                return strong ? new Color(0x6B7682) : new Color(0x3D454F);
            }
            return strong ? new Color(0x9AA0A6) : new Color(0xD8D8D4);
        }

        /** Обводка чёрной накладки: сама накладка чёрная в любой теме. */
        private Color обводкаНакладки() {
            if (ExportPaint.active()) {
                return new Color(0x5A6068);
            }
            return Theme.isDark() ? new Color(0x8A929B) : new Color(0x5A6068);
        }

        /** Бледная метка содержимого гекса поверх блоков. */
        private Color меткаСодержимого() {
            if (ExportPaint.active()) {
                return new Color(0x33000000, true);
            }
            return Theme.alpha(Theme.ink(), 0.42);
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            kelium.report.Сглаживание.включить(g);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            if (result == null) {
                message(g, "Подбираю сборку…", Theme.ink2());
                return;
            }
            if (result.status() != kelium.engine.BlockAssembler.Status.OK) {
                drawMuted(g, true);
                String head = result.status() == kelium.engine.BlockAssembler.Status.EMPTY
                    ? "Поле пустое" : "Поле не собирается";
                String sub = switch (result.status()) {
                    case IMPOSSIBLE -> "Из такого запаса блоков эту раскладку сложить нельзя";
                    case TIMEOUT -> "Не удалось подобрать сборку за отведённое время";
                    default -> "Нарисуй поле в конструкторе";
                };
                banner(g, head, sub);
                return;
            }

            // 1) приглушённая раскладка
            drawMuted(g, false);

            // 2)+3) блоки и чёрные накладки
            drawBlocksAndBlacks(g);

            // 4) приглушённое содержимое поверх блоков
            drawContentGhost(g);

            // 5) РЕЖИМ КАРТОНОК: печатные метки на своих местах и номера блоков
            //    на чертёжных выносках. Порядок важен: метки поверх блоков, а
            //    выноски поверх меток — линия не должна прятаться под меткой.
            if (!картонки.isEmpty()) {
                // С ПЕЧАТНЫМ КАРТОНОМ РИСОВАННЫЕ МЕТКИ НЕ НУЖНЫ: контейнеры и
                // жёлтые ячейки уже НАПЕЧАТАНЫ на картинке модуля, и вторые
                // поверх них были бы двойными.
                if (!kelium.report.BlockArt.matches(версияКартонок)) {
                    рисоватьМетки(g);
                }
                рисоватьВыноски(g);
            }
            legend(g);
        }

        /**
         * ТОЛЬКО БЛОКИ: заливка, швы, жирный внешний контур и чёрные накладки —
         * без приглушённого содержимого и без легенды. Вынесено отдельно для
         * СЛИЯНИЯ (просьба дизайнера 14.08.2026): там эти блоки — ФОН ПОЗАДИ
         * ВСЕГО, а содержимое рисует поверх само полотно конструктора, в полную
         * силу, а не бледной тенью.
         */
        private void drawBlocksAndBlacks(Graphics2D g) {
            drawBlocksOnly(g, false);
            drawBlackOverlays(g);
        }

        /** Блоки без накладок; {@code monochrome} — белая заливка вместо палитры. */
        private void drawBlocksOnly(Graphics2D g, boolean monochrome) {
            List<Placement> blocks = result.blocks();
            // ПЕЧАТНЫЙ КАРТОН ВМЕСТО ЦВЕТНОЙ ПЛАШКИ, когда он нарисован под этот
            // набор: на картинке настоящий модуль, и рисованная заливка с
            // метками ему уже не нужна. В монохром (выгрузка контуров под
            // печать) картон не идёт — там нужны линии, а не рисунок.
            java.util.Map<Placement, ПривязкаБлоков.Привязка> поКартону =
                new java.util.HashMap<>();
            if (!monochrome && kelium.report.BlockArt.matches(версияКартонок)) {
                for (ПривязкаБлоков.Привязка п : картонки) {
                    поКартону.put(п.место(), п);
                }
            }
            for (int i = 0; i < blocks.size(); i++) {
                Placement p = blocks.get(i);
                Set<Cell> own = new HashSet<>(p.cells());
                if (!рисоватьКартон(g, поКартону.get(p))) {
                    g.setColor(заливкаБлока(i, monochrome));
                    for (Cell c : p.cells()) {
                        double[] xy = center(c.q(), c.r());
                        g.fill(roundedCell(xy[0], xy[1], size * 0.99, c, own));
                    }
                    // тонкие внутренние швы между гексами одного блока
                    g.setColor(шовБлока());
                    g.setStroke(new BasicStroke(1f));
                    for (Cell c : p.cells()) {
                        double[] xy = center(c.q(), c.r());
                        g.draw(roundedCell(xy[0], xy[1], size * 0.99, c, own));
                    }
                }
                // внешний контур блока
                g.setColor(контурБлока(monochrome));
                g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (Cell c : p.cells()) {
                    double[] xy = center(c.q(), c.r());
                    for (int side = 0; side < 6; side++) {
                        int[] d = kelium.core.Field.AXIAL_DIRS[side];
                        if (own.contains(new Cell(c.q() + d[0], c.r() + d[1]))) {
                            continue;   // внутреннее ребро — не рисуем
                        }
                        double a1 = Math.toRadians(
                            kelium.report.FieldGeometry.edgeAngle(side) - 30);
                        double a2 = Math.toRadians(
                            kelium.report.FieldGeometry.edgeAngle(side) + 30);
                        g.drawLine(
                            (int) Math.round(xy[0] + size * 0.99 * Math.cos(a1)),
                            (int) Math.round(xy[1] + size * 0.99 * Math.sin(a1)),
                            (int) Math.round(xy[0] + size * 0.99 * Math.cos(a2)),
                            (int) Math.round(xy[1] + size * 0.99 * Math.sin(a2)));
                    }
                }
            }

        }

        /**
         * НАСТОЯЩАЯ КАРТОНКА на своих гексах.
         *
         * <p>Привязка уже знает, какая сторона какого блока легла и каким
         * поворотом, — остаётся сказать укладчику, куда встал гекс, который у
         * этого блока значится как (0,0). Само преобразование живёт в
         * {@link kelium.report.BlockArt}: тем же кладут картон и в каталоге, и на
         * поле, иначе рисунок поедет относительно гексов в одном из трёх мест.
         *
         * @return нарисовали ли (нет привязки или нет картинки — нет)
         */
        private boolean рисоватьКартон(Graphics2D g, ПривязкаБлоков.Привязка п) {
            if (п == null) {
                return false;
            }
            List<НаборыБлоков.Гекс> свои = п.сторона().гексы();
            for (int i = 0; i < свои.size(); i++) {
                if (свои.get(i).q() != 0 || свои.get(i).r() != 0) {
                    continue;
                }
                Cell c = п.гексы().get(i).клетка();
                double[] xy = center(c.q(), c.r());
                return kelium.report.BlockArt.paint(g, п.сторона().блок(),
                    п.сторона().имя(), xy[0], xy[1], size, п.поворотов());
            }
            return false;
        }

        /** Чёрные накладки «недоступный гекс» — свой слой (см. порядок слоёв). */
        private void drawBlackOverlays(Graphics2D g) {
            for (Cell c : result.blacks()) {
                double[] xy = center(c.q(), c.r());
                // ОТДЕЛЬНАЯ ПЕЧАТНАЯ НАКЛАДКА — со скруглёнными углами, как тайлы
                // зарождения и запретные гексы в конструкторе (просьба дизайнера
                // 19.08.2026). Скругление считает FieldGeometry, чтобы во всех
                // трёх приложениях оно было одно и то же.
                var tile = kelium.report.FieldGeometry.roundedHexPath(xy[0], xy[1],
                    size * 0.82, kelium.report.FieldGeometry.TILE_ROUND);
                g.setColor(new Color(0x14171A));
                g.fill(tile);
                g.setColor(обводкаНакладки());
                g.setStroke(new BasicStroke(1.6f));
                g.draw(tile);
            }
        }

        /** Контуры нарисованного поля: игровые светло, запретные пунктиром. */
        private void drawMuted(Graphics2D g, boolean strong) {
            // НАБОР ЗДЕСЬ — ВСЁ ПОЛЕ: мягким должен быть контур поля целиком, а
            // швы между его клетками остаются острыми и сходятся вплотную.
            Set<Cell> поле = new HashSet<>();
            for (LHex h : model.hexes.values()) {
                поле.add(new Cell(h.q, h.r));
            }
            for (LHex h : model.hexes.values()) {
                double[] xy = center(h.q, h.r);
                var poly = roundedCell(xy[0], xy[1], size * 0.99,
                    new Cell(h.q, h.r), поле);
                boolean forbidden = "forbidden".equals(h.content);
                g.setColor(заливкаПоля(forbidden));
                g.fill(poly);
                g.setColor(контурПоля(strong));
                g.setStroke(forbidden
                    ? new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                        0, new float[]{4, 4}, 0)
                    : new BasicStroke(1.2f));
                g.draw(poly);
            }
            if (strong) {
                drawContentGhost(g);
            }
        }

        /** Что лежит на гексах — бледно, чтобы не спорить с границами блоков. */
        private void drawContentGhost(Graphics2D g) {
            g.setFont(getFont().deriveFont(Font.BOLD, (float) (size * 0.34)));
            for (LHex h : model.hexes.values()) {
                String mark = switch (h.content) {
                    case "kelium_tile" -> "K";
                    case "spawn_start" -> "S";
                    case "player_start" -> "P" + (h.seat + 1);
                    case "forbidden" -> "✕";
                    default -> null;
                };
                if (mark == null) {
                    continue;
                }
                double[] xy = center(h.q, h.r);
                g.setColor(меткаСодержимого());
                var fm = g.getFontMetrics();
                g.drawString(mark, (float) (xy[0] - fm.stringWidth(mark) / 2.0),
                    (float) (xy[1] + fm.getAscent() / 2.5));
            }
        }

        /**
         * ПЕЧАТНЫЕ МЕТКИ КАРТОНОК на своих секторах.
         *
         * <p>Фигуры берутся у {@link kelium.report.FieldGeometry} — те же, что
         * рисует каталог блоков и разбор партии. Иначе одна и та же ячейка
         * выглядела бы в трёх приложениях по-разному, и сверять печать было бы
         * нечем.
         */
        private void рисоватьМетки(Graphics2D g) {
            // ПОД ЧЁРНОЙ НАКЛАДКОЙ ПЕЧАТИ НЕ ВИДНО. Блок лежит под накладкой
            // целиком, но накладка — это физическая картонка поверх, и она
            // закрывает и контейнер, и жёлтую ячейку. Рисовать их сквозь неё
            // значило бы показывать то, чего за столом не увидишь.
            Set<Cell> подНакладкой = new HashSet<>(result.blacks());
            for (ПривязкаБлоков.Привязка п : картонки) {
                for (ПривязкаБлоков.ГексНаПоле h : п.гексы()) {
                    if (подНакладкой.contains(h.клетка())) {
                        continue;
                    }
                    double[] xy = center(h.клетка().q(), h.клетка().r());
                    double s = size * 0.99;
                    if (h.естьЭнергия()) {
                        float толщина = (float) Math.max(1.2, s * 0.055);
                        g.setColor(Theme.energy());
                        g.setStroke(new BasicStroke(толщина, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                        g.draw(kelium.report.FieldGeometry.path(
                            kelium.report.FieldGeometry.energyCellOutline(
                                xy[0], xy[1], s, h.энергия(), толщина)));
                        double[] точка = kelium.report.FieldGeometry.energyCellSpot(
                            xy[0], xy[1], s, h.энергия());
                        g.fill(kelium.report.FieldGeometry.path(
                            kelium.report.FieldGeometry.boltPolygon(
                                точка[0], точка[1], s * 0.24)));
                    }
                    if (h.естьКонтейнер()) {
                        var квадрат = kelium.report.FieldGeometry.path(
                            kelium.report.FieldGeometry.containerCellQuad(
                                xy[0], xy[1], s, h.контейнер(), s * 0.24));
                        g.setColor(Theme.container());
                        g.fill(квадрат);
                        g.setColor(Theme.alpha(Theme.ink(), 0.45));
                        g.setStroke(new BasicStroke(1.2f));
                        g.draw(квадрат);
                    }
                }
            }
        }

        /**
         * ВЫНОСКИ С НОМЕРАМИ БЛОКОВ — по-чертёжному.
         *
         * <p>Линия идёт от середины блока наклонным участком к краю, там
         * ломается и уходит ГОРИЗОНТАЛЬНО до полки, на которой лежит подпись.
         * Подписи стоят двумя столбцами за пределами поля — слева и справа, — и
         * разведены по высоте, чтобы не наезжали друг на друга.
         *
         * <p>Почему не подписывать прямо на блоке: блок — это пять-шесть гексов,
         * и на них уже лежат печатные метки и бледное содержимое поля. Подпись
         * поверх спорила бы с ними, а вынесенная за поле читается сразу и самой
         * раскладке не мешает.
         */
        /**
         * ВЫНОСКИ С НОМЕРАМИ БЛОКОВ — по-чертёжному.
         *
         * <p>От середины блока идёт наклонный участок, у края поля он ломается и
         * уходит ГОРИЗОНТАЛЬНО до полки, на которой лежит подпись. Подписи
         * стоят двумя столбцами сразу за полем — слева и справа, — и разведены
         * по высоте, чтобы не наезжали.
         *
         * <p>ПОЧЕМУ У КРАЯ ПОЛЯ, А НЕ ОКНА. Полотно шире раскладки, и подписи,
         * прижатые к краю окна, тянули за собой линии через весь холст — рисунок
         * читался как паутина. Полки ставятся вплотную к рамке сборки: линия
         * короткая, и видно, к какому блоку она идёт.
         *
         * <p>Почему подпись не на самом блоке: блок — это пять-шесть гексов, и
         * на них уже лежат печатные метки и бледное содержимое поля. Подпись
         * поверх спорила бы с ними.
         */
        private void рисоватьВыноски(Graphics2D g) {
            g.setFont(шрифтПодписей());
            var fm = g.getFontMetrics();
            int полка = полкаПодписи(fm);
            int зазор = зазорПодписей(fm);

            // СЕРЕДИНА БЛОКА — по экранным центрам его гексов: осевые средние
            // дробные, а перевод в экран берёт целые, и дробную середину
            // пришлось бы считать второй формулой, которая бы и разошлась.
            List<Object[]> все = new ArrayList<>();
            double полеЛево = Double.MAX_VALUE;
            double полеПраво = -Double.MAX_VALUE;
            double серединаX = 0;
            for (ПривязкаБлоков.Привязка п : картонки) {
                double sx = 0;
                double sy = 0;
                for (ПривязкаБлоков.ГексНаПоле h : п.гексы()) {
                    double[] c = center(h.клетка().q(), h.клетка().r());
                    sx += c[0];
                    sy += c[1];
                    полеЛево = Math.min(полеЛево, c[0] - size);
                    полеПраво = Math.max(полеПраво, c[0] + size);
                }
                double[] xy = {sx / п.гексы().size(), sy / п.гексы().size()};
                все.add(new Object[]{п, xy});
                серединаX += xy[0];
            }
            серединаX /= картонки.size();

            List<Object[]> слева = new ArrayList<>();
            List<Object[]> справа = new ArrayList<>();
            for (Object[] пара : все) {
                double[] xy = (double[]) пара[1];
                (xy[0] < серединаX ? слева : справа).add(пара);
            }
            слева.sort((a, b) -> Double.compare(((double[]) a[1])[1], ((double[]) b[1])[1]));
            справа.sort((a, b) -> Double.compare(((double[]) a[1])[1], ((double[]) b[1])[1]));

            выноскиСтолбца(g, слева, true, fm, полка,
                (int) Math.round(полеЛево - зазор));
            выноскиСтолбца(g, справа, false, fm, полка,
                (int) Math.round(полеПраво + зазор));
        }

        /**
         * Один столбец подписей. {@code крайX} — та вертикаль, от которой
         * начинаются полки: слева подписи прижаты к ней правым краем, справа —
         * левым, чтобы текст всегда смотрел от поля наружу.
         */
        /**
         * ШРИФТ ПОДПИСЕЙ БЛОКОВ.
         *
         * <p>НА ЭКРАНЕ — из темы, чтобы жил вместе с масштабом интерфейса.
         * В ВЫГРУЗКЕ — от высоты кадра: картинка уходит в печать и на просмотр
         * в полный размер, и фиксированные одиннадцать точек на кадре в две
         * тысячи пикселей превращаются в нечитаемую сыпь (просьба дизайнера
         * 07.09.2026). Границы держат подпись в разумных пределах и на крошечных
         * кадрах, и на огромных.
         */
        private Font шрифтПодписей() {
            if (ExportPaint.active()) {
                return Theme.font(кегльПодписи(getHeight()), Font.BOLD);
            }
            // ВДВОЕ КРУПНЕЕ (заказ дизайнера 08.09.2026: «шрифт мелковатый, в
            // два раза бы его увеличить»). Подпись читают, косясь на блок, а не
            // вглядываясь: тринадцати было мало.
            return Theme.font(26, Font.BOLD);
        }

        /** Кегль подписи в выгрузке — от высоты кадра, вдвое крупнее прежнего. */
        private static int кегльПодписи(int высотаКадра) {
            return (int) Math.round(Math.max(26, Math.min(68, высотаКадра / 21.0)));
        }

        /** Длина полки за подписью — от кегля, а не константой. */
        private int полкаПодписи(java.awt.FontMetrics fm) {
            return Math.max(Theme.px(10), fm.getHeight());
        }

        /** Отступ от рамки поля до полки: подпись не должна липнуть к гексам. */
        private int зазорПодписей(java.awt.FontMetrics fm) {
            return Math.max(Theme.px(26), (int) Math.round(fm.getHeight() * 2.0));
        }

        /**
         * СКОЛЬКО МЕСТА ПО КАЖДОМУ БОКУ НАДО ОТДАТЬ ПОДПИСЯМ в выгрузке.
         *
         * <p>Считается ДО подгонки масштаба: иначе поле занимает кадр целиком, а
         * подписи, которые ставятся за его рамкой, уезжают за край картинки —
         * ровно то, на что жаловался дизайнер. Ширина берётся по самой длинной
         * подписи набора, плюс полка, плюс зазор до поля, плюс поле от края
         * кадра.
         */
        private int полосаПодписей(int высотаКадра) {
            if (картонки.isEmpty()) {
                return 0;
            }
            Font f = Theme.font(кегльПодписи(высотаКадра), Font.BOLD);
            java.awt.FontMetrics fm = getFontMetrics(f);
            int самая = 0;
            for (ПривязкаБлоков.Привязка п : картонки) {
                самая = Math.max(самая, fm.stringWidth(п.сторона().подпись()));
            }
            return самая + полкаПодписи(fm) + зазорПодписей(fm) + краяКадра(fm);
        }

        /** Поле от края картинки до подписи — «для красоты», просьба дизайнера. */
        private int краяКадра(java.awt.FontMetrics fm) {
            return Math.max(Theme.px(12), fm.getHeight());
        }

        private void выноскиСтолбца(Graphics2D g, List<Object[]> столбец, boolean влево,
                                    java.awt.FontMetrics fm, int полка, int крайX) {
            if (столбец.isEmpty()) {
                return;
            }
            int шаг = Math.max(fm.getHeight() + Theme.px(3), Theme.px(16));
            int верх = Math.max(fm.getAscent() + Theme.px(6),
                (getHeight() - шаг * столбец.size()) / 2);

            // НЕ ВЫЛЕЗАТЬ ЗА КРАЙ КАДРА. Место под подписи резервируется заранее
            // ({@link #полосаПодписей}), но раскладка поля может оказаться уже
            // резерва — тогда рамка поля стоит дальше от края, чем нужно, и
            // подпись имеет право отодвинуться внутрь. Обрезанной она не будет
            // никогда: край — это жёсткая граница.
            int поле = краяКадра(fm);
            int самая = 0;
            for (Object[] пара : столбец) {
                самая = Math.max(самая, fm.stringWidth(
                    ((ПривязкаБлоков.Привязка) пара[0]).сторона().подпись()));
            }
            if (влево) {
                крайX = Math.max(крайX, поле + самая);
            } else {
                // Хвостик полки ушёл влево, к полю, — справа под него места
                // резервировать больше не надо.
                крайX = Math.min(крайX, getWidth() - поле - самая);
            }

            for (int i = 0; i < столбец.size(); i++) {
                ПривязкаБлоков.Привязка п = (ПривязкаБлоков.Привязка) столбец.get(i)[0];
                double[] xy = (double[]) столбец.get(i)[1];
                int yТекста = верх + шаг * i;
                int yЛинии = yТекста + Theme.px(3);
                String текст = п.сторона().подпись();
                int ширина = fm.stringWidth(текст);

                // ПОЛКА: под текстом, плюс хвостик В СТОРОНУ ПОЛЯ — именно от
                // конца хвостика и уходит наклонный участок к блоку.
                //
                // ИСПРАВЛЕНО 08.09.2026 (замечание дизайнера: «справа пробел
                // между палкой и подчёркивающей палкой»). У правого столбца
                // хвостик отсчитывался от ПРАВОГО конца полки, а перелом — от
                // левого: между наклонной линией и полкой оставалась дырка
                // длиной в хвостик. Теперь столбцы устроены зеркально: полка
                // накрывает текст и выходит хвостиком к полю, а перелом стоит
                // ровно на конце этого хвостика.
                int xТекста = влево ? крайX - ширина : крайX;
                int xПолкиОт = влево ? xТекста : крайX - полка;
                int xПолкиДо = влево ? крайX + полка : крайX + ширина;
                int xПерелома = влево ? крайX + полка : крайX - полка;

                g.setColor(Theme.alpha(Theme.ink(), 0.6));
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
                g.drawLine(xПолкиОт, yЛинии, xПолкиДо, yЛинии);
                // наклонный участок от перелома к середине блока
                g.drawLine(xПерелома, yЛинии,
                    (int) Math.round(xy[0]), (int) Math.round(xy[1]));
                // точка на блоке — как в чертеже
                double r = Theme.px(3) * 0.8;
                g.fill(new java.awt.geom.Ellipse2D.Double(
                    xy[0] - r, xy[1] - r, 2 * r, 2 * r));

                g.setColor(Theme.ink());
                g.drawString(текст, xТекста, yТекста);
            }
        }

        private void legend(Graphics2D g) {
            g.setFont(getFont().deriveFont(11f));
            g.setColor(ExportPaint.active() ? ExportPaint.LABEL : Theme.ink3());
            // ПОДСКАЗКА ПРО МЫШЬ — ТОЛЬКО НА ЭКРАНЕ: в печатной картинке колесо
            // и перетаскивание не значат ничего, а место занимают.
            String text = "жирная линия — граница физического блока";
            if (!ExportPaint.active()) {
                text += " · колесо — масштаб, перетаскивание — сдвиг";
            }
            g.drawString(text, 12, getHeight() - 12);
        }

        private void message(Graphics2D g, String text, Color color) {
            g.setFont(getFont().deriveFont(Font.BOLD, 18f));
            g.setColor(color);
            var fm = g.getFontMetrics();
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2);
        }

        /** Лаконичная плашка поверх поля, когда сборки нет. */
        private void banner(Graphics2D g, String head, String sub) {
            int w = 520;
            int h = 116;
            int x = (getWidth() - w) / 2;
            int y = (getHeight() - h) / 2;
            g.setColor(Theme.alpha(Theme.panel(), 0.95));
            g.fillRoundRect(x, y, w, h, 16, 16);
            g.setColor(Theme.bad());
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, w, h, 16, 16);

            g.setFont(getFont().deriveFont(Font.BOLD, 20f));
            var fm = g.getFontMetrics();
            g.drawString(head, x + (w - fm.stringWidth(head)) / 2, y + 46);

            g.setFont(getFont().deriveFont(13f));
            g.setColor(Theme.ink2());
            fm = g.getFontMetrics();
            g.drawString(sub, x + (w - fm.stringWidth(sub)) / 2, y + 74);

            g.setColor(Theme.ink3());
            String hint = "измени запас блоков сверху — сборка пересчитается сразу";
            fm = g.getFontMetrics();
            g.drawString(hint, x + (w - fm.stringWidth(hint)) / 2, y + 96);
        }
    }

    /** Все клетки, покрытые блоками (для тестов/проверок). */
    static Set<Cell> covered(Result r) {
        Set<Cell> out = new HashSet<>();
        for (Placement p : r.blocks()) {
            out.addAll(p.cells());
        }
        return new HashSet<>(new ArrayList<>(out));
    }
}
