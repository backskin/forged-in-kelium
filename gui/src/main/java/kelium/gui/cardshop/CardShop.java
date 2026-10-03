package kelium.gui.cardshop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

import net.miginfocom.swing.MigLayout;

/**
 * МАСТЕРСКАЯ КАРТ (заказ дизайнера 28.09.2026): выбираешь тип карты, заполняешь
 * разделы — верх, название и условие, награду, дополнительное, номер — и сразу
 * видишь карту на пустом шаблоне.
 *
 * <p>Иконки видны везде: под каждым текстовым полем — живая строка с настоящими
 * иконками, иконки выбираются плитками с картинками. Награда — отдельный блок:
 * вид (действие в кольце или ресурсы со счётом) и выбор (одно, одно из двух,
 * всё вместе) — и для основной, и для усиленной награды.
 *
 * <p>Рисует тем же расположением, что рисовальщик выпусков
 * ({@code tools/gen_cards_from_blanks.py}).
 */
public final class CardShop {

    private final CardAssets assets = new CardAssets();
    private final JFrame frame = new JFrame("Мастерская карт — Кристаллы Раздора");
    private final JPanel form = new WidthPanel();
    private final Preview preview = new Preview();
    private final JLabel status = new JLabel(" ");
    private JScrollPane formScroll;
    private final Timer redraw;
    private final List<JToggleButton> typeButtons = new ArrayList<>();
    private CardSpec card = CardSpec.blank(CardSpec.Type.OBJECTIVE);
    private File lastFile;
    private Library lib;
    private CatalogPanel catalog;
    private int index = -1;
    private final Timer autosave = new Timer(1200, e -> saveLibrary());
    private BufferedImage lastImage;

    public static void main(String[] args) {
        try {
            com.formdev.flatlaf.FlatDarkLaf.setup();
            javax.swing.UIManager.put("Component.arc", 10);
            javax.swing.UIManager.put("Button.arc", 10);
            javax.swing.UIManager.put("TextComponent.arc", 8);
            javax.swing.UIManager.put("Component.accentColor", Style.ACCENT);
            javax.swing.UIManager.put("Panel.background", Style.BG);
            // выбранное — заметно: заливка акцентом, белый текст
            javax.swing.UIManager.put("ToggleButton.selectedBackground", Style.ACCENT);
            javax.swing.UIManager.put("ToggleButton.selectedForeground", Color.WHITE);
            javax.swing.UIManager.put("ToggleButton.background", Style.PANEL);
        } catch (Throwable ignored) {
            // без темы — стандартный вид Swing
        }
        SwingUtilities.invokeLater(() -> new CardShop().show());
    }

    private CardShop() {
        redraw = new Timer(220, e -> render());
        redraw.setRepeats(false);
    }

    // ======================================================================
    //  ОКНО
    // ======================================================================

    private void show() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Style.PANEL);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Style.LINE));
        JPanel types = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 10));
        types.setOpaque(false);
        JLabel logo = new JLabel("МАСТЕРСКАЯ КАРТ");
        logo.setFont(Style.title(20));
        logo.setForeground(Style.INK);
        logo.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 18));
        types.add(logo);
        ButtonGroup tg = new ButtonGroup();
        for (CardSpec.Type t : CardSpec.Type.values()) {
            JToggleButton b = new JToggleButton(t.ru);
            b.putClientProperty("type", t);
            b.setFocusable(false);
            b.addActionListener(e -> openType(t));
            tg.add(b);
            typeButtons.add(b);
            types.add(b);
        }
        top.add(types, BorderLayout.CENTER);
        JPanel acts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 10));
        acts.setOpaque(false);
        acts.add(button("Импорт .kcard…", this::open, false));
        acts.add(button("Сохранить .kcard…", this::save, false));
        acts.add(button("Выпустить PNG", this::export, true));
        acts.add(button("Выпустить все", this::exportAll, false));
        acts.add(button("В игру…", this::toGame, false));
        acts.add(button("Рубашка", this::toggleBack, false));
        acts.add(button("Папка шаблонов", () -> openFolder(assets.templates), false));
        acts.add(button("Папка библиотеки", () -> openFolder(lib.folder()), false));
        top.add(acts, BorderLayout.EAST);

        form.setBackground(Style.BG);
        formScroll = new JScrollPane(form);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(20);
        formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setPreferredSize(new Dimension(600, 800));
        JPanel right = new JPanel(new BorderLayout());
        right.add(preview, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        status.setOpaque(true);
        status.setBackground(Style.PANEL);
        right.add(status, BorderLayout.SOUTH);
        JSplitPane edit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formScroll, right);
        edit.setBorder(null);
        edit.setDividerSize(6);
        lib = new Library(workFolder());
        lib.seedFrom(new File(workFolder(), "выпуск 27.09.2026"));
        catalog = new CatalogPanel(assets, new CatalogPanel.Actions() {
            @Override public void select(int i) {
                pick(i);
            }

            @Override public void addEnd() {
                insertAt(lib.list(card.type()).size(), CardSpec.blank(card.type()));
            }

            @Override public void insertAfter(int i) {
                insertAt(i < 0 ? lib.list(card.type()).size() : i + 1, CardSpec.blank(card.type()));
            }

            @Override public void duplicate(int i) {
                if (i < 0) {
                    return;
                }
                CardSpec src = lib.list(card.type()).get(i);
                CardSpec copy = new CardSpec(src.type());
                copy.fields.putAll(deepCopy(src.fields));
                insertAt(i + 1, copy);
            }

            @Override public void delete(int i) {
                deleteAt(i);
            }

            @Override public void move(int i, int d) {
                CardSpec.Type t = card.type();
                if (i < 0 || i + d < 0 || i + d >= lib.list(t).size()) {
                    return;
                }
                lib.move(t, i, i + d);
                index = i + d;
                saveLibrary();
                catalog.show(t, lib.list(t), index);
                setCard(lib.list(t).get(index));
            }
        });
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, catalog, edit);
        split.setBorder(null);
        split.setDividerSize(6);

        frame.setLayout(new BorderLayout());
        frame.add(top, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1760, 1000);
        frame.setLocationRelativeTo(null);
        openType(CardSpec.Type.OBJECTIVE);
        frame.setVisible(true);
        if (!assets.icons.isDirectory()) {
            JOptionPane.showMessageDialog(frame, "Не нашёл папку иконок:\n" + assets.icons
                + "\n\nМастерская берёт шаблоны и иконки с Яндекс.Диска (Forged in Kelium). "
                + "Другую папку можно задать переменной KELIUM_DISK.");
        }
    }

    private static JButton button(String text, Runnable r, boolean primary) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        if (primary) {
            b.setBackground(Style.ACCENT);
            b.setForeground(Color.WHITE);
            b.setFont(b.getFont().deriveFont(Font.BOLD));
        }
        b.addActionListener(e -> r.run());
        return b;
    }

    // ======================================================================
    //  БИБЛИОТЕКА: каталог карт каждого типа, автонумерация по месту
    // ======================================================================

    /** Перейти в каталог типа: первая карта, пустой каталог — с одной новой картой. */
    private void openType(CardSpec.Type t) {
        saveLibrary();
        java.util.List<CardSpec> l = lib.list(t);
        if (l.isEmpty()) {
            lib.insert(t, 0, CardSpec.blank(t));
        }
        index = 0;
        catalog.show(t, l, index);
        setCard(l.get(index));
    }

    private void pick(int i) {
        java.util.List<CardSpec> l = lib.list(card.type());
        if (i >= 0 && i < l.size() && l.get(i) != card) {
            index = i;
            setCard(l.get(i));
        }
    }

    private void insertAt(int at, CardSpec c) {
        CardSpec.Type t = c.type();
        lib.insert(t, at, c);
        index = Math.max(0, Math.min(at, lib.list(t).size() - 1));
        saveLibrary(t);
        catalog.show(t, lib.list(t), index);
        setCard(lib.list(t).get(index));
    }

    private void deleteAt(int i) {
        CardSpec.Type t = card.type();
        java.util.List<CardSpec> l = lib.list(t);
        if (i < 0 || i >= l.size()) {
            return;
        }
        String name = l.get(i).text("имя").isBlank() ? "№ " + (i + 1) : l.get(i).text("имя");
        if (JOptionPane.showConfirmDialog(frame, "Удалить карту «" + name + "»?", "Удалить",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        lib.remove(t, i);
        if (l.isEmpty()) {
            lib.insert(t, 0, CardSpec.blank(t));
        }
        index = Math.min(i, l.size() - 1);
        saveLibrary(t);
        catalog.show(t, l, index);
        setCard(l.get(index));
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> deepCopy(java.util.Map<String, Object> m) {
        Object o = new org.yaml.snakeyaml.Yaml().load(new org.yaml.snakeyaml.Yaml().dump(m));
        return (java.util.Map<String, Object>) o;
    }

    private void saveLibrary() {
        if (lib != null && card != null) {
            saveLibrary(card.type());
        }
    }

    private void saveLibrary(CardSpec.Type t) {
        autosave.stop();
        try {
            lib.save(t);
        } catch (Exception e) {
            status.setForeground(Style.BAD);
            status.setText("Каталог не сохранился: " + e.getMessage());
        }
    }

    /** Номер карты — её место в каталоге. */
    private JComponent numberInfo() {
        JLabel l = new JLabel("№ " + (index + 1) + " — ставится сам по месту в каталоге");
        l.setForeground(Style.INK2);
        return l;
    }

    /** Все карты каталога — картинками в папку «библиотека/<тип>». */
    private void exportAll() {
        CardSpec.Type t = card.type();
        File dir = new File(lib.folder(), t.ru);
        dir.mkdirs();
        int n = 0;
        java.util.List<String> bad = new ArrayList<>();
        java.util.List<CardSpec> l = lib.list(t);
        for (int i = 0; i < l.size(); i++) {
            CardSpec c = l.get(i);
            String nm = c.text("имя").replaceAll("[\\\\/:*?\"<>|\n]", " ").trim();
            File f = new File(dir, String.format("%02d", i + 1) + (nm.isEmpty() ? "" : " — " + nm) + ".png");
            try {
                ImageIO.write(CardRender.render(assets, c), "png", f);
                n++;
            } catch (Exception e) {
                bad.add("№ " + (i + 1) + ": " + e.getMessage());
            }
        }
        status.setForeground(bad.isEmpty() ? Style.GOOD : Style.BAD);
        status.setText("Выпущено " + n + " из " + l.size() + " → " + dir
            + (bad.isEmpty() ? "" : " · не нарисовались: " + String.join("; ", bad)));
    }

    private void fresh() {
        lastFile = null;
        setCard(CardSpec.blank(card.type()));
    }

    // ======================================================================
    //  ФОРМА
    // ======================================================================

    private void setCard(CardSpec c) {
        card = c;
        for (JToggleButton b : typeButtons) {
            b.setSelected(b.getClientProperty("type") == c.type());
        }
        form.removeAll();
        form.setLayout(new MigLayout("wrap 1, fillx, insets 14 16 24 16, gapy 2", "[grow,fill]", ""));
        switch (c.type().layout) {
            case OBJECTIVE -> objectiveForm();
            case ARSENAL -> arsenalForm();
            case MARKET -> marketForm();
            case CONTAINER -> containerForm();
            case HEX -> hexForm();
            case ORDER -> orderForm();
            case SUPER_OBJECTIVE -> superObjectiveForm();
        }
        form.revalidate();
        form.repaint();
        if (formScroll != null) {
            SwingUtilities.invokeLater(() -> formScroll.getVerticalScrollBar().setValue(0));
        }
        render();
    }

    private void objectiveForm() {
        section("Верх карты — эффект в чужой ход или сразу");
        form.add(field("Значок слева", segmented("слот", new String[] {"∞", "▶"},
            new String[] {"∞ постоянный", "▶ спец-действие"})));
        String kind = card.text("верх_вид");
        if ("иконка".equals(kind) || "2 max".equals(kind) || kind.isBlank()) {
            // прежние виды — теперь ряд иконок
            card.fields.put("иконки_верха", CardRender.topIcons(card));
            card.fields.put("верх_вид", "иконки");
            kind = "иконки";
        }
        form.add(field("Вид верха", segmentedOf(new String[] {"иконки", "текст", "реакция"},
            new String[] {"Иконки и текст", "Только текст", "Плашка реакции"}, kind, v -> {
                changed("верх_вид", v);
                setCard(card);
            })));
        if ("реакция".equals(kind)) {
            form.add(field("Заголовок верха", text("заголовок_верха", false, "для реакции: «Рикошет!»")));
        }
        if ("иконки".equals(kind)) {
            form.add(field("Иконки верха", text("иконки_верха", false,
                "по центру, на всю высоту: {36} / {34}   или   {66} 2 {54} max")));
        }
        form.add(field("Текст верха", text("верх", true, "свой перенос — Enter; без переноса — "
            + "ровными строками; занимает остаток зоны")));

        section("Название и условие");
        art();
        form.add(field("Название карты", text("имя", false, "")));
        form.add(field("Условие", text("условие", true,
            "перенос сам — ровными строками; свой перенос — Enter. С фигурой можно оставить "
                + "пустым — напечатается «Займи своими жетонами закрашенные секторы»")));
        section("Фигура — закрашенные секторы надо занять своими жетонами");
        form.add(figure("фигура"));

        section("Награда");
        form.add(new RewardEditor("награда", true));

        if (card.type() != CardSpec.Type.OBJECTIVE_START) {
            section("Дополнительно — усиленное условие и награда");
            form.add(field("Условие", text("дополнительно", true,
                "пусто — шаблон без полосы «дополнительно», награда опустится ниже")));
            form.add(new RewardEditor("доп_награда", false));
        }

        section("Для игры — что делает верх");
        form.add(field("Утиль", utilCombo()));
        form.add(note("В колоду игры задание выгружается с фигурой: фигура — его требование. "
            + "Награда берётся по иконкам награды."));

        section("Номер");
        form.add(field("Номер карты", numberInfo()));
    }

    private void arsenalForm() {
        section("Кнопка слева");
        form.add(field("Карта", segmentedBool("спец", "∞ постоянная", "▶ со спец-действием")));
        boolean sup = card.type() == CardSpec.Type.ARSENAL_SUPER;
        if (!sup) {
        section("Верх — разовый эффект");
        form.add(field("Текст верха", text("верх", true, "одна-две строки")));
        form.add(field("По центру", segmentedBool("верх_по_центру", "от иконок слева", "по центру")));
        form.add(field("Иконки слева", text("верх_слева", false, "перед текстом: -X {1} = {8}")));
        form.add(field("Иконки справа", text("верх_справа", false, "после текста: {44}")));
        }
        section("Название и постоянный эффект");
        art();
        form.add(field("Название", text("имя", false, "")));
        form.add(field("Эффект", text("низ", true, "**жирное** — жирным")));
        section("Фигура — свойство работает, пока на поле есть такая фигура");
        form.add(figure("фигура"));
        form.add(field("Ряд иконок", text("ряд", false,
            "под текстом по центру; {/} перечёркивает иконку перед ним")));
        form.add(field("Звезда", segmentedBool("звезда", "нет", "★ есть")));
        if (card.bool("спец")) {
            section("Спец-действие ▶");
            form.add(field("Цена", text("цена", false, "сколько стоит: 1")));
            form.add(field("Чем платят", iconField("цена_иконка")));
            form.add(field("Иконка", iconField("спец_иконка")));
            form.add(field("Знак у иконки", text("спец_знак", false, "=1")));
            form.add(field("Текст", text("спец_текст", true, "строками")));
            form.add(field("Контейнер", segmentedBool("контейнер", "нет", "место для контейнера")));
        }
        section("Для игры — свойство");
        form.add(field("Когда", whenEditor("свойство_когда")));
        form.add(field("Что даёт", effectEditor("свойство_эффект")));
        form.add(field("Раз за ход", segmentedOf(new String[] {"1", "2", "3"},
            new String[] {"не больше 1", "не больше 2", "не больше 3"},
            String.valueOf(card.integer("свойство_предел", 1)),
            v -> changed("свойство_предел", Integer.parseInt(v)))));
        JButton print = new JButton("Напечатать текст свойства из этих данных");
        print.setFocusable(false);
        print.addActionListener(e -> {
            card.fields.put("низ", kelium.cards.язык.Срабатывание.текст(GameEntry.bottom(card)));
            setCard(card);
        });
        form.add(field("", print));
        if (!sup) {
            section("Для игры — верх (утиль)");
            form.add(field("Что даёт", effectEditor("верх_эффект")));
        }

        section("Номер");
        form.add(field("Номер карты", numberInfo()));
    }

    /** Выбор из списка [код, подпись]; хранится код. */
    private javax.swing.JComboBox<String> combo(String[][] items, String current,
                                                Consumer<String> on) {
        javax.swing.JComboBox<String> cb = new javax.swing.JComboBox<>();
        int sel = 0;
        for (int i = 0; i < items.length; i++) {
            cb.addItem(items[i][1]);
            if (items[i][0].equals(current)) {
                sel = i;
            }
        }
        cb.setSelectedIndex(sel);
        cb.addActionListener(e -> on.accept(items[cb.getSelectedIndex()][0]));
        return cb;
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> sub(String key) {
        Object v = card.fields.get(key);
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (v instanceof java.util.Map<?, ?> mm) {
            m.putAll((java.util.Map<String, Object>) mm);
        }
        return m;
    }

    private void subPut(String key, String k, Object v) {
        java.util.Map<String, Object> m = sub(key);
        m.put(k, v);
        changed(key, m);
    }

    /** Событие свойства: что случилось, и для ветки/развилки — какая. */
    private JComponent whenEditor(String key) {
        java.util.Map<String, Object> m = sub(key);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        String ev = String.valueOf(m.getOrDefault("событие", "ход"));
        p.add(combo(GameEntry.EVENTS, ev, v -> {
            subPut(key, "событие", v);
            setCard(card);
        }));
        if ("ветка".equals(ev)) {
            p.add(combo(GameEntry.BRANCHES, String.valueOf(m.getOrDefault("ветка", "mining")),
                v -> subPut(key, "ветка", v)));
        } else if ("развилка".equals(ev)) {
            p.add(combo(GameEntry.FORKS, String.valueOf(m.getOrDefault("развилка", "extract")),
                v -> subPut(key, "развилка", v)));
        }
        if (card.fields.get(key) == null) {
            card.fields.put(key, new java.util.LinkedHashMap<>(java.util.Map.of("событие", ev)));
        }
        return p;
    }

    /** Эффект: что и сколько (для «сыграй ветку» — какую). */
    private JComponent effectEditor(String key) {
        java.util.Map<String, Object> m = sub(key);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        String what = String.valueOf(m.getOrDefault("что", "спец"));
        p.add(combo(GameEntry.EFFECTS, what, v -> {
            subPut(key, "что", v);
            setCard(card);
        }));
        boolean counted = java.util.Set.of("спец", "coin", "ammo", "kelium", "trophy").contains(what);
        if (counted) {
            JSpinner sp = new JSpinner(new SpinnerNumberModel(
                m.get("сколько") instanceof Number n ? n.intValue() : 1, 1, 6, 1));
            sp.setPreferredSize(new Dimension(64, 30));
            sp.addChangeListener(e -> subPut(key, "сколько", (Integer) sp.getValue()));
            p.add(sp);
        }
        if ("free_action".equals(what)) {
            p.add(combo(GameEntry.BRANCHES, String.valueOf(m.getOrDefault("ветка", "mining")),
                v -> subPut(key, "ветка", v)));
        }
        if (card.fields.get(key) == null) {
            card.fields.put(key, new java.util.LinkedHashMap<>(java.util.Map.of("что", what)));
        }
        return p;
    }

    /** Утиль задания для игры — из утилей языка карт. */
    private JComponent utilCombo() {
        kelium.cards.objectives.Утиль[] all = kelium.cards.objectives.Утиль.values();
        String[][] items = new String[all.length + 1][];
        items[0] = new String[] {"", "— не выбран —"};
        for (int i = 0; i < all.length; i++) {
            items[i + 1] = new String[] {all[i].name(), all[i].метка()};
        }
        return combo(items, card.text("утиль"), v -> changed("утиль", v));
    }

    /** Выгрузить все карты папки мастерской в колоды игры. */
    private void toGame() {
        try {
            saveLibrary();
            ВыгрузкаВИгру.Итог и = ВыгрузкаВИгру.выгрузить(lib.all(), workFolder());
            StringBuilder sb = new StringBuilder("Выгружено: заданий " + и.заданий() + ", арсенала "
                + и.арсенала() + "\n" + и.задания() + "\n" + и.арсенал());
            if (!и.замечания().isEmpty()) {
                sb.append("\n\nЗамечания:");
                for (String з : и.замечания().subList(0, Math.min(25, и.замечания().size()))) {
                    sb.append("\n• ").append(з);
                }
            }
            JTextArea a = new JTextArea(sb.toString(), 16, 70);
            a.setEditable(false);
            JOptionPane.showMessageDialog(frame, new JScrollPane(a), "В игру",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Не выгрузилось: " + e);
        }
    }

    /** Выбор рисунка шаблона — если у типа их несколько — и любого фона того же размера. */
    private void art() {
        int n = card.type().arts();
        if (n > 1) {
            form.add(field("Рисунок шаблона", segmentedNumbers("рисунок", n)));
        }
        form.add(field("Любой фон", backgroundCombo()));
    }

    /** Все фоны папки «фоны» размера этой карты; «по рисунку» — номер выше. */
    private JComponent backgroundCombo() {
        int[] wh = switch (card.type().layout) {
            case ARSENAL -> new int[] {803, 520};
            case MARKET -> new int[] {1028, 661};

            default -> new int[] {661, 1028};
        };
        List<String> all = assets.backgrounds(wh[0], wh[1]);
        String[][] items = new String[all.size() + 2][];
        items[0] = new String[] {"", "— по номеру рисунка —"};
        items[1] = new String[] {"белый", "белый, без рисунка"};
        for (int i = 0; i < all.size(); i++) {
            items[i + 2] = new String[] {all.get(i), all.get(i).substring("фоны/".length())};
        }
        return combo(items, card.text("фон"), v -> {
            if (v.isEmpty()) {
                card.fields.remove("фон");
                redraw.restart();
            } else {
                changed("фон", v);
            }
        });
    }

    private void superObjectiveForm() {
        section("Супер-задание — две ступени награды");
        art();
        form.add(field("За ★", text("условие", true, "под плашкой «Награда: по ★»; строками")));
        form.add(field("За ★★", text("условие_2", true, "под плашкой «Награда: по ★★»; строками")));
        section("Фигура — под второй ступенью (если нужна)");
        form.add(figure("фигура"));
        section("Номер");
        form.add(field("Номер карты", numberInfo()));
    }

    private void orderForm() {
        section("Приказ");
        form.add(field("Цвет", segmented("цвет", new String[] {"красный", "синий", "зеленый", "желтый"},
            new String[] {"красный", "синий", "зелёный", "жёлтый"})));
        form.add(field("Название", text("имя", false, "ОСВОИТЬ")));
        section("Верхний приказ — два действия");
        form.add(field("Слева", text("верх_слева", false, "одна иконка или две: {36}")));
        form.add(field("Подпись", text("подпись_слева", false, "добыча")));
        form.add(field("Справа", text("верх_справа", false, "{34}")));
        form.add(field("Подпись", text("подпись_справа", false, "питание")));
        form.add(field("Плашка", text("плашка", false, "спец-плашка: {26} : {1}{1}; пусто — без плашки")));
        section("Нижний приказ");
        form.add(field("Название", text("низ_имя", false, "НАСТУПАТЬ")));
        form.add(field("Слева", text("низ_слева", false, "{35}")));
        form.add(field("Подпись", text("низ_подпись_слева", false, "снабжение")));
        form.add(field("Справа", text("низ_справа", false, "{37}")));
        form.add(field("Подпись", text("низ_подпись_справа", false, "командование")));
    }

    private void marketForm() {
        section("Рынок — два предложения");
        art();
        form.add(field("Слева", text("слева", true, "строками: «Выполни» / «Манёвр»")));
        form.add(field("Иконка слева", text("иконка_слева", false, "{38}")));
        form.add(field("Справа", text("справа", true, "строками")));
        form.add(field("Иконка справа", text("иконка_справа", false, "{34}")));
        section("Номер");
        form.add(field("Номер карты", numberInfo()));
    }

    private void hexForm() {
        section("Жетон гекса зарождения");
        form.add(field("Сторона", segmented("сторона", new String[] {"зелёная", "рыжая"},
            new String[] {"зелёная рамка", "рыжая рамка"})));
        form.add(field("Рисунок", segmentedNumbers("рисунок", 2)));
        form.add(field("Start", segmentedBool("старт", "нет", "надпись Start")));
        form.add(field("Число", text("число", false, "крупно справа от гекса с кубами")));
        form.add(field("Ярлык", text("ярлык", false, "над плашкой: 0 {5} ?")));
        form.add(field("Ряд", text("ряд", false, "в плашке: + {9} → {52}  (→ рисуется стрелкой)")));
    }

    private void containerForm() {
        section("Контейнер");
        form.add(field("Буква", text("буква", false, "А, Б, В… — слева сверху")));
        form.add(field("Число", text("число", false, "справа сверху")));
        form.add(field("Иконка", text("иконка", false, "одна или несколько: {1}{1}")));
        form.add(field("Название", text("имя", true, "одна-две строки")));
    }

    /** Редактор фигуры; рядом с фигурой карта хранит и её узел для игры («язык»). */
    private JComponent figure(String key) {
        return new FigureEditor(Figure.of(card.fields.get(key)), f -> {
            if (f.isEmpty()) {
                card.fields.remove(key);
                card.fields.remove("язык_фигуры");
                redraw.restart();
            } else {
                card.fields.put("язык_фигуры", f.node(card.text("имя")));
                changed(key, f.toList());
            }
        });
    }

    private void section(String name) {
        form.add(Style.caption(name), "gaptop 8");
    }

    private static JComponent note(String text) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setForeground(Style.INK2);
        return a;
    }

    /** Строка формы: подпись слева, редактор справа. */
    private JComponent field(String label, JComponent editor) {
        JPanel p = new JPanel(new MigLayout("insets 4 0 4 0, fillx", "[130!]10[grow,fill,0::]", "[top]"));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setForeground(Style.INK2);
        l.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        p.add(l);
        p.add(editor, "wmin 0, growx");
        return p;
    }

    private void changed(String key, Object value) {
        card.fields.put(key, value);
        redraw.restart();
        autosave.restart();
        if (catalog != null) {
            catalog.refresh(card);
        }
    }

    /** Текстовое поле: ввод, кнопка «+ иконка» и живая строка с иконками под ним. */
    private JComponent text(String key, boolean multiline, String hint) {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, gapy 3, hidemode 3",
            "[grow,fill,0::]6[]", ""));
        p.setOpaque(false);
        JTextComponent t;
        JComponent view;
        if (multiline) {
            JTextArea a = new JTextArea(card.text(key), 2, 10);
            a.setLineWrap(true);
            a.setWrapStyleWord(true);
            JScrollPane sp = new JScrollPane(a);
            sp.setPreferredSize(new Dimension(100, 58));
            t = a;
            view = sp;
        } else {
            JTextField f = new JTextField(card.text(key), 8);
            t = f;
            view = f;
        }
        t.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        if (!hint.isEmpty() && t instanceof JTextField tf) {
            tf.putClientProperty("JTextField.placeholderText", hint);
        }
        TextPreview pv = new TextPreview(assets);
        pv.set(t.getText());
        t.getDocument().addDocumentListener(new DocumentListener() {
            void upd() {
                changed(key, t.getText());
                pv.set(t.getText());
            }
            @Override public void insertUpdate(DocumentEvent e) { upd(); }
            @Override public void removeUpdate(DocumentEvent e) { upd(); }
            @Override public void changedUpdate(DocumentEvent e) { upd(); }
        });
        JButton ic = new JButton("+ иконка");
        ic.setFocusable(false);
        ic.setToolTipText("вставить иконку туда, где стоит курсор");
        ic.addActionListener(e -> IconPicker.open(frame, assets, "Вставить иконку", k -> {
            int at = Math.max(0, Math.min(t.getCaretPosition(), t.getText().length()));
            try {
                t.getDocument().insertString(at, "{" + k + "}", null);
            } catch (Exception ex) {
                t.setText(t.getText() + "{" + k + "}");
            }
            t.requestFocusInWindow();
        }));
        p.add(view, "wmin 0, growx");
        p.add(ic, "top, wrap");
        p.add(pv, "span 2, growx, wmin 0");
        if (multiline && !hint.isEmpty()) {
            JTextArea h = new JTextArea(hint);
            h.setEditable(false);
            h.setFocusable(false);
            h.setOpaque(false);
            h.setLineWrap(true);
            h.setWrapStyleWord(true);
            h.setForeground(Style.INK3);
            h.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            p.add(h, "span 2, growx, wmin 0");
        }
        return p;
    }

    /** Выбор одной иконки: плитка с картинкой, щелчок — окно выбора. */
    private JComponent iconField(String key) {
        IconButton b = new IconButton(assets, CardAssets.tokens(card.text(key)).stream()
            .filter(s -> s.startsWith("{")).map(s -> s.substring(1, s.length() - 1))
            .findFirst().orElse(null));
        b.onClick(() -> IconPicker.open(frame, assets, "Выбрать иконку", k -> {
            b.setKey(k);
            changed(key, "{" + k + "}");
        }));
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(b);
        return p;
    }

    /** Переключатель из нескольких кнопок. */
    private JComponent segmented(String key, String[] values, String[] labels) {
        return segmentedOf(values, labels, card.text(key).isEmpty() ? values[0] : card.text(key),
            v -> changed(key, v));
    }

    private JComponent segmentedBool(String key, String off, String on) {
        return segmentedOf(new String[] {"false", "true"}, new String[] {off, on},
            String.valueOf(card.bool(key)), v -> {
                changed(key, Boolean.parseBoolean(v));
                if ("спец".equals(key)) {
                    setCard(card);          // у ▶ свой раздел полей
                }
            });
    }

    private JComponent segmentedNumbers(String key, int n) {
        String[] v = new String[n];
        for (int i = 0; i < n; i++) {
            v[i] = String.valueOf(i + 1);
        }
        return segmentedOf(v, v, String.valueOf(card.integer(key, 1)),
            s -> changed(key, Integer.parseInt(s)));
    }

    private static JComponent segmentedOf(String[] values, String[] labels, String current,
                                          Consumer<String> on) {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        p.setOpaque(false);
        ButtonGroup g = new ButtonGroup();
        for (int i = 0; i < values.length; i++) {
            String v = values[i];
            JToggleButton b = new JToggleButton(labels[i]);
            b.setFocusable(false);
            b.setSelected(v.equals(current));
            b.addActionListener(e -> on.accept(v));
            g.add(b);
            p.add(b);
        }
        return p;
    }

    // ======================================================================
    //  НАГРАДА
    // ======================================================================

    /** Блок награды: вид, выбор и позиции с иконками и счётом. */
    private final class RewardEditor extends JPanel {
        private static final long serialVersionUID = 1L;
        private final String key;
        private final boolean main;
        private final Reward r;
        private final JPanel rows = new JPanel(new MigLayout("insets 0, gapy 4", "[]", ""));

        RewardEditor(String key, boolean main) {
            super(new MigLayout("insets 10 12 12 12, fillx, gapy 6", "[130!]10[grow,fill]", ""));
            this.key = key;
            this.main = main;
            this.r = Reward.of(card.fields.get(key), main);
            setBackground(Style.PANEL);
            setBorder(BorderFactory.createLineBorder(Style.LINE));
            rows.setOpaque(false);
            rebuild();
        }

        private void rebuild() {
            removeAll();
            add(label("Вид награды"));
            add(segmentedOf(new String[] {Reward.ACTION, Reward.RESOURCES},
                new String[] {"Действие — в кольце", "Ресурсы — со счётом"}, r.kind, v -> {
                    r.kind = v;
                    if (Reward.ACTION.equals(v) && Reward.ALL.equals(r.choice)) {
                        r.choice = Reward.ONE;
                    }
                    trim();
                    commit();
                    rebuild();
                }), "wrap");
            add(label("Сколько вариантов"));
            String[] ch = Reward.ACTION.equals(r.kind)
                ? new String[] {Reward.ONE, Reward.EITHER}
                : new String[] {Reward.ONE, Reward.EITHER, Reward.ALL};
            String[] chl = Reward.ACTION.equals(r.kind)
                ? new String[] {"Одно действие", "Одно из двух"}
                : new String[] {"Одно", "Одно из двух", "Всё вместе"};
            add(segmentedOf(ch, chl, r.choice, v -> {
                r.choice = v;
                trim();
                commit();
                rebuild();
            }), "wrap");
            add(label(Reward.ACTION.equals(r.kind) ? "Действия" : "Что даёт"), "top");
            rows.removeAll();
            for (int i = 0; i < r.items.size(); i++) {
                rows.add(itemRow(i), "wrap");
            }
            int limit = limit();
            if (r.items.size() < limit) {
                JButton add = new JButton(r.items.isEmpty() ? "+ выбрать иконку" : "+ ещё");
                add.setFocusable(false);
                add.addActionListener(e -> IconPicker.open(frame, assets,
                    Reward.ACTION.equals(r.kind) ? "Какое действие" : "Какой ресурс", k -> {
                        r.items.add(new Reward.Item(k, 1));
                        commit();
                        rebuild();
                    }));
                rows.add(add, "wrap");
            }
            if (!main && r.items.isEmpty()) {
                JLabel h = new JLabel("пусто — без дополнительной награды");
                h.setForeground(Style.INK3);
                rows.add(h, "wrap");
            }
            add(rows, "wrap");
            revalidate();
            repaint();
        }

        private JComponent itemRow(int i) {
            Reward.Item it = r.items.get(i);
            JPanel p = new JPanel(new MigLayout("insets 0, gapx 8", "[][][]", "[center]"));
            p.setOpaque(false);
            IconButton b = new IconButton(assets, it.icon());
            b.onClick(() -> IconPicker.open(frame, assets, "Заменить иконку", k -> {
                r.items.set(i, new Reward.Item(k, r.items.get(i).count()));
                commit();
                rebuild();
            }));
            p.add(b);
            if (Reward.RESOURCES.equals(r.kind)) {
                JSpinner sp = new JSpinner(new SpinnerNumberModel(it.count(), 1, 12, 1));
                sp.setPreferredSize(new Dimension(70, 34));
                sp.addChangeListener(e -> {
                    r.items.set(i, new Reward.Item(r.items.get(i).icon(), (Integer) sp.getValue()));
                    commit();
                });
                JLabel x = new JLabel("×");
                x.setForeground(Style.INK2);
                p.add(x);
                p.add(sp);
            }
            JButton del = new JButton("убрать");
            del.setFocusable(false);
            del.addActionListener(e -> {
                r.items.remove(i);
                commit();
                rebuild();
            });
            p.add(del);
            if (i == 0 && Reward.EITHER.equals(r.choice) && r.items.size() == 2) {
                JLabel or = new JLabel("  или");
                or.setForeground(Style.ACCENT);
                p.add(or);
            }
            return p;
        }

        private int limit() {
            return switch (r.choice) {
                case Reward.ONE -> 1;
                case Reward.EITHER -> 2;
                default -> 4;
            };
        }

        private void trim() {
            while (r.items.size() > limit()) {
                r.items.remove(r.items.size() - 1);
            }
        }

        private void commit() {
            changed(key, r.toMap());
        }

        private JLabel label(String s) {
            JLabel l = new JLabel(s);
            l.setForeground(Style.INK2);
            return l;
        }
    }

    // ======================================================================
    //  КОМПОНЕНТЫ
    // ======================================================================

    /** Плитка-кнопка с картинкой иконки. */
    static final class IconButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final CardAssets a;

        IconButton(CardAssets a, String key) {
            this.a = a;
            setFocusable(false);
            setPreferredSize(new Dimension(64, 64));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setKey(key);
        }

        void setKey(String key) {
            BufferedImage ic = key == null ? null : a.icon(CardAssets.key(key));
            if (ic != null) {
                setIcon(new ImageIcon(CardAssets.fit(ic, 50, 50)));
                setText(null);
            } else {
                setIcon(null);
                setText(key == null ? "выбрать" : "?" + key);
            }
            setToolTipText(key == null ? "выбрать иконку" : "{" + key + "} — щёлкните, чтобы заменить");
        }

        void onClick(Runnable r) {
            addActionListener(e -> r.run());
        }
    }

    /** Живая строка: текст поля с настоящими иконками вместо {номеров}. */
    static final class TextPreview extends JComponent {
        private static final long serialVersionUID = 1L;
        private final CardAssets a;
        private String text = "";

        TextPreview(CardAssets a) {
            this.a = a;
        }

        void set(String t) {
            text = t == null ? "" : t;
            setVisible(text.contains("{") || text.contains("**"));
            revalidate();
            repaint();
        }

        private List<String> lines() {
            List<String> out = new ArrayList<>();
            for (String l : text.split("\n")) {
                out.add(l);
            }
            return out;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, 8 + lines().size() * 28);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(new Color(0x14171d));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            Font f = a.font("TekturNarrow-Medium.ttf", 17);
            Font fb = a.font("TekturNarrow-Bold.ttf", 17);
            int y = 4;
            for (String line : lines()) {
                int x = 8;
                int base = y + 20;
                for (CardCanvas.Part p : CardCanvas.parts(line)) {
                    if (p.kind() == 'i') {
                        BufferedImage ic = a.icon(CardAssets.key(p.value()));
                        if (ic != null) {
                            BufferedImage s = CardAssets.fit(ic, 24, 24);
                            g.drawImage(s, x, y + 2, null);
                            x += s.getWidth() + 5;
                        } else {
                            g.setColor(Style.BAD);
                            g.setFont(f);
                            String m = "?" + p.value();
                            g.drawString(m, x, base);
                            x += g.getFontMetrics().stringWidth(m) + 5;
                        }
                    } else {
                        g.setFont(p.kind() == 'b' ? fb : f);
                        g.setColor(p.kind() == 'b' ? Color.WHITE : Style.INK);
                        g.drawString(p.value(), x, base);
                        x += g.getFontMetrics().stringWidth(p.value() + " ");
                    }
                }
                y += 28;
            }
            g.dispose();
        }
    }

    /** Панель формы по ширине окна прокрутки: поля не уезжают вправо. */
    private static final class WidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 20; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 200; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ======================================================================
    //  РИСОВАНИЕ
    // ======================================================================

    private boolean showBack;

    /** Показать оборот карты (рубашку типа) или снова лицо. */
    private void toggleBack() {
        showBack = !showBack;
        if (showBack) {
            BufferedImage b = assets.back(card.type());
            if (b == null) {
                showBack = false;
                status.setForeground(Style.BAD);
                status.setText("Рубашки для типа «" + card.type().ru + "» нет в «экспорт-рубашки»"
                    + (card.type() == CardSpec.Type.SPAWN_HEX ? " — у жетона гекса вторая сторона: "
                    + "переключите «Сторона»" : ""));
                return;
            }
            preview.set(b, false);
            status.setForeground(Style.GOOD);
            status.setText("Рубашка · " + card.type().ru + " · ещё раз «Рубашка» — лицо");
        } else {
            render();
        }
    }

    private void render() {
        showBack = false;
        CardSpec snapshot = new CardSpec(card.type());
        snapshot.fields.putAll(card.fields);
        new SwingWorker<BufferedImage, Void>() {
            private String problem;

            @Override
            protected BufferedImage doInBackground() {
                try {
                    return CardRender.render(assets, snapshot);
                } catch (CardRender.Problem p) {
                    problem = p.getMessage();
                } catch (RuntimeException e) {
                    problem = "Не нарисовалось: " + e;
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    BufferedImage im = get();
                    if (im != null) {
                        lastImage = im;
                        preview.set(im, false);
                        status.setForeground(Style.GOOD);
                        status.setText("Готово · " + im.getWidth() + "×" + im.getHeight()
                            + " · шаблон «" + templateName(snapshot) + "»");
                    } else {
                        preview.set(lastImage, true);
                        status.setForeground(Style.BAD);
                        status.setText(problem);
                    }
                } catch (Exception e) {
                    status.setText(e.toString());
                }
            }
        }.execute();
    }

    private static String templateName(CardSpec c) {
        CardSpec.Type t = c.type();
        if (t.wholeFile() != null) {
            return t.wholeFile();
        }
        if (!c.text("фон").isBlank()) {
            return c.text("фон") + " + подложка типа";
        }
        if (t.layout == CardSpec.Layout.ORDER) {
            return "карты-приказов-" + c.text("цвет") + ".png";
        }
        if (t.layout == CardSpec.Layout.HEX) {
            return t.hexFile("рыжая".equals(c.text("сторона")), c.integer("рисунок", 1));
        }
        boolean alt = t.layout == CardSpec.Layout.OBJECTIVE ? !c.text("дополнительно").isBlank()
            : c.bool("спец");
        return t.bgFile(c.integer("рисунок", 1)) + " + " + t.overFile(alt);
    }

    /** Предпросмотр: карта вписана в панель; если не нарисовалась — прежняя, приглушённо. */
    private static final class Preview extends JComponent {
        private static final long serialVersionUID = 1L;
        private BufferedImage im;
        private boolean stale;

        void set(BufferedImage im, boolean stale) {
            this.im = im;
            this.stale = stale;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(new Color(0x15181e));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (im == null) {
                g.dispose();
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double k = Math.min((getWidth() - 48.0) / im.getWidth(),
                (getHeight() - 48.0) / im.getHeight());
            k = Math.min(k, 1.4);
            int w = (int) (im.getWidth() * k);
            int h = (int) (im.getHeight() * k);
            int x = (getWidth() - w) / 2;
            int y = (getHeight() - h) / 2;
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(x + 6, y + 10, w, h, 24, 24);
            if (stale) {
                g.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.35f));
            }
            g.drawImage(im, x, y, w, h, null);
            g.dispose();
        }
    }

    // ======================================================================
    //  ФАЙЛЫ
    // ======================================================================

    private File workFolder() {
        File f = new File(assets.common, "мастерская карт");
        f.mkdirs();
        return f;
    }

    private void open() {
        JFileChooser ch = new JFileChooser(lastFile != null ? lastFile.getParentFile() : workFolder());
        ch.setFileFilter(new FileNameExtensionFilter("Карта мастерской (.kcard)", "kcard"));
        if (ch.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            lastFile = ch.getSelectedFile();
            CardSpec c = CardSpec.load(lastFile);
            if (c.type() != card.type()) {
                openType(c.type());
            }
            insertAt(index + 1, c);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Не открылось: " + e.getMessage());
        }
    }

    private String baseName() {
        String name = card.text("имя").replaceAll("[\\\\/:*?\"<>|]", "").trim();
        return card.type().ru + " " + card.text("номер") + " — " + (name.isEmpty() ? "без имени" : name);
    }

    private void save() {
        JFileChooser ch = new JFileChooser(lastFile != null ? lastFile.getParentFile() : workFolder());
        ch.setSelectedFile(lastFile != null ? lastFile : new File(workFolder(), baseName() + ".kcard"));
        if (ch.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = ch.getSelectedFile();
        if (!f.getName().endsWith(".kcard")) {
            f = new File(f.getParentFile(), f.getName() + ".kcard");
        }
        try {
            card.save(f);
            lastFile = f;
            status.setText("Сохранено: " + f);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Не сохранилось: " + e.getMessage());
        }
    }

    /** Выпуск: картинка PNG и рядом её описание .kcard — чтобы потом поправить. */
    private void export() {
        if (lastImage == null) {
            JOptionPane.showMessageDialog(frame, "Карта не нарисовалась — см. строку внизу");
            return;
        }
        File dir = lastFile != null ? lastFile.getParentFile() : workFolder();
        JFileChooser ch = new JFileChooser(dir);
        ch.setSelectedFile(new File(dir, baseName() + ".png"));
        if (ch.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = ch.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".png")) {
            f = new File(f.getParentFile(), f.getName() + ".png");
        }
        try {
            BufferedImage out = CardRender.render(assets, card);
            ImageIO.write(out, "png", f);
            File spec = new File(f.getParentFile(), f.getName().replaceAll("\\.png$", ".kcard"));
            card.save(spec);
            lastFile = spec;
            status.setForeground(Style.GOOD);
            status.setText("Выпущено: " + f.getName() + " (и описание .kcard рядом)");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Не выпустилось: " + e.getMessage());
        }
    }

    private void openFolder(File f) {
        try {
            Desktop.getDesktop().open(f);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, f.toString());
        }
    }

    /** Нарисовать карту из файла описания в PNG без окна — для выпусков и проверок. */
    public static void renderFile(File spec, File png) throws Exception {
        BufferedImage im = CardRender.render(new CardAssets(), CardSpec.load(spec));
        ImageIO.write(im, "png", png);
    }
}
