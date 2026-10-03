package kelium.gui.cardshop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/**
 * КАТАЛОГ ТИПА: список карт с миниатюрами, номерами и названиями; кнопки —
 * добавить в конец, вставить после выбранной, копия, удалить, выше, ниже.
 * Миниатюры рисуются в фоне и обновляются после правки карты.
 */
final class CatalogPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int THUMB_H = 96;

    /** Что делает окно мастерской по кнопкам каталога. */
    interface Actions {
        void select(int index);

        void addEnd();

        void insertAfter(int index);

        void duplicate(int index);

        void delete(int index);

        void move(int index, int delta);
    }

    private final CardAssets assets;
    private final Model model = new Model();
    private final JList<CardSpec> list = new JList<>(model);
    private final JLabel title = new JLabel();
    private final Map<CardSpec, ImageIcon> thumbs = new HashMap<>();
    private final Map<CardSpec, Integer> thumbKey = new HashMap<>();
    private final ExecutorService painter = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "миниатюры каталога");
        t.setDaemon(true);
        return t;
    });
    private List<CardSpec> cards = List.of();
    private boolean quiet;

    CatalogPanel(CardAssets assets, Actions act) {
        super(new BorderLayout(0, 6));
        this.assets = assets;
        setBackground(Style.PANEL);
        setBorder(BorderFactory.createEmptyBorder(10, 8, 8, 8));
        title.setFont(Style.title(15));
        title.setForeground(Style.INK);
        add(title, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Style.BG);
        list.setCellRenderer(new Cell());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !quiet && list.getSelectedIndex() >= 0) {
                act.select(list.getSelectedIndex());
            }
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(Style.LINE));
        sp.getVerticalScrollBar().setUnitIncrement(24);
        add(sp, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(3, 2, 4, 4));
        buttons.setOpaque(false);
        buttons.add(btn("+ в конец", act::addEnd));
        buttons.add(btn("+ после выбранной", () -> act.insertAfter(list.getSelectedIndex())));
        buttons.add(btn("Копия", () -> act.duplicate(list.getSelectedIndex())));
        buttons.add(btn("Удалить", () -> act.delete(list.getSelectedIndex())));
        buttons.add(btn("▲ выше", () -> act.move(list.getSelectedIndex(), -1)));
        buttons.add(btn("▼ ниже", () -> act.move(list.getSelectedIndex(), +1)));
        add(buttons, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(300, 600));
    }

    private static JButton btn(String t, Runnable r) {
        JButton b = new JButton(t);
        b.setFocusable(false);
        b.addActionListener(e -> r.run());
        return b;
    }

    /** Показать каталог типа и выделить карту index. */
    void show(CardSpec.Type t, List<CardSpec> cards, int index) {
        this.cards = cards;
        title.setText(t.ru + " · карт: " + cards.size());
        model.changed();
        quiet = true;
        if (index >= 0 && index < cards.size()) {
            list.setSelectedIndex(index);
            list.ensureIndexIsVisible(index);
        } else {
            list.clearSelection();
        }
        quiet = false;
        for (CardSpec c : cards) {
            refresh(c);
        }
    }

    /** Карта изменилась — перерисовать её миниатюру и строку. */
    void refresh(CardSpec c) {
        int key = c.fields.toString().hashCode();
        Integer had = thumbKey.get(c);
        if (had != null && had == key) {
            return;
        }
        thumbKey.put(c, key);
        CardSpec snap = new CardSpec(c.type());
        snap.fields.putAll(c.fields);
        painter.submit(() -> {
            ImageIcon icon;
            try {
                BufferedImage im = CardRender.render(assets, snap);
                double k = (double) THUMB_H / im.getHeight();
                icon = new ImageIcon(CardAssets.scale(im, (int) Math.round(im.getWidth() * k), THUMB_H));
            } catch (RuntimeException e) {
                icon = null;
            }
            ImageIcon done = icon;
            SwingUtilities.invokeLater(() -> {
                if (done != null) {
                    thumbs.put(c, done);
                } else {
                    thumbs.remove(c);
                }
                list.repaint();
            });
        });
        list.repaint();
    }

    private final class Model extends AbstractListModel<CardSpec> {
        private static final long serialVersionUID = 1L;

        @Override public int getSize() {
            return cards.size();
        }

        @Override public CardSpec getElementAt(int i) {
            return cards.get(i);
        }

        void changed() {
            fireContentsChanged(this, 0, Math.max(0, cards.size() - 1));
        }
    }

    /** Строка каталога: миниатюра, номер и название. */
    private final class Cell extends JPanel implements ListCellRenderer<CardSpec> {
        private static final long serialVersionUID = 1L;
        private final JLabel pic = new JLabel();
        private final JLabel num = new JLabel();
        private final JLabel name = new JLabel();

        Cell() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
            pic.setPreferredSize(new Dimension(THUMB_H * 803 / 520, THUMB_H));
            pic.setHorizontalAlignment(JLabel.CENTER);
            JPanel txt = new JPanel(new GridLayout(2, 1));
            txt.setOpaque(false);
            num.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            name.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            txt.add(num);
            txt.add(name);
            add(pic, BorderLayout.WEST);
            add(txt, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CardSpec> l, CardSpec c,
                                                      int i, boolean sel, boolean focus) {
            ImageIcon t = thumbs.get(c);
            pic.setIcon(t);
            pic.setText(t == null ? "…" : null);
            num.setText("№ " + (i + 1));
            String n = c.text("имя").replace("\n", " ");
            if (n.isBlank()) {
                n = c.text("условие").replace("\n", " ");
            }
            name.setText(n.isBlank() ? "без названия" : n);
            setBackground(sel ? Style.ACCENT : i % 2 == 0 ? Style.BG : Style.PANEL);
            num.setForeground(sel ? Color.WHITE : Style.INK);
            name.setForeground(sel ? Color.WHITE : Style.INK2);
            return this;
        }
    }
}
