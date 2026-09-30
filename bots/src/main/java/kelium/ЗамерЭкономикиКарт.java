package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.agents.ОбученныйСтратег;
import kelium.agents.сеть.Сеть;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.ContentLibrary;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.dataio.Вариант;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ЭКОНОМИКА КАРТ (Карты 2.0, 30.09.2026) — сколько карт реально играется за
 * партию и что меняют рычаги, от которых зависит поток карт.
 *
 * <p>Замер прогона бульона: карт арсенала ставят меньше одной на игрока за
 * партию. С таким потоком карты не могут быть главной частью игры, как бы
 * хороши они ни были.
 *
 * <p>РЫЧАГИ — САМИ КАРТЫ, НЕ ПРАВИЛА. «Выполнение задания без спец-действия»
 * дизайнер закрыл 23.09.2026 как бездну (бесконечный ход), поэтому здесь его
 * нет. Проверяется то, что предложил сам дизайнер 30.09: награда-спец-
 * действие за задание и карта арсенала за усиленное задание — связки
 * случаются, когда выпали нужные карты, а не каждый ход. Два спец-действия
 * за ход — только для сравнения, это правило целиком.
 *
 * <p>Партии — одни и те же раздачи во всех вариантах, стратеги с одной сетью
 * (читается один раз в начале). Меры: выполненные задания и установленный
 * арсенал на игрока, ходы с двумя картами и больше (связка за ход),
 * срабатывания, спец-действия, длина партии и разброс очков.
 *
 * <p>Запуск: {@code kelium.ЗамерЭкономикиКарт [партий на вариант] [потоков]}.
 */
public final class ЗамерЭкономикиКарт {

    private ЗамерЭкономикиКарт() {
    }

    /** Вариант: имя, правки свода, правки наград заданий. */
    record ВариантКарт(String имя, Вариант правила, int спецЗаЗадание, int арсеналЗаУсиление) {
    }

    private static final List<ВариантКарт> ВАРИАНТЫ = List.of(
        new ВариантКарт("как сейчас", Вариант.обычный("как сейчас"), 0, 0),
        new ВариантКарт("два спец-действия за ход (правило, для сравнения)",
            Вариант.разобрать("два спец;правило=actions.spec_per_turn=2"), 0, 0),
        new ВариантКарт("задание даёт ещё спец-действие", Вариант.обычный("спец"), 1, 0),
        new ВариантКарт("усиленное задание даёт карту арсенала", Вариант.обычный("арсенал"), 0, 1),
        new ВариантКарт("и спец-действие, и карта арсенала", Вариант.обычный("оба"), 1, 1));

    /** Итог одной партии одного варианта. */
    record Итог(double заданий, double арсенала, double связокЗаХод, double срабатываний,
                double спец, int раундов, double разброс) {
    }

    public static void main(String[] args) throws Exception {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int потоков = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        Сеть сеть = Files.exists(ОбученныйСтратег.файл()) ? Сеть.загрузить(ОбученныйСтратег.файл()) : null;
        System.out.printf("экономика карт: %d партий на вариант, %d потоков, стратег %s%n", партий,
            потоков, сеть != null ? "обученный" : "без сети");
        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        Map<String, List<Future<Итог>>> ff = new LinkedHashMap<>();
        for (ВариантКарт в : ВАРИАНТЫ) {
            List<Future<Итог>> список = new ArrayList<>();
            for (int g = 0; g < партий; g++) {
                final long seed = 8_300_000L + g;
                список.add(пул.submit(() -> в.правила().применить(() -> партия(seed, сеть, в))));
            }
            ff.put(в.имя(), список);
        }
        StringBuilder sb = new StringBuilder("# Экономика карт — рычаги потока карт на одних раздачах\n\n"
            + "Стратеги (одна сеть), " + партий + " раздач на вариант. Всё — средние на игрока за"
            + " партию, кроме раундов и разброса очков (лучший минус худший). «Задание без"
            + " спец-действия» не проверяется: закрыто дизайнером 23.09 как бездна.\n\n"
            + "| вариант | заданий выполнено | арсенала установлено | ходов со связкой (2+ карты)"
            + " | срабатываний | спец-действий | раундов | разброс очков |\n"
            + "|---|---|---|---|---|---|---|---|\n");
        for (var e : ff.entrySet()) {
            double[] сумма = new double[7];
            int n = 0;
            for (Future<Итог> f : e.getValue()) {
                Итог и;
                try {
                    и = f.get();
                } catch (Exception ex) {
                    System.out.println("  партия сорвалась: " + ex.getCause());
                    continue;
                }
                сумма[0] += и.заданий();
                сумма[1] += и.арсенала();
                сумма[2] += и.связокЗаХод();
                сумма[3] += и.срабатываний();
                сумма[4] += и.спец();
                сумма[5] += и.раундов();
                сумма[6] += и.разброс();
                n++;
            }
            n = Math.max(1, n);
            sb.append(String.format(java.util.Locale.ROOT,
                "| %s | %.2f | %.2f | %.2f | %.2f | %.1f | %.1f | %.1f |%n", e.getKey(), сумма[0] / n,
                сумма[1] / n, сумма[2] / n, сумма[3] / n, сумма[4] / n, сумма[5] / n, сумма[6] / n));
            System.out.println("  вариант готов: " + e.getKey());
        }
        пул.shutdown();
        Path out = Path.of("design-docs/фигуры/экономика карт.md");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println(sb);
    }

    /**
     * Настройка партии с правкой наград: каждой обычной карте задания — ещё
     * {@code спец} спец-действий в награду, и {@code арсенал} карт арсенала в
     * усиленную. Записи копируются: общий кэш настроек не трогается.
     */
    @SuppressWarnings("unchecked")
    private static GameConfig настройка(long seed, ВариантКарт в) {
        GameConfig база = LayoutLibrary.configFor(4, seed);
        if (в.спецЗаЗадание() == 0 && в.арсеналЗаУсиление() == 0) {
            return база;
        }
        ContentSet задания = база.content.get("objectives");
        List<Map<String, Object>> копии = new ArrayList<>();
        for (Map<String, Object> e : задания.entries) {
            Map<String, Object> к = new LinkedHashMap<>(e);
            if ("regular".equals(e.get("kind"))) {
                if (в.спецЗаЗадание() > 0) {
                    Map<String, Object> н = e.get("base_reward") instanceof Map<?, ?> m
                        ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
                    н.merge("spec_actions", в.спецЗаЗадание(),
                        (a, b) -> ((Number) a).intValue() + ((Number) b).intValue());
                    к.put("base_reward", н);
                }
                if (в.арсеналЗаУсиление() > 0 && e.get("enhanced") != null) {
                    Map<String, Object> с = e.get("special_reward") instanceof Map<?, ?> m
                        ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
                    с.merge("arsenal", в.арсеналЗаУсиление(),
                        (a, b) -> ((Number) a).intValue() + ((Number) b).intValue());
                    к.put("special_reward", с);
                }
            }
            копии.add(к);
        }
        Map<String, ContentSet> наборы = new LinkedHashMap<>(база.content.sets);
        наборы.put("objectives", new ContentSet("objectives", задания.version, копии, задания.raw,
            задания.sourcePath));
        GameConfig cfg = new GameConfig(база.ruleset, new ContentLibrary(наборы), 4, seed,
            база.dataRoot, база.boardSides);
        return LayoutLibrary.configFor(cfg, 4, seed);
    }

    private static Итог партия(long seed, Сеть сеть, ВариантКарт в) {
        GameState s = Setup.buildGame(настройка(seed, в));
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(ЦиклСтратега.стратег(Bots.ROSTER_4.get(i), i, new Random(seed * 31 + i), сеть,
                сеть == null ? 0 : 1.0));
        }
        int[] заданий = new int[1];
        int[] арсенала = new int[1];
        int[] срабатываний = new int[1];
        int[] связок = new int[1];
        int[] спец = new int[1];
        Map<String, Integer> карт = new LinkedHashMap<>();
        GameEngine.playGame(s, agents, ev -> {
            String тип = String.valueOf(ev.get("type"));
            Object место = ev.get("seat");
            String ход = s.round + ":" + s.circle + ":" + место;
            switch (тип) {
                case "objective" -> {
                    заданий[0]++;
                    карт.merge(ход, 1, Integer::sum);
                }
                case "arsenal" -> {
                    if ("install".equals(ev.get("mode"))) {
                        арсенала[0]++;
                        карт.merge(ход, 1, Integer::sum);
                    } else if ("burn".equals(ev.get("mode"))) {
                        карт.merge(ход, 1, Integer::sum);
                    }
                }
                case "objective_burn" -> карт.merge(ход, 1, Integer::sum);
                case "card_trigger" -> срабатываний[0]++;
                case "turn_end" -> {
                    if (место instanceof Integer m && s.journal != null) {
                        спец[0] += s.journal.of(m).спецИспользовано;
                    }
                }
                default -> { }
            }
        });
        for (int c : карт.values()) {
            if (c >= 2) {
                связок[0]++;
            }
        }
        int мин = Integer.MAX_VALUE;
        int макс = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            int v = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
            мин = Math.min(мин, v);
            макс = Math.max(макс, v);
        }
        return new Итог(заданий[0] / 4.0, арсенала[0] / 4.0, связок[0] / 4.0, срабатываний[0] / 4.0,
            спец[0] / 4.0, s.round, макс - мин);
    }
}
