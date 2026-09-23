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
                "Имей на поле 2 свои вышки на разных гексах (обе не на гексе с ЦУ)",
                "Одна из вышек стоит на гексе с жетонами врага",
                Награда.выбор("combat", "build"),
                Награда.трофеи(1).иСпец(1),
                Утиль.БОЙ,
                "Имей на поле 2 свои вышки на разных гексах (обе не на гексе с ЦУ) — "
                + "награда на выбор: Бой или Стройка. Усиление — Одна из вышек стоит на "
                + "гексе с жетонами врага: 1 трофей и спец-действие.");
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
            return new Лицо("Засада", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей военное здание с войском внутри, по соседству с гексом, где "
                + "есть жетоны врага",
                "Держи так сразу два своих войска",
                Награда.выбор("combat", "assembly"),
                Награда.картаАрсенала().иСпец(1),
                Утиль.РИКОШЕТ,
                "Имей военное здание с войском внутри, по соседству с гексом, где есть "
                + "жетоны врага — награда на выбор: Бой или Снаряжение. Усиление — Держи "
                + "так сразу два своих войска: карта арсенала и спец-действие.");
        }

        /**
         * СКОЛЬКО ВОЙСК СИДИТ В ВОЕННЫХ ЗДАНИЯХ у чужого порога.
         *
         * <p>Рода войска и рода здания карта не сличает: в таблице дизайнера
         * стоит «военное здание с войском внутри», без «того же рода». По
         * правилу 09.09.2026 здание и производит жетон прямо на себя, и внутри
         * может сидеть любое их число — привязка к роду была моей выдумкой.
         */
        private int укрытых(CardContext ctx) {
            int n = 0;
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (!ctx.adjacentToEnemy(u.hexId)) {
                    continue;
                }
                for (BuildingToken b : ctx.me().buildingsOnField()) {
                    if (ВОЕННЫЕ.contains(b.type) && b.hexId.equals(u.hexId)) {
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
                "В ЭТОТ ХОД построй свое здание на гексе, соседнем с гексом, где "
                + "есть войска врага",
                "На том же соседнем гексе есть здание врага",
                Награда.выбор("energy_swap", "science"),
                Награда.картаАрсенала().иСпец(1),
                Утиль.РАЗВЁРТЫВАНИЕ,
                "В ЭТОТ ХОД построй свое здание на гексе, соседнем с гексом, где есть "
                + "войска врага — награда на выбор: Смена энергии или Наука. Усиление — "
                + "На том же соседнем гексе есть здание врага: карта арсенала и "
                + "спец-действие.");
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
                "В ЭТОТ ХОД построй своё здание на гексе, где есть жетоны врага",
                "На этом гексе стоит войско врага",
                Награда.выбор("energy_swap", "assembly"),
                Награда.модуль("attack").иСпец(1),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "В ЭТОТ ХОД построй своё здание на гексе, где есть жетоны врага — "
                + "награда на выбор: Смена энергии или Снаряжение. Усиление — На этом "
                + "гексе стоит войско врага: жетон модуля атаки и спец-действие.");
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

        // БАЗА И УСИЛЕНИЕ ПОМЕНЯЛИСЬ МЕСТАМИ (19.09.2026). Прежде базовое
        // требование просило построиться на гексе с чужим ВОЙСКОМ, а усиление —
        // на гексе с двумя любыми жетонами. Замер 150 партий: карта не
        // выполнялась НИ РАЗУ. Причина понятна из правил: на гексе с чужим
        // войском нужен ещё и свободный сектор под след здания, а войско как раз
        // занимает место. Чужое ЗДАНИЕ стоит на своих секторах и оставляет
        // соседние — построиться рядом с ним трудно, но возможно.
        @Override
        public boolean satisfied(CardContext ctx) {
            return строилСреди(ctx, 1, false);
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return строилСреди(ctx, 1, true);
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
                Награда.выбор("combat", "market"),
                Награда.трофеи(1).иСпец(1),
                Утиль.РАЗВЁРТЫВАНИЕ,
                "Имей своё здание на гексе, соседнем с гексом, где стоит ЦУ противника "
                + "— награда на выбор: Бой или Рынок. Усиление — Твоих зданий вокруг "
                + "чужого ЦУ два, и стоят они на разных гексах: 1 трофей и спец-действие.");
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
                Награда.выбор("movement", "build"),
                Награда.картаАрсенала().иСпец(1),
                Утиль.РАЗВЁРТЫВАНИЕ,
                "В ЭТОТ ХОД уничтожь два жетона противника — награда на выбор: Манёвр "
                + "или Стройка. Усиление — В ЭТОТ ХОД у одного из уничтоженных прочность "
                + "была 2 или больше: карта арсенала и спец-действие.");
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
                "Имей среди уничтоженных жетонов жетон врага",
                "два жетона, один из них - добытчик",
                Награда.выбор("science", "movement"),
                Награда.модуль("attack").иСпец(1),
                Утиль.БОЙ,
                "Имей среди уничтоженных жетонов жетон врага — награда на выбор: Наука "
                + "или Манёвр. Усиление — два жетона, один из них - добытчик: жетон "
                + "модуля атаки и спец-действие.");
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
