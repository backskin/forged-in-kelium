package kelium.agents;

import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.GameState;
import kelium.core.Agent;
import kelium.core.Choice;

/**
 * RandomAgent — выбирает равновероятно среди допустимых опций.
 *
 * <p>Базовый бот для дымового тестирования (что партии проходят от начала до
 * конца) и контрольный оппонент для последующего измерения силы эвристики/RL.
 * Детерминирован при заданном сиде ГСЧ.
 */
public final class RandomAgent extends Agent {

    private final Random rng;

    public RandomAgent(int seat, Random rng, String name) {
        super(seat, name == null || name.isEmpty() ? "Random#" + seat : name);
        this.rng = rng != null ? rng : new Random();
    }

    public RandomAgent(int seat, Random rng) {
        this(seat, rng, null);
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        return options.get(rng.nextInt(options.size()));
    }
}
