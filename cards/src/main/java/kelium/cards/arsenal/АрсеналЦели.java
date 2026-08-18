package kelium.cards.arsenal;

import java.util.HashMap;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.cards.CardContext;

import static kelium.cards.arsenal.ЛицоАрсенала.Вид.ОБЫЧНАЯ;
import static kelium.cards.arsenal.ЛицоАрсенала.НизВид.SCORING;

/**
 * КАРТЫ-ЦЕЛИ АРСЕНАЛА 2.3.0 (v01-v06) — очки за ПОЛОЖЕНИЕ жетона, не за
 * накопление. Реальный подсчёт очков в конце партии делает
 * {@code Scoring.scorePlayer} по записи {@code bottom.scoring}; здесь
 * {@link КартаЦелиВКоде#близость} считает то же самое ТЕМИ ЖЕ КРИТЕРИЯМИ —
 * это и есть исправление бага «карта близка к условию, а бот думает, что нет».
 */
public final class АрсеналЦели {

    private АрсеналЦели() {
    }

    /** Своё войско на гексе, где есть жетоны противника. */
    private static boolean наГексеПротивник(CardContext ctx, String hexId) {
        return hexId != null && !ctx.enemyTokensOn(hexId).isEmpty();
    }

    /** Единственный свой жетон на гексе (кроме названного uid). */
    private static boolean одинНаГексе(CardContext ctx, String hexId, int uid) {
        if (hexId == null) {
            return false;
        }
        for (UnitToken u : ctx.me().unitsOnField()) {
            if (u.uid != uid && hexId.equals(u.hexId)) {
                return false;
            }
        }
        for (BuildingToken b : ctx.me().buildingsOnField()) {
            if (hexId.equals(b.hexId)) {
                return false;
            }
        }
        return true;
    }

    public static final class ВоздушноеКрыло extends КартаЦелиВКоде {
        public ВоздушноеКрыло() {
            super("v01");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Воздушное крыло", ОБЫЧНАЯ, false,
                "free_action", Map.of("action", "build"), "выполни Стройку",
                SCORING, null, Map.of("per", 1, "of", "aircraft_on_enemy_hex", "vp", 1),
                "1 ПО за каждую свою авиацию на гексе, где есть жетоны противника",
                "Утиль позволяет выполнить Стройку без расхода приказа. "
                + "Установленная карта в конце партии приносит очко за каждый ваш "
                + "жетон авиации, стоящий на гексе с жетонами противника. Платит "
                + "не за то, что авиация у вас есть, а за то, что она висит над "
                + "чужой головой.");
        }

        @Override
        protected double близость(CardContext ctx) {
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (u.type == UnitType.AIRCRAFT && наГексеПротивник(ctx, u.hexId)) {
                    return 1.0;
                }
            }
            // Есть авиация вообще — половина дела, осталось её подвести.
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (u.type == UnitType.AIRCRAFT) {
                    return 0.4;
                }
            }
            return 0.0;
        }
    }

    public static final class ТанковыйКорпус extends КартаЦелиВКоде {
        public ТанковыйКорпус() {
            super("v02");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Танковый корпус", ОБЫЧНАЯ, false,
                "gain", Map.of("ammo", 3), "3 боеприпаса",
                SCORING, null, Map.of("per", 1, "of", "vehicles_alone_on_hex", "vp", 1),
                "1 ПО за каждую свою технику на гексе без других твоих войск и зданий",
                "Утиль сразу приносит 3 боеприпаса. Установленная карта приносит "
                + "очко за каждый ваш жетон техники, стоящий на гексе, где нет ни "
                + "одного другого вашего жетона. Одинокий танк в чужом тылу — "
                + "дорого и опасно, потому и оплачено.");
        }

        @Override
        protected double близость(CardContext ctx) {
            for (UnitToken v : ctx.me().unitsOnField()) {
                if (v.type == UnitType.VEHICLE && одинНаГексе(ctx, v.hexId, v.uid)) {
                    return 1.0;
                }
            }
            for (UnitToken v : ctx.me().unitsOnField()) {
                if (v.type == UnitType.VEHICLE) {
                    return 0.4;
                }
            }
            return 0.0;
        }
    }

    public static final class СторожеваяСеть extends КартаЦелиВКоде {
        public СторожеваяСеть() {
            super("v03");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Сторожевая сеть", ОБЫЧНАЯ, false,
                "free_action", Map.of("action", "mining"), "выполни Добычу",
                SCORING, null, Map.of("per", 1, "of", "lone_tower_hexes", "vp", 1),
                "1 ПО за каждый гекс с твоей вышкой, где нет других твоих войск и зданий",
                "Утиль позволяет выполнить Добычу без расхода приказа. "
                + "Установленная карта приносит очко за каждый гекс, где стоит "
                + "ваша вышка и больше ничего вашего. Вышка неподвижна и ставится "
                + "только там, где есть ваше здание, — значит выносить её в "
                + "одиночку приходится заранее и осознанно.");
        }

        @Override
        protected double близость(CardContext ctx) {
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (u.type == UnitType.TOWER && одинНаГексе(ctx, u.hexId, u.uid)) {
                    return 1.0;
                }
            }
            for (UnitToken u : ctx.me().unitsOnField()) {
                if (u.type == UnitType.TOWER) {
                    return 0.4;
                }
            }
            return 0.0;
        }
    }

    public static final class ВторойЭшелон extends КартаЦелиВКоде {
        public ВторойЭшелон() {
            super("v04");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Второй эшелон", ОБЫЧНАЯ, false,
                "gain", Map.of("objective_cards", 2), "2 карты задания",
                SCORING, null, Map.of("per", 1, "of", "miner_plant_level_pairs", "vp", 1),
                "1 ПО за каждую пару «добытчик и энергостанция одного уровня» на поле",
                "Утиль даёт две карты задания. Установленная карта приносит очко "
                + "за каждую пару из добытчика и энергостанции ОДНОГО уровня, "
                + "стоящих у вас на поле. Заставляет строить экономику ровно, а не "
                + "гнать один номер вперёд, пока второй отстаёт.");
        }

        @Override
        protected double близость(CardContext ctx) {
            Map<Integer, Integer> miners = new HashMap<>();
            Map<Integer, Integer> plants = new HashMap<>();
            for (BuildingToken b : ctx.me().buildingsOnField()) {
                if (b.level == null) {
                    continue;
                }
                if (b.type == BuildingType.MINER) {
                    miners.merge(b.level, 1, Integer::sum);
                } else if (b.type == BuildingType.POWER_PLANT) {
                    plants.merge(b.level, 1, Integer::sum);
                }
            }
            int pairs = 0;
            for (var e : miners.entrySet()) {
                pairs += Math.min(e.getValue(), plants.getOrDefault(e.getKey(), 0));
            }
            if (pairs > 0) {
                return 1.0;
            }
            // Есть хоть один добытчик и хоть одна станция — половина дела,
            // осталось свести их уровни.
            return miners.isEmpty() || plants.isEmpty() ? 0.0 : 0.4;
        }
    }

    public static final class ДиверсионнаяГруппа extends КартаЦелиВКоде {
        public ДиверсионнаяГруппа() {
            super("v05");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Диверсионная группа", ОБЫЧНАЯ, false,
                "market_card_from_discard", Map.of(),
                "верни на маркет сброшенную карту сделок на рынке",
                SCORING, null, Map.of("per", 1, "of", "kelium_ammo_pairs", "vp", 1),
                "1 ПО за каждую пару «келемий и боеприпас» в хранилище",
                "Утиль возвращает на маркет сброшенную карту сделок на рынке. "
                + "Установленная карта приносит очко за каждую пару келемия и "
                + "боеприпаса в вашем хранилище: считается по меньшему из двух. "
                + "Платит за равновесие склада, а не за одну гору — а места в "
                + "хранилище на всё сразу не хватает.");
        }

        @Override
        protected double близость(CardContext ctx) {
            int pairs = Math.min(ctx.have(Resource.KELIUM), ctx.have(Resource.AMMO));
            if (pairs > 0) {
                return 1.0;
            }
            return ctx.have(Resource.KELIUM) > 0 || ctx.have(Resource.AMMO) > 0 ? 0.4 : 0.0;
        }
    }

    public static final class ВоеннаяДоктрина extends КартаЦелиВКоде {
        public ВоеннаяДоктрина() {
            super("v06");
        }

        @Override
        public ЛицоАрсенала лицо() {
            return new ЛицоАрсенала("Военная доктрина", ОБЫЧНАЯ, false,
                "grab_first_player", Map.of("steal_coin", 1),
                "забери жетон первого игрока и 1 монету у его владельца",
                SCORING, null,
                Map.of("combo", java.util.List.of(Map.of("of", "cu_tokens", "at_least", 1)), "vp", 2),
                "2 ПО, если у тебя есть жетон уничтожения ЦУ",
                "Утиль забирает жетон первого игрока вместе с монетой у его "
                + "прежнего владельца. Установленная карта приносит 2 победных "
                + "очка, если к концу партии у вас есть жетон уничтожения чужого "
                + "ЦУ. Единственная карта колоды, которую нельзя выполнить, не "
                + "штурмуя чужой штаб.");
        }

        @Override
        protected double близость(CardContext ctx) {
            return ctx.me().cuDestructionTokens >= 1 ? 1.0 : 0.0;
        }
    }
}
