package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LayoutMaker — генератор РАСКЛАДОК ПОД МАКСИМАЛЬНУЮ АГРЕССИЮ.
 *
 * <p>Почему генератор, а не рисование руками. Требование «поле должно быть
 * симметрично справедливым — у всех игроков одинаковые расстояния и одинаковая
 * свобода» глазомером не проверяется: на гексовой сетке легко сделать поле, где
 * у одного старта пять соседей, а у другого три. Здесь поле СТРОИТСЯ из
 * фундаментальной области действием группы симметрии, поэтому равенство игроков
 * гарантировано построением, а не аккуратностью. Плюс программа сама проверяет
 * итог и печатает отчёт: расстояния между стартами, число соседей у каждого,
 * тайлы и нейтралы на игрока.
 *
 * <p><b>На чём основан замысел «агрессивного» поля</b> — на замерах, а не на
 * вкусе (см. {@code reports/balance/лига-раскладок-*.md}):
 * <ul>
 *   <li><b>Длина партии — главный рычаг.</b> Партия обрывается, когда остаётся
 *       последний источник келемия, поэтому число тайлов на игрока прямо задаёт,
 *       сколько раундов идёт игра. На четырёх авторских полях на 3 игроков связь
 *       вышла монотонной: 1 тайл на игрока → 3.2 раунда и почти нет боёв;
 *       2 тайла → 6.9 раунда и в пять раз больше уничтожений. Поэтому здесь
 *       <b>3 тайла на игрока</b>.</li>
 *   <li><b>Старты на минимальном допустимом расстоянии</b> (3 — то есть два гекса
 *       между базами): армиям надо меньше ходов до соприкосновения.</li>
 *   <li><b>Центр открыт.</b> Тайлы зарождения перекрывают гекс целиком, поэтому
 *       тайл в центре превратил бы серединe поля в стену. Тайлы стоят В СТОРОНЕ от
 *       путей между базами, а середина оставлена под бой.</li>
 *   <li><b>Нейтралы не между базами.</b> Стенка нейтрала закрывает проход, то есть
 *       прикрывает игрока от соседа. Нейтралы вынесены за спины баз: они дают цель
 *       для сноса, но не мешают соприкосновению.</li>
 *   <li><b>Часть тайлов — спорные:</b> стоят на равном расстоянии от двух баз, так
 *       что за место под добытчик у них приходится бороться.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.LayoutMaker <папка>}.
 */
public final class LayoutMaker {

    private LayoutMaker() {
    }

    /** Гекс в осевых координатах. */
    private record Hx(int q, int r) {
        @Override
        public String toString() {
            return q + "," + r;
        }
    }

    // ==================== геометрия ====================

    /** Шесть направлений — те же, что в движке ({@code Field.AXIAL_DIRS}). */
    private static final int[][] DIRS = {
        {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}
    };

    private static int dist(Hx a, Hx b) {
        int dq = b.q() - a.q();
        int dr = b.r() - a.r();
        return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
    }

    private static int ring(Hx h) {
        return Math.max(Math.abs(h.q()), Math.max(Math.abs(h.r()), Math.abs(h.q() + h.r())));
    }

    private static List<Hx> neighbours(Hx h) {
        List<Hx> out = new ArrayList<>();
        for (int[] d : DIRS) {
            out.add(new Hx(h.q() + d[0], h.r() + d[1]));
        }
        return out;
    }

    /** Поворот на 120° вокруг центра: (q,r) → (−q−r, q). */
    private static Hx rot120(Hx h) {
        return new Hx(-h.q() - h.r(), h.q());
    }

    /** Поворот на 180°: (q,r) → (−q,−r). */
    private static Hx rot180(Hx h) {
        return new Hx(-h.q(), -h.r());
    }

    /** Отражение: (q,r) → (r,q). Для этих направлений это настоящая симметрия. */
    private static Hx mirror(Hx h) {
        return new Hx(h.r(), h.q());
    }

    /** Все образы гекса под группой симметрии данного состава. */
    private static List<Hx> orbit(Hx h, int players) {
        List<Hx> out = new ArrayList<>();
        switch (players) {
            case 2 -> {
                out.add(h);
                out.add(mirror(h));
            }
            case 3 -> {
                Hx a = h;
                for (int i = 0; i < 3; i++) {
                    out.add(a);
                    a = rot120(a);
                }
            }
            default -> {
                out.add(h);
                out.add(rot180(h));
            }
        }
        LinkedHashSet<Hx> uniq = new LinkedHashSet<>(out);
        return new ArrayList<>(uniq);
    }

    private static void addOrbit(Set<Hx> into, Hx h, int players) {
        into.addAll(orbit(h, players));
    }

    // ==================== описание одной раскладки ====================

    /** Собранная раскладка: гексы, старты, тайлы, нейтралы. */
    private static final class Layout {
        final int players;
        final Set<Hx> hexes = new LinkedHashSet<>();
        final List<Hx> starts = new ArrayList<>();
        final Set<Hx> tiles = new LinkedHashSet<>();
        final Set<Hx> startTiles = new LinkedHashSet<>();
        final Set<Hx> neutrals = new LinkedHashSet<>();

        Layout(int players) {
            this.players = players;
        }
    }

    /**
     * ДВОЕ. Основа — шестиугольник радиуса 2 (19 гексов, 9.5 на игрока). Симметрия
     * — отражение, при котором расстояние между стартами получается НЕЧЁТНЫМ:
     * поворот на 180° давал бы только чётные (4 и больше), а нам нужно ровно 3 —
     * минимально допустимое.
     */
    private static Layout duel() {
        Layout l = new Layout(2);
        for (int q = -2; q <= 2; q++) {
            for (int r = -2; r <= 2; r++) {
                Hx h = new Hx(q, r);
                if (ring(h) <= 2) {
                    l.hexes.add(h);
                }
            }
        }
        Hx s0 = new Hx(2, -1);
        l.starts.add(s0);
        l.starts.add(mirror(s0));               // (-1,2), расстояние ровно 3

        addOrbit(l.startTiles, new Hx(2, -2), 2);   // свой тайл у каждой базы
        addOrbit(l.tiles, new Hx(2, 0), 2);         // второй свой
        // Спорные тайлы — на оси симметрии, ровно между базами.
        l.tiles.add(new Hx(1, 1));
        l.tiles.add(new Hx(-1, -1));
        addOrbit(l.neutrals, new Hx(1, -2), 2);     // за спиной, не между базами
        return l;
    }

    /**
     * ТРОЕ. Поворот на 120°. Основа — шестиугольник радиуса 2 плюс «карман» из трёх
     * гексов за каждой базой (28 гексов, 9.3 на игрока). Старты на кольце 2 дают
     * все три расстояния ровно по 3.
     */
    private static Layout trio() {
        Layout l = new Layout(3);
        for (int q = -3; q <= 3; q++) {
            for (int r = -3; r <= 3; r++) {
                Hx h = new Hx(q, r);
                if (ring(h) <= 2) {
                    l.hexes.add(h);
                }
            }
        }
        // карманы за базами: три гекса кольца 3 на каждого
        addOrbit(l.hexes, new Hx(3, -1), 3);
        addOrbit(l.hexes, new Hx(3, -2), 3);
        addOrbit(l.hexes, new Hx(3, -3), 3);

        Hx s0 = new Hx(2, -1);
        for (Hx h : orbit(s0, 3)) {
            l.starts.add(h);
        }
        addOrbit(l.startTiles, new Hx(2, -2), 3);   // свой у базы
        addOrbit(l.tiles, new Hx(3, -1), 3);        // свой в кармане
        addOrbit(l.tiles, new Hx(1, 1), 3);         // СПОРНЫЙ: равноудалён от двух баз
        addOrbit(l.neutrals, new Hx(3, -3), 3);     // в глубине карманов
        return l;
    }

    /**
     * ЧЕТВЕРО. На гексовой сетке поворота на 90° не существует, поэтому равенство
     * достигается иначе: четыре базы стоят на кольце 2 через три шага по кольцу,
     * набор замкнут поворотом на 180°, и у КАЖДОЙ базы получается одинаковый набор
     * расстояний до остальных — 3, 3 и 4. Плюс за каждой базой достраиваются ВСЕ её
     * внешние соседи, чтобы у всех четырёх было ровно по 6 соседей: иначе у баз в
     * «углах» кольца свободы меньше, чем у баз на «рёбрах».
     */
    private static Layout quartet() {
        Layout l = new Layout(4);
        for (int q = -3; q <= 3; q++) {
            for (int r = -3; r <= 3; r++) {
                Hx h = new Hx(q, r);
                if (ring(h) <= 2) {
                    l.hexes.add(h);
                }
            }
        }
        List<Hx> starts = List.of(new Hx(2, 0), new Hx(1, -2),
            new Hx(-2, 0), new Hx(-1, 2));
        l.starts.addAll(starts);
        // выровнять свободу: добавить всем стартам их внешних соседей
        for (Hx s : starts) {
            for (Hx nb : neighbours(s)) {
                if (ring(nb) == 3) {
                    l.hexes.add(nb);
                }
            }
        }
        // ПО ДВА СВОИХ ТАЙЛА КАЖДОЙ БАЗЕ — в её кармане. Раздаём поштучно каждому
        // старту, а не орбитой: при повороте на 180° орбита состоит из ДВУХ гексов,
        // и тайлы достались бы только двум игрокам из четырёх.
        for (Hx s : starts) {
            int given = 0;
            for (Hx nb : neighbours(s)) {
                if (given >= 2) {
                    break;
                }
                if (ring(nb) == 3 && l.hexes.contains(nb)) {
                    if (given == 0) {
                        l.startTiles.add(nb);   // стартовый тайл (беднее)
                    } else {
                        l.tiles.add(nb);        // второй свой (богаче)
                    }
                    given++;
                }
            }
        }
        addOrbit(l.tiles, new Hx(2, -2), 4);        // спорный между двумя базами
        addOrbit(l.tiles, new Hx(1, 1), 4);         // спорный между другими двумя
        addOrbit(l.neutrals, new Hx(0, -2), 4);     // в стороне, не между базами
        addOrbit(l.neutrals, new Hx(2, 1), 4);
        return l;
    }

    // ==================== проверка справедливости ====================

    /** Проверить равенство игроков и напечатать отчёт. Вернуть найденные беды. */
    private static List<String> verify(Layout l, StringBuilder log) {
        List<String> problems = new ArrayList<>();
        log.append(String.format(Locale.ROOT,
            "%n=== %d игрока: гексов %d (%.1f на игрока), тайлов %d (%.1f), нейтралов %d%n",
            l.players, l.hexes.size(), l.hexes.size() / (double) l.players,
            l.tiles.size() + l.startTiles.size(),
            (l.tiles.size() + l.startTiles.size()) / (double) l.players,
            l.neutrals.size()));

        if (l.starts.size() != l.players) {
            problems.add("стартов " + l.starts.size() + " вместо " + l.players);
        }
        // 1. РАССТОЯНИЯ: у каждой базы должен быть ОДИНАКОВЫЙ набор расстояний.
        List<String> profiles = new ArrayList<>();
        for (Hx a : l.starts) {
            List<Integer> ds = new ArrayList<>();
            for (Hx b : l.starts) {
                if (!a.equals(b)) {
                    ds.add(dist(a, b));
                }
            }
            ds.sort(Comparator.naturalOrder());
            profiles.add(ds.toString());
            log.append("  база ").append(a).append(": расстояния ").append(ds);
            int nb = 0;
            for (Hx n : neighbours(a)) {
                if (l.hexes.contains(n)) {
                    nb++;
                }
            }
            log.append(", соседей ").append(nb);
            boolean ownTile = false;
            for (Hx n : neighbours(a)) {
                if (l.startTiles.contains(n) || l.tiles.contains(n)) {
                    ownTile = true;
                }
            }
            log.append(ownTile ? ", свой тайл рядом есть" : ", СВОЕГО ТАЙЛА РЯДОМ НЕТ")
               .append('\n');
            if (!ownTile) {
                problems.add("у базы " + a + " нет примыкающего тайла");
            }
            if (nb < 3) {
                problems.add("у базы " + a + " меньше трёх соседей");
            }
        }
        for (String p : profiles) {
            if (!p.equals(profiles.get(0))) {
                problems.add("расстояния между базами НЕ одинаковы: " + profiles);
                break;
            }
        }
        // 2. СВОБОДА: одинаковое число соседей у всех баз.
        Set<Integer> degrees = new LinkedHashSet<>();
        for (Hx a : l.starts) {
            int nb = 0;
            for (Hx n : neighbours(a)) {
                if (l.hexes.contains(n)) {
                    nb++;
                }
            }
            degrees.add(nb);
        }
        if (degrees.size() > 1) {
            problems.add("у баз РАЗНОЕ число соседей: " + degrees);
        }
        // 3. Тайл не должен стоять на базе, нейтрал — тоже.
        for (Hx s : l.starts) {
            if (l.tiles.contains(s) || l.startTiles.contains(s) || l.neutrals.contains(s)) {
                problems.add("на базе " + s + " стоит тайл или нейтрал");
            }
        }
        // 4. Все объекты — внутри поля.
        for (Hx h : l.tiles) {
            if (!l.hexes.contains(h)) {
                problems.add("тайл " + h + " вне поля");
            }
        }
        for (Hx h : l.startTiles) {
            if (!l.hexes.contains(h)) {
                problems.add("стартовый тайл " + h + " вне поля");
            }
        }
        for (Hx h : l.neutrals) {
            if (!l.hexes.contains(h)) {
                problems.add("нейтрал " + h + " вне поля");
            }
        }
        // 5. Поле связно (иначе игроки не встретятся вовсе).
        if (!connected(l)) {
            problems.add("поле НЕ СВЯЗНО");
        }
        for (String p : problems) {
            log.append("  ПРОБЛЕМА: ").append(p).append('\n');
        }
        if (problems.isEmpty()) {
            log.append("  проверки пройдены: игроки равны\n");
        }
        return problems;
    }

    private static boolean connected(Layout l) {
        if (l.hexes.isEmpty()) {
            return false;
        }
        Set<Hx> seen = new LinkedHashSet<>();
        java.util.Deque<Hx> q = new java.util.ArrayDeque<>();
        Hx first = l.hexes.iterator().next();
        seen.add(first);
        q.add(first);
        while (!q.isEmpty()) {
            Hx cur = q.poll();
            for (Hx nb : neighbours(cur)) {
                if (l.hexes.contains(nb) && seen.add(nb)) {
                    q.add(nb);
                }
            }
        }
        return seen.size() == l.hexes.size();
    }

    // ==================== запись в YAML ====================

    private static String toYaml(Layout l, String id) {
        StringBuilder sb = new StringBuilder();
        sb.append("version: editor\n");
        sb.append("scenarios:\n");
        sb.append("- id: ").append(id).append('\n');
        sb.append("  players: ").append(l.players).append('\n');
        sb.append("  _made_with: LayoutMaker (генератор симметричных полей)\n");
        sb.append("  hexes:\n");
        Map<Hx, Integer> seatOf = new LinkedHashMap<>();
        for (int i = 0; i < l.starts.size(); i++) {
            seatOf.put(l.starts.get(i), i);
        }
        // порядок вывода стабильный: по q, потом по r
        List<Hx> ordered = new ArrayList<>(l.hexes);
        ordered.sort(Comparator.comparingInt(Hx::q).thenComparingInt(Hx::r));
        for (Hx h : ordered) {
            sb.append("  - q: ").append(h.q()).append('\n');
            sb.append("    r: ").append(h.r()).append('\n');
            if (seatOf.containsKey(h)) {
                sb.append("    content: player_start\n");
                sb.append("    seat: ").append(seatOf.get(h)).append('\n');
            } else if (l.startTiles.contains(h)) {
                // spawn_start — код СТАРТОВОГО тайла зарождения (3/2 келемия);
                // обычный kelium_tile богаче (4/3). Названия — из загрузчика
                // сценариев, придумывать свои нельзя.
                sb.append("    content: spawn_start\n");
            } else if (l.tiles.contains(h)) {
                sb.append("    content: kelium_tile\n");
            } else if (l.neutrals.contains(h)) {
                sb.append("    neutral_list:\n");
                sb.append("    - size: small\n");
                sb.append("      corners:\n");
                sb.append("      - 2\n");
                sb.append("      - 3\n");
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        Path dir = args.length > 0 ? Path.of(args[0])
            : kelium.dataio.GameConfig.resolveDataRoot(null)
                .resolve("scenarios").resolve("new");
        Files.createDirectories(dir);

        StringBuilder log = new StringBuilder();
        List<Layout> layouts = List.of(duel(), trio(), quartet());
        boolean allOk = true;
        for (Layout l : layouts) {
            List<String> problems = verify(l, log);
            allOk &= problems.isEmpty();
            String id = "агрессивное " + l.players + " игрока-looksmaxxing";
            Path file = dir.resolve(id + ".yaml");
            Files.writeString(file, toYaml(l, id), StandardCharsets.UTF_8);
            log.append("  записано: ").append(file.getFileName()).append('\n');
        }
        System.out.print(log);
        System.out.println(allOk
            ? "\nВСЕ ПОЛЯ ПРОШЛИ ПРОВЕРКУ СПРАВЕДЛИВОСТИ."
            : "\nЕСТЬ ПРОБЛЕМЫ — смотри выше.");
    }
}
