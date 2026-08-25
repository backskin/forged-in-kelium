package kelium.gui.kp;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JPanel;

import kelium.gui.replay2.Theme;

/**
 * ПАНЕЛЬ ДЕЙСТВИЙ ХОДА — восемь ПОСТОЯННЫХ плиток (концепт §2: «все 8 действий
 * видны всегда, недоступные — серые с причиной»). Плитки никогда не исчезают —
 * меняются только их состояния:
 *
 * <ul>
 *   <li>доступно — движок предложил его в текущей точке {@code action};</li>
 *   <li>сыграно ✓ — уже играно в этом ходу (по событиям);</li>
 *   <li>серое с причиной — прямо сейчас не предлагается.</li>
 * </ul>
 */
public final class ActionBar extends JPanel {

    /** Порядок плиток фиксирован — игрок выучивает раскладку, она не скачет. */
    public static final Map<String, String> ACTIONS = new LinkedHashMap<>();
    static {
        ACTIONS.put("build", "Стройка");
        ACTIONS.put("mining", "Добыча");
        ACTIONS.put("movement", "Манёвр");
        ACTIONS.put("combat", "Бой");
        ACTIONS.put("market", "Рынок");
        ACTIONS.put("science", "Наука");
        ACTIONS.put("assembly", "Сборка");
        ACTIONS.put("energy_swap", "Энергия");
    }

    private final Map<String, KpButton> tiles = new LinkedHashMap<>();
    private final Set<String> playedThisTurn = new LinkedHashSet<>();

    public ActionBar() {
        setOpaque(false);
        setLayout(new GridLayout(2, 4, Theme.px(6), Theme.px(6)));
        for (var e : ACTIONS.entrySet()) {
            KpButton b = new KpButton(e.getValue(), "", null);
            b.setPreferredSize(new Dimension(Theme.px(96), Theme.px(46)));
            b.setState(KpButton.State.DISABLED);
            b.setTexts(e.getValue(), "не сейчас");
            tiles.put(e.getKey(), b);
            add(b);
        }
    }

    /** Начался чей-то ход: если наш — счёт сыгранного с нуля. */
    public void turnStarted() {
        playedThisTurn.clear();
    }

    /** Событие «действие сыграно» нашего места. */
    public void actionPlayed(String name) {
        playedThisTurn.add(name);
    }

    /**
     * Точка решения вида {@code action}: доступные действия — кликабельны,
     * сыгранные — с галкой, остальные — серые с причиной.
     */
    public void showDecision(Map<String, Integer> availableToOption, Consumer<Integer> onPick) {
        for (var e : tiles.entrySet()) {
            String name = e.getKey();
            KpButton b = e.getValue();
            Integer idx = availableToOption.get(name);
            if (idx != null) {
                b.setState(KpButton.State.AVAILABLE);
                b.setTexts(ACTIONS.get(name), "доступно");
                b.onClick(() -> onPick.accept(idx));
                b.setToolTipText(null);
            } else if (playedThisTurn.contains(name)) {
                b.setState(KpButton.State.PLAYED);
                b.setTexts(ACTIONS.get(name), "сыграно ✓");
                b.setToolTipText("Уже сыграно в этом ходу");
            } else {
                b.setState(KpButton.State.DISABLED);
                b.setTexts(ACTIONS.get(name), "не в приказе");
                b.setToolTipText("Вскрытый приказ не открывает это действие сейчас");
            }
        }
    }

    /** Точка решения не про действия (или чужой ход) — плитки гаснут, но ОСТАЮТСЯ. */
    public void idle(String why) {
        for (var e : tiles.entrySet()) {
            KpButton b = e.getValue();
            if (playedThisTurn.contains(e.getKey())) {
                b.setState(KpButton.State.PLAYED);
                b.setTexts(ACTIONS.get(e.getKey()), "сыграно ✓");
            } else {
                b.setState(KpButton.State.DISABLED);
                b.setTexts(ACTIONS.get(e.getKey()), why);
            }
            b.onClick(null);
        }
    }

    /** Плитка по имени действия (для прогонщиков/тестов). */
    public KpButton tile(String action) {
        return tiles.get(action);
    }
}
