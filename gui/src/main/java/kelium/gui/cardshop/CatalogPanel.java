package kelium.gui.cardshop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * КАТАЛОГ ТИПА (дизайнер 05.10.2026: «слишком много всего перед глазами»):
 * плитки карт — крупная миниатюра, номер и название; сверху поиск и короткая
 * строка кнопок. Остальное — правой кнопкой по плитке и клавишами:
 * Insert — новая после выбранной, Ctrl+D — копия, Delete — удалить,
 * Alt+↑/↓ — переставить. Плитку можно перетащить мышью на новое место.
 * Миниатюры рисуются в фоне и обновляются после правки карты.
 */
final class CatalogPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int THUMB_H = 150;
    private static final int TILE_W = 170;

    /** Что делает окно мастерской по командам каталога. */
    interface Actions {
        void select(int index);

        void insert(int at);

        void duplicate(int index);

        void delete(int index);

        void moveTo(int from, int to);
    }

    private final CardAssets assets;
    private final Actions act;
    private final Model model = new Model();
    private final JList<CardSpec> list = new JList<>(model);
    private final JLabel title = new JLabel();
    private final JTextField search = new JTextField();
    private final Map<CardSpec, BufferedImage> thumbs = new HashMap<>();
    private final Map<CardSpec, Integer> thumbKey = new HashMap<>();
    // три потока низкого приоритета: каталог в 40 карт рисуется за секунды, а
    // окно и процессор остаются свободны (бюджет — оставить 15% Владу)
    private final ExecutorService painter = Executors.newFixedThreadPool(
        Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 2)), r -> {
        Thread t = new Thread(r, "миниатюры каталога");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private List<CardSpec> all = List.of();
    private final List<Integer> shown = new ArrayList<>();
    private boolean quiet;

    CatalogPanel(CardAssets assets, Actions act) {
        super(new BorderLayout(0, 8));
        this.assets = assets;
        this.act = act;
        setBackground(Style.PANEL);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        title.setFont(Style.title(16));
        title.setForeground(Style.INK);
        head.add(title, BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText", "найти по названию или тексту");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });
        head.add(search, BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bar.setOpaque(false);
        bar.add(tool("+ Новая", "Новая карта после выбранной (Insert)", () -> act.insert(sel() + 1)));
        bar.add(tool("⧉", "Копия выбранной (Ctrl+D)", () -> act.duplicate(sel())));
        bar.add(tool("✕", "Удалить выбранную (Delete)", () -> act.delete(sel())));
        head.add(bar, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setBackground(Style.BG);
        list.setCellRenderer(new Tile());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !quiet && sel() >= 0) {
                act.select(sel());
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override public void mouseReleased(MouseEvent e) {
                popup(e);
            }
        });
        keys();
        dragAndDrop();
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(Style.LINE));
        sp.getVerticalScrollBar().setUnitIncrement(30);
        add(sp, BorderLayout.CENTER);
        JLabel hint = new JLabel("<html>Правая кнопка — меню · перетащите плитку, чтобы "
            + "переставить · номера ставятся сами</html>");
        hint.setForeground(Style.INK3);
        hint.setFont(hint.getFont().deriveFont(11f));
        add(hint, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(380, 600));
    }

    private static JButton tool(String text, String tip, Runnable r) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.setToolTipText(tip);
        b.addActionListener(e -> r.run());
        return b;
    }

    /** Номер выбранной карты в полном списке; −1 — ничего. */
    int sel() {
        int i = list.getSelectedIndex();
        return i < 0 || i >= shown.size() ? -1 : shown.get(i);
    }

    private void keys() {
        bind("INSERT", () -> act.insert(sel() + 1));
        bind("ctrl D", () -> act.duplicate(sel()));
        bind("DELETE", () -> act.delete(sel()));
        bind("alt UP", () -> {
            if (sel() > 0 && !filtered()) {
                act.moveTo(sel(), sel() - 1);
            }
        });
        bind("alt DOWN", () -> {
            if (sel() >= 0 && sel() < all.size() - 1 && !filtered()) {
                act.moveTo(sel(), sel() + 1);
            }
        });
    }

    private void bind(String key, Runnable r) {
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        list.getActionMap().put(key, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        });
    }

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int i = list.locationToIndex(e.getPoint());
        if (i >= 0) {
            list.setSelectedIndex(i);
        }
        int at = sel();
        JPopupMenu m = new JPopupMenu();
        m.add(item("Новая карта перед этой", () -> act.insert(Math.max(0, at))));
        m.add(item("Новая карта после этой   Insert", () -> act.insert(at + 1)));
        m.add(item("Новая карта в конец", () -> act.insert(all.size())));
        m.addSeparator();
        m.add(item("Копия   Ctrl+D", () -> act.duplicate(at)));
        m.add(item("Выше   Alt+↑", () -> {
            if (at > 0 && !filtered()) {
                act.moveTo(at, at - 1);
            }
        }));
        m.add(item("Ниже   Alt+↓", () -> {
            if (at >= 0 && at < all.size() - 1 && !filtered()) {
                act.moveTo(at, at + 1);
            }
        }));
        m.add(item("В начало", () -> {
            if (at > 0 && !filtered()) {
                act.moveTo(at, 0);
            }
        }));
        m.add(item("В конец", () -> {
            if (at >= 0 && !filtered()) {
                act.moveTo(at, all.size() - 1);
            }
        }));
        m.addSeparator();
        m.add(item("Удалить   Delete", () -> act.delete(at)));
        m.show(list, e.getX(), e.getY());
    }

    private static JMenuItem item(String t, Runnable r) {
        JMenuItem i = new JMenuItem(t);
        i.addActionListener(e -> r.run());
        return i;
    }

    /** Перетаскивание плитки на новое место (при поиске — выключено). */
    private void dragAndDrop() {
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(new TransferHandler() {
            private static final long serialVersionUID = 1L;

            @Override public int getSourceActions(JComponent c) {
                return filtered() ? NONE : MOVE;
            }

            @Override protected Transferable createTransferable(JComponent c) {
                return new StringSelection(String.valueOf(sel()));
            }

            @Override public boolean canImport(TransferSupport s) {
                return s.isDrop() && s.isDataFlavorSupported(DataFlavor.stringFlavor) && !filtered();
            }

            @Override public boolean importData(TransferSupport s) {
                try {
                    int from = Integer.parseInt((String) s.getTransferable()
                        .getTransferData(DataFlavor.stringFlavor));
                    int to = ((JList.DropLocation) s.getDropLocation()).getIndex();
                    if (to > from) {
                        to--;
                    }
                    if (from >= 0 && to != from) {
                        int fFrom = from;
                        int fTo = Math.max(0, Math.min(all.size() - 1, to));
                        SwingUtilities.invokeLater(() -> act.moveTo(fFrom, fTo));
                    }
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
    }

    private boolean filtered() {
        return !search.getText().isBlank();
    }

    private void filter() {
        int keep = sel();
        rebuildShown();
        reselect(keep);
    }

    private void rebuildShown() {
        shown.clear();
        String q = search.getText().trim().toLowerCase();
        for (int i = 0; i < all.size(); i++) {
            if (q.isEmpty() || (all.get(i).text("имя") + " " + all.get(i).text("условие") + " "
                    + all.get(i).text("низ") + " " + all.get(i).text("верх")).toLowerCase().contains(q)) {
                shown.add(i);
            }
        }
        model.changed();
    }

    private void reselect(int index) {
        quiet = true;
        int pos = shown.indexOf(index);
        if (pos >= 0) {
            list.setSelectedIndex(pos);
            list.ensureIndexIsVisible(pos);
        } else {
            list.clearSelection();
        }
        quiet = false;
    }

    /** Показать каталог типа и выделить карту index. */
    void show(CardSpec.Type t, List<CardSpec> cards, int index) {
        this.all = cards;
        title.setText(t.ru + " — " + cards.size() + " " + Library.cardsWord(cards.size()));
        rebuildShown();
        reselect(index);
        for (CardSpec c : cards) {
            refresh(c);
        }
    }

    /** Карта изменилась — перерисовать её миниатюру. */
    void refresh(CardSpec c) {
        refresh(c, null);
    }

    void refresh(CardSpec c, Map<String, Object> typeLayout) {
        int key = c.fields.toString().hashCode() + (typeLayout == null ? 0 : typeLayout.toString().hashCode());
        Integer had = thumbKey.get(c);
        if (had != null && had == key) {
            return;
        }
        thumbKey.put(c, key);
        CardSpec snap = new CardSpec(c.type());
        snap.fields.putAll(c.fields);
        if (typeLayout != null) {
            snap.fields.put("_раскладка_типа", typeLayout);
        }
        painter.submit(() -> {
            BufferedImage im;
            try {
                BufferedImage full = CardRender.render(assets, snap);
                double k = Math.min((double) THUMB_H / full.getHeight(), (TILE_W - 16.0) / full.getWidth());
                im = CardAssets.scale(full, (int) Math.round(full.getWidth() * k),
                    (int) Math.round(full.getHeight() * k));
            } catch (RuntimeException e) {
                im = null;
            }
            BufferedImage done = im;
            SwingUtilities.invokeLater(() -> {
                if (done != null) {
                    thumbs.put(c, done);
                } else {
                    thumbs.remove(c);
                }
                list.repaint();
            });
        });
    }

    private final class Model extends AbstractListModel<CardSpec> {
        private static final long serialVersionUID = 1L;

        @Override public int getSize() {
            return shown.size();
        }

        @Override public CardSpec getElementAt(int i) {
            return all.get(shown.get(i));
        }

        void changed() {
            fireContentsChanged(this, 0, Math.max(0, shown.size() - 1));
        }
    }

    /** Плитка: миниатюра, номер двумя знаками и название. */
    private final class Tile extends JComponent implements ListCellRenderer<CardSpec> {
        private static final long serialVersionUID = 1L;
        private CardSpec c;
        private int number;
        private boolean selected;

        Tile() {
            setPreferredSize(new Dimension(TILE_W, THUMB_H + 46));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CardSpec> l, CardSpec c,
                                                      int i, boolean sel, boolean focus) {
            this.c = c;
            this.number = shown.get(i) + 1;
            this.selected = sel;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(selected ? Style.ACCENT : Style.PANEL);
            g.fillRoundRect(4, 4, w - 8, h - 8, 12, 12);
            BufferedImage t = thumbs.get(c);
            if (t != null) {
                g.drawImage(t, (w - t.getWidth()) / 2, 10 + (THUMB_H - t.getHeight()) / 2, null);
            } else {
                g.setColor(Style.INK3);
                g.drawString("рисую…", w / 2 - 22, THUMB_H / 2);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(selected ? Color.WHITE : Style.INK);
            String n = String.format("%02d", number);
            g.drawString(n, 12, THUMB_H + 28);
            int nx = 14 + g.getFontMetrics().stringWidth(n) + 6;
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.setColor(selected ? Color.WHITE : Style.INK2);
            String name = c.text("имя").replace("\n", " ");
            if (name.isBlank()) {
                name = c.text("условие").replace("\n", " ");
            }
            if (name.isBlank()) {
                name = "без названия";
            }
            int avail = w - nx - 10;
            while (g.getFontMetrics().stringWidth(name) > avail && name.length() > 2) {
                name = name.substring(0, name.length() - 2) + "…";
                if (name.endsWith("……")) {
                    name = name.substring(0, name.length() - 1);
                }
            }
            g.drawString(name, nx, THUMB_H + 28);
            g.dispose();
        }
    }
}
