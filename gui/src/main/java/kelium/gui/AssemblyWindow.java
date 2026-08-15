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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import kelium.gui.BlockAssembler.Cell;
import kelium.gui.BlockAssembler.Placement;
import kelium.gui.BlockAssembler.Result;
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

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(view, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    /**
     * Пересчитать сборку под текущее состояние раскладки. Вызывается, когда
     * пользователь переключается на эту вкладку: поле могло измениться.
     */
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
    java.awt.image.BufferedImage renderField(int w, int h) {
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
        double savedSize = view.size;
        double savedX = view.panX;
        double savedY = view.panY;
        view.size = fitSize;
        view.panX = fitPanX;
        view.panY = fitPanY;
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(view.getBackground());
        g.fillRect(0, 0, w, h);
        int savedW = view.getWidth();
        int savedH = view.getHeight();
        view.setSize(w, h);
        if (hasResult()) {
            view.drawBlocksAndBlacks(g);
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
        List<PngExport.Item> legend = new ArrayList<>();
        legend.add(PngExport.Item.hex(new Color(0xCBD9EA),
            "цвет заливки = отдельный блок картона"));
        legend.add(PngExport.Item.hex(new Color(0x1A1A1A),
            "чёрная накладка «недоступный гекс» — " + view.result.blacks().size() + " шт."));
        legend.add(PngExport.Item.hex(new Color(0xF0EFEA),
            "жирная линия — граница блока, тонкая — стык гексов внутри блока"));
        legend.add(PngExport.Item.circle(new Color(0x2E7D32), "тайл зарождения (показан бледно)"));
        legend.add(PngExport.Item.circle(new Color(0x3b82d0), "старт игрока (показан бледно)"));
        legend.add(PngExport.Item.square(new Color(0x9AA0A6), "нейтральное здание (показано бледно)"));
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
                return BlockAssembler.solveVariants(playable, big, small, black,
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
        Set<Cell> playable = Set.of();
        double size = 44;
        double panX = 480;
        double panY = 330;
        private int lastX;
        private int lastY;

        View() {
            setBackground(new Color(0xF7F7F5));
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
            size = Math.max(18, Math.min(90,
                Math.min((w - 60) / (b[2] - b[0]), (h - 60 - captionH) / (b[3] - b[1]))));
            panX = w / 2.0 - size * (b[0] + b[2]) / 2;
            panY = (h - captionH) / 2.0 - size * (b[1] + b[3]) / 2;
            java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(getBackground());
            g.fillRect(0, 0, w, h);
            int savedW = getWidth();
            int savedH = getHeight();
            setSize(w, h);
            paintComponent(g);
            setSize(savedW, savedH);
            g.dispose();
            size = savedSize;
            panX = savedX;
            panY = savedY;
            return img;
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

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            if (result == null) {
                message(g, "Подбираю сборку…", new Color(0x666666));
                return;
            }
            if (result.status() != BlockAssembler.Status.OK) {
                drawMuted(g, true);
                String head = result.status() == BlockAssembler.Status.EMPTY
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
            List<Placement> blocks = result.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                Placement p = blocks.get(i);
                Set<Cell> own = new HashSet<>(p.cells());
                g.setColor(palette[i % palette.length]);
                for (Cell c : p.cells()) {
                    double[] xy = center(c.q(), c.r());
                    g.fillPolygon(hexPoly(xy[0], xy[1], size * 0.99));
                }
                // тонкие внутренние швы между гексами одного блока
                g.setColor(new Color(0x00000022, true));
                g.setStroke(new BasicStroke(1f));
                for (Cell c : p.cells()) {
                    double[] xy = center(c.q(), c.r());
                    g.drawPolygon(hexPoly(xy[0], xy[1], size * 0.99));
                }
                // внешний контур блока
                g.setColor(new Color(0x1F2933));
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

            // 3) чёрные накладки поверх блоков
            for (Cell c : result.blacks()) {
                double[] xy = center(c.q(), c.r());
                g.setColor(new Color(0x14171A));
                g.fillPolygon(hexPoly(xy[0], xy[1], size * 0.82));
                g.setColor(new Color(0x5A6068));
                g.setStroke(new BasicStroke(1.6f));
                g.drawPolygon(hexPoly(xy[0], xy[1], size * 0.82));
            }
        }

        /** Контуры нарисованного поля: игровые светло, запретные пунктиром. */
        private void drawMuted(Graphics2D g, boolean strong) {
            for (LHex h : model.hexes.values()) {
                double[] xy = center(h.q, h.r);
                Polygon poly = hexPoly(xy[0], xy[1], size * 0.99);
                boolean forbidden = "forbidden".equals(h.content);
                g.setColor(forbidden ? new Color(0xE4E4E4) : new Color(0xFFFFFF));
                g.fillPolygon(poly);
                g.setColor(strong ? new Color(0x9AA0A6) : new Color(0xD8D8D4));
                g.setStroke(forbidden
                    ? new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                        0, new float[]{4, 4}, 0)
                    : new BasicStroke(1.2f));
                g.drawPolygon(poly);
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
                g.setColor(new Color(0x33000000, true));
                var fm = g.getFontMetrics();
                g.drawString(mark, (float) (xy[0] - fm.stringWidth(mark) / 2.0),
                    (float) (xy[1] + fm.getAscent() / 2.5));
            }
        }

        private void legend(Graphics2D g) {
            g.setFont(getFont().deriveFont(11f));
            g.setColor(new Color(0x555555));
            g.drawString("жирная линия — граница физического блока · чёрный гекс — накладка "
                + "«недоступен» · колесо — масштаб, перетаскивание — сдвиг", 12, getHeight() - 12);
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
            g.setColor(new Color(0xF2FFFFFF, true));
            g.fillRoundRect(x, y, w, h, 16, 16);
            g.setColor(new Color(0xB00020));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, w, h, 16, 16);

            g.setFont(getFont().deriveFont(Font.BOLD, 20f));
            var fm = g.getFontMetrics();
            g.drawString(head, x + (w - fm.stringWidth(head)) / 2, y + 46);

            g.setFont(getFont().deriveFont(13f));
            g.setColor(new Color(0x444444));
            fm = g.getFontMetrics();
            g.drawString(sub, x + (w - fm.stringWidth(sub)) / 2, y + 74);

            g.setColor(new Color(0x777777));
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
