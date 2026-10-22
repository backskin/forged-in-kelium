package kelium;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import kelium.agents.HeuristicAgent;
import kelium.agents.PassiveAgent;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * СОБИРАТЕЛЬ КАРТ (заказ дизайнера 13.10.2026): сколько карт заданий и
 * арсенала игрок может держать в руке, если копит их и ничего не тратит.
 *
 * <p>Собиратель — обёртка над эвристическим ботом: обычные ходы (добыча,
 * стройка, бой) делает бот, а собиратель (1) запрещает тратить карты —
 * выполнять, сжигать, ставить; (2) когда среди вариантов есть такой, что даёт
 * карту задания или арсенала, берёт его. Цены за карты списывает движок.
 *
 * <p>Прогон А: один собиратель и трое манекенов (потолок, никто не мешает).
 * Прогон Б: четыре собирателя. Запуск: {@code kelium.CardHoarderProbe [партий]}.
 */
public final class CardHoarderProbe {

    private CardHoarderProbe() {
    }

    /** Варианты, которые тратят карты из руки. */
    static final Set<String> SPEND = Set.of(
        "spec_objective", "spec_objective_enh", "spec_objective_burn",
        "free_objective_burn", "spec_arsenal_install", "spec_arsenal_burn",
        "reaction_burn");

    static boolean givesCard(Choice c) {
        String s = (c.kind() + " " + c.label() + " " + c.payload()).toLowerCase(Locale.ROOT);
        return s.contains("objective") || s.contains("arsenal")
            || s.contains("задани") || s.contains("арсенал");
    }

    static int cards(PlayerState p) {
        return p.objectiveHand.size() + p.arsenalHand.size();
    }

    /** Собиратель: не тратит карты, берёт варианты с картой. */
    static final class Hoarder extends Agent {
        final Agent base;
        String lastKind = "старт";
        int lastCards = -1;
        final Map<String, Integer> sources = new TreeMap<>();

        Hoarder(int seat, long seed) {
            super(seat, "собиратель#" + seat);
            base = new HeuristicAgent(seat, new Random(seed), "economist");
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
            PlayerState me = state.player(seat);
            int now = cards(me);
            if (lastCards >= 0 && now > lastCards) {
                sources.merge(lastKind, now - lastCards, Integer::sum);
            }
            List<Choice> allowed = new ArrayList<>();
            for (Choice o : options) {
                if (!SPEND.contains(o.kind())) {
                    allowed.add(o);
                }
            }
            if (allowed.isEmpty()) {
                allowed = options;
            }
            Choice pick = null;
            for (Choice o : allowed) {
                if (givesCard(o)) {
                    pick = o;
                    break;
                }
            }
            if (pick == null) {
                Choice b = base.choose(state, allowed, context);
                pick = allowed.contains(b) ? b : allowed.get(0);
            }
            lastKind = pick.kind();
            lastCards = cards(state.player(seat));
            return pick;
        }
    }

    record Run(int[][] byRound, int[] finalObj, int[] finalArs, Map<String, Integer> sources,
               int[] rounds) {
    }

    static Run run(int games, boolean allHoarders) {
        int maxR = 12;
        int[][] byRound = new int[games][maxR + 1];
        int[] fo = new int[games];
        int[] fa = new int[games];
        int[] rounds = new int[games];
        Map<String, Integer> src = new TreeMap<>();
        for (int g = 0; g < games; g++) {
            long seed = 7000L + g;
            GameConfig cfg = GameConfig.buildCached(GameConfig.GAME_RULESET, 4, seed, null, null);
            GameState state = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            List<Hoarder> hs = new ArrayList<>();
            for (int s = 0; s < 4; s++) {
                if (s == 0 || allHoarders) {
                    Hoarder h = new Hoarder(s, seed * 10 + s);
                    hs.add(h);
                    agents.add(h);
                } else {
                    agents.add(new PassiveAgent(s));
                }
            }
            int[] row = byRound[g];
            GameEngine.playGame(state, agents, ev -> {
                int r = Math.min(state.round, maxR);
                int c = cards(state.player(0));
                if (c > row[r]) {
                    row[r] = c;
                }
            });
            fo[g] = state.player(0).objectiveHand.size();
            fa[g] = state.player(0).arsenalHand.size();
            rounds[g] = state.round;
            for (Hoarder h : hs) {
                h.sources.forEach((k, v) -> src.merge(k, v, Integer::sum));
            }
            if ((g + 1) % 10 == 0) {
                System.out.printf("  %s: %d/%d партий%n", allHoarders ? "Б" : "А", g + 1, games);
            }
        }
        return new Run(byRound, fo, fa, src, rounds);
    }

    static String stats(int[] v) {
        int[] s = v.clone();
        Arrays.sort(s);
        double mean = Arrays.stream(s).average().orElse(0);
        return String.format(Locale.ROOT, "среднее %.1f · медиана %d · 9 из 10 ≤ %d · максимум %d",
            mean, s[s.length / 2], s[(int) Math.min(s.length - 1, Math.floor(s.length * 0.9))],
            s[s.length - 1]);
    }

    static void report(String title, Run r, int hoarders) {
        int games = r.finalObj.length;
        System.out.println();
        System.out.println("=== " + title + " ===");
        System.out.println("раундов в партии: " + stats(r.rounds));
        System.out.println("карт в руке (задания + арсенал), наибольшее за раунд:");
        for (int k = 1; k <= 12; k++) {
            int[] col = new int[games];
            int n = 0;
            for (int g = 0; g < games; g++) {
                if (r.rounds[g] >= k) {
                    col[n++] = r.byRound[g][k];
                }
            }
            if (n == 0) {
                break;
            }
            System.out.printf("  раунд %2d (%3d партий): %s%n", k, n, stats(Arrays.copyOf(col, n)));
        }
        System.out.println("к концу партии: задания — " + stats(r.finalObj));
        System.out.println("к концу партии: арсенал — " + stats(r.finalArs));
        int total = r.sources.values().stream().mapToInt(Integer::intValue).sum();
        System.out.printf("откуда карты (на %d собирателей × %d партий, всего %d):%n",
            hoarders, games, total);
        r.sources.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> System.out.printf(Locale.ROOT, "  %-28s %5d  (%.0f%%)%n",
                e.getKey(), e.getValue(), 100.0 * e.getValue() / Math.max(1, total)));
    }

    public static void main(String[] args) {
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        System.out.println("Собиратель карт, свод " + GameConfig.GAME_RULESET + ", партий: " + games);
        long t0 = System.currentTimeMillis();
        Run a = run(games, false);
        report("А. Один собиратель и трое манекенов", a, 1);
        Run b = run(games, true);
        report("Б. Четыре собирателя (цифры по месту 1, источники — по всем)", b, 4);
        System.out.printf("%nвремя: %.0f с%n", (System.currentTimeMillis() - t0) / 1000.0);
    }
}
