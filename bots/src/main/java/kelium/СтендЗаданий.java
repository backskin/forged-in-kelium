package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.core.Agent;
import kelium.core.Deck;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * СТЕНД ЗАДАНИЙ — что карта задания делает с партией (дизайнер 06.10.2026).
 *
 * <p>ЗАЧЕМ. Счётчик «пришла / выполнена» ({@link ЗаданияПоКартам}) не отвечает
 * на вопросы дизайнера: даёт ли карта ОБХОДНОЙ путь мимо конвейера действий,
 * стоит ли она чего-то, и не разгоняет ли она лидера («снежный ком»). Для этого
 * нужен парный опыт, а не средние по колоде.
 *
 * <p>КАК. Живая партия ботов доигрывается до конца раунда R и замораживается —
 * это СРЕЗ: настоящее поле середины партии, а не придуманная раскладка. Со
 * среза снимаются две копии с ОДНИМ И ТЕМ ЖЕ генератором случайностей и одними
 * ботами:
 * <ul>
 *   <li>ОПЫТ — проверяемая карта подложена в руку одному игроку;</li>
 *   <li>КОНТРОЛЬ — всё то же, карты нет.</li>
 * </ul>
 * Обе играются K раундов (или до конца партии). Без карты обе копии сыграли бы
 * одинаково ход в ход, поэтому ЛЮБАЯ разница между ними — вклад карты, а не шум.
 * Карту подкладывают по очереди ЛИДЕРУ среза и ОТСТАЮЩЕМУ: сравнение их выгоды и
 * есть мерило снежного кома.
 *
 * <p>ЧТО СЧИТАЕТСЯ НА КАРТУ:
 * <ul>
 *   <li><b>выполнена</b> — доля опытов, где игрок выполнил карту за горизонт;</li>
 *   <li><b>Δ очков</b> — очки игрока в опыте минус в контроле;</li>
 *   <li><b>снежный ком</b> — Δ у лидера минус Δ у отстающего: больше нуля —
 *       карта помогает тому, кто и так впереди;</li>
 *   <li><b>сдвиг</b> — насколько поменялся набор сыгранных игроком действий
 *       (полусумма модулей разностей долей, 0…1): карта, после которой игрок
 *       играет то же самое, обходного пути не открывает;</li>
 *   <li><b>победы</b> (если играли до конца) — доля побед в опыте минус в контроле.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.СтендЗаданий [срезов] [раунд среза] [горизонт|0=до конца]
 * [игроков] [свод] [потоков] [набор заданий|-] [карта,карта…]}.
 * Отчёт: {@code reports/balance/стенд-заданий.md}.
 */
public final class СтендЗаданий {

    private СтендЗаданий() {
    }

    /** Боты стола: {@code -Dkelium.стенд.боты=builder:2,supplier:2,…} (по умолчанию уровень 2 — быстрее). */
    private static final List<String> ПУЛ = List.of(System.getProperty("kelium.стенд.боты",
        "builder:2,supplier:2,stalker:2,punisher:2").split(","));

    /** Итог одного парного опыта. */
    private record Опыт(String карта, boolean лидер, boolean выполнена, int δОчков,
                        int δПобед, double сдвиг, int δМеста, String выдано, boolean былаГотова,
                        Map<String, Integer> δИсточников) {
    }

    /** Накопитель по карте. */
    private static final class Итог {
        String имя = "";
        int опытов;
        int выполнено;
        long δЛидер;
        int nЛидер;
        long δОтстающий;
        int nОтстающий;
        double сдвиг;
        long δПобед;
        long δМеста;
        /** Раздельно: опыты, где карту выполнили, и где нет — разница и есть вклад награды. */
        /** Что награда выдала на деле — пара примеров для отчёта. */
        List<String> выдано = new ArrayList<>();
        /** ОТКУДА ОЧКИ: Δ по источникам (опыт − контроль), сумма по опытам. */
        Map<String, Long> источники = new TreeMap<>();
        /** Опытов, где карта хоть раз была готова к выполнению. */
        int былаГотова;
        long δОчковВып;
        long δОчковНевып;
        double сдвигВып;
        double сдвигНевып;

        void учесть(Опыт о) {
            опытов++;
            if (о.былаГотова()) {
                былаГотова++;
            }
            for (var e : о.δИсточников().entrySet()) {
                источники.merge(e.getKey(), (long) e.getValue(), Long::sum);
            }
            if (о.выполнена()) {
                выполнено++;
                if (выдано.size() < 2 && о.выдано() != null) {
                    выдано.add(о.выдано());
                }
                δОчковВып += о.δОчков();
                сдвигВып += о.сдвиг();
            } else {
                δОчковНевып += о.δОчков();
                сдвигНевып += о.сдвиг();
            }
            if (о.лидер()) {
                δЛидер += о.δОчков();
                nЛидер++;
            } else {
                δОтстающий += о.δОчков();
                nОтстающий++;
            }
            сдвиг += о.сдвиг();
            δПобед += о.δПобед();
            δМеста += о.δМеста();
        }

        double дЛ() {
            return nЛидер == 0 ? 0 : (double) δЛидер / nЛидер;
        }

        double дО() {
            return nОтстающий == 0 ? 0 : (double) δОтстающий / nОтстающий;
        }
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int срезов = арг(args, 0, 20);
        int раунд = арг(args, 1, 3);
        int горизонт = арг(args, 2, 3);
        int игроков = арг(args, 3, 4);
        String свод = args.length > 4 ? args[4] : "1.50.0";
        int потоков = арг(args, 5, Math.max(1, Runtime.getRuntime().availableProcessors()));
        String набор = args.length > 6 && !"-".equals(args[6]) ? args[6] : null;
        List<String> толькоКарты = args.length > 7 ? List.of(args[7].split(",")) : null;
        if (набор != null) {
            // набор заданий подменяется правкой свода, как и любая другая настройка запуска
            String было = System.getProperty("kelium.rules", "").trim();
            System.setProperty("kelium.rules", (было.isEmpty() ? "" : было + ",")
                + "content_versions.objectives=" + набор);
        }

        GameConfig пробный = GameConfig.buildCached(свод, игроков, 1L, null, null);
        Map<String, Итог> итоги = new TreeMap<>();
        for (Map<String, Object> e : пробный.content.get("objectives").entries) {
            if ("starting".equals(String.valueOf(e.getOrDefault("kind", "regular")))) {
                continue;
            }
            String id = String.valueOf(e.get("id"));
            if (толькоКарты != null && !толькоКарты.contains(id)) {
                continue;
            }
            Итог и = new Итог();
            Object язык = e.get("язык");
            и.имя = язык instanceof Map<?, ?> я && я.get("имя") != null
                ? String.valueOf(я.get("имя")) : String.valueOf(e.getOrDefault("name", id));
            итоги.put(id, и);
        }
        System.out.println("карт: " + итоги.size() + ", срезов: " + срезов + ", раунд среза " + раунд
            + ", горизонт " + (горизонт == 0 ? "до конца" : горизонт + " р.") + ", потоков " + потоков);

        long t0 = System.currentTimeMillis();
        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        // 1) срезы и контроль — по одному на срез
        List<Future<Срез>> срезы = new ArrayList<>();
        for (int g = 0; g < срезов; g++) {
            final long сид = 91000L + g;
            срезы.add(пул.submit(() -> срез(свод, игроков, сид, раунд, горизонт)));
        }
        // 2) опыты — каждая пара (срез, карта, место) отдельной задачей
        List<Future<Опыт>> работы = new ArrayList<>();
        for (Future<Срез> f : срезы) {
            Срез ср = f.get();
            if (ср == null) {
                continue;
            }
            for (String карта : итоги.keySet()) {
                if (вРукахУКого(ср.s, карта) >= 0) {
                    continue;                       // карту уже держат — опыт нечестный
                }
                for (int место : new int[] {ср.лидер, ср.отстающий}) {
                    работы.add(пул.submit(() -> опыт(ср, карта, место, раунд, горизонт)));
                }
            }
        }
        System.out.printf(Locale.ROOT, "срезы готовы за %.0f с, опытов: %d%n",
            (System.currentTimeMillis() - t0) / 1000.0, работы.size());
        int готово = 0;
        for (Future<Опыт> f : работы) {
            Опыт о = f.get();
            if (о != null) {
                итоги.get(о.карта()).учесть(о);
            }
            if (++готово % 100 == 0) {
                System.out.printf(Locale.ROOT, "опытов %d/%d, %.0f с%n", готово, работы.size(),
                    (System.currentTimeMillis() - t0) / 1000.0);
            }
        }
        пул.shutdown();
        отчёт(итоги, свод, срезов, раунд, горизонт, игроков);
    }

    /** Срез партии: замороженное состояние, места и общий для всех опытов контроль. */
    private static final class Срез {
        GameState s;
        long сид2;
        int лидер;
        int отстающий;
        Прогон контроль;
    }

    /** Доиграть партию до конца раунда R и снять с неё контрольное доигрывание. */
    private static Срез срез(String свод, int игроков, long сид, int раунд, int горизонт) {
        GameConfig cfg = GameConfig.buildCached(свод, игроков, сид, null, null);
        GameState s = Setup.buildGame(cfg);
        new GameEngine(s, боты(игроков, сид), null).runToRound(раунд);
        if (s.winner != null) {
            return null;                            // партия кончилась раньше среза
        }
        Срез ср = new Срез();
        ср.s = s;
        ср.сид2 = сид * 7919L;
        int[] места = места(s);
        ср.лидер = места[0];
        ср.отстающий = места[игроков - 1];
        ср.контроль = прогон(s, null, -1, ср.сид2, раунд, горизонт);
        return ср.контроль == null ? null : ср;
    }

    private static Опыт опыт(Срез ср, String карта, int место, int раунд, int горизонт) {
        Прогон опыт = прогон(ср.s, карта, место, ср.сид2, раунд, горизонт);
        Прогон контроль = ср.контроль;
        if (опыт == null) {
            return null;
        }
        int δОчков = опыт.очки[место] - контроль.очки[место];
        int δПобед = (опыт.победители.contains(место) ? 1 : 0) - (контроль.победители.contains(место) ? 1 : 0);
        int δМеста = ранг(контроль.очки, место) - ранг(опыт.очки, место);
        return new Опыт(карта, место == ср.лидер, опыт.выполнена, δОчков, δПобед,
            сдвиг(опыт, контроль, место), δМеста, опыт.выдано, опыт.былаГотова || опыт.выполнена,
            δИсточников(опыт.разбивка.get(место), контроль.разбивка.get(место)));
    }

    /** Результат одного доигрывания. */
    private static final class Прогон {
        int[] очки;
        /** Разбивка очков по источникам, по местам. */
        List<Map<String, Integer>> разбивка = new ArrayList<>();
        List<Integer> победители = new ArrayList<>();
        boolean выполнена;
        boolean былаГотова;
        String выдано;
        /** Сыгранные действия по местам: место → действие → сколько раз. */
        Map<Integer, Map<String, Integer>> действия = new HashMap<>();
    }

    /**
     * Доиграть копию среза. {@code карта == null} — контроль.
     */
    private static Прогон прогон(GameState срез, String карта, int место, long сид2, int раунд,
                                 int горизонт) {
        GameState s = срез.deepCopy(сид2);
        // ОПЫТ: карту вынимают из колоды и кладут игроку в руку. Контроль (карта
        // null) — позиция как есть; копии расходятся только из-за этой карты.
        if (карта != null) {
            Deck колода = s.decks.get("objectives");
            if (колода != null) {
                колода.removeCard(карта);
            }
            s.player(место).objectiveHand.add(карта);
        }
        Прогон п = new Прогон();
        GameEngine e = new GameEngine(s, боты(s.numPlayers(), сид2), ev -> {
            if (!(ev.get("seat") instanceof Integer si)) {
                return;
            }
            String тип = String.valueOf(ev.get("type"));
            if ("objective_hints".equals(тип) && si == место && карта != null
                    && ev.get("hints") instanceof List<?> hs) {
                for (Object h : hs) {
                    if (h instanceof Map<?, ?> m && карта.equals(String.valueOf(m.get("card")))
                            && Boolean.TRUE.equals(m.get("ready"))) {
                        п.былаГотова = true;
                    }
                }
            }
            if ("objective".equals(тип) && si == место && карта != null && карта.equals(String.valueOf(ev.get("card")))) {
                п.выполнена = true;
                п.выдано = String.valueOf(ev.get("granted"));
            } else if ("action".equals(тип) && Boolean.TRUE.equals(ev.get("ok"))) {
                п.действия.computeIfAbsent(si, k -> new HashMap<>())
                    .merge(String.valueOf(ev.get("action")), 1, Integer::sum);
            }
        });
        int предел = горизонт <= 0 ? 99 : раунд + горизонт;
        try {
            e.withRoundLimit(предел).resume();
        } catch (RuntimeException ex) {
            return null;                            // редкий сбой движка — опыт выбрасываем
        }
        Map<Integer, Map<String, Integer>> sc = Scoring.scoreAll(s);
        п.очки = new int[s.numPlayers()];
        for (int i = 0; i < s.numPlayers(); i++) {
            п.очки[i] = sc.get(i).getOrDefault("total", 0);
            п.разбивка.add(new HashMap<>(sc.get(i)));
        }
        if (горизонт <= 0) {
            п.победители.addAll(s.winners.isEmpty() && s.winner != null ? List.of(s.winner) : s.winners);
        }
        return п;
    }

    private static Map<String, Integer> δИсточников(Map<String, Integer> опыт, Map<String, Integer> контроль) {
        Map<String, Integer> out = new HashMap<>();
        java.util.Set<String> ключи = new java.util.HashSet<>(опыт.keySet());
        ключи.addAll(контроль.keySet());
        for (String k : ключи) {
            if ("total".equals(k)) {
                continue;
            }
            int d = опыт.getOrDefault(k, 0) - контроль.getOrDefault(k, 0);
            if (d != 0) {
                out.put(k, d);
            }
        }
        return out;
    }

    /** Насколько поменялся набор действий игрока: опыт против контроля. */
    private static double сдвиг(Прогон опыт, Прогон контроль, int место) {
        return полусумма(опыт.действия.getOrDefault(место, Map.of()),
            контроль.действия.getOrDefault(место, Map.of()));
    }

    private static double полусумма(Map<String, Integer> а, Map<String, Integer> б) {
        double na = а.values().stream().mapToInt(Integer::intValue).sum();
        double nb = б.values().stream().mapToInt(Integer::intValue).sum();
        if (na == 0 || nb == 0) {
            return 0;
        }
        java.util.Set<String> ключи = new java.util.HashSet<>(а.keySet());
        ключи.addAll(б.keySet());
        double d = 0;
        for (String k : ключи) {
            d += Math.abs(а.getOrDefault(k, 0) / na - б.getOrDefault(k, 0) / nb);
        }
        return d / 2;
    }

    private static List<Agent> боты(int игроков, long сид) {
        List<Agent> ags = new ArrayList<>();
        int shift = (int) (Math.abs(сид) % игроков);
        for (int i = 0; i < игроков; i++) {
            ags.add(kelium.agents.BotCatalog.create(ПУЛ.get((i + shift) % ПУЛ.size()), i,
                new Random(i * 131L + сид), игроков));
        }
        return ags;
    }

    /** Места игроков по очкам среза: [0] — лидер. */
    private static int[] места(GameState s) {
        Map<Integer, Map<String, Integer>> sc = Scoring.scoreAll(s);
        Integer[] idx = new Integer[s.numPlayers()];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, (a, b) -> Integer.compare(
            sc.get(b).getOrDefault("total", 0), sc.get(a).getOrDefault("total", 0)));
        int[] out = new int[idx.length];
        for (int i = 0; i < idx.length; i++) {
            out[i] = idx[i];
        }
        return out;
    }

    private static int ранг(int[] очки, int место) {
        int r = 0;
        for (int i = 0; i < очки.length; i++) {
            if (очки[i] > очки[место]) {
                r++;
            }
        }
        return r;
    }

    private static int вРукахУКого(GameState s, String карта) {
        for (PlayerState p : s.players) {
            if (p.objectiveHand.contains(карта)) {
                return p.seat;
            }
        }
        return -1;
    }

    private static int арг(String[] a, int i, int д) {
        return a.length > i ? Integer.parseInt(a[i]) : д;
    }

    private static void отчёт(Map<String, Итог> итоги, String свод, int срезов, int раунд, int горизонт,
                              int игроков) throws java.io.IOException {
        StringBuilder b = new StringBuilder();
        b.append("# Стенд заданий\n\n");
        b.append("Свод **").append(свод).append("**, игроков ").append(игроков)
            .append(", срезов ").append(срезов).append(", срез после раунда ").append(раунд)
            .append(", горизонт ").append(горизонт == 0 ? "до конца партии" : горизонт + " раунда")
            .append(".\n\n");
        b.append("Каждая строка — парные опыты: карта подложена лидеру или отстающему среза, ")
            .append("контроль — та же позиция без карты, с теми же случайностями.\n\n");
        b.append("- **выполнена** — доля опытов, где карту выполнили за горизонт;\n")
            .append("- **Δ лидеру / Δ отстающему** — прибавка очков от карты;\n")
            .append("- **ком** — Δ лидеру минус Δ отстающему (больше нуля — карта разгоняет лидера);\n")
            .append("- **сдвиг** — насколько карта поменяла набор сыгранных действий (0…1);\n")
            .append("- **Δ мест** — на сколько мест в среднем поднялся игрок;\n")
            .append("- **очки выполн. / невып.** — Δ очков в опытах, где карту выполнили и где нет;\n")
            .append("- **обход** — сдвиг действий у выполнивших минус у невыполнивших: шум от лишней ")
            .append("карты в руке вычитается, остаётся то, насколько награда увела игрока с привычного пути.\n\n");
        b.append("| карта | название | опытов | была готова | выполнена | Δ лидеру | Δ отстающему | ком | сдвиг | Δ мест "
            + "| очки выполн. | очки невып. | обход |");
        if (горизонт == 0) {
            b.append(" Δ побед |");
        }
        b.append("\n|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        if (горизонт == 0) {
            b.append("---:|");
        }
        b.append("\n");
        List<Map.Entry<String, Итог>> список = new ArrayList<>(итоги.entrySet());
        список.sort((x, y) -> Double.compare(
            (double) y.getValue().выполнено / Math.max(1, y.getValue().опытов),
            (double) x.getValue().выполнено / Math.max(1, x.getValue().опытов)));
        double сумКом = 0;
        double сумСдвиг = 0;
        double сумВып = 0;
        int n = 0;
        for (var e : список) {
            Итог и = e.getValue();
            if (и.опытов == 0) {
                continue;
            }
            double вып = (double) и.выполнено / и.опытов;
            double ком = и.дЛ() - и.дО();
            double сд = и.сдвиг / и.опытов;
            сумКом += ком;
            сумСдвиг += сд;
            сумВып += вып;
            n++;
            int нв = и.опытов - и.выполнено;
            String очВ = и.выполнено == 0 ? "—" : String.format(Locale.ROOT, "%+.2f", (double) и.δОчковВып / и.выполнено);
            String очН = нв == 0 ? "—" : String.format(Locale.ROOT, "%+.2f", (double) и.δОчковНевып / нв);
            String обх = и.выполнено == 0 || нв == 0 ? "—" : String.format(Locale.ROOT, "%+.2f",
                и.сдвигВып / и.выполнено - и.сдвигНевып / нв);
            b.append(String.format(Locale.ROOT, "| %s | %s | %d | %.0f%% | %.0f%% | %+.2f | %+.2f | %+.2f | %.2f | %+.2f | %s | %s | %s |",
                e.getKey(), и.имя, и.опытов, 100.0 * и.былаГотова / и.опытов, 100 * вып, и.дЛ(), и.дО(), ком, сд, (double) и.δМеста / и.опытов,
                очВ, очН, обх));
            if (горизонт == 0) {
                b.append(String.format(Locale.ROOT, " %+.0f%% |", 100.0 * и.δПобед / и.опытов));
            }
            b.append("\n");
        }
        if (n > 0) {
            b.append(String.format(Locale.ROOT,
                "\n**По набору:** выполняется в среднем %.0f%%, ком %+.2f очка, сдвиг %.2f.\n",
                100 * сумВып / n, сумКом / n, сумСдвиг / n));
        }
        b.append("\n## Откуда очки — Δ по источникам на опыт (опыт − контроль)\n\n");
        b.append("Источник растёт — карта ведёт туда; падает — игрок за него заплатил. ")
            .append("Показаны источники с |Δ| ≥ 0.15 на опыт.\n\n");
        for (var e : итоги.entrySet()) {
            Итог и = e.getValue();
            if (и.опытов == 0) {
                continue;
            }
            List<String> части = new ArrayList<>();
            и.источники.entrySet().stream()
                .sorted((x, y) -> Long.compare(Math.abs(y.getValue()), Math.abs(x.getValue())))
                .forEach(x -> {
                    double v = (double) x.getValue() / и.опытов;
                    if (Math.abs(v) >= 0.15) {
                        части.add(String.format(Locale.ROOT, "%s %+.2f", x.getKey(), v));
                    }
                });
            b.append("- **").append(e.getKey()).append(" ").append(и.имя).append("**: ")
                .append(части.isEmpty() ? "—" : String.join(", ", части)).append("\n");
        }
        b.append("\n## Что выдали награды (примеры)\n\n");
        for (var e : итоги.entrySet()) {
            if (!e.getValue().выдано.isEmpty()) {
                b.append("- **").append(e.getKey()).append(" ").append(e.getValue().имя).append("**: ")
                    .append(String.join(" · ", e.getValue().выдано)).append("\n");
            }
        }
        String набор = System.getProperty("kelium.rules", "").contains("content_versions.objectives=")
            ? System.getProperty("kelium.rules").replaceAll(".*content_versions.objectives=([^,]+).*", "$1") : "свод";
        b.insert(0, "Набор заданий: **" + набор + "**\n\n");
        Path out = Path.of("reports", "balance", "стенд-заданий-" + набор + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }
}
