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
                "В ЭТОТ ХОД произведи только боеприпасы и ни одного войска",
                "Эти здания - завод и авиабаза",
                Награда.монеты(2).иКартыЗаданий(1), Награда.картаАрсенала(),
                Утиль.ОБМЕН_НАУКА_ИЛИ_РЫНОК,
                "В ЭТОТ ХОД произведи только боеприпасы и ни одного войска — награда "
                + "2 монеты и 1 карта задания. Если эти здания - завод и авиабаза, "
                + "условие усилено: 1 карта арсенала. Ход без единого нового "
                + "войска — цена полного залпа.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            var ф = ход(ctx);
            // ТРЕБОВАНИЕ УПРОЩЕНО (таблица дизайнера 04.09.2026): порога «не
            // меньше двух зданий» больше нет — важен сам выбор в Сборке, когда
            // весь ход отдан боеприпасам. Дороже всего в нём именно отказ от
            // войск, а не число труб.
            return ф.assemblyChoseUnits == 0 && !ф.assemblyAmmoBuildingTypes.isEmpty();
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            if (!satisfied(ctx)) {
                return false;
            }
            var типы = ход(ctx).assemblyAmmoBuildingTypes;
            return типы.contains("factory") && типы.contains("airbase");
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
            if (!ф.assemblyAmmoBuildingTypes.isEmpty()) {
                return 1.0;
            }
            return готовность(доля(готовыхКСнаряжению(ctx.me()), 1));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "собрать в этот ход только боеприпасы, не производя войск";
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
                Награда.монеты(2).иКартыЗаданий(1), Награда.трофеи(2),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "В ЭТОТ ХОД найми два войска разных родов — награда 2 монеты и 1 карта "
                + "задания. Если среди нанятых есть авиация и нет ни одной вышки, условие "
                + "усилено: 2 трофея. Авиабаза требует трёх кубиков энергии — конвейер до "
                + "неё ещё надо дотянуть.");
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

        /** Сколько РАЗНЫХ родов войск я вообще способен нанять Сборкой. */
        private int родовПодРукой(CardContext ctx, boolean толькоЗапитанные) {
            java.util.Set<String> роды = new java.util.LinkedHashSet<>();
            for (BuildingToken b : ctx.me().buildingsOnField()) {
                var род = kelium.engine.Actions.ASSEMBLY_UNIT.get(b.type);
                if (род != null && (!толькоЗапитанные || b.powered())) {
                    роды.add(род.code);
                }
            }
            return роды.size();
        }

        @Override
        public double progress(CardContext ctx) {
            // ТРИ СТУПЕНИ, А НЕ ОДНА. Прежде близость считала только УЖЕ нанятых
            // в этот ход, то есть до Сборки была ровно нулём — и бот не видел,
            // что до конвейера ему не хватает второго ЗАПИТАННОГО военного
            // здания. Замер 08.09.2026: ни одного выполнения на двадцать четыре
            // попадания в руку, близость никогда выше половины.
            //
            // Теперь карта отвечает и на вопрос «а чем я это сделаю»: два
            // запитанных военных здания разных родов — средства собраны
            // (готовность); два здания при нехватке энергии — дорога есть
            // (подготовка). По этим ступеням наведение и ведёт бота: сперва
            // построить второе здание, потом развести на него энергию.
            return ступени(
                доля(нанятыхВидов(ctx).size(), 2),
                готовность(родовПодРукой(ctx, true) >= 2),
                подготовка(родовПодРукой(ctx, false) >= 2));
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = нанятыхВидов(ctx).size();
            if (есть >= 2) {
                return "";
            }
            if (родовПодРукой(ctx, true) < 2) {
                return родовПодРукой(ctx, false) >= 2
                    ? "запитать второе военное здание другого рода"
                    : "построить второе военное здание другого рода";
            }
            return "нанять ещё " + (2 - есть) + " войско другого рода";
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
                "Имей на поле два своих добытчика с полной энергией, примыкающих "
                + "к разным зарождениям",
                "Оба примыкают к большим зарождениям",
                Награда.монеты(3), Награда.модуль("attack"),
                Утиль.ЗАГРАДИТЕЛЬНЫЙ_ОГОНЬ,
                "Имей на поле два своих добытчика с полной энергией, примыкающих к "
                + "разным зарождениям, — награда 3 монеты. Если оба примыкают к большим "
                + "зарождениям, условие усилено: жетон модуля атаки. Своё зарождение "
                + "вырабатывают все, чужое — только те, кто дотянулся.");
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

    /** o05 «Выработка» — забрать последний келемий с зарождения. */
    public static final class Выработка extends ЗаданиеВКоде {
        public Выработка() {
            super("o05");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Выработка", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД забери последний келемий с зарождения",
                "ты исчерпал зарождение - оно ушло с поля",
                Награда.монеты(3), Награда.модуль("assembly"),
                Утиль.ОТХОД,
                "В ЭТОТ ХОД забери последний келемий с зарождения — награда 3 монеты. "
                + "Если ты исчерпал зарождение и оно ушло с поля, условие усилено: "
                + "жетон модуля сборки. Исчерпанное зарождение приближает конец "
                + "партии — считай, кому это выгодно.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            // ЗАРОЖДЕНИЕ ЛЮБОЕ, стартовое или большое: так стоит в таблице
            // дизайнера. Прежде карта требовала большое (в старых словах —
            // «нестартовое»), и на карте это было напечатано, но таблица выше.
            return ход(ctx).tookLastKeliumFromGrid;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).spawnTileClaimed;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // РЫВОК ВОЗМОЖЕН, ТОЛЬКО ЕСЛИ ЖИЛА ПОЧТИ ВЫЧЕРПАНА. Требование —
            // забрать ПОСЛЕДНИЙ келемий, поэтому полная жила у добытчика к цели
            // не приближает: близость даёт лишь жила с единственным оставшимся
            // кубиком, до которой добытчик уже дотягивается.
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
                if (h != null && h.spawnTile != null && h.spawnTile.kelium == 1) {
                    return готовность(true);
                }
            }
            // ПОДГОТОВКА: почти вычерпанная жила есть, и до неё можно
            // ДОСТРОИТЬСЯ — в зоне стройки есть примыкающий к ней гекс. Прежде
            // близость обрывалась нулём: 95% попаданий карты в руку — ровно ноль,
            // потому что бот не связывал стройку добытчика у нужной жилы с этой
            // картой. Порог два кубика, а не один: один добытчик забирает до двух.
            java.util.Set<String> уЖилы = new java.util.LinkedHashSet<>();
            for (String hid : ctx.allHexes()) {
                var h = ctx.state().field.get(hid);
                if (h != null && h.spawnTile != null
                        && h.spawnTile.kelium > 0 && h.spawnTile.kelium <= 2) {
                    уЖилы.addAll(ctx.neighbors(hid));
                }
            }
            return подготовка(естьГексПодСтройку(ctx, уЖилы::contains));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "забрать последний келемий с зарождения";
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
                "В ЭТОТ ХОД забери 3 контейнера (добытчиками или войсками)",
                null,
                Награда.монеты(2).иБоеприпасы(1), Награда.нет(),
                Утиль.АТАКА_ОДНИМ_ВОЙСКОМ,
                "В ЭТОТ ХОД забери 3 контейнера (добытчиками или войсками) — "
                + "награда 2 монеты и 1 боеприпас. Усиления у карты нет: три "
                + "контейнера за ход и так работа всей смены. Каждый добытчик в "
                + "Добыче выбирает одно: келемий или контейнер.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).containersTaken >= 3;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return false;      // усиления у карты нет (таблица дизайнера 04.09.2026)
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // УЖЕ ВЗЯТОЕ ЗА ХОД — главная часть близости: карта считает штуки,
            // и два контейнера из трёх это две трети, а не «ещё не начал».
            int взято = ход(ctx).containersTaken;
            if (взято > 0) {
                return доля(взято, 3);
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
            // ПОДГОТОВКА: добытчика у контейнера ещё нет, но его можно ПОСТАВИТЬ
            // — в зоне стройки есть гекс, с которого контейнер достанется.
            // Прежде близость обрывалась нулём, и бот не связывал стройку
            // добытчика с этой картой: 84% попаданий в руку — ровно ноль.
            java.util.Set<String> сКонтейнером = new java.util.LinkedHashSet<>();
            for (String hid : ctx.allHexes()) {
                var h = ctx.state().field.get(hid);
                if (h != null && h.containerCell >= 0) {
                    сКонтейнером.add(hid);
                    сКонтейнером.addAll(ctx.neighbors(hid));
                }
            }
            return подготовка(естьГексПодСтройку(ctx, сКонтейнером::contains));
        }

        @Override
        public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "";
            }
            int взято = ход(ctx).containersTaken;
            return взято > 0 ? "забрать ещё " + (3 - взято) + " контейнер за этот ход"
                : "поставить добытчик рядом с печатным контейнером";
        }


        @Override
        protected String действие() {
            return "mining";
        }
    }
}
