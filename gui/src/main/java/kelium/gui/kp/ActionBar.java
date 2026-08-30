package kelium.gui.kp;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import kelium.gui.replay2.Theme;

/**
 * ДЕЙСТВИЯ ХОДА — то, что можно сыграть ПРЯМО СЕЙЧАС, и ничего сверх того.
 *
 * <p>Панель повторяет устройство карты приказа: у карты две половины, верхняя и
 * нижняя, и каждая даёт свои действия. Здесь так же — верхний приказ сверху,
 * нижний снизу. Играется всегда одна половина, её действия и стоят кнопками;
 * вторая строка в это время только подписана, чтобы место не прыгало.
 *
 * <p>ПОКАЗЫВАЮТСЯ ТОЛЬКО ДОСТУПНЫЕ ДЕЙСТВИЯ. Прежде здесь висели все восемь
 * постоянной сеткой 2×4: шесть из них всегда были серыми, сетка занимала треть
 * нижней панели, и картам приказов не оставалось места (замечание дизайнера
 * 30.08.2026: «кнопки действий справа огромные, да и нахуя все восемь
 * показывать; выбираешь карту приказа, и после этого появляются те кнопки
 * действий, которые доступны»). Что уже сыграно в этом ходу, видно в ленте
 * шагов хода — дублировать это восемью серыми плитками незачем.
 */
public final class ActionBar extends JPanel {

    /** Русские имена действий. Порядок печатный — он же порядок на карте. */
    public static final Map<String, String> ACTIONS = new LinkedHashMap<>();
    static {
        ACTIONS.put("build", "Стройка");
        ACTIONS.put("energy_swap", "Энергия");
        ACTIONS.put("assembly", "Сборка");
        ACTIONS.put("mining", "Добыча");
        ACTIONS.put("movement", "Манёвр");
        ACTIONS.put("combat", "Бой");
        ACTIONS.put("market", "Рынок");
        ACTIONS.put("science", "Наука");
    }

    private static final int BTN_W = 108;
    private static final int BTN_H = 42;

    private final Set<String> playedThisTurn = new LinkedHashSet<>();
    private final Map<String, KpButton> shown = new LinkedHashMap<>();

    /** Карта приказа, вскрытая в этот ход, — приставлена слева к кнопкам. */
    private final OrderPlate plate = new OrderPlate();

    private final JLabel topCap = cap();
    private final JLabel bottomCap = cap();
    private final JPanel topRow = row();
    private final JPanel bottomRow = row();

    public ActionBar() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        // ВСКРЫТЫЙ ПРИКАЗ ПРИСТАВЛЕН К КНОПКАМ. Действия берутся с этой карты —
        // и стоят вплотную к ней, чтобы не держать её в голове.
        add(plate);
        add(javax.swing.Box.createHorizontalStrut(Theme.px(10)));
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(topCap);
        rows.add(javax.swing.Box.createVerticalStrut(Theme.px(2)));
        rows.add(topRow);
        rows.add(javax.swing.Box.createVerticalStrut(Theme.px(6)));
        rows.add(bottomCap);
        rows.add(javax.swing.Box.createVerticalStrut(Theme.px(2)));
        rows.add(bottomRow);
        add(rows);
        idle("ход соперника");
    }

    /**
     * ВСКРЫТЫЙ В ЭТОТ ХОД ПРИКАЗ. {@code null} — карту ещё не вскрыли или ход
     * чужой: тогда на её месте пустое гнездо, и раскладка не прыгает.
     */
    public void setOrderCard(OrderCardFace.Info info, boolean bottomOpen) {
        plate.info = info;
        plate.bottomOpen = bottomOpen;
        plate.repaint();
    }

    /** Гнездо вскрытого приказа: лицо карты или пунктирная пустота. */
    private static final class OrderPlate extends JPanel {
        private OrderCardFace.Info info;
        private boolean bottomOpen;

        OrderPlate() {
            setOpaque(false);
            Dimension d = new Dimension(Theme.px(84), Theme.px(118));
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            if (info == null) {
                // МЕСТО ДЕРЖИТСЯ ПУНКТИРОМ, а не пустотой: иначе кнопки
                // действий переезжают всякий раз, как вскрывают приказ.
                g.setColor(Theme.ink3());
                g.setStroke(new java.awt.BasicStroke(1f, java.awt.BasicStroke.CAP_BUTT,
                    java.awt.BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
                g.drawRoundRect(0, 0, w, h, Theme.px(8), Theme.px(8));
                g.setFont(Theme.font(9, Font.PLAIN));
                var fm = g.getFontMetrics();
                String t = "приказ";
                g.drawString(t, (w - fm.stringWidth(t)) / 2, h / 2);
                g.dispose();
                return;
            }
            OrderCardFace.paint(g, info, 0, 0, w, h, false);
            if (!bottomOpen && info.bottom() != null) {
                // Нижняя половина не открылась — так и написано на карте.
                g.setColor(Theme.alpha(Theme.panel(), 0.55));
                g.fillRoundRect(0, h / 2, w, h - h / 2, Theme.px(8), Theme.px(8));
                g.setFont(Theme.font(8.5, Font.BOLD));
                g.setColor(Theme.ink3());
                var fm = g.getFontMetrics();
                String t = "низ закрыт";
                g.drawString(t, (w - fm.stringWidth(t)) / 2, h - Theme.px(6));
            }
            g.dispose();
        }
    }

    private static JLabel cap() {
        JLabel l = new JLabel(" ");
        l.setFont(Theme.font(9.5, Font.BOLD));
        l.setForeground(Theme.ink3());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel row() {
        JPanel p = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, gapx " + Theme.px(6) + ", gapy 0"));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** Начался чей-то ход: если наш — счёт сыгранного с нуля. */
    public void turnStarted() {
        playedThisTurn.clear();
    }

    /** Событие «действие сыграно» нашего места. */
    public void actionPlayed(String name) {
        playedThisTurn.add(name);
    }

    /** ПЕРЕЗАПИСАТЬ сыгранное — после отката «до точки». */
    public void setPlayed(Collection<String> names) {
        playedThisTurn.clear();
        playedThisTurn.addAll(names);
    }

    /** Что уже сыграно в этом ходу — для ленты шагов и прогонщиков. */
    public Set<String> played() {
        return Set.copyOf(playedThisTurn);
    }

    /** Совместимость: точка решения без указания половины приказа. */
    public void showDecision(Map<String, Integer> availableToOption, Consumer<Integer> onPick) {
        showDecision(availableToOption, null, null, onPick);
    }

    /**
     * Точка решения вида {@code action}.
     *
     * @param half {@code top} / {@code bottom} / {@code joker} — какая половина
     *             карты сейчас играется; {@code null}, если движок не сказал.
     * @param orderCat код категории приказа этой половины (у джокера — null).
     */
    public void showDecision(Map<String, Integer> availableToOption, String half,
                              String orderCat, Consumer<Integer> onPick) {
        shown.clear();
        topRow.removeAll();
        bottomRow.removeAll();

        boolean снизу = "bottom".equals(half);
        JPanel цель = снизу ? bottomRow : topRow;
        JLabel подпись = снизу ? bottomCap : topCap;
        JLabel другая = снизу ? topCap : bottomCap;

        подпись.setText(заголовок(half, orderCat));
        подпись.setForeground(Theme.accent());
        другая.setText(снизу ? "ВЕРХНИЙ ПРИКАЗ — сыгран" : "НИЖНИЙ ПРИКАЗ — если откроется");
        другая.setForeground(Theme.ink3());

        // Порядок кнопок — печатный порядок действий на карте, а не тот, в
        // котором их перечислил движок: раскладка не должна скакать.
        List<String> names = new ArrayList<>(ACTIONS.keySet());
        names.retainAll(availableToOption.keySet());
        for (String name : availableToOption.keySet()) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        for (String name : names) {
            int idx = availableToOption.get(name);
            KpButton b = new KpButton(ACTIONS.getOrDefault(name, name), "доступно", null);
            b.setPreferredSize(new Dimension(Theme.px(BTN_W), Theme.px(BTN_H)));
            b.setState(KpButton.State.AVAILABLE);
            b.onClick(() -> onPick.accept(idx));
            shown.put(name, b);
            цель.add(b);
        }
        if (names.isEmpty()) {
            цель.add(пусто("нет доступных действий"));
        }
        обновить();
    }

    /** Точка решения не про действия (или чужой ход): кнопок нет вовсе. */
    public void idle(String why) {
        shown.clear();
        topRow.removeAll();
        bottomRow.removeAll();
        topCap.setText("ДЕЙСТВИЯ ХОДА");
        topCap.setForeground(Theme.ink3());
        bottomCap.setText(" ");
        bottomCap.setForeground(Theme.ink3());
        topRow.add(пусто(why == null || why.isBlank() ? "не сейчас" : why));
        обновить();
    }

    private static JLabel пусто(String текст) {
        // ПУСТОТА — ОБЪЯСНЕНИЕ, А НЕ ДЫРА: строка занимает высоту кнопки, чтобы
        // соседи не переезжали, когда действия появляются и исчезают.
        JLabel l = new JLabel(текст);
        l.setFont(Theme.italic());
        l.setForeground(Theme.ink3());
        l.setPreferredSize(new Dimension(Theme.px(2 * BTN_W), Theme.px(BTN_H)));
        return l;
    }

    private static String заголовок(String half, String orderCat) {
        String кат = orderCat == null ? "" : " · " + ActionIcons.categoryRu(orderCat);
        if ("bottom".equals(half)) {
            return "НИЖНИЙ ПРИКАЗ" + кат;
        }
        if ("joker".equals(half)) {
            return "БЕЗОПАСНОСТЬ · любые два разных";
        }
        if ("top".equals(half)) {
            return "ВЕРХНИЙ ПРИКАЗ" + кат;
        }
        return "ДОСТУПНЫЕ ДЕЙСТВИЯ";
    }

    private void обновить() {
        revalidate();
        repaint();
    }

    /** Кнопка по имени действия — или null, если сейчас не предлагается. */
    public KpButton tile(String action) {
        return shown.get(action);
    }
}
