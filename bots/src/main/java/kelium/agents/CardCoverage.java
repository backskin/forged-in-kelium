package kelium.agents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.ContentLibrary;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ПОКРЫТИЕ КАРТ НА РЕАЛЬНЫХ ПАРТИЯХ (заказ дизайнера 19.08.2026: «проверь, что
 * КАЖДАЯ карта арсенала и КАЖДАЯ карта задания ВЫПОЛНИМА, прям на реальных
 * партиях; если что-то невыполнимо — это проблема кода»).
 *
 * <p>Инструмент НЕ проверяет правила на бумаге и не разбирает условия карт
 * разумом — он играет партии и смотрит, что произошло. Карта считается
 * доказанно играбельной, только если её видели В ДЕЛЕ:
 *
 * <ul>
 *   <li>задание — событие {@code objective} с этим {@code card} (выполнено);</li>
 *   <li>арсенал — событие {@code arsenal} с {@code mode=install} либо
 *       {@code install_mandate} (карта установлена и работает).</li>
 * </ul>
 *
 * <p>Отдельно считается ПОПАДАНИЕ В РУКИ: задание — сколько раз пришло и
 * сколько раз сожжено, арсенал — сколько раз взято с витрины. Это разделяет два
 * совершенно разных диагноза, которые легко спутать: «карта невыполнима» и
 * «карта просто не попадалась». Без этого разделения редкая карта, ни разу не
 * пришедшая за прогон, выглядела бы сломанной.
 *
 * <p>Взятие с витрины видно по телеметрии обмена науки {@code
 * draw_arsenal:<cid>} — до 19.08.2026 этот путь получения карты не оставлял в
 * событиях никакого следа, и «попадала ли карта в игру вообще» было нечем
 * измерить (см. {@code Actions.takeFromArsenalDisplay}).
 */
public final class CardCoverage {

    private CardCoverage() {
    }

    /** Счётчики по одной карте. */
    private static final class Tally {
        long dealt;       // пришло в руку / взято с витрины
        long done;        // выполнено (задание) / установлено (арсенал)
        long burned;      // сожжено (только задания)
    }

    public static void main(String[] args) throws IOException {
        int games = 400;
        long seed = 1;
        Path out = Path.of("reports", "balance", "покрытие-карт.md");
        String botMemory = "data/genomes";

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--games" -> games = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--botmemory" -> botMemory = args[++i];
                default -> { }
            }
        }
        // Системное свойство, а не Locations.setBotMemory: тот пишет в
        // постоянные Preferences (реестр Windows) и переживает выход процесса.
        System.setProperty("kelium.botmemory", botMemory);

        List<String> pool = new ArrayList<>();
        for (BotCatalog.Entry e : BotCatalog.ALL) {
            if (!"random".equals(e.id())) {
                pool.add(e.id());
            }
        }

        Map<String, Tally> objectives = new TreeMap<>();
        Map<String, Tally> arsenal = new TreeMap<>();
        long totalGames = 0;

        for (int g = 0; g < games; g++) {
            long gameSeed = seed * 1_000_003L + g;
            int players = 2 + (g % 3);   // 2, 3 и 4 игрока — разные партии
            Random pickRng = new Random(gameSeed);

            GameConfig cfg = LayoutLibrary.configFor(players, gameSeed);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < players; seat++) {
                String spec = pool.get(pickRng.nextInt(pool.size()));
                agents.add(BotCatalog.create(spec, seat,
                    new Random(gameSeed * 131 + seat * 17 + 3), players));
            }

            GameEngine.playGame(s, agents, ev -> {
                String type = String.valueOf(ev.get("type"));
                String card = ev.get("card") instanceof String c ? c : null;
                switch (type) {
                    case "objective" -> {
                        if (card != null) {
                            objectives.computeIfAbsent(card, k -> new Tally()).done++;
                        }
                    }
                    case "objective_burn" -> {
                        if (card != null) {
                            objectives.computeIfAbsent(card, k -> new Tally()).burned++;
                        }
                    }
                    case "objective_flood", "objective_drawn" -> {
                        if (card != null) {
                            objectives.computeIfAbsent(card, k -> new Tally()).dealt++;
                        }
                    }
                    case "arsenal_flood" -> {
                        if (card != null) {
                            arsenal.computeIfAbsent(card, k -> new Tally()).dealt++;
                        }
                    }
                    case "arsenal" -> {
                        String mode = String.valueOf(ev.get("mode"));
                        if (card != null
                                && ("install".equals(mode) || "install_mandate".equals(mode))) {
                            arsenal.computeIfAbsent(card, k -> new Tally()).done++;
                        }
                    }
                    // ВЗЯТИЕ С ВИТРИНЫ — в телеметрии действия науки.
                    case "action" -> {
                        if (!(ev.get("telemetry") instanceof Map<?, ?> tm)) {
                            return;
                        }
                        Object ex = tm.get("exchange");
                        if (ex == null) {
                            return;
                        }
                        for (String part : String.valueOf(ex).split("\\+")) {
                            if (part.startsWith("draw_arsenal:")) {
                                arsenal.computeIfAbsent(part.substring("draw_arsenal:".length()),
                                    k -> new Tally()).dealt++;
                            }
                        }
                    }
                    default -> { }
                }
            });
            totalGames++;

            // Карты, взятые и оставшиеся в руке к концу партии, тоже «попали в
            // игру»: их отсутствие среди установленных — это не «не пришла».
            for (int seat = 0; seat < players; seat++) {
                for (String cid : s.player(seat).arsenalHand) {
                    arsenal.computeIfAbsent(cid, k -> new Tally());
                }
            }

            if ((g + 1) % 50 == 0) {
                System.out.println("партий: " + (g + 1) + "/" + games);
            }
        }

        // ПОЛНЫЙ СПИСОК КАРТ ИЗ КАТАЛОГА, а не только встреченные: карта,
        // которой ни разу не было, — главный подозреваемый, и потерять её из
        // отчёта нельзя.
        ContentLibrary lib = LayoutLibrary.configFor(3, 1).content;
        List<String> allObjectives = idsOf(lib, "objectives");
        List<String> allArsenal = idsOf(lib, "arsenal");

        StringBuilder md = new StringBuilder();
        md.append("# Покрытие карт на реальных партиях\n\n");
        md.append("Свод по умолчанию, состав ботов из `").append(botMemory)
            .append("`, партий: **").append(totalGames)
            .append("** (по 2, 3 и 4 игрока).\n\n");
        md.append("Карта считается доказанно играбельной, только если её видели "
            + "в деле: задание — выполнено, арсенал — установлен. Столбец "
            + "«пришло» отделяет «карта сломана» от «карта просто не "
            + "попадалась».\n\n");

        section(md, "Задания", allObjectives, objectives, true);
        section(md, "Арсенал", allArsenal, arsenal, false);

        // СРАБАТЫВАЕТ ЛИ НАВЕДЕНИЕ — в отчёт и в консоль: без этой строки нельзя
        // отличить «наводка не нашла лучшего выбора» от «нашла и проиграла
        // жадной оценке», а лечится это по-разному.
        String targeting = kelium.engine.ObjectiveTargeting.countersLine();
        System.out.println(targeting);
        md.append("Наведение внутри действия: ").append(targeting).append("\n\n");

        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static List<String> idsOf(ContentLibrary lib, String type) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> e : lib.get(type).entries) {
            Object id = e.get("id");
            if (id != null) {
                out.add(String.valueOf(id));
            }
        }
        return out;
    }

    private static void section(StringBuilder md, String title, List<String> all,
                               Map<String, Tally> tally, boolean burns) {
        List<String> never = new ArrayList<>();
        List<String> dealtButNever = new ArrayList<>();
        Map<String, Tally> ordered = new LinkedHashMap<>();
        for (String id : all) {
            Tally t = tally.getOrDefault(id, new Tally());
            ordered.put(id, t);
            if (t.done == 0) {
                // «КАРТУ ВИДЕЛИ» — это не только «раздали»: сожжённая карта
                // тоже побывала на руках. Считать иначе значит записать
                // сгоревшую сотню раз карту в «не попадалась» и искать
                // несуществующую причину.
                if (t.dealt > 0 || t.burned > 0) {
                    dealtButNever.add(id);
                } else {
                    never.add(id);
                }
            }
        }

        md.append("## ").append(title).append("\n\n");
        md.append("Карт в каталоге: **").append(all.size()).append("**, из них ")
            .append(burns ? "ни разу не выполнено" : "ни разу не установлено")
            .append(": **").append(never.size() + dealtButNever.size()).append("**.\n\n");

        if (!dealtButNever.isEmpty()) {
            md.append("### ПОДОЗРЕВАЕМЫЕ: карта в игру попадала, но так и не сработала\n\n");
            md.append("Это и есть возможная поломка кода — карта на руках была, "
                + "а результата нет ни в одной партии.\n\n");
            for (String id : dealtButNever) {
                md.append("- `").append(id).append("` — пришло ")
                    .append(ordered.get(id).dealt).append(" раз");
                if (burns) {
                    md.append(", сожжено ").append(ordered.get(id).burned);
                }
                md.append('\n');
            }
            md.append('\n');
        }
        if (!never.isEmpty()) {
            md.append("### НЕ ПОПАДАЛИСЬ ВООБЩЕ (диагноз отложен: мало партий или карта недоступна)\n\n");
            for (String id : never) {
                md.append("- `").append(id).append("`\n");
            }
            md.append('\n');
        }

        md.append("### Полная таблица\n\n");
        md.append(burns
            ? "| Карта | Пришло | Выполнено | Сожжено | Доля толку |\n|---|---|---|---|---|\n"
            : "| Карта | Взято | Установлено | Доля толку |\n|---|---|---|---|\n");
        for (var e : ordered.entrySet()) {
            Tally t = e.getValue();
            double rate = t.dealt == 0 ? 0 : 100.0 * t.done / t.dealt;
            if (burns) {
                md.append(String.format(Locale.ROOT, "| `%s` | %d | %d | %d | %.0f%% |%n",
                    e.getKey(), t.dealt, t.done, t.burned, rate));
            } else {
                md.append(String.format(Locale.ROOT, "| `%s` | %d | %d | %.0f%% |%n",
                    e.getKey(), t.dealt, t.done, rate));
            }
        }
        md.append('\n');
    }
}
