package kelium.engine;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.rules.Ruleset;
import kelium.core.Agent;
import kelium.dataio.Ctx;

/**
 * Базовый класс для 8 действий. Читает правила из ruleset игрового состояния.
 *
 * <p>Каждое действие кодирует ПРОЦЕДУРУ, а не баланс: все настраиваемые числа
 * берутся из {@link Ruleset}. Частичный/ранний набор действий допустим: каждое
 * действие сообщает через {@link #implemented}, реализовано ли оно полностью.
 */
public abstract class Action {

    protected final GameState state;
    protected final Ruleset rs;

    public String name() {
        return "abstract";
    }

    public Order order() {
        return null;
    }

    public boolean implemented() {
        return false;
    }

    protected Action(GameState state) {
        this.state = state;
        this.rs = Ctx.rules(state);
    }

    /** Дешёвая проверка: стоит ли вообще предлагать это действие. */
    public boolean legal(PlayerState player, TurnContext ctx) {
        return !ctx.actionsPlayed.contains(name());
    }

    /** Выполнить действие (переопределяется в наследниках). */
    public abstract ActionResult perform(PlayerState player, TurnContext ctx, Agent agent);
}
