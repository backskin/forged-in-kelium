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

    /** Частые иконки: [ключ, подпись]. */
    static final String[][] ACTIONS = {
        {"34", "Добыча"}, {"32", "Питание"}, {"действие — снабжение", "Снабжение"},
        {"действие — командование", "Командование"}, {"действие — развитие", "Развитие"},
        {"33", "Выпустить"}, {"35", "Манёвр"}, {"36", "Бой"}, {"37", "Рынок"}, {"38", "Наука"},
    };
    static final String[][] RESOURCES = {
        {"1", "монета"}, {"3", "боеприпас"}, {"5", "келемий"}, {"9", "трофей"},
        {"14", "арсенал"}, {"25", "спец-действие"}, {"13", "контейнер"}, {"43", "позолота"},
        {"40", "модуль атаки"}, {"41", "модуль сборки"}, {"21", "звезда"}, {"22", "прочность"},
    };

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
            every.add(new String[] {k, k.matches("\\d+") ? "№ " + k : k});
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
            BufferedImage ic = a.icon(key);
            img = ic == null ? null : CardAssets.fit(ic, 64, 64);
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
