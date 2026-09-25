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

    /** o33 «Биржа» — келемий потрачен на Рынке двумя разными способами за ход. */
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
            // ПЕЧАТНАЯ КАРТА «Обеспечение» № 11 (25.09.2026).
            return печатное("Биржа", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД потрать на Рынке келемий двумя разными способами.",
                null,
                Награда.выбор("assembly", "mining"), Награда.нет(),
                Утиль.ЗАГРАДИТЕЛЬНЫЙ_ОГОНЬ);
        }

        /**
         * СКОЛЬКО РАЗНЫХ СПОСОБОВ ТРАТЫ КЕЛЕМИЯ НА РЫНКЕ за ход. Каждая сделка
         * Рынка платится келемием: напечатанный обмен (свой на каждый курс),
         * предложение карты рынка и смена карты рынка — три разных способа.
         */
        private static int способов(CardContext ctx) {
            var ж = ход(ctx);
            return ж.marketOffersUsed.size() + (ж.usedMarketRefresh ? 1 : 0);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return способов(ctx) >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return false;      // усиления у карты нет (таблица дизайнера 04.09.2026)
        }

        @Override
        public double progress(CardContext ctx) {
            return ступени(доля(способов(ctx), 2),
                готовность(ctx.have(Resource.KELIUM) >= 2));
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = способов(ctx);
            return есть >= 2 ? "" : "потратить келемий на Рынке ещё " + (2 - есть)
                + " другим способом";
        }


        @Override
        protected String действие() {
            return "market";
        }
    }

    /** o34 «Научный отдел» — трофеи потрачены на три разных обмена Научного отдела. */
    public static final class НаучныйОтдел extends ЗаданиеВКоде {
        public НаучныйОтдел() {
            super("o34");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Обеспечение» № 13 (25.09.2026): способов ТРИ.
            return печатное("Научный отдел", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД потрать в Научном отделе трофеи тремя разными способами",
                null,
                Награда.выбор("assembly", "combat"), Награда.нет(),
                Утиль.РИКОШЕТ);
        }

        /** Сколько разных обменов Научного отдела надо сделать за ход. */
        private static final int НАДО = 3;

        /**
         * РАЗНЫЕ ОБМЕНЫ НАУЧНОГО ОТДЕЛА ЗА ХОД. Научный отдел — блок обменов на
         * планшете технологий (трофеи на добро), а не треки: обменов там ровно
         * три, и «тремя разными способами» значит — всеми тремя. Шаг по треку
         * сюда не идёт.
         */
        private static int обменов(CardContext ctx) {
            int n = 0;
            for (String к : ход(ctx).scienceOffersUsed) {
                if (к.startsWith("rate:")) {
                    n++;
                }
            }
            return n;
        }

        // ТРИ ПРЕДЛОЖЕНИЯ ЗА ХОД — ПОЧТИ НЕВОЗМОЖНО (замер 19.09.2026). Обменов
        // на планшете ровно три, стоят они 1+1+2 трофея, а на руках у игрока
        // 0.6-0.9 трофея; стенд пропускной способности дал 0.7% ходов, где в
        // Науку ушло три и больше трофеев, и карта выполнилась 0.5 раза на сто
        // партий. Два предложения — уже живая, хоть и трудная, цель.
        @Override
        public boolean satisfied(CardContext ctx) {
            return обменов(ctx) >= НАДО;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return false;      // усиления у карты нет (таблица дизайнера 04.09.2026)
        }

        @Override
        public double progress(CardContext ctx) {
            // ТРИ ПРЕДЛОЖЕНИЯ ЗА ХОД ПОКУПАЮТСЯ ТРОФЕЯМИ, и до Науки счёт
            // взятых равен нулю — карта молчала ровно тогда, когда решается,
            // копить ли трофеи под неё. Замер 08.09.2026: 77% попаданий в руку —
            // близость ровно ноль. Теперь карта говорит и о средствах: шаг трека
            // плюс два обмена трофеев стоят около четырёх трофеев, и по этому
            // запасу считается готовность.
            int взято = обменов(ctx);
            int трофеи = ctx.have(kelium.core.Resource.TROPHY);
            // Три обмена стоят 1+1+2 трофея: по этому запасу и готовность.
            return ступени(Math.min(0.99, доля(взято, НАДО)),
                готовность(доля(трофеи, 4)));
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = обменов(ctx);
            if (есть >= НАДО) {
                return "";
            }
            return ctx.have(kelium.core.Resource.TROPHY) < 4
                ? "накопить трофеи на три обмена Научного отдела"
                : "сделать ещё " + (НАДО - есть) + " разных обмена в Научном отделе";
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
            // ПЕЧАТНАЯ КАРТА «Обеспечение» № 12 (25.09.2026).
            return печатное("Научный рывок", СОСТОЯНИЕ,
                "Дойди до второй ступени хотя бы на одном треке технологий",
                "хотя бы на двух треках технологий.",
                Награда.выбор("combat", "energy_swap"),
                Награда.позолотой(),
                Утиль.ЭВАКУАЦИЯ);
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
        protected String действие() {
            return "science";
        }
    }


    /** o40 «Ва-банк» — ноль келемия и ноль боеприпасов одновременно. */
    public static final class ВаБанк extends ЗаданиеВКоде {
        public ВаБанк() {
            super("o40");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Выработка» № 03 (25.09.2026): монеты и келемий
            // поменялись местами — в базе келемий, в усилении монеты.
            return печатное("Ва-банк", СОСТОЯНИЕ,
                "Имей одновременно ноль келемия и ноль боеприпасов в хранилище",
                "не имей даже монет.",
                Награда.выбор("assembly", "mining"),
                Награда.трофеи(1).иСпец(1),
                Утиль.ЭВАКУАЦИЯ);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ctx.have(Resource.KELIUM) == 0 && ctx.have(Resource.AMMO) == 0;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && ctx.have(Resource.COIN) == 0;
        }

        @Override
        public double progress(CardContext ctx) {
            // ТРЕБОВАНИЕ НАИЗНАНКУ: приближает не накопление, а трата. Поэтому
            // близость считается от ОСТАТКА — чем меньше на руках, тем ближе.
            // Одна обнулённая половина уже половина дела, а мерить остаток надо
            // с потолком: разница между 9 и 12 монетами для карты одинаково
            // далека, и без потолка градиент растворился бы в богатстве.
            int келемий = ctx.have(Resource.KELIUM);
            int бпр = ctx.have(Resource.AMMO);
            double доляКел = 1.0 - доля(келемий, 4);
            double доляБпр = 1.0 - доля(бпр, 4);
            return Math.min(0.99, (доляКел + доляБпр) / 2.0);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "потратить весь келемий и все боеприпасы";
        }

        /**
         * Действие подсказывается по тому, ЧЕГО БОЛЬШЕ ОСТАЛОСЬ: келемий уходит
         * на Рынок, боеприпасы — в Бой. Единственная карта, где подсказка зависит
         * от обстановки: у остальных условие закрывается всегда одним и тем же
         * действием, а здесь тратить надо то, что мешает.
         */
        @Override
        protected String действие(CardContext ctx) {
            return ctx.have(Resource.AMMO) > ctx.have(Resource.KELIUM) ? "combat" : "market";
        }

    }
}
