package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.agents.PlannerAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ЛИНЕЙКА СИЛЫ — честный ответ на вопрос «эта правка сделала бота сильнее?».
 *
 * <p>Зачем понадобилась. Силу бота здесь не мерил никто: обучение сравнивало
 * кандидата с чемпионом своей же приспособленностью, в которую вписаны надбавки
 * за поведение («сноси жетоны», «ставь арсенал»). Такое число говорит,
 * насколько бот похож на наши представления о правильной игре, а не насколько
 * он выигрывает.
 *
 * <p><b>ПАРНОЕ СРАВНЕНИЕ.</b> Каждая раздача играется ДВАЖДЫ: одним и тем же
 * составом, на одном поле, с одними семенами — сперва испытуемый играет как
 * эталон, потом с проверяемой правкой. Сравниваются исходы ОДНОЙ И ТОЙ ЖЕ
 * партии. Это не прихоть: разброс между полями в этой игре достигает 140%, и
 * непарное сравнение съедает любую настоящую разницу.
 *
 * <p><b>ПОЧЕМУ ОДИН ХАРАКТЕР.</b> Первая версия сажала испытуемого на
 * сдвигающееся место И раздавала характеры по тому же сдвигу — от этого ему
 * доставались только два характера из четырёх, и проверка «одинаковые боты»
 * дала 16% побед вместо 25%. Теперь характер испытуемого задан и не меняется,
 * сдвигается только место за столом: место само по себе стоит очков.
 *
 * <p><b>ПОЧЕМУ С ДОВЕРИТЕЛЬНЫМ ИНТЕРВАЛОМ.</b> Сто партий различают разницу
 * примерно в 9 процентных пунктов, не меньше. Объявлять улучшением «27 против
 * 25» — ровно та ошибка, из-за которой пришлось чинить отбор. Интервал
 * печатается всегда.
 *
 * <p>Запуск: {@code kelium.Линейка [партий] [игроков] [горизонт] [характер]}.
 * Горизонт 0 означает проверку самой линейки: обе стороны одинаковы, и разницы
 * быть не должно.
 */
public final class Линейка {

    private Линейка() {
    }

    /** Исход одной раздачи для обеих сторон. */
    private record Пара(boolean победаЭталона, double отрывЭталона,
                        boolean победаПравки, double отрывПравки, int раундов) {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int раздач = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int горизонт = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        String характер = args.length > 3 ? args[3] : "punisher";
        int проб = args.length > 4 ? Integer.parseInt(args[4]) : 1;
        double вес = args.length > 5 ? Double.parseDouble(args[5]) : 0.5;
        int потоков = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);

        out.printf("ЛИНЕЙКА СИЛЫ · свод %s · %d раздач × 2 партии · %d игроков%n",
            GameConfig.DEFAULT_RULESET, раздач, игроков);
        out.println("поля: " + LayoutLibrary.describePool(игроков));
        out.printf("испытуемый: гроссмейстер «%s»%n", характер);
        out.printf("правка: горизонт %d %s, доигрываний %d, вес доигрывания %.2f%n%n",
            горизонт, горизонт == 0 ? "(то же самое — проверка линейки)"
                : "раундов при оценке хода", проб, вес);

        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        try {
            List<Future<Пара>> будущее = new ArrayList<>();
            for (int g = 0; g < раздач; g++) {
                final int номер = g;
                будущее.add(пул.submit(раздача(номер, игроков, горизонт, характер, проб, вес)));
            }
            int победЭталона = 0;
            int победПравки = 0;
            int тольноПравка = 0;
            int тольноЭталон = 0;
            double суммаРазницы = 0;
            double суммаКвадратов = 0;
            double суммаРаундов = 0;
            for (Future<Пара> f : будущее) {
                Пара п = f.get();
                if (п.победаЭталона()) {
                    победЭталона++;
                }
                if (п.победаПравки()) {
                    победПравки++;
                }
                if (п.победаПравки() && !п.победаЭталона()) {
                    тольноПравка++;
                }
                if (п.победаЭталона() && !п.победаПравки()) {
                    тольноЭталон++;
                }
                double d = п.отрывПравки() - п.отрывЭталона();
                суммаРазницы += d;
                суммаКвадратов += d * d;
                суммаРаундов += п.раундов();
            }

            out.printf("ПОБЕД: эталон %d из %d (%.1f%%), с правкой %d (%.1f%%)%n",
                победЭталона, раздач, 100.0 * победЭталона / раздач,
                победПравки, 100.0 * победПравки / раздач);
            out.printf("раздач, где выиграла только правка: %d; только эталон: %d%n",
                тольноПравка, тольноЭталон);

            // ЗНАЧИМОСТЬ ПО РАСХОЖДЕНИЯМ (критерий Макнемара). Раздачи, где обе
            // стороны сыграли одинаково, о разнице не говорят ничего — считать
            // надо только те, где исход разошёлся.
            int расхождений = тольноПравка + тольноЭталон;
            if (расхождений >= 1) {
                double z = (тольноПравка - тольноЭталон) / Math.sqrt(расхождений);
                out.printf("расхождений %d, перевес правки z = %+.2f (значимо при |z| > 1.96)%n",
                    расхождений, z);
            } else {
                out.println("расхождений нет вовсе: правка не изменила ни одного исхода");
            }

            double среднее = суммаРазницы / раздач;
            double дисп = Math.max(0, суммаКвадратов / раздач - среднее * среднее);
            double ошибка = 1.96 * Math.sqrt(дисп / раздач);
            out.printf("РАЗНИЦА ОТРЫВА (правка минус эталон, по одним раздачам): "
                + "%+.2f ПО ± %.2f%n", среднее, ошибка);
            out.printf("раундов в партии %.1f%n%n", суммаРаундов / раздач);

            boolean различимо = Math.abs(среднее) > ошибка;
            if (горизонт == 0) {
                out.println(различимо
                    ? "ВНИМАНИЕ: стороны одинаковы, а разница различима — линейка врёт."
                    : "Проверка пройдена: одинаковые боты неразличимы, как и должно быть.");
            } else {
                out.println(различимо
                    ? (среднее > 0 ? "ПРАВКА СИЛЬНЕЕ — разница различима."
                        : "ПРАВКА СЛАБЕЕ — разница различима и отрицательна.")
                    : "РАЗНИЦА НЕ РАЗЛИЧИМА: на этом числе раздач сказать нечего.");
            }
        } finally {
            пул.shutdown();
        }
    }

    private static Callable<Пара> раздача(int номер, int игроков, int горизонт,
                                          String характер, int проб, double вес) {
        return () -> {
            long seed = 5_500_000L + номер;
            int место = номер % игроков;
            double[] эт = партия(seed, игроков, место, характер, 0, 1, 0.5);
            double[] пр = партия(seed, игроков, место, характер, горизонт, проб, вес);
            return new Пара(эт[0] > 0, эт[1], пр[0] > 0, пр[1], (int) эт[2]);
        };
    }

    /** Одна партия. Возвращает {победа, отрыв, раундов}. */
    private static double[] партия(long seed, int игроков, int место, String характер,
                                   int горизонт, int проб, double вес) {
        GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
        List<String> прочие = new ArrayList<>(Bots.ROSTER_4);
        прочие.remove(характер);
        List<Agent> agents = new ArrayList<>();
        int k = 0;
        for (int i = 0; i < игроков; i++) {
            String ch = i == место ? характер : прочие.get(k++ % прочие.size());
            Agent a = Bots.create(ch, Bots.Level.ГРОССМЕЙСТЕР, i,
                new Random(seed * 31 + i), игроков);
            // ГОРИЗОНТ ВЫСТАВЛЯЕТСЯ ВСЕГДА, А НЕ ТОЛЬКО КОГДА ОН БОЛЬШЕ НУЛЯ.
            // С тех пор как горизонт включён гроссмейстеру по умолчанию, «ноль»
            // здесь означал не «эталон без горизонта», а «оставить как у всех»,
            // и линейка сравнивала правку саму с собой.
            if (i == место && a instanceof PlannerAgent пл) {
                пл.horizonRounds = горизонт;
                пл.horizonSamples = проб;
                пл.horizonWeight = вес;
            }
            agents.add(a);
        }
        new GameEngine(s, agents, ev -> { }).run();
        int мои = Scoring.scorePlayer(s, место).getOrDefault("total", 0);
        int лучшийЧужой = Integer.MIN_VALUE;
        for (PlayerState p : s.players) {
            if (p.seat != место) {
                лучшийЧужой = Math.max(лучшийЧужой,
                    Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0));
            }
        }
        return new double[]{мои > лучшийЧужой ? 1 : 0, мои - лучшийЧужой, s.round};
    }
}
