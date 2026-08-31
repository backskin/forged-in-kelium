package kelium.cards.objectives;

import java.util.List;
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
            return new Лицо("Опорный пункт", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей на поле две свои вышки на разных гексах, и ни одна из них "
                + "не стоит на гексе с твоим ЦУ",
                "Хотя бы одна из этих вышек стоит на гексе, где есть жетоны противника",
                Награда.боеприпасы(3), Награда.картаАрсенала(),
                "СВОБОДНОЕ ДВИЖЕНИЕ",
                "Имей на поле две свои вышки на разных гексах, и ни одна из них не "
                + "стоит на гексе с твоим ЦУ, — награда 3 боеприпаса. Если хотя бы "
                + "одна из этих вышек стоит на гексе, где есть жетоны противника, "
                + "условие усилено: 1 карта арсенала. Вышка не "
                + "двигается вовсе, поэтому опорный пункт выбирают один раз и "
                + "навсегда.");
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
        public boolean burn(CardContext ctx) {
            return свободноеДвижение(ctx);
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

        /** Какое здание укрывает какой род войск (СВОД §5.3). */
        private static final Map<UnitType, BuildingType> УКРЫТИЕ = Map.of(
            UnitType.INFANTRY, BuildingType.BARRACKS,
            UnitType.VEHICLE, BuildingType.FACTORY,
            UnitType.AIRCRAFT, BuildingType.AIRBASE);

        @Override
        public Лицо лицо() {
            return new Лицо("Засада", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей своё войско внутри своего военного здания того же рода, "
                + "стоящего на гексе, соседнем с гексом, где есть жетоны противника",
                "Держи так сразу два своих войска",
                Награда.боеприпасы(3), Награда.картаАрсенала(),
                "СВОБОДНЫЙ БОЙ",
                "Имей своё войско внутри своего военного здания того же рода, "
                + "стоящего на гексе, соседнем с гексом, где есть жетоны "
                + "противника, — награда 3 боеприпаса. Держи так сразу два войска, "
                + "и условие усилено: 1 карта арсенала. Гарнизон в "
                + "здании не виден до первого выстрела.");
        }

        private int укрытых(CardContext ctx) {
            int n = 0;
            for (UnitToken u : ctx.me().unitsOnField()) {
                BuildingType надо = УКРЫТИЕ.get(u.type);
                if (надо == null) {
                    continue;
                }
                for (BuildingToken b : ctx.me().buildingsOnField()) {
                    if (b.type == надо && b.hexId.equals(u.hexId)
                            && ctx.adjacentToEnemy(u.hexId)) {
                        n++;
                        break;
                    }
                }
            }
            return n;
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
                : "завести войско в своё военное здание того же рода у чужого гекса";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободныйБой(ctx);
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
            return new Лицо("Передовая база", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД построй своё здание на гексе, соседнем с гексом, где "
                + "есть войска противника",
                "В ЭТОТ ХОД построй его на гексе, соседнем с гексом, где есть "
                + "здание противника",
                Награда.монеты(2).иБоеприпасы(3), Награда.картаАрсенала(),
                "СВОБОДНОЕ ДВИЖЕНИЕ",
                "В ЭТОТ ХОД построй своё здание на гексе, соседнем с гексом, где "
                + "есть войска противника, — награда 2 монеты и 3 боеприпаса. "
                + "Построй его на гексе, соседнем с гексом, где есть здание "
                + "противника, и условие усилено: 1 карта арсенала. Передовая база строится "
                + "под огнём — потому и платит.");
        }

        /** Было ли в этот ход строительство рядом с чужим жетоном нужного вида. */
        private boolean строилРядом(CardContext ctx, boolean зданиеПротивника) {
            for (String где : ход(ctx).builtOnHexes) {
                for (String рядом : ctx.neighbors(где)) {
                    if (зданиеПротивника) {
                        if (!ctx.enemyBuildingsOn(рядом).isEmpty()) {
                            return true;
                        }
                    } else {
                        for (Token t : ctx.enemyTokensOn(рядом)) {
                            if (t instanceof UnitToken) {
                                return true;
                            }
                        }
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
        public boolean burn(CardContext ctx) {
            return свободноеДвижение(ctx);
        }

        @Override
        protected String действие() {
            return "build";
        }
    }

    /**
     * o12 «Наглая стройка» — стройка прямо на гексе с войском противника.
     *
     * <p>БЫЛО ПОМЕЧЕНО КАК СОСТОЯНИЕ, а текст требовал события («строй»). Стройка
     * — событие, и доказать её задним числом нечем: задание не может проверять то,
     * что случилось не в этот ход.
     */
    public static final class НаглаяСтройка extends ЗаданиеВКоде {
        public НаглаяСтройка() {
            super("o12");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Наглая стройка", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД построй своё здание на гексе, где стоит войско противника",
                "В ЭТОТ ХОД сделай это на гексе, где стоят два войска противника",
                Награда.монеты(4), Награда.картаАрсенала(),
                "СВОБОДНЫЙ МАРКЕТ",
                "В ЭТОТ ХОД построй своё здание на гексе, где стоит войско "
                + "противника, — награда 4 монеты. Сделай это на гексе, где стоят "
                + "два войска противника, и условие усилено: 1 карта арсенала. Стройка под чужим стволом — заявка, а не расчёт.");
        }

        private boolean строилСреди(CardContext ctx, int войск) {
            for (String где : ход(ctx).builtOnHexes) {
                int n = 0;
                for (Token t : ctx.enemyTokensOn(где)) {
                    if (t instanceof UnitToken) {
                        n++;
                    }
                }
                if (n >= войск) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return строилСреди(ctx, 1);
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return строилСреди(ctx, 2);
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // То же, что у «Передовой базы», но гекс нужен НЕ рядом, а тот
            // самый — где чужое войско и стоит.
            return готовность(естьГексПодСтройку(ctx, hid -> {
                for (Token t : ctx.enemyTokensOn(hid)) {
                    if (t instanceof UnitToken) {
                        return true;
                    }
                }
                return false;
            }));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "построить здание на гексе, где стоит войско противника";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободныйМаркет(ctx);
        }

        @Override
        protected String действие() {
            return "build";
        }
    }

    /** o14 «Осадный лагерь» — свои здания вокруг гекса с чужим зданием. */
    public static final class ОсадныйЛагерь extends ЗаданиеВКоде {
        public ОсадныйЛагерь() {
            super("o14");
        }

        @Override
        protected String отсев() {
            return "[4]";
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Осадный лагерь", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей свои здания на двух гексах, соседних с одним и тем же гексом, "
                + "где стоит здание противника",
                "Замкни это кольцо с трёх сторон",
                Награда.боеприпасы(3), Награда.модуль("attack").иОбломки(2),
                "ЩИТ: техника или авиация",
                "Имей свои здания на двух гексах, соседних с одним и тем же гексом, "
                + "где стоит здание противника, — награда 3 боеприпаса. Замкни "
                + "кольцо с трёх сторон, и условие усилено: жетон модуля атаки и 2 "
                + "обломка. Осада стоит дорого, поэтому и платит крупно.");
        }

        /** Наибольшее число своих гексов вокруг одного гекса с чужим зданием. */
        private int охват(CardContext ctx) {
            var свои = ctx.myBuildingHexes();
            int лучший = 0;
            for (String центр : ctx.allHexes()) {
                if (!ctx.passable(центр) || ctx.enemyBuildingsOn(центр).isEmpty()) {
                    continue;
                }
                int n = 0;
                for (String рядом : ctx.neighbors(центр)) {
                    if (свои.contains(рядом)) {
                        n++;
                    }
                }
                лучший = Math.max(лучший, n);
            }
            return лучший;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return охват(ctx) >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return охват(ctx) >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(охват(ctx), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = охват(ctx);
            return есть >= 2 ? "" : "поставить ещё " + (2 - есть)
                + " своё здание вокруг гекса с чужим зданием";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return ctx.shield(List.of("vehicle", "aircraft"));
        }

        @Override
        protected String действие() {
            return "build";
        }
    }

    /**
     * o17 «Штаб на передовой» — ЦУ придвинуто к чужому ЦУ.
     *
     * <p>БЫЛО «перенеси ЦУ на гекс, где ещё никто не строил». Такого состояния в
     * игре нет: снятое здание не оставляет следа, и проверить историю гекса за
     * столом нечем — записи матча у игроков не бывает. Стало измеримое расстояние.
     */
    public static final class ШтабНаПередовой extends ЗаданиеВКоде {
        public ШтабНаПередовой() {
            super("o17");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Штаб на передовой", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД построй или перенеси своё ЦУ так, чтобы от него до "
                + "гекса с ЦУ противника осталось не больше 2 гексов",
                "В ЭТОТ ХОД поставь его так, чтобы оно примыкало к зданию противника",
                Награда.монеты(3), Награда.обломки(2),
                "СНАРЯЖЕНИЕ ОДНИМ ЗДАНИЕМ",
                "В ЭТОТ ХОД построй или перенеси своё ЦУ так, чтобы от него до "
                + "гекса с ЦУ противника осталось не больше 2 гексов, — награда 3 "
                + "монеты. Поставь его так, чтобы оно примыкало к зданию "
                + "противника, и условие усилено: 2 обломка. Штаб, "
                + "придвинутый к чужому, экономит каждое перемещение.");
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

        private boolean поставилБлизко(CardContext ctx, boolean примыкая) {
            var чужие = чужиеЦУ(ctx);
            if (чужие.isEmpty()) {
                return false;
            }
            for (String где : ход(ctx).cuPlacedHexes) {
                Integer d = ctx.distance(где, чужие);
                if (d == null || d > 2) {
                    continue;
                }
                if (!примыкая) {
                    return true;
                }
                for (String рядом : ctx.neighbors(где)) {
                    if (!ctx.enemyBuildingsOn(рядом).isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return поставилБлизко(ctx, false);
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return поставилБлизко(ctx, true);
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // ГРАДИЕНТ ПО ПРОЙДЕННОМУ ПУТИ, а не «да/нет». Требование мерится
            // расстоянием от своего ЦУ до чужого, и это редкий случай, когда
            // близость карты буквально есть близость на поле: чем ЦУ ближе, тем
            // меньше остаётся довезти. Порог требования 2 гекса; отсчёт ведём от
            // шести, дальше этого разница для карты уже неразличима.
            var чужие = чужиеЦУ(ctx);
            if (чужие.isEmpty()) {
                return 0.0;
            }
            Integer лучшее = null;
            for (String своё : ctx.cuHexesOf(ctx.seat())) {
                Integer d = ctx.distance(своё, чужие);
                if (d != null && (лучшее == null || d < лучшее)) {
                    лучшее = d;
                }
            }
            if (лучшее == null) {
                return 0.0;
            }
            // Уже стоит близко, но ЦУ в этот ход не двигали — рывок в одно
            // перемещение, поэтому готовность, а не выполнение.
            if (лучшее <= 2) {
                return готовность(true);
            }
            return готовность(доля(6 - лучшее, 6 - 2));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "перенести своё ЦУ ближе к чужому ЦУ";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободноеСнаряжение(ctx, 1);
        }

        @Override
        protected String действие() {
            return "build";
        }
    }

    /**
     * o21 «Первая кровь» — два уничтоженных жетона противника за ход.
     *
     * <p>БЫЛО один жетон: за ход это делается спокойно, и задание закрывалось
     * одним действием. Усиление платит за толстую цель — у одного из двух была
     * прочность 2 или больше.
     */
    public static final class ПерваяКровь extends ЗаданиеВКоде {
        public ПерваяКровь() {
            super("o21");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Первая кровь", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь два жетона противника",
                "В ЭТОТ ХОД у одного из уничтоженных прочность была 2 или больше",
                Награда.монеты(4), Награда.модуль("attack"),
                "СНАРЯЖЕНИЕ ОДНИМ ЗДАНИЕМ",
                "В ЭТОТ ХОД уничтожь два жетона противника — награда 4 монеты. Если "
                + "у одного из них прочность была 2 или больше, условие усилено: жетон модуля атаки. Первая кровь считается по "
                + "двум, а не по одному.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).enemyTokensDestroyed >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && ход(ctx).maxDestroyedHp >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ход(ctx).enemyTokensDestroyed, 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ход(ctx).enemyTokensDestroyed;
            return есть >= 2 ? "" : "уничтожить ещё " + (2 - есть) + " жетон противника";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободноеСнаряжение(ctx, 1);
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }

    /** o23 «Подранки» — два раненых чужих жетона за ход и ни одного убитого. */
    public static final class Подранки extends ЗаданиеВКоде {
        public Подранки() {
            super("o23");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Подранки", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД нанеси урон двум разным жетонам противника и не "
                + "уничтожь ни одного",
                "В ЭТОТ ХОД рань так три разных жетона противника",
                Награда.боеприпасы(3), Награда.обломки(2).иКартуСВитрины(),
                "+1 К СКОРОСТИ ОДНОГО РОДА ДО КОНЦА ХОДА",
                "В ЭТОТ ХОД нанеси урон двум разным жетонам противника — войскам, "
                + "зданиям, как выйдет — и не уничтожь ни одного. Награда: 3 "
                + "боеприпаса. Рань так три жетона, и условие усилено: 2 обломка и "
                + "карта арсенала на выбор из открытых. Подранков добивают позже и дешевле.");
        }

        private int раненых(CardContext ctx) {
            var ф = ход(ctx);
            return ф.enemyTokensDestroyed == 0 ? ф.enemyTokensDamaged.size() : 0;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return раненых(ctx) >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return раненых(ctx) >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(раненых(ctx), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            if (ход(ctx).enemyTokensDestroyed > 0) {
                return "в этот ход уже уничтожен чужой жетон — карта закрыта до конца хода";
            }
            int есть = раненых(ctx);
            return есть >= 2 ? "" : "ранить ещё " + (2 - есть) + " чужой жетон, не убивая";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return ctx.speedBoost();
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }

    /** o45 «Пристрелка» — урон двум разным ЗДАНИЯМ противника за ход. */
    public static final class Пристрелка extends ЗаданиеВКоде {
        public Пристрелка() {
            super("o45");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Пристрелка", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД нанеси урон двум разным зданиям противника",
                "В ЭТОТ ХОД достань три разных здания противника",
                Награда.монеты(3), Награда.обломки(2).иКартыЗаданий(1),
                "ЩИТ: пехота или авиация",
                "В ЭТОТ ХОД нанеси урон двум разным зданиям противника — награда 3 "
                + "монеты. Достань три разных здания, и условие усилено: 2 обломка и "
                + "карта задания. Пристрелка не сносит стену, но показывает, где она "
                + "тонкая.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).enemyBuildingsDamaged.size() >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).enemyBuildingsDamaged.size() >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ход(ctx).enemyBuildingsDamaged.size(), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ход(ctx).enemyBuildingsDamaged.size();
            return есть >= 2 ? "" : "нанести урон ещё " + (2 - есть) + " зданию противника";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return ctx.shield(List.of("infantry", "aircraft"));
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }

    /**
     * o25 «Осада» — чужое здание в трофеях.
     *
     * <p>БЫЛО «нанеси удар по зданию за один ход»: удара за два хода не бывает,
     * приписка была пустой. Стало проверяемое состояние трофейного места, а
     * усиление платит за чужую экономику — снесённый добытчик дороже казармы.
     */
    public static final class Осада extends ЗаданиеВКоде {
        public Осада() {
            super("o25");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Осада", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей в трофеях здание противника",
                "Среди твоих трофеев есть добытчик противника",
                Награда.монеты(1), Награда.модуль("attack"),
                "СНАРЯЖЕНИЕ ОДНИМ ЗДАНИЕМ",
                "Имей в трофеях здание противника — награда 1 монета. Если "
                + "среди твоих трофеев есть добытчик противника, условие усилено: "
                + "жетон модуля атаки. Снесённая экономика соседа стоит дороже "
                + "снесённой казармы.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            for (Token t : ctx.me().trophySpace) {
                if (t instanceof BuildingToken) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            for (Token t : ctx.me().trophySpace) {
                if (t instanceof BuildingToken b && b.type == BuildingType.MINER) {
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
            return готовность(лучшая);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "снести чужое здание и взять его в трофеи";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободноеСнаряжение(ctx, 1);
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }
}
