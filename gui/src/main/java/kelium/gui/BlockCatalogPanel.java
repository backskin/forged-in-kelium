package kelium.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;

import kelium.gui.replay2.Theme;
import kelium.report.FieldGeometry;

/**
 * КАТАЛОГ ФИЗИЧЕСКИХ БЛОКОВ ПОЛЯ — вкладка Конструктора для пролистывания
 * всех сторон всех блоков (заказ дизайнера 18.08.2026).
 *
 * <p>ЗАЧЕМ. Блоки — печатный картон: 5 малых (по 5 гексов) и 5 больших (по 6),
 * каждый двусторонний, итого 20 сторон на размер. Разные версии набора
 * (data/blocks/blocks.*.yaml) фиксируют разное покрытие ячеек контейнера и
 * жёлтой (энергетической) ячейки — прежде увидеть это можно было только читая
 * YAML цифрами. Здесь то же самое видно глазами: жёлтая ячейка подсвечена
 * жёлтым кружком на нужной стороне гекса, ячейка контейнера — коричневым.
 *
 * <p>ВЕРСИИ — ПРЕДЗАГОТОВЛЕННЫЕ ФАЙЛЫ, НЕ ГЕНЕРАЦИЯ НА ЛЕТУ. Ровно как в игре:
 * набор блоков — это физический картон, и версия набора — это конкретный файл
 * {@code blocks.<id>.yaml}, а не параметр, который что-то досчитывает в
 * момент показа. Список версий и подпись каждой (сколько контейнеров, сколько
 * жёлтых ячеек) читаются из самих файлов, а не вписаны в код руками — новая
 * версия попадает в список сама, как только лежит в папке.
 *
 * <p>УСТРОЙСТВО ЭКРАНА: сверху — выбор версии (один на оба размера сразу, как
 * и в данных: один файл версии описывает и малые, и большие блоки разом),
 * снизу — окно поровну поделено на два скролла: слева все стороны малых
 * блоков, справа — больших. Прокрутка только горизонтальная, элементы сначала
 * уменьшаются, потом начинают скроллиться — тот же приём, что в «Картах».
 */
public final class BlockCatalogPanel extends JPanel {

    /** Один гекс блока: осевые координаты и печатные ячейки. */
    private record HexRec(int q, int r, int container, int energy) {
    }

    /** Одна сторона блока (лицо А или Б) — список гексов. */
    private record Face(String blockId, String kind, String faceName, List<HexRec> hexes) {
    }

    /** Загруженная версия набора: id файла + все стороны, разложенные по размеру. */
    private record Version(String id, List<Face> small, List<Face> big) {
        int containersOnSmall() {
            return small.isEmpty() ? 0
                : (int) small.get(0).hexes.stream().filter(h -> h.container >= 0).count();
        }

        int hexesOnSmall() {
            return small.isEmpty() ? 0 : small.get(0).hexes.size();
        }

        int containersOnBig() {
            return big.isEmpty() ? 0
                : (int) big.get(0).hexes.stream().filter(h -> h.container >= 0).count();
        }

        int hexesOnBig() {
            return big.isEmpty() ? 0 : big.get(0).hexes.size();
        }

        int energyOnSmall() {
            return small.isEmpty() ? 0
                : (int) small.get(0).hexes.stream().filter(h -> h.energy >= 0).count();
        }

        int energyOnBig() {
            return big.isEmpty() ? 0
                : (int) big.get(0).hexes.stream().filter(h -> h.energy >= 0).count();
        }

        /** Подпись «сколько контейнеров» — общая для набора обоих размеров. */
        String подписьКонтейнеров() {
            return containersOnSmall() + "/" + hexesOnSmall() + " и "
                + containersOnBig() + "/" + hexesOnBig();
        }

        /** Подпись «сколько энергии» — общая для набора обоих размеров. */
        String подписьЭнергии() {
            int es = energyOnSmall();
            int eb = energyOnBig();
            if (es == 0 && eb == 0) {
                return "нет жёлтых ячеек";
            }
            return es + "/" + hexesOnSmall() + " и " + eb + "/" + hexesOnBig();
        }
    }

    private final Map<String, Version> versions = new TreeMap<>();
    /**
     * ВЫБОР ПО КОМБИНАЦИИ ПАРАМЕТРОВ, А НЕ ПО ОДНОМУ СПИСКУ ВЕРСИЙ (заказ
     * дизайнера 18.08.2026). Версия набора блоков и так задаётся ДВУМЯ
     * независимо тюнящимися вещами — сколько контейнеров и сколько жёлтых
     * ячеек, — и выбирать по внутреннему id файла неудобно: чтобы найти нужное
     * сочетание, пришлось бы помнить, какая версия что содержит. Здесь два
     * списка, оба посчитаны из самих данных, а не вписаны в код руками; третий
     * список — сами версии, которые попали под выбранное сочетание (обычно
     * одна, но если версий с одинаковым сочетанием несколько — видно все).
     */
    private final JComboBox<String> контейнерыПикер = new JComboBox<>();
    private final JComboBox<String> энергияПикер = new JComboBox<>();
    private final JComboBox<String> совпаденияПикер = new JComboBox<>();
    private final JLabel пусто = new JLabel(
        "Нет набора блоков с таким сочетанием контейнеров и энергии.");
    private final Плитка плитка = new Плитка();
    private final JScrollPane прокрутка = new JScrollPane(плитка,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    private boolean обновляюсь;
    /**
     * ПОВОРОТ ПОКАЗА — на сколько шагов по 60° повёрнуты ВСЕ блоки разом
     * (просьба дизайнера 30.08.2026: блоки в каталоге лежали не тем углом,
     * что на его примерах). Это чистый показ: данные версий не трогаются,
     * YAML остаётся как был. Один на обе галереи — малые и большие крутятся
     * вместе, шесть нажатий возвращают исходный вид.
     */
    private int поворот;

    public BlockCatalogPanel(Path dataRoot) {
        super(new java.awt.BorderLayout());
        загрузитьВсеВерсии(dataRoot);

        JPanel top = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        top.add(new JLabel("Контейнеры:"));
        top.add(контейнерыПикер);
        top.add(new JLabel("Энергия:"));
        top.add(энергияПикер);
        top.add(new JLabel("Версия:"));
        top.add(совпаденияПикер);
        // Кнопка поворота — БЕЗ значка-эмодзи: эмодзи в Swing выводились
        // пустыми квадратами (скриншот дизайнера 12.08.2026), текст надёжнее.
        JButton повернутьКнопка = new JButton("Повернуть на 60°");
        повернутьКнопка.setToolTipText("<html><div style='width:280px'>"
            + "<b>Повернуть все блоки</b><br>"
            + "Каждое нажатие поворачивает ВСЕ стороны всех блоков на 60° "
            + "по часовой стрелке — и малые, и большие разом.<br>"
            + "Ячейки контейнеров и жёлтые ячейки поворачиваются вместе с "
            + "гексами. Шесть нажатий — полный круг.<br>"
            + "<i>Только показ: файлы версий не меняются.</i></div></html>");
        повернутьКнопка.setFocusPainted(false);
        повернутьКнопка.addActionListener(e -> {
            поворот = (поворот + 1) % 6;
            плитка.поворот(поворот);
        });
        top.add(повернутьКнопка);
        add(top, java.awt.BorderLayout.NORTH);

        // ПЛИТКА ДВЕ В ШИРИНУ, ПРОЛИСТЫВАНИЕ ВНИЗ (просьба дизайнера 31.08.2026).
        //
        // Прежде было два горизонтальных ряда рядом: слева малые блоки, справа
        // большие, каждый со своей прокруткой. Плохо это тем, что карточки были
        // фиксированные и мелкие (260 точек), десять сторон в ряд не влезали
        // никогда, а по высоте оставалась пустая половина экрана.
        //
        // Теперь один вертикальный список, ДВА СТОЛБЦА, и строка — это ОДИН
        // БЛОК: слева его сторона А, справа сторона Б. Так две стороны одной
        // картонки видны рядом, а сравнивать их и надо чаще всего. Карточка
        // занимает половину ширины окна, поэтому блок рисуется крупно, и
        // разметка ячеек читается без прищуривания.
        прокрутка.setBorder(null);
        прокрутка.getVerticalScrollBar().setUnitIncrement(Theme.px(24));
        прокрутка.getViewport().setBackground(Theme.bg());
        add(прокрутка, java.awt.BorderLayout.CENTER);

        // ВЫБОР ВЕДЁТ ТОЛЬКО К СУЩЕСТВУЮЩИМ НАБОРАМ (баг, найденный дизайнером
        // 31.08.2026).
        //
        // Пикеры были НЕЗАВИСИМЫМИ: в один складывались все встречающиеся
        // сочетания контейнеров, в другой — все сочетания энергии. Но наборов
        // блоков пять, а пар из этих списков — двенадцать, и семь из них не
        // существуют. Дизайнер видел ровно это: меняешь любой пикер и получаешь
        // «нет набора с таким сочетанием», то есть до четырёх версий из пяти
        // добраться нельзя вовсе.
        //
        // Теперь список энергии ЗАВИСИТ от выбранных контейнеров: в нём только
        // те значения, которые с ними и правда встречаются. Мёртвых пар не
        // остаётся, и каждая версия достижима.
        for (Version v : versions.values()) {
            добавитьЕслиНет(контейнерыПикер, v.подписьКонтейнеров());
        }
        контейнерыПикер.addActionListener(e -> обновитьЭнергию());
        энергияПикер.addActionListener(e -> обновитьСовпадения());
        совпаденияПикер.addActionListener(e -> обновитьПоказ());
        if (контейнерыПикер.getItemCount() > 0) {
            контейнерыПикер.setSelectedIndex(контейнерыПикер.getItemCount() - 1);
        }
        обновитьЭнергию();

    }

    /**
     * Пересобрать список энергии под выбранные контейнеры — и только из тех
     * значений, которые с ними встречаются в данных.
     */
    private void обновитьЭнергию() {
        if (обновляюсь) {
            return;
        }
        обновляюсь = true;
        String контейнеры = (String) контейнерыПикер.getSelectedItem();
        String былаЭнергия = (String) энергияПикер.getSelectedItem();
        энергияПикер.removeAllItems();
        for (Version v : versions.values()) {
            if (v.подписьКонтейнеров().equals(контейнеры)) {
                добавитьЕслиНет(энергияПикер, v.подписьЭнергии());
            }
        }
        // Если прежнее значение энергии с новыми контейнерами тоже встречается,
        // оставляем его: иначе выбор дизайнера сбрасывался бы на каждый щелчок.
        if (былаЭнергия != null) {
            for (int i = 0; i < энергияПикер.getItemCount(); i++) {
                if (энергияПикер.getItemAt(i).equals(былаЭнергия)) {
                    энергияПикер.setSelectedIndex(i);
                    break;
                }
            }
        }
        обновляюсь = false;
        обновитьСовпадения();
    }

    private static void добавитьЕслиНет(JComboBox<String> box, String item) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).equals(item)) {
                return;
            }
        }
        box.addItem(item);
    }

    /**
     * Пересчитать список версий, подходящих под выбранное сочетание
     * контейнеров и энергии, — и сразу показать первую из них.
     */
    private void обновитьСовпадения() {
        if (обновляюсь) {
            return;
        }
        обновляюсь = true;
        String контейнеры = (String) контейнерыПикер.getSelectedItem();
        String энергия = (String) энергияПикер.getSelectedItem();
        совпаденияПикер.removeAllItems();
        for (Version v : versions.values()) {
            if (v.подписьКонтейнеров().equals(контейнеры) && v.подписьЭнергии().equals(энергия)) {
                совпаденияПикер.addItem(v.id());
            }
        }
        обновляюсь = false;
        обновитьПоказ();
    }

    private void обновитьПоказ() {
        String id = (String) совпаденияПикер.getSelectedItem();
        Version v = id == null ? null : versions.get(id);
        remove(пусто);
        if (v == null) {
            плитка.показать(List.of(), List.of());
            add(пусто, java.awt.BorderLayout.SOUTH);
        } else {
            плитка.показать(v.small, v.big);
        }
        revalidate();
        repaint();
    }

    // ==================================================================
    //  ЗАГРУЗКА ДАННЫХ
    // ==================================================================

    @SuppressWarnings("unchecked")
    private void загрузитьВсеВерсии(Path dataRoot) {
        Path dir = dataRoot.resolve("blocks");
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path f : stream.filter(p -> p.getFileName().toString().startsWith("blocks.")
                    && p.getFileName().toString().endsWith(".yaml")).sorted().toList()) {
                Map<String, Object> doc;
                try (var in = Files.newInputStream(f)) {
                    doc = new org.yaml.snakeyaml.Yaml().load(in);
                } catch (IOException e) {
                    continue;
                }
                if (doc == null || !(doc.get("blocks") instanceof List<?> blockList)) {
                    continue;
                }
                String id = doc.get("meta") instanceof Map<?, ?> meta
                    && meta.get("id") != null ? String.valueOf(meta.get("id"))
                    : f.getFileName().toString();
                List<Face> small = new ArrayList<>();
                List<Face> big = new ArrayList<>();
                for (Object bo : blockList) {
                    Map<String, Object> b = (Map<String, Object>) bo;
                    String bid = String.valueOf(b.get("id"));
                    String kind = String.valueOf(b.get("kind"));
                    Map<String, Object> faces = (Map<String, Object>) b.get("faces");
                    for (String faceName : new String[]{"A", "B"}) {
                        if (!(faces.get(faceName) instanceof List<?> hexList)) {
                            continue;
                        }
                        List<HexRec> recs = new ArrayList<>();
                        for (Object ho : hexList) {
                            Map<String, Object> h = (Map<String, Object>) ho;
                            recs.add(new HexRec(
                                num(h.get("q")), num(h.get("r")),
                                num(h.get("cell")), num(h.getOrDefault("energy", -1))));
                        }
                        Face face = new Face(bid, kind, faceName, recs);
                        ("small".equals(kind) ? small : big).add(face);
                    }
                }
                versions.put(id, new Version(id, small, big));
            }
        } catch (IOException e) {
            // нет папки данных — каталог просто останется пустым
        }
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : -1;
    }

    // ==================================================================
    //  ПЛИТКА: ДВА СТОЛБЦА, ПРОЛИСТЫВАНИЕ ВНИЗ
    // ==================================================================

    /**
     * Все двадцать сторон набора одной плиткой: строка — один блок, слева его
     * сторона А, справа сторона Б.
     *
     * <p>Ширина карточки — половина окна, поэтому блок рисуется тем крупнее, чем
     * шире окно, и разметка ячеек читается без прищуривания. Высота строки
     * привязана к ширине карточки (не наоборот): у блоков фиксированная форма, и
     * если считать высоту первой, широкое окно оставит по бокам пустоту.
     *
     * <p>Размеры и цвета — только из {@link Theme}: собственные пиксели и
     * {@code new Color(...)} по месту рассыпаются при смене масштаба и темы, чем
     * этот экран и болел (карточка была жёстко 260×230 и белая).
     */
    private static final class Плитка extends JComponent implements Scrollable {
        private List<Face> малые = List.of();
        private List<Face> большие = List.of();
        /** Поворот показа: шагов по 60° по часовой стрелке (0..5). */
        private int поворот;

        /**
         * ВЫСОТА СТРОКИ СЧИТАЕТСЯ ПО САМОМУ ВЫСОКОМУ БЛОКУ РАЗДЕЛА, а не берётся
         * долей от ширины наугад.
         *
         * <p>Иначе получается ровно та беда, из-за которой вкладку и переделали:
         * лишняя высота — это пустые поля внутри карточек и лишняя прокрутка, а
         * нехватка — сплюснутый блок с полями по бокам. Габарит зависит и от
         * формы блока, и от поворота показа, поэтому меряется каждый раз.
         */
        private double долиВысоты(List<Face> faces) {
            double худшая = 0.5;
            for (Face f : faces) {
                double[] габ = габарит(f);
                худшая = Math.max(худшая, габ[1] / габ[0]);
            }
            return худшая;
        }

        /** Ширина и высота блока при пробном радиусе гекса 100. */
        private double[] габарит(Face f) {
            double проба = 100;
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (HexRec hx : повернутые(f.hexes())) {
                double[] c = FieldGeometry.hexCenter(hx.q(), hx.r(), проба);
                minX = Math.min(minX, c[0]);
                maxX = Math.max(maxX, c[0]);
                minY = Math.min(minY, c[1]);
                maxY = Math.max(maxY, c[1]);
            }
            return new double[]{(maxX - minX) + 2 * проба, (maxY - minY) + 2 * проба};
        }

        void показать(List<Face> м, List<Face> б) {
            малые = м;
            большие = б;
            revalidate();
            repaint();
        }

        void поворот(int шагов) {
            поворот = шагов;
            repaint();
        }

        /** Ширина карточки при текущей ширине компонента: ровно два столбца. */
        private int ширинаКарточки() {
            int свободно = Math.max(Theme.px(200), getWidth())
                - 2 * Theme.px(Theme.PAD_PANEL) - Theme.px(Theme.GAP_TILE);
            return Math.max(Theme.px(120), свободно / 2);
        }

        private int высотаКарточки(List<Face> faces) {
            int поле = Theme.px(Theme.PAD_TILE) * 2;
            int подпись = Theme.px(Theme.PAD_TILE) * 2 + Theme.px(12);
            double блок = (ширинаКарточки() - поле) * долиВысоты(faces);
            int h = (int) Math.round(блок) + поле + подпись;
            // ПОТОЛОК ПО ВЫСОТЕ ОКНА. Без него карточка растёт вслед за шириной,
            // и на экран влезает одна строка: листать двадцать сторон пришлось бы
            // десятью экранами. С потолком видно больше двух строк сразу, а блок
            // остаётся крупным — он просто перестаёт растягиваться по ширине и
            // центрируется с полями по бокам.
            if (getParent() instanceof javax.swing.JViewport vp && vp.getHeight() > 0) {
                h = Math.min(h, (int) Math.round(vp.getHeight() / 2.2));
            }
            return Math.max(Theme.px(150), h);
        }

        /** Полная высота содержимого: два заголовка разделов и строки блоков. */
        private int высотаВсего() {
            int строкМалых = (малые.size() + 1) / 2;
            int строкБольших = (большие.size() + 1) / 2;
            int h = Theme.px(Theme.PAD_PANEL);
            if (строкМалых > 0) {
                h += высотаЗаголовка()
                    + строкМалых * (высотаКарточки(малые) + Theme.px(Theme.GAP_TILE));
            }
            if (строкБольших > 0) {
                h += Theme.px(Theme.GAP_BLOCK) + высотаЗаголовка()
                    + строкБольших * (высотаКарточки(большие) + Theme.px(Theme.GAP_TILE));
            }
            return h + Theme.px(Theme.PAD_PANEL);
        }

        private static int высотаЗаголовка() {
            return Theme.px(22);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Math.max(Theme.px(320), getWidth()), высотаВсего());
        }

        // Прокрутка по ширине окна: горизонтальной полосы у плитки нет вовсе,
        // столбцы всегда ровно два и тянутся вместе с окном.
        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) {
            return Theme.px(24);
        }

        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
            return Math.max(Theme.px(24), r.height - Theme.px(24));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Theme.bg());
            g.fillRect(0, 0, getWidth(), getHeight());

            int y = Theme.px(Theme.PAD_PANEL);
            y = рисоватьРаздел(g, y, малые,
                "МАЛЫЕ БЛОКИ · 5 гексов · " + (малые.size() / 2) + " блоков, "
                    + малые.size() + " сторон");
            if (!большие.isEmpty() && !малые.isEmpty()) {
                y += Theme.px(Theme.GAP_BLOCK);
            }
            рисоватьРаздел(g, y, большие,
                "БОЛЬШИЕ БЛОКИ · 6 гексов · " + (большие.size() / 2) + " блоков, "
                    + большие.size() + " сторон");
            g.dispose();
        }

        /** Заголовок раздела и его строки; возвращает следующий свободный y. */
        private int рисоватьРаздел(Graphics2D g, int y, List<Face> faces, String заголовок) {
            if (faces.isEmpty()) {
                return y;
            }
            g.setFont(Theme.caption());
            g.setColor(Theme.ink3());
            g.drawString(заголовок, Theme.px(Theme.PAD_PANEL),
                y + высотаЗаголовка() - Theme.px(7));
            y += высотаЗаголовка();

            int cw = ширинаКарточки();
            int ch = высотаКарточки(faces);
            for (int i = 0; i < faces.size(); i++) {
                int столбец = i % 2;
                int x = Theme.px(Theme.PAD_PANEL) + столбец * (cw + Theme.px(Theme.GAP_TILE));
                рисоватьКарточку(g, x, y, cw, ch, faces.get(i));
                if (столбец == 1 || i == faces.size() - 1) {
                    y += ch + Theme.px(Theme.GAP_TILE);
                }
            }
            return y;
        }

        private void рисоватьКарточку(Graphics2D g, int x, int y, int w, int h, Face f) {
            int радиус = Theme.px(10);
            g.setColor(Theme.tile());
            g.fillRoundRect(x, y, w, h, радиус, радиус);
            g.setColor(Theme.border());
            g.drawRoundRect(x, y, w - 1, h - 1, радиус, радиус);

            String подпись = f.blockId + " · сторона " + f.faceName;
            g.setFont(Theme.font(12, Font.BOLD));
            g.setColor(Theme.ink());
            g.drawString(подпись, x + Theme.px(Theme.PAD_TILE),
                y + Theme.px(Theme.PAD_TILE) + g.getFontMetrics().getAscent());

            int верх = Theme.px(Theme.PAD_TILE) * 2 + Theme.px(12);
            рисоватьБлок(g, f, x + w / 2, y + верх + (h - верх) / 2,
                размерГекса(f, w, h - верх));
        }

        /**
         * Какой радиус гекса взять, чтобы блок целиком влез в карточку.
         *
         * <p>Считается по РАМКЕ САМОГО БЛОКА при пробном размере, а не по
         * прикидке «три гекса в ширину»: блоки в данных не симметричны началу
         * координат, а показ ещё и поворачивается на 60°, поэтому габарит меняется.
         * Мерить надо то, что будет нарисовано.
         */
        private double размерГекса(Face f, int w, int h) {
            double проба = 100;
            List<HexRec> гексы = повернутые(f.hexes());
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (HexRec hx : гексы) {
                double[] c = FieldGeometry.hexCenter(hx.q(), hx.r(), проба);
                minX = Math.min(minX, c[0]);
                maxX = Math.max(maxX, c[0]);
                minY = Math.min(minY, c[1]);
                maxY = Math.max(maxY, c[1]);
            }
            // Плюс один радиус с каждой стороны — центры не учитывают сам гекс.
            double шир = (maxX - minX) + 2 * проба;
            double выс = (maxY - minY) + 2 * проба;
            double поле = Theme.px(Theme.PAD_TILE) * 2;
            double доля = Math.min((w - поле) / шир, (h - поле) / выс);
            return Math.max(Theme.pxf(6), проба * доля);
        }

        /**
         * Сам блок: под каждым гексом — лёгкая заливка шести наземных секторов и
         * небесного (очень бледным, только чтобы дать глазу структуру гекса), а
         * поверх — ячейка энергии и ячейка контейнера цветами {@link Theme}, теми
         * же, какими они закрашены на планшетах и на поле в проигрывателе.
         */
        private void рисоватьБлок(Graphics2D g, Face f, int cx0, int cy0, double size) {
            List<HexRec> гексы = повернутые(f.hexes());
            // ЧТО ВХОДИТ В ЭТУ КАРТОНКУ — нужно, чтобы скруглить контур блока и
            // НЕ скруглять швы между его гексами: срезанные общие углы давали
            // дырки на стыках (правка дизайнера 19.08.2026).
            // Заодно считается рамка блока: повёрнутый блок центрируется по ней,
            // иначе после поворота вокруг гекса (0,0) фигура уезжала бы из
            // карточки — блоки в данных не обязаны быть симметричны началу.
            java.util.Set<Long> свои = new java.util.HashSet<>();
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (HexRec h : гексы) {
                свои.add((((long) h.q()) << 32) ^ (h.r() & 0xffffffffL));
                double[] c = FieldGeometry.hexCenter(h.q(), h.r(), size);
                minX = Math.min(minX, c[0]);
                maxX = Math.max(maxX, c[0]);
                minY = Math.min(minY, c[1]);
                maxY = Math.max(maxY, c[1]);
            }
            double сдвигX = cx0 - (minX + maxX) / 2;
            double сдвигY = cy0 - (minY + maxY) / 2;
            for (HexRec hx : гексы) {
                double[] c = FieldGeometry.hexCenter(hx.q(), hx.r(), size);
                double cx = сдвигX + c[0];
                double cy = сдвигY + c[1];

                рисоватьСекторы(g, cx, cy, size);

                // КОНТУР ГЕКСА — СО СКРУГЛЁННЫМИ УГЛАМИ и той же функцией
                // геометрии, что у поля (решение дизайнера 19.08.2026). Свой
                // цикл по шести углам убран: он и был причиной, по которой
                // каталог жил своей формой отдельно от поля.
                boolean[] nb = new boolean[6];
                for (int side = 0; side < 6; side++) {
                    int[] d = kelium.core.Field.AXIAL_DIRS[side];
                    nb[side] = свои.contains(
                        (((long) (hx.q() + d[0])) << 32) ^ ((hx.r() + d[1]) & 0xffffffffL));
                }
                g.setStroke(new BasicStroke(1.4f));
                g.setColor(new Color(0x8A8F98));
                g.draw(FieldGeometry.outlineRoundedHexPath(cx, cy, size,
                    FieldGeometry.TILE_ROUND, nb));

                double apothem = FieldGeometry.apothem(size);
                if (hx.energy() >= 0 && hx.energy() < 6) {
                    рисоватьМетку(g, cx, cy, apothem, hx.energy(), Theme.energy(), true, size);
                }
                if (hx.container() == 6) {
                    // ВОЗДУШНАЯ ячейка контейнера — в центре гекса, там же, где
                    // воздушный сектор.
                    рисоватьМеткуВЦентре(g, cx, cy, Theme.container(), size);
                } else if (hx.container() >= 0) {
                    рисоватьМетку(g, cx, cy, apothem, hx.container(), Theme.container(), false, size);
                }
            }
        }

        /**
         * ГЕКСЫ СТОРОНЫ, ПОВЁРНУТЫЕ на {@link #поворот} шагов по 60° по часовой.
         *
         * <p>Математика поворота выведена из геометрии поля ({@code hexCenter}:
         * x = 1.5q, y = √3(r + q/2), ось y экрана вниз): шаг по часовой стрелке —
         * это {@code (q, r) → (−r, q + r)}. Ячейки контейнера и энергии привязаны
         * к НОМЕРУ СТОРОНЫ гекса, а нумерация сторон {@code Field.AXIAL_DIRS}
         * идёт ПРОТИВ часовой — поэтому при повороте по часовой номер стороны
         * сдвигается назад: {@code k → (k + 5) % 6}. Воздушная ячейка (номер 6)
         * стороне не принадлежит и не поворачивается.
         */
        private List<HexRec> повернутые(List<HexRec> исходные) {
            if (поворот == 0) {
                return исходные;
            }
            List<HexRec> out = new ArrayList<>(исходные.size());
            for (HexRec h : исходные) {
                int q = h.q();
                int r = h.r();
                int контейнер = h.container();
                int энергия = h.energy();
                for (int i = 0; i < поворот; i++) {
                    int nq = -r;
                    r = q + r;
                    q = nq;
                    if (контейнер >= 0 && контейнер < 6) {
                        контейнер = (контейнер + 5) % 6;
                    }
                    if (энергия >= 0 && энергия < 6) {
                        энергия = (энергия + 5) % 6;
                    }
                }
                out.add(new HexRec(q, r, контейнер, энергия));
            }
            return out;
        }

        /**
         * Очень лёгкая заливка шести наземных секторов гекса и небесного сектора
         * в центре — только структура, без спора с маркерами ячеек.
         */
        private void рисоватьСекторы(Graphics2D g, double cx, double cy, double size) {
            Color наземный = new Color(0, 0, 0, 12);
            Color небесный = new Color(30, 110, 200, 22);
            for (int k = 0; k < 6; k++) {
                double a1 = Math.toRadians(60 * k - 90 + FieldGeometry.TILT);
                double a2 = Math.toRadians(60 * (k + 1) - 90 + FieldGeometry.TILT);
                java.awt.Polygon sector = new java.awt.Polygon();
                sector.addPoint((int) cx, (int) cy);
                sector.addPoint((int) Math.round(cx + size * Math.cos(a1)),
                    (int) Math.round(cy + size * Math.sin(a1)));
                sector.addPoint((int) Math.round(cx + size * Math.cos(a2)),
                    (int) Math.round(cy + size * Math.sin(a2)));
                g.setColor(наземный);
                g.fillPolygon(sector);
            }
            // ВОЗДУШНЫЙ СЕКТОР — ГЕКСАГОНАЛЬНЫЙ, а не круглый (замечание
            // дизайнера 19.08.2026). Рисовальщик поля рисует его именно
            // шестиугольником по AIR_CELL_R, и круг здесь был отсебятиной.
            g.setColor(небесный);
            g.fill(FieldGeometry.path(FieldGeometry.hexCorners(cx, cy,
                size * FieldGeometry.AIR_CELL_R)));
        }

        /**
         * МАРКЕР ЯЧЕЙКИ НА СТОРОНЕ ГЕКСА — ровно тот же, что на поле.
         *
         * <p>ПЕРЕПИСАНО 19.08.2026 по замечанию дизайнера: энергозона рисовалась
         * кругом, а контейнер — ромбом по осям экрана, и ни то, ни другое не
         * совпадало с полем. На поле энергоячейка — это ОБВОДКА ТРАПЕЦИИ по
         * границам ячейки с молнией посередине, а контейнер — КВАДРАТ, повёрнутый
         * по своей стороне гекса. Обе фигуры теперь берутся из
         * {@link FieldGeometry}, там же, откуда их берёт рисовальщик поля, — иначе
         * они снова разъедутся при первой же правке.
         */
        private void рисоватьМетку(Graphics2D g, double cx, double cy, double apothem,
                                   int side, Color color, boolean энергия, double size) {
            if (энергия) {
                float stroke = (float) Math.max(1.2, size * 0.055);
                g.setColor(color);
                g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
                g.draw(FieldGeometry.path(
                    FieldGeometry.energyCellOutline(cx, cy, size, side, stroke)));
                double[] spot = FieldGeometry.energyCellSpot(cx, cy, size, side);
                g.fill(FieldGeometry.path(
                    FieldGeometry.boltPolygon(spot[0], spot[1], size * 0.24)));
                return;
            }
            var quad = FieldGeometry.path(
                FieldGeometry.containerCellQuad(cx, cy, size, side, size * 0.24));
            g.setColor(color);
            g.fill(quad);
            g.setColor(new Color(0, 0, 0, 110));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(quad);
        }

        /**
         * Контейнер в ВОЗДУШНОЙ ячейке — в центре гекса, там же, где авиация.
         * Форма та же, что у ячеек на сторонах (квадрат), только без поворота:
         * воздушная ячейка ничьей стороне не принадлежит. Фигуру, как и там,
         * даёт {@link FieldGeometry} — ячейка 6 у неё и означает воздушную.
         */
        private void рисоватьМеткуВЦентре(Graphics2D g, double cx, double cy, Color color,
                                          double size) {
            var quad = FieldGeometry.path(
                FieldGeometry.containerCellQuad(cx, cy, size, 6, size * 0.24));
            g.setColor(color);
            g.fill(quad);
            g.setColor(new Color(0, 0, 0, 110));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(quad);
        }
    }
}
