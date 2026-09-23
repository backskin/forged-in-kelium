package kelium.engine.step;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.engine.GameEngine;

/**
 * ПОШАГОВЫЙ API ДВИЖКА (23.09.2026): «позиция → чьё решение и какие варианты →
 * применить вариант». Всё, что нужно боту, который думает перебором ходов, а
 * не формулой.
 *
 * <p>Как устроено. Движок проигрывает позицию с её снимка, отвечая на вопросы
 * записанными номерами вариантов. Когда записанные кончаются, следующий вопрос
 * и есть «текущее решение»: прогон останавливается и отдаёт его наружу. Правила
 * не меняются ни в одной строке — работает тот же код розыгрыша, что в
 * настоящей партии.
 *
 * <p>Цена — повтор от начала круга (до четырёх ходов), единицы миллисекунд.
 */
public final class Шаг {

    private Шаг() {
    }

    /** Текущее решение позиции — или конец партии. */
    public record Развилка(boolean конец, int место, String вид, List<Choice> варианты,
                           Map<String, Object> обстановка, GameState стол) {
    }

    /** Сигнал «записанные решения кончились — вот следующий вопрос». */
    private static final class Стоп extends Error {
        final int место;
        final List<Choice> варианты;
        final Map<String, Object> обстановка;
        final GameState стол;

        Стоп(int место, List<Choice> варианты, Map<String, Object> обстановка, GameState стол) {
            super(null, null, false, false);
            this.место = место;
            this.варианты = варианты;
            this.обстановка = обстановка;
            this.стол = стол;
        }
    }

    /** Отвечает записанными номерами; кончились — останавливает прогон. */
    private static final class Повтор extends Agent {
        private final int[] курсор;
        private final List<Integer> ходы;

        Повтор(int seat, int[] курсор, List<Integer> ходы) {
            super(seat, "повтор#" + seat);
            this.курсор = курсор;
            this.ходы = ходы;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            if (курсор[0] >= ходы.size()) {
                throw new Стоп(seat, new ArrayList<>(options), ctx, state);
            }
            int i = ходы.get(курсор[0]++);
            if (i < 0 || i >= options.size()) {
                throw new IllegalStateException("повтор разошёлся с партией: вариант " + i
                    + " из " + options.size() + " на решении " + (курсор[0] - 1));
            }
            return options.get(i);
        }
    }

    /**
     * ОБРЫВ РОЗЫГРЫША: агент продолжения бросает его, когда розыгрыш дальше вести
     * незачем (достигнут горизонт). {@link #прогнать} ловит его и возвращает стол
     * в этот миг.
     */
    public static final class Обрыв extends Error {
        public Обрыв() {
            super(null, null, false, false);
        }
    }

    /**
     * Проиграть позицию и ПРОДОЛЖИТЬ партию дальше. Записанные решения
     * повторяются; в миг, когда они кончились (корень), один раз зовётся
     * {@code наКорне} — например, перемешать скрытое, — а все решения после
     * корня принимают агенты {@code послеКорня} (по одному на место).
     *
     * @return стол в конце партии или в миг {@link Обрыв}
     */
    public static GameState прогнать(Позиция позиция,
                                     java.util.function.Consumer<GameState> наКорне,
                                     List<Agent> послеКорня) {
        GameState c = позиция.снимок.exactCopy();
        int[] курсор = {0};
        boolean[] корень = {false};
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < c.numPlayers(); i++) {
            final int место = i;
            agents.add(new Agent(i, "продолжение#" + i) {
                @Override
                public Choice choose(GameState state, List<Choice> options,
                                     Map<String, Object> ctx) {
                    if (курсор[0] < позиция.ходы.size()) {
                        int k = позиция.ходы.get(курсор[0]++);
                        if (k < 0 || k >= options.size()) {
                            throw new IllegalStateException("повтор разошёлся с партией");
                        }
                        return options.get(k);
                    }
                    if (!корень[0]) {
                        корень[0] = true;
                        if (наКорне != null) {
                            наКорне.accept(state);
                        }
                    }
                    return послеКорня.get(место).choose(state, options, ctx);
                }
            });
        }
        GameEngine engine = new GameEngine(c, agents, null);
        try {
            engine.resume();
        } catch (Обрыв обрыв) {
            return c;
        }
        return c;
    }

    /**
     * Проиграть позицию и остановиться на следующем решении.
     *
     * <p>Стол в ответе — живая копия в миг вопроса: из неё можно читать
     * обстановку, но продолжать её нельзя (прогон уже свёрнут). Продолжение —
     * это {@link Позиция#применить} и новый вызов.
     */
    public static Развилка достать(Позиция позиция) {
        GameState c = позиция.снимок.exactCopy();
        int[] курсор = {0};
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < c.numPlayers(); i++) {
            agents.add(new Повтор(i, курсор, позиция.ходы));
        }
        GameEngine engine = new GameEngine(c, agents, null);
        try {
            engine.resume();
        } catch (Стоп стоп) {
            String вид = стоп.обстановка == null ? ""
                : String.valueOf(стоп.обстановка.getOrDefault("kind", ""));
            return new Развилка(false, стоп.место, вид, стоп.варианты, стоп.обстановка, стоп.стол);
        }
        return new Развилка(true, -1, "", List.of(), Map.of(), c);
    }
}
