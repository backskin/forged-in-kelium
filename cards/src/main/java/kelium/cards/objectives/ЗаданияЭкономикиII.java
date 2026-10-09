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
     * o79 «Строй крупно» (комплект «пять развилок», 27.09.2026, вместо o15
     * «Стройбум»: ветка «построить» ставит одно здание, и «два здания за ход»
     * стало почти невыполнимым). В этот ход поставлен добытчик или станция
     * 3-го или 4-го уровня; дополнительно — у гекса с войсками врага.
     */
    public static final class СтройКрупно extends ЗаданиеВКоде {
        public СтройКрупно() {
            super("o79");
        }

        @Override
        public Лицо лицо() {
            return печатное("Строй крупно", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД построй добытчик или энергостанцию 3-го или 4-го уровня",
                "на гексе, соседнем с гексом, где есть войска врага.",
                Награда.выбор("command", "develop"),
                Награда.модуль("attack").иСпец(1),
                Утиль.ЗАГРАДИТЕЛЬНЫЙ_ОГОНЬ);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return !ход(ctx).builtBigEconomyHexes.isEmpty();
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            var s = ctx.state();
            java.util.Set<String> чужие = new java.util.HashSet<>();
            for (var p : s.players) {
                if (p.seat != ctx.seat()) {
                    for (var u : p.unitsOnField()) {
                        if (u.hexId != null) {
                            чужие.add(u.hexId);
                        }
                    }
                }
            }
            for (String гекс : ход(ctx).builtBigEconomyHexes) {
                for (String сосед : s.field.neighbors(гекс)) {
                    if (чужие.contains(сосед)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            return готовность(ctx.me().resources.coin() >= 3) * 0.3;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "построить добытчик или энергостанцию 3-го или 4-го уровня";
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
            // ПЕЧАТНАЯ КАРТА «Развёртывание» № 17 (25.09.2026).
            return печатное("Полная нагрузка", СОСТОЯНИЕ,
                "Все твои здания, требующие энергию, запитаны (не менее 3)",
                "не менее 5 зданий.",
                Награда.выбор("market", "assembly"),
                Награда.картаАрсенала().иСпец(1),
                Утиль.ДВИЖЕНИЕ_ДВУМЯ);
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
            return всеЗапитаны(ctx, 5);
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
            // ПЕЧАТНАЯ КАРТА «Развёртывание» № 21 (25.09.2026).
            return печатное("Коммутация", СОСТОЯНИЕ,
                "Имей на двух зданиях - источниках энергии ровно один свободный кубик",
                "На трех зданиях - источниках энергии.",
                Награда.выбор("extract", "power"),
                Награда.картаАрсенала().иСпец(1),
                Утиль.ЭВАКУАЦИЯ);
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
