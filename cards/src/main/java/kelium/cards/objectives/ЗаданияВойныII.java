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
            // ПЕЧАТНАЯ КАРТА «Засада» № 09 (25.09.2026): усиление — два таких
            // гекса со зданиями ОДНОГО врага.
            return печатное("На чужом дворе", СОСТОЯНИЕ,
                "Имей своё войско на гексе, где есть здание врага",
                "на двух гексах, где есть здания одного врага",
                Награда.выбор("market", "combat"),
                Награда.нет().иКартыАрсенала(2),
                Утиль.КОНТРАТАКА);
        }

        /**
         * СКОЛЬКО РАЗНЫХ ГЕКСОВ СО СВОИМ ВОЙСКОМ И ЗДАНИЕМ ОДНОГО И ТОГО ЖЕ
         * ВРАГА — наибольшее по врагам.
         */
        private int гексовУОдного(CardContext ctx) {
            java.util.Map<Integer, java.util.Set<String>> поВрагам = new java.util.HashMap<>();
            for (UnitToken u : ctx.me().unitsOnField()) {
                for (Token t : ctx.enemyBuildingsOn(u.hexId)) {
                    поВрагам.computeIfAbsent(t.owner(), k -> new java.util.HashSet<>())
                        .add(u.hexId);
                }
            }
            int лучший = 0;
            for (var гексы : поВрагам.values()) {
                лучший = Math.max(лучший, гексы.size());
            }
            return лучший;
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
            return гексовУОдного(ctx) >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
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
            // ПЕЧАТНАЯ КАРТА «Засада» № 06 (25.09.2026): счёт по ГЕКСАМ — три,
            // в усилении четыре.
            return печатное("Пустой двор", СОСТОЯНИЕ,
                "Имей свои войска на трех гексах, где нет твоих зданий",
                "на четырех гексах.",
                Награда.выбор("energy_swap", "build"),
                Награда.монеты(4),
                Утиль.АТАКА_ДВУМЯ);
        }

        /** Разные гексы, где стоят мои войска и нет ни одного моего здания. */
        private java.util.Set<String> гексыВне(CardContext ctx) {
            java.util.Set<String> гексы = new java.util.LinkedHashSet<>();
            for (UnitToken u : снаружи(ctx)) {
                гексы.add(u.hexId);
            }
            return гексы;
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
            return гексыВне(ctx).size() >= 3;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return гексыВне(ctx).size() >= 4;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(гексыВне(ctx).size(), 3);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = гексыВне(ctx).size();
            return есть >= 3 ? "" : "вывести войска ещё на " + (3 - есть)
                + " гекс, где нет твоих зданий";
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
            // ПЕЧАТНАЯ КАРТА «Устранение» № 28 (25.09.2026): усиление —
            // добытчик у СТАРТОВОГО зарождения.
            return печатное("Разорение", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь добытчик противника",
                "добытчик примыкал к стартовому зарождению.",
                Награда.выбор("energy_swap", "build"),
                Награда.трофеи(1).иСпец(1),
                Утиль.ОТВЕТНЫЙ_ОГОНЬ);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).destroyedTypes.contains("miner");
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            for (var у : убитые(ctx)) {
                if (у.atStartSpawn()) {
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
     * o43 «Охота на сильного» — сбить жетон того, у кого келемия больше.
     *
     * <p>Мерка «сильного» видна глазами: келемий лежит в хранилище открыто.
     * Прежде мерой были здания на поле (до печатной карты 25.09.2026), ещё
     * раньше — победные очки, которые за столом не посчитать.
     */
    public static final class ОхотаНаСильного extends ЗаданиеВКоде {
        public ОхотаНаСильного() {
            super("o43");
        }

        @Override
        public Лицо лицо() {
            // ПЕЧАТНАЯ КАРТА «Устранение» № 34 (25.09.2026): сильнее — по
            // КЕЛЕМИЮ, а не по зданиям; усиление — два жетона, один добытчик.
            return печатное("Охота на сильного", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД уничтожь жетон игрока, у которого больше келемия, чем у тебя.",
                "2 жетона, один из них - добытчик.",
                Награда.выбор("mining", "assembly"),
                Награда.трофеи(1).иСпец(1),
                Утиль.ДВИЖЕНИЕ_ДВУМЯ);
        }

        /** Келемий игрока — мерка «сильного» на печатной карте. */
        private int келемия(CardContext ctx, int seat) {
            return ctx.state().player(seat).resources.kelium();
        }

        /**
         * СНЯТОЕ У ТЕХ, У КОГО В МИГ УДАРА КЕЛЕМИЯ БЫЛО БОЛЬШЕ МОЕГО. Сравнение —
         * на момент удара: к проверке келемий мог уже уйти на Рынок.
         */
        private static java.util.Map<Integer, java.util.List<kelium.core.TurnJournal.Убитый>>
                уБогатого(CardContext ctx) {
            return убитыеУ(ctx, у -> у.victimKelium() > у.myKelium());
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return !уБогатого(ctx).isEmpty();
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            for (var снятые : уБогатого(ctx).values()) {
                if (снятые.size() < 2) {
                    continue;
                }
                for (var у : снятые) {
                    if (BuildingType.MINER.code.equals(у.kind())) {
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
            // ГОДНАЯ ЦЕЛЬ ЕСТЬ ИЛИ ЕЁ НЕТ: жетон именно того игрока, у кого
            // келемия больше моего, и именно в пределах выстрела. Без такой цели
            // близость честный ноль. Проверка повторяет условие карты, но глядя
            // вперёд.
            if (ctx.have(Resource.AMMO) < 1) {
                return 0.0;
            }
            int моих = келемия(ctx, ctx.seat());
            for (UnitToken u : ctx.me().unitsOnField()) {
                for (String гекс : ctx.attackReach(u)) {
                    for (Token t : ctx.enemyTokensOn(гекс)) {
                        if (t.owner() != ctx.seat() && t.owner() >= 0
                                && келемия(ctx, t.owner()) > моих) {
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
                    && келемия(ctx, t.owner()) > моих));
        }

        @Override
        public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "";
            }
            return progress(ctx) >= 0.5
                ? "сбить жетон игрока, у которого келемия больше твоего"
                : "подвести войска к игроку, у которого келемия больше твоего";
        }


        @Override
        protected String действие() {
            return "combat";
        }
    }

}
