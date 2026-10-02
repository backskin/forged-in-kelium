package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * СБОРКА НАБОРА ИЗ ИТОГОВ БУЛЬОНА (Карты 2.0, 30.09.2026).
 *
 * <p>Берёт кандидатов бульона ({@link Бульон}) и их меру, снятую живыми
 * партиями стратегов ({@link ПрогонБульона}, файл {@code прогон.md}), и
 * собирает колоду заданий и арсенала. Правила сборки заданы ДО того, как
 * известны итоги, — числа годности не подгоняются под кандидатов:
 *
 * <ul>
 *   <li><b>годное задание</b> — было в руке не меньше {@link #МИН_В_РУКЕ} раз и
 *       выполняется в доле от {@link #ДОЛЯ_МИН} до {@link #ДОЛЯ_МАКС}: не
 *       даром и не мёртвое;</li>
 *   <li><b>награда — по трудности</b>: трудность = 1 − доля выполнения;
 *       лёгкое — ветка на выбор, среднее — ветка и ресурс, трудное — ветка и
 *       спец-действие (цепочка!);</li>
 *   <li><b>поровну по развилкам</b>: каждой развилке — одинаковое число карт
 *       (развилка карты — развилка действия, которым её закрывают);</li>
 *   <li><b>связки</b>: пары, выполненные в одном ходу чаще других, идут в набор
 *       вместе, если обе годны;</li>
 *   <li><b>арсенал</b>: срабатывания, которые срабатывали, — поровну по
 *       развилкам события; спец-действие и карта — не больше
 *       {@link #РЕДКИХ} карт на колоду: комбинации возможны, но не каждый ход.</li>
 * </ul>
 */
public final class СборкаНабора {

    private СборкаНабора() {
    }

    /** Пробный режим (-Dkelium.набор.проба=true): пороги сняты — только проверка пути. */
    static final boolean ПРОБА = Boolean.getBoolean("kelium.набор.проба");
    /** Карт прокачки «каждый ход — ещё одно спец-действие» в арсенале. */
    static final int ПРОКАЧКА = Integer.getInteger("kelium.набор.прокачка", 4);
    /** Задания Командования дают трофей в основной награде (опыт 01.10.2026). */
    static final boolean ВОЙНА_ТРОФЕЕМ = !"false".equals(System.getProperty("kelium.набор.трофей", "true"));
    /** Трудные задания дают карту арсенала в основной награде (опыт 01.10.2026). */
    static final boolean СВЯЗОЧНЫЙ_АРСЕНАЛ = !"false".equals(System.getProperty("kelium.набор.арсенал", "false"));
    static final int МИН_В_РУКЕ = ПРОБА ? 1 : 6;
    static final double ДОЛЯ_МИН = ПРОБА ? 0.0 : 0.08;
    // ПОТОЛОК 0,6 (01.10.2026): «Имей 4 здания» выполняли в 78% раздач — это не
    // выбор, а подарок тому, кто и так строится; Снабжение брало 34% побед
    static final double ДОЛЯ_МАКС = ПРОБА ? 1.0 : 0.6;
    // ПОРОВНУ НА РАЗВИЛКУ (01.10.2026): не больше 6 заданий на развилку. При 8 у
    // Командования выходило 9 карт, у Добычи и Питания по 4 — и «дорога победителя»
    // тянулась к развилке с большей колодой без всякой её силы.
    // РАЗМЕРЫ КОЛОД (решение Влада 02.10.2026): заданий 50, арсенала 40 — из
    // них 36 из бульона и 4 карты прокачки; начальных заданий 12, начального
    // арсенала 8 (как есть).
    static final int ЗАДАНИЙ = Integer.getInteger("kelium.набор.заданий", 50);
    static final int АРСЕНАЛА = Integer.getInteger("kelium.набор.арсенала", 36);
    /** Версии выгрузки: задания, арсенал, свод — и свод, от которого он сделан. */
    static final String ВЕРСИЯ_ЗАДАНИЙ = System.getProperty("kelium.набор.версия_заданий", "3.0.0");
    static final String ВЕРСИЯ_АРСЕНАЛА = System.getProperty("kelium.набор.версия_арсенала", "9.0.0");
    static final String СВОД = System.getProperty("kelium.набор.свод", "1.50.0");
    static final String СВОД_ОСНОВА = System.getProperty("kelium.набор.основа", "1.49.0");
    /** Сколько карт заданий получает свободный утиль «∞ +1 спец-действие». */
    static final int СВОБОДНЫХ_СПЕЦ = Integer.getInteger("kelium.набор.свободных_спец", 3);
    static final int РЕДКИХ = 6;

    record Мера(String id, String текст, int вРуке, int выполнено, double доля, double раунд,
                double отрывВыполнил, double отрывНе) {
    }

    public static void main(String[] args) throws Exception {
        Path папка = Path.of(args.length > 0 ? args[0] : "design-docs/фигуры/бульон");
        org.yaml.snakeyaml.LoaderOptions lo = new org.yaml.snakeyaml.LoaderOptions();
        lo.setMaxAliasesForCollections(Integer.MAX_VALUE);
        org.yaml.snakeyaml.Yaml y = new org.yaml.snakeyaml.Yaml(lo);
        List<Map<String, Object>> задания = y.load(Files.readString(папка.resolve("задания.yaml")));
        List<Map<String, Object>> арсенал = y.load(Files.readString(папка.resolve("арсенал.yaml")));
        Map<String, Map<String, Object>> заданиеПоId = new HashMap<>();
        for (Map<String, Object> з : задания) {
            заданиеПоId.put(String.valueOf(з.get("id")), з);
        }
        Map<String, Map<String, Object>> арсеналПоId = new HashMap<>();
        for (Map<String, Object> а : арсенал) {
            арсеналПоId.put(String.valueOf(а.get("id")), а);
        }
        // ВСЕ ФАЙЛЫ ПРОГОНОВ (прогон.md, прогон2.md…) складываются: суммы
        // восстанавливаются из средних и числа выполнений.
        Map<String, double[]> суммы = new LinkedHashMap<>();   // вРуке, выполнено, раунд, отрывВ, отрывН
        Map<String, String> тексты = new HashMap<>();
        Map<String, int[]> срабатывания = new HashMap<>();
        Map<String, Integer> пары = new HashMap<>();
        List<Path> файлы = new ArrayList<>();
        try (var поток = Files.list(папка)) {
            поток.filter(ф -> ф.getFileName().toString().startsWith("прогон")
                && ф.getFileName().toString().endsWith(".md")).sorted().forEach(файлы::add);
        }
        for (Path ф : файлы) {
            int раздел = 0;
            for (String с : Files.readAllLines(ф, StandardCharsets.UTF_8)) {
                if (с.startsWith("# Прогон бульона — задания")) {
                    раздел = 1;
                } else if (с.startsWith("# Прогон бульона — арсенал")) {
                    раздел = 2;
                } else if (с.startsWith("# Пары")) {
                    раздел = 3;
                }
                if (!с.startsWith("| ") || с.startsWith("| id") || с.startsWith("| пара")) {
                    continue;
                }
                String[] ч = с.split("\\|");
                if (раздел == 1 && ч.length >= 9) {
                    String id = ч[1].trim();
                    int вРуке = целое(ч[3]);
                    int выполнено = целое(ч[4]);
                    double[] s = суммы.computeIfAbsent(id, k -> new double[5]);
                    s[0] += вРуке;
                    s[1] += выполнено;
                    s[2] += дробь(ч[6]) * выполнено;
                    s[3] += дробь(ч[7]) * выполнено;
                    s[4] += дробь(ч[8]) * (вРуке - выполнено);
                    тексты.put(id, ч[2].trim());
                } else if (раздел == 2 && ч.length >= 5) {
                    int[] x = срабатывания.computeIfAbsent(ч[1].trim(), k -> new int[2]);
                    x[0] += целое(ч[3]);
                    x[1] += целое(ч[4]);
                } else if (раздел == 3 && ч.length >= 3) {
                    пары.merge(ч[1].trim(), целое(ч[2]), Integer::sum);
                }
            }
        }
        List<Мера> меры = new ArrayList<>();
        for (var e : суммы.entrySet()) {
            double[] s = e.getValue();
            меры.add(new Мера(e.getKey(), тексты.get(e.getKey()), (int) s[0], (int) s[1],
                s[0] == 0 ? 0 : s[1] / s[0], s[1] == 0 ? 0 : s[2] / s[1], s[1] == 0 ? 0 : s[3] / s[1],
                s[0] == s[1] ? 0 : s[4] / (s[0] - s[1])));
        }
        System.out.println("файлов прогона: " + файлы.size());

        // ---------------- задания ----------------
        Map<String, List<Мера>> поРазвилке = new LinkedHashMap<>();
        for (String р : List.of("extract", "power", "supply", "command", "develop")) {
            поРазвилке.put(р, new ArrayList<>());
        }
        List<Мера> годные = new ArrayList<>();
        for (Мера м : меры) {
            boolean связочное = СВЯЗОЧНЫЕ.contains(узел(заданиеПоId.get(м.id())));
            // СВЯЗОЧНЫЕ задания («выполни вторым», «третьим спец-действием», «обе
            // ветки») прогон мерил с ровной наградой без спец-действий — без них
            // второе задание за ход почти невыполнимо. В наборе награды дают 2–3
            // спец-действия, поэтому их порог ниже: лишь бы вообще выполнялись.
            double мин = связочное ? Math.min(ДОЛЯ_МИН, 0.02) : ДОЛЯ_МИН;
            if (м.вРуке() >= МИН_В_РУКЕ && м.доля() >= мин && м.доля() <= ДОЛЯ_МАКС
                    && !однимДействием(заданиеПоId.get(м.id()))) {
                годные.add(м);
                String р = развилкаТребования(заданиеПоId.get(м.id()));
                поРазвилке.getOrDefault(р, поРазвилке.get("command")).add(м);
            }
        }
        List<Мера> набор = new ArrayList<>();
        int наРазвилку = ЗАДАНИЙ / поРазвилке.size();
        for (var e : поРазвилке.entrySet()) {
            // ОДНА КАРТА НА СЕМЕЙСТВО (вид требования + кто): «2 / 3 / 4 своих
            // здания» — одно и то же задание с разным числом. Из семейства берём
            // вариант средней трудности (доля ближе к трети); семейства — по
            // очереди видов узлов, чтобы в развилке были разные задачи.
            Map<String, Мера> лучшее = new LinkedHashMap<>();
            for (Мера м : e.getValue()) {
                String семья = семейство(заданиеПоId.get(м.id()));
                Мера был = лучшее.get(семья);
                if (был == null || Math.abs(м.доля() - 0.33) < Math.abs(был.доля() - 0.33)) {
                    лучшее.put(семья, м);
                }
            }
            Map<String, List<Мера>> поУзлу = new LinkedHashMap<>();
            for (Мера м : лучшее.values()) {
                поУзлу.computeIfAbsent(узел(заданиеПоId.get(м.id())), k -> new ArrayList<>()).add(м);
            }
            for (List<Мера> л : поУзлу.values()) {
                л.sort(Comparator.comparingDouble(м -> Math.abs(м.доля() - 0.33)));
            }
            int взято = 0;
            for (int круг = 0; взято < наРазвилку && круг < 10; круг++) {
                for (List<Мера> л : поУзлу.values()) {
                    if (круг < л.size() && взято < наРазвилку && кластерСвободен(л.get(круг), набор, заданиеПоId)) {
                        набор.add(л.get(круг));
                        взято++;
                    }
                }
            }
            // МЕСТ ОСТАЛОСЬ — ДОБОР ВТОРЫМ ВАРИАНТОМ СЕМЕЙСТВА (02.10.2026). Колода
            // заданий теперь 50 (решение Влада), и одна карта на семейство её не
            // наполняет. Второй вариант берётся, только если он ЗАМЕТНО другой по
            // трудности (доля выполнения отличается на 0,15 и больше): лёгкая и
            // трудная ступени одной задачи — разные решения за столом.
            if (взято < наРазвилку) {
                List<Мера> запас = new ArrayList<>(e.getValue());
                запас.removeIf(набор::contains);
                запас.sort(Comparator.comparingDouble(м -> Math.abs(м.доля() - 0.33)));
                for (Мера м : запас) {
                    if (взято >= наРазвилку) {
                        break;
                    }
                    String семья = семейство(заданиеПоId.get(м.id()));
                    boolean далеко = true;
                    for (Мера х : набор) {
                        if (семья.equals(семейство(заданиеПоId.get(х.id())))
                                && Math.abs(х.доля() - м.доля()) < 0.15) {
                            далеко = false;
                        }
                    }
                    if (далеко && кластерСвободен(м, набор, заданиеПоId)
                            && !двойник(м, набор, заданиеПоId)) {
                        набор.add(м);
                        взято++;
                    }
                }
            }
        }
        java.util.Set<String> семьиНабора = new java.util.HashSet<>();
        for (Мера м : набор) {
            семьиНабора.add(семейство(заданиеПоId.get(м.id())));
        }
        // связки: пары, выполненные в одном ходу, — обе карты в набор, если годны
        List<String> связки = new ArrayList<>();
        пары.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(20).forEach(e -> {
            String[] ab = e.getKey().split("\\+");
            if (ab.length == 2) {
                связки.add(ab[0] + " + " + ab[1] + " (" + e.getValue() + ")");
                for (String id : ab) {
                    годные.stream().filter(м -> м.id().equals(id)).findFirst().ifPresent(м -> {
                        // и пара не перерастает норму развилки (01.10.2026)
                        String рп = развилкаТребования(заданиеПоId.get(м.id()));
                        long уже = набор.stream()
                            .filter(x -> рп.equals(развилкаТребования(заданиеПоId.get(x.id())))).count();
                        if (!набор.contains(м) && уже < наРазвилку && кластерСвободен(м, набор, заданиеПоId)
                                && семьиНабора.add(семейство(заданиеПоId.get(м.id())))) {
                            набор.add(м);
                        }
                    });
                }
            }
        });

        // ОБЩИЙ ДОБОР ДО РАЗМЕРА КОЛОДЫ (02.10.2026): развилке не хватило своих
        // карт — место отдаётся лучшей по трудности годной карте любой развилки
        // (то же правило: второй вариант семейства — только заметно другой ступенью)
        List<Мера> добор = new ArrayList<>(годные);
        добор.removeIf(набор::contains);
        добор.sort(Comparator.comparingDouble(м -> Math.abs(м.доля() - 0.33)));
        for (Мера м : добор) {
            if (набор.size() >= ЗАДАНИЙ) {
                break;
            }
            String семья = семейство(заданиеПоId.get(м.id()));
            boolean далеко = true;
            for (Мера х : набор) {
                if (семья.equals(семейство(заданиеПоId.get(х.id()))) && Math.abs(х.доля() - м.доля()) < 0.15) {
                    далеко = false;
                }
            }
            if (далеко && кластерСвободен(м, набор, заданиеПоId) && !двойник(м, набор, заданиеПоId)) {
                набор.add(м);
            }
        }
        StringBuilder sb = new StringBuilder("# Набор из бульона — черновик\n\n"
            + "Собран программой по правилам, заданным до итогов (kelium.СборкаНабора).\n\n"
            + "## Задания\n\n| id | развилка | требование | выполняют | награда |\n|---|---|---|---|---|\n");
        for (Мера м : набор) {
            Map<String, Object> з = заданиеПоId.get(м.id());
            sb.append(String.format(java.util.Locale.ROOT, "| %s | %s | %s | %.0f%% | %s |%n", м.id(),
                развилкаТребования(з), м.текст(), 100 * м.доля(), награда(м.доля())));
        }
        sb.append("\n## Связки, выполненные в одном ходу\n\n");
        for (String с : связки) {
            sb.append("- ").append(с).append('\n');
        }

        // ---------------- арсенал ----------------
        Map<String, List<String>> арсПоРазвилке = new LinkedHashMap<>();
        for (String р : List.of("extract", "power", "supply", "command", "develop", "карты")) {
            арсПоРазвилке.put(р, new ArrayList<>());
        }
        List<Map.Entry<String, int[]>> сработавшие = new ArrayList<>(срабатывания.entrySet());
        сработавшие.removeIf(e -> !ПРОБА && (e.getValue()[0] < 2 || e.getValue()[1] == 0));
        // ПАССИВНЫЙ РУЧЕЁК НЕ БЕРЁМ (заказ Влада 30.09.2026: «карты не сыплются
        // сами»). «Когда у тебя открыт нижний приказ» — не поступок, а состояние:
        // карта платит каждый ход, где низ выпал, и игроку нечего устраивать.
        сработавшие.removeIf(e -> арсеналПоId.get(e.getKey()) != null
            && пассивное(арсеналПоId.get(e.getKey())));
        // ОТСЕЯННЫЕ ПРОВЕРКОЙ НАБОРА (01.10.2026): карты, которые с витрины почти
        // не берут (<30 установок на 300 раздач при обычных 60–110), — мёртвое место
        // в колоде. Список — бульон/отсеяны.txt, по строке на карту.
        Path отсев = папка.resolve("отсеяны.txt");
        if (Files.exists(отсев)) {
            java.util.Set<String> мёртвые = new java.util.HashSet<>();
            for (String с : Files.readAllLines(отсев, StandardCharsets.UTF_8)) {
                if (!с.isBlank() && !с.startsWith("#")) {
                    мёртвые.add(с.trim());
                }
            }
            сработавшие.removeIf(e -> мёртвые.contains(e.getKey()));
            System.out.println("отсеяно проверкой набора: " + мёртвые.size());
        }
        // ПОРЯДОК — ОТ УМЕРЕННЫХ К КАПЕЛЬНЫМ. Карта, что срабатывает по 8–11 раз
        // за партию «за любую ветку», — пассивный доход, а не связка; ближе к
        // началу — те, что срабатывают 2–5 раз: их надо устроить.
        сработавшие.sort(Comparator.comparingDouble(e -> Math.abs(e.getValue()[1]
            / (double) Math.max(1, e.getValue()[0]) - 3.5)));
        for (var e : сработавшие) {
            Map<String, Object> а = арсеналПоId.get(e.getKey());
            if (а != null) {
                арсПоРазвилке.get(развилкаСрабатывания(а)).add(e.getKey());
            }
        }
        List<String> арсНабор = new ArrayList<>();
        java.util.Set<String> семьиАрс = new java.util.HashSet<>();
        int редких = 0;
        // сначала редкие (спец-действие, карта): их 4–6 на колоду — связки
        // случаются, когда такая карта выпала, а не каждый ход
        for (var e : сработавшие) {
            Map<String, Object> а = арсеналПоId.get(e.getKey());
            // карта с платой идёт своим проходом (одна на развилку), не редкой
            if (а == null || !редкое(а) || редких >= РЕДКИХ
                    || ((Map<String, Object>) а.get("низ")).get("плата") != null) {
                continue;
            }
            if (семьиАрс.add(семействоАрсенала(а))) {
                арсНабор.add(e.getKey());
                редких++;
            }
        }
        // ПОСТУПОК В КАЖДОЙ РАЗВИЛКЕ (01.10.2026): по одной карте, что меняет
        // правило — передвинь войско, нанеси урон, забери ресурс. Без них арсенал
        // выходил сплошь «ветка → ресурс». Берётся самая умеренная по частоте.
        Map<String, Integer> видов = new HashMap<>();
        // ПЛАТА КЕЛЕМИЕМ В КАЖДОЙ РАЗВИЛКЕ (01.10.2026, заказ Влада): по карте
        // «можешь заплатить 1 келемий: …» — вторая дверь келемию кроме Рынка
        for (var e : арсПоРазвилке.entrySet()) {
            for (String id : e.getValue()) {
                Map<String, Object> а = арсеналПоId.get(id);
                if (а.get("низ") instanceof Map<?, ?> н && н.get("плата") != null
                        && семьиАрс.add(семействоАрсенала(а))) {
                    арсНабор.add(id);
                    видов.merge(e.getKey() + ":" + ((Map<String, Object>) а.get("низ")).get("когда"), 1,
                        Integer::sum);
                    break;
                }
            }
        }
        int[] грабежей = {0};
        for (var e : арсПоРазвилке.entrySet()) {
            for (String id : e.getValue()) {
                Map<String, Object> а = арсеналПоId.get(id);
                // грабёж — не больше двух на колоду: шесть «забери у врага» из 33
                // превращали игру в мелкую делёжку, кого обобрать (01.10.2026)
                if (грабёж(а) && грабежей[0] >= 2) {
                    continue;
                }
                if (поступок(а) && !редкое(а) && семьиАрс.add(семействоАрсенала(а))) {
                    if (грабёж(а)) {
                        грабежей[0]++;
                    }
                    арсНабор.add(id);
                    видов.merge(e.getKey() + ":" + видЭффекта(а), 1, Integer::sum);
                    видов.merge(e.getKey() + ":" + ((Map<String, Object>) а.get("низ")).get("когда"), 1,
                        Integer::sum);
                    break;
                }
            }
        }
        int наКорзину = (АРСЕНАЛА - редких) / арсПоРазвилке.size() + 1;
        // не больше трёх карт с одним видом эффекта в корзине развилки: шесть
        // «сними урон» — скучная колода
        for (var e : арсПоРазвилке.entrySet()) {
            int взято = 0;
            for (String id : e.getValue()) {
                if (взято >= наКорзину || арсНабор.size() >= АРСЕНАЛА) {
                    break;
                }
                Map<String, Object> а = арсеналПоId.get(id);
                String вид = e.getKey() + ":" + видЭффекта(а);
                // и не больше двух карт на один повод: четыре «Построить
                // добытчик → …» с разными наградами — одна карта четырежды
                String повод = e.getKey() + ":" + ((Map<String, Object>) а.get("низ")).get("когда");
                // карта с платой — одна на развилку: три у Добычи дали ей 33% побед
                // при ровных 25% (замер 200 раздач, 01.10.2026)
                boolean сПлатой = ((Map<String, Object>) а.get("низ")).get("плата") != null;
                if (редкое(а) || арсНабор.contains(id) || видов.getOrDefault(вид, 0) >= 3
                        || видов.getOrDefault(повод, 0) >= 2
                        || сПлатой
                        || (грабёж(а) && грабежей[0] >= 2)
                        || семьиАрс.contains(семействоАрсенала(а))) {
                    continue;
                }
                if (грабёж(а)) {
                    грабежей[0]++;
                }
                семьиАрс.add(семействоАрсенала(а));
                арсНабор.add(id);
                видов.merge(вид, 1, Integer::sum);
                видов.merge(повод, 1, Integer::sum);
                взято++;
            }
        }
        sb.append("\n## Арсенал\n\n| id | развилка | срабатывание | срабатываний на установку |\n"
            + "|---|---|---|---|\n");
        for (String id : арсНабор) {
            Map<String, Object> а = арсеналПоId.get(id);
            int[] с = срабатывания.get(id);
            sb.append(String.format(java.util.Locale.ROOT, "| %s | %s | %s | %.2f |%n", id,
                развилкаСрабатывания(а), а.get("текст"), с[1] / (double) с[0]));
        }
        sb.append(String.format("%nГодных заданий %d из %d измеренных; в наборе %d. Сработавших"
            + " карт арсенала %d; в наборе %d (редких — спец-действие и карта — %d).%n",
            годные.size(), меры.size(), набор.size(), сработавшие.size(), арсНабор.size(), редких));
        Files.writeString(папка.resolve("набор — черновик.md"), sb.toString(), StandardCharsets.UTF_8);
        System.out.println(sb.substring(sb.lastIndexOf("Годных")));
        if (args.length > 1 && "--выгрузить".equals(args[1])) {
            выгрузить(набор, заданиеПоId, арсНабор, арсеналПоId, начальные(сработавшие, арсНабор, арсеналПоId));
        }
    }

    // ======================================================================
    //  ВЫГРУЗКА В КОЛОДЫ ИГРЫ
    // ======================================================================

    private static final List<String> КРУГ = List.of("extract", "power", "supply", "command", "develop");

    /** Ветки развилки: «пустить в дело» и «построить» (или обе ветки Командования и Развития). */
    private static List<String> веткиРазвилки(String р) {
        return switch (р) {
            case "extract" -> List.of("mining", "build_miner");
            case "power" -> List.of("energy_swap", "build_plant");
            case "supply" -> List.of("assembly", "build_military");
            case "command" -> List.of("movement", "combat");
            default -> List.of("market", "science");
        };
    }

    /**
     * Выгрузить набор в колоды игры: задания 2.0.0, арсенал 8.0.0 и свод
     * 1.47.0 (копия 1.46.0 с этими колодами). Прежние колоды и свод не
     * трогаются: сравнить старое с новым можно всегда.
     */
    @SuppressWarnings("unchecked")
    static void выгрузить(List<Мера> набор, Map<String, Map<String, Object>> заданиеПоId,
                          List<String> арсНабор, Map<String, Map<String, Object>> арсеналПоId,
                          List<String> новыеНачальные)
            throws Exception {
        org.yaml.snakeyaml.Yaml y = new org.yaml.snakeyaml.Yaml();
        Map<String, Object> старыеЗадания = y.load(Files.readString(Path.of("data/cards/objectives.2.1.0.yaml")));
        // верхи арсенала — утили 7.4.0 (их доли сожжений измерены); начальные — 8.0.0
        Map<String, Object> старыйАрсенал = y.load(Files.readString(Path.of("data/cards/arsenal.7.4.0.yaml")));
        Map<String, Object> начальныйАрсенал = y.load(Files.readString(Path.of("data/cards/arsenal.8.0.0.yaml")));
        String пз = "z" + ВЕРСИЯ_ЗАДАНИЙ.charAt(0) + "_";
        String па = "a" + ВЕРСИЯ_АРСЕНАЛА.charAt(0) + "_";
        int свободныхСпец = 0;
        List<Map<String, Object>> задания = new ArrayList<>();
        int номер = 1;
        for (Мера м : набор) {
            Map<String, Object> з = заданиеПоId.get(м.id());
            Map<String, Object> т = (Map<String, Object>) з.get("требование");
            String р = развилкаТребования(з);
            String соседняя = КРУГ.get((КРУГ.indexOf(р) + 1) % КРУГ.size());
            List<String> свои = веткиРазвилки(р);
            List<String> чужие = веткиРазвилки(соседняя);
            // НАГРАДЫ ПО ТРУДНОСТИ (дизайнер 30.09.2026). Выполнение задания —
            // по-прежнему спец-действие; награда же может вернуть их два-три,
            // и выполненное задание оплачивает следующее задание или утиль
            // арсенала — это и есть связка за ход.
            Map<String, Object> награда = new LinkedHashMap<>();
            награда.put("действие", свои.get(номер % 2) + "|" + чужие.get((номер / 2) % 2));
            Map<String, Object> язык = new LinkedHashMap<>();
            язык.put("имя", имя(т));
            язык.put("требование", т);
            Map<String, Object> усиление = ступеньВыше(т);
            if (усиление != null) {
                // основное — ветка; усиление — 2 спец-действия (трудному 3) и карта арсенала
                язык.put("усиление", усиление);
                // МОДУЛИ НАГРАДОЙ ПРОБОВАЛИ (01.10.2026) — отменено замером: урон
                // золотым модулем не вырос (0,12 → 0,15), а ходов со связкой стало
                // на треть меньше (0,78 → 0,51) — карта арсенала кормит связки.
                Map<String, Object> сверх = new LinkedHashMap<>();   // порядок полей постоянный
                сверх.put("картыАрсенала", 1);
                сверх.put("спецДействий", м.доля() < 0.25 ? 3 : 2);
                язык.put("сверх", сверх);
            } else if (м.доля() >= 0.5) {
                награда.put("монеты", 2);
            } else if (м.доля() >= 0.25) {
                награда.put("спецДействий", 2);
            } else if (м.доля() >= 0.12) {
                награда.put("спецДействий", 2);
                награда.put("монеты", 2);
            } else {
                награда.put("спецДействий", 3);
            }
            // ТРУДНОЕ ЗАДАНИЕ КОРМИТ АРСЕНАЛ (опыт 01.10.2026): связки держатся на
            // потоке карт арсенала (замер: без карты в награде ходов со связкой −35%).
            // Задание, что выполняют реже чем в четверти раздач, даёт карту арсенала
            // и в основной награде — вместо монет.
            if (СВЯЗОЧНЫЙ_АРСЕНАЛ && м.доля() < 0.25) {
                награда.remove("монеты");
                награда.put("картыАрсенала", 1);
            }
            // ВОЙНА ПЛАТИТ ТРОФЕЕМ (опыт 01.10.2026): дорога Командования брала
            // 19–21% побед при ровных 25% в пяти замерах — войска сами очков не
            // дают. Трофей — валюта науки, через неё война и превращается в очки.
            if (ВОЙНА_ТРОФЕЕМ && "command".equals(р)) {
                награда.remove("монеты");
                награда.put("трофеи", 1);
            }
            язык.put("награда", награда);
            // СВОБОДНЫЙ УТИЛЬ «∞ +1 СПЕЦ-ДЕЙСТВИЕ» (решение Влада 02.10.2026) — только
            // картам, в наградах которых спец-действий нет: иначе верх и низ одно и то же
            boolean спецВНаградах = награда.containsKey("спецДействий")
                || (язык.get("сверх") instanceof Map<?, ?> св && св.containsKey("спецДействий"));
            if (!спецВНаградах && свободныхСпец < СВОБОДНЫХ_СПЕЦ) {
                язык.put("верх", "СПЕЦ_ДЕЙСТВИЯ");
                свободныхСпец++;
            } else {
                язык.put("верх", верхРазвилки(р, номер));
            }
            язык.put("значок", р);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", String.format(пз + "%02d", номер++));
            e.put("язык", язык);
            задания.add(e);
        }
        for (Map<String, Object> e : (List<Map<String, Object>>) старыеЗадания.get("objectives")) {
            if ("starting".equals(e.get("kind"))) {
                задания.add(e);
            }
        }
        List<Map<String, Object>> старыеВерхи = new ArrayList<>();
        List<Map<String, Object>> арсенал = new ArrayList<>();
        for (Map<String, Object> e : (List<Map<String, Object>>) старыйАрсенал.get("arsenal")) {
            if ("regular".equals(e.get("kind")) && e.get("top") != null) {
                старыеВерхи.add((Map<String, Object>) e.get("top"));
            }
        }
        номер = 1;
        for (String id : арсНабор) {
            Map<String, Object> а = арсеналПоId.get(id);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", String.format(па + "%02d", номер));
            e.put("name", имяСрабатывания(а));
            e.put("kind", "regular");
            e.put("значок", развилкаСрабатывания(а));
            e.put("top", старыеВерхи.get((номер - 1) % старыеВерхи.size()));
            e.put("bottom", а.get("низ"));
            e.put("описание", а.get("текст"));
            арсенал.add(e);
            номер++;
        }
        // ПРОКАЧКА СПЕЦ-ДЕЙСТВИЯ (решение Влада 01.10.2026): «2 спец-действия —
        // только прокачкой через арсенал или наградой». Установленная карта даёт
        // ещё одно спец-действие каждый ход. Не из бульона — по замыслу; число —
        // -Dkelium.набор.прокачка (0 — без них, для сравнения).
        Map<String, Object> низПрокачки = new LinkedHashMap<>();
        низПрокачки.put("когда", Map.of("событие", "ход"));
        Map<String, Object> эфПрокачки = new LinkedHashMap<>();   // порядок полей постоянный
        эфПрокачки.put("effect", "спец");
        эфПрокачки.put("params", Map.of("n", 1));
        низПрокачки.put("эффект", эфПрокачки);
        низПрокачки.put("предел", 1);
        for (int i = 0; i < ПРОКАЧКА; i++) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", String.format(па + "%02d", номер));
            e.put("name", "Каждый ход → спец-действие");
            e.put("kind", "regular");
            e.put("значок", "карты");
            e.put("top", старыеВерхи.get((номер - 1) % старыеВерхи.size()));
            e.put("bottom", низПрокачки);
            e.put("описание", kelium.cards.язык.Срабатывание.текст(низПрокачки));
            арсенал.add(e);
            номер++;
        }
        подобратьУтили(арсенал, старыеВерхи);
        // НАЧАЛЬНЫЙ АРСЕНАЛ — 8 карт 8.0.0 (решение Влада 02.10.2026: начальных 8)
        int начальных = 0;
        for (Map<String, Object> e : (List<Map<String, Object>>) начальныйАрсенал.get("arsenal")) {
            if ("starting".equals(e.get("kind"))) {
                арсенал.add(e);
                начальных++;
            }
        }
        if (начальных >= 8) {
            новыеНачальные = List.of();
        }
        // ДВЕ НОВЫЕ НАЧАЛЬНЫЕ (01.10.2026): после снятия «Сдачи тары» их было 6,
        // в запасе на четверых — две. Верх — стартовый набор ценой как у
        // прочих (два ресурса), низ — срабатывание на слой карт из бульона.
        List<Map<String, Object>> наборыВерха = List.of(
            Map.of("coin", 1, "objective_cards", 1, "kit", true),
            Map.of("kelium", 1, "ammo", 1, "kit", true));
        List<String> подписиВерха = List.of("1 монета и 1 карта задания", "1 келемий и 1 боеприпас");
        for (int i = 0; i < новыеНачальные.size() && i < наборыВерха.size(); i++) {
            Map<String, Object> а = арсеналПоId.get(новыеНачальные.get(i));
            Map<String, Object> верх = new LinkedHashMap<>();
            верх.put("effect", "gain");
            верх.put("params", наборыВерха.get(i));
            верх.put("label", подписиВерха.get(i));
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", "bs80_" + (i + 1));
            e.put("name", имяСрабатывания(а));
            e.put("kind", "starting");
            e.put("top", верх);
            e.put("bottom", а.get("низ"));
            e.put("описание", "Утиль: " + подписиВерха.get(i) + ". Установка: " + а.get("текст"));
            арсенал.add(e);
            System.out.println("начальная bs80_" + (i + 1) + ": " + а.get("текст"));
        }
        org.yaml.snakeyaml.DumperOptions o = new org.yaml.snakeyaml.DumperOptions();
        o.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        o.setAllowUnicode(true);
        o.setWidth(120);
        org.yaml.snakeyaml.Yaml д = new org.yaml.snakeyaml.Yaml(o);
        Map<String, Object> фЗ = new LinkedHashMap<>();
        фЗ.put("meta", Map.of("id", ВЕРСИЯ_ЗАДАНИЙ, "type", "objectives"));
        фЗ.put("objectives", задания);
        Files.writeString(Path.of("data/cards/objectives." + ВЕРСИЯ_ЗАДАНИЙ + ".yaml"),
            "# " + ВЕРСИЯ_ЗАДАНИЙ + ": задания языком карт, собраны kelium.СборкаНабора из бульона\n"
                + "# по мере живых партий стратегов (энергия и уровни зданий — с 02.10.2026).\n"
                + "# Начальные — как в 2.1.0.\n"
                + "# CONTENT: objectives  version " + ВЕРСИЯ_ЗАДАНИЙ + "\n" + д.dump(копияБезЯкорей(фЗ)),
            StandardCharsets.UTF_8);
        Map<String, Object> фА = new LinkedHashMap<>();
        фА.put("meta", Map.of("id", ВЕРСИЯ_АРСЕНАЛА, "type", "arsenal"));
        фА.put("arsenal", арсенал);
        Files.writeString(Path.of("data/cards/arsenal." + ВЕРСИЯ_АРСЕНАЛА + ".yaml"),
            "# " + ВЕРСИЯ_АРСЕНАЛА + ": низ — срабатывания данными, верх — утили арсенала 7.4.0;\n"
                + "# собран kelium.СборкаНабора из бульона. Начальные — как в 8.0.0.\n"
                + "# CONTENT: arsenal  version " + ВЕРСИЯ_АРСЕНАЛА + "\n" + д.dump(копияБезЯкорей(фА)),
            StandardCharsets.UTF_8);
        String основа = Files.readString(Path.of("data/rulesets/" + СВОД_ОСНОВА + ".yaml"),
            StandardCharsets.UTF_8);
        String свод = основа
            .replaceFirst("(?m)^  id: " + java.util.regex.Pattern.quote(СВОД_ОСНОВА), "  id: " + СВОД)
            .replaceFirst("(?m)^  objectives: \\d+\\.\\d+\\.\\d+.*$",
                "  objectives: " + ВЕРСИЯ_ЗАДАНИЙ + "             # собраны из бульона")
            .replaceFirst("(?m)^  arsenal: \\d+\\.\\d+\\.\\d+.*$",
                "  arsenal: " + ВЕРСИЯ_АРСЕНАЛА + "                # собран из бульона")
            // супер-арсенал 8 карт (решение Влада 02.10.2026): 4 супер-войска и 4 способности
            .replaceFirst("(?m)^  super_arsenal: 4\\.0\\.0.*$",
                "  super_arsenal: 4.1.0          # 4.1 (02.10.2026): 8 карт — без «Баллистического расчёта»");
        Files.writeString(Path.of("data/rulesets/" + СВОД + ".yaml"),
            "# " + СВОД + ": как " + СВОД_ОСНОВА + ", колоды из бульона — задания " + ВЕРСИЯ_ЗАДАНИЙ
                + " и арсенал " + ВЕРСИЯ_АРСЕНАЛА + ".\n" + свод,
            StandardCharsets.UTF_8);
        System.out.printf("выгружено: заданий %d, арсенала %d, свод %s%n", задания.size(),
            арсенал.size(), СВОД);
    }

    /** Имя карты арсенала по сути: «Рынок → монета», «Уничтожил → спец-действие». */
    @SuppressWarnings("unchecked")
    private static String имяСрабатывания(Map<String, Object> а) {
        Map<String, Object> низ = (Map<String, Object>) а.get("низ");
        Map<String, Object> когда = (Map<String, Object>) низ.get("когда");
        Map<String, Object> эф = (Map<String, Object>) низ.get("эффект");
        String что = String.valueOf(когда.get("событие"));
        String событие = switch (что) {
            case "ветка" -> kelium.cards.язык.Требование.ветка(String.valueOf(когда.get("ветка")))
                .replace("«", "").replace("»", "");
            case "развилка" -> switch (String.valueOf(когда.get("развилка"))) {
                case "extract" -> "Добыча";
                case "power" -> "Питание";
                case "supply" -> "Снабжение";
                case "command" -> "Командование";
                default -> "Развитие";
            };
            case "задание" -> "Задание";
            case "сжёг" -> "Сжёг карту";
            case "установил" -> "Установил";
            case "уничтожил" -> "Уничтожил";
            case "потерял" -> "Потеря";
            case "совпадение" -> "Совпадение";
            case "низ" -> "Нижний приказ";
            case "ход" -> "Каждый ход";
            case "построил_крупно" -> "Здание 3-го уровня";
            default -> что;
        };
        Map<String, Object> п = эф.get("params") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String итог = switch (String.valueOf(эф.get("effect"))) {
            case "спец" -> "спец-действие";
            case "heal_one" -> "ремонт";
            case "move_unit" -> "шаг войска";
            case "place_damage" -> "урон врагу";
            case "steal_resource" -> switch (String.valueOf(п.get("resource"))) {
                case "coin" -> "монета врага";
                case "ammo" -> "боеприпас врага";
                default -> "келемий врага";
            };
            case "free_action" -> kelium.cards.язык.Требование.ветка(String.valueOf(п.get("action")))
                .replace("«", "").replace("»", "");
            case "upgrade_building" -> "уровень выше";
            case "permanent_energy" -> "кубик энергии";
            case "gain_per" -> (п.containsKey("coin") ? "монеты" : п.containsKey("kelium") ? "келемий"
                : "боеприпасы") + (String.valueOf(п.get("per")).startsWith("own_powered") ? " за запитанные"
                : " за уровни");
            default -> п.containsKey("coin") ? "монеты" : п.containsKey("ammo") ? "боеприпас"
                : п.containsKey("kelium") ? "келемий" : п.containsKey("trophy") ? "трофей"
                : п.containsKey("objective_cards") ? "задание" : "добро";
        };
        return событие + (низ.get("плата") != null ? " + келемий" : "") + " → " + итог;
    }

    /** Имя карты — строго по сути требования, без придуманных слов. */
    @SuppressWarnings("unchecked")
    private static String имя(Map<String, Object> т) {
        String узел = String.valueOf(т.get("узел"));
        Object n = т.getOrDefault("сколько", т.getOrDefault("гексов", т.get("k")));
        String число = n == null ? "" : " ×" + n;
        return switch (узел) {
            // «Запитанный добытчик ×2» и «Добытчик ×3» — разные карты, имя их различает
            case "жетоны" -> {
                Map<String, Object> г = (Map<String, Object>) т.get("группа");
                String к = кто(г);
                if ("КРУПНЫЙ".equals(String.valueOf(г.get("сост")))) {
                    yield к + " 3-го уровня" + число;
                }
                yield "ЗАПИТАН".equals(String.valueOf(г.get("сост")))
                    ? запитанный(String.valueOf(г.get("кто"))) + " " + к.toLowerCase() + число
                    : к + число;
            }
            // на карте «на соседнем гексе», а «у врага» читалось как «на гексе врага»
            case "рядом" -> кто((Map<String, Object>) т.get("а")) + " рядом с врагом" + число;
            case "ресурс" -> switch (String.valueOf(т.get("ресурс"))) {
                case "COIN" -> "Монеты";
                case "KELIUM" -> "Келемий";
                case "TROPHY" -> "Трофеи";
                case "AMMO" -> "Боеприпасы";
                default -> "Запас";
            } + число;
            case "арсенал" -> "Арсенал" + число;
            case "свалка" -> "Свалка" + число;
            // имя называет то, что на карте: «Бой и Наука», а не «Две ветки»
            case "ветки" -> {
                List<String> слова = new ArrayList<>();
                for (Object в : (List<?>) т.get("ветки")) {
                    слова.add(kelium.cards.язык.Требование.ветка(String.valueOf(в))
                        .replace("«", "").replace("»", ""));
                }
                yield String.join(" и ", слова);
            }
            case "обе_ветки" -> "Обе ветки " + switch (String.valueOf(т.get("развилка"))) {
                case "extract" -> "Добычи";
                case "power" -> "Питания";
                case "supply" -> "Снабжения";
                case "command" -> "Командования";
                default -> "Развития";
            };
            case "очередь_задания" -> порядковоеИм(т.get("k")) + " задание за ход";
            case "очередь_спец" -> порядковоеИм(т.get("k")) + " спец-действие";
            case "сожги" -> "Сожги" + число;
            case "установи" -> "Установи";
            case "уничтожь" -> {
                String цель = String.valueOf(((Map<String, Object>) т.get("цель")).get("кто"));
                yield "Уничтожь " + (цель.equals("ЖЕТОН") ? "жетон" : цель.equals("ЗДАНИЕ") ? "здание"
                    : цель.equals("ВОЙСКО") ? "войско" : цель.toLowerCase().replace('_', ' ')) + число;
            }
            case "построй" -> "Построй" + число;
            case "найми" -> "Найми" + число;
            case "добудь" -> "Добудь" + число;
            case "запитай" -> "Запитай" + число;
            case "выпусти" -> "Выпусти" + число;
            case "потрать" -> "Потрать келемий" + число;
            // БЕЗ САМОДЕЛЬНЫХ СЛОВ (02.10.2026): «лестница», «хозяйство», «крупная
            // постройка» — не термины игры; имя называет то, что на карте
            case "уровни" -> ("plant".equals(т.get("вид")) ? "Уровни энергостанций"
                : "miner".equals(т.get("вид")) ? "Уровни добытчиков"
                : "Уровни добытчиков и энергостанций") + " " + т.get("сумма") + "+";
            case "разные_уровни" -> ("plant".equals(т.get("вид")) ? "Энергостанции разных уровней"
                : "miner".equals(т.get("вид")) ? "Добытчики разных уровней"
                : "Здания разных уровней") + число;
            case "энергия" -> "Энергия в ячейках" + число;
            case "все_запитаны" -> switch (String.valueOf(т.get("кто"))) {
                case "ДОБЫТЧИК" -> "Все добытчики запитаны";
                case "ЭНЕРГОСТАНЦИЯ" -> "Все энергостанции запитаны";
                case "ВОЕННОЕ" -> "Все военные здания запитаны";
                default -> "Все здания запитаны";
            };
            case "построй_крупно" -> "Здание 3-го уровня";
            case "треки" -> "Наука на " + т.get("треков") + (Integer.valueOf(1).equals(т.get("треков")) ? " треке" : " треках")
                + (т.get("ступень") instanceof Number ст && ст.intValue() > 1 ? ", ступень " + ст + "+" : "");
            case "модули" -> (Boolean.TRUE.equals(т.get("золотых")) ? "Золотые модули" : "Модули") + число;
            case "рода" -> "Рода войск" + число;
            case "и" -> "Боеприпасы и войска";
            default -> узел;
        };
    }

    /**
     * ПАРА «ВЕРХ + НИЗ» С УМЫСЛОМ (01.10.2026). Проверка 300 раздач: судьбу карты
     * решает утиль — «2 боеприпаса», «Построй любое здание за 1 монету» сжигали в
     * 90–95% случаев, «Удали у врага арсенал» — в 3%. По кругу половина карт выбора
     * не задавала. Самые сжигаемые утили — картам с сильным низом (прокачка,
     * спец-действие, карта задания, плата келемием): сжечь или поставить — мука.
     * Доли сожжений — бульон/утили — доля сожжений.txt; нет файла — по кругу.
     */
    @SuppressWarnings("unchecked")
    static void подобратьУтили(List<Map<String, Object>> арсенал, List<Map<String, Object>> верхи)
            throws Exception {
        Path файл = Path.of("design-docs/фигуры/бульон/утили — доля сожжений.txt");
        if (!Files.exists(файл)) {
            return;
        }
        Map<String, Double> доля = new HashMap<>();
        for (String с : Files.readAllLines(файл, StandardCharsets.UTF_8)) {
            String[] ч = с.split("\t", 2);
            if (ч.length == 2 && !с.startsWith("#")) {
                доля.put(ч[1].trim(), Double.parseDouble(ч[0].trim()));
            }
        }
        // утили по убыванию «сжигаемости», с повторами, по одному на карту
        List<Map<String, Object>> поСиле = new ArrayList<>();
        for (int i = 0; поСиле.size() < арсенал.size(); i++) {
            поСиле.add(верхи.get(i % верхи.size()));
        }
        поСиле.sort((a, b) -> Double.compare(доля.getOrDefault(String.valueOf(b.get("label")), 0.3),
            доля.getOrDefault(String.valueOf(a.get("label")), 0.3)));
        List<Map<String, Object>> сильные = new ArrayList<>();
        List<Map<String, Object>> обычные = new ArrayList<>();
        for (Map<String, Object> e : арсенал) {
            Map<String, Object> низ = (Map<String, Object>) e.get("bottom");
            Map<String, Object> обёртка = Map.of("низ", низ);
            boolean сильная = низ.get("плата") != null || редкое(обёртка)
                || низ.get("когда") instanceof Map<?, ?> к && "ход".equals(к.get("событие"));
            (сильная ? сильные : обычные).add(e);
        }
        int i = 0;
        for (Map<String, Object> e : сильные) {
            e.put("top", поСиле.get(i++));
        }
        for (Map<String, Object> e : обычные) {
            e.put("top", поСиле.get(i++));
        }
        for (Map<String, Object> e : арсенал) {
            Map<String, Object> верх = (Map<String, Object>) e.get("top");
            Map<String, Object> низ = (Map<String, Object>) e.get("bottom");
            e.put("описание", "Утиль: " + верх.get("label") + ". Установка: "
                + kelium.cards.язык.Срабатывание.текст(низ));
        }
        System.out.println("утили подобраны: сильных низов " + сильные.size() + ", обычных " + обычные.size());
    }

    /** «Второе», «Третье» — для имени карты (именительный, средний род). */
    private static String порядковоеИм(Object k) {
        int n = k instanceof Number x ? x.intValue() : 2;
        return switch (n) {
            case 2 -> "Второе";
            case 3 -> "Третье";
            case 4 -> "Четвёртое";
            default -> n + "-е";
        };
    }

    /** «Запитанный / запитанная / запитанное» — по роду жетона. */
    private static String запитанный(String кто) {
        return switch (кто) {
            case "ЭНЕРГОСТАНЦИЯ", "КАЗАРМА", "АВИАБАЗА" -> "Запитанная";
            case "ЗДАНИЕ", "ВОЕННОЕ" -> "Запитанное";
            default -> "Запитанный";
        };
    }

    private static String кто(Map<String, Object> г) {
        String к = String.valueOf(г.get("кто"));
        if (к.equals("ВОЕННОЕ")) {
            return "Военное здание";
        }
        return к.charAt(0) + к.substring(1).toLowerCase().replace('_', ' ');
    }

    /**
     * Усиление — то же требование на ступень выше, если такая ступень вообще
     * бывает: жетонов у игрока не больше, чем в его запасе (книга, гл. 4),
     * ячеек арсенала три. Ресурсы, найм и стройка за ход без усиления: их
     * потолок задают хранилище и число веток, и ступень выше могла бы оказаться
     * невыполнимой.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> ступеньВыше(Map<String, Object> т) {
        String узел = String.valueOf(т.get("узел"));
        int предел;
        String ключ;
        switch (узел) {
            case "жетоны" -> {
                ключ = "сколько";
                предел = kelium.cards.язык.Кто.valueOf(String.valueOf(
                    ((Map<String, Object>) т.get("группа")).get("кто"))).наибольшее();
            }
            case "рядом" -> {
                ключ = "гексов";
                предел = Math.min(3, kelium.cards.язык.Кто.valueOf(String.valueOf(
                    ((Map<String, Object>) т.get("а")).get("кто"))).наибольшее());
            }
            case "арсенал" -> {
                ключ = "сколько";
                предел = 3;
            }
            case "свалка", "уничтожь" -> {
                ключ = "сколько";
                предел = 3;
            }
            default -> {
                return null;
            }
        }
        if (!(т.get(ключ) instanceof Number n) || n.intValue() + 1 > предел) {
            return null;
        }
        Map<String, Object> у = new LinkedHashMap<>(т);
        у.put(ключ, n.intValue() + 1);
        return у;
    }

    /**
     * ПЕЧАТНЫЙ УТИЛЬ ПО РАЗВИЛКЕ КАРТЫ — по кругу из своего набора (02.10.2026).
     * Прежде у всех карт развилки был один верх («все 10 карт Добычи сжигают ради
     * Свободной добычи»): выбор «сжечь или выполнить» не зависел от карты. Теперь у
     * каждой развилки 3–4 утиля, среди них реакция в чужой ход — защита и ответ.
     */
    private static final Map<String, Integer> ВЕРХОВ_ВЗЯТО = new HashMap<>();

    private static String верхРазвилки(String р, int номер) {
        List<String> круг = switch (р) {
            case "extract" -> List.of("ДОБЫЧА", "СТРОЙКА", "КОНВЕРСИЯ");
            case "power" -> List.of("ЭНЕРГИЯ_ИЛИ_МОДУЛИ", "МОДУЛИ", "ЩИТ_ТЕХНИКА_АВИАЦИЯ");
            case "supply" -> List.of("СНАРЯЖЕНИЕ", "РАЗВЁРТЫВАНИЕ", "ЗАКРОМА", "ЩИТ_ПЕХОТА_АВИАЦИЯ");
            case "command" -> List.of("ДВИЖЕНИЕ", "АТАКА_ОДНИМ_ВОЙСКОМ", "ОТХОД", "СКОРОСТЬ");
            default -> List.of("РЫНОК", "НАУКА", "ОБМЕН_НАУКА_ИЛИ_РЫНОК");
        };
        int k = ВЕРХОВ_ВЗЯТО.merge(р, 1, Integer::sum) - 1;
        return круг.get(k % круг.size());
    }

    private static Object копияБезЯкорей(Object x) {
        if (x instanceof Map<?, ?> m) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (var e : m.entrySet()) {
                out.put(e.getKey(), копияБезЯкорей(e.getValue()));
            }
            return out;
        }
        if (x instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object v : l) {
                out.add(копияБезЯкорей(v));
            }
            return out;
        }
        return x;
    }

    /** Награда по трудности (без усиления): чем реже выполняют, тем больше спец-действий. */
    static String награда(double доля) {
        if (доля >= 0.5) {
            return "ветка на выбор и 2 монеты";
        }
        if (доля >= 0.25) {
            return "ветка на выбор и 2 спец-действия";
        }
        if (доля >= 0.12) {
            return "ветка на выбор, 2 спец-действия и 2 монеты";
        }
        return "ветка на выбор и 3 спец-действия";
    }

    /** Развилка требования: действием закрывают — его развилка; состояния — по роду. */
    @SuppressWarnings("unchecked")
    static String развилкаТребования(Map<String, Object> з) {
        if (з == null || !(з.get("требование") instanceof Map<?, ?> т)) {
            return "command";
        }
        Map<String, Object> м = (Map<String, Object>) т;
        String узел = String.valueOf(м.get("узел"));
        return switch (узел) {
            case "обе_ветки" -> String.valueOf(м.get("развилка"));
            case "ветки" -> {
                List<?> в = (List<?>) м.get("ветки");
                yield kelium.engine.Срабатывания.развилка(String.valueOf(в.get(0)));
            }
            case "уничтожь", "свалка" -> "command";
            // рядом с врагом — развилка того, что ставишь сам: энергостанцию у
            // вражеского здания строят Энергией, войска ведут Командованием
            case "рядом" -> switch (String.valueOf(((Map<String, Object>) м.get("а")).get("кто"))) {
                case "ДОБЫТЧИК" -> "extract";
                case "ЭНЕРГОСТАНЦИЯ" -> "power";
                case "ВОЕННОЕ", "КАЗАРМА", "ЗАВОД", "АВИАБАЗА" -> "supply";
                default -> "command";
            };
            case "добудь" -> "extract";
            case "запитай" -> "power";
            // ЭНЕРГИЯ И УРОВНИ (02.10.2026)
            case "уровни", "разные_уровни" -> "miner".equals(м.get("вид")) ? "extract" : "power";
            case "энергия", "все_запитаны", "построй_крупно" -> "power";
            case "треки", "модули" -> "develop";
            case "рода" -> "supply";
            // «и»: пары Снабжения — боеприпасы и войска (или военные здания)
            case "и" -> "supply";
            case "выпусти" -> "supply";
            // трата келемия — задача добытчика: кто добыл, тот и тратит
            case "потрать" -> "extract";
            case "построй" -> {
                Object вид = м.get("вид");
                yield вид == null || "miner".equals(вид) ? "extract" : "plant".equals(вид) ? "power"
                    : "supply";
            }
            case "найми" -> "supply";
            case "ресурс" -> "COIN".equals(м.get("ресурс")) || "KELIUM".equals(м.get("ресурс"))
                ? "develop" : "TROPHY".equals(м.get("ресурс")) ? "develop" : "supply";
            case "арсенал", "очередь_задания", "очередь_спец", "сожги", "установи" -> "develop";
            case "жетоны" -> {
                Map<String, Object> г = (Map<String, Object>) м.get("группа");
                String кто = String.valueOf(г.get("кто"));
                yield switch (кто) {
                    case "ДОБЫТЧИК" -> "extract";
                    case "ЭНЕРГОСТАНЦИЯ" -> "power";
                    case "ВОЕННОЕ", "ЗДАНИЕ" -> "supply";
                    default -> "command";
                };
            }
            default -> "command";
        };
    }

    /** Развилка срабатывания — развилка события; карты и прочее — отдельной корзиной. */
    @SuppressWarnings("unchecked")
    static String развилкаСрабатывания(Map<String, Object> а) {
        Map<String, Object> низ = (Map<String, Object>) а.get("низ");
        Map<String, Object> когда = (Map<String, Object>) низ.get("когда");
        String что = String.valueOf(когда.get("событие"));
        return switch (что) {
            case "ветка" -> kelium.engine.Срабатывания.развилка(String.valueOf(когда.get("ветка")));
            case "развилка" -> String.valueOf(когда.get("развилка"));
            case "уничтожил", "потерял" -> "command";
            case "построил_крупно" -> "extract";
            default -> "карты";
        };
    }

    /** Узлы, которые спрашивают о картах хода, — связки. */
    static final java.util.Set<String> СВЯЗОЧНЫЕ = java.util.Set.of("очередь_задания", "очередь_спец",
        "обе_ветки", "сожги", "установи", "ветки");

    @SuppressWarnings("unchecked")
    static String узел(Map<String, Object> з) {
        return з != null && з.get("требование") instanceof Map<?, ?> т
            ? String.valueOf(((Map<String, Object>) т).get("узел")) : "";
    }

    /** Семейство задания: вид требования и кто — без числа. */
    @SuppressWarnings("unchecked")
    static String семейство(Map<String, Object> з) {
        Map<String, Object> т = (Map<String, Object>) з.get("требование");
        String узел = String.valueOf(т.get("узел"));
        return switch (узел) {
            // состояние — часть задачи: «3 добытчика» — стройка, «2 запитанных
            // добытчика» — энергия к ним; это разные карты
            case "жетоны" -> узел + ":" + ((Map<String, Object>) т.get("группа")).get("кто") + ":"
                + ((Map<String, Object>) т.get("группа")).get("сост");
            // род вражеского жетона семью не меняет: «военное здание у здания /
            // у войска / у добытчика врага» — одна задача трижды (01.10.2026)
            case "рядом" -> узел + ":" + ((Map<String, Object>) т.get("а")).get("кто");
            case "ресурс" -> узел + ":" + т.get("ресурс");
            case "свалка" -> узел + ":" + ((Map<String, Object>) т.get("группа")).get("кто");
            case "уничтожь" -> узел + ":" + ((Map<String, Object>) т.get("цель")).get("кто") + ":" + т.get("кем");
            case "построй" -> узел + ":" + т.get("вид");
            case "уровни", "разные_уровни" -> узел + ":" + т.get("вид");
            case "все_запитаны" -> узел + ":" + т.get("кто");
            case "треки" -> узел + ":" + т.get("ступень");
            case "модули" -> узел + ":" + т.get("золотых");
            case "и" -> узел + ":" + т.get("части").toString().contains("ВОЕННОЕ");
            case "обе_ветки" -> узел + ":" + т.get("развилка");
            case "ветки" -> узел + ":" + т.get("ветки");
            default -> узел;
        };
    }

    /**
     * КЛАСТЕР ДЕЯТЕЛЬНОСТИ (02.10.2026): задания, которые закрывает одно и то же
     * дело. «Сумма уровней добытчиков», «разные уровни добытчиков», «добытчик
     * 3-го уровня», «3 добытчика» — всё это «строй добытчики»: один план
     * выполнял четыре карты, и дорога Добычи брала 38% побед (замер 1.50.0).
     * Не больше {@link #НА_КЛАСТЕР} карт на кластер; {@code null} — вне кластеров.
     */
    @SuppressWarnings("unchecked")
    static String кластер(Map<String, Object> з) {
        if (з == null || !(з.get("требование") instanceof Map<?, ?> м)) {
            return null;
        }
        Map<String, Object> т = (Map<String, Object>) м;
        String узел = String.valueOf(т.get("узел"));
        Object вид = т.get("вид");
        if (узел.equals("уровни") || узел.equals("разные_уровни")) {
            return "plant".equals(вид) ? "строй станции" : "miner".equals(вид) ? "строй добытчики" : "строй хозяйство";
        }
        if (узел.equals("жетоны") && т.get("группа") instanceof Map<?, ?> г
                && !"ЗАПИТАН".equals(String.valueOf(г.get("сост")))) {
            String кто = String.valueOf(г.get("кто"));
            if (кто.equals("ДОБЫТЧИК")) {
                return "строй добытчики";
            }
            if (кто.equals("ЭНЕРГОСТАНЦИЯ")) {
                return "строй станции";
            }
        }
        return null;
    }

    static final int НА_КЛАСТЕР = 2;

    /**
     * ДВОЙНИК В НАБОРЕ (02.10.2026): карта, что за столом выглядит как уже взятая
     * и выполняется вместе с ней. «Свой добытчик рядом с вражеским добытчиком» и
     * «… рядом с вражеским зданием» — одно имя, и первое закрывает второе (добытчик
     * — тоже здание); «4 войска» и «4 войска на разных гексах» — так же. Набор 3.0
     * взял пять таких пар, добирая до 50 карт.
     */
    @SuppressWarnings("unchecked")
    private static boolean двойник(Мера м, List<Мера> набор, Map<String, Map<String, Object>> заданиеПоId) {
        Map<String, Object> т = (Map<String, Object>) заданиеПоId.get(м.id()).get("требование");
        String узел = String.valueOf(т.get("узел"));
        for (Мера х : набор) {
            Map<String, Object> у = (Map<String, Object>) заданиеПоId.get(х.id()).get("требование");
            if (!узел.equals(String.valueOf(у.get("узел")))) {
                continue;
            }
            switch (узел) {
                case "рядом" -> {
                    // тот же свой жетон, то же число гексов — враг «здание» вмещает
                    // «добытчика», «войско» — «пехоту»: одно имя, одно закрывает другое
                    if (String.valueOf(((Map<String, Object>) т.get("а")).get("кто"))
                            .equals(String.valueOf(((Map<String, Object>) у.get("а")).get("кто")))
                            && String.valueOf(т.get("гексов")).equals(String.valueOf(у.get("гексов")))) {
                        return true;
                    }
                }
                case "жетоны" -> {
                    // та же группа и то же число: «на разных гексах» лишь строже
                    if (String.valueOf(т.get("группа")).equals(String.valueOf(у.get("группа")))
                            && String.valueOf(т.get("сколько")).equals(String.valueOf(у.get("сколько")))) {
                        return true;
                    }
                }
                case "все_запитаны" -> {
                    // «все здания запитаны» и «все добытчики запитаны» при том же
                    // «не меньше» — второе почти всегда идёт вместе с первым
                    if (String.valueOf(т.get("не_меньше")).equals(String.valueOf(у.get("не_меньше")))) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    /** Не переполнен ли кластер карты в наборе. */
    private static boolean кластерСвободен(Мера м, List<Мера> набор, Map<String, Map<String, Object>> заданиеПоId) {
        String к = кластер(заданиеПоId.get(м.id()));
        if (к == null) {
            return true;
        }
        long уже = набор.stream().filter(х -> к.equals(кластер(заданиеПоId.get(х.id())))).count();
        return уже < НА_КЛАСТЕР;
    }

    /** Семейство карты арсенала: событие и вид эффекта (ресурс) — без предела. */
    @SuppressWarnings("unchecked")
    static String семействоАрсенала(Map<String, Object> а) {
        Map<String, Object> низ = (Map<String, Object>) а.get("низ");
        Map<String, Object> эф = (Map<String, Object>) низ.get("эффект");
        Map<String, Object> п = эф.get("params") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return String.valueOf(низ.get("когда")) + "|" + эф.get("effect") + "|" + new java.util.TreeSet<>(п.keySet())
            + (низ.get("плата") != null ? "|плата" : "");
    }

    /** Вид эффекта карты арсенала: монеты, боеприпас, ремонт, ветка… */
    @SuppressWarnings("unchecked")
    static String видЭффекта(Map<String, Object> а) {
        Map<String, Object> эф = (Map<String, Object>) ((Map<String, Object>) а.get("низ")).get("эффект");
        Map<String, Object> п = эф.get("params") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return эф.get("effect") + "|" + new java.util.TreeSet<>(п.keySet());
    }

    /** Эффект-поступок, меняющий правило: передвинь войско, нанеси урон, забери ресурс. */
    @SuppressWarnings("unchecked")
    static boolean поступок(Map<String, Object> а) {
        Map<String, Object> эф = (Map<String, Object>) ((Map<String, Object>) а.get("низ")).get("эффект");
        return java.util.Set.of("move_unit", "place_damage", "steal_resource")
            .contains(String.valueOf(эф.get("effect")));
    }

    /** «Забери у одного врага …» — грабёж. */
    @SuppressWarnings("unchecked")
    static boolean грабёж(Map<String, Object> а) {
        Map<String, Object> эф = (Map<String, Object>) ((Map<String, Object>) а.get("низ")).get("эффект");
        return "steal_resource".equals(String.valueOf(эф.get("effect")));
    }

    /** Спец-действие или карта — редкая, сильная шестерёнка связок. */
    @SuppressWarnings("unchecked")
    static boolean редкое(Map<String, Object> а) {
        Map<String, Object> низ = (Map<String, Object>) а.get("низ");
        Map<String, Object> эф = (Map<String, Object>) низ.get("эффект");
        String имя = String.valueOf(эф.get("effect"));
        return имя.equals("спец") || (эф.get("params") instanceof Map<?, ?> п
            && п.containsKey("objective_cards"));
    }

    /**
     * Задание, что закрывается ОДНИМ действием с пустого места («В ЭТОТ ХОД
     * построй здание»): дизайнер исключил такие ещё в каталоге 10.0 (17.08.2026).
     * С наградой-веткой оно к тому же кормит само себя.
     */
    @SuppressWarnings("unchecked")
    static boolean однимДействием(Map<String, Object> з) {
        if (з == null || !(з.get("требование") instanceof Map<?, ?> м)) {
            return false;
        }
        Map<String, Object> т = (Map<String, Object>) м;
        String у = String.valueOf(т.get("узел"));
        int сколько = т.get("сколько") instanceof Number n ? n.intValue() : 1;
        return (у.equals("построй") || у.equals("найми")) && сколько <= 1;
    }

    /** Срабатывание на состояние (открыт нижний приказ), а не на поступок игрока. */
    @SuppressWarnings("unchecked")
    static boolean пассивное(Map<String, Object> а) {
        Map<String, Object> когда = (Map<String, Object>) ((Map<String, Object>) а.get("низ")).get("когда");
        return когда != null && "низ".equals(String.valueOf(когда.get("событие")));
    }

    /**
     * Две новые начальные карты: по одному срабатыванию на «выполнил задание» и
     * «установил карту арсенала», эффект — ресурс (не спец-действие: начальная
     * достаётся случайно, сильную шестерёнку одному игроку не дарим). Из
     * годных берётся самое частое на установку, не взятое в колоду.
     */
    @SuppressWarnings("unchecked")
    static List<String> начальные(List<Map.Entry<String, int[]>> сработавшие, List<String> арсНабор,
                                  Map<String, Map<String, Object>> арсеналПоId) {
        List<String> итог = new ArrayList<>();
        for (String событие : List.of("задание", "установил")) {
            String лучший = null;
            double частота = -1;
            for (var e : сработавшие) {
                Map<String, Object> а = арсеналПоId.get(e.getKey());
                if (а == null || арсНабор.contains(e.getKey()) || e.getValue()[0] < 4) {
                    continue;
                }
                Map<String, Object> низ = (Map<String, Object>) а.get("низ");
                Map<String, Object> когда = (Map<String, Object>) низ.get("когда");
                Map<String, Object> эф = (Map<String, Object>) низ.get("эффект");
                if (!событие.equals(String.valueOf(когда.get("событие"))) || когда.size() > 1
                        || !"gain".equals(эф.get("effect")) || редкое(а)) {
                    continue;
                }
                double ч = e.getValue()[1] / (double) e.getValue()[0];
                if (ч > частота) {
                    частота = ч;
                    лучший = e.getKey();
                }
            }
            if (лучший != null) {
                итог.add(лучший);
            }
        }
        return итог;
    }

    private static int целое(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double дробь(String s) {
        try {
            return Double.parseDouble(s.trim().replace("+", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
