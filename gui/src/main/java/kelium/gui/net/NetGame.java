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
        List<Agent> playing = MoveLog.recording(agents, moves);
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
        List<Integer> moves = new ArrayList<>();
        ReplayRecord rec = play(o, seat -> {
            String spec = o.seatSpecs().get(seat);
            if (NetSeats.claims(spec)) {
                return host.agent(seat);
            }
            return "human".equals(spec) ? hostAgent : null;
        }, host::onFrame, moves);
        host.finish(rec, moves);
        return rec;
    }
}
