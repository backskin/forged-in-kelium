package kelium.agents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Choice;

/**
 * ПРИВЫЧКИ СОПЕРНИКОВ ПО ПРИКАЗАМ — то, что живой игрок держит в голове само.
 *
 * <p>Зачем. Приказы вскрываются ОДНОВРЕМЕННО и закрыто, а совпадение приказов
 * блокирует ход: замер показал, что так теряется 66% ходов. Значит выбор приказа —
 * это угадывание, что вскроют соседи. Наши боты не угадывали вовсе: в просчёте
 * соперники выбирали приказ заново своей формулой, то есть бот считал, что все
 * думают как он.
 *
 * <p>При этом ВСКРЫТЫЕ ПРИКАЗЫ — открытая информация: их видит весь стол, и
 * запоминать их не подглядывание, а обычная игра. Здесь копится простой счёт «кто
 * что вскрывал», и в просчёте соперник разыгрывается по своим привычкам, а не по
 * чужой формуле.
 *
 * <p>Счёт хранится по КАРТЕ приказа (её опознаваемое имя), потому что именно карту
 * игрок выбирает из руки. Сглаживание единицей: невиданная карта не считается
 * невозможной — соперник просто мог до неё не дойти.
 */
public final class OrderHabits {

    /** место → (карта приказа → сколько раз вскрывал). */
    private final Map<Integer, Map<String, Integer>> counts = new HashMap<>();
    private int seen = 0;

    /** Запомнить круг вскрытий (событие {@code reveal} движка). */
    public void note(Map<?, ?> revealed) {
        if (revealed == null) {
            return;
        }
        for (Map.Entry<?, ?> e : revealed.entrySet()) {
            if (!(e.getKey() instanceof Number seat) || e.getValue() == null) {
                continue;
            }
            counts.computeIfAbsent(seat.intValue(), k -> new HashMap<>())
                .merge(String.valueOf(e.getValue()), 1, Integer::sum);
            seen++;
        }
    }

    /** Сколько вскрытий уже запомнено (ноль — привычек ещё нет). */
    public int seen() {
        return seen;
    }

    /** Копия для просчёта: копия нужна, чтобы модель не портила настоящий счёт. */
    public OrderHabits copy() {
        OrderHabits c = new OrderHabits();
        counts.forEach((seat, m) -> c.counts.put(seat, new HashMap<>(m)));
        c.seen = seen;
        return c;
    }

    /**
     * Выбрать приказ за соперника ПО ПРИВЫЧКЕ: розыгрыш пропорционально тому, как
     * часто он эту карту вскрывал (плюс единица на всякую карту в руке).
     *
     * @return выбранный вариант или {@code null}, если привычек ещё нет и угадывать
     *         не из чего — тогда пусть решает обычная формула
     */
    public Choice pick(int seat, List<Choice> options, Random rng) {
        Map<String, Integer> mine = counts.get(seat);
        if (mine == null || mine.isEmpty() || options.isEmpty()) {
            return null;
        }
        int total = 0;
        int[] weight = new int[options.size()];
        for (int i = 0; i < options.size(); i++) {
            String id = String.valueOf(options.get(i).payload());
            weight[i] = 1 + mine.getOrDefault(id, 0);
            total += weight[i];
        }
        int roll = rng.nextInt(total);
        for (int i = 0; i < weight.length; i++) {
            roll -= weight[i];
            if (roll < 0) {
                return options.get(i);
            }
        }
        return options.get(options.size() - 1);
    }
}
