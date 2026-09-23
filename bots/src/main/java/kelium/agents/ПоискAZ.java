package kelium.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.сеть.Кодировщик;
import kelium.agents.сеть.Сеть;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.engine.step.Летопись;
import kelium.engine.step.Позиция;
import kelium.engine.step.Шаг;

/**
 * БОТ ALPHAZERO (23.09.2026): поиск по дереву, которым ведут две сети.
 *
 * <p>На каждом своём решении бот делает {@link #симуляций} спусков по дереву
 * решений из текущей позиции партии (пошаговый API, {@link Шаг#прогнать}).
 * Каждый спуск:
 * <ol>
 *   <li>перемешивает то, чего бот не видит (колоды, карты соперников на руке);</li>
 *   <li>идёт по дереву: в каждом узле берёт вариант с лучшей суммой «средний итог
 *       + совет сети ходов, поделённый на число проб» (PUCT); решают все
 *       игроки, каждый за себя;</li>
 *   <li>дойдя до нового решения, раскрывает его: сеть ходов даёт каждому
 *       варианту долю доверия, сеть оценки — ожидаемый итог партии для каждого
 *       игрока; этот итог и уходит вверх по пути.</li>
 * </ol>
 * Ход — самый посещаемый вариант корня. Пока сеть оценки молодая, итог
 * новой позиции — смесь её оценки и доигрывания до конца (см.
 * {@link #доляДоигрывания}).
 *
 * <p>Без сетей бот тоже играет: доверие ко всем вариантам поровну, а оценка —
 * текущий отрыв в очках. С этого и начинается самоигра — дальше сети учатся на
 * его же партиях: сеть ходов — на том, какие варианты поиск проверял чаще,
 * сеть оценки — на итогах.
 */
public final class ПоискAZ extends Agent {

    private final Летопись летопись;
    private final Random rng;
    public Сеть оценка;
    public Сеть ходы;
    public int симуляций = 64;
    public double cPuct = 1.5;
    /** Самоигра: шум Дирихле в корне и выбор хода жребием по посещениям в начале партии. */
    public boolean самоигра = false;
    /** До какого раунда в самоигре ход выбирается жребием. */
    public int раундовЖребия = 2;
    public int потоков = 1;
    /**
     * ДОЛЯ ДОИГРЫВАНИЯ в оценке новой позиции (как у AlphaGo): итог = (1 − λ)·сеть
     * + λ·доигрывание до конца случайными ходами. Пока сеть молодая, доигрывание
     * не даёт игре скатиться в пустоту; без сети λ = 1.
     */
    public double доляДоигрывания = 0.5;

    /** Последний корень: ключ варианта → посещений (для записи самоигры). */
    public final Map<String, Integer> последнийКорень = new java.util.LinkedHashMap<>();
    public long решений;
    public long спусков;
    public long сорвалось;

    public ПоискAZ(int seat, Летопись летопись, long seed) {
        super(seat, "az#" + seat);
        this.летопись = летопись;
        this.rng = new Random(seed);
    }

    private static final class Узел {
        final int место;
        boolean раскрыт;
        int N;
        final Map<String, Ребро> рёбра = new HashMap<>();

        Узел(int место) {
            this.место = место;
        }
    }

    private static final class Ребро {
        float P;
        int N;
        final double[] W;
        Узел ребёнок;

        Ребро(float P, int мест) {
            this.P = P;
            this.W = new double[мест];
        }
    }

    public static String ключ(Choice c) {
        return c.kind() + "|" + (c.label() != null ? c.label() : String.valueOf(c.payload()));
    }

    private static String вид(Map<String, Object> ctx) {
        return ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
        if (options.size() == 1) {
            return options.get(0);
        }
        Позиция корень = летопись == null ? null : летопись.сейчас();
        if (корень == null) {
            return options.get(rng.nextInt(options.size()));
        }
        решений++;
        int мест = state.numPlayers();
        int ядер = Math.max(1, Math.min(потоков, симуляций));
        List<Узел> деревья = new ArrayList<>();
        for (int k = 0; k < ядер; k++) {
            Узел д = new Узел(seat);
            раскрыть(д, state, seat, options, ctx, мест, null);
            if (самоигра) {
                шумДирихле(д, new Random(rng.nextLong()));
            }
            деревья.add(д);
        }
        long[] сиды = new long[ядер];
        for (int k = 0; k < ядер; k++) {
            сиды[k] = rng.nextLong();
        }
        if (ядер == 1) {
            Random r = new Random(сиды[0]);
            for (int i = 0; i < симуляций; i++) {
                спуск(корень, деревья.get(0), мест, r);
            }
        } else {
            java.util.stream.IntStream.range(0, ядер).parallel().forEach(k -> {
                Random r = new Random(сиды[k]);
                for (int i = k; i < симуляций; i += ядер) {
                    спуск(корень, деревья.get(k), мест, r);
                }
            });
        }
        последнийКорень.clear();
        int[] посещений = new int[options.size()];
        for (int i = 0; i < options.size(); i++) {
            String к = ключ(options.get(i));
            for (Узел д : деревья) {
                Ребро р = д.рёбра.get(к);
                if (р != null) {
                    посещений[i] += р.N;
                }
            }
            последнийКорень.put(к, посещений[i]);
        }
        int выбор = 0;
        if (самоигра && state.round <= раундовЖребия) {
            int всего = 0;
            for (int n : посещений) {
                всего += n;
            }
            if (всего > 0) {
                int жребий = rng.nextInt(всего);
                for (int i = 0; i < посещений.length; i++) {
                    жребий -= посещений[i];
                    if (жребий < 0) {
                        выбор = i;
                        break;
                    }
                }
            }
        } else {
            for (int i = 1; i < посещений.length; i++) {
                if (посещений[i] > посещений[выбор]) {
                    выбор = i;
                }
            }
        }
        return options.get(выбор);
    }

    /** Раскрыть узел: доверие к вариантам по сети ходов; вернуть оценку позиции по местам. */
    private double[] раскрыть(Узел у, GameState st, int место, List<Choice> options,
                              Map<String, Object> ctx, int мест, double[] уже) {
        float[] P = доверие(st, место, options, ctx);
        for (int i = 0; i < options.size(); i++) {
            String к = ключ(options.get(i));
            if (!у.рёбра.containsKey(к)) {
                у.рёбра.put(к, new Ребро(P[i], мест));
            }
        }
        у.раскрыт = true;
        return уже != null ? уже : оценить(st, мест);
    }

    private float[] доверие(GameState st, int место, List<Choice> options, Map<String, Object> ctx) {
        if (ходы == null) {
            float[] P = new float[options.size()];
            java.util.Arrays.fill(P, 1f / options.size());
            return P;
        }
        float[] стол = Кодировщик.закодировать(st, место);
        String в = вид(ctx);
        float[][] входы = new float[options.size()][];
        for (int i = 0; i < options.size(); i++) {
            входы[i] = Кодировщик.вход(стол, Кодировщик.вариант(st, место, в, options.get(i)));
        }
        return ходы.вероятности(входы, 1f);
    }

    /** Ожидаемый итог партии по местам: сеть оценки, а без неё — текущий отрыв в очках. */
    private double[] оценить(GameState st, int мест) {
        if (st.finished || оценка == null) {
            return Поиск.итоги(st, мест);
        }
        double[] v = new double[мест];
        for (int k = 0; k < мест; k++) {
            v[k] = оценка.оценить(Кодировщик.закодировать(st, k));
        }
        return v;
    }

    private static void шумДирихле(Узел у, Random r) {
        int n = у.рёбра.size();
        double[] шум = new double[n];
        double сумма = 0;
        for (int i = 0; i < n; i++) {
            шум[i] = гамма(0.3, r);
            сумма += шум[i];
        }
        int i = 0;
        for (Ребро р : у.рёбра.values()) {
            р.P = (float) (0.75 * р.P + 0.25 * шум[i++] / сумма);
        }
    }

    /** Гамма-распределение (Марсалья–Цан) — для шума Дирихле. */
    private static double гамма(double k, Random r) {
        if (k < 1) {
            return гамма(k + 1, r) * Math.pow(r.nextDouble(), 1 / k);
        }
        double d = k - 1.0 / 3, c = 1 / Math.sqrt(9 * d);
        while (true) {
            double x = r.nextGaussian(), v = 1 + c * x;
            if (v <= 0) {
                continue;
            }
            v = v * v * v;
            double u = r.nextDouble();
            if (Math.log(u) < 0.5 * x * x + d - d * v + d * Math.log(v)) {
                return d * v;
            }
        }
    }

    /** Один спуск по дереву до нового решения (или конца партии) и обратный ход оценки. */
    private void спуск(Позиция корень, Узел дерево, int мест, Random r) {
        List<Узел> узлы = new ArrayList<>();
        List<Ребро> рёбра = new ArrayList<>();
        Узел[] текущий = {дерево};
        Ребро[] ждём = {null};
        double[][] лист = {null};
        // Без сети оценки новая позиция судится доигрыванием до конца партии
        // (исходный MCTS, только для первого поколения самоигры).
        boolean[] доигрывание = {false};
        double[][] сеть = {null};
        int[] решенийДоигрывания = {0};
        List<Agent> агенты = new ArrayList<>();
        for (int i = 0; i < мест; i++) {
            final int место = i;
            агенты.add(new Agent(i, "спуск#" + i) {
                @Override
                public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                    if (options.size() == 1) {
                        return options.get(0);
                    }
                    if (доигрывание[0]) {
                        if (++решенийДоигрывания[0] > 4000) {
                            throw new Шаг.Обрыв();
                        }
                        return Поиск.неПасовать(options, r);
                    }
                    Узел у = текущий[0];
                    if (у == null) {
                        у = new Узел(место);
                        ждём[0].ребёнок = у;
                    }
                    if (у.место != место || !у.раскрыт) {
                        // новая позиция (или дерево от другой раздачи) — оцениваем
                        if (у.место == место) {
                            раскрыть(у, st, место, options, ctx, мест, new double[мест]);
                        }
                        double λ = оценка == null ? 1 : доляДоигрывания;
                        if (λ < 1) {
                            сеть[0] = оценить(st, мест);
                        }
                        if (λ <= 0) {
                            лист[0] = сеть[0];
                            throw new Шаг.Обрыв();
                        }
                        доигрывание[0] = true;
                        return Поиск.неПасовать(options, r);
                    }
                    // варианты, которых в узле нет (другая раздача скрытого), — добавить
                    boolean новые = false;
                    for (Choice o : options) {
                        if (!у.рёбра.containsKey(ключ(o))) {
                            новые = true;
                            break;
                        }
                    }
                    if (новые) {
                        раскрыть(у, st, место, options, ctx, мест, new double[мест]);
                    }
                    Choice лучший = null;
                    Ребро лучшее = null;
                    double лучшийСчёт = Double.NEGATIVE_INFINITY;
                    double корень = Math.sqrt(Math.max(1, у.N));
                    for (Choice o : options) {
                        Ребро р = у.рёбра.get(ключ(o));
                        double q = р.N == 0 ? 0 : р.W[место] / р.N;
                        double счёт = q + cPuct * р.P * корень / (1 + р.N)
                            + r.nextDouble() * 1e-6;     // ничьи — жребием, а не первым в списке
                        if (счёт > лучшийСчёт) {
                            лучшийСчёт = счёт;
                            лучший = o;
                            лучшее = р;
                        }
                    }
                    узлы.add(у);
                    рёбра.add(лучшее);
                    текущий[0] = лучшее.ребёнок;
                    ждём[0] = лучшее;
                    return лучший;
                }
            });
        }
        GameState итог;
        try {
            итог = Шаг.прогнать(корень,
                st -> Доигрывание.перемешатьСкрытое(st, seat, r), агенты);
        } catch (RuntimeException e) {
            synchronized (this) {
                сорвалось++;
            }
            return;
        }
        double[] v;
        if (лист[0] != null) {
            v = лист[0];
        } else {
            v = Поиск.итоги(итог, мест);         // доиграно до конца (или до обрыва)
            if (сеть[0] != null) {
                double λ = доляДоигрывания;
                for (int m = 0; m < мест; m++) {
                    v[m] = (1 - λ) * сеть[0][m] + λ * v[m];
                }
            }
        }
        for (int k = 0; k < рёбра.size(); k++) {
            узлы.get(k).N++;
            Ребро р = рёбра.get(k);
            р.N++;
            for (int m = 0; m < мест; m++) {
                р.W[m] += v[m];
            }
        }
        synchronized (this) {
            спусков++;
        }
    }
}
