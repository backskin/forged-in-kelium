package kelium.cards.arsenal;

import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.engine.cards.CardContext;

import static kelium.cards.arsenal.ЛицоАрсенала.Вид.СТАРТОВАЯ;
import static kelium.cards.arsenal.ЛицоАрсенала.НизВид.POST;
import static kelium.cards.arsenal.ЛицоАрсенала.НизВид.SPEC;

/** АРСЕНАЛ 2.3.0 — стартовые карты (bs1-bs8, по одной каждому игроку). */
public final class АрсеналСтартовые {

    private АрсеналСтартовые() {
    }

    public static final class ПолевойГенератор extends КартаАрсеналаВКоде {
        public ПолевойГенератор() {
            super("bs1");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Полевой генератор", СТАРТОВАЯ, false,
                "free_action", Map.of("action", "market"), "выполни Маркет",
                POST, "card_is_energy_source_upkeep", null,
                "считай эту карту отдельным гексом с одним кубиком энергии; в "
                + "Обновление заплати 1 монету, иначе удали карту из игры",
                "Утиль позволяет выполнить Маркет без расхода приказа. Пока карта "
                + "установлена, считайте её отдельным гексом с одним кубиком "
                + "энергии: этот кубик участвует в Смене энергии наравне с "
                + "кубиками на поле. В фазу Обновления за карту нужно платить 1 "
                + "монету — иначе она уходит из игры навсегда.");
        }
    }

    public static final class ОтветныйЗалп extends КартаАрсеналаВКоде {
        public ОтветныйЗалп() {
            super("bs2");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Ответный залп", СТАРТОВАЯ, false,
                "gain", Map.of("ammo", 3), "3 боеприпаса",
                POST, "ammo_on_retaliation_kill", null,
                "если тебя контратаковали и уничтожили твой жетон — получи 1 "
                + "боеприпас",
                "Утиль сразу приносит 3 боеприпаса. Пока карта установлена, если в "
                + "ответном бою противник уничтожил ваш жетон, вы получаете 1 "
                + "боеприпас. Карта платит не за сам факт контратаки, а за "
                + "понесённую потерю.");
        }
    }

    public static final class Мародёрка extends КартаАрсеналаВКоде {
        public Мародёрка() {
            super("bs3");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Мародёрка", СТАРТОВАЯ, false,
                "gain", Map.of("kelium", 2), "2 келемия",
                SPEC, "spec_loot_enemy_building_hex", null,
                "СПЕЦ: если твои войска на гексе со зданием противника — забери у "
                + "него 1 боеприпас или 1 келемий",
                "Утиль сразу приносит 2 келемия. Пока карта установлена, "
                + "спец-действием можно забрать у противника 1 боеприпас или 1 "
                + "келемий, если ваши войска стоят на гексе с его зданием. Хорошая "
                + "награда за то, что вы стоите там, где вам стоять не должны.");
        }
    }

    public static final class ШтабнаяПочта extends КартаАрсеналаВКоде {
        public ШтабнаяПочта() {
            super("bs4");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Штабная почта", СТАРТОВАЯ, false,
                "gain", Map.of("objective_cards", 3), "3 карты задания",
                SPEC, "spec_draw_two_objectives_keep_one", null,
                "СПЕЦ: 1 монета — возьми две карты задания, оставь одну",
                "Утиль сразу приносит 3 карты задания. Пока карта установлена, "
                + "спец-действием за 1 монету можно взять две карты задания и "
                + "оставить себе одну — по своему выбору. Помогает подобрать "
                + "задание точнее под текущую партию.");
        }
    }

    /** bs5 «Премия за голову» — своя оценка: зависит от достижимых чужих жетонов. */
    public static final class ПремияЗаГолову extends КартаАрсеналаВКоде {
        public ПремияЗаГолову() {
            super("bs5");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Премия за голову", СТАРТОВАЯ, false,
                "free_action", Map.of("action", "build"), "выполни Стройку",
                POST, "coin_on_kill", null,
                "в конце Боя, если уничтожил хотя бы один жетон — 1 монета",
                "Утиль позволяет выполнить Стройку без расхода приказа. Пока "
                + "карта установлена, если в конце Боя вы уничтожили хотя бы один "
                + "жетон противника, вы получаете 1 монету — один раз за действие, "
                + "а не за каждое убийство.");
        }

        @Override
        protected double installValue(CardContext ctx) {
            int reachable = 0;
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (u.hexId == null) {
                    continue;
                }
                for (String nb : ctx.state().field.neighbors(u.hexId)) {
                    for (PlayerState other : ctx.state().players) {
                        if (other.seat == ctx.seat()) {
                            continue;
                        }
                        for (UnitToken e : other.unitsOnField()) {
                            if (nb.equals(e.hexId)) {
                                reachable++;
                            }
                        }
                        for (BuildingToken b : other.buildingsOnField()) {
                            if (nb.equals(b.hexId)) {
                                reachable++;
                            }
                        }
                    }
                }
            }
            double left = Math.min(4, Math.max(0, ctx.roundsLeft())) / 4.0;
            return clamp(0.1 + 0.9 * clamp(reachable / 4.0) * left);
        }
    }

    public static final class АварийныеЩиты extends КартаАрсеналаВКоде {
        public АварийныеЩиты() {
            super("bs6");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Аварийные щиты", СТАРТОВАЯ, false,
                "swap_order_card", Map.of(),
                "замени 1 карту приказа в руке на карту из своего сброса",
                POST, "economy_plus1_hp_returns_on_damage", null,
                "твои добытчики и энергостанции с прочностью 1 имеют прочность 2; "
                + "получив урон, такое здание сразу после боя возвращается тебе в "
                + "запас",
                "Утиль меняет карту приказа в руке на карту из вашего сброса. Пока "
                + "карта установлена, ваши добытчики и энергостанции с прочностью 1 "
                + "держат два попадания — но, получив урон, такое здание сразу "
                + "после боя возвращается вам в запас. Карта не спасает здание, а "
                + "меняет форму его потери: противник тратит боеприпас и не "
                + "получает ни трофея, ни очков.");
        }
    }

    public static final class Переформирование extends КартаАрсеналаВКоде {
        public Переформирование() {
            super("bs7");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Переформирование", СТАРТОВАЯ, false,
                "free_action", Map.of("action", "mining"), "выполни Добычу",
                SPEC, "spec_swap_ground_unit", null,
                "СПЕЦ: 2 монеты — замени на поле один свой жетон наземного войска "
                + "на другой наземный жетон из запаса",
                "Утиль позволяет выполнить Добычу без расхода приказа. Пока карта "
                + "установлена, спец-действием за 2 монеты один ваш наземный жетон "
                + "на поле меняется на другой наземный жетон из запаса — пехота на "
                + "технику или наоборот. Ничего не добавляет, а пересобирает уже "
                + "стоящее: полезно ровно тогда, когда рода войск оказались не там, "
                + "где нужны.");
        }
    }

    /** bs8 «Вольная застройка» — своя оценка: зависит от тесноты вокруг базы. */
    public static final class ВольнаяЗастройка extends КартаАрсеналаВКоде {
        public ВольнаяЗастройка() {
            super("bs8");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Вольная застройка", СТАРТОВАЯ, false,
                "free_action", Map.of("action", "build"), "выполни Стройку",
                POST, "build_on_adjacent_with_own_units", null,
                "можешь строить на гексах, соседних с твоими зданиями, без "
                + "примыкания стенкой, если на таком гексе стоят твои войска",
                "Утиль позволяет выполнить Стройку без расхода приказа. Пока "
                + "карта установлена, вы можете строить на гексах, соседних с "
                + "вашими зданиями, без обязательного примыкания стенкой — но "
                + "только там, где уже стоят ваши войска. База идёт следом за "
                + "армией, а не растёт сама.");
        }

        @Override
        protected double installValue(CardContext ctx) {
            int blocked = 0;
            int looked = 0;
            for (BuildingToken b : ctx.me().buildingsOnField()) {
                if (b.hexId == null) {
                    continue;
                }
                for (String nb : ctx.state().field.neighbors(b.hexId)) {
                    looked++;
                    var hex = ctx.state().field.get(nb);
                    if (hex != null && hex.hasNeutral()) {
                        blocked++;
                    }
                }
            }
            if (looked == 0) {
                return 0.1;
            }
            double tightness = blocked / (double) looked;
            double left = Math.min(4, Math.max(0, ctx.roundsLeft())) / 4.0;
            return clamp(0.1 + 0.9 * tightness * left);
        }
    }
}
