package kelium.gui.replay2;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import kelium.dataio.GameConfig;
import kelium.gui.GameRecorder;

/**
 * TableDialog — ВСЕ НАСТРОЙКИ ИГРОКОВ ЗА СТОЛОМ, ОДНИМ ОКНОМ.
 *
 * <p>Заказ дизайнера 13.08.2026, дополнен 14.08.2026. Раньше здесь был только
 * цвет колоды и стороны планшетов, а характер бота и поворот ЦУ жили СНАРУЖИ,
 * отдельной строкой на ленте настроек — «нелогично, что часть вынесена».
 * Теперь ВСЁ хозяйство одного места — колода, стороны планшетов, характер
 * бота, поворот ЦУ — в одном окне. Списки характера и ЦУ — ТЕ ЖЕ САМЫЕ живые
 * компоненты, что раньше стояли на ленте: их выбор применяется сразу, без
 * кнопки «ОК» (как и было), а «ОК»/«Отмена» здесь решают судьбу только колоды
 * и сторон планшетов.
 *
 * <p>Что здесь НЕ выбирается: место за столом и стартовый гекс (даёт раскладка)
 * и ЦВЕТ МЕСТА НА ПОЛЕ (даёт номер места, не колода — см. {@link SeatChip}).
 * Колода приказов — это выбор УЗОРА нижних приказов, а не раскраски жетона,
 * и раньше подпись «цвет игрока» врала об этом (замечание дизайнера
 * 14.08.2026: «по факту цвет не выбирается, только колода»).
 */
public final class TableDialog {

    /**
     * ПЕРВЫЙ ПУНКТ СПИСКА КОЛОД — не «как раздастся», а «по умолчанию».
     *
     * <p>Заказ дизайнера 11.09.2026: «колода приказов теперь всегда должна
     * соответствовать цвету игрока и его планшетов по умолчанию; только я сам
     * могу зайти и поменять». Раньше невыбранная колода раздавалась по сиду —
     * то есть за красным планшетом мог оказаться зелёный узор приказов.
     */
    private static final String ПО_УМОЛЧАНИЮ = "по умолчанию";

    private TableDialog() {
    }

    /**
     * Показать диалог. Возвращает {@code true}, если колода или стороны
     * планшета поменялись, — тогда настройку стоит перерисовать и обновить
     * расстановку. Характер бота и поворот ЦУ применяются сразу по щелчку
     * (те же слушатели, что были на ленте), в этот флаг не входят.
     */
    public static boolean show(Component parent, String rulesetId, int players,
                               @SuppressWarnings("rawtypes") JComboBox[] characterBoxes,
                               @SuppressWarnings("rawtypes") JComboBox[] levelBoxes,
                               JComboBox<String>[] facingBoxes, SeatChip[] chips,
                               java.util.function.IntConsumer randomCharacter) {
        List<String> decks = decks(rulesetId);

        JPanel form = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(10) + ", gapx " + Theme.px(10)
                + ", gapy " + Theme.px(8),
            "[]" + Theme.px(6) + "[]" + Theme.px(6) + "[]" + Theme.px(6) + "[]"
                + Theme.px(4) + "[]" + Theme.px(6) + "[]" + Theme.px(6) + "[]"));
        form.add(new JLabel(), "");
        form.add(caption("характер бота"));
        // УРОВЕНЬ ОТДЕЛЬНЫМ СТОЛБЦОМ (заказ дизайнера 25.08.2026): кем играет
        // соперник и насколько он силён — два разных вопроса.
        form.add(caption("уровень"));
        form.add(caption("поворот ЦУ"), "span 2");
        // СТОЛБЦОВ «ПЛАНШЕТ ВОЙСК» И «ПЛАНШЕТ ХРАНИЛИЩА» ЗДЕСЬ НЕТ. Сторон «Б»
        // не существует (решение дизайнера 09.09.2026): планшеты у всех
        // одинаковые, и выбирать было бы нечего.
        boolean цветВыбирается = players < 4;
        if (цветВыбирается) {
            form.add(caption("цвет"));
        }
        form.add(caption("колода приказов"), "wrap");

        List<JComboBox<String>> dc = new ArrayList<>();
        List<JComboBox<String>> cc = new ArrayList<>();
        List<GameConfig.SeatPick> now = GameConfig.seatPickAll();
        for (int seat = 0; seat < players; seat++) {
            final int seatFinal = seat;
            GameConfig.SeatPick pick = seat < now.size() && now.get(seat) != null
                ? now.get(seat) : new GameConfig.SeatPick(null, null);

            form.add(chips[seat]);

            // ХАРАКТЕР И ЦУ — ТЕ ЖЕ ЖИВЫЕ КОМПОНЕНТЫ, что были на ленте: их
            // слушатели (обновить расстановку сразу) никуда не делись, они
            // просто теперь стоят в этом окне, а не в отдельной строке снаружи.
            form.add(characterBoxes[seat], "growx");
            form.add(levelBoxes[seat], "growx");
            form.add(dice(() -> randomCharacter.accept(seatFinal)));
            form.add(facingBoxes[seat], "growx");

            int цвет = pick.colorSlot() == null ? seat : pick.colorSlot();
            if (цветВыбирается) {
                JComboBox<String> c = colourBox(цвет);
                c.setToolTipText(Ui2.tip("ЦВЕТ ЭТОГО МЕСТА: краска его жетонов и "
                    + "планшетов. Втроём и вдвоём цвет выбирают, вчетвером на столе "
                    + "и так все четыре. За цветом идёт и колода приказов, если её "
                    + "не выбрали руками."));
                form.add(c, "growx");
                cc.add(c);
            }

            JComboBox<String> d = deckBox(decks, pick.orderColor(), цвет);
            d.setToolTipText(Ui2.tip("КОЛОДА ПРИКАЗОВ этого места: у каждой колоды свой "
                + "узор нижних приказов (что откроется вскрытой картой) — это главная "
                + "асимметрия партии. «По умолчанию» — колода цвета этого места."));
            form.add(d, "growx, wrap");
            dc.add(d);
        }
        JLabel note = new JLabel("<html>Колода, выбранная за столом, остальным местам "
            + "уже не достанется. На что колода не выбрана — берётся колода цвета "
            + "этого места. Характер бота и поворот ЦУ применяются сразу.</html>");
        note.setFont(Theme.note(11));
        note.setForeground(Theme.ink3());
        form.add(note, "span 5, growx, gaptop " + Theme.px(6));
        form.setPreferredSize(new Dimension(Theme.px(820),
            form.getPreferredSize().height));

        Window owner = parent == null ? null
            : javax.swing.SwingUtilities.getWindowAncestor(parent);
        int ok = JOptionPane.showConfirmDialog(owner, form,
            "Игроки: характер, уровень, поворот ЦУ и колода приказов",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return false;
        }
        boolean changed = false;
        for (int seat = 0; seat < players; seat++) {
            String deck = deckValue(dc.get(seat), decks);
            Integer colour = цветВыбирается && seat < cc.size()
                ? cc.get(seat).getSelectedIndex() : null;
            GameConfig.SeatPick was = seat < now.size() && now.get(seat) != null
                ? now.get(seat) : new GameConfig.SeatPick(null, null);
            if (!java.util.Objects.equals(was.orderColor(), deck)
                    || !java.util.Objects.equals(was.colorSlot(), colour)) {
                changed = true;
            }
            GameConfig.pickSeat(seat, deck, colour);
        }
        return changed;
    }

    /**
     * СБРОСИТЬ ВЫБОР МЕСТ К УМОЛЧАНИЮ.
     *
     * <p>Заказ дизайнера 11.09.2026: «кнопка „все случайно" НЕ МЕНЯЕТ колоду,
     * только я сам могу зайти и поменять». Поэтому случайной раздачи колод
     * здесь больше нет — есть возврат к умолчанию, то есть к колоде цвета
     * своего места. Цвет мест тоже возвращается к номерам.
     *
     * <p>Почему вообще что-то делаем, а не пропускаем кнопку мимо: выбор,
     * сделанный для партии на четверых, иначе остался бы висеть на игре вдвоём,
     * и колода ушла бы тому, кого за столом нет.
     */
    public static void randomise(String rulesetId, int players, java.util.Random rng) {
        for (int seat = 0; seat < 4; seat++) {
            GameConfig.pickSeat(seat, null, null);
        }
    }

    /** Короткая сводка выбора для строки настройки: «1: Красная колода». */
    public static String summary(int players) {
        List<GameConfig.SeatPick> all = GameConfig.seatPickAll();
        List<String> out = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            GameConfig.SeatPick p = seat < all.size() && all.get(seat) != null
                ? all.get(seat) : null;
            if (p == null) {
                continue;
            }
            List<String> bits = new ArrayList<>();
            if (p.orderColor() != null) {
                bits.add(Names.orderDeck(p.orderColor()));
            }
            out.add((seat + 1) + ": " + String.join(" ", bits));
        }
        return out.isEmpty() ? "" : String.join(" · ", out);
    }

    // ==================== содержимое списков ====================

    /** Колоды приказов из карточного набора выбранных правил (коды цветов). */
    private static List<String> decks(String rulesetId) {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> e : entries(rulesetId, "orders")) {
            if (Boolean.TRUE.equals(e.get("joker"))) {
                continue;      // БЕЗОПАСНОСТЬ — общая карта, не колода игрока
            }
            Object deck = e.get("deck");
            if (deck != null) {
                out.add(String.valueOf(deck));
            }
        }
        return new ArrayList<>(out);
    }

    private static List<Map<String, Object>> entries(String rulesetId, String type) {
        try {
            return GameConfig.buildCached(rulesetId, 4, 0L, null, null)
                .content.get(type).entries;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    // ==================== мелочи ====================

    private static JLabel caption(String text) {
        JLabel l = Ui2.caption(text);
        l.setFont(Theme.font(10, java.awt.Font.BOLD));
        return l;
    }

    /** Цветная точка-кубик — «случайный характер этого места», рядом со списком. */
    private static Component dice(Runnable action) {
        return Ui2.iconButton(kelium.gui.TransportIcons.of("DICE", Theme.px(16)),
            "Случайный характер бота из списка.", 22, action);
    }

    /**
     * Список колод: имя животного — как ПЕРВИЧНОЕ, цвет — как пояснение
     * («Волк — голубая»), а не наоборот. Раздел индексом, а не разбором
     * текста по тире, — иначе имя животного само содержало бы тире и
     * ломало разбор.
     */
    private static JComboBox<String> deckBox(List<String> values, String chosen,
                                            int colorSlot) {
        JComboBox<String> box = new JComboBox<>();
        box.setFont(Theme.body());
        String своя = Names.orderDeck(Names.orderDeckOfColour(colorSlot));
        box.addItem(ПО_УМОЛЧАНИЮ + " — " + своя);
        int sel = 0;
        for (int i = 0; i < values.size(); i++) {
            String v = values.get(i);
            String colour = Names.orderDeckColourWord(v);
            box.addItem(colour.isBlank() ? Names.orderDeck(v)
                : Names.orderDeck(v) + " — " + colour);
            if (v.equals(chosen)) {
                sel = i + 1;
            }
        }
        box.setSelectedIndex(sel);
        return box;
    }

    /**
     * СПИСОК ЦВЕТОВ МЕСТА. Порядок гнёзд тот же, по которому раздаются планшеты
     * и жетоны: синий, красный, зелёный, песочный.
     */
    private static JComboBox<String> colourBox(int chosen) {
        JComboBox<String> box = new JComboBox<>();
        box.setFont(Theme.body());
        for (String имя : new String[]{"синий", "красный", "зелёный", "песочный"}) {
            box.addItem(имя);
        }
        box.setSelectedIndex(Math.floorMod(chosen, 4));
        return box;
    }

    /** Обратное к {@link #deckBox}: индекс пункта → исходный код колоды. */
    private static String deckValue(JComboBox<String> box, List<String> values) {
        int idx = box.getSelectedIndex();
        return idx <= 0 || idx - 1 >= values.size() ? null : values.get(idx - 1);
    }


}
