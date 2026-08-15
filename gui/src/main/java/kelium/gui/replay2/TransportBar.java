package kelium.gui.replay2;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;

import kelium.gui.TransportIcons;

/**
 * TransportBar — ПУЛЬТ: одна полоса, слева кнопки, состояние РЯДОМ с кнопками.
 *
 * <p>Что исправлено против 1.0:
 * <ul>
 *   <li>«шаг 301 из 720» стояло в дальнем правом углу окна — глаз бегал через весь
 *       экран. Теперь состояние в том же месте, где кнопки;</li>
 *   <li>«играть» и «шаг вперёд» были одним и тем же треугольником; теперь у шага
 *       стойка, а «играть» — единственная крупная кнопка с цветом;</li>
 *   <li>скорость была списком (лишний щелчок) — теперь {@code − 1× +};</li>
 *   <li>вместо одной галочки «пауза на боях» — СПИСОК условий автостопа;</li>
 *   <li>появилась ГРАНУЛЯРНОСТЬ шага: событие, ход, круг, раунд. 720 событий — это
 *       много, по ходам партия читается вдвое быстрее;</li>
 *   <li>«как можно быстрее» больше не таймер на 1 мс с полной перерисовкой: шаги
 *       проходятся пачками, экран обновляется ~20 раз в секунду.</li>
 * </ul>
 */
public final class TransportBar extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Ступени скорости: во сколько раз быстрее обычного. Последняя — «без пауз». */
    private static final double[] SPEEDS = {0.25, 0.5, 1, 2, 4, 8, Double.POSITIVE_INFINITY};
    private static final int NORMAL_DELAY = 700;

    private final Session session;
    private final SceneField field;

    private final JButton toStart;
    private final JButton stepBack;
    private final JButton playPause;
    private final JButton stepFwd;
    private final JButton toEnd;
    private final JButton roundPrev;
    private final JButton roundNext;
    private final JButton battlePrev;
    private final JButton battleNext;
    private final JButton turnPrev;
    private final JButton turnNext;
    private final JButton stops;
    private final JLabel position = new JLabel();
    private final JLabel speedLabel = new JLabel();
    private final JButton stepMode;

    private int speedIndex = 2;
    private final Timer ticker;
    private java.util.function.Consumer<String> onSay = s -> { };

    public TransportBar(Session session, SceneField field) {
        this.session = session;
        this.field = field;
        setOpaque(true);
        setLayout(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(4) + " " + Theme.px(8) + " " + Theme.px(4) + " "
                + Theme.px(8) + ", gapx " + Theme.px(6) + ", novisualpadding",
            "[]" + Theme.px(10) + "[]" + Theme.px(10) + "[]" + Theme.px(10)
                + "[]push[]" + Theme.px(8) + "[]" + Theme.px(8) + "[]", "[]"));

        int big = 34;
        int side = 28;
        toStart = Ui2.iconButton(TransportIcons.of("TO_START", Theme.px(16)),
            "В самое начало партии (Home).", side, () -> {
                stop();
                session.seek(0);
            });
        stepBack = Ui2.iconButton(TransportIcons.of("STEP_BACK", Theme.px(16)),
            "Шаг назад (стрелка ←). Чем именно шагать — выбирается кнопкой справа.",
            side, () -> {
                stop();
                session.stepBy(-1);
            });
        playPause = Ui2.iconButton(TransportIcons.of("PLAY", Theme.px(20)),
            "Играть или пауза (пробел).", big, this::togglePlay);
        stepFwd = Ui2.iconButton(TransportIcons.of("STEP_FWD", Theme.px(16)),
            "Шаг вперёд (стрелка →).", side, () -> {
                stop();
                session.stepBy(+1);
            });
        toEnd = Ui2.iconButton(TransportIcons.of("TO_END", Theme.px(16)),
            "В конец партии (End).", side, () -> {
                stop();
                session.seekEnd();
            });

        roundPrev = Ui2.iconButton(TransportIcons.of("ROUND_PREV", Theme.px(16)),
            "К началу предыдущего раунда (Page Up).", side, () -> {
                stop();
                session.jumpRound(-1);
            });
        roundNext = Ui2.iconButton(TransportIcons.of("ROUND_NEXT", Theme.px(16)),
            "К началу следующего раунда (Page Down).", side, () -> {
                stop();
                session.jumpRound(+1);
            });
        battlePrev = Ui2.iconButton(TransportIcons.of("BATTLE_PREV", Theme.px(16)),
            "К ближайшему бою назад (Shift+B).", side, () -> {
                stop();
                if (!session.jumpBattle(-1)) {
                    onSay.accept("Раньше этого шага боёв не было.");
                }
            });
        battleNext = Ui2.iconButton(TransportIcons.of("BATTLE_NEXT", Theme.px(16)),
            "К ближайшему бою вперёд (B).", side, () -> {
                stop();
                if (!session.jumpBattle(+1)) {
                    onSay.accept("Дальше боёв нет — до самого конца партии.");
                }
            });
        turnPrev = Ui2.iconButton(TransportIcons.of("STEP_BACK", Theme.px(14)),
            "К предыдущему ходу ТОГО ЖЕ игрока (Shift+N).", side, () -> {
                stop();
                if (!session.jumpSameSeatTurn(-1)) {
                    onSay.accept("Раньше этот игрок не ходил.");
                }
            });
        turnNext = Ui2.iconButton(TransportIcons.of("STEP_FWD", Theme.px(14)),
            "К следующему ходу ТОГО ЖЕ игрока (N).", side, () -> {
                stop();
                if (!session.jumpSameSeatTurn(+1)) {
                    onSay.accept("Дальше этот игрок не ходит.");
                }
            });

        stepMode = Ui2.textButton(stepModeText(),
            "Чем шагают стрелки: одно событие, ход игрока, круг или целый раунд.",
            this::cycleStepMode);
        stops = Ui2.textButton("автостоп 1",
            "На чём показ останавливается сам. Щёлкни, чтобы выбрать условия.",
            () -> { });
        stops.addActionListener(e -> showStopMenu());

        JPanel main = groupOf(toStart, stepBack, playPause, stepFwd, toEnd);
        JPanel jumps = groupOf(roundPrev, roundNext, battlePrev, battleNext);
        JPanel turns = groupOf(turnPrev, turnNext);

        add(main);
        add(jumps);
        add(turns);
        add(stepMode);
        position.setFont(Theme.mono(12, Font.BOLD));
        Ui2.fg(position, "ink");
        add(position);
        add(speedPanel());
        add(stops);

        ticker = new Timer(NORMAL_DELAY, this::tick);
        session.whenFrameChanged(s -> refresh());
        session.whenRecordChanged(s -> {
            stop();
            refresh();
        });
        refresh();
    }

    /** Фон — из темы на каждую отрисовку, чтобы переключение темы его не забывало. */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        g.setColor(Theme.panel());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    public void setOnSay(java.util.function.Consumer<String> say) {
        this.onSay = say;
    }

    private JPanel groupOf(JButton... buttons) {
        JPanel p = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, gapx " + Theme.px(2) + ", novisualpadding"));
        p.setOpaque(false);
        for (JButton b : buttons) {
            p.add(b);
        }
        return p;
    }

    private JPanel speedPanel() {
        JPanel p = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, gapx " + Theme.px(2) + ", novisualpadding"));
        p.setOpaque(false);
        JButton minus = Ui2.textButton("−", "Медленнее.", () -> changeSpeed(-1));
        JButton plus = Ui2.textButton("+", "Быстрее.", () -> changeSpeed(+1));
        speedLabel.setFont(Theme.mono(12, Font.BOLD));
        Ui2.fg(speedLabel, "ink2");
        speedLabel.setToolTipText(Ui2.tip("Скорость показа. Щелчок возвращает к 1×."));
        speedLabel.setCursor(java.awt.Cursor.getPredefinedCursor(
            java.awt.Cursor.HAND_CURSOR));
        speedLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                speedIndex = 2;
                applySpeed();
            }
        });
        p.add(minus);
        p.add(speedLabel, "w " + Theme.px(38) + "!, align center");
        p.add(plus);
        return p;
    }

    // ==================== показ ====================
    public boolean isPlaying() {
        return ticker.isRunning();
    }

    public void togglePlay() {
        if (!session.playable()) {
            return;
        }
        if (ticker.isRunning()) {
            stop();
        } else {
            if (session.cursor() >= session.frameCount() - 1) {
                session.seek(0);
            }
            applySpeed();
            ticker.start();
            refresh();
        }
    }

    public void stop() {
        if (ticker.isRunning()) {
            ticker.stop();
            field.setCheapMode(false);
            refresh();
        }
    }

    private void changeSpeed(int direction) {
        speedIndex = Math.max(0, Math.min(SPEEDS.length - 1, speedIndex + direction));
        applySpeed();
    }

    private void applySpeed() {
        double s = SPEEDS[speedIndex];
        // «Без пауз» — не таймер на 1 мс: экран обновляется 20 раз в секунду, а шаги
        // проходятся пачками. Иначе окно захлёбывается и перестаёт отвечать.
        ticker.setDelay(Double.isInfinite(s) ? 50 : (int) Math.max(16, NORMAL_DELAY / s));
        ticker.setInitialDelay(ticker.getDelay());
        field.setCheapMode(Double.isInfinite(s) || s > 2);
        refresh();
    }

    private void tick(ActionEvent e) {
        if (!session.playable()) {
            stop();
            return;
        }
        int batch = Double.isInfinite(SPEEDS[speedIndex]) ? 25 : 1;
        for (int k = 0; k < batch; k++) {
            int next = session.cursor() + 1;
            if (next >= session.frameCount()) {
                session.seekEnd();
                stop();
                return;
            }
            session.seek(next);
            String reason = session.stopReason(next);
            if (reason != null) {
                stop();
                onSay.accept("Остановился " + reason + " — так стоят условия автостопа.");
                return;
            }
        }
    }

    // ==================== гранулярность и автостоп ====================
    private String stepModeText() {
        return "шаг: " + session.step().label;
    }

    private void cycleStepMode() {
        Session.Step[] all = Session.Step.values();
        session.setStep(all[(session.step().ordinal() + 1) % all.length]);
        stepMode.setText(stepModeText());
        onSay.accept("Стрелки листают: " + session.step().label + ".");
    }

    private void showStopMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (Session.Stop s : Session.Stop.values()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(s.label,
                session.stops().contains(s));
            item.addActionListener(e -> {
                session.toggleStop(s, item.isSelected());
                refresh();
            });
            menu.add(item);
        }
        menu.addSeparator();
        JCheckBoxMenuItem none = new JCheckBoxMenuItem("не останавливаться ни на чём");
        none.addActionListener(e -> {
            session.stops().clear();
            refresh();
        });
        menu.add(none);
        menu.show(stops, 0, -menu.getPreferredSize().height);
    }

    // ==================== обновление вида ====================
    private void refresh() {
        boolean has = session.playable();
        boolean atStart = !has || session.cursor() <= 0;
        boolean atEnd = !has || session.cursor() >= session.frameCount() - 1;
        for (JButton b : new JButton[]{toStart, stepBack, roundPrev, battlePrev, turnPrev}) {
            b.setEnabled(!atStart);
        }
        for (JButton b : new JButton[]{stepFwd, toEnd, roundNext, battleNext, turnNext}) {
            b.setEnabled(!atEnd);
        }
        playPause.setEnabled(has);
        stepMode.setEnabled(has);
        stops.setEnabled(has);
        String code = ticker.isRunning() ? "PAUSE" : (atEnd && has ? "REPLAY" : "PLAY");
        playPause.setIcon(TransportIcons.of(code, Theme.px(20)));
        playPause.setToolTipText(Ui2.tip(ticker.isRunning() ? "Пауза (пробел)."
            : (atEnd && has ? "Партия закончена — показать сначала (пробел)."
                : "Играть (пробел).")));

        if (!session.hasRecord()) {
            position.setText("запись не загружена");
        } else {
            var f = session.frame();
            position.setText("шаг " + (session.cursor() + 1) + "/" + session.frameCount()
                + "   Р" + f.round + (f.circle > 0 ? " круг " + f.circle : ""));
        }
        double s = SPEEDS[speedIndex];
        speedLabel.setText(Double.isInfinite(s) ? "∞"
            : (s == Math.floor(s) ? (int) s + "×" : String.valueOf(s).replace('.', ',') + "×"));
        stops.setText("автостоп " + session.stops().size());
        List<String> names = new ArrayList<>();
        for (Session.Stop st : session.stops()) {
            names.add(st.label);
        }
        stops.setToolTipText(Ui2.tip(names.isEmpty()
            ? "Показ не останавливается сам. Щёлкни, чтобы выбрать условия."
            : "Останавливаться: " + String.join(", ", names)
              + ".\n\nЩёлкни, чтобы изменить."));
    }
}
