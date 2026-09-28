package kelium.gui.cardshop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

import net.miginfocom.swing.MigLayout;

/**
 * МАСТЕРСКАЯ КАРТ (заказ дизайнера 28.09.2026): выбираешь тип карты, пишешь
 * поля — заголовок, верх, условие, награду, номер — и сразу видишь карту,
 * нарисованную на пустом шаблоне. Иконки вставляются в текст, как эмодзи:
 * палитра «Иконки» кладёт в поле {@code {номер}}. Готовая карта выпускается
 * картинкой PNG, а её описание сохраняется рядом файлом {@code .kcard}.
 *
 * <p>Рисует тем же расположением элементов, что и рисовальщик выпусков
 * ({@code tools/gen_cards_from_blanks.py}).
 */
public final class CardShop {

    private final CardAssets assets = new CardAssets();
    private final JFrame frame = new JFrame("Мастерская карт — Кристаллы Раздора");
    private final JComboBox<CardSpec.Type> typeBox = new JComboBox<>(CardSpec.Type.values());
    private final JPanel form = new WidthPanel();
    private final Preview preview = new Preview();
    private final JLabel status = new JLabel(" ");
    private final Timer redraw;
    private final Map<String, JComponent> inputs = new LinkedHashMap<>();
    private CardSpec card = CardSpec.blank(CardSpec.Type.OBJECTIVE);
    private JTextComponent lastText;
    private File lastFile;
    private BufferedImage lastImage;
    private JDialog palette;

    public static void main(String[] args) {
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
        } catch (Throwable ignored) {
            // без темы — стандартный вид Swing
        }
        SwingUtilities.invokeLater(() -> new CardShop().show());
    }

    private CardShop() {
        redraw = new Timer(250, e -> render());
        redraw.setRepeats(false);
    }

    private void show() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.add(new JLabel("Тип карты:"));
        typeBox.addActionListener(e -> {
            CardSpec.Type t = (CardSpec.Type) typeBox.getSelectedItem();
            if (t != null && t != card.type()) {
                setCard(CardSpec.blank(t));
            }
        });
        top.add(typeBox);
        top.add(button("Новая", () -> setCard(CardSpec.blank(card.type()))));
        top.add(button("Открыть…", this::open));
        top.add(button("Сохранить…", this::save));
        top.add(button("Выпустить PNG…", this::export));
        top.add(button("Иконки…", this::showPalette));
        top.add(button("Папка шаблонов", () -> openFolder(assets.templates)));

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        formScroll.setPreferredSize(new Dimension(560, 800));
        formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel right = new JPanel(new BorderLayout());
        right.add(preview, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        right.add(status, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formScroll, right);
        split.setResizeWeight(0);

        frame.setLayout(new BorderLayout());
        frame.add(top, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1400, 950);
        frame.setLocationRelativeTo(null);
        setCard(card);
        frame.setVisible(true);
        if (!assets.icons.isDirectory()) {
            JOptionPane.showMessageDialog(frame, "Не нашёл папку иконок:\n" + assets.icons
                + "\n\nМастерская берёт шаблоны и иконки с Яндекс.Диска (Forged in Kelium). "
                + "Другую папку можно задать переменной KELIUM_DISK.");
        }
    }

    /** Панель формы по ширине окна прокрутки: поля не уезжают вправо, подсказки переносятся. */
    private static final class WidthPanel extends JPanel implements javax.swing.Scrollable {
        private static final long serialVersionUID = 1L;

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 200; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static JButton button(String text, Runnable r) {
        JButton b = new JButton(text);
        b.addActionListener(e -> r.run());
        return b;
    }

    // ==================== форма ====================

    /** Поле формы: ключ описания, подпись, вид ввода, подсказка. */
    private record Field(String key, String label, String kind, String hint, String[] options) {
        Field(String key, String label, String kind, String hint) {
            this(key, label, kind, hint, null);
        }
    }

    private static final String ICONS = "иконки в фигурных скобках: {25} — по номеру, "
        + "{военное здание} — по имени файла; палитра «Иконки…» вставит сама";

    private List<Field> fieldsOf(CardSpec.Type t) {
        List<Field> f = new ArrayList<>();
        if (t.layout == CardSpec.Layout.OBJECTIVE) {
            f.add(new Field("рисунок", "Рисунок", "int",
                "номер рисунка шаблона: 1 = файлы 2 и 3, 2 = 4 и 5 … 11 = 22 и 23"));
            f.add(new Field("слот", "Значок слева вверху", "choice", "∞ — постоянно, ▶ — спец-действие",
                new String[] {"∞", "▶"}));
            f.add(new Field("верх_вид", "Верх карты", "choice",
                "реакция — плашка боевого эффекта с заголовком; иконка — крупная иконка сверху; "
                    + "2 max — «2 войска max»", new String[] {"реакция", "иконка", "2 max"}));
            f.add(new Field("заголовок_верха", "Заголовок верха", "text", "для реакции: «Рикошет!»"));
            f.add(new Field("иконка_верха", "Иконка верха", "text", "для вида «иконка»: {52}"));
            f.add(new Field("верх", "Эффект верха", "area", "строки — как на карте; " + ICONS));
            f.add(new Field("имя", "Название карты", "text", "своё имя карты"));
            f.add(new Field("условие", "Условие", "area",
                "переносится само в 4 строки; свой перенос — Enter; " + ICONS));
            f.add(new Field("награда", "Награда", "text",
                "1 или 2 иконки в кольцах через косую черту; 3 и больше — рядом без колец"));
            f.add(new Field("дополнительно", "Дополнительно", "area",
                "пусто — шаблон без полосы «дополнительно», награда ниже"));
            f.add(new Field("доп_награда", "Доп. награда", "text",
                "иконки в ряд: {1}{1}{1} {25}; монеты подряд — внахлёст"));
            f.add(new Field("номер", "Номер карты", "text", "в правом нижнем углу"));
        } else if (t.layout == CardSpec.Layout.ARSENAL) {
            f.add(new Field("спец", "Спец-действие ▶", "bool",
                "шаблон с кнопкой ▶ (арсенал-2); без — постоянная карта ∞ (арсенал-1)"));
            f.add(new Field("верх", "Верх: текст", "area", "одна-две строки; " + ICONS));
            f.add(new Field("верх_по_центру", "Верх по центру", "bool", "текст верха по центру полосы"));
            f.add(new Field("верх_слева", "Верх: связка слева", "text",
                "иконки и знаки перед текстом: -X {1} = {8} {75}"));
            f.add(new Field("верх_справа", "Верх: связка справа", "text",
                "иконки и знаки справа: {39} или {69} =1 {1}"));
            f.add(new Field("имя", "Название карты", "text", ""));
            f.add(new Field("низ", "Постоянный эффект", "area",
                "по правому краю; **жирное**; " + ICONS));
            f.add(new Field("ряд", "Ряд иконок", "text",
                "под текстом по центру: {66} {61} + {53}; {/} перечёркивает иконку перед ним"));
            f.add(new Field("звезда", "Звезда ★", "bool", "значок звезды слева внизу"));
            f.add(new Field("цена", "Цена под ▶", "text", "сколько стоит спец-действие: 1"));
            f.add(new Field("цена_иконка", "Чем платят", "text", "{1} — монеты, {3} — боеприпасы…"));
            f.add(new Field("спец_иконка", "Иконка спец-действия", "text", "{32}"));
            f.add(new Field("спец_знак", "Знак у иконки", "text", "=1"));
            f.add(new Field("спец_текст", "Текст спец-действия", "area", "строками"));
            f.add(new Field("контейнер", "Место для контейнера", "bool", "ячейка с ящиком справа"));
            f.add(new Field("номер", "Номер карты", "text", ""));
        }
        return f;
    }

    private void setCard(CardSpec c) {
        card = c;
        if (typeBox.getSelectedItem() != c.type()) {
            typeBox.setSelectedItem(c.type());
        }
        form.removeAll();
        inputs.clear();
        form.setLayout(new MigLayout("wrap 2, fillx, insets 12", "[right]10[grow,fill,100::]", ""));
        List<Field> fs = fieldsOf(c.type());
        if (fs.isEmpty()) {
            form.add(new JLabel("<html>Раскладку этого типа ещё не сделали.<br>Пустой шаблон кладите "
                + "в «шаблоны карт» — «" + c.type().pattern + "».</html>"), "span 2");
        }
        for (Field f : fs) {
            JLabel l = new JLabel(f.label());
            l.setFont(l.getFont().deriveFont(Font.BOLD));
            form.add(l, f.kind().equals("area") ? "top" : "");
            JComponent in = input(f);
            inputs.put(f.key(), in);
            form.add(in);
            if (!f.hint().isEmpty()) {
                JTextArea h = new JTextArea(f.hint());
                h.setEditable(false);
                h.setFocusable(false);
                h.setOpaque(false);
                h.setLineWrap(true);
                h.setWrapStyleWord(true);
                h.setForeground(new Color(0x77, 0x77, 0x77));
                h.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                form.add(h, "skip 1, wmin 10, growx");
            }
        }
        form.revalidate();
        form.repaint();
        render();
    }

    private JComponent input(Field f) {
        String v = card.text(f.key());
        switch (f.kind()) {
            case "bool" -> {
                JCheckBox b = new JCheckBox();
                b.setSelected(card.bool(f.key()));
                b.addActionListener(e -> changed(f.key(), b.isSelected()));
                return b;
            }
            case "choice" -> {
                JComboBox<String> box = new JComboBox<>(f.options());
                box.setSelectedItem(v.isEmpty() ? f.options()[0] : v);
                box.addActionListener(e -> changed(f.key(), box.getSelectedItem()));
                return box;
            }
            case "area" -> {
                JTextArea a = new JTextArea(v, 3, 10);
                a.setLineWrap(true);
                a.setWrapStyleWord(true);
                a.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                hook(a, f.key());
                JScrollPane sp = new JScrollPane(a);
                sp.setPreferredSize(new Dimension(120, 74));
                return sp;
            }
            default -> {
                JTextField t = new JTextField(v, 8);
                t.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                hook(t, f.key());
                return t;
            }
        }
    }

    private void hook(JTextComponent t, String key) {
        t.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed(key, t.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { changed(key, t.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { changed(key, t.getText()); }
        });
        t.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { lastText = t; }
        });
    }

    private void changed(String key, Object value) {
        card.fields.put(key, value);
        redraw.restart();
    }

    // ==================== рисование ====================

    private void render() {
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
                        preview.set(im);
                        status.setForeground(new Color(40, 110, 50));
                        status.setText("Готово: " + im.getWidth() + "×" + im.getHeight()
                            + " — шаблон «" + templateName(snapshot) + "»");
                    } else {
                        status.setForeground(new Color(170, 30, 30));
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
        if (t.layout == CardSpec.Layout.OBJECTIVE) {
            return t.templateFile(c.integer("рисунок", 1), !c.text("дополнительно").isBlank());
        }
        return t.templateFile(c.bool("спец") ? 2 : 1, false);
    }

    /** Предпросмотр: карта вписана в панель, без искажений. */
    private static final class Preview extends JComponent {
        private static final long serialVersionUID = 1L;
        private BufferedImage im;

        void set(BufferedImage im) {
            this.im = im;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setColor(new Color(0x2b2f36));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (im == null) {
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double k = Math.min((getWidth() - 40.0) / im.getWidth(),
                (getHeight() - 40.0) / im.getHeight());
            k = Math.min(k, 1.5);
            int w = (int) (im.getWidth() * k);
            int h = (int) (im.getHeight() * k);
            g.drawImage(im, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        }
    }

    // ==================== палитра иконок ====================

    private void showPalette() {
        if (palette == null) {
            palette = new JDialog(frame, "Иконки — щёлкните, чтобы вставить в поле", false);
            JTextField filter = new JTextField();
            JPanel grid = new JPanel(new GridLayout(0, 8, 4, 4));
            Map<String, File> all = assets.iconFiles();
            List<JButton> buttons = new ArrayList<>();
            for (var e : all.entrySet()) {
                String key = e.getKey();
                BufferedImage ic = assets.icon(key);
                JButton b = new JButton(key.length() > 14 ? key.substring(0, 13) + "…" : key);
                if (ic != null) {
                    b.setIcon(new ImageIcon(CardAssets.fit(ic, 56, 56)));
                }
                b.setToolTipText("{" + key + "}");
                b.setVerticalTextPosition(JLabel.BOTTOM);
                b.setHorizontalTextPosition(JLabel.CENTER);
                b.setFont(b.getFont().deriveFont(10f));
                b.addActionListener(ev -> insert("{" + key + "}"));
                b.putClientProperty("key", key);
                buttons.add(b);
                grid.add(b);
            }
            filter.getDocument().addDocumentListener(new DocumentListener() {
                void apply() {
                    String q = filter.getText().trim().toLowerCase();
                    for (JButton b : buttons) {
                        b.setVisible(q.isEmpty()
                            || String.valueOf(b.getClientProperty("key")).toLowerCase().contains(q));
                    }
                    grid.revalidate();
                }
                @Override public void insertUpdate(DocumentEvent e) { apply(); }
                @Override public void removeUpdate(DocumentEvent e) { apply(); }
                @Override public void changedUpdate(DocumentEvent e) { apply(); }
            });
            JPanel p = new JPanel(new BorderLayout(6, 6));
            JPanel head = new JPanel(new BorderLayout(6, 6));
            head.add(new JLabel("Найти:"), BorderLayout.WEST);
            head.add(filter, BorderLayout.CENTER);
            head.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
            p.add(head, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(grid);
            sp.getVerticalScrollBar().setUnitIncrement(24);
            p.add(sp, BorderLayout.CENTER);
            palette.setContentPane(p);
            palette.setSize(820, 640);
            palette.setLocationRelativeTo(frame);
        }
        palette.setVisible(true);
    }

    /** Вставить фишку в поле, где стоял курсор. */
    private void insert(String token) {
        JTextComponent t = lastText;
        if (t == null) {
            status.setText("Сначала щёлкните в поле, куда вставить иконку");
            return;
        }
        int at = Math.max(0, Math.min(t.getCaretPosition(), t.getText().length()));
        try {
            t.getDocument().insertString(at, token, null);
            t.setCaretPosition(at + token.length());
        } catch (Exception ignored) {
            t.setText(t.getText() + token);
        }
        t.requestFocusInWindow();
    }

    // ==================== файлы ====================

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
            setCard(CardSpec.load(lastFile));
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
        JFileChooser ch = new JFileChooser(lastFile != null ? lastFile.getParentFile() : workFolder());
        ch.setSelectedFile(new File(lastFile != null ? lastFile.getParentFile() : workFolder(),
            baseName() + ".png"));
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
