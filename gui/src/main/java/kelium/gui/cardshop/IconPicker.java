package kelium.gui.cardshop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * ВЫБОР ИКОНКИ — плитками с самими картинками: сверху частые (действия и
 * ресурсы), ниже все иконки папки «экспорт-иконки» с поиском по номеру и имени.
 * Щелчок по плитке отдаёт ключ иконки и закрывает окно.
 */
final class IconPicker {

    private IconPicker() {
    }

    /** Частые иконки: [ключ, подпись] — номера выгрузки 03.10.2026. */
    static final String[][] ACTIONS = {
        {"36", "Добыча"}, {"34", "Питание"}, {"35", "Снабжение"}, {"37", "Командование"},
        {"40", "Развитие"}, {"38", "Манёвр"}, {"39", "Бой"}, {"41", "Рынок"}, {"42", "Наука"},
        {"32", "знак стройки"}, {"68", "произвести боеприпасы"}, {"69", "нанять"},
    };
    static final String[][] RESOURCES = {
        {"1", "монета"}, {"3", "боеприпас"}, {"5", "келемий"}, {"9", "трофей"},
        {"15", "арсенал"}, {"26", "спец-действие"}, {"13", "контейнер"}, {"48", "позолота"},
        {"45", "модуль боя"}, {"46", "модуль сборки"}, {"22", "очко"}, {"23", "прочность"},
    };
    /** Подписи всех иконок экспорта — для плиток и поиска словом. */
    static final java.util.Map<String, String> NAMES = new java.util.HashMap<>();
    static {
        String[] n = {
            "1", "монета",
            "2", "сундучок боеприпасов",
            "3", "боеприпас",
            "4", "кристалл келемия",
            "5", "келемий",
            "6", "батарея",
            "7", "энергия",
            "8", "шестерня трофея",
            "9", "трофей",
            "10", "трофей или келемий",
            "11", "любой куб",
            "12", "бесконечный запас",
            "13", "контейнер (мелко)",
            "14", "контейнер крупно",
            "15", "арсенал",
            "16", "супер-арсенал",
            "17", "задание",
            "18", "супер-задание",
            "19", "сброс задания",
            "20", "сброс контейнера",
            "21", "сжечь арсенал",
            "22", "победное очко",
            "23", "прочность",
            "24", "урон",
            "25", "кольцо действия",
            "26", "спец-действие",
            "27", "сразу",
            "28", "постоянно ∞",
            "29", "ячейка энергии",
            "30", "военное здание",
            "31", "круг энергии",
            "32", "знак стройки",
            "33", "стройка (кран)",
            "34", "Питание / Переложить энергию",
            "35", "Снабжение / Выпустить",
            "36", "Добыча / Добыть",
            "37", "Командование",
            "38", "Манёвр",
            "39", "Бой",
            "40", "Развитие",
            "41", "Рынок",
            "42", "Наука",
            "43", "трек Науки",
            "44", "чужой приказ",
            "45", "модуль боя",
            "46", "модуль сборки",
            "47", "модуль хранилища",
            "48", "позолота",
            "49", "первый игрок",
            "50", "Наука → очко",
            "51", "гекс с кубами",
            "52", "поворот гекса",
            "53", "раскол гекса",
            "54", "жетон войск",
            "55", "уничтожение / свалка",
            "56", "флаг конца",
            "57", "переставить модуль",
            "58", "гекс",
            "59", "ячейка хранилища",
            "60", "монета крупно",
            "61", "монета 5",
            "62", "пехота",
            "63", "техника",
            "64", "авиация",
            "65", "вышка",
            "66", "переброска",
            "67", "шаг",
            "68", "произвести боеприпасы",
            "69", "нанять",
            "70", "удар",
            "71", "пехота линией",
            "72", "техника линией",
            "73", "авиация линией",
            "74", "вышка линией",
            "75", "здания",
            "76", "цветные здания",
            "77", "игрок",
            "78", "реакция",
            "79", "контейнер в чужой ход",
            "80", "чужой ход",
            "81", "стрелка",
            "82", "зоны стройки",
            "83", "стройка на своём гексе",
            "84", "стройка за стенкой",
            "85", "закрытый контейнер",
            "86", "забрать жетон 1-го и монету",
            "87", "келемий + добыча",
            "88", "2 разных войска",
            "89", "3 спец-действия",
            "90", "здание за 1 монету",
            "91", "забрать арсенал",
            "92", "приказ",
            "93", "удалить арсенал врага",
            "94", "забрать задание",
            "95", "карта рынка",
            "96", "предложение за 1",
            "97", "+ гекс",
            "98", "ремонт",
            "99", "гекс перечёркнут",
            "100", "прочность перечёркнута",
            "101", "добытчик",
            "102", "энергостанция",
        };
        for (int i = 0; i < n.length; i += 2) {
            NAMES.put(n[i], n[i + 1]);
        }
    }

    static void open(Window owner, CardAssets a, String title, Consumer<String> onPick) {
        JDialog d = new JDialog(owner, title, java.awt.Dialog.ModalityType.MODELESS);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(Style.BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JTextField search = new JTextField();
        search.putClientProperty("JTextField.placeholderText", "найти: номер или имя иконки");
        root.add(search, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Style.BG);
        List<Tile> all = new ArrayList<>();
        Consumer<String> pick = key -> {
            onPick.accept(key);
            d.dispose();
        };
        body.add(Style.caption("Действия"));
        body.add(row(a, ACTIONS, all, pick));
        body.add(Style.caption("Ресурсы и награды"));
        body.add(row(a, RESOURCES, all, pick));
        body.add(Style.caption("Все иконки"));
        List<String[]> every = new ArrayList<>();
        for (String k : a.iconFiles().keySet()) {
            every.add(new String[] {k, k.matches("\\d+") ? k + " · " + NAMES.getOrDefault(k, "") : k});
        }
        JPanel grid = row(a, every.toArray(new String[0][]), all, pick);
        body.add(grid);
        JScrollPane sp = new JScrollPane(body);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        sp.getViewport().setBackground(Style.BG);
        root.add(sp, BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentListener() {
            void apply() {
                String q = search.getText().trim().toLowerCase();
                for (Tile t : all) {
                    t.setVisible(q.isEmpty() || t.key.toLowerCase().contains(q)
                        || t.name.toLowerCase().contains(q));
                }
                body.revalidate();
                body.repaint();
            }
            @Override public void insertUpdate(DocumentEvent e) { apply(); }
            @Override public void removeUpdate(DocumentEvent e) { apply(); }
            @Override public void changedUpdate(DocumentEvent e) { apply(); }
        });
        d.setContentPane(root);
        d.setSize(900, 700);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        search.requestFocusInWindow();
    }

    private static JPanel row(CardAssets a, String[][] items, List<Tile> all, Consumer<String> pick) {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 6));
        p.setBackground(Style.BG);
        p.setAlignmentX(0f);
        for (String[] it : items) {
            Tile t = new Tile(a, it[0], it[1], pick);
            all.add(t);
            p.add(t);
        }
        return p;
    }

    /** Миниатюры плиток — один раз на всё время работы мастерской. */
    static final java.util.Map<String, BufferedImage> TILE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    static final BufferedImage NONE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    /** Плитка: картинка иконки и подпись; наведение подсвечивает. */
    static final class Tile extends JComponent {
        private static final long serialVersionUID = 1L;
        final String key;
        final String name;
        private final BufferedImage img;
        private boolean hover;

        Tile(CardAssets a, String key, String name, Consumer<String> pick) {
            this.key = key;
            this.name = name;
            img = TILE_CACHE.computeIfAbsent(key, kk -> {
                BufferedImage ic = a.icon(kk);
                return ic == null ? NONE : CardAssets.fit(ic, 64, 64);
            }) == NONE ? null : TILE_CACHE.get(key);
            setPreferredSize(new Dimension(96, 100));
            setToolTipText("{" + key + "} — " + name);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { pick.accept(key); }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(hover ? Style.PANEL_HOVER : Style.PANEL);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            if (hover) {
                g.setColor(Style.ACCENT);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            }
            if (img != null) {
                g.drawImage(img, (getWidth() - img.getWidth()) / 2, 6 + (64 - img.getHeight()) / 2,
                    null);
            } else {
                g.setColor(Color.PINK);
                g.drawString("нет файла", 14, 40);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g.setColor(Style.INK2);
            String n = name.length() > 15 ? name.substring(0, 14) + "…" : name;
            int w = g.getFontMetrics().stringWidth(n);
            g.drawString(n, (getWidth() - w) / 2, getHeight() - 10);
            g.dispose();
        }
    }

    static JLabel dummy() {
        return new JLabel();
    }
}
