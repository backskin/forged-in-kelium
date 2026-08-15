package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.agents.Genome;
import kelium.agents.Lookahead;
import kelium.agents.SearchAgent;
import kelium.agents.StrategicAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * SearchProbe — пробник просчёта вперёд. Отвечает на три вопроса, каждый из
 * которых надо проверить ДО того, как строить на этом обучение:
 * <ol>
 *   <li>копия состояния честная? (копию доигрываем — партия не падает, итоги
 *       осмысленны, исходное состояние не испорчено);</li>
 *   <li>сколько это стоит по времени (во сколько раз медленнее обычного бота);</li>
 *   <li>просчёт вообще меняет решения или формула и так угадывает?</li>
 * </ol>
 *
 * <p>Запуск: {@code kelium.SearchProbe [игроков] [партий]}.
 */
public final class SearchProbe {

    private SearchProbe() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 3;

        System.out.println("=== 1. ЧЕСТНОСТЬ КОПИИ СОСТОЯНИЯ ===");
        checkCopy(players);

        System.out.println();
        System.out.println("=== 2. ЦЕНА ПРОСЧЁТА И ЕГО ПОЛЬЗА ===");
        long tPlain = 0;
        long tSearch = 0;
        int plainVp = 0;
        int searchVp = 0;
        int plainWins = 0;
        int searchWins = 0;
        String stats = "";
        for (int g = 0; g < games; g++) {
            long seed = 5000L + g;
            int seat = g % players;

            long t0 = System.nanoTime();
            int[] r1 = playOne(players, seed, seat, false);
            tPlain += System.nanoTime() - t0;
            plainVp += r1[0];
            plainWins += r1[1];

            t0 = System.nanoTime();
            int[] r2 = playOne(players, seed, seat, true);
            tSearch += System.nanoTime() - t0;
            searchVp += r2[0];
            searchWins += r2[1];
            stats = LAST_STATS;
            System.out.printf(Locale.ROOT,
                "  партия %d (место %d): формула ПО=%d%s | просчёт ПО=%d%s%n",
                g + 1, seat, r1[0], r1[1] == 1 ? " ПОБЕДА" : "",
                r2[0], r2[1] == 1 ? " ПОБЕДА" : "");
        }
        System.out.printf(Locale.ROOT,
            "%nформула: ПО в среднем %.2f, побед %d/%d, время %.1f с%n",
            plainVp / (double) games, plainWins, games, tPlain / 1e9);
        System.out.printf(Locale.ROOT,
            "просчёт: ПО в среднем %.2f, побед %d/%d, время %.1f с (медленнее в %.1f раза)%n",
            searchVp / (double) games, searchWins, games, tSearch / 1e9,
            tPlain == 0 ? 0 : (double) tSearch / tPlain);
        System.out.println("последняя партия — " + stats);
    }

    private static String LAST_STATS = "";

    /** Проверка копии: доиграть копию, убедиться, что оригинал не пострадал. */
    private static void checkCopy(int players) {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players, 777L,
            null, null);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            agents.add(new StrategicAgent(i, new Random(100 + i), Genome.defaults()));
        }
        // Играем 2 раунда «настоящей» партии, чтобы состояние стало непустым.
        // Именно runToRound: он НЕ подводит итоги, партия остаётся живой.
        new GameEngine(s, agents, null).runToRound(2);

        String before = fingerprint(s);
        int roundBefore = s.round;
        double v0 = Lookahead.finalScore(s, 0);
        // Доигрываем ТРИ копии — итоги должны различаться (разные зёрна), а
        // оригинал остаться байт-в-байт прежним.
        List<Double> outs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            outs.add(Lookahead.playOut(s, 0, Genome.defaults(), Genome.defaults(),
                null, 0, 900 + i));
        }
        String after = fingerprint(s);
        System.out.println("  отпечаток оригинала до просчёта:    " + before);
        System.out.println("  отпечаток оригинала после просчёта: " + after);
        System.out.println(before.equals(after)
            ? "  ОК: просчёт не испортил настоящую партию"
            : "  ОШИБКА: копия протекла в оригинал!");
        System.out.println("  раунд оригинала " + roundBefore
            + ", оценка позиции места 0 сейчас " + String.format(Locale.ROOT, "%.1f", v0));
        System.out.println("  итоги трёх доигрываний: " + outs
            + (outs.get(0).equals(outs.get(1)) && outs.get(1).equals(outs.get(2))
                ? "  (внимание: все совпали — проверь зёрна)" : "  (различаются — так и надо)"));
    }

    /** Короткий отпечаток состояния: по нему видно любую утечку копии. */
    private static String fingerprint(GameState s) {
        StringBuilder sb = new StringBuilder();
        sb.append('r').append(s.round).append('c').append(s.circle);
        for (var p : s.players) {
            sb.append(" |").append(p.seat).append(':')
              .append(p.resources.coin()).append('/')
              .append(p.resources.kelium()).append('/')
              .append(p.resources.ammo())
              .append(" b").append(p.buildingsOnField().size())
              .append(" u").append(p.unitsOnField().size())
              .append(" o").append(p.orderHand.size())
              .append(" j").append(p.objectiveHand.size());
        }
        int tiles = 0;
        for (var h : s.field.hexes.values()) {
            if (h.spawnTile != null) {
                tiles += h.spawnTile.kelium;
            }
        }
        sb.append(" tiles=").append(tiles);
        return sb.toString();
    }

    /** Партия: на месте {@code seat} — просчитывающий или обычный бот. */
    private static int[] playOne(int players, long seed, int seat, boolean search) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        SearchAgent probe = null;
        for (int i = 0; i < players; i++) {
            Random rng = new Random(seed * 31 + i);
            if (i == seat && search) {
                probe = SearchAgent.fast(i, rng, Bots.genome("balanced", players), "search");
                agents.add(probe);
            } else {
                agents.add(Bots.create("balanced", i, rng, players));
            }
        }
        Map<String, Object> res = GameEngine.playGame(s, agents, null);
        if (probe != null) {
            LAST_STATS = probe.searchStats();
        }
        int vp = Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
        int win = res.get("winner") instanceof Number w && w.intValue() == seat ? 1 : 0;
        return new int[]{vp, win};
    }
}
