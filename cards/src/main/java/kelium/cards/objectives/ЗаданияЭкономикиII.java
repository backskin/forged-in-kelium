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
                "В ЭТОТ ХОД выполни две строительные операции на двух гексах, не "
                + "соседних между собой",
                "Выполни три операции на трёх попарно несоседних гексах",
                Награда.боеприпасы(3), Награда.трофеи(2),
                Утиль.БОЙ,
                "В ЭТОТ ХОД выполни две строительные операции на двух гексах, не "
                + "соседних между собой, — награда 3 боеприпаса. Выполни три "
                + "операции на трёх попарно несоседних гексах, и условие усилено: 2 трофея. Зона стройки растёт от твоих стенок, "
                + "поэтому разъехаться дороже, чем прирасти.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ctx.chooseNonAdjacent(гексыОпераций(ctx), 2);
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
            // ГРАДИЕНТ ПО НАБРАННОМУ НЕСОСЕДСТВУ, а не по числу операций: две
            // стройки на соседних гексах к цели не приближают вовсе, и карта
            // обязана это различать. Считаем, какой самый большой попарно
            // несоседний набор уже собран — той же проверкой движка, которой
            // считается выполнение.
            var гексы = гексыОпераций(ctx);
            return ctx.chooseNonAdjacent(гексы, 1) ? доля(1, 2) : 0.0;
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

    /**
     * o16 «Переезд» — перенос двух зданий за ход.
     *
     * <p>Оговорка «не считая ЦУ» снята: перенос любого здания стоит одну монету
     * независимо от типа. ЦУ переносится не чаще раза за ход, поэтому стало
     * усилением, а не исключением из счёта.
     */
    public static final class Переезд extends ЗаданиеВКоде {
        public Переезд() {
            super("o16");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Переезд", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД построй здание на гексе, где у тебя не было ни одного "
                + "своего здания",
                "В ЭТОТ ХОД снеси при этом своё здание на другом гексе",
                Награда.монеты(3), Награда.картаАрсенала(),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "В ЭТОТ ХОД построй здание на гексе, где у тебя не было ни одного "
                + "своего здания, — награда 3 монеты. Если в этот же ход ты снёс "
                + "своё здание на другом гексе, условие усилено: 1 карта арсенала. "
                + "Переезда как операции в Стройке нет: хозяйство переезжает "
                + "стройкой на новом месте и сносом на старом, и обе половины "
                + "платные.");
        }

        /** Гексы этого хода, где здание встало НА ЧИСТОЕ место (моих там не было). */
        private java.util.Set<String> новыеГексы(CardContext ctx) {
            java.util.Set<String> out = new java.util.LinkedHashSet<>();
            for (String гекс : ход(ctx).builtOnHexes) {
                int мои = 0;
                for (var b : ctx.me().buildingsOnField()) {
                    if (гекс.equals(b.hexId)) {
                        мои++;
                    }
                }
                // НЕ БОЛЬШЕ ОДНОГО моего здания на гексе — то самое, только что
                // поставленное: значит до этого хода моих там не было.
                //
                // Почему «не больше», а не «ровно одно». Наведение ботов
                // спрашивает карту ГИПОТЕЗОЙ: «что было бы, построй я на этом
                // гексе» — и добавляет гекс в журнал хода, НЕ ставя здания на
                // поле. При «ровно одном» чистый гекс в гипотезе давал ноль
                // (зданий там ещё нет), и бот не видел разницы между чистым
                // гексом и застроенным: замер 08.09.2026 — ни одного выполнения
                // на пятнадцать попаданий карты в руку. «Не больше одного»
                // верно и в гипотезе (ноль), и после настоящей стройки (один), а
                // гекс с чужим… то есть со своим прежним зданием даёт два и не
                // считается.
                if (мои <= 1) {
                    out.add(гекс);
                }
            }
            return out;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return !новыеГексы(ctx).isEmpty();
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            if (!satisfied(ctx)) {
                return false;
            }
            for (String снесли : ход(ctx).razedOwnHexes) {
                if (!новыеГексы(ctx).contains(снесли)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return satisfiedEnhanced(ctx) ? 1.0 : 0.8;
            }
            // Половина дела — Стройка в этот ход уже игралась: место под новое
            // здание есть, осталось поставить его на чистый гекс.
            return ход(ctx).buildOps > 0 ? 0.4 : 0.0;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "построить здание на гексе без своих зданий";
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
                "Не имей на поле ни одного незапитанного здания, при этом зданий "
                + "у тебя не меньше трёх",
                "Доведи число зданий до четырёх, сохранив запитанность всех",
                Награда.монеты(4), Награда.картаАрсенала(),
                Утиль.ДВИЖЕНИЕ,
                "Не имей на поле ни одного незапитанного здания, при этом зданий у "
                + "тебя не меньше трёх, — награда 4 монеты. Доведи "
                + "число зданий до четырёх, сохранив запитанность всех, и условие "
                + "усилено: 1 карта арсенала. Пустая ячейка ЭНР закрывается монетой "
                + "только на одно действие — на состояние это не работает.");
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
     * o19 «Военпром» — два военных здания на разных гексах, примыкающих друг
     * к другу общей стенкой.
     *
     * <p>УСИЛЕНИЕ СНИМАЕТ ОГРАНИЧЕНИЕ ПО ВОЕННОСТИ: базовое требование считает
     * только военные здания (казарма/завод/авиабаза), усиленное — цепочку из
     * трёх ЛЮБЫХ своих зданий, не обязательно военных. Это буквально то, что
     * напечатано в каталоге, и разница не случайна.
     */
    public static final class Военпром extends ЗаданиеВКоде {
        public Военпром() {
            super("o19");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Военпром", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей два своих военных здания на разных гексах, примыкающих друг "
                + "к другу общей стенкой",
                "Вытяни цепочку из трёх своих зданий, примыкающих друг к другу",
                Награда.монеты(3), Награда.картаАрсенала(),
                Утиль.ДВИЖЕНИЕ,
                "Имей два своих военных здания на разных гексах, примыкающих друг "
                + "к другу общей стенкой, — награда 3 монеты. Вытяни такую цепочку "
                + "из трёх своих зданий, и условие усилено: 1 карта арсенала. Здание, повёрнутое стенкой к соседнему гексу, и "
                + "растит зону стройки, и держит эту цепочку.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ctx.largestWallChain(true) >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ctx.largestWallChain(false) >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            // Длина цепочки — сама себе градиент: одно военное здание со стенкой
            // это половина требования, и бот видит, что достраивать.
            return доля(ctx.largestWallChain(true), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "построить два военных здания на соседних гексах общей стенкой";
        }


        @Override
        protected String действие() {
            return "build";
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

        private int источниковСОдним(CardContext ctx, boolean строго) {
            int источников = 0;
            for (var b : ctx.me().buildingsOnField()) {
                if (b.type != BuildingType.POWER_PLANT && b.type != BuildingType.COMMAND_CENTER) {
                    continue;
                }
                источников++;
                if (строго && b.energyIdle != 1) {
                    return -1;
                }
            }
            return источников;
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Коммутация", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей на каждом своём источнике энергии ровно один свободный "
                + "кубик, и источников у тебя не меньше двух",
                "Держи так три источника",
                Награда.монеты(4), Награда.модуль("assembly"),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "Имей на каждом своём источнике энергии ровно один свободный кубик, "
                + "и источников у тебя не меньше двух, — награда 4 монеты. Держи так "
                + "три источника, и условие усилено: жетон модуля сборки. Простой кубик лежит на своём источнике, а собрать его "
                + "обратно стоит гекса в Смене энергии — держать по одному на каждом "
                + "неудобно намеренно.");
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
            // ДВА УСЛОВИЯ РАЗОМ: источников хотя бы два И на КАЖДОМ ровно один
            // простой кубик. Считаем по обеим частям и берём худшую — иначе
            // четыре источника с неверными кубиками выглядели бы как «почти
            // готово», хотя до цели там дальше, чем от двух правильных.
            int источников = 0;
            int верных = 0;
            for (var b : ctx.me().buildingsOnField()) {
                if (b.type != BuildingType.POWER_PLANT
                        && b.type != BuildingType.COMMAND_CENTER) {
                    continue;
                }
                источников++;
                if (b.energyIdle == 1) {
                    верных++;
                }
            }
            if (источников == 0) {
                return 0.0;
            }
            return Math.min(доля(источников, 2), доля(верных, источников));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "оставить ровно один свободный кубик на каждом источнике энергии";
        }


        @Override
        protected String действие() {
            return "energy_swap";
        }
    }
}
