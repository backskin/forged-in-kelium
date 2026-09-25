package kelium.cards.objectives;

import java.util.Map;

import kelium.cards.Награда;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.cards.CardContext;

import static kelium.cards.objectives.Лицо.Вид.ОБЫЧНАЯ;
import static kelium.cards.objectives.Лицо.Природа.ПРОИСШЕСТВИЕ;
import static kelium.cards.objectives.Лицо.Природа.СОСТОЯНИЕ;

/**
 * ВОЙНА И ДАВЛЕНИЕ, первая половина: позиция, застройка под огнём, первые удары.
 *
 * <p>Каждая карта — свой класс со своим кодом условия, усиления и одноразового
 * эффекта. Ни одна не читает YAML и ни одна не ссылается на реестр предикатов:
 * запись каталога выгружается из класса.
 *
 * <p>ЧТО ЗДЕСЬ ИСПРАВЛЕНО ПО РЕВЬЮ ДИЗАЙНЕРА, помимо переезда. Формулировки
 * приведены к терминам игры: вышку НАНИМАЮТ, а не строят; «у самой границы с
 * врагом» заменено на «гекс, соседний с гексом, где есть жетоны противника»; у
 * каждого требования-происшествия стоит приписка «В ЭТОТ ХОД», без которой карта
 * читается как «когда-нибудь сделал», а доказать это за столом нечем.
 */
public final class ЗаданияВойныI {

    private ЗаданияВойныI() {
    }

    /**
     * o03 «Опорный пункт» — две свои вышки на разных гексах вне гекса ЦУ.
     *
     * <p>БЫЛО «в этот ход найми вышку вне гекса ЦУ» — одно действие с пустого
     * места, а такого обычного задания быть не должно. Вышка неподвижна (скорость
     * ноль), поэтому две вышки на разных гексах — это две заранее продуманные
     * Сборки ЦУ, а не случайное совпадение.
     */
    public static final class ОпорныйПункт extends ЗаданиеВКоде {
        public ОпорныйПункт() {
            super("o03");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Засада» № 07 (25.09.2026).
            return печатное("Опорный пункт", СОСТОЯНИЕ,
                "Имей на поле 2 своих жетона вышки на разных гексах (обе не на гексе с ЦУ)",
                "одна из вышек стоит на гексе с жетонами врага.",
                Награда.выбор("assembly", "science"),
                Награда.трофеи(1).иСпец(1),
                Утиль.ДВИЖЕНИЕ_ДВУМЯ);
        }

        /** Гексы своих вышек, стоящих не на гексе своего ЦУ. */
        private java.util.Set<String> гексыВышек(CardContext ctx) {
            var цу = гексыЦУ(ctx.me());
            java.util.Set<String> out = new java.util.LinkedHashSet<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (u.type == UnitType.TOWER && !цу.contains(u.hexId)) {
                    out.add(u.hexId);
                }
            }
            return out;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return гексыВышек(ctx).size() >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            if (!satisfied(ctx)) {
                return false;
            }
            for (String h : гексыВышек(ctx)) {
                if (!ctx.enemyTokensOn(h).isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(гексыВышек(ctx).size(), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = гексыВышек(ctx).size();
            return есть >= 2 ? "" : "нанять ещё " + (2 - есть)
                + " вышку на гекс без своего ЦУ";
        }


        @Override
        protected String действие() {
            return "assembly";
        }
    }

    /**
     * o07 «Засада» — войско укрыто в своём военном здании того же рода.
     *
     * <p>ФОРМУЛИРОВКА ПЕРЕПИСАНА: «у самой границы с врагом» — не термин игры.
     * Механика прежняя: войско стоит ВНУТРИ своего военного здания того рода,
     * который его укрывает, а гекс этого здания соседний с гексом противника.
     */
    public static final class Засада extends ЗаданиеВКоде {
        public Засада() {
            super("o07");
        }

        /** Военные здания: любое из них может держать войско внутри. */
        private static final java.util.Set<BuildingType> ВОЕННЫЕ = java.util.Set.of(
            BuildingType.BARRACKS, BuildingType.FACTORY,
            BuildingType.AIRBASE, BuildingType.COMMAND_CENTER);

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Засада» № 08 (25.09.2026): «в гарнизоне», и
            // усиление считает ЗДАНИЯ, а не войска.
            return печатное("Засада", СОСТОЯНИЕ,
                "Имей военное здание с войском в гарнизоне, и по соседству с гексом, "
                + "где есть жетоны врага",
                "два таких военных здания.",
                Награда.выбор("movement", "energy_swap"),
                Награда.боеприпасы(2).иСпец(1),
                Утиль.МОДУЛИ);
        }

        /**
         * СКОЛЬКО СВОИХ ВОЕННЫХ ЗДАНИЙ ДЕРЖАТ ГАРНИЗОН у чужого порога.
         *
         * <p>Гарнизон — войско ВНУТРИ здания ({@code insideBuildingUid}), а не
         * просто на его гексе: так на печатной карте 25.09.2026 («с войском в
         * гарнизоне»). Рода войска и здания карта не сличает.
         */
        private int укрытых(CardContext ctx) {
            java.util.Set<Integer> здания = new java.util.HashSet<>();
            for (BuildingToken b : ctx.me().buildingsOnField()) {
                if (!ВОЕННЫЕ.contains(b.type) || !ctx.adjacentToEnemy(b.hexId)) {
                    continue;
                }
                for (UnitToken u : ctx.me().unitsOnField()) {
                    if (u.insideBuildingUid != null && u.insideBuildingUid == b.uid) {
                        здания.add(b.uid);
                        break;
                    }
                }
            }
            return здания.size();
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return укрытых(ctx) >= 1;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return укрытых(ctx) >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(укрытых(ctx), 1);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "завести войско в гарнизон своего военного здания рядом с врагом";
        }


        @Override
        protected String действие() {
            return "movement";
        }
    }

    /** o11 «Передовая база» — стройка на гексе, соседнем с войсками противника. */
    public static final class ПередоваяБаза extends ЗаданиеВКоде {
        public ПередоваяБаза() {
            super("o11");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Развёртывание» № 16 (25.09.2026).
            return печатное("Передовая база", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД построй свое здание на гексе, соседнем с гексом, где "
                + "есть войска врага",
                "на том же соседнем гексе есть здание врага.",
                Награда.выбор("movement", "assembly"),
                Награда.нет().иКартыАрсенала(2),
                Утиль.МОДУЛИ);
        }

        /** Было ли в этот ход строительство рядом с чужим жетоном нужного вида. */
        /**
         * Было ли в этот ход строительство рядом с гексом, где стоят чужие войска;
         * с {@code иЗдание} — и на ТОМ ЖЕ соседнем гексе есть чужое здание
         * («на том же соседнем гексе есть здание врага»).
         */
        private boolean строилРядом(CardContext ctx, boolean иЗдание) {
            for (String где : ход(ctx).builtOnHexes) {
                for (String рядом : ctx.neighbors(где)) {
                    boolean войска = false;
                    for (Token t : ctx.enemyTokensOn(рядом)) {
                        if (t instanceof UnitToken) {
                            войска = true;
                            break;
                        }
                    }
                    if (войска && (!иЗдание || !ctx.enemyBuildingsOn(рядом).isEmpty())) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return строилРядом(ctx, false);
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return строилРядом(ctx, true);
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Рывок возможен, только если в зоне стройки ЕСТЬ гекс рядом с чужим
            // войском. Проверка повторяет условие карты, но смотрит в будущее:
            // не «где я построил», а «где я МОГУ построить».
            return готовность(естьГексПодСтройку(ctx, hid -> {
                for (String рядом : ctx.neighbors(hid)) {
                    for (Token t : ctx.enemyTokensOn(рядом)) {
                        if (t instanceof UnitToken) {
                            return true;
                        }
                    }
                }
                return false;
            }));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "построить здание на гексе рядом с войсками противника";
        }


        @Override
        protected String действие() {
            return "build";
        }
    }

    /**
     * o12 — ДВА ЗДАНИЯ НА РАЗНЫХ ГЕКСАХ, ОБА В ДВУХ ГЕКСАХ ОТ СВОЕГО ЦУ.
     *
     * <p>ПЕЧАТНАЯ КАРТА «Развёртывание» № 19 (25.09.2026) заменила прежнюю
     * «Наглую стройку» (стройка на гексе с жетонами врага): такой карты среди
     * рисунков дизайнера нет, а номер 19 стоит в семействе ровно на её месте.
     * Прежнее имя карте больше не подходит, поэтому имя — печатный заголовок.
     * Класс назван по-старому только ради ссылок на него.
     */
    public static final class НаглаяСтройка extends ЗаданиеВКоде {
        public НаглаяСтройка() {
            super("o12");
        }

        @Override
        public Лицо лицо() {
            return печатное("Развёртывание", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД построй 2 здания на разных гексах",
                "оба на расстоянии 2 гекса от твоего ЦУ.",
                Награда.выбор("mining", "combat"),
                Награда.модуль("assembly").иСпец(1),
                Утиль.ОТВЕТНЫЙ_ОГОНЬ);
        }

        /** Гексы, где в этот ход построено своё здание. */
        private java.util.Set<String> построено(CardContext ctx) {
            return new java.util.LinkedHashSet<>(ход(ctx).builtOnHexes);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return построено(ctx).size() >= 2;
        }

        /**
         * «НА РАССТОЯНИИ 2 ГЕКСА ОТ ТВОЕГО ЦУ» — ровно два гекса по полю от
         * ближайшего своего ЦУ. Читается буквально: не «два и дальше».
         */
        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            if (!satisfied(ctx)) {
                return false;
            }
            var цу = гексыЦУ(ctx.me());
            int годных = 0;
            for (String h : построено(ctx)) {
                if (гексовДо(ctx, h, цу) == 2) {
                    годных++;
                }
            }
            return годных >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            return ступени(доля(построено(ctx).size(), 2),
                готовность(ctx.have(kelium.core.Resource.COIN) >= 2));
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = построено(ctx).size();
            return есть >= 2 ? ""
                : "построить ещё " + (2 - есть) + " здание на другом гексе";
        }


        @Override
        protected String действие() {
            return "build";
        }
    }


    /**
     * o17 «Штаб на передовой» — своё ВОЕННОЕ здание у чужого ЦУ.
     *
     * <p>БЫЛО «перенеси ЦУ на гекс, где ещё никто не строил». Такого состояния в
     * игре нет: снятое здание не оставляет следа, и проверить историю гекса за
     * столом нечем. Тогда карту переписали на «в этот ход построй или перенеси
     * своё ЦУ ближе к чужому».
     *
     * <p>И ЭТО ТОЖЕ УМЕРЛО. Переноса зданий в базовой Стройке больше нет
     * (решение 06.09.2026), а уничтоженное ЦУ ставится обратно только
     * СПЕЦ-действием — то есть «поставить ЦУ в этот ход» может лишь тот, у кого
     * его только что снесли. Замер 08.09.2026: ноль выполнений на двадцать шесть
     * попаданий в руку, близость никогда выше 0.38.
     *
     * <p>РЕШЕНО ТАК: передовой штаб — это ВОЕННОЕ ЗДАНИЕ у чужого ЦУ, а не
     * переезд собственного ЦУ. Условие стало состоянием, достижимым обычной
     * Стройкой: доведи свои жетоны до чужого двора и поставь там казарму, завод
     * или авиабазу. Смысл карты тот же — производство под самым носом врага.
     */
    public static final class ШтабНаПередовой extends ЗаданиеВКоде {
        public ШтабНаПередовой() {
            super("o17");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Развёртывание» № 15 (25.09.2026): база — рядом с
            // любым чужим зданием, усиление — рядом с чужим ЦУ.
            return печатное("Штаб на передовой", СОСТОЯНИЕ,
                "Имей своё здание на гексе, соседнем с гексом, где есть здание врага",
                "с гексом, где есть ЦУ врага",
                Награда.выбор("assembly", "build"),
                Награда.монеты(3).иСпец(1),
                Утиль.ЗАКРОМА);
        }

        /** Есть ли своё здание по соседству с гексом, где стоит чужое здание. */
        private boolean уЧужогоЗдания(CardContext ctx) {
            for (BuildingToken b : ctx.me().buildingsOnField()) {
                for (String рядом : ctx.neighbors(b.hexId)) {
                    if (!ctx.enemyBuildingsOn(рядом).isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Гексы, где стоят ЦУ противников. */
        private java.util.Set<String> чужиеЦУ(CardContext ctx) {
            java.util.Set<String> out = new java.util.LinkedHashSet<>();
            for (var p : ctx.state().players) {
                if (p.seat != ctx.seat()) {
                    out.addAll(ctx.cuHexesOf(p.seat));
                }
            }
            return out;
        }

        /**
         * ГЕКСЫ СВОИХ ЗДАНИЙ, СОСЕДНИХ С ЧУЖИМ ЦУ.
         *
         * <p>Здание ЛЮБОЕ, а не только военное (таблица дизайнера 06.09.2026):
         * карта про то, чтобы дотянуться стройкой до чужого штаба, и добытчик у
         * чужого порога стоит там ровно так же дорого.
         */
        private java.util.Set<String> уЧужогоЦУ(CardContext ctx) {
            var чужие = чужиеЦУ(ctx);
            java.util.Set<String> out = new java.util.LinkedHashSet<>();
            if (чужие.isEmpty()) {
                return out;
            }
            for (BuildingToken b : ctx.me().buildingsOnField()) {
                for (String рядом : ctx.neighbors(b.hexId)) {
                    if (чужие.contains(рядом)) {
                        out.add(b.hexId);
                        break;
                    }
                }
            }
            return out;
        }

        /** Наименьшее расстояние от своего здания до чужого здания — для близости. */
        private Integer ближайшее(CardContext ctx) {
            java.util.Set<String> чужие = new java.util.LinkedHashSet<>();
            for (kelium.core.Token t : ctx.enemyTokensOnField()) {
                if (t instanceof BuildingToken b && b.hexId != null) {
                    чужие.add(b.hexId);
                }
            }
            if (чужие.isEmpty()) {
                return null;
            }
            Integer лучшее = null;
            for (BuildingToken b : ctx.me().buildingsOnField()) {
                Integer d = ctx.distance(b.hexId, чужие);
                if (d != null && (лучшее == null || d < лучшее)) {
                    лучшее = d;
                }
            }
            return лучшее;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return уЧужогоЗдания(ctx);
        }

        /** ЦУ — тоже здание, поэтому усиление само влечёт базу. */
        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return !уЧужогоЦУ(ctx).isEmpty();
        }

        @Override
        public double progress(CardContext ctx) {
            // ГРАДИЕНТ ПО ПРОЙДЕННОМУ ПУТИ, а не «да/нет»: близость карты здесь
            // буквально есть близость на поле. Порог требования — соседний гекс;
            // отсчёт от шести, дальше разница для карты неразличима.
            if (satisfied(ctx)) {
                return 1.0;
            }
            Integer d = ближайшее(ctx);
            if (d == null) {
                return 0.0;
            }
            return доля(Math.max(0, 6 - d), 5) * 0.8;
        }

        @Override
        public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "";
            }
            Integer d = ближайшее(ctx);
            return d == null ? "построить здание"
                : "построить здание на " + Math.max(1, d - 1)
                    + " гекс(а) ближе к чужому зданию";
        }


        @Override
        protected String действие() {
            return "build";
        }
    }

    /**
     * o21 «Первая кровь» — удар по тому, у кого войск на поле больше твоего;
     * усиление — у него же два жетона прочностью 2 и выше.
     *
     * <p>ПЕЧАТНАЯ КАРТА «Устранение» № 33 (25.09.2026). Условие у неё то же, что
     * у № 32 (o72), различаются усиление, награды и верх. Прежняя редакция
     * («уничтожь два жетона противника», усиление — один из них толстый)
     * среди рисунков не встречается; усиление про прочность перешло сюда.
     */
    public static final class ПерваяКровь extends ЗаданиеВКоде {
        public ПерваяКровь() {
            super("o21");
        }

        @Override
        public Лицо лицо() {
            return печатное("Первая кровь", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь жетон игрока, у которого на поле больше войск, "
                + "чем у тебя",
                "2 жетона с прочностью 2 и выше.",
                Награда.выбор("market", "science"),
                Награда.картаАрсенала().иСпец(1),
                Утиль.ЗАКРОМА);
        }

        private static java.util.Map<Integer, java.util.List<kelium.core.TurnJournal.Убитый>>
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
                int толстых = 0;
                for (var у : снятые) {
                    if (у.hp() >= 2) {
                        толстых++;
                    }
                }
                if (толстых >= 2) {
                    return true;
                }
            }
            return false;
        }

        /** Сколько чужих жетонов я достаю выстрелом прямо сейчас. */
        private int целейВДосягаемости(CardContext ctx) {
            java.util.Set<Integer> цели = new java.util.LinkedHashSet<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                for (String гекс : ctx.attackReach(u)) {
                    for (Token t : ctx.enemyTokensOn(гекс)) {
                        цели.add(System.identityHashCode(t));
                    }
                }
            }
            return цели.size();
        }

        @Override
        public double progress(CardContext ctx) {
            // ДВЕ СТУПЕНИ СВЕРХ СЧЁТА УБИТЫХ. Прежде близость считала только
            // уничтоженных В ЭТОТ ХОД, то есть до Боя была нулём — карта молчала
            // ровно тогда, когда решается, идти ли за ней. Замер 08.09.2026: 83%
            // попаданий в руку — близость ноль за всё время.
            //
            // Теперь карта говорит и о средствах: две цели под выстрелом и
            // боеприпасы на два удара — средства собраны; цели в двух гексах
            // хода — дорога есть.
            if (satisfied(ctx)) {
                return 1.0;
            }
            int моих = ctx.me().unitsOnField().size();
            boolean естьСильнее = false;
            for (var pl : ctx.state().players) {
                if (pl.seat != ctx.seat() && pl.unitsOnField().size() > моих) {
                    естьСильнее = true;
                }
            }
            boolean хватаетБоеприпасов = ctx.have(kelium.core.Resource.AMMO) >= 1;
            return ступени(
                готовность(естьСильнее && целейВДосягаемости(ctx) >= 1 && хватаетБоеприпасов),
                подготовка(естьСильнее && чужойВПределахДороги(ctx, 2, t -> true)));
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


    /**
     * o25 «Осада» — чужое здание среди уничтоженных жетонов.
     *
     * <p>БЫЛО «нанеси удар по зданию за один ход»: удара за два хода не бывает,
     * приписка была пустой. Стало проверяемое состояние места уничтоженных жетонов, а
     * усиление платит за чужую экономику — снесённый добытчик дороже казармы.
     */
    public static final class Осада extends ЗаданиеВКоде {
        public Осада() {
            super("o25");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Устранение» № 29 (25.09.2026): на свалке нужно
            // ЗДАНИЕ врага, усиление — два здания, одно из них энергостанция.
            // «Свалка» на карте — это место уничтоженных жетонов игрока.
            return печатное("Осада", СОСТОЯНИЕ,
                "Имей у себя на свалке хотя бы 1 жетон здания врага",
                "2 жетона здания; 1 из них - энергостанция.",
                Награда.выбор("market", "build"),
                Награда.модуль("assembly").иСпец(1),
                Утиль.ЗАГРАДИТЕЛЬНЫЙ_ОГОНЬ);
        }

        /** Чужие ЗДАНИЯ на моей свалке (месте уничтоженных жетонов). */
        private java.util.List<BuildingToken> чужие(CardContext ctx) {
            java.util.List<BuildingToken> out = new java.util.ArrayList<>();
            for (Token t : ctx.me().destroyedTokens) {
                if (t.owner() != ctx.seat() && t instanceof BuildingToken b) {
                    out.add(b);
                }
            }
            return out;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return !чужие(ctx).isEmpty();
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            var чуж = чужие(ctx);
            if (чуж.size() < 2) {
                return false;
            }
            for (BuildingToken b : чуж) {
                if (b.type == BuildingType.POWER_PLANT) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Близость — по САМОМУ ПОБИТОМУ чужому зданию в пределах досягаемости:
            // здание с одной оставшейся прочностью это почти трофей, целое —
            // почти ничего. Недосягаемые здания не считаем вовсе: до них у бота
            // нет хода, и близость по ним была бы обманом.
            if (ctx.have(kelium.core.Resource.AMMO) < 1) {
                return 0.0;
            }
            // МЕРА — ОСТАТОК ПРОЧНОСТИ, а не доля от полной: полной прочности
            // здания жетон не помнит (бонусы правил её меняют), и выдумывать
            // максимум значило бы считать близость по неверной шкале. Остаток
            // же говорит ровно то, что нужно: 1 прочность — почти трофей.
            double лучшая = 0.0;
            for (UnitToken u : ctx.me().unitsOnField()) {
                for (String гекс : ctx.attackReach(u)) {
                    for (Token t : ctx.enemyBuildingsOn(гекс)) {
                        if (t instanceof BuildingToken b && b.hp > 0) {
                            лучшая = Math.max(лучшая, 1.0 / b.hp);
                        }
                    }
                }
            }
            // ПОДГОТОВКА: чужого здания под выстрелом нет, но оно есть в двух
            // гексах хода — дорога к осаде существует. Прежде близость обрывалась
            // нулём в 67% попаданий карты в руку, и бот не вёл войска к чужой
            // застройке ради этой карты.
            return ступени(готовность(лучшая),
                подготовка(чужойВПределахДороги(ctx, 2, t -> t instanceof BuildingToken)));
        }

        @Override
        public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "";
            }
            return progress(ctx) >= 0.3
                ? "снести чужое здание и взять его на место уничтоженных жетонов"
                : "подвести войска к чужим зданиям";
        }


        @Override
        protected String действие() {
            return "combat";
        }
    }
}
