package kelium.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.engine.Action;
import kelium.engine.Actions;
import kelium.engine.GameEngine;
import kelium.engine.Passives;
import kelium.engine.Scoring;
import kelium.engine.TurnContext;
import kelium.dataio.Ctx;

/**
 * ПРОСЧЁТ ВПЕРЁД — то, чего у ботов не было вообще.
 *
 * <p>До этого класса каждое решение оценивалось формулой «этот вариант стоит 6.0,
 * тот 4.5», и НИ ОДИН ход не проверялся на деле. Живой игрок так не играет: он
 * мысленно делает ход и смотрит, что получилось. Здесь ровно это и происходит —
 * состояние КОПИРУЕТСЯ ({@link GameState#deepCopy}), на копии ход реально
 * выполняется движком, и полученная позиция оценивается.
 *
 * <p>Два режима, по цене:
 * <ul>
 *   <li>{@link #actionValue} — выполнить ОДНО действие на копии и оценить позицию.
 *       Дёшево (копия + одно действие), годится даже для обучения: именно этот
 *       режим превращает {@code eval.*} веса генома в настоящую оценочную
 *       функцию, которую отбору есть смысл настраивать.</li>
 *   <li>{@link #playOut} — доиграть копию до конца партии (или до горизонта в N
 *       раундов) и взять итог. Дорого, зато честно: так проверяется решение,
 *       последствия которого проявляются через раунды (какой приказ вскрыть).</li>
 * </ul>
 *
 * <p>ЧЕСТНОСТЬ ПО ЗАКРЫТОЙ ИНФОРМАЦИИ. Копия не даёт подглядывать: руки
 * соперников в ней те же карты, что и в жизни, но их ВЫБОР в просчёте делается
 * заново — вскрытие приказов одновременное и закрытое, поэтому «что вскроет
 * сосед» и в жизни неизвестно. Просчёт усредняет по нескольким прогонам с
 * разными зёрнами — это и есть выборка из неизвестного.
 */
public final class Lookahead {

    private Lookahead() {
    }

    /**
     * ИТОГ ПАРТИИ глазами места {@code seat}: отрыв от сильнейшего соперника плюс
     * крупная премия за победу.
     *
     * <p>Именно ОТРЫВ, а не свои очки: игра многопользовательская, и «набрать 10
     * при 14 у лидера» хуже, чем «набрать 8 при 6». Премия за победу отдельно —
     * играют на победу, а не на среднее.
     */
    public static double finalScore(GameState s, int seat) {
        int vp = Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
        int rivalMax = Integer.MIN_VALUE;
        for (int st = 0; st < s.numPlayers(); st++) {
            if (st == seat) {
                continue;
            }
            rivalMax = Math.max(rivalMax,
                Scoring.scorePlayer(s, st).getOrDefault("total", 0));
        }
        double margin = rivalMax == Integer.MIN_VALUE ? vp : vp - rivalMax;
        double win = s.winner != null && s.winner == seat ? 10.0 : 0.0;
        return margin + win;
    }

    /**
     * ИТОГ ОБОРВАННОГО НА ГОРИЗОНТЕ ПРОСЧЁТА — не то же самое, что итог партии.
     *
     * <p>Зачем понадобилось. Просчёт на два-три раунда вперёд не давал прибавки
     * силы, сколько ресурсов в него ни вкладывай: щедрый режим не обгонял скромный.
     * Причина оказалась не в переборе, а в СУДЬЕ. Оборванная партия оценивалась
     * очками на столе, а очки в этой игре приходят В КОНЦЕ: наука отдаёт вершинами
     * треков, задания — закрытием, тайлы — исчерпыванием. На середине партии
     * лидирует тот, кто НЕ вложился, поэтому «победитель» оборванной партии —
     * ложный ориентир, и просчёт на три раунда судил ровно так же жадно, как на
     * один. Дальше смотреть было бесполезно.
     *
     * <p>Что теперь. Очки на столе остаются (они настоящие), но премия за «победу»
     * посреди партии снимается — победы там нет, — а к отрыву по очкам добавляется
     * ОТРЫВ ПО ПОЛОЖЕНИЮ: во сколько бот оценивает свою позицию против лучшей
     * чужой. Вес положения — ручка генома {@code search.horizon_pos}: обучение
     * вправе решить, насколько верить обещанию против уже набранного.
     *
     * <p>ЭТО ПРОВЕРЕНО ОТДЕЛЬНО и оказалось самой крупной одиночной прибавкой в
     * просчёте: тот же бот с весом положения 0.5 обходит себя же с весом 0 в 59%
     * очных сравнений (224 партии), а вес 1.5 не лучше 0.5 (53%). При этом
     * КОНКРЕТНЫЕ веса внутри оценки почти безразличны (замена всех двадцати девяти
     * на исходные — ничья). Читается это так: важно САМО НАЛИЧИЕ поправки на
     * «недоигранность» партии, а не тонкая настройка её слагаемых.
     */
    public static double horizonScore(GameState s, int seat, Genome mine) {
        int vp = Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
        int rivalVp = Integer.MIN_VALUE;
        double own = StrategicAgent.evaluate(s, seat, mine);
        double rivalPos = Double.NEGATIVE_INFINITY;
        for (int st = 0; st < s.numPlayers(); st++) {
            if (st == seat) {
                continue;
            }
            rivalVp = Math.max(rivalVp, Scoring.scorePlayer(s, st).getOrDefault("total", 0));
            rivalPos = Math.max(rivalPos, StrategicAgent.evaluate(s, st, mine));
        }
        double vpMargin = rivalVp == Integer.MIN_VALUE ? vp : vp - rivalVp;
        double posMargin = Double.isInfinite(rivalPos) ? own : own - rivalPos;
        return vpMargin + mine.get("search.horizon_pos", 0.5) * posMargin;
    }

    /**
     * Собрать «моделей игроков» для просчёта: на своём месте — свой геном, на
     * чужих — общий стратег. Это САМОМОДЕЛЬ: бот считает, что соперники играют
     * так же разумно, как он сам. Обычная и правильная посылка — переоценивать
     * соперника безопаснее, чем недооценивать.
     *
     * <p>В моделях стоит ОБЫЧНЫЙ {@link StrategicAgent}, а не просчитывающий:
     * иначе просчёт внутри просчёта разложил бы машину на порядки.
     */
    /**
     * ПРИВЫЧКИ СОПЕРНИКОВ на время просчёта. Ставится вызывающим ботом перед
     * просчётом и снимается после: тащить их через все подписи ради одного
     * необязательного улучшения — значит переписать половину вызовов.
     */
    private static final ThreadLocal<OrderHabits> HABITS = new ThreadLocal<>();

    /** Учитывать в доигрываниях привычки соперников (или {@code null} — не учитывать). */
    public static void useHabits(OrderHabits habits) {
        HABITS.set(habits);
    }

    private static List<Agent> modelAgents(GameState s, int seat, Genome mine,
                                           Genome others, long seed) {
        // ЗА СЕБЯ В ПРОСЧЁТЕ можно играть внимательнее, чем жадной формулой:
        // иначе бот примеряет приказ к тому, как его отыграет ЖАДНЫЙ игрок, и
        // недооценивает приказы, которые требуют аккуратной игры. Ручка
        // {@code search.rollout_smart} включает в доигрывании отсев холостых ходов
        // за своё место (вложенных доигрываний там нет — иначе цена растёт в разы).
        boolean smartSelf = mine.get("search.rollout_smart", 0.0) > 0.5;
        OrderHabits habits = HABITS.get();
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < s.numPlayers(); i++) {
            Genome g = i == seat ? mine : others;
            Random r = new Random(seed * 31 + i + 7);
            Agent a = i == seat && smartSelf
                ? new SearchAgent(i, r, g, "просчёт-в-просчёте", 4, 0, 0, 0, 0, 0)
                : new StrategicAgent(i, r, g);
            // За СОПЕРНИКОВ приказы вскрываются по их привычкам, если бот их
            // запомнил: совпадение приказов блокирует ход, и предсказывать надо
            // именно это.
            if (i != seat && habits != null && habits.seen() > 0) {
                a = new HabitAgent(a, habits, i, new Random(seed * 71 + i));
            }
            agents.add(a);
        }
        return agents;
    }

    /**
     * Что дал просчёт одного действия.
     *
     * @param ok         движок вообще позволил его сыграть
     * @param changed    в позиции ХОТЬ ЧТО-ТО материально изменилось
     * @param value      оценка позиции сразу после действия
     * @param playOut    итог доигранной партии (или {@code NaN}, если не играли)
     */
    public record ActionOutcome(boolean ok, boolean changed, double value, double playOut) {

        static final ActionOutcome FAILED = new ActionOutcome(false, false, 0, Double.NaN);
    }

    /**
     * ПРОСЧЁТ ОДНОГО ДЕЙСТВИЯ: выполнить {@code actionName} на копии состояния и
     * посмотреть, что вышло.
     *
     * <p>Главное здесь — признак {@code changed}: изменилось ли в позиции хоть
     * что-нибудь материальное (ресурсы, жетоны, запитанность, треки, трофеи, урон
     * на чужих зданиях, карты). Это и есть честное определение ХОЛОСТОГО ХОДА, и
     * никакая формула его дать не может: «Бой» без единой достижимой цели, «Добыча»
     * с выработанной жилы и «Сборка» без места на складе с точки зрения движка
     * проходят успешно, а в позиции не меняют ничего.
     *
     * <p>Почему нельзя оценивать действие ПРОСТО по росту оценки позиции. Проверено
     * на лиге: так бот становится слабее. Любое ВЛОЖЕНИЕ — Стройка (минус монеты),
     * Маркет (минус келемий) — на один шаг вперёд выглядит убытком, и бот
     * перестаёт строить и продавать, то есть перестаёт развиваться. Отличить
     * вложение от растраты можно только доиграв партию, поэтому глубокий режим
     * ({@code horizon > 0}) именно это и делает.
     *
     * <p>ОГОВОРКА ПО НАЦЕНКАМ: у копии заводится ЧИСТЫЙ учёт хода
     * ({@link TurnContext}), поэтому наценка за вторую операцию того же действия в
     * просчёте не видна. Наценки плоские (+1 монета), на сравнение вариантов это
     * почти не влияет, а тащить учёт хода до агента значило бы протянуть
     * служебный объект движка через весь интерфейс решений.
     */
    public static ActionOutcome actionOutcome(GameState s, int seat, String actionName,
                                              Genome mine, Genome others,
                                              int horizon, long seed) {
        GameState c = s.deepCopy(seed);
        List<Agent> agents = modelAgents(c, seat, mine, others, seed);
        GameEngine.bindResume(c, agents, null);
        PlayerState p = c.player(seat);
        int specLimit = Math.max(Ctx.rules(c).getInt("actions.spec_per_turn"),
            Passives.specActions(c, seat));
        TurnContext ctx = new TurnContext(seat, specLimit);
        long before = materialSignature(s, seat);
        try {
            Action action = Actions.create(actionName, c);
            if (!action.perform(p, ctx, agents.get(seat)).ok()) {
                return ActionOutcome.FAILED;
            }
        } catch (RuntimeException e) {
            return ActionOutcome.FAILED;
        }
        boolean changed = materialSignature(c, seat) != before;
        double value = StrategicAgent.evaluate(c, seat, mine);
        double playOut = Double.NaN;
        if (horizon > 0) {
            // Доигрываем СО СЛЕДУЮЩЕГО круга: этот круг у меня уже отыгран
            // выбранным действием, повторять его нельзя.
            c.circle = c.circle + 1;
            GameEngine engine = new GameEngine(c, agents, null)
                .withRoundLimit(c.round + horizon);
            try {
                engine.resume();
                // Партия оборвана на горизонте — судим по положению, а не по
                // очкам на середине (см. horizonScore).
                playOut = horizonScore(c, seat, mine);
            } catch (RuntimeException e) {
                playOut = Double.NaN;
            }
        }
        return new ActionOutcome(true, changed, value, playOut);
    }

    /**
     * МАТЕРИАЛЬНЫЙ ОТПЕЧАТОК позиции игрока: всё, что можно потрогать руками за
     * столом. Если после действия отпечаток тот же — действие было холостым, чем
     * бы оно ни называлось.
     */
    public static long materialSignature(GameState s, int seat) {
        PlayerState p = s.player(seat);
        long h = 1469598103934665603L;
        h = mix(h, p.resources.coin());
        h = mix(h, p.resources.kelium());
        h = mix(h, p.resources.ammo());
        h = mix(h, p.resources.debris());
        h = mix(h, p.trophySpacePoints());
        h = mix(h, p.containers);
        h = mix(h, p.objectiveHand.size());
        h = mix(h, p.arsenalHand.size());
        h = mix(h, p.arsenalInstalled.size());
        h = mix(h, p.superObjectiveProgress);
        h = mix(h, p.cuDestructionTokens);
        h = mix(h, p.cuKills);
        h = mix(h, p.flippedStartTiles + p.flippedNormalTiles);
        h = mix(h, p.redModules + p.blueModules + p.goldModules);
        h = mix(h, p.storageTokens.size());
        for (int v : p.techSteps.values()) {
            h = mix(h, v);
        }
        // Жетоны: где стоят, запитаны ли, сколько на них урона — этим действия и
        // отличаются друг от друга (движение, стройка, бой).
        for (kelium.core.UnitToken u : p.units) {
            h = mix(h, u.uid);
            h = mix(h, u.hexId == null ? 0 : u.hexId.hashCode());
            h = mix(h, u.damage);
        }
        for (kelium.core.BuildingToken b : p.buildings) {
            h = mix(h, b.uid);
            h = mix(h, b.hexId == null ? 0 : b.hexId.hashCode());
            h = mix(h, b.damage);
            h = mix(h, b.energyPlaced);
            h = mix(h, b.energyIdle);
        }
        // Урон, нанесённый чужим жетонам: без этого Бой, снявший половину ЦУ, но
        // никого не убивший, считался бы холостым.
        for (PlayerState other : s.players) {
            if (other.seat == seat) {
                continue;
            }
            for (kelium.core.UnitToken u : other.units) {
                h = mix(h, u.damage);
            }
            for (kelium.core.BuildingToken b : other.buildings) {
                h = mix(h, b.damage);
            }
        }
        // Келемий на жилах: Добыча меняет поле, даже если склад полон.
        for (kelium.core.Hex hx : s.field.hexes.values()) {
            if (hx.spawnTile != null) {
                h = mix(h, hx.spawnTile.kelium);
            }
        }
        return h;
    }

    private static long mix(long h, int v) {
        return (h ^ v) * 1099511628211L;
    }

    /**
     * ДОИГРАТЬ ПАРТИЮ с текущего круга и вернуть итог глазами {@code seat}.
     *
     * @param forced  что мой бот обязан выбрать первым решением заданного вида
     *                (например конкретную карту приказа); {@code null} — играть
     *                свободно
     * @param horizon сколько раундов вперёд доигрывать (0 = до конца партии)
     */
    public static double playOut(GameState s, int seat, Genome mine, Genome others,
                                 ForcedAgent.Forced forced, int horizon, long seed) {
        GameState c = s.deepCopy(seed);
        List<Agent> agents = modelAgents(c, seat, mine, others, seed);
        if (forced != null) {
            agents.set(seat, new ForcedAgent(agents.get(seat), forced));
        }
        GameEngine engine = new GameEngine(c, agents, null);
        boolean truncated = horizon > 0;
        if (truncated) {
            engine.withRoundLimit(c.round + horizon - 1);
        }
        try {
            engine.resume();
        } catch (RuntimeException e) {
            // Партия в просчёте сорвалась — вариант считаем нейтральным, а не
            // лучшим: молча возвращать 0 честнее, чем выдавать сбой за находку.
            return 0.0;
        }
        // Доиграли до конца — судим настоящим итогом; оборвали на горизонте —
        // по положению, иначе просчёт вперёд остаётся таким же жадным (см.
        // horizonScore).
        return truncated ? horizonScore(c, seat, mine) : finalScore(c, seat);
    }
}
