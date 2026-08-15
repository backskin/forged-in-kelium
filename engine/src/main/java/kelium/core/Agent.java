package kelium.core;

import java.util.List;
import java.util.Map;


/**
 * Интерфейс агента (стратегии/политики).
 *
 * <p>В каждой точке решения движок предлагает агенту набор допустимых опций, а
 * агент возвращает одну из них. Модель «точек выбора» делает огромное условное
 * пространство действий управляемым, а маскирование недопустимых действий —
 * тривиальным. Эвристический бот работает уже сейчас, обучаемый (RL) агент
 * подключается позже без изменений движка (реализует тот же {@link #choose}).
 */
public abstract class Agent {

    public final int seat;
    public final String name;

    protected Agent(int seat, String name) {
        this.seat = seat;
        this.name = (name == null || name.isEmpty())
                ? getClass().getSimpleName() + "#" + seat : name;
    }

    /**
     * Вернуть одну опцию из {@code options}. {@code context} несёт метаданные
     * точки решения (вид, фаза и т. п.). Движок гарантирует легальность опций.
     */
    public abstract Choice choose(GameState state, List<Choice> options, Map<String, Object> context);

    /** Необязательная зацепка: движок присылает события телеметрии (обучение/логи). */
    public void observeEvent(Map<String, Object> event) {
        // по умолчанию ничего
    }

    /**
     * ВСЁ, ЧТО ПРОИСХОДИТ НА СТОЛЕ, — присылается КАЖДОМУ агенту, а не только
     * тому, чей это ход.
     *
     * <p>{@link #observeEvent} получает только «свои» события: движок находит в
     * событии номер места и уведомляет одного агента. Для памяти о происходящем
     * этого мало — жертва не узнавала, КТО её ударил, потому что в событии боя
     * стоит место АТАКУЮЩЕГО. А за столом удар видят все, это открытая
     * информация. Отсюда отдельная зацепка: она нужна ботам, которые помнят
     * обиды и мстят ({@code HumanLikeAgent}).
     */
    public void observePublicEvent(Map<String, Object> event) {
        // по умолчанию ничего
    }
}
