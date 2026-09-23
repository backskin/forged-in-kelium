package kelium.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.engine.Scoring;
import kelium.engine.step.Летопись;
import kelium.engine.step.Позиция;
import kelium.engine.step.Шаг;

/**
 * ПОИСК — бот, который решает перебором ходов, а не формулой (23.09.2026).
 *
 * <p>На каждом своём решении бот берёт текущую позицию партии из
 * {@link Летопись} и многократно разыгрывает её вперёд через пошаговый API
 * ({@link Шаг#прогнать}). Каждый розыгрыш:
 * <ol>
 *   <li>перемешивает то, чего бот не видит (колоды, карты соперников на руке) —
 *       бот не подглядывает;</li>
 *   <li>спускается по дереву решений: где варианты уже пробовали — берёт
 *       лучший с поправкой на недоисследованность (UCB), где нет — пробует
 *       новый; решают ВСЕ игроки, каждый за себя;</li>
 *   <li>дальше доигрывает случайными (но не пасующими) ходами до горизонта;</li>
 *   <li>итог — настоящий счёт игры: отрыв своих очков от лучшего соперника,
 *       у каждого игрока свой.</li>
 * </ol>
 * Выбирается вариант, который пробовали чаще всех: поиск сам тратит розыгрыши на
 * то, что выглядит лучше.
 *
 * <p>Здесь нет ни одного веса и ни одной оценочной формулы: судья — очки.
 * Длинные розыгрыши потом заменит нейросеть, обученная игрой сама с собой.
 */
public final class Поиск extends Agent {

    private final Летопись летопись;
    private final Random rng;
    /** Розыгрышей на решение. */
    public int розыгрышей = 64;
    /** Горизонт: сколько кругов после корня доигрывать. */
    public int горизонтКругов = 4;
    /** Сила исследования в UCB. */
    public double исследование = 0.7;
    /** Сколько ядер тратить на розыгрыши одного решения. */
    public int потоков = 1;
    /**
     * Обученная оценка позиции. Есть — розыгрыш, оборванный на горизонте,
     * судится сетью; нет — отрывом в очках на миг обрыва.
     */
    public kelium.agents.сеть.Сеть сеть;

    // ---- телеметрия (для прозрачности) ----
    public long решений;
    public long розыгрышейВсего;
    public long сорвалось;
    /** Чем кончались розыгрыши: [конец партии, горизонт, страховка от петель]. */
    public final long[] концы = new long[3];
    /** Сколько решений в среднем занимал розыгрыш (сумма). */
    public long решенийВРозыгрышах;
    /** Последнее решение: вариант → [посещений, средний итог]. Для проигрывателя. */
    public final Map<String, double[]> последнее = new java.util.LinkedHashMap<>();

    public Поиск(int seat, Летопись летопись, long seed) {
        super(seat, "поиск#" + seat);
        this.летопись = летопись;
        this.rng = new Random(seed);
    }

    /** Узел: чьё решение и что из него пробовали. */
    private static final class Узел {
        final int место;
        final Map<String, Ребро> рёбра = new HashMap<>();

        Узел(int место) {
            this.место = место;
        }
    }

    /** Ребро: вариант решения и накопленные итоги по всем местам. */
    private static final class Ребро {
        int посещений;
        int доступен;         // сколько раз вариант был доступен (ISMCTS)
        final double[] сумма;
        Узел ребёнок;

        Ребро(int мест) {
            сумма = new double[мест];
        }
    }

    private static String ключ(Choice c) {
        return c.kind() + "|" + (c.label() != null ? c.label() : String.valueOf(c.payload()));
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
        if (options.size() == 1) {
            return options.get(0);
        }
        Позиция корень = летопись == null ? null : летопись.сейчас();
        if (корень == null) {
            return неПасовать(options, rng);
        }
        решений++;
        int мест = state.numPlayers();
        // ПАРАЛЛЕЛЬНО ПО КОРНЮ: каждое ядро растит своё дерево, в корне счёт
        // складывается. Деревья не делят память — блокировок нет.
        int ядер = Math.max(1, Math.min(потоков, розыгрышей));
        List<Узел> деревья = new ArrayList<>();
        for (int k = 0; k < ядер; k++) {
            деревья.add(new Узел(seat));
        }
        long[] сиды = new long[ядер];
        for (int k = 0; k < ядер; k++) {
            сиды[k] = rng.nextLong();
        }
        java.util.stream.IntStream.range(0, ядер).parallel().forEach(k -> {
            Random свой = new Random(сиды[k]);
            for (int i = k; i < розыгрышей; i += ядер) {
                розыгрыш(корень, деревья.get(k), мест, свой);
            }
        });
        // ВЫБОР ПО СРЕДНЕМУ ИТОГУ, а не по числу посещений. Деревьев столько же,
        // сколько ядер, и в каждом всего десяток-другой розыгрышей: посещения
        // вариантов там почти равны, и «самый посещаемый» — это жребий. Средний
        // итог по всем деревьям разом опирается на все розыгрыши. Вариант,
        // который почти не пробовали, среднему не верит: нужно хотя бы 4
        // розыгрыша.
        Choice лучший = null;
        double лучшеСреднее = Double.NEGATIVE_INFINITY;
        int лучшеПосещений = -1;
        последнее.clear();
        for (Choice o : options) {
            int пос = 0;
            double сумма = 0;
            for (Узел д : деревья) {
                Ребро р = д.рёбра.get(ключ(o));
                if (р != null) {
                    пос += р.посещений;
                    сумма += р.сумма[seat];
                }
            }
            double ср = пос == 0 ? 0 : сумма / пос;
            последнее.put(ключ(o), new double[]{пос, ср});
            boolean надёжно = пос >= 4;
            if (надёжно && (ср > лучшеСреднее || (ср == лучшеСреднее && пос > лучшеПосещений))) {
                лучшеСреднее = ср;
                лучшеПосещений = пос;
                лучший = o;
            }
        }
        if (лучший == null) {
            // ни один вариант не набрал 4 розыгрыша — берём самый посещаемый
            for (Choice o : options) {
                double[] д = последнее.get(ключ(o));
                if (д[0] > лучшеПосещений) {
                    лучшеПосещений = (int) д[0];
                    лучший = o;
                }
            }
        }
        return лучший;
    }

    /** Один розыгрыш: спуск по дереву, доигрывание, обратный ход итога. */
    private void розыгрыш(Позиция корень, Узел дерево, int мест, Random свой) {
        List<Ребро> путь = new ArrayList<>();
        // Где мы в дереве: узел текущего решения; null при ждём != null — узел
        // ещё не создан (чьё решение, станет ясно на следующем вопросе).
        Узел[] узел = {дерево};
        Ребро[] ждём = {null};
        boolean[] вДереве = {true};
        int[] старт = {-1};
        int[] решенийВРозыгрыше = {0};
        Random r = new Random(свой.nextLong());
        List<Agent> продолжение = new ArrayList<>();
        for (int i = 0; i < мест; i++) {
            final int место = i;
            продолжение.add(new Agent(i, "розыгрыш#" + i) {
                @Override
                public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                    int кругов = Math.max(1, kelium.dataio.Ctx.rules(st)
                        .getInt("rounds.circles_per_round"));
                    int сейчас = st.round * кругов + st.circle;
                    if (старт[0] < 0) {
                        старт[0] = сейчас;
                    }
                    // Горизонт и страховка от бесконечных петель случайной игры.
                    if (сейчас - старт[0] >= горизонтКругов) {
                        synchronized (концы) { концы[1]++; }
                        throw new Шаг.Обрыв();
                    }
                    if (++решенийВРозыгрыше[0] > 4000) {
                        synchronized (концы) { концы[2]++; }
                        throw new Шаг.Обрыв();
                    }
                    if (options.size() == 1) {
                        return options.get(0);
                    }
                    if (!вДереве[0]) {
                        return неПасовать(options, r);
                    }
                    Узел у = узел[0];
                    if (у == null && ждём[0] != null) {
                        у = new Узел(место);
                        ждём[0].ребёнок = у;
                    }
                    if (у == null || у.место != место) {
                        вДереве[0] = false;      // дерево от другой раздачи — дальше случайно
                        return неПасовать(options, r);
                    }
                    List<Choice> новые = new ArrayList<>();
                    for (Choice o : options) {
                        Ребро р = у.рёбра.get(ключ(o));
                        if (р == null) {
                            новые.add(o);
                        } else {
                            р.доступен++;
                        }
                    }
                    Choice выбор = null;
                    Ребро ребро = null;
                    if (!новые.isEmpty()) {
                        выбор = новые.get(r.nextInt(новые.size()));
                        ребро = new Ребро(мест);
                        ребро.доступен = 1;
                        у.рёбра.put(ключ(выбор), ребро);
                        вДереве[0] = false;      // расширили дерево — дальше доигрывание
                    } else {
                        double лучший = Double.NEGATIVE_INFINITY;
                        for (Choice o : options) {
                            Ребро р = у.рёбра.get(ключ(o));
                            double ср = р.посещений == 0 ? 0 : р.сумма[место] / р.посещений;
                            double ucb = ср + исследование
                                * Math.sqrt(Math.log(Math.max(2, р.доступен)) / (1 + р.посещений));
                            if (ucb > лучший) {
                                лучший = ucb;
                                выбор = o;
                                ребро = р;
                            }
                        }
                    }
                    путь.add(ребро);
                    узел[0] = ребро.ребёнок;
                    ждём[0] = ребро;
                    return выбор;
                }
            });
        }
        GameState итог;
        try {
            итог = Шаг.прогнать(корень, st -> Доигрывание.перемешатьСкрытое(st, seat, r),
                продолжение);
        } catch (RuntimeException e) {
            synchronized (концы) { сорвалось++; }
            return;
        }
        synchronized (концы) {
            розыгрышейВсего++;
            решенийВРозыгрышах += решенийВРозыгрыше[0];
            if (итог.finished) {
                концы[0]++;
            }
        }
        double[] очки = итог.finished || сеть == null ? итоги(итог, мест) : поСети(итог, мест);
        for (Ребро р : путь) {
            р.посещений++;
            for (int k = 0; k < мест; k++) {
                р.сумма[k] += очки[k];
            }
        }
    }

    /** Оборванный розыгрыш глазами сети: ожидаемый итог каждого места. */
    private double[] поСети(GameState s, int мест) {
        double[] out = new double[мест];
        for (int i = 0; i < мест; i++) {
            out[i] = сеть.оценить(kelium.agents.сеть.Кодировщик.закодировать(s, i));
        }
        return out;
    }

    /** Итог розыгрыша по местам: отрыв своих очков от лучшего соперника, /10. */
    public static double[] итоги(GameState s, int мест) {
        int[] очки = new int[мест];
        for (int i = 0; i < мест; i++) {
            очки[i] = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
        }
        double[] out = new double[мест];
        for (int i = 0; i < мест; i++) {
            int лучшийДругой = Integer.MIN_VALUE;
            for (int k = 0; k < мест; k++) {
                if (k != i) {
                    лучшийДругой = Math.max(лучшийДругой, очки[k]);
                }
            }
            out[i] = Math.max(-1, Math.min(1, (очки[i] - лучшийДругой) / 10.0));
        }
        return out;
    }

    /** Случайный ход, но не пас, если есть что делать. */
    static Choice неПасовать(List<Choice> options, Random r) {
        List<Choice> дело = new ArrayList<>();
        for (Choice o : options) {
            if (!"pass".equals(o.kind()) && o.payload() != null) {
                дело.add(o);
            }
        }
        List<Choice> из = дело.isEmpty() ? options : дело;
        return из.get(r.nextInt(из.size()));
    }
}
