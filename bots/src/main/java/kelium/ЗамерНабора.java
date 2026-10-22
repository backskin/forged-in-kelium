package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;
import kelium.engine.cards.ObjectiveCard;

/**
 * ПРОВЕРКА НАБОРА (Карты 2.0, 30.09.2026) — старые и новые колоды на одних
 * раздачах, играют стратеги с одной сетью.
 *
 * <p>Главное мерило — РАЗНООБРАЗИЕ СТРАТЕГИЙ: у каждого игрока его «дорога» —
 * развилка, к которой относится большинство выполненных им заданий (по
 * действию, которым задание закрывают). Для победителей считается доля каждой
 * дороги: хороший набор — когда побеждают разными дорогами и ни одна не
 * забирает больше порога. Рядом — сколько карт играется, связки за ход,
 * срабатывания арсенала.
 *
 * <p>Запуск: {@code kelium.ЗамерНабора [партий] [потоков] [свод …]}.
 */
public final class ЗамерНабора {

    private ЗамерНабора() {
    }

    record Итог(double заданий, double арсенала, double связок, double срабатываний,
                String дорогаПобедителя, double урона, double уничтожено, boolean воинПобедил,
                double сухихБоёв, double сухихБезБоеприпасов, double сухихСКарты,
                double уронаЗолотом, double уронаСупер, Map<String, int[]> ветки,
                double связокСоСрабатываниями) {
    }

    public static void main(String[] args) throws Exception {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int потоков = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        List<String> своды = args.length > 2 ? List.of(args).subList(2, args.length)
            : List.of("1.46.0", "1.47.0");
        Сеть сеть = Files.exists(ОбученныйСтратег.файл()) ? Сеть.загрузить(ОбученныйСтратег.файл()) : null;
        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        Map<String, List<Future<Итог>>> ff = new LinkedHashMap<>();
        for (String свод : своды) {
            List<Future<Итог>> список = new ArrayList<>();
            for (int g = 0; g < партий; g++) {
                // -Dkelium.замер.сид — другие раздачи: отличить шум от сдвига
                final long seed = Long.getLong("kelium.замер.сид", 8_700_000L) + g;
                final int номер = g;
                список.add(пул.submit(() -> партия(свод, seed, сеть, номер)));
            }
            ff.put(свод, список);
        }
        StringBuilder sb = new StringBuilder("# Проверка набора — старые и новые колоды\n\n"
            + партий + " раздач на свод, стратеги с одной сетью. Дорога победителя — развилка"
            + " большинства выполненных им заданий.\n\n| свод | заданий на игрока | арсенала на игрока"
            + " | ходов со связкой на игрока (со срабатываниями) | срабатываний на игрока | урона нанесено на игрока (золотым модулем / супер-войском)"
            + " | уничтожено жетонов на игрока | победил самый воинственный"
            + " | сухих боёв на игрока (без боеприпасов / с карты) | дороги победителей |\n"
            + "|---|---|---|---|---|---|---|---|---|---|\n");
        Map<String, Map<String, int[]>> пустые = new LinkedHashMap<>();
        for (var e : ff.entrySet()) {
            double[] с = new double[13];
            Map<String, Integer> дороги = new java.util.TreeMap<>();
            int n = 0;
            for (Future<Итог> f : e.getValue()) {
                Итог и;
                try {
                    и = f.get();
                } catch (Exception ex) {
                    System.out.println("  партия сорвалась: " + ex.getCause());
                    continue;
                }
                // ХОД ЗАМЕРА В ОКНЕ (Влад 01.10: «было видно прогресс») — каждые 10 партий
                if ((n + 1) % 10 == 0 || n + 1 == e.getValue().size()) {
                    System.out.printf("  %s: партий %d из %d%n", e.getKey(), n + 1, e.getValue().size());
                }
                с[0] += и.заданий();
                с[1] += и.арсенала();
                с[2] += и.связок();
                с[12] += и.связокСоСрабатываниями();
                с[3] += и.срабатываний();
                с[4] += и.урона();
                с[5] += и.уничтожено();
                с[6] += и.воинПобедил() ? 1 : 0;
                с[7] += и.сухихБоёв();
                с[8] += и.сухихБезБоеприпасов();
                с[9] += и.сухихСКарты();
                с[10] += и.уронаЗолотом();
                с[11] += и.уронаСупер();
                for (var в : и.ветки().entrySet()) {
                    int[] x = пустые.computeIfAbsent(e.getKey(), k -> new java.util.TreeMap<>())
                        .computeIfAbsent(в.getKey(), k -> new int[2]);
                    x[0] += в.getValue()[0];
                    x[1] += в.getValue()[1];
                }
                дороги.merge(и.дорогаПобедителя(), 1, Integer::sum);
                n++;
            }
            n = Math.max(1, n);
            // ДОРОГА С ПОПРАВКОЙ НА КОЛОДУ (01.10.2026): дорога — значок большинства
            // выполненных заданий, и развилка с 9 картами в колоде «побеждает» чаще
            // развилки с 4 без всякой силы. В скобках — доля побед на долю карт
            // этой развилки в колоде: 1 — честно, больше — сильнее колоды.
            Map<String, Double> колода = долиКолоды(e.getKey());
            StringBuilder д = new StringBuilder();
            for (var x : дороги.entrySet()) {
                double доля = (double) x.getValue() / n;
                Double вКолоде = колода.get(x.getKey());
                д.append(x.getKey()).append(' ').append(Math.round(100.0 * доля)).append('%');
                if (вКолоде != null && вКолоде > 0) {
                    д.append(String.format(java.util.Locale.ROOT, " (×%.1f)", доля / вКолоде));
                }
                д.append(' ');
            }
            sb.append(String.format(java.util.Locale.ROOT,
                "| %s | %.2f | %.2f | %.2f (%.2f) | %.2f | %.2f (%.2f / %.2f) | %.2f | %.0f%% | %.2f (%.2f / %.2f) | %s |%n",
                e.getKey(), с[0] / n, с[1] / n, с[2] / n, с[12] / n, с[3] / n, с[4] / n, с[10] / n, с[11] / n, с[5] / n,
                100 * с[6] / n, с[7] / n, с[8] / n, с[9] / n, д.toString().trim()));
        }
        пул.shutdown();
        // ПУСТЫЕ ВЕТКИ: игрок выбрал ветку с приказа, а она ничего не сделала —
        // «выбрал действие, а сделать нечего» (Влад, 01.10.2026)
        sb.append("\n## Пустые ветки с приказа (впустую / сыграно, на игрока за партию)\n\n");
        for (var e : пустые.entrySet()) {
            int всего = 0;
            int пусто = 0;
            StringBuilder р = new StringBuilder();
            for (var в : e.getValue().entrySet()) {
                if (в.getKey().startsWith("дорога·") || в.getKey().startsWith("карта·")
                        || в.getKey().startsWith("сжёг·") || в.getKey().startsWith("фракция")
                        || в.getKey().startsWith("бот·") || в.getKey().startsWith("место·")
                        || в.getKey().startsWith("очки·") || в.getKey().startsWith("победитель·")) {
                    continue;                       // доли побед — отдельными разделами
                }
                if (!в.getKey().contains("·")) {     // «mining·причина» — разбивка, не в итог
                    всего += в.getValue()[0];
                    пусто += в.getValue()[1];
                }
                р.append(String.format(java.util.Locale.ROOT, "%s %.2f/%.2f; ", в.getKey(),
                    в.getValue()[1] / (4.0 * партий), в.getValue()[0] / (4.0 * партий)));
            }
            sb.append(String.format(java.util.Locale.ROOT, "- **%s**: впустую %.2f из %.2f (%.0f%%) — %s%n",
                e.getKey(), пусто / (4.0 * партий), всего / (4.0 * партий),
                всего == 0 ? 0.0 : 100.0 * пусто / всего, р.toString().trim()));
        }
        sb.append("\n## Доля побед дороги (побед / игроков этой дороги; ровно — 25%)\n\n");
        for (var e : пустые.entrySet()) {
            StringBuilder р = new StringBuilder();
            for (var в : e.getValue().entrySet()) {
                if (в.getKey().startsWith("дорога·")) {
                    int[] x = в.getValue();
                    р.append(String.format(java.util.Locale.ROOT, "%s %.0f%% (%d из %d); ",
                        в.getKey().substring("дорога·".length()), x[0] == 0 ? 0.0 : 100.0 * x[1] / x[0],
                        x[1], x[0]));
                }
            }
            sb.append("- **").append(e.getKey()).append("**: ").append(р.toString().trim()).append('\n');
        }
        // ФРАКЦИИ, БОТЫ, МЕСТА (02.10.2026): доля побед (ровно — 25%) и средние очки
        sb.append("\n## Фракции, боты, места: доля побед (ровно 25%) и средние очки\n\n");
        for (var e : пустые.entrySet()) {
            sb.append("**").append(e.getKey()).append("**\n\n| срез | сыграно | побед | очки |\n|---|---|---|---|\n");
            for (String приставка : List.of("фракция·", "бот·", "место·", "фракция×бот·")) {
                for (var в : e.getValue().entrySet()) {
                    if (!в.getKey().startsWith(приставка)) {
                        continue;
                    }
                    int[] x = в.getValue();
                    int[] о = e.getValue().getOrDefault("очки·" + в.getKey(), new int[2]);
                    sb.append(String.format(java.util.Locale.ROOT, "| %s | %d | %.0f%% | %.1f |%n",
                        в.getKey(), x[0], 100.0 * x[1] / Math.max(1, x[0]),
                        (double) о[0] / Math.max(1, о[1])));
                }
            }
            // пары «дорога + фракция» победителя, частые сверху (критерий 10: ни одна > 10%)
            List<Map.Entry<String, int[]>> пары = new ArrayList<>();
            for (var в : e.getValue().entrySet()) {
                if (в.getKey().startsWith("победитель·")) {
                    пары.add(в);
                }
            }
            пары.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);
            sb.append("\nПобедитель «дорога · фракция», частые: ");
            for (int i = 0; i < Math.min(6, пары.size()); i++) {
                sb.append(String.format(java.util.Locale.ROOT, "%s %.0f%%; ",
                    пары.get(i).getKey().substring("победитель·".length()),
                    100.0 * пары.get(i).getValue()[0] / Math.max(1, партий)));
            }
            sb.append("\n\n");
        }
        // КАРТЫ АРСЕНАЛА: сколько раз поставлены и доля побед поставивших (ровно 25%).
        // Слишком сильная карта видна сразу; слишком слабая — малой долей или тем,
        // что её почти не ставят.
        sb.append("\n## Карты арсенала: поставлено раз и доля побед поставивших\n\n");
        for (var e : пустые.entrySet()) {
            List<Map.Entry<String, int[]>> карты = new ArrayList<>();
            for (var в : e.getValue().entrySet()) {
                if (в.getKey().startsWith("карта·")) {
                    карты.add(в);
                }
            }
            карты.sort((a, b) -> Double.compare((double) b.getValue()[1] / Math.max(1, b.getValue()[0]),
                (double) a.getValue()[1] / Math.max(1, a.getValue()[0])));
            sb.append("**").append(e.getKey())
                .append("**\n\n| карта | утиль | поставлено | сожжено | побед поставивших |\n|---|---|---|---|---|\n");
            var колода = GameConfig.buildCached(e.getKey(), 4, 1L, null, null).content.get("arsenal");
            for (var в : карты) {
                String id = в.getKey().substring("карта·".length());
                Map<String, Object> карта = колода.find(id);
                String имя = карта == null ? id : id + " " + карта.get("name");
                int[] x = в.getValue();
                int[] сж = e.getValue().getOrDefault("сжёг·" + id, new int[2]);
                Object утиль = карта == null || !(карта.get("top") instanceof Map<?, ?> т) ? "" : т.get("label");
                sb.append(String.format(java.util.Locale.ROOT, "| %s | %s | %d | %d | %.0f%% |%n", имя, утиль,
                    x[0], сж[0], 100.0 * x[1] / Math.max(1, x[0])));
            }
            sb.append('\n');
        }
        // у каждого набора сводов свой файл: два замера разом не затирают друг друга
        String файл = своды.equals(List.of("1.46.0", "1.47.0")) ? "проверка набора.md"
            : "проверка набора (" + String.join(" ", своды) + ").md";
        Files.writeString(Path.of("design-docs/фигуры").resolve(файл), sb.toString(),
            StandardCharsets.UTF_8);
        System.out.println(sb);
    }

    /** Доля обычных заданий колоды по значку развилки (только карты языка). */
    @SuppressWarnings("unchecked")
    private static Map<String, Double> долиКолоды(String свод) {
        Map<String, Double> out = new java.util.TreeMap<>();
        int всего = 0;
        for (Map<String, Object> e : GameConfig.buildCached(свод, 4, 1L, null, null)
                .content.get("objectives").entries) {
            // значок знает сама карта языка: запись колоды движок переписывает без него
            if (CardRegistry.objective(String.valueOf(e.get("id")))
                    instanceof kelium.cards.objectives.ЗаданиеИзЯзыка з && з.значок() != null) {
                out.merge(з.значок(), 1.0, Double::sum);
                всего++;
            }
        }
        final int всегоКарт = всего;
        out.replaceAll((k, v) -> всегоКарт == 0 ? 0 : v / всегоКарт);
        return out;
    }

    /** Главная причина пустой «Добыть» — по телеметрии действия. */
    private static String причинаПустойДобычи(Map<?, ?> t) {
        int добытчиков = число(t, "miners");
        if (добытчиков == 0) {
            return "нет добытчиков";
        }
        if (число(t, "miners_storage_full") > 0) {
            return "склад полон";
        }
        if (число(t, "miners_unpowered") >= добытчиков) {
            return "все без энергии";
        }
        if (число(t, "miners_no_kelium") > 0) {
            return "рядом нет келемия";
        }
        if (число(t, "miners_skipped") > 0) {
            return "пропустил";
        }
        return "прочее";
    }

    private static int число(Map<?, ?> t, String ключ) {
        return t.get(ключ) instanceof Number n ? n.intValue() : 0;
    }

    /** Цвета планшетов войск в порядке свода (место 1 — первый). */
    static final List<String> ЦВЕТА = List.of("blue", "red", "green", "yellow");

    private static Итог партия(String свод, long seed, Сеть сеть, int номер) {
        GameConfig база = GameConfig.buildCached(свод, 4, seed, null, null);
        GameConfig cfg = LayoutLibrary.configFor(база, 4, seed);
        // ФРАКЦИИ — ПЕРЕМЕННАЯ ЗАМЕРА (заказ Влада 02.10.2026). Прежде место 1
        // всегда играло синими строителем, место 4 — жёлтыми карателем, и сила
        // фракции была неотделима от характера бота и очереди хода. Теперь цвет
        // сдвигается по кругу с каждой раздачей, характер бота — своим сдвигом
        // раз в четыре раздачи: за 16 раздач каждая фракция играет за каждого
        // бота и с каждого места. -Dkelium.замер.фракции=нет — как прежде.
        boolean крутить = !"нет".equals(System.getProperty("kelium.замер.фракции", "да"));
        List<String> цвета = new ArrayList<>(ЦВЕТА);
        List<String> боты = new ArrayList<>(Bots.ROSTER_4);
        if (крутить) {
            java.util.Collections.rotate(цвета, номер % 4);
            java.util.Collections.rotate(боты, (номер / 4) % 4);
            GameConfig было = cfg;
            cfg = new GameConfig(было.ruleset, было.content, 4, seed, было.dataRoot, цвета,
                было.scenarioId, было.cuFacing, было.scenarioFile);
            cfg.tokenStatsOverride = было.tokenStatsOverride;
        }
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(ЦиклСтратега.стратег(боты.get(i), i, new Random(seed * 31 + i), сеть,
                сеть == null ? 0 : 1.0));
        }
        int[] заданий = new int[1];
        int[] арсенала = new int[1];
        int[] срабатываний = new int[1];
        Map<String, Integer> карт = new HashMap<>();
        Map<String, Integer> сработало = new HashMap<>();   // срабатывания арсенала по ходам
        Map<Integer, Map<String, Integer>> дороги = new HashMap<>();
        int[] урона = new int[1];
        int[] уничтожено = new int[1];
        int[] убийств = new int[4];
        int[] сухих = new int[3];      // боёв без попадания; из них без боеприпасов; из всех — с карты
        boolean[] сухойЖдёт = new boolean[4];
        int[] особых = new int[2];     // урон с золотого модуля боя; урон супер-войска
        Map<String, int[]> ветки = new java.util.TreeMap<>();   // ветка → {сыграно с приказа, из них впустую}
        // какие карты арсенала поставил каждый игрок — для доли побед по карте
        List<java.util.Set<String>> поставил = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            поставил.add(new java.util.HashSet<>());
        }
        GameEngine.playGame(s, agents, ev -> {
            String тип = String.valueOf(ev.get("type"));
            Object место = ev.get("seat");
            String ход = s.round + ":" + s.circle + ":" + место;
            switch (тип) {
                case "combat_dry" -> {
                    сухих[0]++;
                    if ("нет боеприпасов".equals(ev.get("reason"))) {
                        сухих[1]++;
                    }
                    if (место instanceof Integer m && m >= 0 && m < 4) {
                        сухойЖдёт[m] = true;
                    }
                }
                // событие ветки приходит ПОСЛЕ боя: тогда и видно, чей был сухой бой —
                // с карты (free) или выбранный игроком
                case "action" -> {
                    // ПУСТЫЕ ВЕТКИ С ПРИКАЗА: игрок выбрал ветку, а она ничего не сделала
                    if (!Boolean.TRUE.equals(ev.get("free")) && Boolean.TRUE.equals(ev.get("ok"))) {
                        int[] x = ветки.computeIfAbsent(kelium.engine.Срабатывания.ветка(ev), k -> new int[2]);
                        x[0]++;
                        if (!kelium.engine.Срабатывания.сделала(ev)) {
                            x[1]++;
                            if ("mining".equals(ev.get("action")) && ev.get("telemetry") instanceof Map<?, ?> t) {
                                ветки.computeIfAbsent("mining·" + причинаПустойДобычи(t), k -> new int[2])[1]++;
                            }
                        }
                    }
                    if (место instanceof Integer m && m >= 0 && m < 4 && "combat".equals(ev.get("action"))
                            && сухойЖдёт[m]) {
                        if (Boolean.TRUE.equals(ev.get("free"))) {
                            сухих[2]++;
                        }
                        сухойЖдёт[m] = false;
                    }
                }
                case "combat_hit" -> {
                    урона[0]++;
                    boolean супер = String.valueOf(ev.get("attacker")).contains(".super");
                    if (супер) {
                        особых[1]++;
                    } else if (Boolean.TRUE.equals(ev.get("gold"))) {
                        особых[0]++;
                    }
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        уничтожено[0]++;
                        if (место instanceof Integer m && m >= 0 && m < 4) {
                            убийств[m]++;
                        }
                    }
                }
                case "objective" -> {
                    заданий[0]++;
                    карт.merge(ход, 1, Integer::sum);
                    if (место instanceof Integer m) {
                        ObjectiveCard oc = CardRegistry.objective(String.valueOf(ev.get("card")));
                        // ДОРОГА — ЗНАЧОК РАЗВИЛКИ НА КАРТЕ. У карт языка подсказки
                        // действия для состояний нет (90% уходило в «прочее»), а у
                        // старых она почти всегда «Добыть» — мерило врало в обе стороны.
                        String р;
                        if (oc instanceof kelium.cards.objectives.ЗаданиеИзЯзыка з && з.значок() != null) {
                            р = з.значок();
                        } else {
                            String действие = oc == null ? null : oc.suggestedAction(new EngineCardContext(s, m));
                            р = действие == null ? "прочее"
                                : String.valueOf(kelium.engine.Срабатывания.развилка(действие));
                        }
                        дороги.computeIfAbsent(m, x -> new HashMap<>()).merge(р, 1, Integer::sum);
                    }
                }
                case "arsenal" -> {
                    if ("install".equals(ev.get("mode")) || "burn".equals(ev.get("mode"))) {
                        карт.merge(ход, 1, Integer::sum);
                        if ("install".equals(ev.get("mode"))) {
                            арсенала[0]++;
                            if (место instanceof Integer m && m >= 0 && m < 4) {
                                поставил.get(m).add(String.valueOf(ev.get("card")));
                            }
                        } else {
                            // сожжена ради утиля — судьбу карты решает и верх
                            ветки.computeIfAbsent("сжёг·" + ev.get("card"), k -> new int[2])[0]++;
                        }
                    }
                }
                case "objective_burn" -> карт.merge(ход, 1, Integer::sum);
                case "card_trigger" -> {
                    срабатываний[0]++;
                    сработало.merge(ход, 1, Integer::sum);
                }
                default -> { }
            }
        });
        int связок = 0;
        for (int c : карт.values()) {
            if (c >= 2) {
                связок++;
            }
        }
        // СВЯЗКА СО СРАБАТЫВАНИЯМИ: «задание + сработал арсенал» в один ход — тоже
        // связка, а прежнее мерило считало только разыгранные карты (01.10.2026)
        java.util.Set<String> ходы = new java.util.HashSet<>(карт.keySet());
        ходы.addAll(сработало.keySet());
        int связокПолных = 0;
        for (String х : ходы) {
            if (карт.getOrDefault(х, 0) + сработало.getOrDefault(х, 0) >= 2) {
                связокПолных++;
            }
        }
        String дорога = "без заданий";
        if (s.winner != null && дороги.get(s.winner) != null) {
            дорога = дороги.get(s.winner).entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("без заданий");
        }
        // ДОЛЯ ПОБЕД ПО КАРТЕ АРСЕНАЛА: кто её поставил и победил ли (ключ «карта·id»)
        for (int место = 0; место < 4; место++) {
            for (String id : поставил.get(место)) {
                int[] x = ветки.computeIfAbsent("карта·" + id, k -> new int[2]);
                x[0]++;
                if (s.winner != null && s.winner == место) {
                    x[1]++;
                }
            }
        }
        // ДОЛЯ ПОБЕД ДОРОГИ (01.10.2026): дорога каждого игрока, победил ли он.
        // Лёгкие задания развилки набирают все, и «дорога победителя» тянется к
        // ней без силы; честно — побед дорогой на всех игроков этой дороги (25% — ровно).
        for (int место = 0; место < 4; место++) {
            String д = дороги.get(место) == null ? "без заданий"
                : дороги.get(место).entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("без заданий");
            int[] x = ветки.computeIfAbsent("дорога·" + д, k -> new int[2]);
            x[0]++;
            if (s.winner != null && s.winner == место) {
                x[1]++;
            }
        }
        // самый воинственный — больше всех уничтожил; при равенстве — никто
        int воин = -1;
        for (int i = 0; i < 4; i++) {
            if (убийств[i] > 0 && (воин < 0 || убийств[i] > убийств[воин])) {
                воин = i;
            }
        }
        for (int i = 0; i < 4 && воин >= 0; i++) {
            if (i != воин && убийств[i] == убийств[воин]) {
                воин = -1;
            }
        }
        boolean воинПобедил = воин >= 0 && s.winner != null && s.winner == воин;
        // РАЗНООБРАЗИЕ ПАРТИЙ (критерий 10): пара «дорога + фракция» победителя
        if (s.winner != null && s.winner >= 0 && s.winner < 4) {
            ветки.computeIfAbsent("победитель·" + дорога + "·" + цвета.get(s.winner), k -> new int[2])[0]++;
        }
        // ФРАКЦИЯ, БОТ, МЕСТО: сыграно и побед ({0, 1}); очки — «очки·…» {сумма, партий}
        for (int место = 0; место < 4; место++) {
            boolean победил = s.winner != null && s.winner == место;
            int очки = kelium.engine.Scoring.scorePlayer(s, место).values().stream()
                .mapToInt(Integer::intValue).sum();
            for (String ключ : List.of("фракция·" + цвета.get(место), "бот·" + боты.get(место),
                    "место·" + (место + 1), "фракция×бот·" + цвета.get(место) + "·" + боты.get(место))) {
                int[] x = ветки.computeIfAbsent(ключ, k -> new int[2]);
                x[0]++;
                x[1] += победил ? 1 : 0;
                int[] о = ветки.computeIfAbsent("очки·" + ключ, k -> new int[2]);
                о[0] += очки;
                о[1]++;
            }
        }
        return new Итог(заданий[0] / 4.0, арсенала[0] / 4.0, связок / 4.0, срабатываний[0] / 4.0, дорога,
            урона[0] / 4.0, уничтожено[0] / 4.0, воинПобедил, сухих[0] / 4.0, сухих[1] / 4.0,
            сухих[2] / 4.0, особых[0] / 4.0, особых[1] / 4.0, ветки, связокПолных / 4.0);
    }
}
