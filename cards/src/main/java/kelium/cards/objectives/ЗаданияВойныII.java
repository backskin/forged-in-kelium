package kelium.cards.objectives;

import kelium.cards.Награда;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.cards.CardContext;

import static kelium.cards.objectives.Лицо.Вид.ОБЫЧНАЯ;
import static kelium.cards.objectives.Лицо.Природа.ПРОИСШЕСТВИЕ;
import static kelium.cards.objectives.Лицо.Природа.СОСТОЯНИЕ;

/**
 * ВОЙНА И ДАВЛЕНИЕ, вторая половина: маневр, охват, отдача и трофеи.
 *
 * <p>Каждая карта — свой класс со своим кодом. Три карты этой группы (o41, o42,
 * o43) и раньше жили в коде, но по-своему: они читали пороги из YAML и опирались
 * на предикаты. Здесь они приведены к общему устройству — со своим лицом и без
 * единого чтения данных.
 */
public final class ЗаданияВойныII {

    private ЗаданияВойныII() {
    }

    /** o26 «Блицкриг» — два уничтожения ОДНИМ своим жетоном войска за ход. */
    public static final class Блицкриг extends ЗаданиеВКоде {
        public Блицкриг() {
            super("o26");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Блицкриг", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь два жетона противника одним своим жетоном войска",
                "В ЭТОТ ХОД это войско — пехота",
                Награда.монеты(5), Награда.модуль("attack").иКартыЗаданий(1),
                Утиль.БОЙ,
                "В ЭТОТ ХОД уничтожь два жетона противника одним своим жетоном "
                + "войска — награда 5 монет. Если это войско — пехота, условие "
                + "усилено: жетон модуля атаки и карта задания. Пехота, снявшая двоих "
                + "за ход, стоит целого завода.");
        }

        /** Есть ли жетон, набравший два уничтожения; при {@code род} — и он этого рода. */
        private boolean двоеОдним(CardContext ctx, String род) {
            var ф = ход(ctx);
            for (var e : ф.killsByUnit.entrySet()) {
                if (e.getValue() >= 2
                        && (род == null || род.equals(ф.killerUnitTypes.get(e.getKey())))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return двоеОдним(ctx, null);
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return двоеОдним(ctx, "infantry");
        }

        @Override
        public double progress(CardContext ctx) {
            int лучший = 0;
            for (int n : ход(ctx).killsByUnit.values()) {
                лучший = Math.max(лучший, n);
            }
            return доля(лучший, 2);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "добить второй жетон тем же войском, что уже стреляло";
        }


        @Override
        protected String действие() {
            return "combat";
        }
    }

    /**
     * o27 «На чужом дворе» — своё войско на гексе с войсками противника.
     *
     * <p>«Держите там» — так о гексе не говорят: гекс никому не принадлежит.
     * Правильно «имей на одном гексе».
     */
    public static final class НаЧужомДворе extends ЗаданиеВКоде {
        public НаЧужомДворе() {
            super("o27");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("На чужом дворе", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей своё войско на гексе, где есть войска противника",
                "Имей на одном таком гексе два своих войска",
                Награда.боеприпасы(3), Награда.картаАрсенала(),
                Утиль.СНАРЯЖЕНИЕ,
                "Имей своё войско на гексе, где есть войска противника, — награда "
                + "3 боеприпаса. Имей на одном таком гексе два своих войска, и "
                + "условие усилено: 1 карта арсенала. Гекс никому не "
                + "принадлежит — но стоять на нём вдвоём неудобно обоим.");
        }

        /** Наибольшее число своих войск на одном гексе, где есть чужие войска. */
        private int вместе(CardContext ctx) {
            java.util.Map<String, Integer> мои = new java.util.HashMap<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                мои.merge(u.hexId, 1, Integer::sum);
            }
            int лучший = 0;
            for (var e : мои.entrySet()) {
                for (Token t : ctx.enemyTokensOn(e.getKey())) {
                    if (t instanceof UnitToken) {
                        лучший = Math.max(лучший, e.getValue());
                        break;
                    }
                }
            }
            return лучший;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return вместе(ctx) >= 1;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return вместе(ctx) >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(вместе(ctx), 1);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "завести войско на гекс с войсками противника";
        }


        @Override
        protected String действие() {
            return "movement";
        }
    }

    /** o28 «Клещи» — свои войска на двух гексах вокруг одного чужого. */
    public static final class Клещи extends ЗаданиеВКоде {
        public Клещи() {
            super("o28");
        }

        @Override
        protected String отсев() {
            return "[4]";
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Клещи", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей свои войска на двух гексах, соседних с одним и тем же гексом, "
                + "где есть войска противника",
                "Замкни эти клещи с трёх сторон",
                Награда.боеприпасы(3), Награда.обломки(2),
                Утиль.ДВИЖЕНИЕ,
                "Имей свои войска на двух гексах, соседних с одним и тем же гексом, "
                + "где есть войска противника, — награда 3 боеприпаса. Замкни клещи "
                + "с трёх сторон, и условие усилено: 2 обломка. Клещи не бьют — они отнимают выход.");
        }

        /** Наибольшее число моих гексов вокруг одного гекса с чужими войсками. */
        private int охват(CardContext ctx) {
            java.util.Set<String> мои = new java.util.HashSet<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                мои.add(u.hexId);
            }
            int лучший = 0;
            for (String центр : ctx.allHexes()) {
                boolean чужиеВойска = false;
                for (Token t : ctx.enemyTokensOn(центр)) {
                    if (t instanceof UnitToken) {
                        чужиеВойска = true;
                        break;
                    }
                }
                if (!чужиеВойска) {
                    continue;
                }
                int n = 0;
                for (String рядом : ctx.neighbors(центр)) {
                    if (мои.contains(рядом)) {
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
            return есть >= 2 ? "" : "подвести войско ещё на " + (2 - есть)
                + " гекс вокруг чужого";
        }


        @Override
        protected String действие() {
            return "movement";
        }
    }

    /**
     * o29 «Пустой двор» — три своих войска вне гексов со своими зданиями.
     *
     * <p>ВНИМАНИЕ НА СЛОВО «ВНЕ»: ни одного своего войска НА своих гексах остаться
     * не должно — двор пустой. Это и делает карту неудобной: база остаётся без
     * прикрытия.
     */
    public static final class ПустойДвор extends ЗаданиеВКоде {
        public ПустойДвор() {
            super("o29");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Пустой двор", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей три своих войска вне гексов, где стоят твои здания, и ни "
                + "одного своего войска на этих гексах",
                "Все три стоят на разных гексах",
                Награда.монеты(4), Награда.модуль("assembly"),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "Имей три своих войска вне гексов, где стоят твои здания, и ни "
                + "одного своего войска на этих гексах, — награда 4 монеты. Если все "
                + "три стоят на разных гексах, условие усилено: жетон модуля сборки. Двор пустеет ровно тогда, когда "
                + "фронт наконец поехал.");
        }

        /** Гексы вне своей базы, где стоят мои войска; пусто, если двор не пуст. */
        private java.util.Set<String> снаружи(CardContext ctx) {
            var свои = ctx.myBuildingHexes();
            java.util.Set<String> вне = new java.util.LinkedHashSet<>();
            int внутри = 0;
            int всего = 0;
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (свои.contains(u.hexId)) {
                    внутри++;
                } else {
                    всего++;
                    вне.add(u.hexId);
                }
            }
            if (внутри != 0 || всего < 3) {
                return java.util.Set.of();
            }
            return вне;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return !снаружи(ctx).isEmpty();
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return снаружи(ctx).size() >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            // ДВЕ ЧАСТИ: снаружи не меньше трёх И внутри ни одного. Берём худшую
            // из двух долей — иначе пять войск снаружи при одном забытом во
            // дворе выглядели бы как «готово», хотя карта не выполнена.
            var свои = ctx.myBuildingHexes();
            int внутри = 0;
            int снаружи = 0;
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (свои.contains(u.hexId)) {
                    внутри++;
                } else {
                    снаружи++;
                }
            }
            double поВыходу = доля(снаружи, 3);
            // Каждое войско, оставшееся во дворе, — отдельная недоделка; при
            // одном лишнем близость половинится, при трёх падает почти в ноль.
            double поДвору = внутри == 0 ? 1.0 : доля(1, 1 + внутри);
            return Math.min(поВыходу, поДвору);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "вывести три войска с гексов своих зданий, не оставив там ни одного";
        }


        @Override
        protected String действие() {
            return "movement";
        }
    }

    /** o31 «Господство в небе» — своя авиация на гексе с жетонами противника. */
    public static final class ГосподствоВНебе extends ЗаданиеВКоде {
        public ГосподствоВНебе() {
            super("o31");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Господство в небе", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей свою авиацию на гексе, где есть жетоны противника",
                "На этом гексе стоит ЦУ противника",
                Награда.монеты(4), Награда.модуль("attack").иКартыЗаданий(1),
                Утиль.СКОРОСТЬ,
                "Имей свою авиацию на гексе, где есть жетоны противника, — награда "
                + "4 монеты. Если на этом гексе стоит ЦУ противника, условие усилено: "
                + "жетон модуля атаки и карта задания. В воздушную ячейку чужого "
                + "гекса влезает ровно одна авиация — чья первая, того и небо.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (u.type == UnitType.AIRCRAFT && !ctx.enemyTokensOn(u.hexId).isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (u.type != UnitType.AIRCRAFT) {
                    continue;
                }
                for (Token t : ctx.enemyTokensOn(u.hexId)) {
                    if (t instanceof BuildingToken b
                            && b.type == BuildingType.COMMAND_CENTER) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // ТРЕБОВАНИЕ РАСПАДАЕТСЯ НА ДВА: сперва иметь авиацию вообще, потом
            // довести её до чужого гекса. Без авиации близость честный ноль —
            // тут нечего доводить, нужен найм. С авиацией мерим путь до
            // ближайшего чужого жетона: это ровно то, что осталось пролететь.
            java.util.Set<String> чужие = new java.util.LinkedHashSet<>();
            for (Token t : ctx.enemyTokensOnField()) {
                чужие.add(t.hexId());
            }
            if (чужие.isEmpty()) {
                return 0.0;
            }
            Integer лучшее = null;
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (u.type != UnitType.AIRCRAFT) {
                    continue;
                }
                Integer d = ctx.distance(u.hexId, чужие);
                if (d != null && (лучшее == null || d < лучшее)) {
                    лучшее = d;
                }
            }
            if (лучшее == null) {
                return 0.0;   // авиации нет — карта ждёт Сборки, а не Движения
            }
            return готовность(доля(5 - Math.min(5, лучшее), 5));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "завести авиацию на гекс с жетонами противника";
        }


        @Override
        protected String действие() {
            return "movement";
        }
    }

    /**
     * o41 «Ответный удар» — сбить нападавшего в ответном бою.
     *
     * <p>ЭТА КАРТА БЫЛА НЕВЫПОЛНИМА, И ВОТ ПОЧЕМУ. Ответный бой по устройству
     * случается НЕ В ТВОЙ ХОД: по тебе бьют, ты отбиваешься, и запись ложится в
     * твой журнал посреди чужого хода. К началу твоего собственного хода журнал
     * обнулялся, а в свой ход тебя не атакуют — значит условие «в этот ход
     * уничтожь жетон в ответном бою» не могло стать истинным никогда. Замер это
     * подтверждал: ноль выполнений за сто пятьдесят партий.
     *
     * <p>РЕШЕНО ТАК: годится ответный бой, случившийся ПОСЛЕ НАЧАЛА ТВОЕГО
     * ПРОШЛОГО ХОДА. За столом это проверяемо без записи матча — удар только что
     * был, его видели все, а сбитый жетон лежит в твоём трофейном месте.
     */
    public static final class ОтветныйУдар extends ЗаданиеВКоде {
        public ОтветныйУдар() {
            super("o41");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Ответный удар", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД разыграй карту, если после начала твоего прошлого хода "
                + "ты уничтожил жетон противника в ответном бою",
                "В этих ответных боях ты не потерял ни одного своего жетона",
                Награда.боеприпасы(3), Награда.обломки(3),
                Утиль.БОЙ,
                "В ЭТОТ ХОД разыграй карту, если после начала твоего прошлого хода "
                + "ты уничтожил жетон противника в ответном бою — когда бьют тебя, "
                + "а падает нападавший. Награда: 3 боеприпаса. Если в этих ответных "
                + "боях ты не потерял ни одного своего жетона, условие усилено: 3 обломка. Ответный бой случается не в "
                + "твой ход, поэтому карта и смотрит на то, что было после начала "
                + "твоего прошлого хода: сбитый жетон лежит в твоих трофеях, и "
                + "видели это все.");
        }

        /** Ответные победы, случившиеся пока ходили другие, плюс этот ход. */
        private int ответные(CardContext ctx) {
            var ф = ход(ctx);
            return ф.retaliationSincePrevTurn + ф.destroyedInRetaliation;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ответные(ctx) >= 1;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            var ф = ход(ctx);
            return satisfied(ctx) && ф.lostSincePrevTurn == 0 && ф.lostOwnThisTurn == 0;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // На меня напали — половина дела: случай представился, осталось попасть.
            var ф = ход(ctx);
            return ф.lostSincePrevTurn > 0 || ф.lostOwnThisTurn > 0 ? 0.5 : 0.0;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "сбить нападавшего в ответном бою";
        }


        @Override
        protected String действие() {
            return "combat";
        }
    }

    /**
     * o42 «Разорение» — уничтожить чужой добытчик.
     *
     * <p>ЭНЕРГОСТАНЦИЯ ИЗ УСЛОВИЯ УБРАНА: «запитанная энергостанция» — не термин,
     * станция энергию производит, а не потребляет. Осталась одна цель, добытчик, и
     * усиление платит за то, что он был запитан, то есть работал.
     */
    public static final class Разорение extends ЗаданиеВКоде {
        public Разорение() {
            super("o42");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Разорение", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь добытчик противника",
                "В ЭТОТ ХОД этот добытчик был запитан",
                Награда.монеты(3), Награда.модуль("attack"),
                Утиль.НАУКА,
                "В ЭТОТ ХОД уничтожь добытчик противника — награда 3 монеты. Если "
                + "этот добытчик был запитан, условие усилено: жетон модуля атаки. Отнятый добытчик стоит сопернику всех оставшихся раундов "
                + "добычи.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).destroyedTypes.contains("miner");
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).destroyedPoweredEconomy;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Есть ли вообще до кого дотянуться: чужой добытчик рядом с моими
            // войсками. Без этого карта невыполнима, и бот должен это знать.
            return добытчикВДосягаемости(ctx) ? 0.4 : 0.0;
        }

        private boolean добытчикВДосягаемости(CardContext ctx) {
            java.util.Set<String> достану = new java.util.HashSet<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (u.hexId != null) {
                    достану.add(u.hexId);
                    достану.addAll(ctx.neighbors(u.hexId));
                }
            }
            for (Token t : ctx.enemyTokensOnField()) {
                if (t instanceof BuildingToken b && b.type == BuildingType.MINER
                        && достану.contains(b.hexId)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "уничтожить чужой добытчик";
        }


        @Override
        protected String действие() {
            return "combat";
        }
    }

    /**
     * o43 «Охота на сильного» — сбить жетон того, у кого зданий больше.
     *
     * <p>ПЕРЕПИСАНА ЦЕЛИКОМ. Прежняя редакция требовала бить того, кто ведёт по
     * победным очкам: за столом это не считается, очки в середине партии никто не
     * знает, и правило годилось только для симуляции. Новое условие видно глазами
     * — здания на поле пересчитываются.
     */
    public static final class ОхотаНаСильного extends ЗаданиеВКоде {
        public ОхотаНаСильного() {
            super("o43");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Охота на сильного", ОБЫЧНАЯ, ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь жетон игрока, у которого на поле больше "
                + "зданий, чем у тебя",
                "В ЭТОТ ХОД уничтоженный жетон был зданием",
                Награда.боеприпасы(3), Награда.обломки(2),
                Утиль.БОЙ,
                "В ЭТОТ ХОД уничтожь жетон игрока, у которого на поле больше "
                + "зданий, чем у тебя, — награда 3 боеприпаса. Если уничтоженный "
                + "жетон был зданием, условие усилено: 2 обломка. Догоняющему выгодно бить того, кто оторвался, — и это "
                + "видно по столу без подсчёта очков.");
        }

        private int зданий(CardContext ctx, int seat) {
            return ctx.state().player(seat).buildingsOnField().size();
        }

        /** Сбил ли я в этот ход жетон того, у кого зданий больше моего. */
        private boolean сбилСильного(CardContext ctx) {
            int моих = зданий(ctx, ctx.seat());
            for (int чей : ход(ctx).destroyedOwners) {
                if (чей != ctx.seat() && чей >= 0 && зданий(ctx, чей) > моих) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return сбилСильного(ctx);
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return сбилСильного(ctx) && ход(ctx).destroyedLeaderBuilding;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // ГОДНАЯ ЦЕЛЬ ЕСТЬ ИЛИ ЕЁ НЕТ: жетон именно того игрока, у кого
            // зданий больше моего, и именно в пределах выстрела. Без такой цели
            // близость честный ноль — карта ждёт не Боя, а роста соперника или
            // подхода войск. Проверка повторяет условие карты, но глядя вперёд.
            if (ctx.have(Resource.AMMO) < 1) {
                return 0.0;
            }
            int моих = зданий(ctx, ctx.seat());
            for (UnitToken u : ctx.me().unitsOnField()) {
                for (String гекс : ctx.attackReach(u)) {
                    for (Token t : ctx.enemyTokensOn(гекс)) {
                        if (t.owner() != ctx.seat() && t.owner() >= 0
                                && зданий(ctx, t.owner()) > моих) {
                            return готовность(true);
                        }
                    }
                }
            }
            return 0.0;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "сбить жетон игрока, у которого зданий на поле больше твоего";
        }


        @Override
        protected String действие() {
            return "combat";
        }
    }

    /** o46 «Трофейный обоз» — жетоны трёх разных видов в трофеях. */
    public static final class ТрофейныйОбоз extends ЗаданиеВКоде {
        public ТрофейныйОбоз() {
            super("o46");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Трофейный обоз", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей в трофеях жетоны трёх разных видов",
                "Собери четыре разных вида",
                Награда.монеты(3), Награда.модуль("assembly"),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "Имей в трофеях жетоны трёх разных видов — пехота, техника, "
                + "авиация, вышка и каждый тип здания считаются отдельно. Награда: "
                + "3 монеты. Собери четыре разных вида, и условие усилено: жетон модуля сборки. Обоз показывает не силу удара, а широту "
                + "фронта.");
        }

        private int видов(CardContext ctx) {
            java.util.Set<String> виды = new java.util.HashSet<>();
            for (Token t : ctx.me().trophySpace) {
                if (t instanceof UnitToken u) {
                    виды.add("unit:" + u.type.code);
                } else if (t instanceof BuildingToken b) {
                    виды.add("building:" + b.type.code);
                }
            }
            return виды.size();
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return видов(ctx) >= 3;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return видов(ctx) >= 4;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(видов(ctx), 3);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = видов(ctx);
            return есть >= 3 ? "" : "добавить в трофеи ещё " + (3 - есть)
                + " жетон другого вида";
        }


        @Override
        protected String действие() {
            return "combat";
        }
    }
}
