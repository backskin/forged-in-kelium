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
                "Имей своё войско на гексе, где есть здания врага",
                "Имей на одном таком гексе два своих войска",
                Награда.выбор("combat", "mining"),
                Награда.картаАрсенала().иСпец(1),
                Утиль.ОТВЕТНЫЙ_ОГОНЬ,
                "Имей своё войско на гексе, где есть здания врага, — награда "
                + "бесплатный Манёвр. Имей на одном таком гексе два своих войска, и "
                + "условие усилено: 1 боеприпас. Двор чужой, а гекс общий: здание "
                + "закрывает только свои стенки, и войти во двор можно, пока в нём "
                + "есть свободный сектор.");
        }

        /**
         * Наибольшее число своих войск на одном гексе, где стоит чужое здание.
         *
         * <p>БЫЛО «где есть войска противника», И ЭТО СТАЛО НЕВЫПОЛНИМО. Правило
         * 04.09.2026 запретило наземным войскам и входить, и проходить через гекс
         * с чужими войсками — значит встать рядом с ними могла только авиация, а
         * она в небе и на поле её почти нет. Замер 08.09.2026: 95% попаданий
         * карты в руку — близость ровно ноль, одно выполнение на двадцать два.
         *
         * <p>Чужое ЗДАНИЕ гекс не запирает: оно закрывает свои стенки, а сам гекс
         * проходим, пока в нём есть свободный наземный сектор. «Чужой двор» —
         * это ровно двор со зданием, и в него войти можно.
         */
        private int вместе(CardContext ctx) {
            java.util.Map<String, Integer> мои = new java.util.HashMap<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                мои.merge(u.hexId, 1, Integer::sum);
            }
            int лучший = 0;
            for (var e : мои.entrySet()) {
                if (!ctx.enemyBuildingsOn(e.getKey()).isEmpty()) {
                    лучший = Math.max(лучший, e.getValue());
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
            if (satisfied(ctx)) {
                return вместе(ctx) >= 2 ? 1.0 : 0.75;
            }
            // Половина дела — стоять рядом с чужим зданием: шаг, и ты во дворе.
            for (UnitToken u : ctx.me().unitsOnField()) {
                for (String рядом : ctx.neighbors(u.hexId)) {
                    if (!ctx.enemyBuildingsOn(рядом).isEmpty()) {
                        return 0.5;
                    }
                }
            }
            return 0.0;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "завести войско на гекс с чужим зданием";
        }


        @Override
        protected String действие() {
            return "movement";
        }
    }


    /**
     * o29 «Пустой двор» — свои войска стоят там, где нет своих зданий.
     *
     * <p>ТРЕБОВАНИЕ СМЯГЧЕНО ДО ДВУХ ВОЙСК (таблица дизайнера 04.09.2026), и
     * оговорка «ни одного своего войска на своих гексах» снята. Прежняя
     * редакция запрещала держать во дворе хоть кого-то, то есть требовала
     * оставить базу совсем без прикрытия — а карта про другое: про войска,
     * которые стоят в чистом поле и потому умрут первыми. Усиление платит за
     * четверых, разведённых по разным гексам.
     */
    public static final class ПустойДвор extends ЗаданиеВКоде {
        public ПустойДвор() {
            super("o29");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Пустой двор", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Имей два своих войска на гексах, где нет твоих зданий",
                "Четыре - и все стоят на разных гексах",
                Награда.выбор("build", "market"),
                Награда.модуль("assembly").иСпец(1),
                Утиль.БОЙ,
                "Имей два своих войска на гексах, где нет твоих зданий, — награда "
                + "бесплатный Бой. Если таких войск четыре и все стоят на разных "
                + "гексах, условие усилено: 2 боеприпаса. Войско без своего здания "
                + "рядом умрёт первым — зато не даром.");
        }

        /** Свои войска на гексах, где нет ни одного своего здания. */
        private java.util.List<UnitToken> снаружи(CardContext ctx) {
            var свои = ctx.myBuildingHexes();
            java.util.List<UnitToken> вне = new java.util.ArrayList<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (!свои.contains(u.hexId)) {
                    вне.add(u);
                }
            }
            return вне;
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return снаружи(ctx).size() >= 2;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            var вне = снаружи(ctx);
            java.util.Set<String> гексы = new java.util.LinkedHashSet<>();
            for (UnitToken u : вне) {
                гексы.add(u.hexId);
            }
            return вне.size() >= 4 && гексы.size() == вне.size();
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(снаружи(ctx).size(), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = снаружи(ctx).size();
            return есть >= 2 ? "" : "вывести ещё " + (2 - есть)
                + " войско на гекс, где нет твоих зданий";
        }


        @Override
        protected String действие() {
            return "movement";
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
                "Этот добытчик был полностью запитан и примыкал к келемию",
                Награда.выбор("mining", "movement"),
                Награда.модуль("attack").иСпец(1),
                Утиль.БОЙ,
                "В ЭТОТ ХОД уничтожь добытчик противника — награда бесплатный Бой. "
                + "Если этот добытчик был полностью запитан и примыкал к келемию, "
                + "условие усилено: 2 боеприпаса. Отнятый добытчик стоит сопернику "
                + "всех оставшихся раундов добычи — а работающий на полную вдвое "
                + "дороже.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).destroyedTypes.contains("miner");
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).destroyedFullMinerAtKelium;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Есть ли вообще до кого дотянуться: чужой добытчик рядом с моими
            // войсками. Без этого карта невыполнима, и бот должен это знать.
            //
            // ПЛЮС СТУПЕНЬ ПОДГОТОВКИ: чужой добытчик в двух гексах хода. Прежде
            // здесь стоял ноль, и карта не отличалась от невыполнимой — бот не
            // вёл войска к чужой экономике, потому что зацепиться было не за что.
            return ступени(добытчикВДосягаемости(ctx) ? 0.4 : 0.0,
                подготовка(чужойВПределахДороги(ctx, 2,
                    t -> t instanceof BuildingToken b && b.type == BuildingType.MINER)));
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
                Награда.выбор("assembly", "science"),
                Награда.трофеи(1).иСпец(1),
                Утиль.РЕМОНТ_ГЕКСА,
                "В ЭТОТ ХОД уничтожь жетон игрока, у которого на поле больше "
                + "зданий, чем у тебя, — награда два спец-действия в этот ход. Если "
                + "уничтоженный жетон был зданием, условие усилено: 1 трофей и спец-действие. "
                + "Догоняющему выгодно бить того, кто оторвался, — и это видно по "
                + "столу без подсчёта очков.");
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
            // ДОРОГА ЕСТЬ: жетон сильного соседа в двух гексах хода. Прежде
            // близость обрывалась здесь нулём, и карта ничем не отличалась от
            // невыполнимой — бот не вёл войска к тому, кто вырос. Замер
            // 08.09.2026: ноль выполнений на двадцать шесть попаданий в руку.
            return подготовка(чужойВПределахДороги(ctx, 2,
                t -> t.owner() != ctx.seat() && t.owner() >= 0
                    && зданий(ctx, t.owner()) > моих));
        }

        @Override
        public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "";
            }
            return progress(ctx) >= 0.5
                ? "сбить жетон игрока, у которого зданий на поле больше твоего"
                : "подвести войска к игроку, у которого зданий больше твоего";
        }


        @Override
        protected String действие() {
            return "combat";
        }
    }

}
