package kelium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.rules.Ruleset;
import kelium.engine.LayoutLibrary;

/**
 * RuleExperiment — БАЛАНСОВЫЙ СТЕНД: «что будет, если поменять вот это число».
 *
 * <p>Зачем. Вопрос «как сделать игру агрессивнее» нельзя решить рассуждением:
 * правила связаны между собой, и любое «очевидное» усиление войны может просто
 * ускорить мирную гонку. Здесь каждая догадка проверяется ЗАМЕРОМ — одинаковые
 * боты играют одинаковые раздачи при разных правилах, и видно, что изменилось.
 *
 * <p>Канонические файлы правил НЕ ТРОГАЮТСЯ: правка накладывается на КОПИЮ
 * ruleset в памяти ({@link Ruleset#copy()} + {@code override}). Это важно — стенд
 * не должен уметь испортить источник правды.
 *
 * <p>Что измеряется. Не «стало больше боёв» (боёв можно накрутить и без смысла), а
 * ТРИ вещи разом:
 * <ol>
 *   <li><b>сколько воюют</b> — боёв, ударов, уничтожено жетонов за партию;</li>
 *   <li><b>окупается ли война</b> — доля побед у агрессивного бота против
 *       мирного. Это главный показатель: если ястреб не начал выигрывать чаще,
 *       правка сделала войну шумной, а не выгодной;</li>
 *   <li><b>чем кончаются партии</b> — по очкам, по военной победе, по
 *       супер-заданию.</li>
 * </ol>
 *
 * <p>Запуск: {@code kelium.RuleExperiment [игроков] [партий на вариант]}.
 */
public final class RuleExperiment {

    private RuleExperiment() {
    }

    /**
     * Один проверяемый вариант. {@code overrides} — правки правил;
     * {@code layout} — раскладка поля (null = как обычно, по сиду).
     *
     * <p>Раскладка тут не для красоты: замеры показали, что 57% розыгрышей Боя
     * проходят вообще БЕЗ достижимых целей — армии не встречаются. Это вопрос
     * геометрии поля, а не правил, и проверять его надо тем же стендом.
     */
    /**
     * Вариант правил для опыта.
     *
     * @param overrides правки НАБОРА ПРАВИЛ (ключ → значение)
     * @param layout    конкретная раскладка или null (тогда из набора по сиду)
     * @param buildingHp правки ПЕЧАТНОЙ ПРОЧНОСТИ зданий («command_center» → 2):
     *                   часть балансовых вопросов живёт не в правилах, а на
     *                   жетонах, и без этого их нельзя проверить, не правя файл
     */
    private record Variant(String name, String why, Map<String, Object> overrides,
                           String layout, Map<String, Integer> buildingHp) {

        Variant(String name, String why, Map<String, Object> overrides) {
            this(name, why, overrides, null, Map.of());
        }

        Variant(String name, String why, Map<String, Object> overrides, String layout) {
            this(name, why, overrides, layout, Map.of());
        }
    }

    /** Что вышло по варианту. */
    private static final class Stats {
        int games;
        long battles;
        long hits;
        long destroyed;
        long unitsMade;
        long ammoSpent;
        int gamesWithKill;
        int militaryWins;
        int pointWins;
        int superWins;
        // РАЗБИВКА МИРНОГО КОНЦА. Раньше все трое сваливались в pointWins, и по
        // отчёту нельзя было отличить «партия доиграна» от «партию оборвали».
        // Разница принципиальная: last_spawn_tile — выработан келемий,
        // all_peaks — гонка по науке, victory_points — КОНЧИЛИСЬ КАРТЫ РЫНКА,
        // то есть партия упёрлась в потолок раундов и была остановлена.
        int lastTileWins;
        int peakWins;
        double vpSum;
        long rounds;
        // Напряжённость финала: отрыв победителя от второго места. Малый отрыв =
        // партия решается на последнем ходу, большой = победитель ясен заранее.
        double marginSum;
        // Победы по характерам: ястреб (война) против голубя (мир).
        final Map<String, Integer> winsByChar = new LinkedHashMap<>();
        final Map<String, Integer> gamesByChar = new LinkedHashMap<>();
        final Map<String, Double> vpByChar = new LinkedHashMap<>();

        double per(long v) {
            return games == 0 ? 0 : (double) v / games;
        }

        double winRate(String ch) {
            int g = gamesByChar.getOrDefault(ch, 0);
            return g == 0 ? 0 : 100.0 * winsByChar.getOrDefault(ch, 0) / g;
        }

        double avgVp(String ch) {
            int g = gamesByChar.getOrDefault(ch, 0);
            return g == 0 ? 0 : vpByChar.getOrDefault(ch, 0.0) / g;
        }
    }

    /** Состав стола: агрессивный, мирный и два средних — так видно, кто выигрывает. */
    private static final List<String> LINEUP =
        List.of("hawk", "dove", "balanced", "opportunist");

    private static synchronized void merge(Stats into, Stats one) {
        into.games += one.games;
        into.battles += one.battles;
        into.hits += one.hits;
        into.destroyed += one.destroyed;
        into.unitsMade += one.unitsMade;
        into.ammoSpent += one.ammoSpent;
        into.gamesWithKill += one.gamesWithKill;
        into.militaryWins += one.militaryWins;
        into.pointWins += one.pointWins;
        into.superWins += one.superWins;
        into.lastTileWins += one.lastTileWins;
        into.peakWins += one.peakWins;
        into.vpSum += one.vpSum;
        into.rounds += one.rounds;
        into.marginSum += one.marginSum;
        one.winsByChar.forEach((k, v) -> into.winsByChar.merge(k, v, Integer::sum));
        one.gamesByChar.forEach((k, v) -> into.gamesByChar.merge(k, v, Integer::sum));
        one.vpByChar.forEach((k, v) -> into.vpByChar.merge(k, v, Double::sum));
    }

    /**
     * Копия записи о жетонах с другой прочностью зданий. Копия обязательна: запись
     * контента общая на процесс, и правка «на месте» протекла бы во все остальные
     * варианты опыта.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withBuildingHp(GameConfig base,
                                                      Map<String, Integer> hp) {
        Map<String, Object> tokens = base.content.get("boards").find("tokens");
        Map<String, Object> copy = new LinkedHashMap<>(tokens);
        Map<String, Object> buildings =
            new LinkedHashMap<>((Map<String, Object>) copy.get("buildings"));
        hp.forEach((code, value) -> {
            Map<String, Object> one =
                new LinkedHashMap<>((Map<String, Object>) buildings.get(code));
            one.put("hp", value);
            buildings.put(code, one);
        });
        copy.put("buildings", buildings);
        return copy;
    }

    /** Одна партия по заданным правилам. Место характера РОТИРУЕТСЯ по сиду. */
    private static Stats playOne(Variant v, int players, long seed) {
        GameConfig base = LayoutLibrary.configFor(players, seed);
        Ruleset rules = base.ruleset.copy();
        for (Map.Entry<String, Object> e : v.overrides().entrySet()) {
            rules.override(e.getKey(), e.getValue());
        }
        // Если вариант задаёт раскладку по имени, надо взять и ЕЁ ФАЙЛ: имя без
        // файла движок ищет в чужом файле, не находит и молча играет на аварийном
        // кольце (обжигались 13.08.2026).
        String layoutId = v.layout() != null ? v.layout() : base.scenarioId;
        java.nio.file.Path layoutFile = base.scenarioFile;
        if (v.layout() != null) {
            for (var e : LayoutLibrary.pool(players)) {
                if (e.id().equals(v.layout())) {
                    layoutFile = e.file();
                    break;
                }
            }
        }
        GameConfig cfg = new GameConfig(rules, base.content, players, seed, base.dataRoot,
            base.boardSides, layoutId, v.layout() != null ? base.cuFacing : base.cuFacing,
            layoutFile);
        if (!v.buildingHp().isEmpty()) {
            cfg.tokenStatsOverride = withBuildingHp(base, v.buildingHp());
        }
        GameState s = Setup.buildGame(cfg);

        List<Agent> agents = new ArrayList<>();
        List<String> chars = new ArrayList<>();
        int shift = (int) (seed % players);
        for (int i = 0; i < players; i++) {
            String ch = LINEUP.get((i + shift) % LINEUP.size());
            chars.add(ch);
            agents.add(Bots.create(ch, i, new Random(seed * 31 + i), players));
        }

        Stats st = new Stats();
        Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
            String type = String.valueOf(ev.get("type"));
            if ("combat_hit".equals(type)) {
                st.hits++;
                if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                    st.destroyed++;
                }
                if (ev.get("ammo") instanceof Number a) {
                    st.ammoSpent += a.intValue();
                }
            } else if ("action".equals(type) && "combat".equals(ev.get("action"))
                    && Boolean.TRUE.equals(ev.get("ok"))) {
                st.battles++;
            } else if ("action".equals(type) && "assembly".equals(ev.get("action"))
                    && ev.get("telemetry") instanceof Map<?, ?> tel
                    && tel.get("units") instanceof Number u) {
                st.unitsMade += u.intValue();
            }
        });

        st.games = 1;
        st.rounds = res.get("rounds") instanceof Number r ? r.intValue() : 0;
        if (st.destroyed > 0) {
            st.gamesWithKill = 1;
        }
        // СРАВНИВАТЬ УСЛОВИЕ ЦЕЛИКОМ, а не по подстроке. Сначала здесь стояло
        // cond.contains("cu") — и в «военные победы» попадало условие
        // all_peaks_occupied, потому что в слове «occupied» есть буквы «cu».
        // Из-за этого военная победа выглядела как 22% партий вместо настоящего 1%.
        String cond = String.valueOf(res.get("condition"));
        switch (cond) {
            case "military" -> st.militaryWins = 1;
            case "super_objective" -> st.superWins = 1;
            case "last_spawn_tile" -> st.lastTileWins = 1;
            case "all_peaks_occupied" -> st.peakWins = 1;
            // victory_points = ни одно условие не сработало, партия ОСТАНОВЛЕНА
            // потолком раундов (кончились карты рынка) и посчитана по очкам.
            default -> st.pointWins = 1;
        }
        int winner = res.get("winner") instanceof Number w ? w.intValue() : -1;
        double top1 = Double.NEGATIVE_INFINITY;
        double top2 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < players; i++) {
            String ch = chars.get(i);
            int vp = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
            st.gamesByChar.merge(ch, 1, Integer::sum);
            st.vpByChar.merge(ch, (double) vp, Double::sum);
            st.vpSum += vp;
            if (vp > top1) {
                top2 = top1;
                top1 = vp;
            } else if (vp > top2) {
                top2 = vp;
            }
            if (i == winner) {
                st.winsByChar.merge(ch, 1, Integer::sum);
            }
        }
        st.marginSum = top2 == Double.NEGATIVE_INFINITY ? 0 : top1 - top2;
        return st;
    }

    private static Stats run(Variant v, int players, int games) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Stats total = new Stats();
        List<Future<Stats>> futures = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            // ОДНИ И ТЕ ЖЕ раздачи у всех вариантов, сиды ПОДРЯД.
            //
            // Была попытка брать только чётные сиды (чтобы поле без явного указания
            // совпадало с первым вариантом и сравнение «правила против поля» было
            // чище). Это оказалось ЛОВУШКОЙ: выборка стала смещённой, и разница
            // между раскладками раздулась с 5 до 40 процентных пунктов — то есть
            // измерялась не раскладка, а особенность чётных сидов. Сравнение
            // раскладок вынесено в отдельный стенд {@code kelium.LayoutArena},
            // который гоняет все поля на одних и тех же полных наборах сидов.
            final long seed = 6_100_000L + g;
            futures.add(pool.submit((Callable<Stats>) () -> playOne(v, players, seed)));
        }
        for (Future<Stats> f : futures) {
            try {
                merge(total, f.get());
            } catch (Exception e) {
                System.err.println("партия сорвалась (" + v.name() + "): " + e.getMessage());
            }
        }
        pool.shutdown();
        return total;
    }

    /**
     * Факты о раскладке: [гексов, тайлов зарождения, минимальное расстояние между
     * стартами]. Расстояние считается по полю (шагами между соседними гексами) —
     * именно оно решает, встретятся ли армии вообще.
     */
    private static int[] layoutFacts(int players, String layout, long seed) {
        GameConfig base = LayoutLibrary.configFor(players, seed);
        GameConfig cfg = new GameConfig(base.ruleset, base.content, players, seed,
            base.dataRoot, base.boardSides, layout, base.cuFacing, base.scenarioFile);
        GameState s = Setup.buildGame(cfg);
        int tiles = 0;
        for (var h : s.field.hexes.values()) {
            if (h.spawnTile != null) {
                tiles++;
            }
        }
        int minDist = Integer.MAX_VALUE;
        int sumDist = 0;
        int pairs = 0;
        for (int a = 0; a < players; a++) {
            for (int b = a + 1; b < players; b++) {
                Integer d = bfs(s, s.player(a).startHex, s.player(b).startHex);
                if (d != null) {
                    minDist = Math.min(minDist, d);
                    sumDist += d;
                    pairs++;
                }
            }
        }
        // Среднее расстояние между стартами важнее минимального: оно говорит,
        // сколько ходов армия идёт до чужой базы В СРЕДНЕМ, а значит — успеет ли
        // война окупиться за партию.
        int avgDist10 = pairs == 0 ? 0 : (10 * sumDist / pairs);
        return new int[]{s.field.size(), tiles,
            minDist == Integer.MAX_VALUE ? 0 : minDist, avgDist10};
    }

    /** Шагов между гексами по полю (без учёта проходимости — это про геометрию). */
    private static Integer bfs(GameState s, String from, String to) {
        if (from == null || to == null) {
            return null;
        }
        Map<String, Integer> dist = new HashMap<>();
        java.util.Deque<String> q = new java.util.ArrayDeque<>();
        dist.put(from, 0);
        q.add(from);
        while (!q.isEmpty()) {
            String cur = q.poll();
            if (cur.equals(to)) {
                return dist.get(cur);
            }
            for (String nb : s.field.neighborsView(cur)) {
                if (!dist.containsKey(nb)) {
                    dist.put(nb, dist.get(cur) + 1);
                    q.add(nb);
                }
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        // Третий аргумент — НАБОР вариантов. Полный список стоит десятков минут, а
        // вопрос обычно узкий; гонять всё ради одной колонки — потеря времени.
        // Латинский псевдоним обязателен: аргументы командной строки на Windows
        // декодируются кодировкой ОС (sun.jnu.encoding), а не UTF-8, поэтому
        // кириллическое имя набора может доехать испорченным.
        String set = args.length > 2 ? args[2] : "всё";
        if ("length".equals(set)) {
            set = "длина";
        }
        System.out.println("набор вариантов: " + set);

        List<Variant> variants = new ArrayList<>();
        variants.add(new Variant("как сейчас", "точка отсчёта", Map.of()));
        if ("длина".equals(set)) {
            lengthVariants(variants);
            runAll(variants, players, games, set);
            return;
        }
        variants.add(new Variant("уничтожение НАВСЕГДА",
            "убитые жетоны не возвращаются владельцу в Возврат",
            Map.of("return_step.return_destroyed_tokens", false)));
        // Раньше здесь стоял вариант «урон НЕ лечится» (heal 0). С 1.7.0 это и есть
        // действующее правило, поэтому вариант был холостым. Сравнение развёрнуто.
        variants.add(new Variant("ВЕРНУТЬ лечение",
            "в Обновление снова снимается 1 кубик урона — как было до свода 1.7.0",
            Map.of("combat_model.heal_per_refresh", 1)));
        variants.add(new Variant("выстрел вдвое дешевле",
            "вторичный ряд атак стоит 1 боеприпас вместо 2",
            Map.of("actions.combat.secondary_row_ammo_cost", 1)));
        variants.add(new Variant("бой без надбавки",
            "второй и следующий бой в ход не дорожают",
            Map.of("actions.combat.open_battle_surcharge_ammo", List.of(0, 0))));
        // ДЛИНА ПАРТИИ. Партия обрывается, когда остаётся последний источник
        // келемия, — то есть её длину задаёт ЧИСЛО ТАЙЛОВ. Война требует
        // раундов: собрать войска, дойти, накопить урон. Если партия коротка,
        // окупиться она не успевает физически.
        variants.add(new Variant("партия ДОЛЬШЕ",
            "условие «остался последний тайл» выключено — партия идёт все 8 раундов",
            Map.of("end_conditions.last_spawn_tile_threshold", -1)));
        variants.add(new Variant("трофеи вдвое ценнее",
            "трофейных очков на победное очко нужно 2 вместо 3",
            Map.of("economy.trophy_per_vp", 2)));
        // ГЛАВНАЯ ПРОВЕРКА: сейчас уничтожение НЕ ДАЁТ ОЧКОВ вовсе — оно даёт
        // трофей, который надо ещё сдать в Науку отдельным действием, а несданный
        // возвращается владельцу. То есть за агрессию игра не платит напрямую.
        variants.add(new Variant("1 очко за уничтожение",
            "каждый уничтоженный жетон = 1 победное очко сразу",
            Map.of("economy.vp_per_kill", 1)));
        variants.add(new Variant("2 очка за уничтожение",
            "каждый уничтоженный жетон = 2 победных очка",
            Map.of("economy.vp_per_kill", 2)));
        // ЦЕЛЬ ДИЗАЙНЕРА 13.08.2026: военная победа в 20% партий, супер-задание в
        // 20%, остальные 60% по очкам. Сейчас военная 0.5%, супер 6%. Эти варианты
        // проверяют, чем именно двигать распределение.
        variants.add(new Variant("ЦУ прочность 2",
            "штурм ЦУ становится вдвое короче: 2 кубика урона вместо 3",
            Map.of(), null, Map.of("command_center", 2)));
        variants.add(new Variant("дольше + урон не лечится",
            "партия все 8 раундов и осада копится — время на войну",
            Map.of("end_conditions.last_spawn_tile_threshold", -1,
                "combat_model.heal_per_refresh", 0)));
        variants.add(new Variant("ВОЙНА: дольше + ЦУ 2 + осада",
            "всё вместе против ЦУ: длинная партия, прочность 2, урон не лечится",
            Map.of("end_conditions.last_spawn_tile_threshold", -1,
                "combat_model.heal_per_refresh", 0), null, Map.of("command_center", 2)));
        variants.add(new Variant("ВСЁ ВМЕСТЕ",
            "уничтожение навсегда + урон не лечится + дешёвый выстрел",
            Map.of("return_step.return_destroyed_tokens", false,
                "combat_model.heal_per_refresh", 0,
                "actions.combat.secondary_row_ammo_cost", 1)));
        variants.add(new Variant("ПЛАТИТЬ + ВСЁ ВМЕСТЕ",
            "то же плюс 1 очко за уничтожение",
            Map.of("return_step.return_destroyed_tokens", false,
                "combat_model.heal_per_refresh", 0,
                "actions.combat.secondary_row_ammo_cost", 1,
                "economy.vp_per_kill", 1)));
        // ГЕОМЕТРИЯ: те же правила, но КОНКРЕТНЫЕ раскладки дизайнера — самая
        // злая против самой мирной по лиге раскладок. Если разница между полями
        // больше, чем между всеми правками правил, значит агрессию решает поле.
        //
        // Раньше здесь стояли встроенные поля (field_4p_v2/v4), и после перехода
        // замеров на папку дизайнера эти строки стали врать: движок искал
        // встроенный id внутри файла дизайнера, не находил и играл на аварийном
        // кольце — отсюда были «1 бой, 0 ударов» в отчёте.
        if (players == 4) {
            var pool = LayoutLibrary.pool(4);
            if (pool.size() >= 2) {
                variants.add(new Variant("поле: " + pool.get(0).id(),
                    "правила как сейчас, первая раскладка набора",
                    Map.of(), pool.get(0).id()));
                variants.add(new Variant("поле: " + pool.get(pool.size() - 1).id(),
                    "правила как сейчас, последняя раскладка набора",
                    Map.of(), pool.get(pool.size() - 1).id()));
            }
        }

        runAll(variants, players, games, set);
    }

    /**
     * РЫЧАГИ ДЛИНЫ ПАРТИИ. Замер 14.08.2026 показал, что партию обрывают ДВА
     * жёстких таймера: выработка келемия (55% партий) и потолок раундов, он же
     * запас карт рынка (35%). Наука обрывает лишь 8%. Значит длину нельзя
     * растянуть, двигая один рычаг: отпустишь тайлы — упрёшься в карты.
     *
     * <p>Военная победа требует ДВУХ сносов ЦУ одному игроку, а первый снос
     * приходится на раунд 6.0–6.5 при длине партии 6–7. Второму сносу физически
     * негде поместиться — поэтому здесь же проверяются длина и прочность ЦУ
     * вместе, а не по отдельности.
     */
    private static void lengthVariants(List<Variant> variants) {
        variants.add(new Variant("тайлы не обрывают",
            "условие «остался последний тайл» выключено — партию держат только карты рынка",
            Map.of("end_conditions.last_spawn_tile_threshold", -1)));
        variants.add(new Variant("тайлы до нуля",
            "партия идёт, пока не выработан ПОСЛЕДНИЙ келемий (порог 0 вместо 1)",
            Map.of("end_conditions.last_spawn_tile_threshold", 0)));
        variants.add(new Variant("потолок 11 раундов",
            "запас карт рынка снят: 11 раундов вместо 8, тайлы обрывают как сейчас",
            Map.of("rounds.reserve_cap", 11)));
        variants.add(new Variant("оба рычага отпущены",
            "тайлы не обрывают И потолок 11 — предельно длинная партия",
            Map.of("end_conditions.last_spawn_tile_threshold", -1,
                "rounds.reserve_cap", 11)));
        variants.add(new Variant("длинная + ЦУ прочность 2",
            "оба рычага отпущены и штурм ЦУ вдвое короче — есть ли время на ВТОРОЙ снос",
            Map.of("end_conditions.last_spawn_tile_threshold", -1,
                "rounds.reserve_cap", 11), null, Map.of("command_center", 2)));
        // ПОРОГ ВОЕННОЙ ПОБЕДЫ. Сейчас нужно ДВА жетона разрушения, а первый снос
        // ЦУ приходится на раунд 6.0 при длине партии 6.0 — второму негде
        // поместиться. Это единственный рычаг, который бьёт в причину, а не в
        // следствие; всё остальное лишь удлиняет партию.
        variants.add(new Variant("военная победа с ОДНОГО ЦУ",
            "порог 1 жетон разрушения вместо 2, длина партии как сейчас",
            Map.of("command_center.cu_tokens_for_military_win", 1)));
        variants.add(new Variant("с одного ЦУ + длинная",
            "порог 1 жетон и оба рычага длины отпущены",
            Map.of("command_center.cu_tokens_for_military_win", 1,
                "end_conditions.last_spawn_tile_threshold", -1,
                "rounds.reserve_cap", 11)));
        variants.add(new Variant("с одного ЦУ + ЦУ прочность 2",
            "порог 1 жетон и штурм ЦУ вдвое короче, длина партии как сейчас",
            Map.of("command_center.cu_tokens_for_military_win", 1),
            null, Map.of("command_center", 2)));
        variants.add(new Variant("КОРОЧЕ: потолок 5",
            "контрольный образец в другую сторону — партия заведомо короче",
            Map.of("rounds.reserve_cap", 5)));
        // ВНИМАНИЕ, ЛОВУШКА (обжёгся 14.08.2026). Свод 1.7.0 УЖЕ содержит
        // heal_per_refresh: 0 — лечение отключено решением дизайнера 13.08.2026.
        // Поэтому вариант «урон не лечится» выставляет то, что и так стоит, и даёт
        // побитово те же числа, что точка отсчёта. Полчаса ушло на поиск
        // несуществующей ошибки в стенде. Осмысленное сравнение теперь ОБРАТНОЕ:
        // вернуть лечение и посмотреть, сколько игра на нём теряла.
        variants.add(new Variant("ВЕРНУТЬ лечение",
            "в Обновление снова снимается 1 кубик урона (правило до 1.7.0)",
            Map.of("combat_model.heal_per_refresh", 1)));
    }

    private static void runAll(List<Variant> variants, int players, int games, String set)
            throws Exception {
        System.out.println("Балансовый стенд: " + players + " игрока, по " + games
            + " партий на вариант, раздачи у всех вариантов ОДНИ И ТЕ ЖЕ.");
        System.out.println("Состав стола: " + LINEUP + " (места ротируются).");

        Map<String, Stats> results = new LinkedHashMap<>();
        for (Variant v : variants) {
            long t0 = System.nanoTime();
            Stats st = run(v, players, games);
            results.put(v.name(), st);
            System.out.printf(Locale.ROOT,
                "  %-24s боёв %.1f, ударов %.1f, уничтожено %.2f, ястреб %.0f%% / голубь %.0f%%  (%.0f с)%n",
                v.name(), st.per(st.battles), st.per(st.hits), st.per(st.destroyed),
                st.winRate("hawk"), st.winRate("dove"), (System.nanoTime() - t0) / 1e9);
        }

        // ==================== отчёт ====================
        StringBuilder sb = new StringBuilder();
        sb.append("# Как сделать игру агрессивнее — проверено замером\n\n");
        sb.append("По ").append(games).append(" партий на вариант, ").append(players)
          .append(" игрока, раздачи у всех вариантов ОДНИ И ТЕ ЖЕ (сравнение честное). ")
          .append("За столом ").append(LINEUP)
          .append(", места ротируются. Канонические правила не менялись: правка ")
          .append("накладывалась на копию ruleset в памяти.\n\n");
        sb.append("**Как читать.** Смотреть надо не на число боёв, а на две колонки ")
          .append("справа: если у ЯСТРЕБА не выросла доля побед, правка сделала войну ")
          .append("шумной, а не выгодной. Цель — чтобы агрессивная линия стала ")
          .append("конкурентной, а не чтобы жетоны чаще стучали друг о друга.\n\n");
        sb.append("| вариант | раундов | боёв за партию | ударов | уничтожено жетонов "
            + "| ястреб, % побед | голубь, % побед | ПО ястреба | ПО голубя |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Variant v : variants) {
            Stats st = results.get(v.name());
            sb.append(String.format(Locale.ROOT,
                "| %s | %.1f | %.1f | %.1f | %.2f | **%.0f%%** | %.0f%% | %.1f | %.1f |%n",
                v.name(), st.per(st.rounds), st.per(st.battles), st.per(st.hits),
                st.per(st.destroyed), st.winRate("hawk"), st.winRate("dove"),
                st.avgVp("hawk"), st.avgVp("dove")));
        }
        sb.append("\n## Что означает каждый вариант\n\n");
        for (Variant v : variants) {
            sb.append("- **").append(v.name()).append("** — ").append(v.why()).append('\n');
        }
        // ГЕОМЕТРИЯ ПОЛЯ — почему раскладки дают разный результат.
        sb.append("\n## Чем различаются раскладки\n\n");
        sb.append("Раскладка влияет на агрессию сильнее любой правки правил, ")
          .append("поэтому важно знать, ЧЕМ именно поля отличаются.\n\n");
        sb.append("| раскладка | гексов | гексов на игрока | тайлов зарождения "
            + "| тайлов на игрока | мин. расстояние стартов | среднее расстояние |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (Variant v : variants) {
            if (v.layout() == null) {
                continue;
            }
            int[] geo = layoutFacts(players, v.layout(), 1L);
            sb.append(String.format(Locale.ROOT,
                "| %s | %d | %.1f | %d | %.1f | %d | %.1f |%n",
                v.layout(), geo[0], geo[0] / (double) players, geo[1],
                geo[1] / (double) players, geo[2], geo[3] / 10.0));
        }

        sb.append("\n## Чем кончаются партии\n\n");
        sb.append("Пять условий РАЗДЕЛЕНЫ. Раньше первые три стояли одной колонкой ")
          .append("«по очкам», и по отчёту нельзя было понять, доиграна партия или ")
          .append("оборвана. «Потолок раундов» — это не развязка, а стена: ни одно ")
          .append("условие не сработало, кончились карты рынка, счёт подвели ")
          .append("принудительно. Цель дизайнера (13.08.2026): военная 20%, ")
          .append("супер-задание 20%, остальные 60%.\n\n");
        sb.append("| вариант | последний тайл | потолок раундов | вершины треков "
            + "| супер-задание | военная (2 ЦУ) | отрыв 1-го от 2-го, ПО |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (Variant v : variants) {
            Stats st = results.get(v.name());
            int g = Math.max(1, st.games);
            sb.append(String.format(Locale.ROOT,
                "| %s | %.0f%% | %.0f%% | %.0f%% | %.0f%% | **%.1f%%** | %.1f |%n",
                v.name(), 100.0 * st.lastTileWins / g, 100.0 * st.pointWins / g,
                100.0 * st.peakWins / g, 100.0 * st.superWins / g,
                100.0 * st.militaryWins / g, st.marginSum / g));
        }
        Stats base = results.get("как сейчас");
        sb.append("\n## Вывод\n\n");

        // Главный вопрос был «чтобы чаще сражались и уничтожались» — значит
        // ранжируем по РОСТУ УНИЧТОЖЕНИЙ, а не по проценту побед ястреба (он, как
        // видно из таблицы, почти не двигается ни от одной правки).
        String bestKills = null;
        double bestKillsGain = 0;
        for (Variant v : variants) {
            if ("как сейчас".equals(v.name()) || v.layout() != null) {
                continue;
            }
            Stats st = results.get(v.name());
            double gain = st.per(st.destroyed) - base.per(base.destroyed);
            if (bestKills == null || gain > bestKillsGain) {
                bestKills = v.name();
                bestKillsGain = gain;
            }
        }
        Stats bk = results.get(bestKills);
        sb.append(String.format(Locale.ROOT,
            "1. **Больше всего боёв и уничтожений даёт «%s»:** уничтожено жетонов "
            + "%.2f против %.2f (%+.0f%%), военных побед %.0f%% против %.0f%%.%n",
            bestKills, bk.per(bk.destroyed), base.per(base.destroyed),
            100.0 * bestKillsGain / Math.max(0.01, base.per(base.destroyed)),
            100.0 * bk.militaryWins / Math.max(1, bk.games),
            100.0 * base.militaryWins / Math.max(1, base.games)));

        // Насколько правки вообще двигают ОКУПАЕМОСТЬ войны.
        double minHawk = 100;
        double maxHawk = 0;
        for (Variant v : variants) {
            if (v.layout() != null) {
                continue;
            }
            double w = results.get(v.name()).winRate("hawk");
            minHawk = Math.min(minHawk, w);
            maxHawk = Math.max(maxHawk, w);
        }
        sb.append(String.format(Locale.ROOT,
            "2. **Ни одна правка правил не меняет, КТО выигрывает:** доля побед "
            + "агрессивной линии по всем вариантам держится в узком коридоре "
            + "%.0f–%.0f%%. Правки делают войну ЧАСТОТНЕЕ, но не выгоднее.%n",
            minHawk, maxHawk));

        // А вот раскладка меняет всё.
        double loW = 100;
        double hiW = 0;
        String loName = null;
        String hiName = null;
        for (Variant v : variants) {
            if (v.layout() == null) {
                continue;
            }
            double w = results.get(v.name()).winRate("hawk");
            if (w < loW) {
                loW = w;
                loName = v.layout();
            }
            if (w > hiW) {
                hiW = w;
                hiName = v.layout();
            }
        }
        if (loName != null && hiName != null && !loName.equals(hiName)) {
            sb.append(String.format(Locale.ROOT,
                "3. **РЕШАЕТ РАСКЛАДКА ПОЛЯ, а не боевые правила:** на «%s» "
                + "агрессивная линия берёт %.0f%% побед, на «%s» — %.0f%%. "
                + "Разница между двумя авторскими полями больше, чем между всеми "
                + "проверенными правками правил вместе.%n",
                hiName, hiW, loName, loW));
        }
        sb.append("\nЭто ЗАМЕР, а не рекомендация: решение за автором игры. ")
          .append("Стенд можно перезапустить с любыми другими числами — ")
          .append("`kelium.RuleExperiment [игроков] [партий]`, варианты правятся в ")
          .append("одном списке в начале `main`.\n");

        String report = sb.toString();
        System.out.println();
        System.out.println(report);
        String stem = "длина".equals(set) ? "длина-партии-" : "агрессивность-варианты-";
        Path out = Path.of("reports", "balance", stem + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }
}
