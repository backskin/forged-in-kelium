package kelium.gui.cardshop;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;

/**
 * ИНСПЕКТОР ЧАСТИ КАРТЫ: выбрана на макете или в панели — здесь её сдвиг,
 * размер, прозрачность, цвет и обводка текста, промежуток между значками,
 * слой и «скрыть».
 *
 * <p>ЭТА КАРТА И ВЕСЬ КАТАЛОГ (дизайнер 12.10.2026: «переключаюсь на весь
 * каталог, меняю — меняется всё, кроме этой карты»). Правило каталога — основа
 * для всех карт типа; правка карты лежит поверх него и главнее. Поэтому:
 * <ul>
 *   <li>на «Этой карте» поля показывают, как карта выглядит сейчас (каталог +
 *       её правка), и меняют только её;</li>
 *   <li>на «Всём каталоге» меняется правило каталога, и у ТЕКУЩЕЙ карты своя
 *       правка того же поля снимается — она тут же следует каталогу;</li>
 *   <li>«Применить ко всем картам» — то, как часть выглядит на этой карте,
 *       становится правилом каталога, а свои правки этой части у всех карт
 *       типа снимаются;</li>
 *   <li>«Сбросить» на карте — вернуть часть к правилу каталога; на каталоге —
 *       снять правило каталога.</li>
 * </ul>
 */
final class ElementInspector extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Откуда и куда писать правки. */
    interface Store {
        /** Правки элемента: карта (catalog=false) или каталог (true); изменяемая запись. */
        Map<String, Object> style(String id, boolean catalog);

        /** Правка внесена — перерисовать (fast — во время перетаскивания). */
        void changed(boolean catalog, boolean fast);

        /** Как часть выглядит на этой карте: правило каталога, поверх — правка карты. */
        default Map<String, Object> effective(String id) {
            Map<String, Object> m = new LinkedHashMap<>(style(id, true));
            m.putAll(style(id, false));
            return m;
        }

        /** Сделать вид части на этой карте правилом каталога; правки этой части у карт снять. */
        void applyToAll(String id);

        /** Рамка части на последнем рисунке, пиксели карты (null — нет). */
        Rectangle2D region(String id);

        /** Размер карты, пиксели (null — нет). */
        Dimension cardSize();
    }

    private final Store store;
    private final JLabel title = new JLabel();
    private final JToggleButton forCard = new JToggleButton("Эта карта");
    private final JToggleButton forAll = new JToggleButton("Весь каталог");
    private final JLabel scopeHint = new JLabel();
    private final JButton applyAll = new JButton("Применить ко всем картам");
    private final JSpinner dx = spin(-600, 600, 1);
    private final JSpinner dy = spin(-600, 600, 1);
    private final JSlider scale = new JSlider(20, 300, 100);
    private final JLabel scaleV = new JLabel();
    private final JSlider fade = new JSlider(0, 100, 0);
    private final JLabel fadeV = new JLabel();
    private final JSlider size = new JSlider(50, 200, 100);
    private final JLabel sizeV = new JLabel();
    private final JLabel sizeL = new JLabel("Кегль текста");
    private final JSlider lead = new JSlider(60, 200, 100);
    private final JLabel leadV = new JLabel();
    private final JLabel leadL = new JLabel("Межстрочный интервал");
    private final JSlider step = new JSlider(20, 160, 78);
    private final JLabel stepV = new JLabel();
    private final JLabel stepL = new JLabel("Шаг иконок в стопке");
    private final JSlider gap = new JSlider(-40, 60, 0);
    private final JLabel gapV = new JLabel();
    private final JSlider letters = new JSlider(40, 160, 100);
    private final JLabel lettersV = new JLabel();
    private final JLabel lettersL = new JLabel("Ширина букв");
    private final JSlider plate = new JSlider(0, 100, 15);
    private final JLabel plateV = new JLabel();
    private final JLabel plateL = new JLabel("Подложка под текстом");
    private final JButton ink = new JButton("цвет");
    private final JButton inkClear = new JButton("×");
    private final JCheckBox strokeOn = new JCheckBox("обводка");
    private final JButton strokeInk = new JButton("цвет");
    private final JSpinner strokeW = spinD(0, 12, 0.5);
    private final JLabel layerV = new JLabel();
    private final JCheckBox hidden = new JCheckBox("Скрыть на карте");
    private final JPanel body = new JPanel();
    private String id;
    private boolean quiet;

    ElementInspector(Store store) {
        this.store = store;
        setLayout(new MigLayout("insets 10 14 10 14, fillx, wrap 1, gapy 6", "[grow,fill]", ""));
        setBackground(Style.PANEL);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Style.LINE));
        title.setFont(Style.title(15));
        title.setForeground(Style.INK);
        add(title);
        body.setOpaque(false);
        body.setLayout(new MigLayout("insets 0, fillx, gapy 4, hidemode 3", "[150!]10[grow,fill]10[60!]", ""));
        ButtonGroup g = new ButtonGroup();
        g.add(forCard);
        g.add(forAll);
        forCard.setSelected(true);
        for (JToggleButton b : new JToggleButton[] {forCard, forAll}) {
            b.setFocusable(false);
            b.addActionListener(e -> load());
        }
        JPanel scope = row();
        scope.add(forCard);
        scope.add(forAll);
        applyAll.setFocusable(false);
        applyAll.setToolTipText("то, как эта часть выглядит на этой карте, станет правилом "
            + "для всех карт каталога; свои правки этой части у карт снимутся");
        applyAll.addActionListener(e -> {
            store.applyToAll(id);
            load();
            store.changed(true, false);
        });
        scope.add(applyAll);
        body.add(label("Правка для"));
        body.add(scope, "span 2, wrap");
        scopeHint.setForeground(Style.INK3);
        body.add(scopeHint, "skip 1, span 2, wrap");

        JPanel xy = row();
        xy.add(new JLabel("X"));
        xy.add(dx);
        xy.add(new JLabel("Y"));
        xy.add(dy);
        JButton cx = small("⇔ по центру", "поставить часть по центру карты по горизонтали");
        cx.addActionListener(e -> centre(true));
        JButton cy = small("⇕ по центру", "поставить часть по центру карты по вертикали");
        cy.addActionListener(e -> centre(false));
        xy.add(cx);
        xy.add(cy);
        body.add(label("Сдвиг, пикс."));
        body.add(xy, "span 2, wrap");
        body.add(label("Размер"));
        body.add(scale);
        body.add(scaleV, "wrap");
        body.add(label("Прозрачность"));
        body.add(fade);
        body.add(fadeV, "wrap");

        // ЦВЕТ И ОБВОДКА (дизайнер 12.10.2026: «с цветом — вот самое важное»)
        JPanel colour = row();
        ink.setFocusable(false);
        ink.addActionListener(e -> pick("цвет", "Цвет текста"));
        inkClear.setFocusable(false);
        inkClear.setToolTipText("вернуть цвет, как задумано");
        inkClear.addActionListener(e -> {
            remove("цвет");
        });
        colour.add(ink);
        colour.add(inkClear);
        body.add(label("Цвет текста"));
        body.add(colour, "span 2, wrap");
        JPanel stroke = row();
        strokeOn.setOpaque(false);
        strokeOn.setFocusable(false);
        strokeOn.addActionListener(e -> {
            if (quiet) {
                return;
            }
            double w = ((Number) strokeW.getValue()).doubleValue();
            put("обводка", strokeOn.isSelected() ? (w > 0 ? w : 2.0) : 0.0);
            load();
        });
        strokeInk.setFocusable(false);
        strokeInk.addActionListener(e -> pick("обводка_цвет", "Цвет обводки"));
        strokeW.addChangeListener(e -> {
            double w = ((Number) strokeW.getValue()).doubleValue();
            put("обводка", w);
            quiet = true;
            strokeOn.setSelected(w > 0);
            quiet = false;
        });
        stroke.add(strokeOn);
        stroke.add(strokeInk);
        stroke.add(new JLabel("толщина"));
        stroke.add(strokeW);
        JButton strokeClear = small("×", "обводка как задумано");
        strokeClear.addActionListener(e -> {
            Map<String, Object> st = store.style(id, forAll.isSelected());
            st.remove("обводка");
            st.remove("обводка_цвет");
            load();
            store.changed(forAll.isSelected(), false);
        });
        stroke.add(strokeClear);
        body.add(label("Обводка текста"));
        body.add(stroke, "span 2, wrap");

        sizeL.setForeground(Style.INK2);
        leadL.setForeground(Style.INK2);
        body.add(sizeL);
        body.add(size);
        body.add(sizeV, "wrap");
        body.add(leadL);
        body.add(lead);
        body.add(leadV, "wrap");
        JLabel gapL = label("Промежуток между значками");
        gapL.setToolTipText("меньше нуля — значки и слова заходят друг на друга; "
            + "в тексте «{а}{б}» без пробела — вплотную, «{а} {б}» — через пробел");
        body.add(gapL);
        body.add(gap);
        body.add(gapV, "wrap");
        stepL.setForeground(Style.INK2);
        stepL.setToolTipText("меньше — иконки плотнее друг на друге, больше — шире");
        body.add(stepL);
        body.add(step);
        body.add(stepV, "wrap");
        lettersL.setForeground(Style.INK2);
        body.add(lettersL);
        body.add(letters);
        body.add(lettersV, "wrap");
        plateL.setForeground(Style.INK2);
        body.add(plateL);
        body.add(plate);
        body.add(plateV, "wrap");

        // СЛОЙ (дизайнер 12.10.2026: «выше или ниже предыдущего — непонятно»)
        JPanel layer = row();
        JButton down = small("▼ ниже", "положить часть под остальные");
        down.addActionListener(e -> shiftLayer(-1));
        JButton up = small("▲ выше", "положить часть поверх остальных");
        up.addActionListener(e -> shiftLayer(1));
        layer.add(down);
        layer.add(layerV);
        layer.add(up);
        body.add(label("Слой"));
        body.add(layer, "span 2, wrap");

        JButton reset = new JButton("Сбросить");
        reset.setFocusable(false);
        reset.addActionListener(e -> {
            store.style(id, forAll.isSelected()).clear();
            load();
            store.changed(forAll.isSelected(), false);
        });
        body.add(hidden, "skip 1, split 2");
        body.add(reset, "wrap");
        add(body);
        dx.addChangeListener(e -> put("dx", ((Number) dx.getValue()).doubleValue()));
        dy.addChangeListener(e -> put("dy", ((Number) dy.getValue()).doubleValue()));
        scale.addChangeListener(e -> {
            scaleV.setText(scale.getValue() + "%");
            put("масштаб", scale.getValue());
        });
        fade.addChangeListener(e -> {
            fadeV.setText(fade.getValue() + "%");
            put("прозрачность", fade.getValue());
        });
        plate.addChangeListener(e -> {
            plateV.setText(plate.getValue() + "%");
            put("подложка", plate.getValue());
        });
        gap.addChangeListener(e -> {
            gapV.setText(gap.getValue() + "");
            put("промежуток", gap.getValue());
        });
        hidden.addActionListener(e -> put("скрыт", hidden.isSelected()));
        letters.addChangeListener(e -> {
            lettersV.setText(letters.getValue() + "%");
            put("ширина_букв", letters.getValue());
        });
        step.addChangeListener(e -> {
            stepV.setText(step.getValue() + "%");
            put("шаг", step.getValue());
        });
        size.addChangeListener(e -> {
            sizeV.setText(size.getValue() + "%");
            put("кегль", size.getValue());
        });
        lead.addChangeListener(e -> {
            leadV.setText(lead.getValue() + "%");
            put("интервал", lead.getValue());
        });
        show(null);
    }

    private static JPanel row() {
        JPanel p = new JPanel(new MigLayout("insets 0, gapx 6", "", ""));
        p.setOpaque(false);
        return p;
    }

    private static JButton small(String t, String tip) {
        JButton b = new JButton(t);
        b.setFocusable(false);
        b.setToolTipText(tip);
        return b;
    }

    private static JSpinner spin(int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(0, min, max, step));
        s.setPreferredSize(new Dimension(70, 28));
        return s;
    }

    private static JSpinner spinD(double min, double max, double step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(0.0, min, max, step));
        s.setPreferredSize(new Dimension(60, 28));
        return s;
    }

    private static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Style.INK2);
        return l;
    }

    /** Показать часть карты id (null — подсказка «щёлкните по карте»). */
    void show(String id) {
        this.id = id;
        if (id == null) {
            title.setText("Щёлкните по части карты справа — здесь появятся её настройки");
            title.setForeground(Style.INK3);
            body.setVisible(false);
        } else {
            title.setText("Выбрано: " + Elements.ru(id));
            title.setForeground(Style.ACCENT);
            body.setVisible(true);
            load();
        }
        revalidate();
        repaint();
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
        }
    }

    String current() {
        return id;
    }

    boolean catalogScope() {
        return forAll.isSelected();
    }

    /** Что показывать в полях: на карте — как она выглядит, на каталоге — его правило. */
    private Map<String, Object> shown() {
        return forAll.isSelected() ? store.style(id, true) : store.effective(id);
    }

    /** Значения выбранной правки (карта или каталог) в поля. */
    void load() {
        if (id == null) {
            return;
        }
        quiet = true;
        boolean cat = forAll.isSelected();
        boolean own = !store.style(id, false).isEmpty();
        applyAll.setVisible(!cat);
        scopeHint.setText(cat
            ? "меняется правило для всех карт каталога — и для этой"
            : own ? "у этой карты своя правка этой части — она главнее каталога"
                  : "эта карта следует каталогу; правка ляжет только на неё");
        Map<String, Object> st = shown();
        dx.setValue((int) Math.round(num(st, "dx", 0)));
        dy.setValue((int) Math.round(num(st, "dy", 0)));
        scale.setValue((int) num(st, "масштаб", 100));
        scaleV.setText(scale.getValue() + "%");
        fade.setValue((int) num(st, "прозрачность", 0));
        fadeV.setText(fade.getValue() + "%");
        boolean texty = Elements.TEXT.contains(id) || id.startsWith("свой_");
        for (javax.swing.JComponent c : new javax.swing.JComponent[] {size, sizeV, sizeL, lead, leadV, leadL}) {
            c.setVisible(texty);
        }
        size.setValue((int) num(st, "кегль", 100));
        sizeV.setText(size.getValue() + "%");
        lead.setValue((int) num(st, "интервал", 100));
        leadV.setText(lead.getValue() + "%");
        gap.setValue((int) num(st, "промежуток", 0));
        gapV.setText(gap.getValue() + "");
        boolean squeeze = Elements.SQUEEZE.contains(id);
        for (javax.swing.JComponent c : new javax.swing.JComponent[] {letters, lettersV, lettersL}) {
            c.setVisible(squeeze);
        }
        letters.setValue((int) num(st, "ширина_букв", 100));
        lettersV.setText(letters.getValue() + "%");
        boolean stack = Elements.STACK.contains(id);
        for (javax.swing.JComponent c : new javax.swing.JComponent[] {step, stepV, stepL}) {
            c.setVisible(stack);
        }
        step.setValue((int) num(st, "шаг", 78));
        stepV.setText(step.getValue() + "%");
        boolean plated = Elements.PLATED.contains(id);
        plate.setVisible(plated);
        plateV.setVisible(plated);
        plateL.setVisible(plated);
        plate.setValue((int) num(st, "подложка", Elements.plateDefault(id)));
        plateV.setText(plate.getValue() + "%");
        swatch(ink, CardCanvas.parseColor(st.get("цвет")));
        swatch(strokeInk, CardCanvas.parseColor(st.get("обводка_цвет")));
        boolean hasStroke = st.get("обводка") instanceof Number;
        double w = hasStroke ? ((Number) st.get("обводка")).doubleValue() : 0;
        strokeW.setValue(Math.max(0, Math.min(12, w)));
        strokeOn.setSelected(hasStroke && w > 0);
        int z = (int) Math.round(num(st, "слой", 0));
        layerV.setText(z == 0 ? "как задумано" : z < 0 ? "ниже остальных (" + z + ")"
            : "выше остальных (+" + z + ")");
        hidden.setSelected(Boolean.TRUE.equals(st.get("скрыт")));
        quiet = false;
    }

    private static void swatch(JButton b, Color c) {
        b.setText(c == null ? "как задумано" : String.format("#%06X", c.getRGB() & 0xFFFFFF));
        b.setBackground(c);
        b.setOpaque(c != null);
        if (c != null) {
            double l = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
            b.setForeground(l > 140 ? Color.BLACK : Color.WHITE);
        } else {
            b.setForeground(null);
        }
    }

    private void pick(String key, String what) {
        if (id == null) {
            return;
        }
        Color now = CardCanvas.parseColor(shown().get(key));
        Color c = JColorChooser.showDialog(this, what + " — " + Elements.ru(id),
            now != null ? now : Color.WHITE);
        if (c != null) {
            put(key, String.format("#%06X", c.getRGB() & 0xFFFFFF));
            if ("обводка_цвет".equals(key) && !(shown().get("обводка") instanceof Number n && n.doubleValue() > 0)) {
                put("обводка", 2.0);
            }
            load();
        }
    }

    private void remove(String key) {
        if (id == null) {
            return;
        }
        store.style(id, forAll.isSelected()).remove(key);
        if (forAll.isSelected()) {
            store.style(id, false).remove(key);
        }
        load();
        store.changed(forAll.isSelected(), false);
    }

    private void shiftLayer(int d) {
        if (id == null) {
            return;
        }
        int z = (int) Math.round(num(shown(), "слой", 0)) + d;
        z = Math.max(-5, Math.min(5, z));
        put("слой", z);
        load();
    }

    /** Поставить часть по центру карты: сдвиг меняется так, чтобы рамка встала посередине. */
    private void centre(boolean horizontal) {
        Rectangle2D r = store.region(id);
        Dimension cs = store.cardSize();
        if (r == null || cs == null) {
            return;
        }
        Map<String, Object> st = shown();
        if (horizontal) {
            double v = num(st, "dx", 0) + (cs.width / 2.0 - r.getCenterX());
            put("dx", (double) Math.round(v));
        } else {
            double v = num(st, "dy", 0) + (cs.height / 2.0 - r.getCenterY());
            put("dy", (double) Math.round(v));
        }
        load();
    }

    private static double num(Map<String, Object> m, String k, double def) {
        return m.get(k) instanceof Number n ? n.doubleValue() : def;
    }

    private void put(String key, Object v) {
        if (quiet || id == null) {
            return;
        }
        boolean cat = forAll.isSelected();
        store.style(id, cat).put(key, v);
        if (cat) {
            // правило каталога меняют — текущая карта следует ему, а не своей правке
            store.style(id, false).remove(key);
        }
        store.changed(cat, false);
    }

    /** Перетаскивание на макете: сдвиг от начального значения. */
    private double baseX;
    private double baseY;

    void dragStart() {
        Map<String, Object> st = shown();
        baseX = num(st, "dx", 0);
        baseY = num(st, "dy", 0);
    }

    void dragTo(double ddx, double ddy, boolean done) {
        if (id == null) {
            return;
        }
        boolean cat = forAll.isSelected();
        Map<String, Object> st = store.style(id, cat);
        st.put("dx", Math.round(baseX + ddx));
        st.put("dy", Math.round(baseY + ddy));
        quiet = true;
        dx.setValue((int) Math.round(baseX + ddx));
        dy.setValue((int) Math.round(baseY + ddy));
        quiet = false;
        // пока тянут — карта не перерисовывается (рамка на макете едет сама),
        // отпустили — одна перерисовка
        if (done) {
            if (cat) {
                Map<String, Object> own = store.style(id, false);
                own.remove("dx");
                own.remove("dy");
            }
            store.changed(cat, false);
            dragStart();
        }
    }

    static Map<String, Object> newStyle() {
        return new LinkedHashMap<>();
    }

    static final Color HL = new Color(0xE0, 0x4A, 0x36, 60);
}
