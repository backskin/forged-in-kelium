package kelium.cards.objectives;

import kelium.cards.Награда;
import kelium.core.BuildingType;
import kelium.engine.cards.CardContext;

import static kelium.cards.objectives.Лицо.Вид.ОБЫЧНАЯ;
import static kelium.cards.objectives.Лицо.Природа.ПРОИСШЕСТВИЕ;
import static kelium.cards.objectives.Лицо.Природа.СОСТОЯНИЕ;

/**
 * ЭКОНОМИКА И НАУКА, вторая часть: застройка, энергетика.
 */
public final class ЗаданияЭкономикиII {

    private ЗаданияЭкономикиII() {
    }

    /**
     * o15 «Стройбум» — две строительные операции на двух попарно несоседних гексах.
     *
     * <p>БЫЛО «две операции за ход» — считалось само собой. СТАЛО: операции на
     * гексах, не соседних между собой, то есть застройка вширь, а не вглубь.
     */
    public static final class Стройбум extends ЗаданиеВКоде {
        public Стройбум() {
            super("o15");
        }

        private java.util.List<String> гексыОпераций(CardContext ctx) {
            return java.util.List.copyOf(new java.util.LinkedHashSet<>(ход(ctx).buildOpHexes));
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Стройбум", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД построй 2 здания на разных гексах",
                "3 здания на разных и несоседних между собой гексах",
                Награда.выбор("energy_swap", "mining"),
                Награда.трофеи(1).иСпец(1),
                Утиль.ДВИЖЕНИЕ,
                "В ЭТОТ ХОД построй 2 здания на разных гексах — награда бесплатная "
                + "Стройка. Построй 3 здания на разных и несоседних между собой "
                + "гексах, и условие усилено: 1 трофей и спец-действие. Зона стройки растёт от твоих "
                + "стенок, поэтому разъехаться дороже, чем прирасти.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            // РАЗНЫЕ ГЕКСЫ, А НЕ НЕСОСЕДНИЕ: несоседство спрашивает усиление
            // (таблица дизайнера). Прежде база требовала сразу несоседства — то
            // есть карта была строже, чем напечатано у дизайнера.
            return гексыОпераций(ctx).size() >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ctx.chooseNonAdjacent(гексыОпераций(ctx), 3);
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // ГРАДИЕНТ ПО ЧИСЛУ ЗАНЯТЫХ ГЕКСОВ: базе довольно двух разных, а
            // несоседство спрашивает только усиление.
            return доля(гексыОпераций(ctx).size(), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "выполнить стройку ещё на одном гексе, не соседнем с уже занятыми";
        }


        @Override
        protected String действие() {
            return "build";
        }
    }


    /** o18 «Полная нагрузка» — все свои здания запитаны, их не меньше трёх. */
    public static final class ПолнаяНагрузка extends ЗаданиеВКоде {
        public ПолнаяНагрузка() {
            super("o18");
        }

        private boolean всеЗапитаны(CardContext ctx, int минимум) {
            var зд = ctx.me().buildingsOnField();
            if (зд.size() < минимум) {
                return false;
            }
            for (var b : зд) {
                if (!b.powered()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Полная нагрузка", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Все твои здания, требующие энергию, запитаны (не менее 3)",
                "Не менее 4 зданий",
                Награда.выбор("assembly", "science"),
                Награда.картаАрсенала().иСпец(1),
                Утиль.РАЗВЁРТЫВАНИЕ,
                "Все твои здания, требующие энергию, запитаны, и таких зданий не "
                + "менее трёх, — награда бесплатное Снаряжение. Не менее четырёх "
                + "зданий — условие усилено: 1 монета. Пустая ячейка ЭНР закрывается "
                + "монетой только на одно действие — на состояние это не работает.");
        }

        @Override
        public double progress(CardContext ctx) {
            // ТРЕБОВАНИЕ ИЗ ДВУХ ЧАСТЕЙ — число зданий и запитанность ВСЕХ, —
            // поэтому близость считается по обеим и берётся худшая: три здания
            // с одним погашенным ближе к цели, чем два запитанных, но пока не
            // цель. Незапитанные считаем поимённо: это ровно то, что осталось
            // сделать Сменой энергии.
            var зд = ctx.me().buildingsOnField();
            if (зд.isEmpty()) {
                return 0.0;
            }
            int погашенных = 0;
            for (var b : зд) {
                if (!b.powered()) {
                    погашенных++;
                }
            }
            double поЧислу = доля(зд.size(), 3);
            double поЭнергии = доля(зд.size() - погашенных, зд.size());
            return Math.min(поЧислу, поЭнергии);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return всеЗапитаны(ctx, 3);
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return всеЗапитаны(ctx, 4);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "запитать все здания, доведя их число до трёх";
        }


        @Override
        protected String действие() {
            return "energy_swap";
        }
    }


    /**
     * o20 «Коммутация» — на каждом своём источнике энергии ровно один свободный
     * кубик, источников не меньше двух.
     */
    public static final class Коммутация extends ЗаданиеВКоде {
        public Коммутация() {
            super("o20");
        }

        /**
         * СКОЛЬКО ИСТОЧНИКОВ ДЕРЖАТ РОВНО ОДИН ПРОСТОЙ КУБИК.
         *
         * <p>Считаются подходящие, а не все: в таблице дизайнера требование
         * звучит «имей на ДВУХ зданиях-источниках ровно один свободный кубик».
         * Прежде карта требовала этого от КАЖДОГО источника, и четвёртый
         * источник с двумя кубиками ломал уже собранное условие.
         */
        private int источниковСОдним(CardContext ctx, boolean строго) {
            int подходящих = 0;
            for (var b : ctx.me().buildingsOnField()) {
                if (b.type != BuildingType.POWER_PLANT && b.type != BuildingType.COMMAND_CENTER) {
                    continue;
                }
                if (!строго || b.energyIdle == 1) {
                    подходящих++;
                }
            }
            return подходящих;
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Коммутация", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей на двух зданиях - источниках энергии ровно один свободный кубик",
                "На трех источниках энергии",
                Награда.выбор("build", "market"),
                Награда.трофеи(1).иСпец(1),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "Имей на двух зданиях - источниках энергии ровно один свободный "
                + "кубик — награда бесплатный Манёвр. На трёх источниках энергии "
                + "условие усилено: 1 трофей и спец-действие. Простой кубик лежит на своём "
                + "источнике, а собрать его обратно стоит гекса в Смене энергии — "
                + "держать по одному неудобно намеренно.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return источниковСОдним(ctx, true) >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return источниковСОдним(ctx, true) >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            // ГРАДИЕНТ ПО ЧИСЛУ ПОДХОДЯЩИХ ИСТОЧНИКОВ: нужно два, и близость
            // считается по тому, сколько уже держат ровно один простой кубик.
            return доля(источниковСОдним(ctx, true), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "оставить ровно один свободный кубик на двух источниках энергии";
        }


        @Override
        protected String действие() {
            return "energy_swap";
        }
    }
}
