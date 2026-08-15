package kelium.engine;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Учёт в пределах одного хода игрока внутри круга.
 *
 * <p>Хранит номер места (seat), использование SPEC-действий и лимит, счётчики
 * операций для наценок, множество уже сыгранных действий приказа и флаг
 * использования бесплатного хода ЦУ.
 */
public final class TurnContext {

    public final int seat;
    public int specUsed = 0;
    public int specLimit = 1;
    // счётчики операций по имени действия -> сколько раз выполнена «операция»
    public final Map<String, Integer> opCounts = new HashMap<>();
    // действия приказа, уже сыгранные в этот ход (каждое по разу). LinkedHashSet,
    // а не HashSet, — порядок нужен проигрывателю: карта БЕЗОПАСНОСТЬ (джокер)
    // показывает, какие именно два действия из восьми выбрал игрок, и делает
    // это в том порядке, в каком их разыграли.
    public final Set<String> actionsPlayed = new LinkedHashSet<>();
    // использован ли бесплатный ход ЦУ?
    public boolean cuFreeMoveUsed = false;

    public TurnContext(int seat, int specLimit) {
        this.seat = seat;
        this.specLimit = specLimit;
    }

    /** Осталось ли ещё разрешённое SPEC-действие в этот ход. */
    public boolean canSpec() {
        return specUsed < specLimit;
    }

    /** Отметить использование одного SPEC-действия; ошибка при превышении лимита. */
    public void useSpec() {
        if (!canSpec()) {
            throw new IllegalStateException("превышен лимит SPEC-действий");
        }
        specUsed++;
    }

    /**
     * Наценка за СЛЕДУЮЩУЮ операцию {@code action} по расписанию вида [0,1,2,3];
     * при выходе за пределы берётся последний элемент.
     */
    public int nextOpSurcharge(String action, List<Integer> schedule) {
        int i = opCounts.getOrDefault(action, 0);
        return schedule.get(Math.min(i, schedule.size() - 1));
    }

    /** Учесть проведённую операцию действия {@code action} (для роста наценки). */
    public void recordOp(String action) {
        opCounts.merge(action, 1, Integer::sum);
    }
}
