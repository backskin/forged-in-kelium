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

    private static final String AUTO = "как раздастся";
    private static final String AS_RULES = "как в правилах";

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
                               JComboBox<String>[] facingBoxes, SeatChip[] chips,
                               java.util.function.IntConsumer randomCharacter) {
        List<String> decks = decks(rulesetId);
        List<String> troop = sides(rulesetId, "troop_side");
        List<String> storage = sides(rulesetId, "storage_side");

        JPanel form = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(10) + ", gapx " + Theme.px(10)
                + ", gapy " + Theme.px(8),
            "[]" + Theme.px(6) + "[]" + Theme.px(6) + "[]" + Theme.px(4) + "[]"
                + Theme.px(6) + "[]" + Theme.px(6) + "[]"));
        form.add(new JLabel(), "");
        form.add(caption("характер бота"));
        form.add(caption("поворот ЦУ"), "span 2");
        form.add(caption("колода приказов"));
        form.add(caption("планшет войск"));
        form.add(caption("планшет хранилища"), "wrap");

        List<JComboBox<String>> dc = new ArrayList<>();
        List<JComboBox<String>> tc = new ArrayList<>();
        List<JComboBox<String>> sc = new ArrayList<>();
        List<GameConfig.SeatPick> now = GameConfig.seatPickAll();
        for (int seat = 0; seat < players; seat++) {
            final int seatFinal = seat;
            GameConfig.SeatPick pick = seat < now.size() && now.get(seat) != null
                ? now.get(seat) : new GameConfig.SeatPick(null, null, null);

            form.add(chips[seat]);

            // ХАРАКТЕР И ЦУ — ТЕ ЖЕ ЖИВЫЕ КОМПОНЕНТЫ, что были на ленте: их
            // слушатели (обновить расстановку сразу) никуда не делись, они
            // просто теперь стоят в этом окне, а не в отдельной строке снаружи.
            form.add(characterBoxes[seat], "growx");
            form.add(dice(() -> randomCharacter.accept(seatFinal)));
            form.add(facingBoxes[seat], "growx");

            JComboBox<String> d = deckBox(decks, pick.orderColor());
            d.setToolTipText(Ui2.tip("КОЛОДА ПРИКАЗОВ этого места: у каждой колоды свой "
                + "узор нижних приказов (что откроется вскрытой картой) — это главная "
                + "асимметрия партии. Цвет самого места на поле колода НЕ меняет — "
                + "он всегда идёт по номеру места."));
            form.add(d, "growx");
            dc.add(d);

            JComboBox<String> t = box(AS_RULES, troop, pick.troopSide(), s -> s);
            t.setToolTipText(Ui2.tip("Сторона планшета ВОЙСК: чем и по кому бьют "
                + "роды войск этого игрока."));
            form.add(t, "growx");
            tc.add(t);

            JComboBox<String> s = box(AS_RULES, storage, pick.storageSide(), x -> x);
            s.setToolTipText(Ui2.tip("Сторона планшета ХРАНИЛИЩА: сколько ячеек и "
                + "под что они открываются."));
            form.add(s, "growx, wrap");
            sc.add(s);
        }
        JLabel note = new JLabel("<html>Колода, выбранная за столом, остальным местам "
            + "уже не достанется. На что колода не выбрана — раздаётся по сиду, как "
            + "раньше. Характер бота и поворот ЦУ применяются сразу.</html>");
        note.setFont(Theme.note(11));
        note.setForeground(Theme.ink3());
        form.add(note, "span 7, growx, gaptop " + Theme.px(6));
        form.setPreferredSize(new Dimension(Theme.px(820),
            form.getPreferredSize().height));

        Window owner = parent == null ? null
            : javax.swing.SwingUtilities.getWindowAncestor(parent);
        int ok = JOptionPane.showConfirmDialog(owner, form,
            "Игроки: характер, колода и планшеты",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return false;
        }
        boolean changed = false;
        for (int seat = 0; seat < players; seat++) {
            String deck = deckValue(dc.get(seat), decks);
            String t = value(tc.get(seat), AS_RULES);
            String s = value(sc.get(seat), AS_RULES);
            GameConfig.SeatPick was = seat < now.size() && now.get(seat) != null
                ? now.get(seat) : new GameConfig.SeatPick(null, null, null);
            if (!java.util.Objects.equals(was.orderColor(), deck)
                    || !java.util.Objects.equals(was.troopSide(), t)
                    || !java.util.Objects.equals(was.storageSide(), s)) {
                changed = true;
            }
            GameConfig.pickSeat(seat, t, s, deck);
        }
        return changed;
    }

    /**
     * РАЗДАТЬ КОЛОДЫ И СТОРОНЫ ПЛАНШЕТОВ СЛУЧАЙНО: каждому месту своя колода и
     * свои стороны планшетов.
     *
     * <p>Колоды берутся БЕЗ ПОВТОРОВ — двух одинаковых колод приказов за столом
     * не бывает, и выдать их значило бы собрать партию, которую нельзя сыграть.
     * Стороны планшетов повторяться могут: это разные планшеты у разных игроков.
     */
    public static void randomise(String rulesetId, int players, java.util.Random rng) {
        List<String> decks = new ArrayList<>(decks(rulesetId));
        List<String> troop = sides(rulesetId, "troop_side");
        List<String> storage = sides(rulesetId, "storage_side");
        java.util.Collections.shuffle(decks, rng);
        for (int seat = 0; seat < players; seat++) {
            String c = seat < decks.size() ? decks.get(seat) : null;
            String t = troop.isEmpty() ? null : troop.get(rng.nextInt(troop.size()));
            String s = storage.isEmpty() ? null : storage.get(rng.nextInt(storage.size()));
            GameConfig.pickSeat(seat, t, s, c);
        }
        // Лишние места чистим: иначе выбор от партии на четверых остался бы висеть
        // на игре вдвоём и колода ушла бы тому, кого за столом нет.
        for (int seat = players; seat < 4; seat++) {
            GameConfig.pickSeat(seat, null, null, null);
        }
    }

    /** Короткая сводка выбора для строки настройки: «Волк · Б1/A» и т. п. */
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
            if (p.troopSide() != null || p.storageSide() != null) {
                bits.add((p.troopSide() == null ? "—" : p.troopSide()) + "/"
                    + (p.storageSide() == null ? "—" : p.storageSide()));
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

    /** Стороны планшетов заданного вида из набора досок. */
    private static List<String> sides(String rulesetId, String kind) {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> e : entries(rulesetId, "boards")) {
            if (kind.equals(e.get("kind")) && e.get("side") != null) {
                out.add(String.valueOf(e.get("side")));
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
    private static JComboBox<String> deckBox(List<String> values, String chosen) {
        JComboBox<String> box = new JComboBox<>();
        box.setFont(Theme.body());
        box.addItem(AUTO);
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

    /** Обратное к {@link #deckBox}: индекс пункта → исходный код колоды. */
    private static String deckValue(JComboBox<String> box, List<String> values) {
        int idx = box.getSelectedIndex();
        return idx <= 0 || idx - 1 >= values.size() ? null : values.get(idx - 1);
    }

    private static JComboBox<String> box(String first, List<String> values, String chosen,
                                         java.util.function.Function<String, String> ru) {
        JComboBox<String> box = new JComboBox<>();
        box.setFont(Theme.body());
        box.addItem(first);
        for (String v : values) {
            box.addItem(v.equals(ru.apply(v)) ? v : v + " — " + ru.apply(v));
        }
        if (chosen != null) {
            for (int i = 1; i < box.getItemCount(); i++) {
                if (box.getItemAt(i).startsWith(chosen)) {
                    box.setSelectedIndex(i);
                }
            }
        }
        return box;
    }

    /** Выбранное значение: первый пункт — «не выбрано», остальное — до тире. */
    private static String value(JComboBox<String> box, String first) {
        Object o = box.getSelectedItem();
        if (o == null || first.equals(o)) {
            return null;
        }
        String s = String.valueOf(o);
        int cut = s.indexOf(" — ");
        return cut < 0 ? s : s.substring(0, cut);
    }

}
