package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Modules;
import kelium.engine.Setup;

/**
 * ПОДНИМАЕТ ЛИ СИНИЙ МОДУЛЬ ПРОИЗВОДСТВО НА ДЕЛЕ.
 *
 * <p>ВОПРОС ДИЗАЙНЕРА 28.08.2026: жетон сборки должен увеличивать выход
 * Снаряжения — и войск, и боеприпасов, — а прибавки не видно. В коде прибавка
 * есть ({@code Modules.assemblyOutput}), значит числами надо разделить три
 * разные причины: жетон не доходит до игрока; доходит, но не ложится на планшет;
 * лежит, но не на том здании, которым игрок работает.
 *
 * <p>ЧТО СЧИТАЕТСЯ. На каждом решении Сборки (точка выбора assemble)
 * запоминается здание, выбранный выход и стоял ли на этом здании синий жетон.
 * Выход считается той же функцией, что и в движке, поэтому «с жетоном» и «без»
 * сравниваются честно. Отдельно — сколько синих жетонов игрок получил за партию
 * и сколько из них реально лежит на планшете к концу.
 *
 * <p>Запуск: {@code kelium.СинийМодуль [партий] [игроков] [свод]}
 */
public final class СинийМодуль {

    private СинийМодуль() {
    }

    /** Накопитель по одной ветке: с жетоном или без. */
    private static final class Ветка {
        long решений;
        long войскВыбрано;
        long войскВышло;
        long бпрВыбрано;
        long бпрВышло;
    }

    /** Обёртка: подсматривает решения Сборки, ничего не меняя в выборе. */
    private static final class Наблюдатель extends Agent {
        private final Agent внутри;
        private final Ветка сЖетоном;
        private final Ветка без;

        Наблюдатель(Agent внутри, Ветка сЖетоном, Ветка без) {
            super(внутри.seat, внутри.name);
            this.внутри = внутри;
            this.сЖетоном = сЖетоном;
            this.без = без;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options,
                             Map<String, Object> context) {
            Choice pick = внутри.choose(state, options, context);
            if (!"assemble".equals(String.valueOf(context.get("kind")))
                    || !(pick.payload() instanceof Map<?, ?> pl)) {
                return pick;
            }
            BuildingType bt = тип(String.valueOf(context.get("building_type")));
            if (bt == null) {
                return pick;
            }
            PlayerState p = state.player(seat);
            Ветка в = p.bluePlacements.containsKey(bt) ? сЖетоном : без;
            в.решений++;
            String kind = String.valueOf(pl.get("kind"));
            if ("ammo".equals(kind) || "both".equals(kind)) {
                в.бпрВыбрано++;
                в.бпрВышло += Modules.assemblyOutput(p, bt, "ammo");
            }
            if ("unit".equals(kind) || "both".equals(kind)) {
                в.войскВыбрано++;
                в.войскВышло += Modules.assemblyOutput(p, bt, "unit");
            }
            return pick;
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            внутри.observeEvent(event);
        }

        @Override
        public void observePublicEvent(Map<String, Object> event) {
            внутри.observePublicEvent(event);
        }
    }

    private static BuildingType тип(String code) {
        for (BuildingType b : BuildingType.values()) {
            if (b.code.equals(code)) {
                return b;
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 150;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Ветка сЖетоном = new Ветка();
        Ветка без = new Ветка();
        long получено = 0;          // синих жетонов выдано игрокам всего
        long наПланшете = 0;        // синих жетонов лежит на планшете к концу
        long золотых = 0;
        long игроков = 0;
        long безЖетонаВовсе = 0;    // игроков, не получивших ни одного синего
        Map<String, Long> поЗданиям = new LinkedHashMap<>();
        // ПРЕДЕЛЬНЫЕ ЗНАЧЕНИЯ (вопрос дизайнера 28.08.2026): доходил ли кто-то
        // до трёх и четырёх жетонов ОДНОГО цвета сразу. Счётчик жетонов за
        // партию только растёт — выданный жетон из игры не уходит, — поэтому
        // значение на конец партии и есть наибольшее за партию.
        long[] раскладкаСиних = new long[9];
        long[] раскладкаКрасных = new long[9];
        int рекордСиних = 0;
        int рекордКрасных = 0;
        // Докуда доходят по трекам: синий модуль выдают ШАГИ 2 и 3 синего трека
        // (right), и если туда не доходят, жетону взяться неоткуда.
        Map<String, long[]> поТрекам = new LinkedHashMap<>();   // [сумма шагов, дошли до 2+]

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 81000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(new Наблюдатель(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 811L + g), players),
                    сЖетоном, без));
            }
            GameEngine.playGame(s, ags, ev -> { });
            for (PlayerState p : s.players) {
                игроков++;
                получено += p.blueModules;
                наПланшете += p.bluePlacements.size();
                золотых += p.goldModules;
                if (p.blueModules == 0) {
                    безЖетонаВовсе++;
                }
                раскладкаСиних[Math.min(p.blueModules, раскладкаСиних.length - 1)]++;
                раскладкаКрасных[Math.min(p.redModules, раскладкаКрасных.length - 1)]++;
                рекордСиних = Math.max(рекордСиних, p.blueModules);
                рекордКрасных = Math.max(рекордКрасных, p.redModules);
                for (BuildingType bt : p.bluePlacements.keySet()) {
                    поЗданиям.merge(bt.code, 1L, Long::sum);
                }
                for (String track : s.tech.tracks) {
                    int высшийШаг = 0;
                    List<List<Integer>> оккуп = s.tech.occupancy.get(track);
                    for (int step = 0; step < оккуп.size(); step++) {
                        if (оккуп.get(step).contains(p.seat)) {
                            высшийШаг = step + 1;
                        }
                    }
                    long[] т = поТрекам.computeIfAbsent(track, k -> new long[2]);
                    т[0] += высшийШаг;
                    if (высшийШаг >= 2) {
                        т[1]++;
                    }
                }
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# Синий модуль: поднимает ли он производство\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players)
            .append(". Места и характеры ротируются.\n\n");

        b.append("## Доходят ли жетоны до планшета\n\n");
        b.append("| показатель | на игрока за партию |\n|---|---:|\n");
        b.append("| синих жетонов получено, шт | ")
            .append(окр((double) получено / игроков)).append(" |\n");
        b.append("| из них лежит на планшете к концу, шт | ")
            .append(окр((double) наПланшете / игроков)).append(" |\n");
        b.append("| золотых жетонов (любых), шт | ")
            .append(окр((double) золотых / игроков)).append(" |\n");
        b.append("| игроков БЕЗ единого синего жетона, доля | ")
            .append(проц(безЖетонаВовсе, игроков)).append(" |\n");

        b.append("\n## Предельные значения: сколько жетонов бывает у игрока\n\n");
        b.append("Жетон, однажды выданный, из игры не уходит, поэтому значение на ")
            .append("конец партии — оно же наибольшее за партию.\n\n");
        b.append("| жетонов у игрока, шт | синих: игроков | доля | красных: игроков | доля |\n");
        b.append("|---|---:|---:|---:|---:|\n");
        for (int i = 0; i < раскладкаСиних.length; i++) {
            if (раскладкаСиних[i] == 0 && раскладкаКрасных[i] == 0) {
                continue;
            }
            b.append("| ")
                .append(i == раскладкаСиних.length - 1 ? (i + " и больше") : String.valueOf(i))
                .append(" | ").append(раскладкаСиних[i])
                .append(" | ").append(проц(раскладкаСиних[i], игроков))
                .append(" | ").append(раскладкаКрасных[i])
                .append(" | ").append(проц(раскладкаКрасных[i], игроков))
                .append(" |\n");
        }
        b.append("\nРЕКОРД за все партии: синих **").append(рекордСиних)
            .append("**, красных **").append(рекордКрасных).append("**.\n");

        b.append("\n## Выход Сборки: с жетоном на здании и без него\n\n");
        b.append("| ветка | решений Сборки, шт | войск за решение, шт | БПР за решение, шт |\n");
        b.append("|---|---:|---:|---:|\n");
        строка(b, "здание С СИНИМ жетоном", сЖетоном);
        строка(b, "здание без жетона", без);

        b.append("\n## Докуда доходят по трекам (right = трек синих модулей)\n\n");
        b.append("| трек | средний высший шаг, № | дошли до шага 2, доля |\n|---|---:|---:|\n");
        for (var e : поТрекам.entrySet()) {
            long[] т = e.getValue();
            b.append("| ").append(e.getKey()).append(" | ")
                .append(окр((double) т[0] / игроков)).append(" | ")
                .append(проц(т[1], игроков)).append(" |\n");
        }

        b.append("\n## На какие здания кладут синий жетон\n\n");
        b.append("| здание | случаев, шт |\n|---|---:|\n");
        for (var e : поЗданиям.entrySet()) {
            b.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }

        Path out = Path.of("reports", "balance", "синий-модуль-" + ruleset + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("получено на игрока: " + окр((double) получено / игроков)
            + ", на планшете: " + окр((double) наПланшете / игроков));
        System.out.println("решений Сборки с жетоном: " + сЖетоном.решений
            + ", без: " + без.решений);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static void строка(StringBuilder b, String имя, Ветка в) {
        b.append("| ").append(имя).append(" | ").append(в.решений)
            .append(" | ").append(в.войскВыбрано == 0 ? "—"
                : окр((double) в.войскВышло / в.войскВыбрано))
            .append(" | ").append(в.бпрВыбрано == 0 ? "—"
                : окр((double) в.бпрВышло / в.бпрВыбрано))
            .append(" |\n");
    }

    private static String проц(long часть, long всего) {
        return всего == 0 ? "—"
            : String.format(Locale.ROOT, "%.1f%%", 100.0 * часть / всего);
    }

    private static String окр(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
