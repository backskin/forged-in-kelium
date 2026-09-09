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
            return new Лицо("Голый запас", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей в запасе ноль войск одного рода — весь род стоит на поле",
                "Так стоят сразу два рода",
                Награда.боеприпасы(2).иКартыЗаданий(1), Награда.трофеи(2),
                Утиль.ОТВЕТНЫЙ_ОГОНЬ,
                "Имей в запасе ноль войск одного рода — весь род стоит на поле, — "
                + "награда 2 боеприпаса и 1 карта задания. Если так стоят сразу два "
                + "рода, условие усилено: 2 трофея. Вывести род целиком значит "
                + "подставить его целиком: в запасе не осталось никого, кем "
                + "заменить убитого.");
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
     * o64 «Разнос трофеев» — шаги на двух РАЗНЫХ треках за один ход.
     *
     * <p>Свод разрешает одно действие Науки — один трек ({@code
     * tech.tracks_per_action}), поэтому два трека за ход означают ДВА действия
     * Науки: приказом и вторым источником (карта, свободное действие). Карта
     * нарочно дорогая по действиям и потому платит картами заданий.
     */
    public static final class РазносТрофеев extends ЗаданиеВКоде {
        public РазносТрофеев() {
            super("o64");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Разнос трофеев", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД сдай трофеи на ДВУХ разных треках технологий",
                "На трёх треках",
                Награда.картыЗаданий(2), Награда.нет(),
                Утиль.АТАКА_ОДНИМ_ВОЙСКОМ,
                "В ЭТОТ ХОД сдай трофеи на двух разных треках технологий — награда "
                + "2 карты задания. На трёх треках условие усилено. Одно действие "
                + "Науки двигает один трек, поэтому два трека — это два захода в "
                + "науку за ход.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).scienceTracksUsed.size() >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).scienceTracksUsed.size() >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            int треков = ход(ctx).scienceTracksUsed.size();
            return ступени(доля(треков, 2),
                готовность(ctx.have(kelium.core.Resource.TROPHY) >= 2));
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ход(ctx).scienceTracksUsed.size();
            return есть >= 2 ? "" : "сдать трофеи ещё на " + (2 - есть)
                + " треке технологий в этот же ход";
        }

        @Override
        protected String действие() {
            return "science";
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
            return new Лицо("Перестройка", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД снеси своё здание и построй другое",
                "Построенное встало на тот же гекс, где стояло снесённое",
                Награда.монеты(2).иКартыЗаданий(1), Награда.трофеи(1),
                Утиль.АТАКА_ОДНИМ_ВОЙСКОМ,
                "В ЭТОТ ХОД снеси своё здание и построй другое — награда 2 монеты и "
                + "1 карта задания. Если построенное встало на тот же гекс, где стояло "
                + "снесённое, условие усилено: 1 трофей. Здание не переезжает: его "
                + "сносят и ставят заново, и обе операции стоят действия.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            var ж = ход(ctx);
            return ж.razedOwnBuilding && !ж.builtOnHexes.isEmpty();
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            var ж = ход(ctx);
            for (String гекс : ж.razedOwnHexes) {
                if (ж.builtOnHexes.contains(гекс)) {
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
            return new Лицо("Списание", ОБЫЧНАЯ, ЖЕРТВА,
                "Сбрось карту арсенала",
                "Сбрось вторую",
                Награда.монеты(2).иБоеприпасы(1), Награда.модуль("attack"),
                Утиль.ОТВЕТНЫЙ_ОГОНЬ,
                "Сбрось карту арсенала — награда 2 монеты и 1 боеприпас. Сбрось "
                + "вторую, и условие усилено: жетон модуля атаки. Карта арсенала "
                + "стоит места на планшете; иногда выгоднее продать её, чем "
                + "искать, куда установить.");
        }

        @Override
        protected Map<String, Object> жертваВЗаписи() {
            return Map.of("resource", "arsenal_cards", "amount", 1);
        }

        @Override
        protected Map<String, Object> усиленнаяЖертваВЗаписи() {
            return Map.of("predicate", "sacrifice_enhanced",
                "params", Map.of("resource", "arsenal_cards", "amount", 2));
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return true;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ctx.me().arsenalHand.size() >= 2;
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
            return new Лицо("Разрядка", ОБЫЧНАЯ, ЖЕРТВА,
                "Сдай в общий запас 2 боеприпаса",
                "Сдай пять",
                Награда.монеты(2).иКартыЗаданий(1), Награда.картаАрсенала(),
                Утиль.ОТХОД,
                "Сдай в общий запас 2 боеприпаса — награда 2 монеты и 1 карта задания. "
                + "Сдай пять, и условие усилено: карта арсенала. Остаться без "
                + "патронов страшно ровно до тех пор, пока по тебе не начали "
                + "стрелять.");
        }

        @Override
        protected Map<String, Object> жертваВЗаписи() {
            return Map.of("resource", "ammo", "amount", 2);
        }

        @Override
        protected Map<String, Object> усиленнаяЖертваВЗаписи() {
            return Map.of("predicate", "sacrifice_enhanced",
                "params", Map.of("resource", "ammo", "amount", 5));
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return true;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ctx.have(kelium.core.Resource.AMMO) >= 5;
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
            return new Лицо("Бронебой", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь технику или авиацию противника",
                "И ещё два любых чужих жетона",
                Награда.монеты(4), Награда.картаАрсенала(),
                Утиль.ОБМЕН_НАУКА_ИЛИ_РЫНОК,
                "В ЭТОТ ХОД уничтожь технику или авиацию противника — награда 4 "
                + "монеты. Если снял ещё два любых чужих жетона, условие усилено: "
                + "карта арсенала. Техника и авиация дороже прочего и стоят дальше "
                + "от чужих стен: до них ещё надо дотянуться.");
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
            return new Лицо("Зачистка гекса", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь все жетоны противника на одном гексе",
                "Их на этом гексе было не меньше трёх",
                Награда.монеты(4), Награда.модуль("attack"),
                Утиль.ОБМЕН_НАУКА_ИЛИ_РЫНОК,
                "В ЭТОТ ХОД уничтожь все жетоны противника на одном гексе — награда "
                + "4 монеты. Если их там было не меньше трёх, условие усилено: жетон "
                + "модуля атаки. Вычистить гекс целиком — работа не одного выстрела: "
                + "здание закрывает гекс, и снимать приходится по очереди.");
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
            return new Лицо("Обесточивание", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей среди уничтоженных жетонов два жетона противника",
                "Один из них — энергостанция",
                Награда.монеты(4), Награда.модуль("assembly"),
                Утиль.ЗАГРАДИТЕЛЬНЫЙ_ОГОНЬ,
                "Имей среди уничтоженных жетонов два жетона противника — награда 4 "
                + "монеты. Если один из них энергостанция, условие усилено: жетон "
                + "модуля сборки. Энергостанция питает всё остальное, поэтому её "
                + "снос стоит противнику дороже собственной цены.");
        }

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
                        && b.type == BuildingType.POWER_PLANT) {
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

    /** o71 «Две войны» — трофеи от ДВУХ разных противников. */
    public static final class ДвеВойны extends ЗаданиеВКоде {
        public ДвеВойны() {
            super("o71");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Две войны", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей среди уничтоженных жетонов жетоны ДВУХ разных противников",
                "И оба этих жетона — здания",
                Награда.монеты(4), Награда.модуль("assembly"),
                Утиль.ОБМЕН_НАУКА_ИЛИ_РЫНОК,
                "Имей среди уничтоженных жетонов жетоны двух разных противников — "
                + "награда 4 монеты. Если оба этих жетона здания, условие усилено: "
                + "жетон модуля сборки. Единственная карта колоды, которую нельзя "
                + "закрыть, воюя с одним соседом.");
        }

        /** Владельцы чужих жетонов на месте уничтоженных. */
        private Set<Integer> хозяева(CardContext ctx, boolean толькоЗдания) {
            Set<Integer> out = new LinkedHashSet<>();
            for (Token t : ctx.me().destroyedTokens) {
                if (t.owner() == ctx.seat()) {
                    continue;
                }
                if (толькоЗдания && !(t instanceof BuildingToken)) {
                    continue;
                }
                out.add(t.owner());
            }
            return out;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return хозяева(ctx, false).size() >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return хозяева(ctx, true).size() >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(хозяева(ctx, false).size(), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = хозяева(ctx, false).size();
            return есть >= 2 ? ""
                : "уничтожить жетон ещё одного, ДРУГОГО противника";
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
            return new Лицо("Против сильнейшего", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь жетон игрока, у которого на поле больше войск, "
                + "чем у тебя",
                "Уничтоженный жетон был техникой или авиацией",
                Награда.монеты(2).иКартыЗаданий(1), Награда.трофеи(1),
                Утиль.РЕМОНТ_ГЕКСА,
                "В ЭТОТ ХОД уничтожь жетон игрока, у которого на поле больше войск, "
                + "чем у тебя, — награда 2 монеты и 1 карта задания. Если уничтоженный "
                + "был техникой или авиацией, условие усилено: 1 трофей. Бить "
                + "сильнейшего значит получить сдачи, и карта платит именно за это.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            var ж = ход(ctx);
            for (var e : ж.victimUnitsAtHit.entrySet()) {
                int моих = ж.myUnitsAtHit.getOrDefault(e.getKey(), 0);
                if (e.getValue() > моих) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            var типы = ход(ctx).destroyedTypes;
            return satisfied(ctx)
                && (типы.contains(UnitType.VEHICLE.code) || типы.contains(UnitType.AIRCRAFT.code));
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
            return new Лицо("Диверсия", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь энергостанцию противника",
                "Это была энергостанция третьего или четвёртого уровня",
                Награда.боеприпасы(2).иКартыЗаданий(1), Награда.трофеи(2),
                Утиль.РЕМОНТ_ГЕКСА,
                "В ЭТОТ ХОД уничтожь энергостанцию противника — награда 2 боеприпаса "
                + "и 1 карта задания. Если это была энергостанция третьего или "
                + "четвёртого уровня, условие усилено: 2 трофея. Крупные "
                + "энергостанции стоят в глубине, и лезть за ними опасно.");
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
            return new Лицо("Энергосеть", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей на поле все свои энергостанции", null,
                Награда.картыЗаданий(2), Награда.нет(),
                Утиль.БОЙ_ПЕРЕД_БОЕМ,
                "Имей на поле все свои энергостанции — награда 2 карты задания. "
                + "Полный комплект источников на поле — лучшая мишень в партии: "
                + "их нечем прикрыть и негде спрятать.");
        }
    }

    /** o75 «Рудный двор» — все четыре добытчика на поле. */
    public static final class РудныйДвор extends ВсеЗданияРода {
        public РудныйДвор() {
            super("o75", BuildingType.MINER);
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Рудный двор", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей на поле все свои добытчики", null,
                Награда.картыЗаданий(2), Награда.нет(),
                Утиль.БОЙ_ПЕРЕД_БОЕМ,
                "Имей на поле все свои добытчики — награда 2 карты задания. "
                + "Разложил всё, чем добываешь, — держи оборону или жги карту: "
                + "у добытчика прочность один.");
        }
    }

    /** o76 «Расселение» — войска на четырёх разных гексах, минимум двух родов. */
    public static final class Расселение extends ЗаданиеВКоде {
        public Расселение() {
            super("o76");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Расселение", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Займи войсками четыре разных гекса, и родов среди них не меньше двух",
                "Шесть гексов",
                Награда.монеты(3), Награда.трофеи(2),
                Утиль.РИКОШЕТ,
                "Займи войсками четыре разных гекса, и родов среди них не меньше "
                + "двух, — награда 3 монеты. Шесть гексов, и условие усилено: 2 "
                + "трофея. Растянуться по полю выгодно и опасно разом: где тонко, "
                + "там и порвут.");
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
            return гексы(ctx).size() >= 4 && родов(ctx) >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return гексы(ctx).size() >= 6 && родов(ctx) >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            return Math.min(доля(гексы(ctx).size(), 4), доля(родов(ctx), 2));
        }

        @Override
        public String needed(CardContext ctx) {
            int г = гексы(ctx).size();
            if (г < 4) {
                return "занять войсками ещё " + (4 - г) + " гекс";
            }
            return родов(ctx) >= 2 ? "" : "вывести на поле войско ДРУГОГО рода";
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
            return new Лицо("Растяжка", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Займи войсками пять разных гексов",
                "На двух из них есть здания противника",
                Награда.боеприпасы(2).иКартыЗаданий(1), Награда.картаАрсенала(),
                Утиль.БОЙ_ПЕРЕД_БОЕМ,
                "Займи войсками пять разных гексов — награда 2 боеприпаса и "
                + "1 карта задания. Если на двух из них есть здания противника, "
                + "условие усилено: 1 карта арсенала. Гекс с чужим зданием закрыт: стоять "
                + "там можно, бить оттуда — только по зданиям и вышкам.");
        }

        private Set<String> гексы(CardContext ctx) {
            Set<String> out = new LinkedHashSet<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                out.add(u.hexId);
            }
            return out;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return гексы(ctx).size() >= 5;
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
            return доля(гексы(ctx).size(), 5);
        }

        @Override
        public String needed(CardContext ctx) {
            int г = гексы(ctx).size();
            return г >= 5 ? "" : "занять войсками ещё " + (5 - г) + " гекс";
        }

        @Override
        protected String действие() {
            return "movement";
        }
    }
}
