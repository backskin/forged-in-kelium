package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * СКОЛЬКО БЫ ШЛА ПАРТИЯ У ЖИВЫХ ИГРОКОВ — оценка от числа РЕШЕНИЙ.
 *
 * <p>ЗАЧЕМ ИМЕННО ТАК. «Партия идёт часа два» — это чувство, а не число, и
 * проверить его нечем. Но время за столом складывается из вещей, которые
 * СЧИТАЮТСЯ: сколько раз игрок принимает решение, из скольких вариантов
 * выбирает, сколько ходов делает и сколько раундов живёт партия. Всё это движок
 * знает точно — он сам эти решения и спрашивает.
 *
 * <p>ЧТО ИЗМЕРЕНО, А ЧТО ПРЕДПОЛОЖЕНО. Измерено: число решений, размер выбора,
 * ходы, раунды — по настоящим партиям. Предположены ТОЛЬКО секунды на одно
 * решение и накладные расходы за ход и раунд; они собраны в {@link Скорость} и
 * названы прямо, чтобы их можно было оспорить, не разбирая остальной счёт.
 *
 * <p>ВАЖНАЯ ПОПРАВКА — ПАРАЛЛЕЛЬНОЕ ДУМАНЬЕ. За столом игроки думают, пока ходит
 * сосед: к моменту своего хода решение часто уже готово. Поэтому время партии —
 * это НЕ сумма времени всех игроков. Учитывается доля решений, которые
 * действительно приходятся на «свою» очередь (см. {@code перекрытие}).
 *
 * <p>Запуск: {@code kelium.ВремяПартии [партий] [свод]}
 */
public final class ВремяПартии {

    private ВремяПартии() {
    }

    /**
     * СКОРОСТЬ ИГРОКОВ — три профиля. Числа в секундах, и это ПРЕДПОЛОЖЕНИЯ.
     *
     * @param имя         как называть профиль в отчёте
     * @param простое     решение из 2–3 вариантов: куда положить кубик, платить ли
     * @param среднее     решение из 4–8 вариантов: какое действие, какой гекс
     * @param сложное     решение из девяти и более вариантов: приказ, цель боя
     * @param заХод       накладные за ход: взять жетоны, подвинуть, посчитать
     * @param заРаунд     накладные за раунд: вскрытие, Обновление, Возврат
     * @param перекрытие  какая доля решений успевает продуматься в чужой ход
     */
    private record Скорость(String имя, double простое, double среднее, double сложное,
                            double заХод, double заРаунд, double перекрытие) {
    }

    private static final List<Скорость> СКОРОСТИ = List.of(
        new Скорость("опытные, играют быстро", 12, 24, 48, 40, 180, 0.55),
        new Скорость("обычная компания", 23, 46, 92, 83, 345, 0.35),
        new Скорость("новички, разбираются на ходу", 48, 108, 210, 180, 720, 0.15));

    /** Подготовка стола и разбор в конце, минуты — тоже предположение. */
    private static final Map<Integer, Double> ПОДГОТОВКА =
        Map.of(2, 8.0, 3, 11.0, 4, 14.0);

    /** Агент-счётчик: считает решения и размер выбора, решает как обёрнутый. */
    private static final class Счётчик extends Agent {
        private final Agent под;
        long простых;
        long средних;
        long сложных;
        final Map<String, Long> поВидам = new TreeMap<>();
        /**
         * РЕШЕНИЯ ПО РАУНДАМ: раунд → [простых, средних, сложных].
         *
         * <p>Без разбивки по раундам средняя длина хода врёт в обе стороны: в
         * первом раунде на поле почти ничего нет и выбор беден, к восьмому у
         * игрока десяток жетонов, три планшета и полная рука — это совсем другой
         * ход по времени.
         */
        final Map<Integer, long[]> поРаундам = new TreeMap<>();

        Счётчик(Agent под) {
            super(под.seat, "счётчик·" + под.name);
            this.под = под;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            int n = options == null ? 0 : options.size();
            // РЕШЕНИЕМ СЧИТАЕТСЯ ТОЛЬКО НАСТОЯЩИЙ ВЫБОР. Когда вариант один,
            // человек его не обдумывает — он его просто делает, и время уходит
            // не на решение, а на движение рукой (это в накладных за ход).
            if (n >= 2) {
                long[] раунд = поРаундам.computeIfAbsent(
                    Math.max(1, state.round), k -> new long[3]);
                if (n <= 3) {
                    простых++;
                    раунд[0]++;
                } else if (n <= 8) {
                    средних++;
                    раунд[1]++;
                } else {
                    сложных++;
                    раунд[2]++;
                }
                String вид = ctx == null ? "?" : String.valueOf(ctx.getOrDefault("kind", "?"));
                поВидам.merge(вид, 1L, Long::sum);
            }
            return под.choose(state, options, ctx);
        }
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        String ruleset = args.length > 1 ? args[1] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        StringBuilder b = new StringBuilder();
        b.append("# Сколько шла бы партия у живых игроков\n\n");
        b.append("Свод **").append(ruleset).append("**, по **").append(games)
            .append("** партий на каждый состав.\n\n");
        b.append("ЧТО ИЗМЕРЕНО: число решений игрока, размер выбора, ходы, раунды — ")
            .append("по настоящим партиям, движок сам эти решения и спрашивает.\n\n");
        b.append("ЧТО ПРЕДПОЛОЖЕНО: секунды на решение и накладные за ход и раунд. ")
            .append("Эти числа названы прямо и их можно оспорить отдельно от счёта.\n\n");

        Map<Integer, double[]> замер = new TreeMap<>();
        Map<Integer, Map<String, Long>> видыРешений = new TreeMap<>();
        Map<Integer, int[]> раундыПоСоставу = new TreeMap<>();
        Map<Integer, Map<Integer, double[]>> поРаундамСостава = new TreeMap<>();
        for (int players = 2; players <= 4; players++) {
            double простых = 0;
            double средних = 0;
            double сложных = 0;
            double ходов = 0;
            double раундов = 0;
            int[] раундыВсех = new int[games];
            Map<String, Long> виды = new TreeMap<>();
            // РАУНД → [простых, средних, сложных, ходов]: по ним считается длина
            // ОДНОГО хода в круге, а она сильно разная в начале и в конце партии.
            Map<Integer, double[]> поРаундам = new TreeMap<>();
            for (int g = 0; g < games; g++) {
                GameConfig cfg = GameConfig.buildCached(ruleset, players,
                    91000L + g, null, null);
                GameState s = Setup.buildGame(cfg);
                List<Agent> ags = new ArrayList<>();
                List<Счётчик> счётчики = new ArrayList<>();
                int shift = g % players;
                for (int i = 0; i < players; i++) {
                    Счётчик c = new Счётчик(kelium.agents.BotCatalog.create(
                        пул.get((i + shift) % players), i, new Random(i * 97L + g), players));
                    счётчики.add(c);
                    ags.add(c);
                }
                long[] ходыСчёт = {0};
                GameEngine.playGame(s, ags, ev -> {
                    if ("turn_orders".equals(String.valueOf(ev.get("type")))) {
                        ходыСчёт[0]++;
                        поРаундам.computeIfAbsent(Math.max(1, s.round),
                            k -> new double[4])[3]++;
                    }
                });
                for (Счётчик c : счётчики) {
                    простых += c.простых;
                    средних += c.средних;
                    сложных += c.сложных;
                    c.поВидам.forEach((k, v) -> виды.merge(k, v, Long::sum));
                    c.поРаундам.forEach((раунд, счёт) -> {
                        double[] сюда = поРаундам.computeIfAbsent(раунд, k -> new double[4]);
                        сюда[0] += счёт[0];
                        сюда[1] += счёт[1];
                        сюда[2] += счёт[2];
                    });
                }
                ходов += ходыСчёт[0];
                раундов += s.round;
                раундыВсех[g] = s.round;
            }
            замер.put(players, new double[]{простых / games, средних / games,
                сложных / games, ходов / games, раундов / games});
            видыРешений.put(players, виды);
            раундыПоСоставу.put(players, раундыВсех);
            поРаундамСостава.put(players, поРаундам);
        }

        b.append("## Что происходит за партию (замер, на всю партию целиком)\n\n");
        b.append("| игроков | раундов | ходов | простых решений | средних | сложных | всего решений |\n");
        b.append("|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var e : замер.entrySet()) {
            double[] v = e.getValue();
            b.append("| ").append(e.getKey()).append(" | ").append(округл(v[4]))
                .append(" | ").append(округл(v[3])).append(" | ").append(округл(v[0]))
                .append(" | ").append(округл(v[1])).append(" | ").append(округл(v[2]))
                .append(" | ").append(округл(v[0] + v[1] + v[2])).append(" |\n");
        }
        b.append("\nРешением считается только настоящий выбор — из двух вариантов и ")
            .append("больше. Там, где вариант один, человек не думает, а просто делает; ")
            .append("это время сидит в накладных за ход.\n");

        b.append("\n## Предположения о людях\n\n");
        b.append("| профиль | простое решение | среднее | сложное | за ход | за раунд | думают в чужой ход |\n");
        b.append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (Скорость c : СКОРОСТИ) {
            b.append("| ").append(c.имя()).append(" | ").append((int) c.простое())
                .append(" с | ").append((int) c.среднее()).append(" с | ")
                .append((int) c.сложное()).append(" с | ").append((int) c.заХод())
                .append(" с | ").append((int) c.заРаунд()).append(" с | ")
                .append(Math.round(c.перекрытие() * 100)).append(" % |\n");
        }
        b.append("\nПодготовка стола и разбор: ");
        for (var e : new TreeMap<>(ПОДГОТОВКА).entrySet()) {
            b.append(e.getKey()).append(" игрока — ").append((int) (double) e.getValue())
                .append(" мин; ");
        }
        b.append("\n");

        b.append("\n## Сколько это в минутах\n\n");
        b.append("| игроков | профиль | партия целиком | без подготовки |\n");
        b.append("|---:|---|---:|---:|\n");
        Map<Integer, Map<String, Double>> итоги = new TreeMap<>();
        for (var e : замер.entrySet()) {
            int players = e.getKey();
            double[] v = e.getValue();
            itogi(итоги, players);
            for (Скорость c : СКОРОСТИ) {
                double минут = минуты(v, c, players);
                итоги.get(players).put(c.имя(), минут);
                b.append("| ").append(players).append(" | ").append(c.имя())
                    .append(" | **").append(округл(минут + ПОДГОТОВКА.get(players)))
                    .append(" мин** | ").append(округл(минут)).append(" мин |\n");
            }
        }

        b.append("\n## Диапазон: самая короткая и самая длинная партия\n\n");
        b.append("Считано по САМЫМ КОРОТКИМ и САМЫМ ДЛИННЫМ партиям замера ")
            .append("(длина партии в раундах пляшет вдвое), с быстрым и медленным ")
            .append("профилем соответственно.\n\n");
        b.append("| игроков | самая быстрая партия | обычная | самая долгая |\n");
        b.append("|---:|---:|---:|---:|\n");
        for (var e : замер.entrySet()) {
            int players = e.getKey();
            double[] v = e.getValue();
            int[] rr = раундыПоСоставу.get(players);
            int[] s = rr.clone();
            java.util.Arrays.sort(s);
            double минР = s[0];
            double серР = v[4];
            double максР = s[s.length - 1];
            double быстро = минуты(масштаб(v, минР / серР), СКОРОСТИ.get(0), players)
                + ПОДГОТОВКА.get(players);
            double обычно = минуты(v, СКОРОСТИ.get(1), players) + ПОДГОТОВКА.get(players);
            double долго = минуты(масштаб(v, максР / серР), СКОРОСТИ.get(2), players)
                + ПОДГОТОВКА.get(players);
            b.append("| ").append(players).append(" | ").append(округл(быстро))
                .append(" мин (").append((int) минР).append(" раунда) | ")
                .append(округл(обычно)).append(" мин (").append(округл(серР))
                .append(" раунда) | ").append(округл(долго)).append(" мин (")
                .append((int) максР).append(" раундов) |\n");
        }

        b.append("\n## Один ход игрока по раундам\n\n");
        b.append("Ход — это четверть раунда, ход в круге: за раунд каждый игрок ")
            .append("ходит четыре раза. Здесь длина ОДНОГО такого хода.\n\n")
            .append("Накладные за раунд (вскрытие, Обновление, Возврат) сюда НЕ ")
            .append("входят: они общие на раунд, а не на ход.\n");
        for (var состав : поРаундамСостава.entrySet()) {
            int players = состав.getKey();
            b.append("\n### На ").append(players).append(" игрока\n\n");
            b.append("| раунд | ходов в раунде, шт | решений на один ход, шт | ")
                .append("опытные, мин:сек | обычная компания, мин:сек | ")
                .append("новички, мин:сек |\n");
            b.append("|---:|---:|---:|---:|---:|---:|\n");
            for (var e : состав.getValue().entrySet()) {
                double[] v = e.getValue();
                double ходов = Math.max(1, v[3]);
                b.append("| ").append(e.getKey()).append(" | ")
                    .append(округл(ходов / games)).append(" | ")
                    .append(округл((v[0] + v[1] + v[2]) / ходов)).append(" | ");
                for (Скорость c : СКОРОСТИ) {
                    // Накладные за ход входят, за раунд — нет: раунд общий.
                    double сек = (v[0] / ходов * c.простое()
                        + v[1] / ходов * c.среднее()
                        + v[2] / ходов * c.сложное()) * (1 - c.перекрытие())
                        + c.заХод();
                    b.append(минСек(сек)).append(" | ");
                }
                b.append("\n");
            }
        }

        b.append("\n## На что уходят решения\n\n");
        b.append("Виды решений, которых больше всего, — это и есть то, что стоит ")
            .append("упрощать, если партия кажется долгой.\n\n");
        for (var e : видыРешений.entrySet()) {
            b.append("\nНа ").append(e.getKey()).append(" игроков:\n\n");
            b.append("| решение | за партию | доля |\n|---|---:|---:|\n");
            long всего = e.getValue().values().stream().mapToLong(Long::longValue).sum();
            e.getValue().entrySet().stream()
                .sorted((x, y) -> Long.compare(y.getValue(), x.getValue()))
                .limit(12)
                .forEach(r -> b.append("| ").append(видРешения(r.getKey()))
                    .append(" | ").append(округл(r.getValue() / (double) games))
                    .append(" | ").append(String.format("%.0f%%", 100.0 * r.getValue() / всего))
                    .append(" |\n"));
        }

        Path out = Path.of("reports", "balance", "время-партии.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println(b);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static void itogi(Map<Integer, Map<String, Double>> m, int players) {
        m.computeIfAbsent(players, k -> new TreeMap<>());
    }

    /** Тот же замер, растянутый на другую длину партии. */
    private static double[] масштаб(double[] v, double k) {
        return new double[]{v[0] * k, v[1] * k, v[2] * k, v[3] * k, v[4] * k};
    }

    /**
     * Минуты на партию.
     *
     * <p>Решения всех игроков складываются, но с поправкой на параллельное
     * думанье: доля {@code перекрытие} обдумывается в чужой ход и времени стола
     * не занимает. Накладные за ход и за раунд идут полностью — их не
     * распараллелить.
     */
    private static double минуты(double[] v, Скорость c, int players) {
        double решения = v[0] * c.простое() + v[1] * c.среднее() + v[2] * c.сложное();
        double сек = решения * (1 - c.перекрытие()) + v[3] * c.заХод() + v[4] * c.заРаунд();
        return сек / 60.0;
    }

    private static String видРешения(String вид) {
        return switch (вид) {
            case "reveal_order" -> "какой приказ вскрыть";
            case "action" -> "какое действие сыграть";
            case "build_pick" -> "что строить";
            case "build_hex" -> "куда ставить здание";
            case "build_facing" -> "как повернуть здание";
            case "assemble" -> "войско или боеприпас в Снаряжении";
            case "move" -> "кем и куда двигаться";
            case "combat_source" -> "откуда бить";
            case "combat_target" -> "по какому гексу бить";
            case "attack" -> "чем именно атаковать";
            case "mine" -> "что берёт добытчик";
            case "market_deal" -> "какая сделка на рынке";
            case "sci_track" -> "какой трек науки";
            case "sci_exchange" -> "какой обмен науки";
            case "spec" -> "спец-действие";
            case "energy_hex" -> "куда двигать энергию";
            case "pay_power" -> "платить ли за энергию";
            case "module_place_red", "module_place_blue" -> "куда класть жетон модуля";
            case "trophy_pay" -> "чем платить за науку";
            case "container" -> "открывать ли контейнер";
            case "arsenal_replace" -> "что снять с планшета арсенала";
            case "?" -> "прочее (без пометки)";
            default -> вид;
        };
    }

    /** Длительность как мин:сек — чтобы в таблице не было чисел без единиц. */
    private static String минСек(double сек) {
        long всего = Math.round(сек);
        return String.format("%d:%02d", всего / 60, всего % 60);
    }

    private static String округл(double v) {
        return String.format("%.1f", v).replace(',', '.');
    }
}
