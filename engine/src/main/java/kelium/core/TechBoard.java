package kelium.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Общая доска науки из 3 треков. occupancy[трек][шаг] = список мест игроков.
 */
public final class TechBoard {

    public final List<String> tracks;
    public final int steps;
    // occupancy[trackId] = список по шагам: список мест, стоящих на этом шаге.
    public final Map<String, List<List<Integer>>> occupancy;
    public final Map<String, Boolean> firstArriverClaimed;
    /**
     * СКОЛЬКО ИГРОКОВ ВСЕГО ПОБЫВАЛО НА ШАГЕ 1 каждого трека. Нужно призу шага 1
     * («первому полный, второму половина»): сам шаг игрок ПОКИДАЕТ, двигая свой
     * кубик дальше, поэтому по числу стоящих на шаге очередь прихода не
     * определить (уточнение дизайнера 12.08.2026: кубик у игрока ОДИН на трек и
     * он его переставляет, а не занимает новые ячейки).
     */
    public final Map<String, Integer> stepOneArrivals = new HashMap<>();

    public TechBoard(List<String> tracks, int steps,
                     Map<String, List<List<Integer>>> occupancy,
                     Map<String, Boolean> firstArriverClaimed) {
        this.tracks = tracks;
        this.steps = steps;
        this.occupancy = occupancy;
        this.firstArriverClaimed = firstArriverClaimed;
    }

    /** Точная копия доски науки (для копии состояния при просчёте вперёд). */
    public TechBoard copy() {
        Map<String, List<List<Integer>>> occ = new HashMap<>();
        for (Map.Entry<String, List<List<Integer>>> e : occupancy.entrySet()) {
            List<List<Integer>> perStep = new ArrayList<>();
            for (List<Integer> seats : e.getValue()) {
                perStep.add(new ArrayList<>(seats));
            }
            occ.put(e.getKey(), perStep);
        }
        TechBoard c = new TechBoard(new ArrayList<>(tracks), steps, occ,
            new HashMap<>(firstArriverClaimed));
        c.stepOneArrivals.putAll(stepOneArrivals);
        return c;
    }

    /**
     * ПЕРЕСТАВИТЬ КУБИК игрока по треку: он покидает прежний шаг и встаёт на
     * новый. Кубик у игрока ОДИН на трек — занимать несколько ячеек он не может.
     *
     * @param fromStep шаг, на котором кубик стоял (0 — стартовая зона)
     * @param toStep   шаг, на который встаёт (1..steps)
     */
    public void moveCube(String track, int seat, int fromStep, int toStep) {
        List<List<Integer>> perStep = occupancy.get(track);
        if (perStep == null) {
            return;
        }
        if (fromStep >= 1 && fromStep <= perStep.size()) {
            perStep.get(fromStep - 1).remove(Integer.valueOf(seat));
        }
        if (toStep >= 1 && toStep <= perStep.size()) {
            perStep.get(toStep - 1).add(seat);
        }
        if (toStep == 1) {
            stepOneArrivals.merge(track, 1, Integer::sum);
        }
    }

    /**
     * ВЫЛОЖИТЬ НОВЫЙ КУБИК на шаг: прежние ячейки игрока НЕ освобождаются
     * (заказ дизайнера 02.09.2026, ключ свода {@code tech.cubes_are_permanent}).
     * Стартовой ячейки «на нуле» нет — кубики лежат в личном запасе игрока, и
     * каждый купленный шаг забирает из запаса один кубик навсегда. Поэтому
     * ячейки на треке больше не освобождаются, и трек становится гонкой за
     * места, а запас кубиков — пределом всей науки за партию.
     *
     * <p>{@link #moveCube} оставлен для сводов до 1.33.0: там кубик один на
     * трек и он переставляется.
     */
    public void placeCube(String track, int seat, int step) {
        List<List<Integer>> perStep = occupancy.get(track);
        if (perStep == null || step < 1 || step > perStep.size()) {
            return;
        }
        perStep.get(step - 1).add(seat);
        if (step == 1) {
            stepOneArrivals.merge(track, 1, Integer::sum);
        }
    }

    /** Каким по счёту игрок пришёл на шаг 1 этого трека (1 = первым). */
    public int stepOneRank(String track) {
        return stepOneArrivals.getOrDefault(track, 0);
    }

    /** Создать пустую доску науки по списку треков и числу шагов в каждом. */
    public static TechBoard create(List<String> trackIds, int steps) {
        Map<String, List<List<Integer>>> occ = new HashMap<>();
        Map<String, Boolean> claimed = new HashMap<>();
        for (String t : trackIds) {
            List<List<Integer>> perStep = new ArrayList<>();
            for (int i = 0; i < steps; i++) {
                perStep.add(new ArrayList<>());
            }
            occ.put(t, perStep);
            claimed.put(t, false);
        }
        return new TechBoard(new ArrayList<>(trackIds), steps, occ, claimed);
    }

    /** Занят ли кем-либо верхний (последний) шаг указанного трека. */
    public boolean peakOccupied(String track) {
        return !occupancy.get(track).get(steps - 1).isEmpty();
    }

    /** Заняты ли верхние шаги всех треков (условие окончания по науке). */
    public boolean allPeaksOccupied() {
        for (String t : tracks) {
            if (!peakOccupied(t)) {
                return false;
            }
        }
        return true;
    }
}
