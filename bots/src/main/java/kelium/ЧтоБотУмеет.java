package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ЧТО БОТ УМЕЕТ, А ЧЕГО НЕ ТРОГАЕТ ВОВСЕ.
 *
 * <p><b>Зачем это ДО обучения, а не после.</b> Эволюция весов подкручивает то,
 * что бот уже делает. Если он не рассматривает супер-задание в плане ни разу за
 * партию, никакая эволюция его этому не научит: нечего усиливать. Такое чинится
 * в планировщике, а не в отборе, и отличить одно от другого можно только
 * замером. Поэтому здесь считается не «насколько хорошо», а «делает ли вообще».
 *
 * <p>Три слоя карт меряются раздельно:
 *
 * <ul>
 *   <li><b>обычные задания</b> — тянут, выполняют, жгут; и отдельно: сколько
 *       выполнений дали НАГРАДУ-ДЕЙСТВИЕ, ради которой они и переделаны;</li>
 *   <li><b>супер-задания</b> — выбирают одно из двух на подготовке, и вопрос в
 *       том, доходит ли дело до выполнения хоть когда-нибудь;</li>
 *   <li><b>арсенал</b> — ставят ли карты на планшет и пользуются ли их
 *       спец-действием, или карта стоит мёртвым грузом.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.ЧтоБотУмеет [партий] [игроков] [уровень]}.
 */
public final class ЧтоБотУмеет {

    private ЧтоБотУмеет() {
    }

    /** Счётчик на весь прогон: считают все потоки, печатает главный. */
    private static final class Счёт {
        final Map<String, AtomicLong> числа = new TreeMap<>();

        void плюс(String ключ, long сколько) {
            числа.computeIfAbsent(ключ, k -> new AtomicLong()).addAndGet(сколько);
        }

        double на(String ключ, double делитель) {
            AtomicLong v = числа.get(ключ);
            return v == null ? 0 : v.get() / делитель;
        }
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int уровень = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int потоков = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);

        Счёт с = new Счёт();
        // Карты, которых бот НЕ КОСНУЛСЯ НИ РАЗУ, интереснее средних: среднее
        // «0,3 выполнения» может означать и «все понемногу», и «одна карта
        // тянет всё, остальные мертвы».
        Map<String, AtomicLong> выполненоПоКартам = new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, AtomicLong> сожженоПоКартам = new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, AtomicLong> суперВыбрано = new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, AtomicLong> суперСделано = new java.util.concurrent.ConcurrentHashMap<>();

        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        try {
            List<Future<?>> ф = new ArrayList<>();
            for (int g = 0; g < партий; g++) {
                final long seed = 9_100_000L + g;
                ф.add(пул.submit((Callable<Void>) () -> {
                    партия(seed, игроков, уровень, с, выполненоПоКартам, сожженоПоКартам,
                        суперВыбрано, суперСделано);
                    return null;
                }));
            }
            for (Future<?> f : ф) {
                f.get();
            }
        } finally {
            пул.shutdownNow();
        }

        double места = партий * (double) игроков;
        out.printf("ЧТО БОТ УМЕЕТ · свод %s · %d партий · %d игроков · уровень %d%n",
            GameConfig.DEFAULT_RULESET, партий, игроков, уровень);
        out.println("поля: " + LayoutLibrary.describePool(игроков));
        out.println("всё — НА ИГРОКА ЗА ПАРТИЮ\n");

        out.println("ОБЫЧНЫЕ ЗАДАНИЯ");
        out.printf("  вытянуто                     %6.2f%n", с.на("тянул", места));
        out.printf("  ВЫПОЛНЕНО                    %6.2f%n", с.на("выполнил", места));
        out.printf("    из них с усилением         %6.2f%n", с.на("усилил", места));
        out.printf("  сожжено на утиль             %6.2f%n", с.на("сжёг", места));
        double вып = с.на("выполнил", места);
        double сж = с.на("сжёг", места);
        out.printf("  ОТНОШЕНИЕ выполнено/сожжено  %6.2f%n", сж == 0 ? 0 : вып / сж);
        out.printf("  награда-ДЕЙСТВИЕ выдана      %6.2f%n", с.на("наградаДействие", места));
        out.printf("    из них действие СЫГРАЛО   %6.2f%n", с.на("действиеСыграло", места));
        out.printf("  разных карт выполнено хоть раз (с начальными): %d%n", выполненоПоКартам.size());

        out.println("\nСУПЕР-ЗАДАНИЯ");
        out.printf("  карт на руках к концу       %6.2f%n", с.на("суперВзял", места));
        out.printf("  из них ЗАПЛАТИЛИ очки      %6.2f%n", с.на("суперПлатил", места));
        out.printf("  очков с супер-заданий        %6.2f%n", с.на("суперОчки", места));
        out.printf("  разных супер-заданий на руках: %d%n", суперВыбрано.size());

        out.println("\nАРСЕНАЛ");
        out.printf("  карт взято                   %6.2f%n", с.на("арсеналВзял", места));
        out.printf("  УСТАНОВЛЕНО на планшет       %6.2f%n", с.на("арсеналСтавил", места));
        out.printf("  спец-действие карты сыграно  %6.2f%n", с.на("арсеналСпец", места));
        out.printf("  реакция карты сработала    %6.2f%n", с.на("арсеналРеакция", места));
        out.printf("  не влезло (нет места)        %6.2f%n", с.на("арсеналНеВлезло", места));
        out.printf("  содержание не уплачено       %6.2f%n", с.на("арсеналНеСодержал", места));

        out.println("\nМЁРТВЫЕ ЗАДАНИЯ (не выполнены НИ РАЗУ за весь прогон)");
        List<String> мёртвые = new ArrayList<>();
        for (String id : сожженоПоКартам.keySet()) {
            if (!выполненоПоКартам.containsKey(id)) {
                мёртвые.add(id);
            }
        }
        java.util.Collections.sort(мёртвые);
        out.println("  " + (мёртвые.isEmpty() ? "нет — каждая карта хоть раз выполнена"
            : String.join(", ", мёртвые)));

        // НАСКОЛЬКО УЗОК ВЫБОР НА ХОДУ. Дизайнерский критерий 20.09.2026:
        // «настоящий выбор — когда у игрока есть варианты, и ни один не
        // единственно выгодный». Здесь считается, сколько вариантов вообще
        // рассматривалось и насколько лучший оторвался от второго.
        // Включается ключом -Dkelium.bot.diag=true.
        out.println();
        kelium.agents.HeuristicAgent.Диагностика.напечатать(out);

        out.println("\nСАМЫЕ ЖИВЫЕ ЗАДАНИЯ (выполнений за прогон)");
        выполненоПоКартам.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(8)
            .forEach(e -> out.printf("  %-6s %d%n", e.getKey(), e.getValue().get()));
    }

    private static void партия(long seed, int игроков, int уровень, Счёт с,
                               Map<String, AtomicLong> выполненоПоКартам,
                               Map<String, AtomicLong> сожженоПоКартам,
                               Map<String, AtomicLong> суперВыбрано,
                               Map<String, AtomicLong> суперСделано) {
        List<String> состав = Bots.ROSTER_4;
        GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
        List<Agent> agents = new ArrayList<>();
        int сдвиг = (int) Math.floorMod(seed, состав.size());
        for (int i = 0; i < игроков; i++) {
            agents.add(Bots.create(состав.get((i + сдвиг) % состав.size()),
                Bots.Level.of(уровень), i, new Random(seed * 31 + i), игроков));
        }
        new GameEngine(s, agents, ev -> {
            String тип = String.valueOf(ev.get("type"));
            String карта = ev.get("card") == null ? null : String.valueOf(ev.get("card"));
            switch (тип) {
                case "objective_drawn" -> с.плюс("тянул", 1);
                case "objective" -> {
                    с.плюс("выполнил", 1);
                    if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                        с.плюс("усилил", 1);
                    }
                    // Награда-ДЕЙСТВИЕ: ради неё задания и переделали 19.09.2026.
                    // Если выполнений много, а действий ноль, значит выполняются
                    // не те карты, и переделка прошла мимо.
                    // НАГРАДА ЛЕЖИТ В granted.base, А НЕ В КОРНЕ СОБЫТИЯ. Первая
                    // версия замера смотрела в корень и объявила ноль наград-
                    // действий при 4,8 выполнениях — чистая выдумка.
                    if (ev.get("granted") instanceof Map<?, ?> выдано
                            && выдано.get("base") instanceof Map<?, ?> база
                            && база.get("action") != null) {
                        с.плюс("наградаДействие", 1);
                        if (Boolean.TRUE.equals(база.get("action_ran"))) {
                            с.плюс("действиеСыграло", 1);
                        }
                    }
                    if (карта != null) {
                        выполненоПоКартам.computeIfAbsent(карта, k -> new AtomicLong())
                            .incrementAndGet();
                    }
                }
                case "objective_burn" -> {
                    с.плюс("сжёг", 1);
                    if (карта != null) {
                        сожженоПоКартам.computeIfAbsent(карта, k -> new AtomicLong())
                            .incrementAndGet();
                    }
                }
                case "super_pick" -> {
                    с.плюс("суперВыбрал", 1);
                    if (карта != null) {
                        суперВыбрано.computeIfAbsent(карта, k -> new AtomicLong())
                            .incrementAndGet();
                    }
                }
                case "arsenal" -> {
                    с.плюс("арсеналВзял", 1);
                    if ("install".equals(ev.get("mode"))) {
                        с.плюс("арсеналСтавил", 1);
                    }
                }
                // ДВА ПУТИ СПЕЦ-ДЕЙСТВИЯ, СЧИТАТЬ НАДО ОБА. Старый шлёт
                // arsenal_spec_use, новый (способности карт) — ability_spec.
                // Считая только старый, замер объявил, что бот не пользуется
                // картами ни разу; это было про имя события, а не про игру.
                case "arsenal_spec_use", "ability_spec" -> с.плюс("арсеналСпец", 1);
                case "ability_reaction" -> с.плюс("арсеналРеакция", 1);
                case "arsenal_no_room" -> с.плюс("арсеналНеВлезло", 1);
                case "arsenal_upkeep" -> {
                    if (Boolean.FALSE.equals(ev.get("paid"))) {
                        с.плюс("арсеналНеСодержал", 1);
                    }
                }
                default -> { }
            }
        }).run();

        // Супер-задания считаются по КОНЦУ партии: движок отмечает выполненные
        // в состоянии игрока, отдельного события на это нет.
        // ПОЛЕ superObjectivesDone В ДВИЖКЕ НЕ ЗАПОЛНЯЕТСЯ: его трогает только
        // отладочная сцена проигрывателя. Первая версия замера смотрела туда и
        // объявила, что супер-задания не выполняются никогда, — это было про
        // мёртвое поле, а не про игру. Настоящий механизм — накопитель:
        // СуперЗадания.vp платит по двум категориям каждой карты.
        for (PlayerState p : s.players) {
            с.плюс("суперВзял", p.superObjectives.size());
            int очки = kelium.engine.СуперЗадания.vp(s, p.seat);
            с.плюс("суперОчки", очки);
            if (очки > 0) {
                с.плюс("суперПлатил", 1);
            }
            for (String id : p.superObjectives) {
                суперВыбрано.computeIfAbsent(id, k -> new AtomicLong())
                    .incrementAndGet();
                if (очки > 0) {
                    суперСделано.computeIfAbsent(id, k -> new AtomicLong())
                        .incrementAndGet();
                }
            }
        }
    }
}
