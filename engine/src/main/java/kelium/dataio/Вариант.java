package kelium.dataio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ОДИН ПРОВЕРЯЕМЫЙ ВАРИАНТ ПРАВИЛ — свод, поля, скорости, печатные цели.
 *
 * <p><b>Зачем понадобилось.</b> Переключатели замеров ({@code kelium.rules},
 * {@code kelium.layouts.dir}, {@code kelium.speeds}, {@code kelium.targets})
 * читались из системных свойств, то есть были ОДНИ НА ПРОЦЕСС. Из-за этого
 * каждый вариант требовал своего запуска java, и вилка из пяти вариантов
 * поднимала пять виртуальных машин: пять прогревов JIT, пять чтений YAML, пять
 * куч — и ни одной возможности разложить работу по ядрам ровно. Замер
 * 20.09.2026 на машине с шестнадцатью ядрами: пять процессов занимали 85%
 * процессора и всё равно шли по очереди внутри себя.
 *
 * <p><b>Что это меняет.</b> Вариант становится значением на ПОТОКЕ. Один
 * процесс может гонять все варианты сразу, сложив их партии в одну очередь на
 * общий пул: ядра заняты ровно, YAML читается один раз, JIT прогревается один
 * раз. И, что важнее любого железа, все варианты можно играть НА ОДНИХ И ТЕХ ЖЕ
 * сидах — а парное сравнение на общих случайных числах требует в разы меньше
 * партий, чем независимые выборки.
 *
 * <p><b>Совместимость.</b> Когда вариант на потоке не выставлен, всё читается
 * из системных свойств ровно как раньше: старые стенды и запуски продолжают
 * работать без правок.
 */
public record Вариант(String имя,
                      String папкаПолей,
                      Map<String, Object> правкиСвода,
                      Map<String, Object> скорости,
                      Map<String, String> цели) {

    private static final ThreadLocal<Вариант> ТЕКУЩИЙ = new ThreadLocal<>();

    public Вариант {
        правкиСвода = правкиСвода == null ? Map.of() : Map.copyOf(правкиСвода);
        скорости = скорости == null ? Map.of() : Map.copyOf(скорости);
        цели = цели == null ? Map.of() : Map.copyOf(цели);
    }

    /** Пустой вариант: всё как в файлах, ничего не переопределено. */
    public static Вариант обычный(String имя) {
        return new Вариант(имя, null, Map.of(), Map.of(), Map.of());
    }

    /** Вариант, действующий на этом потоке, или {@code null}. */
    public static Вариант сейчас() {
        return ТЕКУЩИЙ.get();
    }

    /**
     * Выполнить работу под этим вариантом. Прежнее значение возвращается на
     * место в {@code finally}: потоки в пуле переиспользуются, и забытый
     * вариант протёк бы в чужую партию — самая тихая из возможных ошибок замера.
     */
    public <T> T применить(java.util.concurrent.Callable<T> работа) throws Exception {
        Вариант прежний = ТЕКУЩИЙ.get();
        ТЕКУЩИЙ.set(this);
        try {
            return работа.call();
        } finally {
            if (прежний == null) {
                ТЕКУЩИЙ.remove();
            } else {
                ТЕКУЩИЙ.set(прежний);
            }
        }
    }

    /**
     * ПОДПИСЬ ДЛЯ КЛЮЧЕЙ КЭША. Свод и контент кэшируются на процесс по ключу,
     * и без подписи варианта второй вариант получил бы правила первого.
     */
    public String подпись() {
        StringBuilder sb = new StringBuilder();
        sb.append(папкаПолей == null ? "" : папкаПолей).append('|');
        for (Map.Entry<String, Object> e : правкиСвода.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        return sb.toString();
    }

    /** Подпись варианта на этом потоке (пустая строка, если варианта нет). */
    public static String подписьТекущего() {
        Вариант в = ТЕКУЩИЙ.get();
        return в == null ? "" : в.подпись();
    }

    /**
     * Разбор из строки вида
     * {@code имя:поля=old;правило=ключ=значение;скорость=infantry:2;цель=aircraft:buildings_towers}.
     * Разделитель частей — точка с запятой, чтобы запятая осталась свободной
     * для перечисления вариантов в одной строке запуска.
     */
    public static Вариант разобрать(String строка) {
        String[] части = строка.split(";");
        String имя = части[0].trim();
        String поля = null;
        Map<String, Object> правки = new LinkedHashMap<>();
        Map<String, Object> скорости = new LinkedHashMap<>();
        Map<String, String> цели = new LinkedHashMap<>();
        for (int i = 1; i < части.length; i++) {
            String кусок = части[i].trim();
            if (кусок.isEmpty()) {
                continue;
            }
            int знак = кусок.indexOf('=');
            if (знак <= 0) {
                throw new IllegalArgumentException("вариант «" + имя + "»: непонятно «" + кусок + "»");
            }
            String вид = кусок.substring(0, знак).trim();
            String значение = кусок.substring(знак + 1).trim();
            switch (вид) {
                case "поля" -> поля = значение;
                case "правило" -> {
                    int р = значение.indexOf('=');
                    if (р <= 0) {
                        throw new IllegalArgumentException(
                            "вариант «" + имя + "»: правило ждёт вид ключ=значение, а было «" + значение + "»");
                    }
                    правки.put(значение.substring(0, р).trim(),
                        разобратьЗначение(значение.substring(р + 1).trim()));
                }
                case "скорость" -> {
                    int д = значение.indexOf(':');
                    скорости.put(значение.substring(0, д).trim(),
                        Integer.parseInt(значение.substring(д + 1).trim()));
                }
                case "цель" -> {
                    int д = значение.indexOf(':');
                    цели.put(значение.substring(0, д).trim(), значение.substring(д + 1).trim());
                }
                default -> throw new IllegalArgumentException(
                    "вариант «" + имя + "»: неизвестная часть «" + вид + "»");
            }
        }
        return new Вариант(имя, поля, правки, скорости, цели);
    }

    /** Число, булево или строка — в том же порядке, что и у правок запуска. */
    private static Object разобратьЗначение(String значение) {
        if ("true".equalsIgnoreCase(значение) || "false".equalsIgnoreCase(значение)) {
            return Boolean.parseBoolean(значение);
        }
        try {
            return значение.contains(".")
                ? (Object) Double.parseDouble(значение) : (Object) Integer.parseInt(значение);
        } catch (NumberFormatException нет) {
            return значение;
        }
    }
}
