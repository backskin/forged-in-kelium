package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * БОЛЬШОЙ ОТЧЁТ ПО ПАРТИЯМ — всё, что можно спросить у пачки сыгранных партий.
 *
 * <p>ЗАЧЕМ ОДИН ОТЧЁТ, А НЕ ДЕСЯТЬ УЗКИХ. Замеров в проекте много, и каждый
 * отвечает на свой вопрос: карты в деле, воронка боя, источники очков. Но когда
 * надо понять, ЧТО ВООБЩЕ ПРОИСХОДИТ В ПАРТИИ, десять узких таблиц не
 * складываются в картину: в них разные прогоны, разные составы, разные своды.
 * Здесь всё считается за ОДИН прогон и по одним и тем же партиям — значит числа
 * можно сравнивать между собой.
 *
 * <p>ЧТО ВНУТРИ:
 * <ol>
 *   <li>партии: длина в раундах (среднее, минимум, максимум, распределение), чем
 *       закончились, кто победил;</li>
 *   <li>очки: среднее, разброс, полная разбивка по источникам;</li>
 *   <li>действия: сколько раз какое сыграно, сколько прошло впустую;</li>
 *   <li>бой: боёв, попаданий, уничтожений, кто по кому бил, почему бой был
 *       пустым, трофеи;</li>
 *   <li>поле к концу партии: здания по типам, войска по родам, запасы;</li>
 *   <li>карты: сколько прокрутилось, что чаще выполняют, что чаще жгут;</li>
 *   <li>аномалии: партии без боёв, игроки без заданий, переполнение склада,
 *       слишком короткие и слишком длинные партии.</li>
 * </ol>
 *
 * <p>Запуск: {@code kelium.ОтчётПоПартиям [игроков] [партий] [свод] [боты через запятую]}
 */
public final class ОтчётПоПартиям {

    private ОтчётПоПартиям() {
    }

    /** Итог одной партии — строка «базы», по ней считается всё остальное. */
    private record Партия(int раундов, Integer победитель, String условиеПобеды,
                          int боёв, int уничтожений, int заданий, int сожжено,
                          double очкиЛидера, double очкиХудшего) {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 150;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> стол = args.length > 3 ? List.of(args[3].split(","))
            : List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        List<Партия> партии = new ArrayList<>();
        Map<String, Long> действия = new TreeMap<>();
        Map<String, Long> холостые = new TreeMap<>();
        Map<String, Long> причиныПустогоБоя = new TreeMap<>();
        Map<String, Long> концы = new TreeMap<>();
        Map<String, Long> событий = new TreeMap<>();
        Map<String, Double> очкиПоИсточникам = new TreeMap<>();
        Map<String, Long> победыБотов = new TreeMap<>();
        Map<String, Double> очкиБотов = new TreeMap<>();
        Map<String, Double> сносыБотов = new TreeMap<>();
        Map<String, Long> партийБота = new TreeMap<>();
        Map<String, Long> выполнено = new TreeMap<>();
        Map<String, Long> сожженоКарт = new TreeMap<>();
        Map<String, Long> установленоКарт = new TreeMap<>();
        Map<String, Long> ктоПоКому = new TreeMap<>();
        double[] поле = new double[8];      // здания, войска и запасы к концу
        long трофеевВзято = 0;
        long трофеевСдано = 0;
        long жетоновВТрофеи = 0;
        long переполнений = 0;
        long партийБезБоёв = 0;
        long игроковБезЗаданий = 0;

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 77000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            String[] ктоНаМесте = new String[players];
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ктоНаМесте[i] = стол.get((i + shift) % players);
                ags.add(kelium.agents.BotCatalog.create(ктоНаМесте[i], i,
                    new Random(i * 97L + g), players));
            }
            long[] c = new long[8];         // боёв, попаданий, сносов, заданий, сожжено...
            int[] сносыМеста = new int[players];
            int[] заданияМеста = new int[players];
            String[] условие = {null};
            Integer[] победитель = {null};
            GameEngine.playGame(s, ags, ev -> {
                String t = String.valueOf(ev.get("type"));
                событий.merge(t, 1L, Long::sum);
                int seat = ev.get("seat") instanceof Number n ? n.intValue() : -1;
                switch (t) {
                    case "action" -> {
                        String имя = String.valueOf(ev.get("action"));
                        действия.merge(имя, 1L, Long::sum);
                        if (!Boolean.TRUE.equals(ev.get("ok"))) {
                            холостые.merge(имя, 1L, Long::sum);
                        }
                        if ("combat".equals(имя)) {
                            c[0]++;
                        }
                    }
                    case "spec_combat" -> c[0]++;
                    case "combat_hit" -> {
                        c[1]++;
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            c[2]++;
                            if (seat >= 0 && seat < players) {
                                сносыМеста[seat]++;
                            }
                            Object жертва = ev.get("victim_owner");
                            if (seat >= 0 && жертва instanceof Number ж) {
                                ктоПоКому.merge(ктоНаМесте[seat] + " → "
                                    + ктоНаМесте[ж.intValue()], 1L, Long::sum);
                            }
                        }
                    }
                    case "combat_dry" -> причиныПустогоБоя.merge(
                        String.valueOf(ev.get("reason")), 1L, Long::sum);
                    case "objective" -> {
                        c[3]++;
                        выполнено.merge(String.valueOf(ev.get("card")), 1L, Long::sum);
                        if (seat >= 0 && seat < players) {
                            заданияМеста[seat]++;
                        }
                    }
                    case "objective_burn" -> {
                        c[4]++;
                        сожженоКарт.merge(String.valueOf(ev.get("card")), 1L, Long::sum);
                    }
                    case "arsenal" -> {
                        if (String.valueOf(ev.get("mode")).startsWith("install")) {
                            установленоКарт.merge(String.valueOf(ev.get("card")), 1L, Long::sum);
                        }
                    }
                    case "game_end" -> {
                        Object усл = ev.get("condition") != null
                            ? ev.get("condition") : ev.get("reason");
                        if (усл != null && условие[0] == null) {
                            условие[0] = String.valueOf(усл);
                        }
                        if (ev.get("winner") instanceof Number w && победитель[0] == null) {
                            победитель[0] = w.intValue();
                        }
                    }
                    default -> { }
                }
            });

            // ТРОФЕИ И ПЕРЕПОЛНЕНИЯ считаем по состоянию: события о них молчат.
            for (int i = 0; i < players; i++) {
                PlayerState p = s.player(i);
                var b = Scoring.scorePlayer(s, i);
                for (var e : b.entrySet()) {
                    if (!"total".equals(e.getKey())) {
                        очкиПоИсточникам.merge(e.getKey(), (double) e.getValue(), Double::sum);
                    }
                }
                double очки = b.getOrDefault("total", 0);
                очкиБотов.merge(ктоНаМесте[i], очки, Double::sum);
                сносыБотов.merge(ктоНаМесте[i], (double) сносыМеста[i], Double::sum);
                партийБота.merge(ктоНаМесте[i], 1L, Long::sum);
                if (заданияМеста[i] == 0) {
                    игроковБезЗаданий++;
                }
                int склад = p.resources.kelium() + p.resources.ammo() + p.resources.trophy();
                if (склад > kelium.engine.Storage.totalMax(s, p)) {
                    переполнений++;
                }
                int казармы = 0;
                int добытчики = 0;
                int станции = 0;
                for (BuildingToken bt : p.buildingsOnField()) {
                    switch (bt.type) {
                        case MINER -> добытчики++;
                        case POWER_PLANT -> станции++;
                        default -> казармы++;
                    }
                }
                поле[0] += казармы;
                поле[1] += добытчики;
                поле[2] += станции;
                for (UnitToken u : p.unitsOnField()) {
                    switch (u.type) {
                        case INFANTRY -> поле[3]++;
                        case VEHICLE -> поле[4]++;
                        case AIRCRAFT -> поле[5]++;
                        case TOWER -> поле[6]++;
                        default -> { }
                    }
                }
                поле[7] += склад;
                трофеевВзято += p.destroyedTokens.size();
            }
            // СЧЁТЧИК СОБЫТИЙ — ОБЩИЙ НА ПРОГОН, а не на партию: прибавлять его
            // к сумме на каждой партии значит складывать одно и то же снова и
            // снова. В отчёте выходило «203 конвертации трофеев за партию» при
            // четырёх игроках — то есть число росло с номером партии.
            жетоновВТрофеи = событий.getOrDefault("trophy_to_trophy", 0L);
            трофеевСдано = событий.getOrDefault("trophy_released", 0L);

            double лучш = -1;
            double хуже = 1e9;
            for (int i = 0; i < players; i++) {
                double v = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
                лучш = Math.max(лучш, v);
                хуже = Math.min(хуже, v);
            }
            if (победитель[0] != null && победитель[0] >= 0 && победитель[0] < players) {
                победыБотов.merge(ктоНаМесте[победитель[0]], 1L, Long::sum);
            }
            концы.merge(условие[0] == null ? "по концу раундов" : условие[0], 1L, Long::sum);
            if (c[0] == 0) {
                партийБезБоёв++;
            }
            партии.add(new Партия(s.round, победитель[0], условие[0], (int) c[0],
                (int) c[2], (int) c[3], (int) c[4], лучш, хуже));
        }

        int мест = games * players;
        StringBuilder b = new StringBuilder();
        b.append("# Отчёт по партиям ботов\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players).append(".\n\nСтол: ")
            .append(String.join(", ", стол)).append(" — места РОТИРУЮТСЯ, ")
            .append("иначе замер мерил бы раскладку, а не ботов.\n\n");

        // ---------- 1. ПАРТИИ ----------
        b.append("## 1. Партии: длина и чем кончились\n\n");
        int[] раунды = партии.stream().mapToInt(Партия::раундов).toArray();
        b.append(строкаЧисел("раундов в партии", раунды));
        Map<Integer, Long> расп = new TreeMap<>();
        for (int r : раунды) {
            расп.merge(r, 1L, Long::sum);
        }
        b.append("\nРаспределение длины:\n\n| раундов | партий | доля |\n|---:|---:|---:|\n");
        for (var e : расп.entrySet()) {
            b.append("| ").append(e.getKey()).append(" | ").append(e.getValue())
                .append(" | ").append(процент(e.getValue(), games)).append(" |\n");
        }
        b.append("\nЧем кончилась партия:\n\n| условие конца | партий | доля |\n|---|---:|---:|\n");
        сортПоЗначению(концы).forEach(e -> b.append("| ").append(e.getKey())
            .append(" | ").append(e.getValue()).append(" | ")
            .append(процент(e.getValue(), games)).append(" |\n"));

        // ---------- 2. КТО ПОБЕЖДАЕТ ----------
        b.append("\n## 2. Кто побеждает и с каким счётом\n\n");
        b.append("| бот | партий | средние очки | побед | доля побед | сносов за партию |\n");
        b.append("|---|---:|---:|---:|---:|---:|\n");
        for (String бот : стол) {
            long n = партийБота.getOrDefault(бот, 0L);
            b.append("| ").append(бот).append(" | ").append(n)
                .append(" | ").append(округл(очкиБотов.getOrDefault(бот, 0.0) / Math.max(1, n)))
                .append(" | ").append(победыБотов.getOrDefault(бот, 0L))
                .append(" | ").append(процент(победыБотов.getOrDefault(бот, 0L), games))
                .append(" | ").append(округл(сносыБотов.getOrDefault(бот, 0.0) / Math.max(1, n)))
                .append(" |\n");
        }
        int[] лидеры = партии.stream().mapToInt(p -> (int) p.очкиЛидера()).toArray();
        int[] последние = партии.stream().mapToInt(p -> (int) p.очкиХудшего()).toArray();
        b.append("\n").append(строкаЧисел("очки победителя партии", лидеры));
        b.append(строкаЧисел("очки последнего места", последние));

        // ---------- 3. ОЧКИ ПО ИСТОЧНИКАМ ----------
        b.append("\n## 3. Откуда берутся очки\n\n");
        double всегоОчков = очкиПоИсточникам.values().stream()
            .mapToDouble(Double::doubleValue).sum();
        b.append("| источник | на игрока за партию | доля всех очков |\n|---|---:|---:|\n");
        сортПоЗначениюD(очкиПоИсточникам).forEach(e -> {
            double v = e.getValue() / мест;
            if (Math.abs(v) >= 0.005) {
                b.append("| ").append(имяИсточника(e.getKey()))
                    .append(" | ").append(округл(v)).append(" | ")
                    .append(процент(Math.round(e.getValue()), Math.round(всегоОчков)))
                    .append(" |\n");
            }
        });

        // ---------- 4. ДЕЙСТВИЯ ----------
        b.append("\n## 4. Что боты делают: действия\n\n");
        long всегоДействий = действия.values().stream().mapToLong(Long::longValue).sum();
        b.append("Действий за партию всего (всеми игроками): **")
            .append(округл(всегоДействий / (double) games)).append("**\n\n");
        b.append("| действие | за партию | доля | прошло впустую |\n|---|---:|---:|---:|\n");
        сортПоЗначению(действия).forEach(e -> b.append("| ")
            .append(имяДействия(e.getKey())).append(" | ")
            .append(округл(e.getValue() / (double) games)).append(" | ")
            .append(процент(e.getValue(), всегоДействий)).append(" | ")
            .append(процент(холостые.getOrDefault(e.getKey(), 0L), e.getValue()))
            .append(" |\n"));

        // ---------- 5. БОЙ ----------
        b.append("\n## 5. Бой\n\n");
        int[] боёв = партии.stream().mapToInt(Партия::боёв).toArray();
        int[] сносов = партии.stream().mapToInt(Партия::уничтожений).toArray();
        b.append(строкаЧисел("боёв в партии", боёв));
        b.append(строкаЧисел("уничтожений в партии", сносов));
        b.append("\nПочему бой оказался пустым (за партию):\n\n| причина | случаев |\n|---|---:|\n");
        сортПоЗначению(причиныПустогоБоя).forEach(e -> b.append("| ").append(e.getKey())
            .append(" | ").append(округл(e.getValue() / (double) games)).append(" |\n"));
        b.append("\nКто кого бьёт (уничтожений за партию):\n\n| бил → жертва | за партию |\n|---|---:|\n");
        сортПоЗначению(ктоПоКому).limit(12).forEach(e -> b.append("| ").append(e.getKey())
            .append(" | ").append(округл(e.getValue() / (double) games)).append(" |\n"));
        b.append("\nТрофеи: лежит на месте уничтоженных жетонов к концу партии ")
            .append(округл(трофеевВзято / (double) мест)).append(" жетонов на игрока; ")
            .append("конвертаций «жетоны на место уничтоженных жетонов» ")
            .append(округл(жетоновВТрофеи / (double) games)).append(" за партию.\n");

        // ---------- 6. ПОЛЕ ----------
        b.append("\n## 6. Что стоит на поле к концу партии (на игрока)\n\n");
        b.append("| что | штук |\n|---|---:|\n");
        b.append("| военные здания и ЦУ | ").append(округл(поле[0] / мест)).append(" |\n");
        b.append("| добытчики | ").append(округл(поле[1] / мест)).append(" |\n");
        b.append("| энергостанции | ").append(округл(поле[2] / мест)).append(" |\n");
        b.append("| пехота | ").append(округл(поле[3] / мест)).append(" |\n");
        b.append("| техника | ").append(округл(поле[4] / мест)).append(" |\n");
        b.append("| авиация | ").append(округл(поле[5] / мест)).append(" |\n");
        b.append("| вышки | ").append(округл(поле[6] / мест)).append(" |\n");
        b.append("| кубиков в хранилище | ").append(округл(поле[7] / мест)).append(" |\n");

        // ---------- 7. КАРТЫ ----------
        b.append("\n## 7. Карты в деле\n\n");
        int[] задВып = партии.stream().mapToInt(Партия::заданий).toArray();
        int[] задСож = партии.stream().mapToInt(Партия::сожжено).toArray();
        b.append(строкаЧисел("заданий ВЫПОЛНЕНО в партии", задВып));
        b.append(строкаЧисел("заданий сожжено в партии", задСож));
        long вып = задВып.length == 0 ? 0 : java.util.Arrays.stream(задВып).sum();
        long сож = java.util.Arrays.stream(задСож).sum();
        b.append("\nДоля толку заданий: **").append(процент(вып, вып + сож)).append("**\n");
        b.append("\nЧаще всего ВЫПОЛНЯЮТ:\n\n| карта | раз за партию |\n|---|---:|\n");
        сортПоЗначению(выполнено).limit(10).forEach(e -> b.append("| ").append(e.getKey())
            .append(" | ").append(округл(e.getValue() / (double) games)).append(" |\n"));
        b.append("\nЧаще всего ЖГУТ:\n\n| карта | раз за партию |\n|---|---:|\n");
        сортПоЗначению(сожженоКарт).limit(10).forEach(e -> b.append("| ").append(e.getKey())
            .append(" | ").append(округл(e.getValue() / (double) games)).append(" |\n"));
        b.append("\nЧаще всего УСТАНАВЛИВАЮТ карту арсенала:\n\n| карта | раз за партию |\n|---|---:|\n");
        сортПоЗначению(установленоКарт).limit(10).forEach(e -> b.append("| ").append(e.getKey())
            .append(" | ").append(округл(e.getValue() / (double) games)).append(" |\n"));

        // ---------- 8. АНОМАЛИИ ----------
        b.append("\n## 8. Аномалии — на что смотреть\n\n");
        b.append("| что | сколько |\n|---|---:|\n");
        b.append("| партий БЕЗ ЕДИНОГО боя | ").append(партийБезБоёв)
            .append(" из ").append(games).append(" (").append(процент(партийБезБоёв, games))
            .append(") |\n");
        b.append("| игроков, не выполнивших НИ ОДНОГО задания | ").append(игроковБезЗаданий)
            .append(" из ").append(мест).append(" (").append(процент(игроковБезЗаданий, мест))
            .append(") |\n");
        b.append("| переполнений склада к концу партии | ").append(переполнений).append(" |\n");
        long короткие = партии.stream().filter(p -> p.раундов() <= 4).count();
        long длинные = партии.stream().filter(p -> p.раундов() >= 9).count();
        b.append("| партий короче пяти раундов | ").append(короткие).append(" |\n");
        b.append("| партий девять раундов и дольше | ").append(длинные).append(" |\n");
        long ничьи = партии.stream().filter(p -> p.победитель() == null).count();
        b.append("| партий без объявленного победителя | ").append(ничьи).append(" |\n");

        b.append("\n## 9. Все события движка за прогон\n\n");
        b.append("Полезно, чтобы увидеть, чего в партии не происходит ВООБЩЕ.\n\n");
        b.append("| событие | за партию |\n|---|---:|\n");
        сортПоЗначению(событий).forEach(e -> b.append("| ").append(e.getKey())
            .append(" | ").append(округл(e.getValue() / (double) games)).append(" |\n"));

        Path out = Path.of("reports", "balance",
            "отчёт-по-партиям-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println(b);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    /**
     * Источник очков по-человечески.
     *
     * <p>Словарь свой, а не общий с окном: модуль ботов не зависит от окна, и
     * тащить эту зависимость ради одной подписи — плохая цена. Строки сверены с
     * {@code Scoring.scorePlayer}: там, где ключ попадает в разбивку, здесь
     * обязана быть подпись, иначе в отчёте появится внутреннее имя.
     */
    private static String имяИсточника(String ключ) {
        return switch (ключ) {
            case "kelium" -> "келемий в хранилище";
            case "coins" -> "монеты";
            case "trophy" -> "трофеи";
            case "buildings_on_field" -> "здания на поле";
            case "units_on_field" -> "войска на поле";
            case "tech" -> "шаги науки";
            case "gold_modules" -> "золотые жетоны модулей";
            case "spawn_tiles" -> "выработанные тайлы зарождения";
            case "cu_tokens" -> "жетоны уничтожения ЦУ";
            case "war_track" -> "военный трек";
            case "super_arsenal" -> "карты супер-арсенала";
            case "super_first_part" -> "первая часть супер-задания";
            case "kills" -> "уничтожения (опыт)";
            case "level4_stars" -> "звёзды четвёртого уровня";
            case "installed_arsenal" -> "установленные карты арсенала";
            case "installed_super_arsenal" -> "установленный супер-арсенал";
            case "objective_card_vp" -> "очки прямо с карты задания";
            case "arsenal_vp" -> "очки карт-целей арсенала";
            default -> ключ;
        };
    }

    private static String имяДействия(String код) {
        return switch (код) {
            case "assembly" -> "Снаряжение (сборка)";
            case "mining" -> "Добыча";
            case "build" -> "Стройка";
            case "energy_swap" -> "Смена энергии";
            case "movement" -> "Движение";
            case "combat" -> "Бой";
            case "market" -> "Рынок";
            case "science" -> "Наука";
            default -> код;
        };
    }

    private static String строкаЧисел(String имя, int[] числа) {
        if (числа.length == 0) {
            return "";
        }
        int[] s = числа.clone();
        java.util.Arrays.sort(s);
        double сумма = 0;
        for (int v : s) {
            сумма += v;
        }
        return "* " + имя + ": среднее **" + округл(сумма / s.length)
            + "**, минимум " + s[0] + ", медиана " + s[s.length / 2]
            + ", максимум " + s[s.length - 1] + "\n";
    }

    private static String округл(double v) {
        return String.format("%.2f", v).replace(',', '.');
    }

    private static String процент(long часть, long всего) {
        return всего == 0 ? "—" : String.format("%.0f%%", 100.0 * часть / всего);
    }

    private static java.util.stream.Stream<Map.Entry<String, Long>> сортПоЗначению(
            Map<String, Long> m) {
        List<Map.Entry<String, Long>> rows = new ArrayList<>(m.entrySet());
        rows.sort(Comparator.comparingLong(Map.Entry<String, Long>::getValue).reversed());
        return rows.stream();
    }

    private static java.util.stream.Stream<Map.Entry<String, Double>> сортПоЗначениюD(
            Map<String, Double> m) {
        List<Map.Entry<String, Double>> rows = new ArrayList<>(m.entrySet());
        rows.sort(Comparator.comparingDouble(Map.Entry<String, Double>::getValue).reversed());
        return rows.stream();
    }
}
