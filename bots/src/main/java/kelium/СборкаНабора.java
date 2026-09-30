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
    static final int МИН_В_РУКЕ = ПРОБА ? 1 : 6;
    static final double ДОЛЯ_МИН = ПРОБА ? 0.0 : 0.08;
    static final double ДОЛЯ_МАКС = ПРОБА ? 1.0 : 0.85;
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
        сработавшие.removeIf(e -> !ПРОБА && (e.getValue()[0] < 2 || e.getValue()[1] == 0));
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
        if (args.length > 1 && "--выгрузить".equals(args[1])) {
            выгрузить(набор, заданиеПоId, арсНабор, арсеналПоId);
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
                          List<String> арсНабор, Map<String, Map<String, Object>> арсеналПоId)
            throws Exception {
        org.yaml.snakeyaml.Yaml y = new org.yaml.snakeyaml.Yaml();
        Map<String, Object> старыеЗадания = y.load(Files.readString(Path.of("data/cards/objectives.1.20.0.yaml")));
        Map<String, Object> старыйАрсенал = y.load(Files.readString(Path.of("data/cards/arsenal.7.4.0.yaml")));
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
                язык.put("сверх", Map.of("спецДействий", м.доля() < 0.25 ? 3 : 2, "картыАрсенала", 1));
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
            язык.put("награда", награда);
            язык.put("верх", верхРазвилки(р, номер));
            язык.put("значок", р);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", String.format("z2_%02d", номер++));
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
            e.put("id", String.format("a8_%02d", номер));
            e.put("name", имяСрабатывания(а));
            e.put("kind", "regular");
            e.put("значок", развилкаСрабатывания(а));
            e.put("top", старыеВерхи.get((номер - 1) % старыеВерхи.size()));
            e.put("bottom", а.get("низ"));
            e.put("описание", а.get("текст"));
            арсенал.add(e);
            номер++;
        }
        for (Map<String, Object> e : (List<Map<String, Object>>) старыйАрсенал.get("arsenal")) {
            if ("starting".equals(e.get("kind"))) {
                арсенал.add(e);
            }
        }
        org.yaml.snakeyaml.DumperOptions o = new org.yaml.snakeyaml.DumperOptions();
        o.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        o.setAllowUnicode(true);
        o.setWidth(120);
        org.yaml.snakeyaml.Yaml д = new org.yaml.snakeyaml.Yaml(o);
        Map<String, Object> фЗ = new LinkedHashMap<>();
        фЗ.put("meta", Map.of("id", "2.0.0", "type", "objectives"));
        фЗ.put("objectives", задания);
        Files.writeString(Path.of("data/cards/objectives.2.0.0.yaml"),
            "# 2.0.0 (Карты 2.0): задания языком карт, собраны kelium.СборкаНабора из бульона\n"
                + "# по мере живых партий стратегов. Начальные — как в 1.20.0.\n"
                + "# CONTENT: objectives  version 2.0.0\n" + д.dump(копияБезЯкорей(фЗ)),
            StandardCharsets.UTF_8);
        Map<String, Object> фА = new LinkedHashMap<>();
        фА.put("meta", Map.of("id", "8.0.0", "type", "arsenal"));
        фА.put("arsenal", арсенал);
        Files.writeString(Path.of("data/cards/arsenal.8.0.0.yaml"),
            "# 8.0.0 (Карты 2.0): низ — срабатывания данными, верх — утили арсенала 7.4.0;\n"
                + "# собран kelium.СборкаНабора из бульона. Начальные — как в 7.4.0.\n"
                + "# CONTENT: arsenal  version 8.0.0\n" + д.dump(копияБезЯкорей(фА)),
            StandardCharsets.UTF_8);
        String свод = Files.readString(Path.of("data/rulesets/1.46.0.yaml"), StandardCharsets.UTF_8)
            .replaceFirst("(?m)^  id: 1\\.46\\.0", "  id: 1.47.0")
            .replaceFirst("(?m)^  objectives: 1\\.20\\.0.*$",
                "  objectives: 2.0.0             # КАРТЫ 2.0 (30.09.2026): задания языком карт из бульона")
            .replaceFirst("(?m)^  arsenal: 7\\.4\\.0.*$",
                "  arsenal: 8.0.0                # КАРТЫ 2.0 (30.09.2026): срабатывания данными из бульона");
        Files.writeString(Path.of("data/rulesets/1.47.0.yaml"),
            "# 1.47.0 (30.09.2026): как 1.46.0, колоды Карт 2.0 — задания 2.0.0 и арсенал 8.0.0.\n" + свод,
            StandardCharsets.UTF_8);
        System.out.printf("выгружено: заданий %d, арсенала %d, свод 1.47.0%n", задания.size(),
            арсенал.size());
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
            default -> что;
        };
        Map<String, Object> п = эф.get("params") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String итог = switch (String.valueOf(эф.get("effect"))) {
            case "спец" -> "спец-действие";
            case "heal_one" -> "ремонт";
            case "free_action" -> kelium.cards.язык.Требование.ветка(String.valueOf(п.get("action")))
                .replace("«", "").replace("»", "");
            default -> п.containsKey("coin") ? "монеты" : п.containsKey("ammo") ? "боеприпас"
                : п.containsKey("kelium") ? "келемий" : п.containsKey("trophy") ? "трофей"
                : п.containsKey("objective_cards") ? "задание" : "добро";
        };
        return событие + " → " + итог;
    }

    /** Имя карты — строго по сути требования, без придуманных слов. */
    @SuppressWarnings("unchecked")
    private static String имя(Map<String, Object> т) {
        String узел = String.valueOf(т.get("узел"));
        Object n = т.getOrDefault("сколько", т.getOrDefault("гексов", т.get("k")));
        String число = n == null ? "" : " ×" + n;
        return switch (узел) {
            case "жетоны" -> кто((Map<String, Object>) т.get("группа")) + число;
            case "рядом" -> кто((Map<String, Object>) т.get("а")) + " у врага" + число;
            case "ресурс" -> "Запас" + число;
            case "арсенал" -> "Арсенал" + число;
            case "свалка" -> "Свалка" + число;
            case "ветки" -> "Две ветки";
            case "обе_ветки" -> "Обе ветки";
            case "очередь_задания" -> "Задание подряд";
            case "очередь_спец" -> "Спец-действие подряд";
            case "сожги" -> "Сожги" + число;
            case "установи" -> "Установи";
            case "уничтожь" -> "Уничтожь" + число;
            case "построй" -> "Построй" + число;
            case "найми" -> "Найми" + число;
            default -> узел;
        };
    }

    private static String кто(Map<String, Object> г) {
        String к = String.valueOf(г.get("кто"));
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

    /** Печатный утиль по развилке карты. */
    private static String верхРазвилки(String р, int номер) {
        return switch (р) {
            case "extract" -> "ДОБЫЧА";
            case "power" -> "ЭНЕРГИЯ_ИЛИ_МОДУЛИ";
            case "supply" -> "СНАРЯЖЕНИЕ";
            case "command" -> номер % 2 == 0 ? "ДВИЖЕНИЕ" : "АТАКА_ОДНИМ_ВОЙСКОМ";
            default -> номер % 2 == 0 ? "РЫНОК" : "НАУКА";
        };
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
