package kelium.agents;

import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;

/**
 * PassiveAgent — «манекен»: не строит, не ходит, не бьёт по своей воле.
 *
 * <p>Нужен сценариям вроде {@link kelium.SoloWarDrill}, где на столе сидит один
 * настоящий бот, а остальные места существуют только затем, чтобы держать
 * заранее расставленные жетоны-мишени. Двигатель партии всё равно обязан
 * спросить агента в каждой точке решения, поэтому просто «не давать хода»
 * нельзя — вместо этого манекен всегда берёт вариант «пропустить», а если
 * пропуска нет (например на вскрытии приказа, где пропуска не бывает) — берёт
 * первый вариант из списка, детерминированно, без ГСЧ.
 */
public final class PassiveAgent extends Agent {

    public PassiveAgent(int seat) {
        super(seat, "passive#" + seat);
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        for (Choice o : options) {
            if ("pass".equals(o.kind())) {
                return o;
            }
        }
        return options.get(0);
    }
}
