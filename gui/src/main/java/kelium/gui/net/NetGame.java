package kelium.gui.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.gui.GameRecorder;
import kelium.gui.HotSeatWindow;
import kelium.gui.MoveLog;
import kelium.report.ReplayRecord;

/**
 * ПАРТИЯ У ХОСТА БЕЗ ОКНА — тот же стол, что строит окно партии, но места
 * сажает вызывающий. Нужна проверке (две «машины» в одном процессе) и столу,
 * за которым хост сам не играет.
 *
 * <p>Стол собирается ровно как в окне партии: тот же свод с теми же правками
 * раундов, та же копия начального стола с закреплённым зерном — поэтому лента
 * решений отсюда доигрывается и в окне, и наоборот.
 */
public final class NetGame {

    private NetGame() {
    }

    /**
     * Сыграть партию. {@code seatAgent} — агент для мест {@code human} и
     * {@code net:N} (null — посадить бота по строке состава); боты — по
     * справочнику с тем же зерном, что в окне партии.
     *
     * @param moves куда писать ленту решений (номера вариантов по порядку)
     */
    public static ReplayRecord play(HotSeatWindow.Options o, IntFunction<Agent> seatAgent,
                                    Consumer<ReplayRecord> onFrame, List<Integer> moves) {
        return play(o, seatAgent, onFrame, moves, null, List.of());
    }

    /**
     * То же с лентой решений по местам ({@code steps} — для правила отмены,
     * null — не вести) и с доигрыванием: первые {@code prefix} решений берутся
     * из ленты, никого не спрашивая (так партия переигрывается после отмены).
     */
    public static ReplayRecord play(HotSeatWindow.Options o, IntFunction<Agent> seatAgent,
                                    Consumer<ReplayRecord> onFrame, List<Integer> moves,
                                    List<NetSeats.Step> steps, List<Integer> prefix) {
        int players = o.players();
        long seed = o.seed();
        kelium.report.FieldGeometry.useSeatColors(o.seatColors());
        GameConfig cfg = GameConfig.buildCached(o.rulesetId(), players, seed, null, null,
            o.scenarioId(), o.cuFacing(), o.scenarioFile());
        applyRoundOptions(cfg, o);
        GameState built = Setup.buildGame(cfg);
        long pinned = built.rng.nextLong();
        GameState state = built.deepCopy(pinned).deepCopy(pinned);

        List<Agent> agents = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            String spec = o.seatSpecs().get(seat);
            Agent a = seatAgent == null ? null : seatAgent.apply(seat);
            if (a == null) {
                a = kelium.agents.BotCatalog.create(spec, seat, new Random(seed * 131 + seat + 1),
                    players);
            }
            agents.add(a);
            // сетевое место в журнале — живой игрок, как за горячим стулом
            labels.add(NetSeats.claims(spec) ? "human" : spec);
        }
        List<Agent> playing = prefix.isEmpty() ? agents : MoveLog.playback(agents, prefix, null);
        if (steps != null) {
            List<Agent> journaled = new ArrayList<>(playing.size());
            for (Agent a : playing) {
                journaled.add(new Journaled(a, steps));
            }
            playing = journaled;
        }
        playing = MoveLog.recording(playing, moves);
        return GameRecorder.playWithAgents(cfg, state, playing, labels, seed, o.seatColors(),
            null, onFrame);
    }

    /** Правки свода, как их делает окно партии (подготовительный раунд, рынок, стартовые). */
    static void applyRoundOptions(GameConfig cfg, HotSeatWindow.Options o) {
        if (o.startCoins() != null) {
            List<Integer> coins = new ArrayList<>();
            for (int i = 0; i < o.players(); i++) {
                coins.add(o.startCoins());
            }
            cfg.ruleset.override("setup.start_coins", coins);
        }
        if (o.startKelium() != null) {
            cfg.ruleset.override("setup.start_kelium", o.startKelium());
        }
        if (o.startAmmo() != null) {
            cfg.ruleset.override("setup.start_ammo", o.startAmmo());
        }
        if (o.prepRound() != null) {
            cfg.ruleset.override("market.preparatory_round", o.prepRound());
        }
        if (o.marketCards() != null) {
            cfg.ruleset.override("market.deck_size", o.marketCards());
        }
    }

    /**
     * Сыграть сетевую партию у хоста без окна: сетевые места — агенты стола,
     * место хоста — {@code hostAgent} (например, бот). По окончании хост
     * рассылает итог, сид и ленту.
     */
    public static ReplayRecord playHosted(NetHost host, Agent hostAgent) {
        HotSeatWindow.Options o = host.begin();
        List<Integer> moves = java.util.Collections.synchronizedList(new ArrayList<>());
        List<NetSeats.Step> steps = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.atomic.AtomicInteger rewindTo =
            new java.util.concurrent.atomic.AtomicInteger(-1);
        // ОТМЕНА КЛИЕНТОМ: движок стоит в вопросе этому месту; отметка, куда
        // откатиться, — и ожидание размыкается, партия переигрывается ниже.
        host.link(new NetSeats.Link() {
            @Override
            public List<NetSeats.Step> steps() {
                synchronized (steps) {
                    return new ArrayList<>(steps);
                }
            }

            @Override
            public void undoTo(int index, Runnable then) {
                rewindTo.set(index);
                then.run();
            }
        });
        List<Integer> prefix = List.of();
        ReplayRecord rec;
        while (true) {
            try {
                rec = play(o, seat -> {
                    String spec = o.seatSpecs().get(seat);
                    if (NetSeats.claims(spec)) {
                        return host.agent(seat);
                    }
                    return "human".equals(spec) ? hostAgent : null;
                }, host::onFrame, moves, steps, prefix);
                break;
            } catch (kelium.core.GameAborted e) {
                int to = rewindTo.getAndSet(-1);
                if (to < 0) {
                    throw e;             // стол закрыт — это не отмена
                }
                synchronized (moves) {
                    prefix = new ArrayList<>(moves.subList(0, Math.min(to, moves.size())));
                    moves.clear();
                }
                steps.clear();
            }
        }
        host.finish(rec, moves);
        return rec;
    }

    /** Пишущая обёртка: чьё решение, в каком раунде и круге — для правила отмены. */
    private static final class Journaled extends Agent {

        private final Agent inner;
        private final List<NetSeats.Step> steps;

        Journaled(Agent inner, List<NetSeats.Step> steps) {
            super(inner.seat, inner.name);
            this.inner = inner;
            this.steps = steps;
        }

        @Override
        public kelium.core.Choice choose(GameState state, List<kelium.core.Choice> options,
                                         java.util.Map<String, Object> context) {
            int round = state.round;
            int circle = state.circle;
            kelium.core.Choice c = inner.choose(state, options, context);
            steps.add(new NetSeats.Step(seat, round, circle));
            return c;
        }

        @Override
        public void observeEvent(java.util.Map<String, Object> event) {
            inner.observeEvent(event);
        }

        @Override
        public void observePublicEvent(java.util.Map<String, Object> event) {
            inner.observePublicEvent(event);
        }

        @Override
        public boolean specInActionMenu() {
            return inner.specInActionMenu();
        }
    }
}
