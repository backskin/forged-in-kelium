package kelium.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.engine.GameEngine;

/**
 * РЕШАТЕЛЬ — бот, который решает ход перебором, а не наугад (переписан
 * 07.10.2026 по требованию дизайнера).
 *
 * <p>Два закона, без исключений:
 * <ol>
 *   <li><b>Ни одного холостого действия.</b> Действие, после которого на столе
 *       не изменилось ничего (материальный отпечаток {@link Lookahead#materialSignature}),
 *       не разыгрывается. Ход, в котором такое действие есть, отбрасывается
 *       целиком. Сорваться оно может только от чужих дел: уничтожили здание,
 *       заняли место.</li>
 *   <li><b>Никакой случайности в выборе.</b> Из равных вариантов берётся
 *       первый; закрытое (руки соперников, колоды) на копии раскладывается
 *       одним и тем же образом для одной и той же позиции.</li>
 * </ol>
 *
 * <p>КАК ДУМАЕТ. Свой ход бот проигрывает целиком на копиях стола
 * ({@link GameEngine#simulateTurn}). Каждое решение хода — какое действие,
 * какую ветку, какое спец-действие, что строить и где, кого бить — это развилка
 * дерева. Перебор идёт от лучших веток к худшим: на каждом прогоне записывается
 * путь решений, и для каждой развилки этого пути ставятся в очередь
 * альтернативы. Лучший по оценке позиции ({@link PositionValue}) путь без
 * холостых действий и разыгрывается за столом. Разошлась жизнь с планом
 * (вытянута другая карта, кубик лёг иначе) — план пересчитывается с того места,
 * где ход сейчас.
 *
 * <p>Приказ на круг выбирается тем же перебором: для каждой карты руки — цена
 * её лучшего хода с поправкой на совпадение и открытый низ (вероятность, что
 * кто-то раньше в круге вскроет ту же карту, считается по открытым сведениям:
 * сыгранные карты видны, рука — нет).
 *
 * <p>Мелкие решения вне своего хода (реакции, свои модули в обновлении,
 * задания на руке) — по правилам эвристики {@link StrategicAgent}, тоже без
 * случайности.
 */
public final class Решатель extends Agent {

    /** Печатать сбои прогонов (для разбора). */
    public static boolean ОТЛАДКА = Boolean.getBoolean("kelium.решатель.отладка");

    /** Прогонов на выбор хода. */
    private final int бюджетХода;
    /** Прогонов на оценку одной карты приказа. */
    private final int бюджетКарты;
    /** Альтернатив на одну мелкую развилку (действия и ветки — все). */
    private static final int АЛЬТЕРНАТИВ = 3;
    /** Штраф за холостое действие — больше любой разницы в оценке позиции. */
    private static final double ХОЛОСТОЕ = 1000.0;

    private final Genome genome;
    private final Genome others;
    private final Intents intents;
    private final StrategicAgent правила;
    private final String характер;

    // ---- текущий ход ----
    private int мойХодКлюч = -1;
    private String карта;
    private boolean совпало;
    private boolean низОткрыт;
    private GameState снимок;
    private final List<Шаг> сыграно = new ArrayList<>();
    private List<Шаг> план = null;
    private java.util.Set<Integer> пустыеВПлане = java.util.Set.of();
    private int последнийРаунд = -1;
    private String последняяКарта;
    private String намерение;
    private boolean вмоёмХоду = false;
    /** Отпечаток стола в миг выбора текущего действия (за столом). */
    private long отпечатокДействия = 0;
    /** Веса соперников в оценке — по позиции до решения. */
    private double[] веса;
    private GameState последнийСтол;

    /** Сколько раз бот видел, что задуманное действие стало холостым, и сменил его. */
    public int холостыхИзбежано = 0;
    /** Сколько холостых действий всё-таки сыграно (должно быть 0). */
    public int холостыхСыграно = 0;

    /** Одно решение пути: вид вопроса и ключ выбранного варианта. */
    record Шаг(String вид, String ключ) {
    }

    public Решатель(int seat, String характер, int players, int бюджетХода, int бюджетКарты) {
        super(seat, "решатель:" + характер + "#" + seat);
        this.характер = характер;
        this.genome = Bots.genome(характер, players);
        this.others = Bots.genome("balanced", players);
        this.intents = new Intents(players, genome.get("pl.commitment", 0.5));
        // лидер — тот, кто ближе к победе по открытому, а не по очкам сейчас
        this.intents.поУгрозам = true;
        // Правила мелких решений — эвристика стратега; случайности в ней нет
        // (HeuristicAgent: равные — первый, неизвестный вопрос — первый вариант).
        this.правила = new StrategicAgent(seat, new Random(0), genome, характер);
        this.бюджетХода = бюджетХода;
        this.бюджетКарты = бюджетКарты;
    }

    // Спец-действие бот получает отдельным вопросом «spec», как все боты: признак
    // «спец в меню хода» окно игры читает как «за местом живой игрок».

    @Override
    public String intent() {
        return намерение;
    }

    @Override
    public void observePublicEvent(Map<String, Object> e) {
        правила.observePublicEvent(e);
        // ПАМЯТЬ ОБИД: кто снёс мой жетон — тот цель; снесли ЦУ — цель сразу
        // пересматривается (так же, как у прежнего планировщика)
        if ("combat_hit".equals(e.get("type")) && Boolean.TRUE.equals(e.get("destroyed"))
                && e.get("victim_owner") instanceof Integer vo && vo == seat
                && e.get("seat") instanceof Integer by && by != seat) {
            intents.hurtBy(by, false);
        } else if ("cu_destroyed".equals(e.get("type")) && e.get("seat") instanceof Integer owner
                && owner == seat && e.get("by") instanceof Integer by) {
            intents.hurtBy(by, true);
            if (последнийСтол != null) {
                intents.retarget(последнийСтол, seat, genome.get("pl.leader_bias", 0.8), true);
            }
        }
        if ("turn_orders".equals(e.get("type")) && e.get("seat") instanceof Number n
                && n.intValue() == seat) {
            карта = String.valueOf(e.get("card"));
            совпало = Boolean.TRUE.equals(e.get("coincided"));
            низОткрыт = Boolean.TRUE.equals(e.get("bottom_open"));
            снимок = null;
            план = null;
            сыграно.clear();
            мойХодКлюч++;
            вмоёмХоду = true;
        }
        if ("turn_end".equals(e.get("type")) && e.get("seat") instanceof Number n
                && n.intValue() == seat) {
            снимок = null;
            план = null;
            вмоёмХоду = false;
        }
        if ("turn_orders".equals(e.get("type")) && e.get("seat") instanceof Number n
                && n.intValue() != seat) {
            снимок = null;   // ход перешёл к другому
            план = null;
            вмоёмХоду = false;
        }
    }

    @Override
    public void observeEvent(Map<String, Object> e) {
        правила.observeEvent(e);
        if (ОТЛАДКА && "action".equals(e.get("type")) && последнийСтол != null
                && e.get("seat") instanceof Number n && n.intValue() == seat
                && !Boolean.TRUE.equals(e.get("free"))
                && Lookahead.materialSignature(последнийСтол, seat) == отпечатокДействия) {
            System.err.println("ХОЛОСТОЕ ЗА СТОЛОМ " + name + " " + e.get("detail") + "\n  план " + план
                + "\n  пустые " + пустыеВПлане + "\n  сыграно " + сыграно);
        }
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
        последнийСтол = state;
        String вид = ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
        if (state.round != последнийРаунд) {
            последнийРаунд = state.round;
            intents.newRound(state, seat, genome.get("pl.leader_bias", 0.8));
            вФокус(state);
        }
        if ("reveal_order".equals(вид) || "blind_discard".equals(вид)) {
            веса = Относительно.веса(state, seat, genome);
        }
        if ("reveal_order".equals(вид)) {
            Choice c = options.size() == 1 ? options.get(0) : выбратьПриказ(state, options);
            последняяКарта = String.valueOf(c.payload());
            return c;
        }
        if ("blind_discard".equals(вид)) {
            return отложитьПриказ(state, options);
        }
        if (снимок == null && карта != null && вмоёмХоду) {
            // первый вопрос моего хода — снимок стола, с него и проигрываются ходы
            снимок = state.deepCopy(семя(state));
            веса = Относительно.веса(state, seat, genome);
            план = null;
            сыграно.clear();
        }
        if (options.size() == 1) {
            return options.get(0);
        }
        if (снимок == null) {
            return поПравилам(правила, state, options, ctx, вид);
        }
        // решение внутри моего хода — по плану
        int k = сыграно.size();
        Choice c = поПлану(options, вид, k);
        if (c == null) {
            Итог и = искать(снимок, карта, совпало, низОткрыт, сыграно, бюджетХода);
            план = и.путь();
            пустыеВПлане = и.пустыеШаги();
            намерение = замысел(state, и);
            c = поПлану(options, вид, k);
        }
        if (c != null && "action".equals(вид) && "action".equals(c.kind()) && c.payload() != null
                && !сработаетЗаСтолом(state, String.valueOf(c.payload()), подшаги(k))) {
            // ПОСЛЕДНЯЯ ПРОВЕРКА НА НАСТОЯЩЕМ СТОЛЕ: план строился по копии в
            // начале хода, а случай в ходе лёг иначе (другой модуль из мешка,
            // другая карта) — задуманное действие стало бы холостым. Берётся
            // другое рабочее действие, нет такого — пас; план пересчитается.
            холостыхИзбежано++;
            c = рабочееИлиПас(state, options, ctx, c);
            план = null;
        }
        if (c == null && ОТЛАДКА) {
            List<String> кл = new ArrayList<>();
            for (Choice o : options) {
                кл.add(TurnSim.keyOf(o));
            }
            System.err.println("ВНЕ ПЛАНА р" + state.round + " к" + state.circle + " " + name + " вопрос " + вид
                + " №" + k + " варианты " + кл + " план " + план + " сыграно " + сыграно);
        }
        if (c == null && !Исполнитель.верхнее(вид) && отпечатокДействия != 0
                && Lookahead.materialSignature(state, seat) == отпечатокДействия) {
            // НАЧАТОЕ ДЕЙСТВИЕ НЕ КОНЧАЕТСЯ ПУСТЫМ: действие уже выбрано, на
            // столе от него пока ничего; жизнь разошлась с планом (цель ушла,
            // кубик лёг иначе) — берётся лучший НЕ пустой вариант по правилам.
            List<Choice> дело = new ArrayList<>();
            for (Choice o : options) {
                if (!"pass".equals(o.kind()) && o.payload() != null) {
                    дело.add(o);
                }
            }
            if (!дело.isEmpty()) {
                c = дело.size() == 1 ? дело.get(0) : правила.choose(state, дело, ctx);
            }
        }
        if (c == null) {
            // план не нашёл этого вопроса (жизнь разошлась с копией) — правило,
            // и для действий проверка «не холостое ли»
            c = поПравилам(правила, state, options, ctx, вид);
            if ("action".equals(вид) && "action".equals(c.kind()) && c.payload() != null
                    && !сработаетЗаСтолом(state, String.valueOf(c.payload()), List.of())) {
                холостыхИзбежано++;
                c = рабочееИлиПас(state, options, ctx, c);
            }
        }
        if (Исполнитель.верхнее(вид)) {
            отпечатокДействия = Lookahead.materialSignature(state, seat);
        }
        сыграно.add(new Шаг(вид, TurnSim.keyOf(c)));
        return c;
    }

    /**
     * Решение правилами. В меню хода рядом с действиями лежат спец-действия
     * (задания, арсенал, плашка) — правила о них не знают, их перебирает поиск;
     * правилам показываются только действия и пас.
     */
    static Choice поПравилам(Agent правила, GameState state, List<Choice> options,
                              Map<String, Object> ctx, String вид) {
        if ("action".equals(вид)) {
            List<Choice> только = new ArrayList<>();
            for (Choice o : options) {
                if ("action".equals(o.kind()) || "pass".equals(o.kind()) || o.payload() == null) {
                    только.add(o);
                }
            }
            if (только.isEmpty()) {
                return options.get(0);
            }
            if (только.size() == 1) {
                return только.get(0);
            }
            return правила.choose(state, только, ctx);
        }
        return правила.choose(state, options, ctx);
    }

    private Choice поПлану(List<Choice> options, String вид, int k) {
        if (план == null || k >= план.size() || !план.get(k).вид().equals(вид)) {
            return null;
        }
        if (пустыеВПлане.contains(k)) {
            холостыхИзбежано++;
            return пас(options);   // в плане этот шаг ничего не дал — пас
        }
        String ключ = план.get(k).ключ();
        for (Choice o : options) {
            if (TurnSim.keyOf(o).equals(ключ)) {
                return o;
            }
        }
        return null;
    }

    /** Решения плана внутри действия, начатого на шаге k (до следующего верхнего решения). */
    private List<Шаг> подшаги(int k) {
        List<Шаг> out = new ArrayList<>();
        if (план == null) {
            return out;
        }
        for (int i = k + 1; i < план.size() && !Исполнитель.верхнее(план.get(i).вид()); i++) {
            out.add(план.get(i));
        }
        return out;
    }

    /**
     * Сработает ли действие на НАСТОЯЩЕМ столе: копия текущего положения,
     * действие разыгрывается теми решениями, что задуманы (дальше — правилами),
     * и смотрится, изменилось ли на столе хоть что-нибудь.
     */
    private boolean сработаетЗаСтолом(GameState s, String развилка, List<Шаг> решения) {
        long семя = семя(s) ^ 0x9E3779B97F4A7C15L;
        GameState c = s.deepCopy(семя);
        Исполнитель исп = new Исполнитель(seat, решения, правила);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < c.numPlayers(); i++) {
            agents.add(i == seat ? исп : new StrategicAgent(i, new Random(0), others, "модель"));
        }
        GameEngine.bindResume(c, agents, null);
        long до = Lookahead.materialSignature(c, seat);
        try {
            kelium.engine.TurnContext ctx = new kelium.engine.TurnContext(seat, 0);
            kelium.engine.ActionResult r = kelium.engine.Actions.create(развилка, c)
                .perform(c.player(seat), ctx, исп);
            return r.ok() && Lookahead.materialSignature(c, seat) != до;
        } catch (RuntimeException e) {
            return true;   // проверить не удалось — верим плану
        }
    }

    /** Другое рабочее действие (по правилам, с проверкой на столе) или пас. */
    private Choice рабочееИлиПас(GameState s, List<Choice> options, Map<String, Object> ctx, Choice плохое) {
        List<Choice> остальные = new ArrayList<>();
        for (Choice o : options) {
            if (o != плохое && "action".equals(o.kind()) && o.payload() != null) {
                остальные.add(o);
            }
        }
        while (!остальные.isEmpty()) {
            Choice o = остальные.size() == 1 ? остальные.get(0) : правила.choose(s, остальные, ctx);
            if (o.payload() == null || !остальные.contains(o)) {
                break;
            }
            if (сработаетЗаСтолом(s, String.valueOf(o.payload()), List.of())) {
                return o;
            }
            остальные.remove(o);
        }
        return пас(options);
    }

    /** Хоть одна ветка развилки что-то меняет на столе (проверка на копии). */
    private boolean развилкаРаботает(GameState s, String развилка) {
        for (String в : kelium.engine.Actions.FORKS.getOrDefault(развилка, List.of(развилка))) {
            String действие = "build".equals(в) && kelium.engine.Actions.FORK_BUILD.get(развилка) != null
                ? "build_" + kelium.engine.Actions.FORK_BUILD.get(развилка) : в;
            Lookahead.ActionOutcome и = Lookahead.actionOutcome(s, seat, действие, genome, others, 0, семя(s));
            if (и.ok() && и.changed()) {
                return true;
            }
        }
        return false;
    }

    private static Choice пас(List<Choice> options) {
        for (Choice o : options) {
            if ("pass".equals(o.kind()) || o.payload() == null) {
                return o;
            }
        }
        return options.get(options.size() - 1);
    }

    /** Одно и то же семя для одной и той же позиции: никакой случайности между прогонами. */
    private long семя(GameState s) {
        return s.round * 1_000_003L + s.circle * 10_007L + seat * 101L + мойХодКлюч;
    }

    // =====================================================================
    //  Перебор хода
    // =====================================================================

    /** Итог прогона: путь решений, развилки по пути, оценка. */
    record Прогон(List<Шаг> путь, List<List<String>> варианты, double оценка, int холостых,
                  double чистая, java.util.Set<Integer> пустыеШаги, GameState после) {
    }

    /** Лучший найденный ход. */
    record Итог(List<Шаг> путь, double оценка, java.util.Set<Integer> пустыеШаги, GameState после) {
    }

    private Итог искать(GameState от, String cardId, boolean совп, boolean низ,
                        List<Шаг> префикс, int бюджет) {
        // очередь: префикс пути и приоритет (оценка родителя)
        record Задание(List<Шаг> префикс, double приоритет, int глубина) {
        }
        PriorityQueue<Задание> очередь = new PriorityQueue<>((a, b) -> {
            int c = Double.compare(b.приоритет, a.приоритет);
            return c != 0 ? c : Integer.compare(a.глубина, b.глубина);
        });
        очередь.add(new Задание(new ArrayList<>(префикс), Double.POSITIVE_INFINITY, префикс.size()));
        java.util.Set<List<Шаг>> было = new java.util.HashSet<>();
        Прогон лучший = null;
        int прогонов = 0;
        while (!очередь.isEmpty() && прогонов < бюджет) {
            Задание з = очередь.poll();
            if (!было.add(з.префикс)) {
                continue;
            }
            Прогон п = прогнать(от, cardId, совп, низ, з.префикс);
            прогонов++;
            if (п == null) {
                continue;
            }
            if (лучший == null || п.оценка() > лучший.оценка()) {
                лучший = п;
            }
            // альтернативы на каждой развилке после префикса
            for (int j = з.префикс.size(); j < п.путь().size(); j++) {
                Шаг взят = п.путь().get(j);
                List<String> вар = п.варианты().get(j);
                if (вар.size() < 2) {
                    continue;
                }
                boolean крупная = "action".equals(взят.вид()) || "action_branch".equals(взят.вид());
                int взято = 0;
                for (String ключ : вар) {
                    if (ключ.equals(взят.ключ())) {
                        continue;
                    }
                    if (!крупная && взято >= АЛЬТЕРНАТИВ) {
                        break;
                    }
                    List<Шаг> нов = new ArrayList<>(п.путь().subList(0, j));
                    нов.add(new Шаг(взят.вид(), ключ));
                    // крупные развилки раньше мелких, ранние раньше поздних
                    double приор = п.оценка() - (крупная ? 0 : 0.5) - 0.01 * j;
                    очередь.add(new Задание(нов, приор, j + 1));
                    взято++;
                }
            }
        }
        if (ОТЛАДКА && лучший != null && лучший.холостых() > 0) {
            System.err.println("ЛУЧШИЙ С ХОЛОСТЫМ " + name + " прогонов " + прогонов + " путь " + лучший.путь());
        }
        // Холостое действие ничего не меняет — позиция та же, что при пасе;
        // поэтому цена хода — оценка без штрафа, а сами пустые шаги при
        // исполнении заменяются пасом.
        return лучший == null ? new Итог(List.of(), Double.NEGATIVE_INFINITY, java.util.Set.of(), null)
            : new Итог(лучший.путь(), лучший.чистая(), лучший.пустыеШаги(), лучший.после());
    }

    /** Один прогон хода на копии: префикс решений задан, дальше — правила. */
    private Прогон прогнать(GameState от, String cardId, boolean совп, boolean низ, List<Шаг> префикс) {
        long семя = семя(от);
        GameState c = от.deepCopy(семя);
        Доигрывание.перемешатьСкрытое(c, seat, new Random(семя ^ 0x2545F4914F6CDD1DL));
        Исполнитель исп = new Исполнитель(seat, префикс, правилаДля(c));
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < c.numPlayers(); i++) {
            agents.add(i == seat ? исп : new StrategicAgent(i, new Random(0), others, "модель"));
        }
        GameEngine.bindResume(c, agents, null);
        try {
            new GameEngine(c, agents, null).simulateTurn(seat, cardId, совп, низ);
        } catch (RuntimeException e) {
            if (ОТЛАДКА) {
                e.printStackTrace();
            }
            return null;
        }
        // Разошлось с путём (вытянуты другие карты) — прогон годится: до
        // расхождения он шёл по пути, дальше — по правилам.
        int холостых = Math.max(исп.холостых(Lookahead.materialSignature(c, seat)), исп.холостыхПоСобытиям);
        // ОЦЕНКА ОТНОСИТЕЛЬНО СОПЕРНИКОВ: моя позиция минус видимая сила
        // соперников с весами — лидер по видимому развитию весит больше
        // (Относительно.веса). Без этого бот бил кого попало: коалиция против
        // лидера упала до случайных 31% (наблюдатель 07.10.2026).
        double v = (веса == null ? PositionValue.value(c, seat, genome, intents)
            : Относительно.оценка(c, seat, genome, intents, веса, others)) - ХОЛОСТОЕ * холостых;
        return new Прогон(исп.путь, исп.варианты, v, холостых, v + ХОЛОСТОЕ * холостых, исп.пустыеШаги, c);
    }

    private Agent правилаДля(GameState c) {
        return правила;
    }

    /**
     * Исполнитель на копии: первые решения — по префиксу, остальные — правилами.
     * Записывает путь, варианты каждой развилки и отпечаток стола перед каждым
     * выбором действия.
     */
    static final class Исполнитель extends Agent {
        final List<Шаг> префикс;
        final Agent правила;
        final List<Шаг> путь = new ArrayList<>();
        final List<List<String>> варианты = new ArrayList<>();
        final List<Long> отпечатки = new ArrayList<>();
        final List<Boolean> действие = new ArrayList<>();
        boolean разошлось = false;
        /** Отпечаток в миг последнего верхнего решения и холостые действия по событиям движка. */
        long отпечатокНачала;
        GameState стол;
        int холостыхПоСобытиям = 0;
        /** Номера верхних шагов пути, оказавшихся холостыми. */
        final java.util.Set<Integer> пустыеШаги = new java.util.HashSet<>();
        int шагНачала = -1;

        Исполнитель(int seat, List<Шаг> префикс, Agent правила) {
            super(seat, "исполнитель#" + seat);
            this.префикс = префикс;
            this.правила = правила;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            String вид = ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
            if (options.size() == 1) {
                return options.get(0);
            }
            int k = путь.size();
            Choice pick = null;
            if (k < префикс.size()) {
                Шаг ш = префикс.get(k);
                if (ш.вид().equals(вид)) {
                    for (Choice o : options) {
                        if (TurnSim.keyOf(o).equals(ш.ключ())) {
                            pick = o;
                            break;
                        }
                    }
                }
                if (pick == null) {
                    разошлось = true;
                }
            }
            if (pick == null) {
                pick = поПравилам(правила, state, options, ctx, вид);
            }
            if (верхнее(вид)) {
                стол = state;
                отпечатокНачала = Lookahead.materialSignature(state, seat);
                шагНачала = путь.size();
                // граница: прежнее верхнее решение (действие или спец-действие)
                // закончилось — его итог виден в отпечатке на этом месте
                отпечатки.add(Lookahead.materialSignature(state, seat));
                действие.add(pick.payload() != null && !"pass".equals(pick.kind()));
            }
            List<String> вар = new ArrayList<>();
            // взятый вариант — первым, остальные в порядке движка
            вар.add(TurnSim.keyOf(pick));
            for (Choice o : options) {
                String кл = TurnSim.keyOf(o);
                if (!вар.contains(кл)) {
                    вар.add(кл);
                }
            }
            путь.add(new Шаг(вид, TurnSim.keyOf(pick)));
            варианты.add(вар);
            return pick;
        }

        /**
         * ДЕЙСТВИЕ ЗАВЕРШЕНО — движок сообщает об этом событием сразу после
         * розыгрыша. Если с мига выбора на столе не изменилось ничего, действие
         * было холостым. Это точнее границ «до следующего решения»: между ними
         * могло сыграться спец-действие и замаскировать пустое.
         */
        @Override
        public void observeEvent(Map<String, Object> e) {
            правила.observeEvent(e);
            if (стол != null && "action".equals(e.get("type")) && !Boolean.TRUE.equals(e.get("free"))
                    && e.get("seat") instanceof Number n && n.intValue() == seat) {
                long теперь = Lookahead.materialSignature(стол, seat);
                if (теперь == отпечатокНачала || !Boolean.TRUE.equals(e.get("ok"))) {
                    холостыхПоСобытиям++;
                    пустыеШаги.add(шагНачала);
                }
                отпечатокНачала = теперь;
            }
        }

        /** Верхнее решение хода: что делать дальше (действие или спец-действие). */
        static boolean верхнее(String вид) {
            return "action".equals(вид) || "spec".equals(вид);
        }

        /** Сколько выбранных действий и спец-действий не изменили на столе ничего. */
        int холостых(long конец) {
            int n = 0;
            for (int i = 0; i < действие.size(); i++) {
                if (!действие.get(i)) {
                    continue;
                }
                long до = отпечатки.get(i);
                long после = i + 1 < отпечатки.size() ? отпечатки.get(i + 1) : конец;
                if (до == после) {
                    n++;
                }
            }
            return n;
        }
    }

    // =====================================================================
    //  Приказ на круг
    // =====================================================================

    private Choice выбратьПриказ(GameState s, List<Choice> options) {
        Choice лучший = null;
        double лучшая = Double.NEGATIVE_INFINITY;
        for (Choice o : options) {
            String cid = String.valueOf(o.payload());
            double v = ценаКарты(s, cid);
            if (v > лучшая) {
                лучшая = v;
                лучший = o;
            }
        }
        return лучший;
    }

    private Choice отложитьПриказ(GameState s, List<Choice> options) {
        Choice худший = null;
        double худшая = Double.POSITIVE_INFINITY;
        for (Choice o : options) {
            double v = искать(s, String.valueOf(o.payload()), false, false, List.of(), бюджетКарты).оценка();
            if (v < худшая) {
                худшая = v;
                худший = o;
            }
        }
        return худший;
    }

    /** Ожидаемая цена хода картой: без совпадения, с совпадением, с открытым низом. */
    private double ценаКарты(GameState s, String cid) {
        double v00 = искать(s, cid, false, false, List.of(), бюджетКарты).оценка();
        Map<String, Object> card = картаПриказа(s, cid);
        String верх = card == null ? null : String.valueOf(card.get("top"));
        String низ = card == null || card.get("bottom") == null ? null : String.valueOf(card.get("bottom"));
        double pВерх = верх == null ? 0 : вероятностьРаньше(s, верх);
        double pНиз = низ == null ? 0 : вероятностьРаньше(s, низ);
        double e = v00;
        if (pВерх > 0.05) {
            double v10 = искать(s, cid, true, false, List.of(), бюджетКарты).оценка();
            e += pВерх * Math.min(0, v10 - v00);
        }
        if (pНиз > 0.05) {
            double v01 = искать(s, cid, false, true, List.of(), бюджетКарты).оценка();
            e += pНиз * Math.max(0, v01 - v00);
        }
        return e;
    }

    /**
     * Вероятность, что приказ {@code top} вскроет кто-то раньше меня в этом
     * круге. По открытому: у соперника на руке осталось n карт (одна из них —
     * под свалкой, какая — не видно); не сыгранную ещё карту он вскроет с
     * вероятностью 1/n.
     */
    private double вероятностьРаньше(GameState s, String top) {
        int n = s.numPlayers();
        double нет = 1.0;
        for (int i = 0; i < n; i++) {
            int место = (s.firstPlayer + i) % n;
            if (место == seat) {
                break;
            }
            var p = s.player(место);
            boolean сыграна = false;
            for (String c : p.orderPlayed) {
                Map<String, Object> card = картаПриказа(s, c);
                if (card != null && top.equals(String.valueOf(card.get("top")))) {
                    сыграна = true;
                }
            }
            if (сыграна) {
                continue;
            }
            int осталось = Math.max(1, 5 - p.orderPlayed.size());
            нет *= 1.0 - 1.0 / осталось;
        }
        return 1.0 - нет;
    }

    private static Map<String, Object> картаПриказа(GameState s, String cid) {
        try {
            return kelium.dataio.Ctx.cards(s, "orders").byId(cid);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Зачем этот ход — словами, по задуманному ходу (для ленты партии). */
    private String замысел(GameState до, Итог и) {
        if (и.после() == null) {
            return null;
        }
        List<String> действия = new ArrayList<>();
        for (Шаг ш : и.путь()) {
            if ("action".equals(ш.вид()) && ш.ключ().startsWith("action|")) {
                действия.add(ш.ключ().substring("action|".length()));
            }
        }
        try {
            return Замысел.ход(до, и.после(), seat, Угрозы.лидер(до, seat), Map.of(), intents, действия);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Задание в фокусе раунда: ближе к выполнению и ценнее по награде. */
    private void вФокус(GameState s) {
        kelium.core.PlayerState p = s.player(seat);
        Map<String, Double> prog = new HashMap<>();
        Map<String, Double> val = new HashMap<>();
        kelium.engine.cards.EngineCardContext ctx = new kelium.engine.cards.EngineCardContext(s, seat);
        for (String cid : p.objectiveHand) {
            var card = kelium.engine.cards.CardRegistry.objective(cid);
            prog.put(cid, card == null ? 0.0 : PositionValue.clamp(card.progress(ctx)));
            val.put(cid, PositionValue.rewardValue(s, seat, cid) / 5.0);
        }
        intents.refocus(p.objectiveHand, prog, val);
    }

    /** Карта хода, о которой бот уже знает (для отчётов). */
    public String последняяКарта() {
        return последняяКарта;
    }

    public String характер() {
        return характер;
    }

    /** Для отчётов: бюджет. */
    public Map<String, Integer> бюджет() {
        Map<String, Integer> m = new HashMap<>();
        m.put("ход", бюджетХода);
        m.put("карта", бюджетКарты);
        return m;
    }
}
