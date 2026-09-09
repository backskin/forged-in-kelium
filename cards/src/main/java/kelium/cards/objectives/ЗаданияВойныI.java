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
            return new Лицо("Опорный пункт", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей на поле две свои вышки на разных гексах, и ни одна из них "
                + "не стоит на гексе с твоим ЦУ",
                "Хотя бы одна из этих вышек стоит на гексе, где есть жетоны противника",
                Награда.боеприпасы(3), Награда.трофеи(2),
                Утиль.ОТВЕТНЫЙ_ОГОНЬ,
                "Имей на поле две свои вышки на разных гексах, и ни одна из них не стоит "
                + "на гексе с твоим ЦУ, — награда 3 боеприпаса. Если хотя бы одна из этих "
                + "вышек стоит на гексе, где есть жетоны противника, условие усилено: 2 "
                + "трофея. Вышка не двигается вовсе, поэтому опорный пункт выбирают один "
                + "раз и навсегда.");
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
                Утиль.ЗАГРАДИТЕЛЬНЫЙ_ОГОНЬ,
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
                Награда.монеты(3), Награда.картаАрсенала(),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "В ЭТОТ ХОД построй своё здание на гексе, соседнем с гексом, где есть "
                + "войска противника, — награда 3 монеты. Построй его на гексе, соседнем с "
                + "гексом, где есть здание противника, и условие усилено: 1 карта арсенала. "
                + "Передовая база строится под огнём — потому и платит.");
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
                "В ЭТОТ ХОД на этом гексе два и более жетонов противника",
                Награда.монеты(4), Награда.трофеи(2),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "В ЭТОТ ХОД построй своё здание на гексе, где стоит войско противника, — "
                + "награда 4 монеты. Если на этом гексе два и более жетонов противника, "
                + "условие усилено: 2 трофея. Стройка под чужим стволом — заявка, а не "
                + "расчёт.");
        }

        /**
         * Строил ли в этот ход на гексе, где стоит столько чужого.
         *
         * <p>БАЗОВОЕ ТРЕБОВАНИЕ СЧИТАЕТ ВОЙСКА, УСИЛЕНИЕ — ЛЮБЫЕ ЖЕТОНЫ
         * (таблица дизайнера 04.09.2026). Разница не придирка: войско на гексе
         * означает драку, а чужое здание — что гекс закрыт, и построиться там
         * тяжелее вдвое.
         */
        private boolean строилСреди(CardContext ctx, int сколько, boolean толькоВойска) {
            for (String где : ход(ctx).builtOnHexes) {
                int n = 0;
                for (Token t : ctx.enemyTokensOn(где)) {
                    if (!толькоВойска || t instanceof UnitToken) {
                        n++;
                    }
                }
                if (n >= сколько) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return строилСреди(ctx, 1, true);
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return строилСреди(ctx, 2, false);
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
                Награда.боеприпасы(3), Награда.модуль("attack").иТрофеи(2),
                Утиль.ЩИТ_ТЕХНИКА_АВИАЦИЯ,
                "Имей свои здания на двух гексах, соседних с одним и тем же гексом, "
                + "где стоит здание противника, — награда 3 боеприпаса. Замкни "
                + "кольцо с трёх сторон, и условие усилено: жетон модуля атаки и 2 "
                + "трофея. Осада стоит дорого, поэтому и платит крупно.");
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
            return new Лицо("Штаб на передовой", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей своё здание на гексе, соседнем с гексом, где стоит ЦУ "
                + "противника",
                "Твоих зданий вокруг чужого ЦУ два, и стоят они на разных гексах",
                Награда.монеты(3), Награда.трофеи(2),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "Имей своё здание на гексе, соседнем с гексом, где стоит ЦУ "
                + "противника, — награда 3 монеты. Если таких твоих зданий два и "
                + "стоят они на разных гексах, условие усилено: 2 трофея. "
                + "Подобраться стройкой к чужому штабу дороже, чем кажется: туда "
                + "ещё надо дойти, и там по тебе будут бить.");
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

        /** Наименьшее расстояние от своего здания до чужого ЦУ — для близости. */
        private Integer ближайшее(CardContext ctx) {
            var чужие = чужиеЦУ(ctx);
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
            return !уЧужогоЦУ(ctx).isEmpty();
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return уЧужогоЦУ(ctx).size() >= 2;
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
                : "построить здание на " + Math.max(1, d - 1) + " гекс(а) ближе к чужому ЦУ";
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
                Утиль.ОБМЕН_НАУКА_ИЛИ_РЫНОК,
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
            int убито = ход(ctx).enemyTokensDestroyed;
            int цели = целейВДосягаемости(ctx);
            boolean хватаетБоеприпасов = ctx.have(kelium.core.Resource.AMMO) >= 2;
            return ступени(
                доля(убито, 2),
                готовность(цели >= 2 && хватаетБоеприпасов),
                готовность(0.5 * Math.min(1.0, цели)),
                подготовка(чужойВПределахДороги(ctx, 2, t -> true)));
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ход(ctx).enemyTokensDestroyed;
            if (есть >= 2) {
                return "";
            }
            if (целейВДосягаемости(ctx) < 2) {
                return "подвести войска к двум чужим жетонам";
            }
            return "уничтожить ещё " + (2 - есть) + " жетон противника";
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
                Награда.боеприпасы(3), Награда.трофеи(2).иКартуСВитрины(),
                Утиль.СКОРОСТЬ,
                "В ЭТОТ ХОД нанеси урон двум разным жетонам противника — войскам, "
                + "зданиям, как выйдет — и не уничтожь ни одного. Награда: 3 "
                + "боеприпаса. Рань так три жетона, и условие усилено: 2 трофея и "
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
                Награда.монеты(3), Награда.трофеи(2).иКартыЗаданий(1),
                Утиль.ЩИТ_ПЕХОТА_АВИАЦИЯ,
                "В ЭТОТ ХОД нанеси урон двум разным зданиям противника — награда 3 "
                + "монеты. Достань три разных здания, и условие усилено: 2 трофея и "
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
            return new Лицо("Осада", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей среди уничтоженных жетонов жетон противника",
                "Их два, и один из них — добытчик",
                Награда.монеты(4), Награда.модуль("attack"),
                Утиль.ЭВАКУАЦИЯ_ТРОФЕЕВ,
                "Имей среди уничтоженных жетонов жетон противника — награда 4 "
                + "монеты. Если их два и один из них добытчик, условие усилено: "
                + "жетон модуля атаки. Снесённая экономика соседа стоит дороже "
                + "снесённой казармы.");
        }

        /** Чужие жетоны на моём месте уничтоженных. */
        private java.util.List<Token> чужие(CardContext ctx) {
            java.util.List<Token> out = new java.util.ArrayList<>();
            for (Token t : ctx.me().destroyedTokens) {
                if (t.owner() != ctx.seat()) {
                    out.add(t);
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
            for (Token t : чуж) {
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
