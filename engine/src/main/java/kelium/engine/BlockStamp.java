package kelium.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Field;
import kelium.core.Hex;
import kelium.core.HexKind;

/**
 * BlockStamp — ПЕЧАТНАЯ РАЗМЕТКА БЛОКОВ на поле: контейнеры и жёлтые ячейки.
 *
 * <p>Поле в реальности всегда собирается из картонных блоков: 5 малых (по 5
 * гексов) и 5 больших (по 6), каждый двусторонний — 20 разных сторон. С
 * релиза 1.4.0 (заказ дизайнера 30.08.2026) покрытие разреженное: малый блок
 * несёт 3 напечатанных контейнера, большой — 4 (два гекса блока пустые), и
 * ровно один контейнер каждой стороны стоит в воздушной ячейке. Жёлтая ячейка
 * напечатана на ТЕХ ЖЕ гексах, что и контейнер (пустые гексы не несут ни
 * того, ни другого) — наземная и никогда не та же, что контейнерная. Набор
 * физический и неизменный: {@code <data>/blocks/blocks.1.4.0.yaml}.
 *
 * <p>Класс делает то же, что дизайнер за столом: раскладывает поле блоками,
 * поворачивает каждый блок как ляжет и переносит печать на ячейки гексов —
 * контейнер в {@link Hex#containerCell}, жёлтую ячейку в {@link Hex#energyCell}.
 *
 * <p><b>Честная оговорка про точность.</b> Разбиение поля на блоки берётся не
 * из перебора (он дорогой и живёт в конструкторе), а простой жадной нарезкой
 * по 5–6 смежных гексов: для контейнеров важно не то, КАКИЕ куски картона
 * легли, а сколько на поле контейнеров, как они распределены по ячейкам и
 * сколько из них воздушных. Эти три вещи воспроизводятся точно: на каждые
 * 5–6 гексов приходится 3–4 контейнера и ровно один воздушный — как на
 * настоящем блоке.
 */
public final class BlockStamp {

    private BlockStamp() {
    }

    /** Индекс воздушной ячейки (наземные — 0..5). */
    public static final int AIR = 6;

    /**
     * ОДИН ГЕКС СТОРОНЫ БЛОКА: где он на картонке и что на нём напечатано —
     * ячейка контейнера (−1 нет, 0..5 наземная, 6 воздушная) и ячейка жёлтая
     * (0..5; −1 только у наборов, которые её ещё не несут).
     *
     * <p>Всё вместе, а не тремя списками, нарочно: координаты и обе ячейки
     * принадлежат ОДНОМУ гексу картона и обязаны ехать вместе и при укладке, и
     * при повороте. Разъедини их — и жёлтая ячейка уедет на чужой гекс, а
     * заметить это на глаз почти нельзя: картинка останется правдоподобной.
     *
     * <p>{@code q}/{@code r} — осевые координаты гекса ВНУТРИ блока, как они
     * записаны в {@code data/blocks}: малый блок — {@code (0,0) (1,0) (2,0)
     * (0,1) (1,1)}, большой — то же плюс {@code (1,−1)}.
     */
    public record Cell(int q, int r, int container, int energy) {
    }

    /** Одна сторона блока: печать по её гексам. */
    public record Face(String blockId, String side, String kind, List<Cell> cells) {
        public int size() {
            return cells.size();
        }
    }

    /**
     * КЭШ ПО ВЕРСИИ НАБОРА. Раньше кэш был один: набор считался единственным и
     * прибит был к 1.4.0. С эталонным набором 4.0.0 версий стало две (старые
     * своды продолжают играть 1.4.0), и один кэш отдавал бы чужой картон.
     */
    private static final java.util.Map<String, List<Face>> cache =
        new java.util.HashMap<>();

    /**
     * КЭШ СБОРОК ПО ФОРМЕ ПОЛЯ. Перебор укладки — десятки миллисекунд; на одну
     * партию это ничто, а на прогоне в двенадцать тысяч партий вышли бы лишние
     * минуты. Форма поля у раскладки одна, значит и считать её надо один раз.
     */
    private static final java.util.Map<String, List<BlockAssembler.Result>> сборки =
        new java.util.HashMap<>();

    /** Сколько даём перебору на одну форму поля. */
    private static final long СРОК_СБОРКИ_МС = 4000;

    /**
     * РУЧКА УПРАВЛЕНИЯ СБОРКОЙ (заказ дизайнера 08.09.2026). Ноль — сборка
     * выбирается по зерну партии, как всё прочее случайное; 1..N — берётся
     * именно этот вариант укладки. Нужна ровно для разглядывания: собрать поле
     * теми же картонками, но иначе, и посмотреть, что вышло.
     *
     * <p>Статическое поле, а не параметр партии, нарочно: это НАСТРОЙКА ПОКАЗА,
     * а не правило. Прогоны ботов её не трогают и играют как играли.
     */
    public static int вариантСборки;

    /**
     * СКОЛЬКО РАЗНЫХ СБОРОК НАШЛОСЬ у поля этой формы (0 — перебор не сошёлся).
     * Нужно настройке партии: предлагать выбор из шести, когда вариантов два,
     * значит врать.
     */
    public static synchronized int вариантовСборки(Field field) {
        List<int[]> нужно = накрываемые(field);
        if (нужно.isEmpty()) {
            return 0;
        }
        List<BlockAssembler.Result> есть = сборки.get(формаПоля(нужно));
        return есть == null ? 0 : есть.size();
    }

    /** Набор, который читают своды БЕЗ ключа content_versions.blocks. */
    public static final String ВЕРСИЯ_ПО_УМОЛЧАНИЮ = "1.4.0";

    /**
     * Прочитать набор блоков. Файл лежит рядом с прочими данными игры
     * ({@code <data>/blocks/blocks.<версия>.yaml}); если его нет — работаем без
     * печатной разметки, а не падаем.
     */
    @SuppressWarnings("unchecked")
    public static List<Face> faces(Path dataRoot) {
        return faces(dataRoot, ВЕРСИЯ_ПО_УМОЛЧАНИЮ);
    }

    /** Тот же набор, но названной версии: {@code blocks.<версия>.yaml}. */
    @SuppressWarnings("unchecked")
    public static synchronized List<Face> faces(Path dataRoot, String версия) {
        String v = версия == null || версия.isBlank() ? ВЕРСИЯ_ПО_УМОЛЧАНИЮ : версия;
        List<Face> готово = cache.get(v);
        if (готово != null) {
            return готово;
        }
        List<Face> out = new ArrayList<>();
        Path p = dataRoot == null ? null
            : dataRoot.resolve("blocks").resolve("blocks." + v + ".yaml");
        if (p != null && Files.exists(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                Map<String, Object> doc = new org.yaml.snakeyaml.Yaml().load(in);
                for (Object bo : (List<Object>) doc.getOrDefault("blocks", List.of())) {
                    Map<String, Object> b = (Map<String, Object>) bo;
                    String id = String.valueOf(b.get("id"));
                    String kind = String.valueOf(b.get("kind"));
                    Map<String, Object> facesMap = (Map<String, Object>) b.get("faces");
                    if (facesMap == null) {
                        continue;
                    }
                    for (var e : facesMap.entrySet()) {
                        List<Cell> cells = new ArrayList<>();
                        for (Object co : (List<Object>) e.getValue()) {
                            Map<String, Object> c = (Map<String, Object>) co;
                            Object en = c.get("energy");
                            cells.add(new Cell(
                                c.get("q") instanceof Number q ? q.intValue() : 0,
                                c.get("r") instanceof Number r ? r.intValue() : 0,
                                ((Number) c.get("cell")).intValue(),
                                en instanceof Number n ? n.intValue() : -1));
                        }
                        out.add(new Face(id, String.valueOf(e.getKey()), kind, cells));
                    }
                }
            } catch (IOException | RuntimeException ex) {
                out.clear();
            }
        }
        cache.put(v, List.copyOf(out));
        return cache.get(v);
    }

    /**
     * КАРТОН НА ПОЛЕ: разложить настоящие модули и перенести с них печать.
     *
     * <p>ПЕРЕПИСАНО 08.09.2026 — раньше здесь была честно объявленная
     * приблизительность: поле нарезалось на связные кусочки по 5–6 гексов, а
     * печать блока РАСКЛАДЫВАЛАСЬ ПО ЭТИМ ГЕКСАМ ВПЕРЕМЕШКУ. Числа выходили
     * правильные (столько же контейнеров, столько же воздушных), но картонки
     * как таковой на поле не было: сказать, ЧЕМ накрыт гекс, игра не могла — а
     * значит не могла и показать настоящий модуль.
     *
     * <p>Теперь кладутся настоящие формы: малый блок — три гекса в ряд и два под
     * ними, большой — то же плюс один сверху, в любом из шести поворотов. Блок
     * ВПРАВЕ СВИСАТЬ за край поля — так и лежит на столе, лишние гексы просто
     * ни к чему не относятся (в конструкторе их закрывают чёрной накладкой).
     * Печать переносится ГЕОМЕТРИЧЕСКИ: ячейка контейнера и жёлтая ячейка
     * оказываются ровно на том гексе и на той стороне, где они напечатаны.
     *
     * <p>Из коробки берётся по одному экземпляру каждого блока (пять малых, пять
     * больших) и одна его сторона — блок не может лежать на поле дважды. Если
     * поле настолько велико, что картонок не хватает, набор берётся по второму
     * кругу: пустой гекс без печати ломал бы правила сильнее, чем повторившийся
     * рисунок.
     *
     * <p>Тайлы зарождения и запретные гексы печати не несут: картон под ними
     * есть, но ячейки закрыты, и встать на них нельзя.
     */
    public static void stamp(Field field, Path dataRoot, Random rng,
                             kelium.rules.Ruleset rules) {
        Map<Long, Hex> byQr = new LinkedHashMap<>();
        for (Hex h : field.hexes.values()) {
            h.containerCell = -1;
            h.energyCell = -1;
            h.blockId = null;
            h.blockSide = null;
            h.blockRot = 0;
            h.blockQ = 0;
            h.blockR = 0;
            int[] c = kelium.report.FieldGeometry.parseQR(h.id);
            if (c != null) {
                byQr.put(qrKey(c[0], c[1]), h);
            }
        }

        // ВЕРСИЯ НАБОРА — ИЗ СВОДА, как у всякого прочего содержимого. Своды без
        // этого ключа читают 1.4.0 и играются в точности как играли.
        String версия = rules == null ? ВЕРСИЯ_ПО_УМОЛЧАНИЮ
            : String.valueOf(rules.get("content_versions.blocks", ВЕРСИЯ_ПО_УМОЛЧАНИЮ));
        List<Face> all = faces(dataRoot, версия);
        if (all.isEmpty()) {
            // Набора блоков нет — печати не будет вовсе. Разыгрывать жёлтые
            // ячейки самим тут нельзя: они напечатаны на том же картоне, что и
            // контейнеры, и без картона их взять неоткуда.
            return;
        }

        // Накрыть надо всё, кроме запретных гексов: там картона либо нет вовсе,
        // либо он под чёрной накладкой, и печать на нём всё равно не играет.
        List<int[]> нужно = накрываемые(field);
        if (нужно.isEmpty()) {
            return;
        }

        java.util.Deque<Face> запас = раздача(all, rng);
        java.util.Set<Long> накрыто = new java.util.HashSet<>();

        // ЛУЧШАЯ СБОРКА — ТА ЖЕ, ЧТО ПОКАЗЫВАЕТ КОНСТРУКТОР: перебор укладывает
        // поле МЕНЬШИМ числом картонок, а значит и меньше печати уходит за край.
        // Жадная укладка на поле 4 игроков тратила 7–8 картонок вместо шести и
        // теряла втрое больше напечатанных ячеек.
        List<BlockAssembler.Placement> сборка = сборка(нужно, rng);
        if (сборка != null) {
            for (BlockAssembler.Placement место : сборка) {
                if (запас.isEmpty()) {
                    запас = раздача(all, rng);
                }
                уложитьНаМесто(запас, место, byQr, накрыто, rules);
            }
        }
        // Что перебор не накрыл (или не сошёлся вовсе) — добираем жадно: гекс
        // без печати ломал бы правила сильнее, чем неидеальная раскладка.
        for (int[] цель : нужно) {
            if (накрыто.contains(qrKey(цель[0], цель[1]))) {
                continue;
            }
            if (запас.isEmpty()) {
                // Картонки кончились — берём набор по второму кругу.
                запас = раздача(all, rng);
            }
            уложить(запас, цель[0], цель[1], byQr, накрыто, rules);
        }
    }

    /**
     * СБОРКА ПОЛЯ ИЗ ФИЗИЧЕСКИХ БЛОКОВ — перебором, тем же
     * {@link BlockAssembler}, каким её показывает Конструктор.
     *
     * <p>КЭШ ПО ФОРМЕ ПОЛЯ, а не по партии. Перебор занимает десятки
     * миллисекунд — на одну партию это незаметно, а на прогоне в двенадцать
     * тысяч партий вышли бы лишние минуты. Форма поля у раскладки одна, поэтому
     * решения считаются один раз и потом только выбираются: сразу берётся
     * несколько разных сборок, и партия тянет из них свою — поле не выглядит
     * одинаково собранным из игры в игру.
     *
     * @return сборка либо {@code null}, если перебор не сошёлся
     */
    private static synchronized List<BlockAssembler.Placement> сборка(List<int[]> нужно,
                                                                      Random rng) {
        String форма = формаПоля(нужно);
        List<BlockAssembler.Result> готово = сборки.get(форма);
        if (готово == null) {
            java.util.Set<BlockAssembler.Cell> playable = new java.util.LinkedHashSet<>();
            for (int[] c : нужно) {
                playable.add(new BlockAssembler.Cell(c[0], c[1]));
            }
            List<BlockAssembler.Result> found = BlockAssembler.solveVariants(
                playable, 5, 5, playable.size(), СРОК_СБОРКИ_МС, 6);
            готово = new ArrayList<>();
            for (BlockAssembler.Result r : found) {
                if (r.status() == BlockAssembler.Status.OK && !r.blocks().isEmpty()) {
                    готово.add(r);
                }
            }
            сборки.put(форма, List.copyOf(готово));
        }
        if (готово.isEmpty()) {
            return null;
        }
        // Ручка настройки бьёт зерно; нет такого варианта — играем по зерну.
        int выбор = вариантСборки >= 1 && вариантСборки <= готово.size()
            ? вариантСборки - 1 : rng.nextInt(готово.size());
        return готово.get(выбор).blocks();
    }

    /** Гексы, которые надо накрыть картоном: всё поле, кроме запретных гексов. */
    private static List<int[]> накрываемые(Field field) {
        // Порядок обхода — построчный: так картонки ложатся встык, а не
        // случайными островами, между которыми потом остаются одинокие дырки.
        List<int[]> out = new ArrayList<>();
        for (Hex h : field.hexes.values()) {
            int[] c = kelium.report.FieldGeometry.parseQR(h.id);
            if (c != null && h.kind != HexKind.FORBIDDEN) {
                out.add(c);
            }
        }
        out.sort(java.util.Comparator.<int[]>comparingInt(c -> c[1])
            .thenComparingInt(c -> c[0]));
        return out;
    }

    /** Отпечаток формы поля — ключ кэша сборок. */
    private static String формаПоля(List<int[]> нужно) {
        StringBuilder sb = new StringBuilder();
        for (int[] c : нужно) {
            sb.append(c[0]).append(':').append(c[1]).append(';');
        }
        return sb.toString();
    }

    /**
     * Положить картонку на ГОТОВОЕ место сборки: найти поворот, которым сторона
     * ложится на этот кусок, и перенести печать.
     *
     * <p>Поворот не приходит из перебора, а выводится сверкой контуров: у
     * сборщика место — просто набор клеток. Формы обоих блоков ахиральны
     * (перевёрнутая картонка ложится тем же контуром, что повёрнутая), поэтому
     * поворота достаточно — отражение искать не нужно.
     */
    private static void уложитьНаМесто(java.util.Deque<Face> запас,
                                       BlockAssembler.Placement место,
                                       Map<Long, Hex> byQr, java.util.Set<Long> накрыто,
                                       kelium.rules.Ruleset rules) {
        Face face = взятьРазмера(запас, место.size());
        if (face == null) {
            return;
        }
        String отпЦели = отпечаток(место.cells());
        for (int rot = 0; rot < 6; rot++) {
            List<int[]> свои = new ArrayList<>();
            for (Cell cell : face.cells()) {
                свои.add(rotate(cell.q(), cell.r(), rot));
            }
            if (!отпечатокQr(свои).equals(отпЦели)) {
                continue;
            }
            // Совпало контуром: ставим первую по порядку клетку блока на первую
            // по порядку клетку места — дальше всё сходится однозначно.
            int[] перваяСвоей = первая(свои);
            BlockAssembler.Cell перваяЦели = перваяКлетка(место.cells());
            int dq = перваяЦели.q() - перваяСвоей[0];
            int dr = перваяЦели.r() - перваяСвоей[1];
            печать(face, rot, dq, dr, byQr, накрыто, rules);
            return;
        }
        // Контур не совпал — такого быть не должно (сборщик работает теми же
        // двумя формами). Возвращаем картонку в запас, гекс доберёт жадный проход.
        запас.addFirst(face);
    }

    /** Взять из запаса картонку нужного размера (5 или 6 гексов). */
    private static Face взятьРазмера(java.util.Deque<Face> запас, int size) {
        for (java.util.Iterator<Face> it = запас.iterator(); it.hasNext();) {
            Face f = it.next();
            if (f.size() == size) {
                it.remove();
                return f;
            }
        }
        return запас.poll();
    }

    /** Отпечаток набора клеток: форма без привязки к месту. */
    private static String отпечаток(List<BlockAssembler.Cell> клетки) {
        List<int[]> qr = new ArrayList<>();
        for (BlockAssembler.Cell c : клетки) {
            qr.add(new int[]{c.q(), c.r()});
        }
        return отпечатокQr(qr);
    }

    private static String отпечатокQr(List<int[]> клетки) {
        List<int[]> s = new ArrayList<>(клетки);
        s.sort(java.util.Comparator.<int[]>comparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        int[] f = s.get(0);
        StringBuilder sb = new StringBuilder();
        for (int[] c : s) {
            sb.append(c[0] - f[0]).append(':').append(c[1] - f[1]).append(';');
        }
        return sb.toString();
    }

    private static int[] первая(List<int[]> клетки) {
        return клетки.stream()
            .min(java.util.Comparator.<int[]>comparingInt(c -> c[1]).thenComparingInt(c -> c[0]))
            .orElseThrow();
    }

    private static BlockAssembler.Cell перваяКлетка(List<BlockAssembler.Cell> клетки) {
        return клетки.stream()
            .min(java.util.Comparator.comparingInt(BlockAssembler.Cell::r)
                .thenComparingInt(BlockAssembler.Cell::q))
            .orElseThrow();
    }

    /**
     * РАЗДАЧА КАРТОНОК ИЗ КОРОБКИ: по одному экземпляру каждого блока, сторона
     * каждого выбирается случайно, порядок перемешан. Физически блок один, и
     * лежать на поле дважды он не может.
     */
    private static java.util.Deque<Face> раздача(List<Face> all, Random rng) {
        Map<String, List<Face>> поБлокам = new LinkedHashMap<>();
        for (Face f : all) {
            поБлокам.computeIfAbsent(f.blockId(), k -> new ArrayList<>()).add(f);
        }
        List<Face> out = new ArrayList<>();
        for (var e : поБлокам.entrySet()) {
            List<Face> свои = e.getValue();
            out.add(свои.get(rng.nextInt(свои.size())));
        }
        Collections.shuffle(out, rng);
        return new java.util.ArrayDeque<>(out);
    }

    /**
     * Положить картонку так, чтобы она накрыла гекс {@code (tq, tr)}.
     *
     * <p>Перебираются все шесть поворотов и все гексы блока в роли того, который
     * ляжет на цель; выбирается положение, накрывающее БОЛЬШЕ ещё не накрытых
     * гексов поля (и, при равенстве, меньше свисающее за край). Совсем без
     * наложений бывает не всегда: одинокая дырка в середине накрытого поля
     * иначе останется без печати вовсе, а это ломало бы правила сильнее —
     * поэтому в последнюю очередь разрешается лечь поверх уже накрытого, и
     * тогда верхняя картонка перебивает нижнюю (как оно и лежит на столе).
     */
    private static void уложить(java.util.Deque<Face> запас, int tq, int tr,
                                Map<Long, Hex> byQr, java.util.Set<Long> накрыто,
                                kelium.rules.Ruleset rules) {
        Face face = запас.poll();
        if (face == null) {
            return;
        }
        int лучшийRot = 0;
        int лучшийИндекс = 0;
        long лучшаяОценка = Long.MIN_VALUE;
        for (int rot = 0; rot < 6; rot++) {
            for (int pivot = 0; pivot < face.cells().size(); pivot++) {
                int[] p = rotate(face.cells().get(pivot).q(), face.cells().get(pivot).r(), rot);
                int aq = tq - p[0];
                int ar = tr - p[1];
                int новых = 0;
                int свисает = 0;
                int поверх = 0;
                for (Cell cell : face.cells()) {
                    int[] d = rotate(cell.q(), cell.r(), rot);
                    long k = qrKey(aq + d[0], ar + d[1]);
                    if (!byQr.containsKey(k)) {
                        свисает++;
                    } else if (накрыто.contains(k)) {
                        поверх++;
                    } else {
                        новых++;
                    }
                }
                // Наложение на чужой картон — самое дорогое; потом свисание.
                long оценка = -1000L * поверх + 10L * новых - свисает;
                if (оценка > лучшаяОценка) {
                    лучшаяОценка = оценка;
                    лучшийRot = rot;
                    лучшийИндекс = pivot;
                }
            }
        }
        int[] p = rotate(face.cells().get(лучшийИндекс).q(),
            face.cells().get(лучшийИндекс).r(), лучшийRot);
        печать(face, лучшийRot, tq - p[0], tr - p[1], byQr, накрыто, rules);
    }

    /**
     * ПЕРЕНЕСТИ ПЕЧАТЬ КАРТОНКИ НА ГЕКСЫ. Блок лежит повёрнутым на {@code rot}
     * шагов, его гекс (0,0) — на клетке {@code (dq, dr)} поля.
     *
     * <p>Геометрически, без всякого перемешивания: ячейка контейнера и жёлтая
     * ячейка встают ровно на тот гекс и на ту сторону, где они напечатаны.
     * Гекс блока, свисающий за край поля, просто теряется — как и на столе.
     */
    private static void печать(Face face, int rot, int dq, int dr,
                               Map<Long, Hex> byQr, java.util.Set<Long> накрыто,
                               kelium.rules.Ruleset rules) {
        // ПОТОЛОК ПРАВИЛА (правило-вариант containers.printed_per_small_block /
        // printed_per_big_block, запрос дизайнера 13.08.2026): срезать контейнеры
        // блока сверх заданного числа. С наборами 1.4.0 и 5.* потолки по
        // умолчанию 4/5 ничего не режут — они остались для вариантов правил,
        // которым нужно ЕЩЁ реже.
        int limit = rules == null ? 99 : "small".equals(face.kind())
            ? ((Number) rules.get("containers.printed_per_small_block", 4)).intValue()
            : ((Number) rules.get("containers.printed_per_big_block", 5)).intValue();
        int поставлено = 0;
        for (Cell cell : face.cells()) {
            int[] d = rotate(cell.q(), cell.r(), rot);
            long k = qrKey(dq + d[0], dr + d[1]);
            Hex h = byQr.get(k);
            if (h == null) {
                continue;                 // этот гекс блока свисает за край поля
            }
            накрыто.add(k);
            h.blockId = face.blockId();
            h.blockSide = face.side();
            h.blockRot = rot;
            h.blockQ = cell.q();
            h.blockR = cell.r();
            if (h.kind == HexKind.FORBIDDEN || h.spawnTile != null) {
                continue;                 // картон есть, а ячейки закрыты
            }
            int контейнер = cell.container();
            if (контейнер >= 0) {
                if (поставлено >= limit) {
                    контейнер = -1;
                } else {
                    поставлено++;
                }
            }
            h.containerCell = контейнер < 0 ? -1 : rotateCell(контейнер, rot);
            h.energyCell = cell.energy() < 0 ? -1 : rotateCell(cell.energy(), rot);
        }
    }

    /** Ключ гекса по осевым координатам. */
    private static long qrKey(int q, int r) {
        return (((long) q) << 32) ^ (r & 0xffffffffL);
    }

    /** Поворот осевых координат на {@code steps} шагов по 60° ПО ЧАСОВОЙ. */
    public static int[] rotate(int q, int r, int steps) {
        int qq = q;
        int rr = r;
        for (int i = Math.floorMod(steps, 6); i > 0; i--) {
            int nq = -rr;
            int nr = qq + rr;
            qq = nq;
            rr = nr;
        }
        return new int[]{qq, rr};
    }

    /**
     * ПОВОРОТ НОМЕРА ЯЧЕЙКИ вместе с картонкой.
     *
     * <p>Нумерация сторон гекса ({@link Field#AXIAL_DIRS}) идёт ПРОТИВ часовой,
     * поэтому поворот картонки по часовой сдвигает номер стороны НАЗАД. Вывод
     * тот же, что в конструкторе (см. {@code ПривязкаБлоков}); воздушная ячейка
     * ({@link #AIR}) стороне не принадлежит и не поворачивается вовсе.
     */
    public static int rotateCell(int cell, int steps) {
        if (cell < 0 || cell == AIR) {
            return cell;
        }
        return (cell + 6 - Math.floorMod(steps, 6)) % 6;
    }

    /** Сбросить кэш набора блоков (для тестов). */
    public static synchronized void resetCache() {
        cache.clear();
        сборки.clear();
    }

    /** Сводка по полю: сколько печатных контейнеров и сколько из них воздушных. */
    public static Map<String, Integer> summary(Field field) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int total = 0;
        int air = 0;
        for (Hex h : field.hexes.values()) {
            if (h.containerCell >= 0) {
                total++;
                if (h.containerCell == AIR) {
                    air++;
                }
            }
        }
        out.put("printed", total);
        out.put("air", air);
        return out;
    }
}
