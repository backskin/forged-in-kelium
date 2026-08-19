package kelium.engine;

import java.util.function.Consumer;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.TurnJournal;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;
import kelium.engine.cards.ObjectiveCard;

/**
 * НАВЕДЕНИЕ ВНУТРИ ДЕЙСТВИЯ: приблизит ли ЭТОТ КОНКРЕТНЫЙ выбор задания в руке.
 *
 * <p>ЗАЧЕМ. Замер 19.08.2026 ({@code CardCoverage}, 300 партий) показал: боты
 * выполняют 1075 заданий против 9710 сожжённых, и починка планировщика подсказок
 * дала всего +1.7%. Разбор причины: {@link ObjectiveHints} умеет сказать боту
 * «сыграй Стройку», но не умеет сказать «строй ВОТ НА ЭТОМ гексе». Цепочка
 * {@code kelium.agents.Plan} несёт цель всего в двух параметрах — какое здание
 * строить и какое запитать, — а у цепочки под задание и они пустые. То есть
 * действие выбиралось верно, а цель внутри действия — нет, и условие карты
 * закрывалось только случайно.
 *
 * <p>КАК ЭТО РАБОТАЕТ — СПЕКУЛЯТИВНЫЙ ЖУРНАЛ. Прописывать цель для каждой из 52
 * карт руками не нужно и вредно: карта уже умеет сама сказать, насколько она
 * близка ({@link ObjectiveCard#progress}). Поэтому мы не описываем требование
 * заново, а СПРАШИВАЕМ карту про гипотетическое будущее: копируем журнал хода,
 * записываем в копию тот факт, который оставил бы рассматриваемый выбор
 * («в этот ход я построил на гексе h2_1»), и заново спрашиваем карту. Выросла
 * близость — выбор наводит на цель.
 *
 * <p>Это отвечает ровно на вопрос «а что я должен сделать, чтобы получить это?»
 * и не требует ни одной строки на карту: новая карта получает наведение в тот
 * же день, когда появляется.
 *
 * <p>ГРАНИЦА ПРИМЕНИМОСТИ, о которой надо знать честно. Гипотеза пишется в
 * ЖУРНАЛ ХОДА, а не на поле. Поэтому механизм видит требования-происшествия
 * («В ЭТОТ ХОД построй / перенеси / атакуй») — а их среди проблемных карт
 * большинство, — но не видит требований-состояний («имей три здания на поле»):
 * для них гипотетическое здание пришлось бы ставить на само поле, а это правка
 * состояния партии в момент раздумья, чего делать нельзя. Требования-состояния
 * и так закрываются обычной оценкой позиции: они не привязаны к одному ходу, и
 * бот доходит до них накоплением.
 *
 * <p>ПОЧЕМУ В ДВИЖКЕ, А НЕ В БОТАХ. Здесь нет ни одного решения бота — только
 * ответ на вопрос о правилах: «изменит ли этот факт близость карты». Тем же
 * соседством живёт {@link ObjectiveHints}: движок считает индикаторы, бот ими
 * пользуется. Наведение — тот же индикатор, только про цель, а не про действие.
 */
public final class ObjectiveTargeting {

    private ObjectiveTargeting() {
    }

    // ==================================================================
    //  СЧЁТЧИКИ ДЛЯ ЗАМЕРА — «срабатывает ли наведение вообще»
    // ==================================================================
    //  Заводить их пришлось после того, как две попытки поднять выполнение
    //  заданий дали +1.7% и +1.4% — то есть почти ноль. Прежде чем крутить силу
    //  наводки, надо знать, СРАБАТЫВАЕТ ли она: наводка, которая ни разу не
    //  нашла лучшего выбора, и наводка, которая находит его и проигрывает
    //  жадной оценке, лечатся совершенно по-разному.

    /** Сколько раз спрашивали наводку. */
    public static final java.util.concurrent.atomic.AtomicLong ASKED =
        new java.util.concurrent.atomic.AtomicLong();
    /** Сколько раз наводка нашла прирост близости (> 0). */
    public static final java.util.concurrent.atomic.AtomicLong HITS =
        new java.util.concurrent.atomic.AtomicLong();
    /** Сумма найденных приростов — чтобы знать их типичный размер. */
    public static final java.util.concurrent.atomic.AtomicLong GAIN_MILLI =
        new java.util.concurrent.atomic.AtomicLong();
    /** Сколько раз спрашивали, когда рука заданий пуста или все готовы. */
    public static final java.util.concurrent.atomic.AtomicLong NO_ROOM =
        new java.util.concurrent.atomic.AtomicLong();

    public static void resetCounters() {
        ASKED.set(0);
        HITS.set(0);
        GAIN_MILLI.set(0);
        NO_ROOM.set(0);
    }

    public static String countersLine() {
        long asked = ASKED.get();
        long hits = HITS.get();
        return String.format(java.util.Locale.ROOT,
            "наводка: спрошено %d, пусто/готово %d, попаданий %d (%.2f%%), средний прирост %.3f",
            asked, NO_ROOM.get(), hits, asked == 0 ? 0.0 : 100.0 * hits / asked,
            hits == 0 ? 0.0 : GAIN_MILLI.get() / 1000.0 / hits);
    }

    /**
     * НАСКОЛЬКО ГИПОТЕТИЧЕСКИЙ ФАКТ ПРИБЛИЖАЕТ ЗАДАНИЯ В РУКЕ — сумма прироста
     * близости по всем картам руки.
     *
     * <p>Сумма, а не максимум: выбор, продвигающий сразу две карты, действительно
     * лучше выбора, продвигающего одну. Уже готовые карты пропускаются — их
     * приближать некуда, а держать их в сумме значило бы вечно тянуть бота к
     * тому, что и так сделано.
     *
     * @param hypothetical что записать в журнал, будь выбор сделан; получает
     *                     факты ХОДА ЭТОГО ЖЕ игрока в копии журнала
     * @return прирост суммарной близости (0, если ничего не двинулось)
     */
    public static double gain(GameState s, int seat,
                              Consumer<TurnJournal.TurnFacts> hypothetical) {
        if (s == null || s.journal == null || hypothetical == null) {
            return 0.0;
        }
        ASKED.incrementAndGet();
        PlayerState me = s.player(seat);
        if (me == null || me.objectiveHand.isEmpty()) {
            NO_ROOM.incrementAndGet();
            return 0.0;
        }
        // Карты руки берём один раз: список коротких обращений к реестру дешевле,
        // чем поиск карты внутри двух проходов по руке.
        java.util.List<ObjectiveCard> cards = new java.util.ArrayList<>();
        for (String cid : me.objectiveHand) {
            ObjectiveCard oc = CardRegistry.objective(cid);
            if (oc != null) {
                cards.add(oc);
            }
        }
        if (cards.isEmpty()) {
            return 0.0;
        }

        double before = sumProgress(s, seat, cards);
        if (before >= cards.size()) {
            NO_ROOM.incrementAndGet();
            return 0.0;   // всё готово — приближать нечего
        }

        TurnJournal saved = s.journal;
        double after;
        try {
            // ПОДМЕНА ЖУРНАЛА, А НЕ ПРАВКА: правка настоящего журнала оставила бы
            // выдуманный факт в партии навсегда, даже если выбор не сделан.
            TurnJournal probe = saved.copy();
            hypothetical.accept(probe.of(seat));
            s.journal = probe;
            after = sumProgress(s, seat, cards);
        } finally {
            s.journal = saved;
        }
        double gain = Math.max(0.0, after - before);
        if (gain > 0) {
            HITS.incrementAndGet();
            GAIN_MILLI.addAndGet(Math.round(gain * 1000));
        }
        return gain;
    }

    private static double sumProgress(GameState s, int seat, java.util.List<ObjectiveCard> cards) {
        EngineCardContext ctx = new EngineCardContext(s, seat);
        double sum = 0.0;
        for (ObjectiveCard oc : cards) {
            try {
                sum += clamp(oc.progress(ctx));
            } catch (RuntimeException e) {
                // Условие карты — произвольный код. Сломанная карта не должна
                // ломать наведение по всей руке: она просто не участвует.
                continue;
            }
        }
        return sum;
    }

    private static double clamp(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ==================================================================
    //  ГОТОВЫЕ ГИПОТЕЗЫ — по одной на вид выбора внутри действия
    // ==================================================================

    /** Построить/перенести здание на гекс. {@code cu} — это ЦУ (для o17 и родни). */
    public static double gainFromBuild(GameState s, int seat, String hex, boolean cu) {
        if (hex == null) {
            return 0.0;
        }
        return gain(s, seat, f -> {
            f.builtOnHexes.add(hex);
            f.buildOpHexes.add(hex);
            f.buildOps += 1;
            if (cu) {
                f.cuPlacedHexes.add(hex);
            }
        });
    }

    /** Нанять войско рода {@code kind} (код рода, как в UnitType.code). */
    public static double gainFromProduce(GameState s, int seat, String kind) {
        if (kind == null) {
            return 0.0;
        }
        return gain(s, seat, f -> {
            f.unitsProduced += 1;
            f.producedByType.merge(kind, 1, Integer::sum);
        });
    }
}
