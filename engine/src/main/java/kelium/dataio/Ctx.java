package kelium.dataio;

import kelium.core.GameState;
import kelium.rules.Ruleset;

/**
 * Ctx — ШОВ между незыблемым ядром и версионным слоем данных.
 *
 * <p>{@link GameState#config} объявлен как {@code Object} нарочно: ядро не
 * должно знать про правила и карты, иначе версионный слой протечёт в модель
 * игры. Плата за это — приведение типа в каждом месте, где нужны правила, и
 * таких мест было около сорока: {@code ((GameConfig) state.config).ruleset}.
 * Одна опечатка — и падение в глубине действия.
 *
 * <p>Здесь это приведение сделано ОДИН раз и с понятной ошибкой, если состояние
 * собрано неправильно. Ядро по-прежнему ничего не знает про {@code dataio} —
 * знает наоборот.
 */
public final class Ctx {

    private Ctx() {
    }

    /** Конфигурация партии из состояния. */
    public static GameConfig cfg(GameState state) {
        if (state == null || !(state.config instanceof GameConfig c)) {
            throw new IllegalStateException(
                "состояние партии собрано без конфигурации — правила недоступны");
        }
        return c;
    }

    /** Правила партии (значения и переключатели версии). */
    public static Ruleset rules(GameState state) {
        return cfg(state).ruleset;
    }

    /** Набор карт указанного типа для этой партии. */
    public static ContentSet cards(GameState state, String contentType) {
        return cfg(state).content.get(contentType);
    }

    /** Вся библиотека контента партии. */
    public static ContentLibrary content(GameState state) {
        return cfg(state).content;
    }
}
