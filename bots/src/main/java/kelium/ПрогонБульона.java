package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import kelium.agents.ОбученныйСтратег;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.ContentLibrary;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.engine.cards.CardRegistry;

/**
 * ПРОГОН БУЛЬОНА (Карты 2.0, 30.09.2026) — живые партии стратегов на колодах,
 * собранных из случайных кандидатов бульона ({@link Бульон}). Одна партия
 * даёт сведения сразу о десятках карт, поэтому сотни кандидатов меряются
 * тысячами, а не сотнями тысяч партий.
 *
 * <p>Что пишется по каждой карте задания: сколько раз была в руке, сколько раз
 * выполнена, в каком раунде, отрыв держателя в конце партии (выполнил / не
 * выполнил). По каждой карте арсенала: сколько раз установлена, сколько раз
 * сработала. По парам заданий — сколько раз выполнены В ОДНОМ ХОДУ одним
 * игроком: это и есть связка за ход, измеренная игрой, а не придуманная.
 *
 * <p>Награда кандидата на время прогона — ровная: ветка из двух на выбор (пара
 * берётся от номера карты, одна и та же в каждой партии), без дополнительной.
 * Так прогон меряет ТРУДНОСТЬ требования; цену награды набор получит потом —
 * по измеренной трудности.
 *
 * <p>Запуск: {@code kelium.ПрогонБульона [партий] [потоков] [папка бульона]}.
 */
public final class ПрогонБульона {

    private ПрогонБульона() {
    }

    private static final List<String> ВЕТКИ = List.of("mining", "build_miner", "energy_swap",
        "build_plant", "assembly", "build_military", "movement", "combat", "market", "science");
    private static final List<String> УТИЛЬ = List.of("ДВИЖЕНИЕ", "БОЙ", "НАУКА", "РЫНОК",
        "ДОБЫЧА", "МОНЕТА", "БОЕПРИПАС", "СКОРОСТЬ", "РЕМОНТ_ГЕКСА", "ДВИЖЕНИЕ_ДВУМЯ");

    /** Счётчики по карте. */
    static final class Счёт {
        int вРуке;
        int выполнено;
        double раундСумма;
        double отрывВыполнил;
        double отрывНе;
        int установлено;
        int сработало;
    }

    static final Map<String, Счёт> СЧЁТ = new ConcurrentHashMap<>();
    /** Имя файла итогов в папке бульона. */
    static String ИТОГ = "прогон.md";
    static final Map<String, Integer> ПАРЫ = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int потоков = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        Path папка = Path.of(args.length > 2 ? args[2] : "design-docs/фигуры/бульон");
        // второй и следующие прогоны — на других раздачах и в свой файл;
        // сборка набора складывает все файлы прогон*.md
        long базаСидов = args.length > 3 ? Long.parseLong(args[3]) : 9_000_000L;
        ИТОГ = args.length > 4 ? args[4] : "прогон.md";
        org.yaml.snakeyaml.LoaderOptions lo = new org.yaml.snakeyaml.LoaderOptions();
        lo.setMaxAliasesForCollections(Integer.MAX_VALUE);
        org.yaml.snakeyaml.Yaml y = new org.yaml.snakeyaml.Yaml(lo);
        List<Map<String, Object>> задания = y.load(Files.readString(папка.resolve("задания.yaml")));
        List<Map<String, Object>> арсенал = y.load(Files.readString(папка.resolve("арсенал.yaml")));
        GameConfig база = GameConfig.build(4, 1L);
        ContentSet основаЗаданий = база.content.get("objectives");
        ContentSet основаАрсенала = база.content.get("arsenal");
        List<Map<String, Object>> верхиАрсенала = new ArrayList<>();
        for (Map<String, Object> e : основаАрсенала.entries) {
            if ("regular".equals(e.get("kind")) && e.get("top") instanceof Map<?, ?> top) {
                верхиАрсенала.add((Map<String, Object>) top);
            }
        }
        System.out.printf("прогон бульона: %d партий, %d потоков; заданий %d, срабатываний %d;"
            + " стратег %s%n", партий, потоков, задания.size(), арсенал.size(),
            ОбученныйСтратег.есть(4) ? "обученный" : "без сети (гроссмейстер)");

        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        List<Future<?>> ff = new ArrayList<>();
        AtomicInteger сделано = new AtomicInteger();
        long t0 = System.currentTimeMillis();
        for (int g = 0; g < партий; g++) {
            final long seed = базаСидов + g;
            ff.add(пул.submit(() -> {
                партия(seed, задания, арсенал, верхиАрсенала, база, основаЗаданий, основаАрсенала);
                int n = сделано.incrementAndGet();
                if (n % 10 == 0) {
                    System.out.printf("  партий %d/%d · %.0f мин%n", n, партий,
                        (System.currentTimeMillis() - t0) / 60000.0);
                }
                return null;
            }));
        }
        for (Future<?> f : ff) {
            try {
                f.get();
            } catch (Exception e) {
                System.out.println("  партия сорвалась: " + e.getCause());
            }
        }
        пул.shutdown();
        записать(папка, задания, арсенал);
    }

    /** Запись кандидата-задания для колоды: язык + ровная награда + утиль. */
    private static Map<String, Object> заданиеВКолоду(Map<String, Object> к) {
        String id = String.valueOf(к.get("id"));
        int h = Math.floorMod(id.hashCode(), 1_000_003);
        String a = ВЕТКИ.get(h % ВЕТКИ.size());
        String b = ВЕТКИ.get((h / 10 + 1 + h % ВЕТКИ.size()) % ВЕТКИ.size());
        if (a.equals(b)) {
            b = ВЕТКИ.get((ВЕТКИ.indexOf(a) + 3) % ВЕТКИ.size());
        }
        Map<String, Object> язык = new LinkedHashMap<>();
        язык.put("имя", "Кандидат " + id);
        язык.put("требование", к.get("требование"));
        язык.put("награда", Map.of("действие", a + "|" + b));
        язык.put("верх", УТИЛЬ.get(h % УТИЛЬ.size()));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("язык", язык);
        return e;
    }

    /** Запись кандидата-арсенала: срабатывание + верх одной из действующих карт. */
    private static Map<String, Object> арсеналВКолоду(Map<String, Object> к,
                                                      List<Map<String, Object>> верхи) {
        String id = String.valueOf(к.get("id"));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("name", "Кандидат " + id);
        e.put("kind", "regular");
        e.put("top", верхи.get(Math.floorMod(id.hashCode(), верхи.size())));
        e.put("bottom", к.get("низ"));
        e.put("описание", к.get("текст"));
        return e;
    }

    private static void партия(long seed, List<Map<String, Object>> задания,
                               List<Map<String, Object>> арсенал, List<Map<String, Object>> верхи,
                               GameConfig база, ContentSet основаЗаданий, ContentSet основаАрсенала) {
        Random r = new Random(seed);
        List<Map<String, Object>> колодаЗ = new ArrayList<>();
        for (Map<String, Object> e : основаЗаданий.entries) {
            if ("starting".equals(e.get("kind"))) {
                колодаЗ.add(new LinkedHashMap<>(e));
            }
        }
        for (int i : выборка(задания.size(), 40, r)) {
            колодаЗ.add(заданиеВКолоду(задания.get(i)));
        }
        List<Map<String, Object>> колодаА = new ArrayList<>();
        for (Map<String, Object> e : основаАрсенала.entries) {
            if ("starting".equals(e.get("kind"))) {
                колодаА.add(new LinkedHashMap<>(e));
            }
        }
        for (int i : выборка(арсенал.size(), 33, r)) {
            колодаА.add(арсеналВКолоду(арсенал.get(i), верхи));
        }
        CardRegistry.bindAll("objectives", колодаЗ);
        Map<String, ContentSet> наборы = new LinkedHashMap<>(база.content.sets);
        наборы.put("objectives", new ContentSet("objectives", основаЗаданий.version, колодаЗ,
            основаЗаданий.raw, основаЗаданий.sourcePath));
        наборы.put("arsenal", new ContentSet("arsenal", основаАрсенала.version, колодаА,
            основаАрсенала.raw, основаАрсенала.sourcePath));
        GameConfig cfg = new GameConfig(база.ruleset, new ContentLibrary(наборы), 4, seed,
            база.dataRoot, база.boardSides);
        GameState s = Setup.buildGame(LayoutLibrary.configFor(cfg, 4, seed));
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(ОбученныйСтратег.создать(kelium.agents.Bots.ROSTER_4.get(i), i,
                new Random(seed * 31 + i), 4));
        }
        Map<Integer, Set<String>> держал = new HashMap<>();
        Map<String, Integer> выполнилРаунд = new HashMap<>();
        Map<String, Integer> выполнилМесто = new HashMap<>();
        Map<String, List<String>> заХод = new HashMap<>();
        GameEngine.playGame(s, agents, ev -> {
            String тип = String.valueOf(ev.get("type"));
            if ("turn_end".equals(тип)) {
                for (int k = 0; k < 4; k++) {
                    держал.computeIfAbsent(k, x -> new HashSet<>()).addAll(s.player(k).objectiveHand);
                }
            } else if ("objective".equals(тип) && ev.get("seat") instanceof Integer место) {
                String c = String.valueOf(ev.get("card"));
                выполнилРаунд.put(c, s.round);
                выполнилМесто.put(c, место);
                держал.computeIfAbsent(место, x -> new HashSet<>()).add(c);
                заХод.computeIfAbsent(s.round + ":" + s.circle + ":" + место,
                    x -> new ArrayList<>()).add(c);
            } else if ("arsenal".equals(тип) && "install".equals(ev.get("mode"))) {
                СЧЁТ.computeIfAbsent(String.valueOf(ev.get("card")), x -> new Счёт()).установлено++;
            } else if ("card_trigger".equals(тип)) {
                СЧЁТ.computeIfAbsent(String.valueOf(ev.get("card")), x -> new Счёт()).сработало++;
            }
        });
        double[] итог = ЦиклСтратега.итоги(s);
        for (var e : держал.entrySet()) {
            for (String c : e.getValue()) {
                Счёт сч = СЧЁТ.computeIfAbsent(c, x -> new Счёт());
                synchronized (сч) {
                    сч.вРуке++;
                    Integer место = выполнилМесто.get(c);
                    if (место != null && место.equals(e.getKey())) {
                        сч.выполнено++;
                        сч.раундСумма += выполнилРаунд.get(c);
                        сч.отрывВыполнил += итог[e.getKey()];
                    } else {
                        сч.отрывНе += итог[e.getKey()];
                    }
                }
            }
        }
        for (List<String> ход : заХод.values()) {
            for (int i = 0; i < ход.size(); i++) {
                for (int k = i + 1; k < ход.size(); k++) {
                    String а = ход.get(i);
                    String б = ход.get(k);
                    String ключ = а.compareTo(б) < 0 ? а + "+" + б : б + "+" + а;
                    ПАРЫ.merge(ключ, 1, Integer::sum);
                }
            }
        }
    }

    private static List<Integer> выборка(int всего, int сколько, Random r) {
        List<Integer> все = new ArrayList<>();
        for (int i = 0; i < всего; i++) {
            все.add(i);
        }
        java.util.Collections.shuffle(все, r);
        return все.subList(0, Math.min(сколько, всего));
    }

    private static void записать(Path папка, List<Map<String, Object>> задания,
                                 List<Map<String, Object>> арсенал) throws Exception {
        StringBuilder sb = new StringBuilder("# Прогон бульона — задания\n\n"
            + "| id | текст | в руке | выполнено | доля | средний раунд | отрыв выполнившего |"
            + " отрыв не выполнившего |\n|---|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> к : задания) {
            Счёт с = СЧЁТ.get(String.valueOf(к.get("id")));
            if (с == null || с.вРуке == 0) {
                continue;
            }
            sb.append(String.format(java.util.Locale.ROOT, "| %s | %s | %d | %d | %.2f | %.1f | %+.2f | %+.2f |%n",
                к.get("id"), к.get("текст"), с.вРуке, с.выполнено, с.выполнено / (double) с.вРуке,
                с.выполнено == 0 ? 0 : с.раундСумма / с.выполнено,
                с.выполнено == 0 ? 0 : 10 * с.отрывВыполнил / с.выполнено,
                с.вРуке == с.выполнено ? 0 : 10 * с.отрывНе / (с.вРуке - с.выполнено)));
        }
        sb.append("\n# Прогон бульона — арсенал\n\n| id | текст | установлено | сработало |"
            + " срабатываний на установку |\n|---|---|---|---|---|\n");
        for (Map<String, Object> к : арсенал) {
            Счёт с = СЧЁТ.get(String.valueOf(к.get("id")));
            if (с == null || с.установлено == 0) {
                continue;
            }
            sb.append(String.format(java.util.Locale.ROOT, "| %s | %s | %d | %d | %.2f |%n",
                к.get("id"), к.get("текст"), с.установлено, с.сработало,
                с.сработало / (double) с.установлено));
        }
        sb.append("\n# Пары заданий, выполненные в одном ходу\n\n| пара | раз |\n|---|---|\n");
        ПАРЫ.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(60)
            .forEach(e -> sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue())
                .append(" |\n"));
        Files.writeString(папка.resolve(ИТОГ), sb.toString(), StandardCharsets.UTF_8);
        System.out.println("записано: " + папка.resolve(ИТОГ));
    }
}
