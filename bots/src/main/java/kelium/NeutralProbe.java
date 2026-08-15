package kelium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Arena;
import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * NeutralProbe — ТРОГАЮТ ЛИ БОТЫ НЕЙТРАЛЬНЫЕ ПОСТРОЙКИ, и отдельно — БОЛЬШИЕ.
 *
 * <p>Вопрос дизайнера. Нейтралы бывают малые (1 прочность, одна стенка) и большие
 * (2 прочности, две стенки). Большого нельзя снести одним ударом — нужны два
 * попадания, а урон между раундами снимается по кубику в Обновление. Поэтому
 * возможен вырожденный случай: большие нейтралы не сносит вообще никто, и половина
 * этой механики в игре мертва.
 *
 * <p>Замер считает отдельно: сколько нейтралов ПОСТАВЛЕНО на поле, сколько
 * ПОВРЕЖДЕНО хоть раз и сколько СНЕСЕНО — по малым и по большим. Разница между
 * «повредили» и «снесли» у больших и есть ответ: если по ним бьют, но не добивают,
 * значит мешает скорость лечения урона, а не отсутствие интереса.
 *
 * <p>Запуск: {@code kelium.NeutralProbe [игроков] [партий]}.
 */
public final class NeutralProbe {

    private NeutralProbe() {
    }

    private static final class Tally {
        int games;
        int smallOnField;
        int bigOnField;
        int smallRazed;
        int bigRazed;
        int smallHit;
        int bigHit;
        int gamesWithBigRazed;
        int gamesWithBigHit;
        int bigLeftAtEnd;
    }

    private static synchronized void merge(Tally into, Tally one) {
        into.games += one.games;
        into.smallOnField += one.smallOnField;
        into.bigOnField += one.bigOnField;
        into.smallRazed += one.smallRazed;
        into.bigRazed += one.bigRazed;
        into.smallHit += one.smallHit;
        into.bigHit += one.bigHit;
        into.gamesWithBigRazed += one.gamesWithBigRazed;
        into.gamesWithBigHit += one.gamesWithBigHit;
        into.bigLeftAtEnd += one.bigLeftAtEnd;
    }

    private static Tally playOne(int players, long seed, String mode) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);

        Tally t = new Tally();
        t.games = 1;
        // Сколько нейтралов вообще стоит на поле при подготовке.
        for (Hex h : s.field.hexes.values()) {
            for (Hex.NeutralBuilding nb : h.neutrals) {
                if (nb.big) {
                    t.bigOnField++;
                } else {
                    t.smallOnField++;
                }
            }
        }

        List<Agent> agents = new ArrayList<>();
        String[] mixed = {"hawk", "dove", "balanced", "opportunist"};
        int shift = (int) (seed % players);
        for (int i = 0; i < players; i++) {
            Random rng = new Random(seed * 31 + i);
            switch (mode) {
                case "все ястребы" -> agents.add(Bots.create("hawk", i, rng, players));
                case "просчёт вперёд" ->
                    agents.add(Arena.make("search:hawk", i, rng, players));
                default -> agents.add(
                    Bots.create(mixed[(i + shift) % mixed.length], i, rng, players));
            }
        }

        GameEngine.playGame(s, agents, ev -> {
            String type = String.valueOf(ev.get("type"));
            boolean big = Boolean.TRUE.equals(ev.get("big"));
            if ("raze_neutral".equals(type)) {
                if (big) {
                    t.bigRazed++;
                } else {
                    t.smallRazed++;
                }
            } else if ("damage_neutral".equals(type)) {
                // Повреждение большого: удар был, но не добил.
                t.bigHit++;
            }
        });

        if (t.bigRazed > 0) {
            t.gamesWithBigRazed = 1;
        }
        if (t.bigHit > 0 || t.bigRazed > 0) {
            t.gamesWithBigHit = 1;
        }
        for (Hex h : s.field.hexes.values()) {
            for (Hex.NeutralBuilding nb : h.neutrals) {
                if (nb.big) {
                    t.bigLeftAtEnd++;
                }
            }
        }
        return t;
    }

    private static Tally run(int players, int games, String mode) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Tally total = new Tally();
        List<Future<Tally>> futures = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            final long seed = 8_800_000L + g;
            futures.add(pool.submit((Callable<Tally>) () -> playOne(players, seed, mode)));
        }
        for (Future<Tally> f : futures) {
            try {
                merge(total, f.get());
            } catch (Exception e) {
                System.err.println("партия сорвалась: " + e.getMessage());
            }
        }
        pool.shutdown();
        return total;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 300;

        List<String> modes = List.of("вперемешку", "все ястребы", "просчёт вперёд");
        Map<String, Tally> results = new LinkedHashMap<>();
        for (String mode : modes) {
            Tally t = run(players, games, mode);
            results.put(mode, t);
            System.out.printf(Locale.ROOT,
                "  %-16s больших на поле %.1f, снесено больших %.2f, малых %.2f%n",
                mode, t.bigOnField / (double) t.games, t.bigRazed / (double) t.games,
                t.smallRazed / (double) t.games);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Трогают ли боты нейтральные постройки (и особенно БОЛЬШИЕ)\n\n");
        sb.append("По ").append(games).append(" партий на состав, ").append(players)
          .append(" игрока. Малый нейтрал — 1 прочность и одна стенка, большой — ")
          .append("2 прочности и две стенки. Большого нельзя снести одним ударом, а ")
          .append("в Обновление с каждого жетона снимается кубик урона — поэтому ")
          .append("важно смотреть отдельно «били» и «добили».\n\n");
        sb.append("| состав стола | больших на поле | снесено больших | снесено малых "
            + "| партий со сносом большого | больших уцелело к концу |\n");
        sb.append("|---|---:|---:|---:|---:|---:|\n");
        for (String mode : modes) {
            Tally t = results.get(mode);
            int g = Math.max(1, t.games);
            sb.append(String.format(Locale.ROOT,
                "| %s | %.1f | **%.2f** | %.2f | %.0f%% | %.1f |%n",
                mode, t.bigOnField / (double) g, t.bigRazed / (double) g,
                t.smallRazed / (double) g, 100.0 * t.gamesWithBigRazed / g,
                t.bigLeftAtEnd / (double) g));
        }

        Tally mix = results.get("вперемешку");
        Tally deep = results.get("просчёт вперёд");
        sb.append("\n## Ответ\n\n");
        if (mix.bigOnField == 0) {
            sb.append("**Больших нейтралов на поле нет вообще** — в текущих ")
              .append("раскладках они не расставляются, поэтому вопрос об их сносе ")
              .append("не имеет смысла до правки раскладок.\n");
        } else {
            sb.append(String.format(Locale.ROOT,
                "- Больших нейтралов на поле в среднем %.1f за партию.%n",
                mix.bigOnField / (double) Math.max(1, mix.games)));
            sb.append(String.format(Locale.ROOT,
                "- Снесено больших: **%.2f за партию** обычным составом и **%.2f** "
                + "столом просчитывающих ботов. Партий, где большой снесли хоть раз: "
                + "%.0f%% и %.0f%%.%n",
                mix.bigRazed / (double) Math.max(1, mix.games),
                deep.bigRazed / (double) Math.max(1, deep.games),
                100.0 * mix.gamesWithBigRazed / Math.max(1, mix.games),
                100.0 * deep.gamesWithBigRazed / Math.max(1, deep.games)));
            sb.append(String.format(Locale.ROOT,
                "- Для сравнения, малых сносят %.2f за партию — в %.0f раз чаще.%n",
                mix.smallRazed / (double) Math.max(1, mix.games),
                mix.bigRazed == 0 ? 0 : mix.smallRazed / (double) Math.max(1, mix.bigRazed)));
        }

        String report = sb.toString();
        System.out.println();
        System.out.println(report);
        Path out = Path.of("reports", "balance", "нейтралы-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }
}
