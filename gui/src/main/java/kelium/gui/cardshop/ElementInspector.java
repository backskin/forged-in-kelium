package kelium.gui.cardshop;

import java.awt.Color;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;

/**
 * ИНСПЕКТОР ЧАСТИ КАРТЫ: выбрана на макете или в панели — здесь её сдвиг,
 * размер, прозрачность, подложка и «скрыть». Правка — для этой карты или на
 * весь каталог типа (каталог — основа, карта — поверх).
 */
final class ElementInspector extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Откуда и куда писать правки. */
    interface Store {
        /** Правки элемента: карта (catalog=false) или каталог (true); изменяемая запись. */
        Map<String, Object> style(String id, boolean catalog);

        /** Правка внесена — перерисовать (fast — во время перетаскивания). */
        void changed(boolean catalog, boolean fast);
    }

    private final Store store;
    private final JLabel title = new JLabel();
    private final JToggleButton forCard = new JToggleButton("Эта карта");
    private final JToggleButton forAll = new JToggleButton("Весь каталог");
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
    private final JLabel stepL = new JLabel("Шаг иконок");
    private final JSlider plate = new JSlider(0, 100, 15);
    private final JLabel plateV = new JLabel();
    private final JLabel plateL = new JLabel("Подложка под текстом");
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
        body.setLayout(new MigLayout("insets 0, fillx, gapy 4", "[130!]10[grow,fill]10[60!]", ""));
        ButtonGroup g = new ButtonGroup();
        g.add(forCard);
        g.add(forAll);
        forCard.setSelected(true);
        forCard.setFocusable(false);
        forAll.setFocusable(false);
        forCard.addActionListener(e -> load());
        forAll.addActionListener(e -> load());
        JPanel scope = new JPanel(new MigLayout("insets 0", "[][]", ""));
        scope.setOpaque(false);
        scope.add(forCard);
        scope.add(forAll);
        body.add(label("Правка для"));
        body.add(scope, "span 2, wrap");
        JPanel xy = new JPanel(new MigLayout("insets 0", "[]4[70!]12[]4[70!]", ""));
        xy.setOpaque(false);
        xy.add(new JLabel("X"));
        xy.add(dx);
        xy.add(new JLabel("Y"));
        xy.add(dy);
        body.add(label("Сдвиг, пикс."));
        body.add(xy, "span 2, wrap");
        body.add(label("Размер"));
        body.add(scale);
        body.add(scaleV, "wrap");
        body.add(label("Прозрачность"));
        body.add(fade);
        body.add(fadeV, "wrap");
        sizeL.setForeground(Style.INK2);
        leadL.setForeground(Style.INK2);
        body.add(sizeL);
        body.add(size);
        body.add(sizeV, "wrap");
        body.add(leadL);
        body.add(lead);
        body.add(leadV, "wrap");
        stepL.setForeground(Style.INK2);
        stepL.setToolTipText("меньше — иконки плотнее друг на друге, больше — шире");
        body.add(stepL);
        body.add(step);
        body.add(stepV, "wrap");
        plateL.setForeground(Style.INK2);
        body.add(plateL);
        body.add(plate);
        body.add(plateV, "wrap");
        JButton reset = new JButton("Сбросить правки");
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
        hidden.addActionListener(e -> put("скрыт", hidden.isSelected()));
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

    private static JSpinner spin(int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(0, min, max, step));
        s.setPreferredSize(new Dimension(70, 28));
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
    }

    String current() {
        return id;
    }

    boolean catalogScope() {
        return forAll.isSelected();
    }

    /** Значения выбранной правки (карта или каталог) в поля. */
    void load() {
        if (id == null) {
            return;
        }
        quiet = true;
        Map<String, Object> st = store.style(id, forAll.isSelected());
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
        hidden.setSelected(Boolean.TRUE.equals(st.get("скрыт")));
        quiet = false;
    }

    private static double num(Map<String, Object> m, String k, double def) {
        return m.get(k) instanceof Number n ? n.doubleValue() : def;
    }

    private void put(String key, Object v) {
        if (quiet || id == null) {
            return;
        }
        store.style(id, forAll.isSelected()).put(key, v);
        store.changed(forAll.isSelected(), false);
    }

    /** Перетаскивание на макете: сдвиг от начального значения. */
    private double baseX;
    private double baseY;

    void dragStart() {
        Map<String, Object> st = store.style(id, forAll.isSelected());
        baseX = num(st, "dx", 0);
        baseY = num(st, "dy", 0);
    }

    void dragTo(double ddx, double ddy, boolean done) {
        if (id == null) {
            return;
        }
        Map<String, Object> st = store.style(id, forAll.isSelected());
        st.put("dx", Math.round(baseX + ddx));
        st.put("dy", Math.round(baseY + ddy));
        quiet = true;
        dx.setValue((int) Math.round(baseX + ddx));
        dy.setValue((int) Math.round(baseY + ddy));
        quiet = false;
        // пока тянут — карта не перерисовывается (рамка на макете едет сама),
        // отпустили — одна перерисовка
        if (done) {
            store.changed(forAll.isSelected(), false);
            dragStart();
        }
    }

    static Map<String, Object> newStyle() {
        return new LinkedHashMap<>();
    }

    static final Color HL = new Color(0xE0, 0x4A, 0x36, 60);
}
