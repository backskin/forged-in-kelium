package kelium.cards.objectives;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import kelium.cards.Награда;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.cards.CardContext;

import static kelium.cards.objectives.Лицо.Вид.ОБЫЧНАЯ;
import static kelium.cards.objectives.Лицо.Природа.ЖЕРТВА;
import static kelium.cards.objectives.Лицо.Природа.ПРОИСШЕСТВИЕ;
import static kelium.cards.objectives.Лицо.Природа.СОСТОЯНИЕ;

/**
 * КАРТЫ, КОТОРЫХ В КОЛОДЕ НЕ БЫЛО — по таблице дизайнера 04-06.09.2026.
 *
 * <p>Таблица (она живёт в {@code tools/таблица-задания-1-15-0.py} и там же
 * сверяет цены) описывает колоду из сорока карт семи семейств. Двадцать пять её
 * строк ложатся на карты, уже написанные раньше; пятнадцать — нет, и вот они.
 *
 * <p>ЧТО В НИХ ОБЩЕГО. Каждая просит того, чего прежняя колода не просила ни
 * разу: пустой род войск в запасе, снос собственного здания ради нового, чистку
 * гекса целиком, войну сразу с двумя соседями, полный комплект зданий одного
 * рода. Это не украшение: дизайнер добирал ими те положения, к которым игру
 * надо подталкивать, а прежняя колода их не оплачивала.
 */
public final class ЗаданияТаблицы {

    private ЗаданияТаблицы() {
    }

    // ==================================================================
    //  ЗАСАДА
    // ==================================================================

    /**
     * o63 «Голый запас» — вывести род войск на поле ЦЕЛИКОМ.
     *
     * <p>Смысл пары с ответным огнём: весь род на столе — это весь род под
     * ударом. Верх обещает, что каждый убитый жетон обойдётся убийце в рану;
     * сжёг верх — держи открытый строй сам.
     */
    public static final class ГолыйЗапас extends ЗаданиеВКоде {
        public ГолыйЗапас() {
            super("o63");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Засада» № 10 (25.09.2026).
            return печатное("Голый запас", СОСТОЯНИЕ,
                "Не имей в запасе войск какого-либо рода (0 пехоты / техники / "
                + "авиации / вышек)",
                "не имей в запасе войска двух и более родов",
                Награда.выбор("energy_swap", "movement"),
                Награда.трофеи(2),
                Утиль.ОТВЕТНЫЙ_ОГОНЬ);
        }

        /**
         * Сколько родов войск выведено ЦЕЛИКОМ: в запасе не осталось ни одного
         * жетона этого рода.
         *
         * <p>ЗАПАС — ЭТО НЕ СПИСОК ЖЕТОНОВ В РУКАХ, А ПЕЧАТНЫЙ ПРЕДЕЛ. Жетоны
         * не нарезаны заранее: они появляются в момент найма, и у игрока в
         * начале партии ровно одна пехота на поле и пусто во всём остальном.
         * Считать «в списке нет жетона без гекса» значит объявить условие
         * выполненным на первом же ходу — так первая редакция карты и
         * ошибалась. Настоящий запас — это {@code unitStock} минус то, что уже
         * нанято.
         */
        private int пустыхРодов(CardContext ctx) {
            int пусто = 0;
            for (UnitType t : UnitType.values()) {
                int предел = ctx.state().tokenStats.unitStock(t);
                if (предел <= 0) {
                    continue;
                }
                int нанято = 0;
                for (UnitToken u : ctx.me().units) {
                    if (u.type == t && u.alive() && u.hexId != null) {
                        нанято++;
                    }
                }
                if (нанято >= предел) {
                    пусто++;
                }
            }
            return пусто;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return пустыхРодов(ctx) >= 1;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return пустыхРодов(ctx) >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            // Близость по САМОМУ ВЫВЕДЕННОМУ роду: сколько его нанято из
            // печатного предела. Так карта тянет доводить начатое, а не
            // начинать заново каждым родом.
            double лучшая = 0;
            for (UnitType t : UnitType.values()) {
                int предел = ctx.state().tokenStats.unitStock(t);
                if (предел <= 0) {
                    continue;
                }
                int нанято = 0;
                for (UnitToken u : ctx.me().units) {
                    if (u.type == t && u.alive() && u.hexId != null) {
                        нанято++;
                    }
                }
                лучшая = Math.max(лучшая, доля(нанято, предел));
            }
            return Math.min(лучшая, 0.99);
        }

        @Override
        public String needed(CardContext ctx) {
            return пустыхРодов(ctx) >= 1 ? ""
                : "вывести на поле весь свой род войск: в запасе не должно "
                + "остаться ни одного его жетона";
        }

        @Override
        protected String действие() {
            return "assembly";
        }
    }

    // ==================================================================
    //  ОБЕСПЕЧЕНИЕ
    // ==================================================================

    /**
     * o64 — НАКОПИ НЕ МЕНЕЕ 10 МОНЕТ.
     *
     * <p>ПЕЧАТНАЯ КАРТА «Обеспечение» № 14 (25.09.2026) заняла место прежнего
     * «Разноса трофеев» (трофеи на двух треках за ход): той карты среди
     * рисунков нет, а в семействе «Обеспечение» четвёртой стоит эта. Прежнее имя
     * к ней не подходит, поэтому имя — печатный заголовок. Класс назван
     * по-старому только ради ссылок.
     */
    public static final class РазносТрофеев extends ЗаданиеВКоде {
        public РазносТрофеев() {
            super("o64");
        }

        /** Печатный порог монет. */
        private static final int МОНЕТ = 10;

        @Override
        public Лицо лицо() {
            return печатное("Обеспечение", СОСТОЯНИЕ,
                "Накопи не менее 10 монет",
                null,
                Награда.выбор("science", "build"), Награда.нет(),
                Утиль.АТАКА_ДВУМЯ);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ctx.have(kelium.core.Resource.COIN) >= МОНЕТ;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return false;              // усиления у карты нет
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ctx.have(kelium.core.Resource.COIN), МОНЕТ);
        }

        @Override
        public String needed(CardContext ctx) {
            int надо = МОНЕТ - ctx.have(kelium.core.Resource.COIN);
            return надо <= 0 ? "" : "накопить ещё " + надо + " монет";
        }

        @Override
        protected String действие() {
            return "market";
        }
    }

    // ==================================================================
    //  РАЗВЁРТЫВАНИЕ
    // ==================================================================

    /**
     * o65 «Перестройка» — снести своё и построить другое в тот же ход.
     *
     * <p>Переноса здания в Стройке больше нет: со зданием делают одно из двух,
     * ставят или сносят. Переезд собирается из этих двух операций, и карта
     * платит именно за него — а усиление требует попасть новым зданием на
     * освободившийся гекс.
     */
    public static final class Перестройка extends ЗаданиеВКоде {
        public Перестройка() {
            super("o65");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Развёртывание» № 18 (25.09.2026): «на том же гексе»
            // перешло в базу, усиление — два своих здания снесены на одном гексе.
            return печатное("Перестройка", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД снеси своё здание и построй на том же гексе другое.",
                "снеси 2 своих здания на одном гексе.",
                Награда.выбор("mining", "energy_swap"),
                Награда.трофеи(1).иМонеты(2),
                Утиль.КОНТРАТАКА);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            var ж = ход(ctx);
            for (String гекс : ж.razedOwnHexes) {
                if (ж.builtOnHexes.contains(гекс)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            if (!satisfied(ctx)) {
                return false;
            }
            for (int снесено : ход(ctx).razedOwnOnHex.values()) {
                if (снесено >= 2) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            var ж = ход(ctx);
            double сделано = (ж.razedOwnBuilding ? 0.5 : 0)
                + (ж.builtOnHexes.isEmpty() ? 0 : 0.5);
            return ступени(сделано, готовность(!ctx.me().buildingsOnField().isEmpty()));
        }

        @Override
        public String needed(CardContext ctx) {
            var ж = ход(ctx);
            if (!ж.razedOwnBuilding) {
                return "снести своё здание";
            }
            return ж.builtOnHexes.isEmpty() ? "построить здание после сноса" : "";
        }

        @Override
        protected String действие() {
            return "build";
        }
    }

    // ==================================================================
    //  РАСПЛАТА
    // ==================================================================

    /** o66 «Списание» — отдать карту арсенала из руки. */
    public static final class Списание extends ЗаданиеВКоде {
        public Списание() {
            super("o66");
        }

        @Override
        public Лицо лицо() {
            // УСИЛЕНИЯ У КАРТЫ НЕТ, А НАГРАДА ОДНА (решение дизайнера
            // 09.09.2026). В таблице у этой карты в колонке усиления тире, и
            // обе колонки награды — это одна и та же награда: она выдаётся
            // целиком за единственное требование. Таких карт восемь из сорока.
            // ПЕЧАТНАЯ КАРТА «Расплата» № 22 (25.09.2026).
            return печатное("Списание", ЖЕРТВА,
                "Сдай в общий запас карту арсенала",
                null,
                Награда.выбор("combat", "science"), Награда.нет(),
                Утиль.РИКОШЕТ);
        }

        @Override
        protected Map<String, Object> жертваВЗаписи() {
            return Map.of("resource", "arsenal_cards", "amount", 1);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return true;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return false;              // усиления у карты нет
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ctx.me().arsenalHand.size(), 1);
        }

        @Override
        public String needed(CardContext ctx) {
            return ctx.me().arsenalHand.isEmpty() ? "получить карту арсенала в руку" : "";
        }

        @Override
        protected String действие() {
            return "market";
        }
    }

    /** o67 «Разрядка» — сдать боеприпасы в общий запас. */
    public static final class Разрядка extends ЗаданиеВКоде {
        public Разрядка() {
            super("o67");
        }

        @Override
        public Лицо лицо() {
            // Усиления нет, награда одна и выдаётся целиком (см. «Списание»).
            // ПЕЧАТНАЯ КАРТА «Расплата» № 23 (25.09.2026).
            return печатное("Разрядка", ЖЕРТВА,
                "Сдай в общий запас 2 боеприпаса",
                null,
                Награда.выбор("mining", "build"), Награда.нет(),
                Утиль.АТАКА_ДВУМЯ);
        }

        @Override
        protected Map<String, Object> жертваВЗаписи() {
            return Map.of("resource", "ammo", "amount", 2);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return true;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return false;              // усиления у карты нет
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ctx.have(kelium.core.Resource.AMMO), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ctx.have(kelium.core.Resource.AMMO);
            return есть >= 2 ? "" : "накопить ещё " + (2 - есть) + " боеприпаса";
        }

        @Override
        protected String действие() {
            return "assembly";
        }
    }

    // ==================================================================
    //  УСТРАНЕНИЕ
    // ==================================================================

    /** o68 «Бронебой» — снять чужую технику или авиацию. */
    public static final class Бронебой extends ЗаданиеВКоде {
        public Бронебой() {
            super("o68");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Устранение» № 26 (25.09.2026).
            return печатное("Бронебой", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь технику или авиацию врага",
                "и еще 2 жетона любого врага",
                Награда.выбор("assembly", "mining"),
                Награда.монеты(4),
                Утиль.МОДУЛИ);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            var типы = ход(ctx).destroyedTypes;
            return типы.contains(UnitType.VEHICLE.code) || типы.contains(UnitType.AIRCRAFT.code);
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && ход(ctx).enemyTokensDestroyed >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            return ступени(satisfied(ctx) ? 1.0 : 0.0,
                готовность(чужойВПределахДороги(ctx, 2, т -> т instanceof UnitToken u
                    && (u.type == UnitType.VEHICLE || u.type == UnitType.AIRCRAFT))));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "уничтожить чужую технику или авиацию";
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }

    /** o69 «Зачистка гекса» — не осталось ни одного чужого жетона. */
    public static final class ЗачисткаГекса extends ЗаданиеВКоде {
        public ЗачисткаГекса() {
            super("o69");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Устранение» № 27 (25.09.2026).
            return печатное("Зачистка гекса", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь все жетоны врага на одном гексе",
                "их на этом гексе не менее 3.",
                Награда.выбор("movement", "science"),
                Награда.картаАрсенала().иСпец(1),
                Утиль.КОНТРАТАКА);
        }

        /** Гексы, где в этот ход снесено чужое и чужого больше не осталось. */
        private Map<String, Integer> вычищенные(CardContext ctx) {
            Map<String, Integer> out = new java.util.LinkedHashMap<>();
            for (var e : ход(ctx).destroyedOnHex.entrySet()) {
                if (ctx.enemyTokensOn(e.getKey()).isEmpty()) {
                    out.put(e.getKey(), e.getValue());
                }
            }
            return out;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return !вычищенные(ctx).isEmpty();
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            for (int сколько : вычищенные(ctx).values()) {
                if (сколько >= 3) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            // Готовность считается по САМОМУ БЕДНОМУ достижимому чужому гексу:
            // вычистить проще всего тот, где стоит один жетон.
            boolean естьОдиночка = false;
            for (String h : ctx.allHexes()) {
                if (ctx.enemyTokensOn(h).size() == 1 && ctx.adjacentToEnemy(h)) {
                    естьОдиночка = true;
                    break;
                }
            }
            return ступени(satisfied(ctx) ? 1.0 : 0.0, готовность(естьОдиночка),
                подготовка(чужойВПределахДороги(ctx, 2, т -> true)));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "снять с одного чужого гекса ВСЕ жетоны противника";
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }

    /** o70 «Обесточивание» — два чужих жетона на месте уничтоженных. */
    public static final class Обесточивание extends ЗаданиеВКоде {
        public Обесточивание() {
            super("o70");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Устранение» № 30 (25.09.2026): усиление — военное
            // здание (энергостанция ушла в усиление «Осады», № 29).
            return печатное("Обесточивание", СОСТОЯНИЕ,
                "Имей у себя на свалке хотя бы 2 жетона врага",
                "один из них - военное здание.",
                Награда.выбор("market", "science"),
                Награда.нет().иКартыАрсенала(2),
                Утиль.ЭВАКУАЦИЯ);
        }

        /**
         * ВОЕННЫЕ ЗДАНИЯ — те, над подписями которых они стоят на планшете войск:
         * казармы, завод, авиабаза и ЦУ.
         */
        private static final java.util.Set<BuildingType> ВОЕННЫЕ = java.util.Set.of(
            BuildingType.BARRACKS, BuildingType.FACTORY, BuildingType.AIRBASE,
            BuildingType.COMMAND_CENTER);

        private int чужих(CardContext ctx) {
            int n = 0;
            for (Token t : ctx.me().destroyedTokens) {
                if (t.owner() != ctx.seat()) {
                    n++;
                }
            }
            return n;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return чужих(ctx) >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            if (!satisfied(ctx)) {
                return false;
            }
            for (Token t : ctx.me().destroyedTokens) {
                if (t.owner() != ctx.seat() && t instanceof BuildingToken b
                        && ВОЕННЫЕ.contains(b.type)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(чужих(ctx), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = чужих(ctx);
            return есть >= 2 ? "" : "уничтожить ещё " + (2 - есть) + " чужой жетон";
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }

    /**
     * o71 — ДВА ЖЕТОНА ВРАГА НА СВАЛКЕ; усиление — оба техника и/или авиация.
     *
     * <p>ПЕЧАТНАЯ КАРТА «Устранение» № 31 (25.09.2026) заняла место прежних
     * «Двух войн» (жетоны двух РАЗНЫХ противников): той карты среди рисунков
     * нет. Прежнее имя к ней не подходит, поэтому имя — печатный заголовок.
     */
    public static final class ДвеВойны extends ЗаданиеВКоде {
        public ДвеВойны() {
            super("o71");
        }

        @Override
        public Лицо лицо() {
            return печатное("Устранение", СОСТОЯНИЕ,
                "Имей у себя на свалке хотя бы 2 жетона врага",
                "эти жетоны - техника и /или авиация.",
                Награда.выбор("mining", "science"),
                Награда.модуль("assembly").иСпец(1),
                Утиль.РИКОШЕТ);
        }

        /** Чужие жетоны на моей свалке; с {@code ударные} — только техника и авиация. */
        private int чужих(CardContext ctx, boolean ударные) {
            int n = 0;
            for (Token t : ctx.me().destroyedTokens) {
                if (t.owner() == ctx.seat()) {
                    continue;
                }
                if (ударные && !(t instanceof UnitToken u
                        && (u.type == UnitType.VEHICLE || u.type == UnitType.AIRCRAFT))) {
                    continue;
                }
                n++;
            }
            return n;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return чужих(ctx, false) >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return чужих(ctx, true) >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(чужих(ctx, false), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = чужих(ctx, false);
            return есть >= 2 ? "" : "уничтожить ещё " + (2 - есть) + " чужой жетон";
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }

    /** o72 «Против сильнейшего» — удар по тому, у кого войск больше. */
    public static final class ПротивСильнейшего extends ЗаданиеВКоде {
        public ПротивСильнейшего() {
            super("o72");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Устранение» № 32 (25.09.2026): усиление — у него же
            // хотя бы два жетона ВОЙСК.
            return печатное("Против сильнейшего", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь жетон игрока, у которого на поле больше войск, "
                + "чем у тебя",
                "уничтожь у него хотя бы 2 жетона войск.",
                Награда.выбор("assembly", "energy_swap"),
                Награда.монеты(4),
                Утиль.АТАКА_ДВУМЯ);
        }

        private static Map<Integer, java.util.List<kelium.core.TurnJournal.Убитый>>
                уСильного(CardContext ctx) {
            return убитыеУ(ctx, у -> у.victimUnits() > у.myUnits());
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return !уСильного(ctx).isEmpty();
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            for (var снятые : уСильного(ctx).values()) {
                int войск = 0;
                for (var у : снятые) {
                    if (!у.building()) {
                        войск++;
                    }
                }
                if (войск >= 2) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            boolean естьСильнее = false;
            int моих = ctx.me().unitsOnField().size();
            for (var pl : ctx.state().players) {
                if (pl.seat != ctx.seat() && pl.unitsOnField().size() > моих) {
                    естьСильнее = true;
                }
            }
            return ступени(satisfied(ctx) ? 1.0 : 0.0,
                готовность(естьСильнее && чужойВПределахДороги(ctx, 2, т -> true)));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "уничтожить жетон того, у кого войск на поле больше твоего";
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }

    /** o73 «Диверсия» — снос чужой энергостанции. */
    public static final class Диверсия extends ЗаданиеВКоде {
        public Диверсия() {
            super("o73");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Устранение» № 35 (25.09.2026).
            return печатное("Диверсия", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь энергостанцию врага",
                "это была энергостанция 3 или 4 уровня.",
                Награда.выбор("market", "build"),
                Награда.позолотой(),
                Утиль.МОДУЛИ);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).destroyedTypes.contains(
                BuildingType.POWER_PLANT.name().toLowerCase(java.util.Locale.ROOT));
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            for (int уровень : ход(ctx).destroyedPlantLevels) {
                if (уровень >= 3) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            return ступени(satisfied(ctx) ? 1.0 : 0.0,
                готовность(чужойВПределахДороги(ctx, 2, т -> т instanceof BuildingToken b
                    && b.type == BuildingType.POWER_PLANT)));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "уничтожить чужую энергостанцию";
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }

    // ==================================================================
    //  ЭКСПАНСИЯ
    // ==================================================================

    /** Общий предок двух карт «весь комплект зданий одного рода на поле». */
    abstract static class ВсеЗданияРода extends ЗаданиеВКоде {
        private final BuildingType род;

        ВсеЗданияРода(String id, BuildingType род) {
            super(id);
            this.род = род;
        }

        /** Сколько зданий этого рода у игрока всего и сколько из них на поле. */
        private int[] счёт(CardContext ctx) {
            int всего = 0;
            int наПоле = 0;
            for (BuildingToken b : ctx.me().buildings) {
                if (b.type == род) {
                    всего++;
                    if (b.hexId != null) {
                        наПоле++;
                    }
                }
            }
            return new int[]{всего, наПоле};
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            int[] c = счёт(ctx);
            return c[0] > 0 && c[1] == c[0];
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return false;      // усиления у этих карт нет вовсе
        }

        @Override
        public double progress(CardContext ctx) {
            int[] c = счёт(ctx);
            return доля(c[1], Math.max(1, c[0]));
        }

        @Override
        public String needed(CardContext ctx) {
            int[] c = счёт(ctx);
            int осталось = c[0] - c[1];
            return осталось <= 0 && c[0] > 0 ? ""
                : "поставить на поле ещё " + Math.max(1, осталось) + " своё здание этого рода";
        }

        @Override
        protected String действие() {
            return "build";
        }
    }

    /** o74 «Энергосеть» — все четыре энергостанции на поле. */
    public static final class Энергосеть extends ВсеЗданияРода {
        public Энергосеть() {
            super("o74", BuildingType.POWER_PLANT);
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Экспансия» № 36 (25.09.2026).
            return печатное("Энергосеть", СОСТОЯНИЕ,
                "Имей на поле все 4 свои энергостанции.", null,
                Награда.выбор("movement", "energy_swap"), Награда.нет(),
                Утиль.КОНТРАТАКА);
        }
    }

    /** o75 «Рудный двор» — все четыре добытчика на поле. */
    public static final class РудныйДвор extends ВсеЗданияРода {
        public РудныйДвор() {
            super("o75", BuildingType.MINER);
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Экспансия» № 38 (25.09.2026).
            return печатное("Рудный двор", СОСТОЯНИЕ,
                "Имей на поле все 4 своих добытчика.", null,
                Награда.выбор("combat", "energy_swap"), Награда.нет(),
                Утиль.ЗАГРАДИТЕЛЬНЫЙ_ОГОНЬ);
        }
    }

    /** o76 «Расселение» — войска на четырёх разных гексах, минимум двух родов. */
    public static final class Расселение extends ЗаданиеВКоде {
        public Расселение() {
            super("o76");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Экспансия» № 39 (25.09.2026): три гекса, РОВНО два
            // вида; усиление — пять гексов.
            return печатное("Расселение", СОСТОЯНИЕ,
                "Займи 3 разных гекса войсками ровно 2 видов",
                "займи ими 5 гексов.",
                Награда.выбор("assembly", "combat"),
                Награда.нет().иКартыАрсенала(2),
                Утиль.ЭВАКУАЦИЯ);
        }

        private Set<String> гексы(CardContext ctx) {
            Set<String> out = new LinkedHashSet<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                out.add(u.hexId);
            }
            return out;
        }

        private int родов(CardContext ctx) {
            Set<UnitType> out = new LinkedHashSet<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                out.add(u.type);
            }
            return out.size();
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return гексы(ctx).size() >= 3 && родов(ctx) == 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return гексы(ctx).size() >= 5 && родов(ctx) == 2;
        }

        @Override
        public double progress(CardContext ctx) {
            // РОВНО ДВА ВИДА: третий вид на поле уводит от цели так же, как
            // нехватка второго.
            double поВидам = родов(ctx) <= 2 ? доля(родов(ctx), 2) : 0.5;
            return Math.min(доля(гексы(ctx).size(), 3), поВидам);
        }

        @Override
        public String needed(CardContext ctx) {
            int г = гексы(ctx).size();
            if (г < 3) {
                return "занять войсками ещё " + (3 - г) + " гекс";
            }
            int р = родов(ctx);
            return р == 2 ? "" : р < 2 ? "вывести на поле войско ДРУГОГО рода"
                : "оставить на поле войска ровно двух родов";
        }

        @Override
        protected String действие() {
            return "movement";
        }
    }

    /** o77 «Растяжка» — пять гексов, и на двух из них чужие здания. */
    public static final class Растяжка extends ЗаданиеВКоде {
        public Растяжка() {
            super("o77");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Экспансия» № 40 (25.09.2026): гексов четыре.
            return печатное("Растяжка", СОСТОЯНИЕ,
                "Займи войсками 4 гекса на поле",
                "на 2 из них есть здания врага.",
                Награда.выбор("movement", "build"),
                Награда.монеты(3).иСпец(1),
                Утиль.РИКОШЕТ);
        }

        /** Печатное число гексов. */
        private static final int ГЕКСОВ = 4;

        private Set<String> гексы(CardContext ctx) {
            Set<String> out = new LinkedHashSet<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                out.add(u.hexId);
            }
            return out;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return гексы(ctx).size() >= ГЕКСОВ;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            if (!satisfied(ctx)) {
                return false;
            }
            int сЧужими = 0;
            for (String h : гексы(ctx)) {
                if (!ctx.enemyBuildingsOn(h).isEmpty()) {
                    сЧужими++;
                }
            }
            return сЧужими >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(гексы(ctx).size(), ГЕКСОВ);
        }

        @Override
        public String needed(CardContext ctx) {
            int г = гексы(ctx).size();
            return г >= ГЕКСОВ ? "" : "занять войсками ещё " + (ГЕКСОВ - г) + " гекс";
        }

        @Override
        protected String действие() {
            return "movement";
        }
    }
}
