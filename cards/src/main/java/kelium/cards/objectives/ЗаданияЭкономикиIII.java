package kelium.cards.objectives;

import kelium.cards.Награда;
import kelium.core.Resource;
import kelium.engine.cards.CardContext;

import static kelium.cards.objectives.Лицо.Вид.ОБЫЧНАЯ;
import static kelium.cards.objectives.Лицо.Природа.ПРОИСШЕСТВИЕ;
import static kelium.cards.objectives.Лицо.Природа.СОСТОЯНИЕ;

/**
 * ЭКОНОМИКА И НАУКА, третья часть: рынок, планшет технологий, ва-банк.
 */
public final class ЗаданияЭкономикиIII {

    private ЗаданияЭкономикиIII() {
    }

    /** o33 «Биржа» — три разных предложения планшета маркета за ход. */
    public static final class Биржа extends ЗаданиеВКоде {
        public Биржа() {
            super("o33");
        }

        @Override
        protected String отсев() {
            return "[3+]";
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Биржа", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД оплати три разных предложения на планшете маркета — "
                + "напечатанные курсы и предложения карт сделок считаются по отдельности",
                "Оплати четыре разных предложения",
                Награда.монеты(3), Награда.трофеи(2).иКартыЗаданий(1),
                "СВОБОДНОЕ ДВИЖЕНИЕ",
                "В ЭТОТ ХОД оплати три разных предложения на планшете маркета — "
                + "напечатанные курсы и предложения карт сделок считаются по "
                + "отдельности. Награда: 3 монеты. Оплати четыре разных предложения, "
                + "и условие усилено: 2 трофея и карта задания. Биржа берёт "
                + "объёмом, а не одной удачной сделкой.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).marketOffersUsed.size() >= 3;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).marketOffersUsed.size() >= 4;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ход(ctx).marketOffersUsed.size(), 3);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ход(ctx).marketOffersUsed.size();
            return есть >= 3 ? "" : "оплатить ещё " + (3 - есть) + " предложение на маркете";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободноеДвижение(ctx);
        }

        @Override
        protected String действие() {
            return "market";
        }
    }

    /** o34 «Научный отдел» — три разных предложения планшета технологий за ход. */
    public static final class НаучныйОтдел extends ЗаданиеВКоде {
        public НаучныйОтдел() {
            super("o34");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Научный отдел", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД воспользуйся тремя разными предложениями планшета "
                + "технологий — каждый трек и каждый вечный курс считаются "
                + "отдельным предложением",
                "Возьми четыре разных предложения",
                Награда.монеты(1).иБоеприпасы(2), Награда.картаАрсенала(),
                "СВОБОДНЫЙ МАРКЕТ",
                "В ЭТОТ ХОД воспользуйся тремя разными предложениями планшета "
                + "технологий — каждый трек и каждый вечный курс считаются отдельным "
                + "предложением. Награда: 2 боеприпаса и 1 монета. Возьми четыре "
                + "разных предложения, и условие усилено: 1 карта арсенала. Больше одного шага на трек за действие не сделать — "
                + "значит придётся идти вширь.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).scienceOffersUsed.size() >= 3;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).scienceOffersUsed.size() >= 4;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ход(ctx).scienceOffersUsed.size(), 3);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ход(ctx).scienceOffersUsed.size();
            return есть >= 3 ? "" : "взять ещё " + (3 - есть) + " предложение планшета технологий";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободныйМаркет(ctx);
        }

        @Override
        protected String действие() {
            return "science";
        }
    }

    /** o36 «Научный рывок» — дойти до второго шага хотя бы на одном треке технологий. */
    public static final class НаучныйРывок extends ЗаданиеВКоде {
        public НаучныйРывок() {
            super("o36");
        }

        private int треков(CardContext ctx, int шаг) {
            int n = 0;
            for (int v : ctx.me().techSteps.values()) {
                if (v >= шаг) {
                    n++;
                }
            }
            return n;
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Научный рывок", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Дойди до второго шага хотя бы на одном треке технологий",
                "Стой на втором шаге сразу на двух треках",
                Награда.монеты(4), Награда.картаАрсенала(),
                "СВОБОДНАЯ НАУКА",
                "Дойди до второго шага хотя бы на одном треке технологий — награда "
                + "4 монеты. Стой на втором шаге сразу на двух треках, и условие "
                + "усилено: 1 карта арсенала. Второй шаг — первое "
                + "место, где трек начинает платить очками.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return треков(ctx, 2) >= 1;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return треков(ctx, 2) >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            // Близость — по САМОМУ ПРОДВИНУТОМУ треку: требование говорит «хотя бы
            // на одном», поэтому дальний трек и есть мера, а сумма по всем врала
            // бы (три трека на первом шаге — это не «полтора второго шага»).
            int лучший = 0;
            for (int v : ctx.me().techSteps.values()) {
                лучший = Math.max(лучший, v);
            }
            return доля(лучший, 2);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "дойти до второго шага хотя бы на одном треке";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободнаяНаука(ctx);
        }

        @Override
        protected String действие() {
            return "science";
        }
    }

    /**
     * o39 «Сдача» — заплатить в Науку за один ход.
     *
     * <p>ПЕРЕПИСАНА 25.08.2026, и вот почему. Карта требовала «сдать не меньше
     * двух трофейных ЖЕТОНОВ», а усиление — «оба на один трек». Два правила
     * убили её по очереди: наука стала платиться трофеями (жетоны в оплату не
     * идут вовсе), а шаг разрешён только по ОДНОМУ треку за действие — то есть
     * «оба на один трек» выполняется само собой, бесплатно. Замер: карту
     * раздали 89 раз за 200 партий и не выполнили НИ РАЗУ.
     *
     * <p>ЦЕНА КАРТЫ — ОБЛОМКИ, А ОБЛОМОК ЭТО ОЧКО. Шаги трека стоят 1·2·3·4, и
     * заплатить 2 за одно действие значит уйти на второй шаг или дальше;
     * заплатить 4 — встать на последний шаг или перепрыгнуть занятые ячейки,
     * сложив цены. Игрок отдаёт победные очки сейчас за карту задания и карту
     * арсенала на выбор — это и есть названная цена, а не «сделай что делал».
     */
    public static final class Сдача extends ЗаданиеВКоде {
        public Сдача() {
            super("o39");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Сдача", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД действием Наука заплати не меньше двух трофеев",
                "Заплатил не меньше четырёх",
                Награда.монеты(3), Награда.картыЗаданий(1).иКартуСВитрины(),
                "КОНВЕРСИЯ: 1 келемий -> 1 трофей",
                "В ЭТОТ ХОД действием Наука заплати не меньше двух трофеев — "
                + "награда 3 монеты. Заплатил четыре и больше: карта задания и "
                + "карта арсенала на выбор из открытых. Шаги трека стоят один, "
                + "два, три и четыре трофея, а прыжок через занятые ячейки "
                + "платится за каждый пройденный шаг — вот единственный способ "
                + "отдать четыре разом.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).sciencePaidUnits >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).sciencePaidUnits >= 4;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ход(ctx).sciencePaidUnits, 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ход(ctx).sciencePaidUnits;
            return есть >= 2 ? "" : "заплатить в Науку ещё " + (2 - есть) + " трофей";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return ctx.convert(Resource.KELIUM, Resource.TROPHY, 1);
        }

        @Override
        protected String действие() {
            return "science";
        }
    }

    /** o40 «Ва-банк» — ноль монет и ноль боеприпасов одновременно. */
    public static final class ВаБанк extends ЗаданиеВКоде {
        public ВаБанк() {
            super("o40");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Ва-банк", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей одновременно ноль монет и ноль боеприпасов",
                "Обнули заодно и келемий",
                Награда.монеты(3).иБоеприпасы(2), Награда.трофеи(3),
                "СВОБОДНАЯ НАУКА",
                "Имей одновременно ноль монет и ноль боеприпасов — награда 3 "
                + "монеты и 2 боеприпаса. Обнули заодно и келемий, и условие "
                + "усилено: 3 трофея. Ва-банк играют только на голом столе.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ctx.have(Resource.COIN) == 0 && ctx.have(Resource.AMMO) == 0;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && ctx.have(Resource.KELIUM) == 0;
        }

        @Override
        public double progress(CardContext ctx) {
            // ТРЕБОВАНИЕ НАИЗНАНКУ: приближает не накопление, а трата. Поэтому
            // близость считается от ОСТАТКА — чем меньше на руках, тем ближе.
            // Одна обнулённая половина уже половина дела, а мерить остаток надо
            // с потолком: разница между 9 и 12 монетами для карты одинаково
            // далека, и без потолка градиент растворился бы в богатстве.
            int монеты = ctx.have(Resource.COIN);
            int бпр = ctx.have(Resource.AMMO);
            double доляМонет = 1.0 - доля(монеты, 4);
            double доляБпр = 1.0 - доля(бпр, 4);
            return (доляМонет + доляБпр) / 2.0;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "потратить все монеты и все боеприпасы";
        }

        /**
         * Действие подсказывается по тому, ЧЕГО БОЛЬШЕ ОСТАЛОСЬ: монеты уходят в
         * Стройку, боеприпасы — в Бой. Единственная карта, где подсказка зависит
         * от обстановки: у остальных условие закрывается всегда одним и тем же
         * действием, а здесь тратить надо то, что мешает.
         */
        @Override
        protected String действие(CardContext ctx) {
            return ctx.have(Resource.AMMO) > ctx.have(Resource.COIN) ? "combat" : "build";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободнаяНаука(ctx);
        }
    }
}
