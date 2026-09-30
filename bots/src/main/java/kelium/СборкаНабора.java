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

    static final int МИН_В_РУКЕ = 6;
    static final double ДОЛЯ_МИН = 0.08;
    static final double ДОЛЯ_МАКС = 0.85;
    static final int ЗАДАНИЙ = 40;
    static final int АРСЕНАЛА = 33;
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
        List<String> строки = Files.readAllLines(папка.resolve("прогон.md"), StandardCharsets.UTF_8);
        List<Мера> меры = new ArrayList<>();
        Map<String, int[]> срабатывания = new HashMap<>();
        Map<String, Integer> пары = new HashMap<>();
        int раздел = 0;
        for (String с : строки) {
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
                меры.add(new Мера(ч[1].trim(), ч[2].trim(), целое(ч[3]), целое(ч[4]), дробь(ч[5]),
                    дробь(ч[6]), дробь(ч[7]), дробь(ч[8])));
            } else if (раздел == 2 && ч.length >= 5) {
                срабатывания.put(ч[1].trim(), new int[]{целое(ч[3]), целое(ч[4])});
            } else if (раздел == 3 && ч.length >= 3) {
                пары.put(ч[1].trim(), целое(ч[2]));
            }
        }

        // ---------------- задания ----------------
        Map<String, List<Мера>> поРазвилке = new LinkedHashMap<>();
        for (String р : List.of("extract", "power", "supply", "command", "develop")) {
            поРазвилке.put(р, new ArrayList<>());
        }
        List<Мера> годные = new ArrayList<>();
        for (Мера м : меры) {
            if (м.вРуке() >= МИН_В_РУКЕ && м.доля() >= ДОЛЯ_МИН && м.доля() <= ДОЛЯ_МАКС) {
                годные.add(м);
                String р = развилкаТребования(заданиеПоId.get(м.id()));
                поРазвилке.getOrDefault(р, поРазвилке.get("command")).add(м);
            }
        }
        List<Мера> набор = new ArrayList<>();
        int наРазвилку = ЗАДАНИЙ / поРазвилке.size();
        for (var e : поРазвилке.entrySet()) {
            List<Мера> список = e.getValue();
            // разброс трудности: по порядку доли выполнения, берём равномерно
            список.sort(Comparator.comparingDouble(Мера::доля));
            int n = Math.min(наРазвилку, список.size());
            for (int i = 0; i < n; i++) {
                набор.add(список.get((int) Math.round(i * (список.size() - 1) / Math.max(1.0, n - 1))));
            }
        }
        // связки: пары, выполненные в одном ходу, — обе карты в набор, если годны
        List<String> связки = new ArrayList<>();
        пары.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(20).forEach(e -> {
            String[] ab = e.getKey().split("\\+");
            if (ab.length == 2) {
                связки.add(ab[0] + " + " + ab[1] + " (" + e.getValue() + ")");
                for (String id : ab) {
                    годные.stream().filter(м -> м.id().equals(id)).findFirst().ifPresent(м -> {
                        if (!набор.contains(м) && набор.size() < ЗАДАНИЙ + 6) {
                            набор.add(м);
                        }
                    });
                }
            }
        });

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
        сработавшие.removeIf(e -> e.getValue()[0] < 2 || e.getValue()[1] == 0);
        сработавшие.sort((a, b) -> Double.compare(
            b.getValue()[1] / (double) b.getValue()[0], a.getValue()[1] / (double) a.getValue()[0]));
        for (var e : сработавшие) {
            Map<String, Object> а = арсеналПоId.get(e.getKey());
            if (а != null) {
                арсПоРазвилке.get(развилкаСрабатывания(а)).add(e.getKey());
            }
        }
        List<String> арсНабор = new ArrayList<>();
        int редких = 0;
        int наКорзину = АРСЕНАЛА / 5;
        for (var e : арсПоРазвилке.entrySet()) {
            int взято = 0;
            for (String id : e.getValue()) {
                if (взято >= наКорзину || арсНабор.size() >= АРСЕНАЛА) {
                    break;
                }
                boolean редкое = редкое(арсеналПоId.get(id));
                if (редкое && редких >= РЕДКИХ) {
                    continue;
                }
                арсНабор.add(id);
                взято++;
                if (редкое) {
                    редких++;
                }
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
    }

    /** Награда по трудности: чем реже выполняют, тем жирнее — вплоть до спец-действия. */
    static String награда(double доля) {
        if (доля >= 0.5) {
            return "ветка на выбор";
        }
        if (доля >= 0.25) {
            return "ветка на выбор и 2 монеты";
        }
        return "ветка на выбор и спец-действие";
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
            case "уничтожь", "свалка", "рядом" -> "command";
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
            default -> "карты";
        };
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
