package kelium.agents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Choice;
import kelium.core.GameState;

/**
 * SearchAgent — бот, который ДУМАЕТ ВПЕРЁД, а не считает формулу.
 *
 * <p>Это ответ на главную беду прежних ботов. Они выбирали каждое решение жадно,
 * по заранее написанной оценке («Стройка стоит 7.0, Бой 5.5»), и НИ ОДИН ход не
 * проверяли на деле. Из-за этого они играли слабее новичка: новичок мысленно
 * делает ход и смотрит, что получилось, а формула этого не умеет — она не знает,
 * что построенный добытчик окажется у выработанной жилы, а вскрытая Операция не
 * найдёт ни одной цели.
 *
 * <p>Здесь два вида просчёта, по цене:
 * <ol>
 *   <li><b>Выбор действия</b> — каждое действие-кандидат ВЫПОЛНЯЕТСЯ на копии
 *       состояния, и позиции сравниваются по оценке. Дёшево (копия + одно
 *       действие). Отсюда же берётся честный ПАС: если ни одно действие не
 *       улучшает позицию, бот не тратит ход впустую — а именно «холостые ходы»
 *       были треть всех действий.</li>
 *   <li><b>Выбор приказа</b> — приказ определяет, какие действия вообще будут
 *       доступны, и его цена проявляется через раунды. Поэтому здесь копия
 *       ДОИГРЫВАЕТСЯ на несколько раундов вперёд, по нескольку прогонов на
 *       вариант (соперники в копии выбирают приказы заново — вскрытие закрытое,
 *       и в жизни их выбор тоже неизвестен).</li>
 * </ol>
 *
 * <p>ПОРЯДОК ЦЕНЫ. Просчёт приказа в разы дороже партии без него, поэтому у бота
 * есть БЮДЖЕТ на партию: кончился — играет штатной оценкой. Так один и тот же
 * класс годится и для показательных партий (щедрый бюджет, глубокий просчёт), и
 * для обучения/лиги (просчёт только действий — почти бесплатно).
 */
public class SearchAgent extends StrategicAgent {

    /** Сколько лучших «на глаз» действий проверять на деле (0 = не проверять). */
    private final int actionWidth;
    /**
     * На сколько раундов доигрывать партию после каждого действия-кандидата.
     * 0 — только проверка на холостой ход (дёшево); 1-2 — настоящее сравнение
     * вариантов, в том числе вложений.
     */
    private final int actionHorizon;
    /** Сколько лучших приказов доигрывать; 0 = приказ выбирается формулой. */
    private final int revealWidth;
    /** Сколько прогонов на каждый приказ (усреднение по неизвестному). */
    private final int revealRollouts;
    /** На сколько раундов вперёд доигрывать (0 = до конца партии). */
    private final int revealHorizon;
    /** Сколько доигрываний осталось на эту партию. */
    private int playOutBudget;
    /** Модель соперника: чем он играет в моей голове. */
    private final Genome oppModel;
    private final Random searchRng;

    /**
     * ПАМЯТЬ О ПРИВЫЧКАХ СОПЕРНИКОВ по приказам. Вскрытые приказы — открытая
     * информация, запоминать их не подглядывание. В просчёте соперники вскрывают
     * приказы по этим привычкам: совпадение приказов блокирует ход, и это главное,
     * что стоит предсказывать.
     */
    private final OrderHabits habits = new OrderHabits();

    /** Сколько раз просчёт реально менял решение — для отчётов и отладки. */
    private int changedMind = 0;
    private int searched = 0;
    /** Сколько раз просчёт поймал ХОЛОСТОЙ ход (действие ничего не меняет). */
    private int hollowCaught = 0;
    private int passedAll = 0;

    public SearchAgent(int seat, Random rng, Genome genome, String character,
                       int actionWidth, int actionHorizon, int revealWidth,
                       int revealRollouts, int revealHorizon, int playOutBudget) {
        super(seat, rng, genome, character);
        this.actionWidth = actionWidth;
        this.actionHorizon = actionHorizon;
        this.revealWidth = revealWidth;
        this.revealRollouts = revealRollouts;
        this.revealHorizon = revealHorizon;
        this.playOutBudget = playOutBudget;
        this.oppModel = genome != null ? genome : Genome.defaults();
        this.searchRng = new Random(rng == null ? seat : rng.nextLong());
    }

    /**
     * ДЕШЁВЫЙ просчёт: проверяются только действия. Годится для обучения и лиги —
     * замедление в разы, а не в сотни раз.
     */
    public static SearchAgent fast(int seat, Random rng, Genome genome, String character) {
        return new SearchAgent(seat, rng, genome, character, 4, 0, 0, 0, 0, 0);
    }

    /**
     * СРЕДНИЙ просчёт: каждое действие-кандидат не просто выполняется на копии, но
     * и доигрывается на раунд вперёд. Именно этот шаг отличает ВЛОЖЕНИЕ от
     * растраты (Стройка и Маркет на один ход выглядят убытком), и именно его не
     * хватало дешёвому режиму — тот умел только вычёркивать холостые ходы.
     *
     * <p>Дорогих доигрываний на выборе приказа здесь нет, поэтому режим годится
     * там, где партий много: обучение, лига, балансовые стенды.
     */
    public static SearchAgent mid(int seat, Random rng, Genome genome, String character) {
        return new SearchAgent(seat, rng, genome, character, 4, 1, 0, 0, 0, 0);
    }

    /**
     * ГЛУБОКИЙ просчёт: действия плюс доигрывание партии на выборе приказа.
     * Для показательных партий и разбора: играет заметно сильнее, но медленно.
     *
     * <p>СОСТАВ ПОДОБРАН ЗАМЕРОМ (13.08.2026, лига по 224 партии на участника), и
     * он вышел не таким, как ожидалось:
     * <ul>
     *   <li>доигрывание на выборе ДЕЙСТВИЯ лучше выключить (горизонт 0, остаётся
     *       только отсев холостых ходов): с ним бот слабее, чем без него, — время
     *       уходит на копии, а решает всё равно выбор приказа;</li>
     *   <li>число прогонов на приказ важнее их длины: четыре прогона на два раунда
     *       обходят два прогона на два раунда с перевесом 56%;</li>
     *   <li>доигрывать приказ ДО КОНЦА партии — хуже всего (44% против горизонта в
     *       два раунда): бюджет думанья съедается целиком, а длинная цепочка
     *       жадных ходов в просчёте наводит больше шума, чем даёт сигнала.</li>
     * </ul>
     * Дальше состав не улучшается: шесть прогонов и ширина пять дают 49% против
     * этого набора, то есть упёрлись не в количество думанья, а в качество
     * доигрывания.
     */
    public static SearchAgent deep(int seat, Random rng, Genome genome, String character) {
        return new SearchAgent(seat, rng, genome, character, 4, 0, 4, 4, 2, 400);
    }

    /** Сколько решений просчитано и в скольких просчёт переспорил формулу. */
    public String searchStats() {
        return "просчитано решений " + searched + ", формулу переспорил " + changedMind
            + ", поймано холостых действий " + hollowCaught
            + ", отказов от хода " + passedAll;
    }

    /** Запоминаем, кто что вскрыл: это открытая информация со стола. */
    @Override
    public void observePublicEvent(Map<String, Object> event) {
        super.observePublicEvent(event);
        if ("reveal".equals(String.valueOf(event.get("type")))
                && event.get("revealed") instanceof Map<?, ?> m) {
            habits.note(m);
        }
    }

    @Override
    protected Choice pick(GameState state, List<Choice> options,
                          Map<String, Object> context, String kind) {
        boolean useHabits = genome().get("search.opponent_habits", 1.0) > 0.5;
        if (useHabits) {
            Lookahead.useHabits(habits);
        }
        try {
            Choice viaSearch = switch (kind) {
                case "action" -> pickAction(state, options, context);
                case "reveal_order" -> pickReveal(state, options, context);
                default -> null;
            };
            if (viaSearch != null) {
                return viaSearch;
            }
            return super.pick(state, options, context, kind);
        } finally {
            // Снимаем обязательно: иначе привычки одного бота протекут в просчёт
            // другого — за столом их несколько, и поток один.
            Lookahead.useHabits(null);
        }
    }

    // ==================================================================
    //  Выбор ДЕЙСТВИЯ: выполнить на копии и сравнить позиции
    // ==================================================================

    private Choice pickAction(GameState state, List<Choice> options,
                              Map<String, Object> context) {
        if (actionWidth <= 0) {
            return null;
        }
        List<Choice> reals = new ArrayList<>();
        Choice pass = null;
        for (Choice o : options) {
            if (o.payload() == null) {
                pass = o;
            } else {
                reals.add(o);
            }
        }
        if (reals.size() < 2) {
            return null;   // выбора нет — просчитывать нечего
        }
        // Сперва отбираем несколько лучших «на глаз»: проверять на деле все
        // восемь действий незачем, а порядок цены важен.
        reals.sort(Comparator.comparingDouble(
            (Choice o) -> -priorScore(state, o, "action", context)));
        int width = Math.min(actionWidth, reals.size());

        // ЧЕМ ПРОСЧЁТ ЗАНИМАЕТСЯ, А ЧЕМ НЕТ — это выяснено ЗАМЕРОМ, а не
        // рассуждением. Первая версия ранжировала действия по росту оценки
        // позиции на один шаг вперёд и оказалась на 160 очков Эло СЛАБЕЕ обычного
        // бота: на один шаг вперёд любое ВЛОЖЕНИЕ выглядит убытком (Стройка —
        // минус монеты, Маркет — минус келемий), и бот перестал развиваться.
        //
        // Поэтому просчёт работает не заменой формулы, а её КОРРЕКТИРОВКОЙ:
        //  * дешёвый режим — вычёркивает ХОЛОСТЫЕ ходы: действие формально
        //    удалось, а в позиции не изменилось НИЧЕГО (Бой без достижимых целей,
        //    Добыча с выработанной жилы, Сборка без места). Формула этого не
        //    видит в принципе, а таких ходов была треть;
        //  * глубокий режим — вдобавок ДОИГРЫВАЕТ партию после каждого варианта,
        //    и вот там вложение уже отличимо от растраты.
        double trust = genome().get("search.trust", 1.0);
        double hollowPenalty = genome().get("search.hollow_penalty", 3.0);
        // ВЕС ОЦЕНКИ ПОЗИЦИИ на один шаг вперёд. Ноль здесь — не безобидная
        // мелочь: пока он был нулём, дешёвый просчёт не спрашивал оценку позиции
        // ВООБЩЕ, и двадцать девять весов оценки ни на что не влияли, то есть отбор
        // не мог их настроить (поймано тестом, а не рассуждением). При этом делать
        // оценку ГЛАВНЫМ судьёй нельзя — так уже пробовали, вышло на 160 очков Эло
        // хуже: на один шаг вперёд любое вложение выглядит убытком. Поэтому она
        // входит малой поправкой к формуле.
        double valueWeight = genome().get("search.value_weight", 0.2);
        Choice best = null;
        double bestRank = Double.NEGATIVE_INFINITY;
        boolean bestHollow = true;
        int hollowHere = 0;
        int checked = 0;
        double refPlayOut = Double.NaN;
        double refValue = Double.NaN;
        // ОБЩЕЕ ЗЕРНО НА ВСЕ ВАРИАНТЫ. Варианты обязаны проверяться в ОДИНАКОВЫХ
        // условиях, иначе сравнивается не сила хода, а удачливость прогона — и
        // выбирается самый везучий. Это уже подводило: щедрый просчёт с широким
        // перебором оказался слабее скромного именно из-за этого.
        long sharedSeed = searchRng.nextLong();
        for (int i = 0; i < width; i++) {
            Choice o = reals.get(i);
            double prior = priorScore(state, o, "action", context);
            Lookahead.ActionOutcome out = Lookahead.actionOutcome(state, seat,
                String.valueOf(o.payload()), genome(), oppModel,
                actionHorizon, sharedSeed);
            if (!out.ok()) {
                continue;   // движок ответил «нельзя» — вариант мёртвый
            }
            checked++;
            boolean hollow = !out.changed();
            if (hollow) {
                hollowHere++;
            }
            double rank = prior - (hollow ? hollowPenalty : 0.0);
            if (Double.isNaN(refValue)) {
                refValue = out.value();
            }
            // Как и с доигрыванием, важна РАЗНИЦА между вариантами, а не
            // абсолютная величина оценки: она зависит от раздачи.
            rank += valueWeight * (out.value() - refValue);
            if (!Double.isNaN(out.playOut())) {
                if (Double.isNaN(refPlayOut)) {
                    refPlayOut = out.playOut();
                }
                // Доигранный итог сравниваем с итогом ПЕРВОГО проверенного
                // варианта: важна разница между вариантами, а не абсолютная
                // величина, которая зависит от раздачи в этом прогоне.
                rank += trust * (out.playOut() - refPlayOut);
            }
            if (rank > bestRank) {
                bestRank = rank;
                best = o;
                bestHollow = hollow;
            }
        }
        if (checked == 0) {
            return null;
        }
        searched++;
        hollowCaught += hollowHere;
        if (!best.equals(reals.get(0))) {
            changedMind++;
        }
        // ОТКАЗ ОТ ХОДА. Пас в этой игре дорог: движок на пасе закрывает ход
        // целиком, второе действие приказа теряется. Поэтому пасуем только когда
        // ВСЁ проверенное оказалось холостым — тогда ход и так ничего не даст, а
        // боеприпасы и монеты сохранятся.
        if (pass != null && bestHollow && hollowHere == checked
                && bestRank < genome().get("search.pass_threshold", 0.4)) {
            passedAll++;
            return pass;
        }
        return best;
    }

    // ==================================================================
    //  Выбор ПРИКАЗА: доиграть партию с каждым вариантом
    // ==================================================================

    private Choice pickReveal(GameState state, List<Choice> options,
                              Map<String, Object> context) {
        if (revealWidth <= 0 || revealRollouts <= 0) {
            return null;
        }
        List<Choice> reals = new ArrayList<>();
        for (Choice o : options) {
            if (o.payload() != null) {
                reals.add(o);
            }
        }
        if (reals.size() < 2) {
            return null;
        }
        int width = Math.min(revealWidth, reals.size());
        int need = width * revealRollouts;
        if (playOutBudget < need) {
            return null;   // бюджет думанья на партию исчерпан — играем формулой
        }
        reals.sort(Comparator.comparingDouble(
            (Choice o) -> -priorScore(state, o, "reveal_order", context)));

        // Общие зёрна на все приказы — сравнение в одинаковых условиях.
        long[] seeds = new long[revealRollouts];
        for (int r = 0; r < revealRollouts; r++) {
            seeds[r] = searchRng.nextLong();
        }
        // ПОЧЕМУ ПРИКАЗ НЕ ВЫБИРАЕТСЯ ПРОСТО ПО ЛУЧШЕМУ ДОИГРЫВАНИЮ. Двух прогонов
        // на вариант мало: разброс итога партии в этой игре порядка нескольких
        // очков, и «лучший из четырёх средних по два прогона» — это в основном
        // самый везучий вариант, а не самый сильный. Такой выбор СМЕЩЁН вверх, и
        // ровно на этом мы уже обожглись с действиями (щедрый просчёт оказался
        // слабее скромного).
        //
        // Поэтому доигрывание не заменяет формулу, а спорит с ней: за опору берётся
        // априорная оценка, а просчёт добавляет РАЗНИЦУ между вариантами с весом
        // доверия. Вес растёт с числом прогонов: один прогон — почти догадка,
        // несколько — уже измерение. Доля {@code n / (n + 2)} и есть эта поправка
        // (два прогона — половина голоса, шесть — три четверти).
        //
        // ЗАМЕР, а не рассуждение: первая версия этой поправки ДЕЛИЛА доверие на
        // корень из числа прогонов, то есть чем дольше бот думал, тем меньше себе
        // верил. Лига это и показала: щедрый режим с тремя прогонами оказался не
        // сильнее скромного с двумя.
        int n = Math.max(1, revealRollouts);
        double trust = genome().get("search.trust", 1.0) * n / (n + 2.0);
        Choice best = null;
        double bestRank = Double.NEGATIVE_INFINITY;
        double refAvg = Double.NaN;
        for (int i = 0; i < width; i++) {
            Choice o = reals.get(i);
            double sum = 0.0;
            for (int r = 0; r < revealRollouts; r++) {
                sum += Lookahead.playOut(state, seat, genome(), oppModel,
                    new ForcedAgent.Forced("reveal_order", o.payload()),
                    revealHorizon, seeds[r]);
            }
            double avg = sum / revealRollouts;
            if (Double.isNaN(refAvg)) {
                refAvg = avg;
            }
            double rank = priorScore(state, o, "reveal_order", context)
                + trust * (avg - refAvg);
            if (rank > bestRank) {
                bestRank = rank;
                best = o;
            }
        }
        playOutBudget -= need;
        searched++;
        if (best != null && !best.equals(reals.get(0))) {
            changedMind++;
        }
        return best;
    }
}
