package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.BotCatalog;
import kelium.agents.ForcedAgent;
import kelium.agents.Genome;
import kelium.agents.Lookahead;
import kelium.agents.StrategicAgent;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ЕСТЬ ЛИ В ИГРЕ ИГРА — замер интерактивности и цены решений.
 *
 * <p>ВОПРОС, НА КОТОРЫЙ ЭТО ОТВЕЧАЕТ. Настольная игра может оказаться пасьянсом:
 * каждый раскладывает свой движок, соседи не мешают, и лучший ход не зависит от
 * того, что делают другие. Отличить пасьянс от игры нельзя ни описанием правил,
 * ни впечатлением — только замером. Замера три, и каждый отвечает на свой вопрос.
 *
 * <p>1. КАСАНИЯ — как часто чужое решение вообще МЕНЯЕТ мой ход или мой стол.
 * Считаются только настоящие касания: блок при совпадении приказов, открытый
 * чужим вскрытием нижний приказ, бой по моим жетонам, ответный бой, штраф от
 * чужой карты. Это нижняя граница взаимодействия: то, что движок фиксирует
 * событием.
 *
 * <p>2. ЧЬИ РУКИ — разложение результата на три источника. Одна и та же партия
 * играется, меняя (а) только МОЮ политику, (б) только политику СОПЕРНИКОВ,
 * (в) только перемешивание. Разброс итога по каждому источнику и есть ответ:
 * если от чужих политик итог не меняется — игра пасьянс; если от своей не
 * меняется — игрушка, где решения не значат ничего.
 *
 * <p>3. ЦЕНА РЕШЕНИЯ О ПРИКАЗЕ — сколько стоит выбор карты приказа. Копия партии
 * доигрывается до конца с каждой картой руки на входе, и разброс итогов — цена
 * этого решения в очках отрыва. Мерится именно приказ: движок продолжает партию
 * только с начала круга, поэтому это ЕДИНСТВЕННАЯ точка, где доигрывание честно
 * (руки целы, ходы не начаты). Для решений внутри хода тот же приём соврал бы:
 * копия переигрывала бы вскрытие заново.
 *
 * <p>ЧЕСТНАЯ ОГОВОРКА. Играют боты, и они слабее людей. Это занижает пункт 2а
 * («свои руки»): бот не умеет выжимать из решения всё. Пункты 1 и 2б занижены
 * ещё сильнее — бот плохо мешает соперникам нарочно. Поэтому все числа читаются
 * как НИЖНЯЯ ГРАНИЦА взаимодействия, а не как его мера.
 *
 * <p>Запуск: {@code kelium.ТеорияИгры [партий] [свод] [что]}, где «что» —
 * {@code касания|руки|цена|всё}.
 */
public final class ТеорияИгры {

    private ТеорияИгры() {
    }

    private static final List<String> ХАРАКТЕРЫ =
        List.of("builder", "supplier", "stalker", "punisher");

    /**
     * СКОЛЬКО ДОИГРЫВАНИЙ НА ОДНУ КАРТУ в замере цены решения. Восемь — не
     * красивое число, а необходимость: одно доигрывание шумит примерно на 8 ПО,
     * различить карты можно только по средним, а среднее восьми прогонов шумит
     * втрое меньше одиночного.
     */
    private static final int ПРОГОНОВ = 8;

    /** Уровень сложности, на котором ведётся весь замер (3 — сильный бот). */
    private static final String УРОВЕНЬ = ":3";

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        String ruleset = args.length > 1 ? args[1] : GameConfig.DEFAULT_RULESET;
        String что = args.length > 2 ? args[2] : "всё";

        StringBuilder b = new StringBuilder();
        b.append("# Есть ли в игре игра — замер интерактивности\n\n");
        b.append("Свод **").append(ruleset).append("**. Играют боты уровня 3, ")
            .append("места ротируются.\n\n");
        b.append("Все числа — НИЖНЯЯ ГРАНИЦА взаимодействия: боты слабее людей и ")
            .append("почти не мешают соперникам нарочно.\n");

        if (что.equals("всё") || что.equals("касания")) {
            касания(b, games, ruleset);
        }
        if (что.equals("всё") || что.equals("руки")) {
            чьиРуки(b, Math.max(6, games / 4), ruleset);
        }
        if (что.equals("всё") || что.equals("цена")) {
            ценаПриказа(b, Math.max(6, games / 4), ruleset);
        }

        Path out = Path.of("reports", "balance", "теория-игр.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    // ======================== 1. КАСАНИЯ ====================================

    /** Счётчики касаний за прогон. Названия — как в отчёте, чтобы не путать. */
    private static final class Касания {
        double ходов;
        double блок;
        double нижнийОткрыт;
        double штрафСпец;
        double боёвПоИгрокам;
        double боёвПоНейтралам;
        double залповВсухую;
        double уничтоженоЧужих;
        double ответныхЗалпов;
        double трофейныхОчков;
        double партий;
    }

    private static void касания(StringBuilder b, int games, String ruleset) {
        b.append("\n## 1. Касания: как часто чужое решение меняет мой ход\n\n");
        b.append("Ход — четверть раунда. «Блок» — совпал верхний приказ, и ")
            .append("действий стало вдвое меньше. «Нижний открыт» — чужое ")
            .append("вскрытие ПОДАРИЛО мне лишнее действие.\n\n");
        b.append("| игроков | ходов за партию, шт | блок, % ходов | нижний открыт, % ходов | ")
            .append("залпов по игрокам, шт | залпов по нейтралам, шт | всухую, шт | ")
            .append("уничтожено чужих жетонов, шт | ответных залпов, шт |\n");
        b.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");

        for (int players = 2; players <= 4; players++) {
            Касания к = new Касания();
            for (int g = 0; g < games; g++) {
                GameConfig cfg = GameConfig.buildCached(ruleset, players, 7700L + g,
                    null, null);
                GameState s = Setup.buildGame(cfg);
                List<Agent> ags = new ArrayList<>();
                int shift = g % players;
                for (int i = 0; i < players; i++) {
                    ags.add(BotCatalog.create(
                        ХАРАКТЕРЫ.get((i + shift) % players) + УРОВЕНЬ, i,
                        new Random(i * 131L + g), players));
                }
                // КТО СЕЙЧАС ХОДИТ — нужно, чтобы отличить ОТВЕТНЫЙ залп от
                // своего: ответный бой ведёт место, которое не ходит.
                int[] активное = {-1};
                GameEngine.playGame(s, ags, ev -> {
                    String t = String.valueOf(ev.get("type"));
                    switch (t) {
                        case "turn_orders" -> {
                            активное[0] = ((Number) ev.get("seat")).intValue();
                            к.ходов++;
                            if (Boolean.TRUE.equals(ev.get("coincided"))) {
                                к.блок++;
                            }
                            if (Boolean.TRUE.equals(ev.get("bottom_open"))) {
                                к.нижнийОткрыт++;
                            }
                        }
                        case "spec_penalty" -> к.штрафСпец++;
                        // НЕЙТРАЛЫ идут отдельными событиями: в combat_hit их
                        // нет вовсе, и считать их там значило бы вечный ноль.
                        case "damage_neutral", "raze_neutral" -> к.боёвПоНейтралам++;
                        case "combat_dry" -> {
                            к.залповВсухую++;
                            if (место(ev) != активное[0]) {
                                к.ответныхЗалпов++;
                            }
                        }
                        case "combat_hit" -> {
                            int жертва = ev.get("victim_owner") == null ? -1
                                : ((Number) ev.get("victim_owner")).intValue();
                            if (жертва >= 0 && жертва != место(ev)) {
                                к.боёвПоИгрокам++;
                                if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                                    к.уничтоженоЧужих++;
                                    к.трофейныхОчков += ev.get("trophy") == null ? 0
                                        : ((Number) ev.get("trophy")).intValue();
                                }
                            }
                            if (место(ev) != активное[0]) {
                                к.ответныхЗалпов++;
                            }
                        }
                        default -> {
                        }
                    }
                });
                к.партий++;
            }
            double п = Math.max(1, к.партий);
            double х = Math.max(1, к.ходов);
            b.append("| ").append(players)
                .append(" | ").append(ч(к.ходов / п))
                .append(" | ").append(ч(100 * к.блок / х))
                .append(" | ").append(ч(100 * к.нижнийОткрыт / х))
                .append(" | ").append(ч(к.боёвПоИгрокам / п))
                .append(" | ").append(ч(к.боёвПоНейтралам / п))
                .append(" | ").append(ч(к.залповВсухую / п))
                .append(" | ").append(ч(к.уничтоженоЧужих / п))
                .append(" | ").append(ч(к.ответныхЗалпов / п))
                .append(" |\n");
        }
    }

    private static int место(Map<String, Object> ev) {
        return ev.get("seat") == null ? -1 : ((Number) ev.get("seat")).intValue();
    }

    // ======================== 2. ЧЬИ РУКИ ===================================

    private static void чьиРуки(StringBuilder b, int seeds, String ruleset) {
        b.append("\n## 2. Чьи руки: от чего зависит результат\n\n");
        b.append("Мерится ОТРЫВ моего места от сильнейшего соперника (в ПО): ")
            .append("именно отрыв решает партию, а не свои очки.\n\n")
            .append("Три источника разброса. **Свои руки** — та же партия, но я ")
            .append("играю другим характером. **Чужие руки** — я тот же, ")
            .append("соперники другие. **Случай** — все политики те же, другое ")
            .append("перемешивание.\n\n");
        b.append("| игроков | свои руки, σ ПО | чужие руки, σ ПО | случай, σ ПО | ")
            .append("доля своих, % | доля чужих, % | доля случая, % |\n");
        b.append("|---:|---:|---:|---:|---:|---:|---:|\n");

        for (int players = 3; players <= 4; players++) {
            List<Double> дисперсииСвои = new ArrayList<>();
            List<Double> дисперсииЧужие = new ArrayList<>();
            List<Double> базовые = new ArrayList<>();
            for (int s = 0; s < seeds; s++) {
                long seed = 4400L + s;
                // (а) СВОИ РУКИ: соперники и перемешивание закреплены, меняется
                // только мой характер.
                List<Double> свои = new ArrayList<>();
                for (String мой : ХАРАКТЕРЫ) {
                    свои.add(отрыв(ruleset, players, seed, мой, 0));
                }
                дисперсииСвои.add(дисперсия(свои));
                базовые.add(свои.get(0));
                // (б) ЧУЖИЕ РУКИ: я всегда первый характер, соперники сдвигаются
                // по кругу — четыре разных стола против одного и того же меня.
                List<Double> чужие = new ArrayList<>();
                for (int сдвиг = 0; сдвиг < ХАРАКТЕРЫ.size(); сдвиг++) {
                    чужие.add(отрыв(ruleset, players, seed, ХАРАКТЕРЫ.get(0), сдвиг));
                }
                дисперсииЧужие.add(дисперсия(чужие));
            }
            double дсвои = среднее(дисперсииСвои);
            double дчужие = среднее(дисперсииЧужие);
            double дслучай = дисперсия(базовые);
            double сумма = Math.max(1e-9, дсвои + дчужие + дслучай);
            b.append("| ").append(players)
                .append(" | ").append(ч(Math.sqrt(дсвои)))
                .append(" | ").append(ч(Math.sqrt(дчужие)))
                .append(" | ").append(ч(Math.sqrt(дслучай)))
                .append(" | ").append(ч(100 * дсвои / сумма))
                .append(" | ").append(ч(100 * дчужие / сумма))
                .append(" | ").append(ч(100 * дслучай / сумма))
                .append(" |\n");
        }
    }

    /**
     * Отрыв места 0 от сильнейшего соперника в одной партии.
     *
     * @param мой   характер места 0
     * @param сдвиг какими характерами играют соперники (сдвиг по кругу)
     */
    private static double отрыв(String ruleset, int players, long seed,
                                String мой, int сдвиг) {
        GameConfig cfg = GameConfig.buildCached(ruleset, players, seed, null, null);
        GameState s = Setup.buildGame(cfg);
        List<Agent> ags = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            String хар = i == 0 ? мой
                : ХАРАКТЕРЫ.get((i + сдвиг) % ХАРАКТЕРЫ.size());
            // ГЕНЕРАТОР ЗАВИСИТ ОТ ЗЕРНА, НО НЕ ОТ ХАРАКТЕРА: иначе смена
            // характера меняла бы ещё и случайность, и замер мерил бы две вещи.
            ags.add(BotCatalog.create(хар + УРОВЕНЬ, i, new Random(seed * 17 + i),
                players));
        }
        GameEngine.playGame(s, ags, null);
        int мои = Scoring.scorePlayer(s, 0).getOrDefault("total", 0);
        int лучший = Integer.MIN_VALUE;
        for (int i = 1; i < players; i++) {
            лучший = Math.max(лучший, Scoring.scorePlayer(s, i).getOrDefault("total", 0));
        }
        return мои - (double) лучший;
    }

    // ==================== 3. ЦЕНА РЕШЕНИЯ О ПРИКАЗЕ =========================

    private static void ценаПриказа(StringBuilder b, int games, String ruleset) {
        b.append("\n## 3. Цена решения: сколько стоит выбор карты приказа\n\n");
        b.append("В отмеченных точках копия партии доигрывается ДО КОНЦА с каждой ")
            .append("картой руки на входе — и не по одному разу, а по ").append(ПРОГОНОВ)
            .append(" раз на карту. Цена решения — разброс СРЕДНИХ по картам.\n\n")
            .append("ПОЧЕМУ ПО МНОГУ РАЗ. Одно доигрывание на карту ничего не ")
            .append("измеряет: соперники в копии вскрывают приказы заново, колоды ")
            .append("тасуются, и разброс между картами выходит такой же, как между ")
            .append("двумя прогонами ОДНОЙ карты. Первая редакция замера так и ")
            .append("вышла: 8.19 ПО «цены» при шуме 8.47.\n\n")
            .append("КОНТРОЛЬ — та же статистика на пустом месте: столько же групп ")
            .append("прогонов, но все по ОДНОЙ И ТОЙ ЖЕ карте. Цена решения значит ")
            .append("что-то только НАД контролем.\n\n");
        b.append("| игроков | замерено решений, шт | карт в руке, шт | ")
            .append("прогонов на карту, шт | цена решения, ПО | контроль, ПО | ")
            .append("сверх контроля, ПО |\n");
        b.append("|---:|---:|---:|---:|---:|---:|---:|\n");

        for (int players = 3; players <= 4; players++) {
            List<Double> разбросы = new ArrayList<>();
            List<Double> картВРуке = new ArrayList<>();
            List<Double> шумы = new ArrayList<>();
            for (int g = 0; g < games; g++) {
                GameConfig cfg = GameConfig.buildCached(ruleset, players, 9900L + g,
                    null, null);
                GameState s = Setup.buildGame(cfg);
                List<Agent> ags = new ArrayList<>();
                for (int i = 0; i < players; i++) {
                    ags.add(BotCatalog.create(ХАРАКТЕРЫ.get(i % ХАРАКТЕРЫ.size())
                        + УРОВЕНЬ, i, new Random(g * 71L + i), players));
                }
                // ЗАМЕРЯЕМ ОДНО МЕСТО: цена решения считается доигрыванием, и
                // каждое доигрывание — целая партия. Мерить всех вчетверо дороже
                // и ничего не добавляет: место в этой игре симметрично.
                Замерщик з = new Замерщик(ags.get(0), 1, new Random(g * 13L + 5));
                ags.set(0, з);
                GameEngine.playGame(s, ags, null);
                разбросы.addAll(з.разбросы);
                картВРуке.addAll(з.вариантов);
                шумы.addAll(з.шумы);
            }
            b.append("| ").append(players)
                .append(" | ").append(разбросы.size())
                .append(" | ").append(ч(среднее(картВРуке)))
                .append(" | ").append(ПРОГОНОВ)
                .append(" | ").append(ч(среднее(разбросы)))
                .append(" | ").append(ч(среднее(шумы)))
                .append(" | ").append(ч(среднее(разбросы) - среднее(шумы)))
                .append(" |\n");
        }
    }

    /**
     * АГЕНТ-ЗАМЕРЩИК: играет как обёрнутый бот, но в отмеченных точках сначала
     * считает, чего стоило бы каждое решение.
     *
     * <p>Мерится только вскрытие приказа: движок возобновляет партию с начала
     * круга, значит копия увидит ровно ту же обстановку, что и я сейчас.
     */
    private static final class Замерщик extends Agent {
        private final Agent под;
        private final int замеровНаПартию;
        private final Random rng;
        final List<Double> разбросы = new ArrayList<>();
        final List<Double> вариантов = new ArrayList<>();
        /** Разброс между ОДИНАКОВЫМИ прогонами — шум самого метода. */
        final List<Double> шумы = new ArrayList<>();
        private int сделано;

        Замерщик(Agent под, int замеровНаПартию, Random rng) {
            super(под.seat, "замерщик·" + под.name);
            this.под = под;
            this.замеровНаПартию = замеровНаПартию;
            this.rng = rng;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options,
                             Map<String, Object> ctx) {
            String вид = ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
            if ("reveal_order".equals(вид) && options != null && options.size() >= 2
                    && сделано < замеровНаПартию && rng.nextDouble() < 0.5
                    && под instanceof StrategicAgent стратег) {
                сделано++;
                Genome мой = стратег.genome();
                // ЦЕНА РЕШЕНИЯ: у каждой карты берётся СРЕДНЕЕ по группе
                // прогонов, и сравниваются средние. Размах одиночных прогонов
                // мерил бы дисперсию доигрывания, а не различие карт.
                List<Double> средниеПоКартам = new ArrayList<>();
                for (Choice o : options) {
                    средниеПоКартам.add(среднееПрогонов(state, мой, o.payload()));
                }
                разбросы.add(размах(средниеПоКартам));
                вариантов.add((double) options.size());
                // КОНТРОЛЬ: та же статистика, но все группы — по ОДНОЙ карте.
                // Если размах средних тут такой же, различать карты этот метод
                // не умеет, и «цена решения» из него не читается.
                List<Double> контроль = new ArrayList<>();
                Object одна = options.get(0).payload();
                for (int k = 0; k < options.size(); k++) {
                    контроль.add(среднееПрогонов(state, мой, одна));
                }
                шумы.add(размах(контроль));
            }
            return под.choose(state, options, ctx);
        }

        /** Среднее по группе доигрываний с одной и той же навязанной картой. */
        private double среднееПрогонов(GameState state, Genome мой, Object карта) {
            double сумма = 0;
            for (int i = 0; i < ПРОГОНОВ; i++) {
                сумма += Lookahead.playOut(state, seat, мой, мой,
                    new ForcedAgent.Forced("reveal_order", карта), 0, rng.nextLong());
            }
            return сумма / ПРОГОНОВ;
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            под.observeEvent(event);
        }

        @Override
        public void observePublicEvent(Map<String, Object> event) {
            под.observePublicEvent(event);
        }
    }

    // ======================== мелочи ========================================

    private static double среднее(List<Double> v) {
        if (v.isEmpty()) {
            return 0;
        }
        double s = 0;
        for (double x : v) {
            s += x;
        }
        return s / v.size();
    }

    /** Размах: максимум минус минимум. */
    private static double размах(List<Double> v) {
        double верх = Double.NEGATIVE_INFINITY;
        double низ = Double.POSITIVE_INFINITY;
        for (double x : v) {
            верх = Math.max(верх, x);
            низ = Math.min(низ, x);
        }
        return v.isEmpty() ? 0 : верх - низ;
    }

    private static double дисперсия(List<Double> v) {
        if (v.size() < 2) {
            return 0;
        }
        double m = среднее(v);
        double s = 0;
        for (double x : v) {
            s += (x - m) * (x - m);
        }
        return s / (v.size() - 1);
    }

    private static String ч(double v) {
        return String.format("%.2f", v).replace(',', '.');
    }

    /** Заглушка, чтобы карта видов решений не потерялась при правках. */
    private static final Map<String, String> ВИДЫ_РЕШЕНИЙ = new LinkedHashMap<>(
        new TreeMap<>(Map.of("reveal_order", "какую карту приказа вскрыть")));
}
