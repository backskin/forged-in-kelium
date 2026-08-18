package kelium.cards.objectives;

import kelium.cards.Награда;
import kelium.core.BuildingType;
import kelium.core.Resource;
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
                Награда.боеприпасы(3), Награда.обломки(2).иКартыЗаданий(1),
                "СВОБОДНЫЙ БОЙ",
                "В ЭТОТ ХОД выполни две строительные операции на двух гексах, не "
                + "соседних между собой, — награда 3 боеприпаса. Выполни три "
                + "операции на трёх попарно несоседних гексах, и условие усилено: "
                + "2 обломка и карта задания. Зона стройки растёт от твоих стенок, "
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
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "выполнить стройку ещё на одном гексе, не соседнем с уже занятыми";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободныйБой(ctx);
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
                "В ЭТОТ ХОД перенеси два своих здания на другие гексы",
                "Одно из перенесённых — твоё ЦУ",
                Награда.монеты(3), Награда.обломки(2).иКартыЗаданий(1),
                "СМЕНА ЭНЕРГИИ ИЛИ СМЕНА МОДУЛЕЙ",
                "В ЭТОТ ХОД перенеси два своих здания на другие гексы — награда 3 "
                + "монеты. Если одно из перенесённых — твоё ЦУ, условие усилено: "
                + "2 обломка и карта задания. ЦУ переносится не чаще раза за ход, "
                + "так что второе здание придётся подгадать.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).movedAnyBuildingUids.size() >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && ход(ctx).movedCuThisTurn;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ход(ctx).movedAnyBuildingUids.size(), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ход(ctx).movedAnyBuildingUids.size();
            return есть >= 2 ? "" : "перенести ещё " + (2 - есть) + " здание";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return ctx.energyOrModules();
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
                Награда.монеты(2).иБоеприпасы(2), Награда.картыЗаданий(1),
                "+1 К СКОРОСТИ ОДНОГО РОДА ДО КОНЦА ХОДА",
                "Не имей на поле ни одного незапитанного здания, при этом зданий "
                + "у тебя не меньше трёх, — награда 2 монеты и 2 боеприпаса. Доведи "
                + "число зданий до четырёх, сохранив запитанность всех, и условие "
                + "усилено: карта задания. Пустая ячейка ЭНР закрывается монетой "
                + "только на одно действие — на состояние это не работает.");
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
        public boolean burn(CardContext ctx) {
            return ctx.speedBoost();
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
                Награда.монеты(3), Награда.модуль("assembly").иКартыЗаданий(1),
                "КОНВЕРСИЯ: 1 келемий -> 1 боеприпас",
                "Имей два своих военных здания на разных гексах, примыкающих друг "
                + "к другу общей стенкой, — награда 3 монеты. Вытяни такую цепочку "
                + "из трёх своих зданий, и условие усилено: жетон модуля сборки и "
                + "карта задания. Здание, повёрнутое стенкой к соседнему гексу, и "
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
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "построить два военных здания на соседних гексах общей стенкой";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return ctx.convert(Resource.KELIUM, Resource.AMMO, 1);
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
                Награда.монеты(4), Награда.модуль("assembly").иКартыЗаданий(1),
                "СМЕНА ЭНЕРГИИ ИЛИ СМЕНА МОДУЛЕЙ",
                "Имей на каждом своём источнике энергии ровно один свободный кубик, "
                + "и источников у тебя не меньше двух, — награда 4 монеты. Держи так "
                + "три источника, и условие усилено: жетон модуля сборки и карта "
                + "задания. Простой кубик лежит на своём источнике, а собрать его "
                + "обратно стоит гекса в Смене энергии — держать по одному на "
                + "каждом неудобно намеренно.");
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
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "оставить ровно один свободный кубик на каждом источнике энергии";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return ctx.energyOrModules();
        }

        @Override
        protected String действие() {
            return "energy_swap";
        }
    }
}
