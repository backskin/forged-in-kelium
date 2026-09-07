package kelium.cards.objectives;

import kelium.cards.Награда;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.engine.cards.CardContext;

import static kelium.cards.objectives.Лицо.Вид.ОБЫЧНАЯ;
import static kelium.cards.objectives.Лицо.Природа.ПРОИСШЕСТВИЕ;
import static kelium.cards.objectives.Лицо.Природа.СОСТОЯНИЕ;

/**
 * ЭКОНОМИКА И НАУКА, первая часть: Снаряжение, найм, добыча.
 *
 * <p>Каждая карта — свой класс со своим кодом. o01 переписана целиком: прежняя
 * формулировка «войска, собранные как боеприпасы» и «орудия» — выдумка, таких
 * слов в игре нет. Механика Снаряжения (СВОД §2.1): каждое запитанное военное здание
 * производит ЛИБО войско, ЛИБО боеприпас.
 */
public final class ЗаданияЭкономикиI {

    private ЗаданияЭкономикиI() {
    }

    /** o01 «Полный залп» — минимум два здания в Снаряжении этого хода выбрали боеприпас. */
    public static final class ПолныйЗалп extends ЗаданиеВКоде {
        public ПолныйЗалп() {
            super("o01");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Полный залп", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД действием Снаряжение произведи боеприпасы не меньше чем "
                + "двумя своими зданиями и не произведи ни одного войска",
                "Среди этих зданий есть завод или авиабаза",
                Награда.монеты(2).иБоеприпасы(2), Награда.картаАрсенала(),
                Утиль.ДОБЫЧА,
                "В ЭТОТ ХОД действием Снаряжение произведи боеприпасы не меньше чем "
                + "двумя своими зданиями и не произведи ни одного войска. Награда: "
                + "2 монеты и 2 боеприпаса. Если среди этих зданий есть завод или "
                + "авиабаза, условие усилено: 1 карта арсенала. Ход без единого нового "
                + "войска — цена полного залпа.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            var ф = ход(ctx);
            return ф.assemblyChoseUnits == 0 && ф.assemblyAmmoBuildingTypes.size() >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            if (!satisfied(ctx)) {
                return false;
            }
            var типы = ход(ctx).assemblyAmmoBuildingTypes;
            return типы.contains("factory") || типы.contains("airbase");
        }

        @Override
        public double progress(CardContext ctx) {
            var ф = ход(ctx);
            // ХОД УЖЕ ИСПОРЧЕН: требование запрещает войска, и после первого же
            // нанятого войска карта в этот ход невыполнима. Честный ноль, иначе
            // бот тянулся бы к цели, которой в этом ходу уже нет.
            if (ф.assemblyChoseUnits > 0) {
                return 0.0;
            }
            if (ф.assemblyAmmoBuildingTypes.size() >= 1) {
                return доля(ф.assemblyAmmoBuildingTypes.size(), 2);   // одно здание — половина
            }
            return готовность(доля(готовыхКСнаряжению(ctx.me()), 2));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "собрать боеприпасы минимум двумя зданиями, не производя войск";
        }


        @Override
        protected String действие() {
            return "assembly";
        }
    }

    /** o02 «Конвейер» — найм двух войск разных родов за ход. */
    public static final class Конвейер extends ЗаданиеВКоде {
        public Конвейер() {
            super("o02");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Конвейер", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД найми два войска разных родов",
                "Среди нанятых есть авиация и нет ни одной вышки",
                Награда.боеприпасы(2), Награда.трофеи(3),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "В ЭТОТ ХОД найми два войска разных родов — награда 2 боеприпаса. "
                + "Если среди нанятых есть авиация и нет ни одной вышки, условие "
                + "усилено: 3 трофея. Авиабаза требует трёх кубиков "
                + "энергии — конвейер до неё ещё надо дотянуть.");
        }

        private java.util.Set<String> нанятыхВидов(CardContext ctx) {
            java.util.Set<String> out = new java.util.LinkedHashSet<>();
            for (var e : ход(ctx).producedByType.entrySet()) {
                if (e.getValue() > 0) {
                    out.add(e.getKey());
                }
            }
            return out;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return нанятыхВидов(ctx).size() >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            var виды = нанятыхВидов(ctx);
            return виды.size() >= 2 && виды.contains("aircraft") && !виды.contains("tower");
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(нанятыхВидов(ctx).size(), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = нанятыхВидов(ctx).size();
            return есть >= 2 ? "" : "нанять ещё " + (2 - есть) + " войско другого рода";
        }


        @Override
        protected String действие() {
            return "assembly";
        }
    }

    /**
     * o04 «Жила» — два запитанных добытчика примыкают к разным тайлам зарождения.
     *
     * <p>НА ТАЙЛЕ ЗАРОЖДЕНИЯ ЖЕТОНОВ НЕ БЫВАЕТ. Добытчик к тайлу только ПРИМЫКАЕТ.
     */
    public static final class Жила extends ЗаданиеВКоде {
        public Жила() {
            super("o04");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Жила", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей запитанными два своих добытчика, примыкающих к разным "
                + "тайлам зарождения",
                "Оба примыкают к нестартовым тайлам зарождения",
                Награда.монеты(5), Награда.модуль("attack"),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "Имей запитанными два своих добытчика, примыкающих к разным тайлам "
                + "зарождения, — награда 5 монет. Если оба "
                + "примыкают к нестартовым тайлам зарождения, условие усилено: жетон модуля атаки. Своё зарождение вырабатывают все, чужое — только те, кто "
                + "дотянулся.");
        }

        /** Жадное назначение «добытчик → свой тайл» (добытчиков максимум 4). */
        private java.util.Set<String> занятыеТайлы(CardContext ctx) {
            java.util.List<java.util.List<String>> варианты = new java.util.ArrayList<>();
            for (var b : ctx.me().buildingsOnField()) {
                if (b.type != kelium.core.BuildingType.MINER || !b.powered()) {
                    continue;
                }
                java.util.List<String> тайлы = new java.util.ArrayList<>();
                for (String рядом : ctx.neighbors(b.hexId)) {
                    if (ctx.hasSpawnTile(рядом)) {
                        тайлы.add(рядом);
                    }
                }
                if (!тайлы.isEmpty()) {
                    варианты.add(тайлы);
                }
            }
            варианты.sort((a, b) -> a.size() - b.size());
            java.util.Set<String> занято = new java.util.LinkedHashSet<>();
            for (var тайлы : варианты) {
                for (String т : тайлы) {
                    if (!занято.contains(т)) {
                        занято.add(т);
                        break;
                    }
                }
            }
            return занято;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return занятыеТайлы(ctx).size() >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            var занято = занятыеТайлы(ctx);
            if (занято.size() < 2) {
                return false;
            }
            for (String т : занято) {
                if (ctx.spawnTileIsStart(т)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(занятыеТайлы(ctx).size(), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = занятыеТайлы(ctx).size();
            return есть >= 2 ? "" : "запитать ещё добытчик у другого тайла зарождения";
        }


        @Override
        protected String действие() {
            return "build";
        }
    }

    /** o05 «Выработка» — забрать последний келемий с нестартового тайла зарождения. */
    public static final class Выработка extends ЗаданиеВКоде {
        public Выработка() {
            super("o05");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Выработка", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД забери последний келемий с нестартового тайла зарождения",
                "Этим ты полностью исчерпал зарождение, и тайл ушёл с поля",
                Награда.монеты(3), Награда.модуль("assembly"),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "В ЭТОТ ХОД забери последний келемий с нестартового тайла "
                + "зарождения — награда 3 монеты. Если ты этим полностью исчерпал "
                + "зарождение и тайл ушёл с поля, условие усилено: жетон модуля сборки. Исчерпанное зарождение приближает конец партии — "
                + "считай, кому это выгодно.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).lastKeliumNonStart;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).spawnTileClaimedNonStart;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // РЫВОК ВОЗМОЖЕН, ТОЛЬКО ЕСЛИ ЖИЛА ПОЧТИ ВЫЧЕРПАНА. Требование —
            // забрать ПОСЛЕДНИЙ келемий, поэтому полная жила у добытчика к цели
            // не приближает: близость даёт лишь нестартовая жила с единственным
            // оставшимся кубиком, до которой добытчик уже дотягивается.
            // Досягаемость спрашивается у движка (minerAdjacentGridWithKelium),
            // а не переписывается здесь: правило про свои стенки и стороны
            // гекса живёт в одном месте.
            for (BuildingToken b : ctx.me().buildingsOnField()) {
                if (b.type != BuildingType.MINER || !b.powered()) {
                    continue;
                }
                String гекс = kelium.engine.Actions
                    .minerAdjacentGridWithKelium(ctx.state(), b);
                if (гекс == null) {
                    continue;
                }
                var h = ctx.state().field.get(гекс);
                if (h != null && h.spawnTile != null && !h.spawnTile.isStart
                        && h.spawnTile.kelium == 1) {
                    return готовность(true);
                }
            }
            return 0.0;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "забрать последний келемий с нестартового тайла зарождения";
        }


        @Override
        protected String действие() {
            return "mining";
        }
    }

    /**
     * o08 «Разведка недр» — добытчик забрал печатный контейнер.
     *
     * <p>«УРОВЕНЬ КОНТЕЙНЕРА» — НЕ ТЕРМИН. Усиление переписано на отказ от
     * келемия: у Добычи ровно два выхода, и не взять ни одного — настоящий отказ.
     */
    public static final class РазведкаНедр extends ЗаданиеВКоде {
        public РазведкаНедр() {
            super("o08");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Разведка недр", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД забери печатный контейнер добытчиком",
                "За весь ход ты не добыл ни одного келемия",
                Награда.монеты(2).иБоеприпасы(2), Награда.картаАрсенала(),
                Утиль.ДОБЫЧА,
                "В ЭТОТ ХОД забери печатный контейнер добытчиком — награда 2 монеты "
                + "и 2 боеприпаса. Если при этом за весь ход ты не добыл ни одного "
                + "келемия, условие усилено: 1 карта арсенала. Каждый добытчик в "
                + "Добыче выбирает одно: келемий или контейнер.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).minerTookContainer;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && ход(ctx).keliumMined == 0;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Близость даёт только добытчик, который ДОСТАЁТ до открытой ячейки
            // печатного контейнера. Проверку спрашиваем у движка той же
            // функцией, которой он строит сам выбор в Добыче, — иначе карта
            // считала бы близость по своему представлению о правиле и разошлась
            // бы с ним при первой же правке контейнеров.
            if (!kelium.engine.PrintedContainers.miningBranchOn(ctx.state())) {
                return 0.0;
            }
            for (BuildingToken b : ctx.me().buildingsOnField()) {
                if (b.type == BuildingType.MINER && b.powered()
                        && kelium.engine.PrintedContainers
                            .minableContainerHex(ctx.state(), b) != null) {
                    return готовность(true);
                }
            }
            return 0.0;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "забрать печатный контейнер добытчиком";
        }


        @Override
        protected String действие() {
            return "mining";
        }
    }
}
