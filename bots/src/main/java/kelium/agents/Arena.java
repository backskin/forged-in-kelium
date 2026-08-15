package kelium.agents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * ARENA — ЛИГА БОТОВ с рейтингом Эло. Единственная общая линейка в системе.
 *
 * <p>Зачем. Раньше сила ботов описывалась несравнимыми числами: «fitness 10.44»,
 * «35% побед против трёх эвристик», «средние ПО 4.33». Эти цифры нельзя было
 * сопоставить ни между собой, ни между сессиями: fitness зависел от формулы,
 * процент побед — от того, кто сидел за столом. Понять, стал ли бот сильнее
 * после правки, было в принципе невозможно.
 *
 * <p>Эло решает именно это: одно число на одной шкале для ЛЮБОГО участника —
 * случайного бота, эвристики, генома характера, просчитывающего бота. Разница в
 * 100 очков Эло всегда значит одно и то же, независимо от того, кто с кем играл.
 * Заодно лига — готовый ответ дизайнеру на вопрос «эта правка правил кого
 * усилила»: прогнать лигу до и после.
 *
 * <p>МНОГОПОЛЬЗОВАТЕЛЬСКИЙ ЭЛО. Партия на 3-4 игрока разбирается на все ПАРЫ
 * участников: кто кого обошёл по очкам. Это стандартный приём, и он честнее, чем
 * «победитель забирает всё»: второе место в сильной компании — не то же самое,
 * что второе место из двух.
 */
public final class Arena {

    /** Участник лиги: имя + способ посадить его за стол. */
    public record Fighter(String name, String spec) {
    }

    /** Итог участника в лиге. */
    public static final class Standing {
        public final String name;
        public double elo = 1500.0;
        public int games = 0;
        public int wins = 0;
        public int vpSum = 0;
        public int vpBest = 0;

        Standing(String name) {
            this.name = name;
        }

        public double winRate() {
            return games == 0 ? 0 : (double) wins / games;
        }

        public double avgVp() {
            return games == 0 ? 0 : (double) vpSum / games;
        }
    }

    private final int players;
    private final Map<String, Standing> table = new ConcurrentHashMap<>();
    /**
     * ОЧНЫЕ СЧЁТЫ: «кто кого обошёл по очкам», ключ «А|Б» → сколько раз А выше Б.
     *
     * <p>Зачем отдельно от Эло. Эло — одно число, и оно сглаживает: замер 13.08.2026
     * дал участника с самой высокой долей побед и при этом с НИЗШИМ Эло, и понять
     * по одному числу, кто врёт, было нельзя. Очный счёт врать не умеет — это просто
     * подсчёт. Эло остаётся для общей шкалы, решения принимаем по очным счетам.
     */
    private final Map<String, int[]> headToHead = new ConcurrentHashMap<>();
    private final List<Fighter> fighters;
    private final double k;

    public Arena(int players, List<Fighter> fighters) {
        this(players, fighters, 16.0);
    }

    public Arena(int players, List<Fighter> fighters, double k) {
        this.players = players;
        this.fighters = new ArrayList<>(fighters);
        this.k = k;
        for (Fighter f : fighters) {
            table.put(f.name(), new Standing(f.name()));
        }
    }

    /** Один стол: результат = ПО по местам плюс кто объявлен победителем. */
    private record TableResult(List<String> names, int[] vp, int winner) {
    }

    private TableResult playTable(List<Fighter> group, long seed) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            Fighter f = group.get(i % group.size());
            names.add(f.name());
            agents.add(make(f.spec(), i, new Random(seed * 131 + i * 17 + 3), players));
        }
        Map<String, Object> res = GameEngine.playGame(s, agents, null);
        int[] vp = new int[players];
        for (int i = 0; i < players; i++) {
            vp[i] = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
        }
        int winner = res.get("winner") instanceof Number w ? w.intValue() : -1;
        return new TableResult(names, vp, winner);
    }

    /** Обновить рейтинги по одному столу (разбор на все пары). */
    private synchronized void applyResult(TableResult r) {
        int n = r.vp().length;
        for (int i = 0; i < n; i++) {
            Standing si = table.get(r.names().get(i));
            si.games++;
            si.vpSum += r.vp()[i];
            si.vpBest = Math.max(si.vpBest, r.vp()[i]);
            if (i == r.winner()) {
                si.wins++;
            }
        }
        double[] delta = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j || r.names().get(i).equals(r.names().get(j))) {
                    continue;   // сам с собой рейтинг не выясняет
                }
                double ri = table.get(r.names().get(i)).elo;
                double rj = table.get(r.names().get(j)).elo;
                double expected = 1.0 / (1.0 + Math.pow(10.0, (rj - ri) / 400.0));
                double actual = r.vp()[i] > r.vp()[j] ? 1.0
                    : (r.vp()[i] == r.vp()[j] ? 0.5 : 0.0);
                delta[i] += k * (actual - expected) / Math.max(1, n - 1);
                // Тот же самый исход, но записанный в лоб: побед / ничьих / всего.
                int[] hh = headToHead.computeIfAbsent(
                    r.names().get(i) + "|" + r.names().get(j), key -> new int[3]);
                if (actual == 1.0) {
                    hh[0]++;
                } else if (actual == 0.5) {
                    hh[1]++;
                }
                hh[2]++;
            }
        }
        for (int i = 0; i < n; i++) {
            table.get(r.names().get(i)).elo += delta[i];
        }
    }

    /**
     * Прогнать лигу: {@code rounds} кругов, в каждом участники тасуются и
     * разбиваются по столам, а внутри стола РОТИРУЮТСЯ МЕСТА. Ротация
     * обязательна: стартовый порядок в этой игре даёт заметное преимущество, и
     * без неё лига мерила бы удачу посадки, а не силу бота.
     */
    public void run(int rounds, long baseSeed) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Random rng = new Random(baseSeed);
            for (int round = 0; round < rounds; round++) {
                List<Fighter> shuffled = new ArrayList<>(fighters);
                Collections.shuffle(shuffled, rng);
                List<Future<TableResult>> futures = new ArrayList<>();
                for (int start = 0; start < shuffled.size(); start++) {
                    List<Fighter> group = new ArrayList<>();
                    for (int i = 0; i < players; i++) {
                        group.add(shuffled.get((start + i) % shuffled.size()));
                    }
                    // ротация мест внутри стола: каждый участник садится на
                    // каждое место по разу
                    for (int rot = 0; rot < players; rot++) {
                        List<Fighter> seated = new ArrayList<>();
                        for (int i = 0; i < players; i++) {
                            seated.add(group.get((i + rot) % players));
                        }
                        long seed = baseSeed + round * 100_003L + start * 97L + rot;
                        Callable<TableResult> job = () -> playTable(seated, seed);
                        futures.add(pool.submit(job));
                    }
                }
                for (Future<TableResult> f : futures) {
                    try {
                        applyResult(f.get());
                    } catch (Exception e) {
                        System.err.println("стол сорвался: " + e.getMessage());
                    }
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    /** Таблица лиги, отсортированная по Эло сверху вниз. */
    public List<Standing> standings() {
        fitRatings();
        List<Standing> out = new ArrayList<>(table.values());
        out.sort(Comparator.comparingDouble((Standing st) -> -st.elo));
        return out;
    }

    /**
     * ПОДГОНКА РЕЙТИНГОВ ПО ОЧНЫМ СЧЕТАМ (Брэдли–Терри) — вместо пошагового Эло.
     *
     * <p>Зачем переделано. Пошаговый Эло правил рейтинг после каждой партии и
     * потому зависел от ПОРЯДКА партий: замер 13.08.2026 выдал участника, который
     * обходит по очкам всех трёх соперников (50%, 57%, 60%), но по Эло оказался
     * последним. Такой шкале верить нельзя, а решения про ботов мы принимаем именно
     * по ней.
     *
     * <p>Здесь рейтинги подбираются СРАЗУ ПО ВСЕМ очным счетам разом, обычным
     * умножающим приближением: сила участника пропорциональна его победам, делённым
     * на сумму «шансов против каждого соперника». Порядок партий на итог не влияет
     * вовсе, а результат по построению согласован с очной таблицей.
     */
    private void fitRatings() {
        List<String> names = new ArrayList<>(table.keySet());
        int n = names.size();
        if (n < 2 || headToHead.isEmpty()) {
            return;
        }
        double[] strength = new double[n];
        java.util.Arrays.fill(strength, 1.0);
        // Победы (с ничьими по половинке) и число встреч между каждой парой.
        double[][] won = new double[n][n];
        double[][] met = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                int[] hh = headToHead.get(names.get(i) + "|" + names.get(j));
                if (hh != null && hh[2] > 0) {
                    won[i][j] = hh[0] + 0.5 * hh[1];
                    met[i][j] = hh[2];
                }
            }
        }
        for (int step = 0; step < 200; step++) {
            double[] next = new double[n];
            for (int i = 0; i < n; i++) {
                double wins = 0.0;
                double denom = 0.0;
                for (int j = 0; j < n; j++) {
                    if (i == j || met[i][j] == 0) {
                        continue;
                    }
                    wins += won[i][j];
                    denom += met[i][j] / (strength[i] + strength[j]);
                }
                // Участник без побед или без встреч рейтинга не получает — оставляем
                // прежнюю силу, иначе приближение уходит в ноль и портит остальных.
                next[i] = wins > 0 && denom > 0 ? wins / denom : strength[i];
            }
            // Нормировка: сила определена с точностью до общего множителя.
            double mean = 0.0;
            for (double v : next) {
                mean += v;
            }
            mean /= n;
            for (int i = 0; i < n; i++) {
                strength[i] = next[i] / mean;
            }
        }
        // Перевод в привычную шкалу Эло: 400 очков = десятикратное превосходство.
        for (int i = 0; i < n; i++) {
            table.get(names.get(i)).elo =
                1500.0 + 400.0 * Math.log10(Math.max(1e-9, strength[i]));
        }
    }

    /** Отчёт по лиге в Markdown (по-русски, для дизайнера). */
    public String report(String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("Состав: ").append(players).append(" игрока за столом. ");
        sb.append("Места РОТИРУЮТСЯ (стартовый порядок в этой игре даёт ")
          .append("преимущество, без ротации лига мерила бы удачу посадки).\n\n");
        sb.append("Эло — единая шкала силы: разница 100 очков всегда значит одно ")
          .append("и то же. Старт всех участников — 1500.\n\n");
        sb.append("| # | участник | Эло | партий | побед | % побед | ПО в среднем | лучшие ПО |\n");
        sb.append("|---|----------|-----|--------|-------|---------|--------------|-----------|\n");
        int i = 1;
        for (Standing st : standings()) {
            sb.append(String.format(Locale.ROOT,
                "| %d | %s | %.0f | %d | %d | %.1f%% | %.2f | %d |%n",
                i++, st.name, st.elo, st.games, st.wins, 100.0 * st.winRate(),
                st.avgVp(), st.vpBest));
        }
        List<Standing> all = standings();
        // ОЧНАЯ ТАБЛИЦА. Читать так: в клетке — доля партий, где участник из СТРОКИ
        // обошёл по очкам участника из СТОЛБЦА. Погрешность доли на 100 очных
        // сравнениях около 5 процентных пунктов, поэтому 52% против 48% — это
        // ничья, а не победа.
        if (all.size() >= 2 && !headToHead.isEmpty()) {
            sb.append("\n## Кто кого обходит по очкам (очный счёт)\n\n");
            sb.append("| строка против столбца |");
            for (Standing st : all) {
                sb.append(' ').append(st.name).append(" |");
            }
            sb.append("\n|---|");
            for (int c = 0; c < all.size(); c++) {
                sb.append("---:|");
            }
            sb.append('\n');
            for (Standing row : all) {
                sb.append("| **").append(row.name).append("** |");
                for (Standing col : all) {
                    if (row == col) {
                        sb.append(" — |");
                        continue;
                    }
                    int[] hh = headToHead.get(row.name + "|" + col.name);
                    if (hh == null || hh[2] == 0) {
                        sb.append(" нет |");
                    } else {
                        double share = (hh[0] + 0.5 * hh[1]) / hh[2];
                        sb.append(String.format(Locale.ROOT, " %.0f%% (%d) |",
                            100.0 * share, hh[2]));
                    }
                }
                sb.append('\n');
            }
            sb.append("\nВ скобках — сколько было очных сравнений.\n");
        }
        if (all.size() >= 2) {
            Standing top = all.get(0);
            Standing bottom = all.get(all.size() - 1);
            sb.append("\n**Разрыв лиги:** ").append(top.name).append(" сильнее ")
              .append(bottom.name).append(" на ")
              .append(String.format(Locale.ROOT, "%.0f", top.elo - bottom.elo))
              .append(" очков Эло.\n");
        }
        return sb.toString();
    }

    // ==================================================================
    //  Фабрика участников по строке-описанию
    // ==================================================================

    /**
     * Создать бота по описанию:
     * <ul>
     *   <li>{@code random} — случайный (нижний репер шкалы);</li>
     *   <li>{@code heuristic[:характер]} — старая эвристика;</li>
     *   <li>{@code default} — стратег на НЕобученном геноме (репер «до обучения»);</li>
     *   <li>{@code <характер>} — обученный геном линии (hawk/dove/…);</li>
     *   <li>{@code search[:характер]} — просчёт действий (дешёвый);</li>
     *   <li>{@code mid[:характер]} — просчёт действий с доигрыванием на раунд;</li>
     *   <li>{@code deep[:характер]} — просчёт действий + доигрывание на приказе;</li>
     *   <li>{@code genome:путь} — геном из файла.</li>
     * </ul>
     */
    public static Agent make(String spec, int seat, Random rng, int players) {
        String kind = spec;
        String arg = null;
        int colon = spec.indexOf(':');
        if (colon >= 0) {
            kind = spec.substring(0, colon);
            arg = spec.substring(colon + 1);
        }
        switch (kind) {
            case "random":
                return new RandomAgent(seat, rng);
            case "heuristic":
                return new HeuristicAgent(seat, rng, arg == null ? "balanced" : arg);
            case "default":
                return new StrategicAgent(seat, rng, Genome.defaults(), "default");
            case "search":
                return SearchAgent.fast(seat, rng,
                    Bots.genome(arg == null ? "balanced" : arg, players),
                    "search-" + (arg == null ? "balanced" : arg));
            case "mid":
                return SearchAgent.mid(seat, rng,
                    Bots.genome(arg == null ? "balanced" : arg, players),
                    "mid-" + (arg == null ? "balanced" : arg));
            case "deep":
                return SearchAgent.deep(seat, rng,
                    Bots.genome(arg == null ? "balanced" : arg, players),
                    "deep-" + (arg == null ? "balanced" : arg));
            case "deep2":
                // Щедрый просчёт: шире перебор, длиннее горизонт, больше прогонов.
                // Нужен, чтобы проверить главное — РАСТЁТ ли сила от количества
                // думанья. Если нет, значит поиск сломан, а не «уже на пределе».
                return new SearchAgent(seat, rng,
                    Bots.genome(arg == null ? "balanced" : arg, players),
                    "deep2-" + (arg == null ? "balanced" : arg),
                    6, 2, 4, 3, 3, 600);
            // ДВЕ СЕМЬИ БОТОВ (заказ дизайнера 13.08.2026).
            // «Живые» играют как люди: помнят обиды, заводятся, смотрят на
            // два-три варианта. «Ищейка» ищет дыры в правилах, а не победу.
            case "human":
                return HumanLikeAgent.normal(seat, rng,
                    Bots.genome(arg == null ? "balanced" : arg, players), players);
            case "vengeful":
                return HumanLikeAgent.vengeful(seat, rng,
                    Bots.genome(arg == null ? "hawk" : arg, players), players);
            case "cool":
                return HumanLikeAgent.coolHeaded(seat, rng,
                    Bots.genome(arg == null ? "balanced" : arg, players), players);
            case "exploit":
                return ExploitAgent.hunter(seat, rng,
                    Bots.genome(arg == null ? "balanced" : arg, players));
            case "sa": {
                // РУЧНАЯ НАСТРОЙКА ПРОСЧЁТА для опытов: числа через дробь —
                // ширина действий / горизонт действия / ширина приказов /
                // прогонов на приказ / горизонт приказа (0 = до конца партии) /
                // бюджет доигрываний на партию. Нужно, чтобы искать удачное
                // сочетание, не заводя по классу на каждую пробу.
                // Седьмое число (необязательное) — играть ли за себя в доигрывании
                // внимательно: 1 включает отсев холостых ходов внутри просчёта.
                // Восьмое число — вес оценки позиции при выборе действия, в сотых
                // (по умолчанию 20 = 0.20).
                // Девятое число — учитывать ли привычки соперников по приказам
                // (1 да, 0 нет).
                // Десятое число — вес положения в судье оборванной партии, в сотых
                // (по умолчанию 50 = 0.50).
                String[] n = (arg == null ? "4/0/4/4/2/400" : arg).split("/");
                int[] v = {4, 0, 4, 4, 2, 400, 0, 0, 1, 50};
                for (int i = 0; i < Math.min(v.length, n.length); i++) {
                    v[i] = Integer.parseInt(n[i].trim());
                }
                Genome g = Bots.genome("balanced", players);
                if (v[6] > 0) {
                    g = g.with("search.rollout_smart", 1.0);
                }
                g = g.with("search.value_weight", v[7] / 100.0)
                     .with("search.opponent_habits", v[8] > 0 ? 1.0 : 0.0)
                     .with("search.horizon_pos", v[9] / 100.0);
                return new SearchAgent(seat, rng, g,
                    "sa-" + (arg == null ? "" : arg),
                    v[0], v[1], v[2], v[3], v[4], v[5]);
            }
            case "judge": {
                // Просчитывающий бот с ОБУЧЕННОЙ оценкой позиции из файла. Нужен,
                // чтобы посадить за один стол двух одинаковых ботов, у которых
                // разница только в судье позиции: рукописные веса против обученной
                // сети. Иначе это сравнение приходилось делать двумя прогонами.
                try {
                    ValueNet net = ValueNet.load(Path.of(arg));
                    return SearchAgent.deep(seat, rng,
                        Bots.genome("balanced", players).withJudge(net), "судья-сеть");
                } catch (Exception e) {
                    System.out.println("не прочитал оценку " + arg + ": " + e.getMessage());
                    return SearchAgent.deep(seat, rng,
                        Bots.genome("balanced", players), "судья-сеть?");
                }
            }
            case "deepgenome0":
                // То же, но с оценкой позиции, возвращённой к исходным значениям:
                // проверка, не хуже ли случайно наблуждавшие веса разумных.
                try {
                    return SearchAgent.deep(seat, rng,
                        Genome.loadJson(Path.of(arg)).withDefaultEval(), "просчёт-геном0");
                } catch (Exception e) {
                    System.out.println("не прочитал геном " + arg + ": " + e.getMessage());
                    return SearchAgent.deep(seat, rng, Genome.defaults(), "просчёт-геном0?");
                }
            case "deepgenome":
                // Геном из файла, играющий С ПРОСЧЁТОМ ВПЕРЁД. Нужен, чтобы
                // сравнивать обучения по тому, как их геномы играют в том мозге, в
                // котором мы их и ставим за стол: веса оценки позиции работают
                // только у просчитывающего бота.
                try {
                    return SearchAgent.deep(seat, rng, Genome.loadJson(Path.of(arg)),
                        "просчёт-геном");
                } catch (Exception e) {
                    System.out.println("не прочитал геном " + arg + ": " + e.getMessage());
                    return SearchAgent.deep(seat, rng, Genome.defaults(), "просчёт-геном?");
                }
            case "genome":
                try {
                    return new StrategicAgent(seat, rng,
                        Genome.loadJson(Path.of(arg)), "genome");
                } catch (Exception e) {
                    return new StrategicAgent(seat, rng, Genome.defaults(), "genome?");
                }
            default:
                if (Bots.CHARACTERS.contains(kind)) {
                    return Bots.create(kind, seat, rng, players);
                }
                // Имена из СПРАВОЧНИКА БОТОВ (окно прогонщика и запись партии знают
                // только их) — чтобы одно имя всюду означало одного бота.
                if (BotCatalog.known(spec)) {
                    return BotCatalog.create(spec, seat, rng, players);
                }
                return new StrategicAgent(seat, rng, Genome.defaults(), kind);
        }
    }

    /**
     * CLI: {@code kelium.agents.Arena [игроков] [кругов] [участники через запятую]}.
     * По умолчанию 4 игрока, 6 кругов, полный состав реперов и характеров.
     */
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 6;
        String list = args.length > 2 ? args[2]
            : "random,heuristic:aggressor,default,balanced,hawk,dove,search:balanced";
        List<Fighter> fighters = new ArrayList<>();
        Map<String, Integer> used = new LinkedHashMap<>();
        for (String spec : list.split(",")) {
            String s = spec.trim();
            if (s.isEmpty()) {
                continue;
            }
            // Имя = описание, но для геномов из файла берём только имя файла:
            // полный путь растягивает очную таблицу так, что её нельзя читать.
            String label = s;
            if (s.startsWith("genome:") || s.startsWith("deepgenome:")
                    || s.startsWith("deepgenome0:")) {
                Path p = Path.of(s.substring(s.indexOf(':') + 1));
                String file = p.getFileName().toString().replaceFirst("\\.json$", "");
                Path parent = p.getParent();
                String dir = parent == null ? "" : parent.getFileName().toString();
                // Папка в имени обязательна: два обучения дают файлы С ОДНИМ
                // ИМЕНЕМ, и без папки в очной таблице их не различить.
                label = (dir.isEmpty() ? "" : dir + "/") + file
                    + (s.startsWith("deepgenome0:") ? " (оценка по умолчанию)" : "");
            }
            // Повторы получают номер, чтобы рейтинги не слились.
            int n = used.merge(label, 1, Integer::sum);
            fighters.add(new Fighter(n == 1 ? label : label + "#" + n, s));
        }
        System.out.println("Лига: " + players + " игрока, " + rounds + " кругов, участников "
            + fighters.size());
        long t0 = System.nanoTime();
        Arena arena = new Arena(players, fighters);
        arena.run(rounds, 20_260_812L);
        double mins = (System.nanoTime() - t0) / 6e10;
        String report = arena.report("Лига ботов — " + players + " игрока");
        System.out.println(report);
        System.out.printf(Locale.ROOT, "время: %.1f мин%n", mins);
        Path out = Path.of("reports", "balance", "лига-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }
}
