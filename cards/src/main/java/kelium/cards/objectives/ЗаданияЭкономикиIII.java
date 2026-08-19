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
                Награда.монеты(3), Награда.обломки(2).иКартыЗаданий(1),
                "СВОБОДНОЕ ДВИЖЕНИЕ",
                "В ЭТОТ ХОД оплати три разных предложения на планшете маркета — "
                + "напечатанные курсы и предложения карт сделок считаются по "
                + "отдельности. Награда: 3 монеты. Оплати четыре разных предложения, "
                + "и условие усилено: 2 обломка и карта задания. Биржа берёт "
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
                Награда.боеприпасы(2).иКартыЗаданий(1), Награда.монеты(4).иКартыЗаданий(1),
                "СВОБОДНЫЙ МАРКЕТ",
                "В ЭТОТ ХОД воспользуйся тремя разными предложениями планшета "
                + "технологий — каждый трек и каждый вечный курс считаются "
                + "отдельным предложением. Награда: 2 боеприпаса и карта задания. "
                + "Возьми четыре разных предложения, и условие усилено: 4 монеты и "
                + "ещё карта задания. Больше одного шага на трек за действие не "
                + "сделать — значит придётся идти вширь.");
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
                Награда.монеты(4), Награда.модуль("assembly").иКартыЗаданий(1),
                "ЩИТ: авиация или вышка",
                "Дойди до второго шага хотя бы на одном треке технологий — награда "
                + "4 монеты. Стой на втором шаге сразу на двух треках, и условие "
                + "усилено: жетон модуля сборки и карта задания. Второй шаг — первое "
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
            return ctx.shield(java.util.List.of("aircraft", "tower"));
        }

        @Override
        protected String действие() {
            return "science";
        }
    }

    /** o39 «Сдача» — сдать в Науку два трофейных жетона за ход. */
    public static final class Сдача extends ЗаданиеВКоде {
        public Сдача() {
            super("o39");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Сдача", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД действием Наука сдай не меньше двух трофейных жетонов",
                "Оба ушли на один и тот же трек",
                Награда.монеты(3), Награда.картыЗаданий(3),
                "КОНВЕРСИЯ: 1 келемий -> 1 обломок",
                "В ЭТОТ ХОД действием Наука сдай не меньше двух трофейных жетонов "
                + "— награда 3 монеты. Если оба ушли на один и тот же трек, условие "
                + "усилено: 3 карты задания. Один шаг на трек за действие — вот "
                + "почему «оба на один трек» стоит дороже, чем кажется.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).scienceTrophiesSpent >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && ход(ctx).scienceTracksUsed.size() == 1;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ход(ctx).scienceTrophiesSpent, 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ход(ctx).scienceTrophiesSpent;
            return есть >= 2 ? "" : "сдать в Науку ещё " + (2 - есть) + " трофейный жетон";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return ctx.convert(Resource.KELIUM, Resource.DEBRIS, 1);
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
                Награда.монеты(3).иБоеприпасы(2), Награда.картыЗаданий(2),
                "СВОБОДНАЯ НАУКА",
                "Имей одновременно ноль монет и ноль боеприпасов — награда 3 "
                + "монеты и 2 боеприпаса. Обнули заодно и келемий, и условие "
                + "усилено: 2 карты задания. Ва-банк играют только на голом столе.");
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

        @Override
        public boolean burn(CardContext ctx) {
            return свободнаяНаука(ctx);
        }
    }
}
