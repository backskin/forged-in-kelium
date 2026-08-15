package kelium.engine;

import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.Ctx;

/**
 * Power — ПРАВИЛА ЭНЕРГИИ: доступно ли здание для действия прямо сейчас.
 *
 * <p>Вынесено из «действий» отдельно, потому что это правило, а не внутренность
 * одного действия: его спрашивают и Сборка, и Добыча, а проверять его тестом,
 * пока оно было спрятано внутри {@code Actions}, было нельзя.
 *
 * <p>Имя намеренно НЕ «запитано ли»: метод не предикат — он может СПРОСИТЬ
 * игрока и списать монеты. Это прямое правило дизайнера, а не побочный эффект.
 */
public final class Power {

    private Power() {
    }

    /**
     * Здание пригодно для действия РАЗРАБОТКИ (Сборка/Добыча), если запитано —
     * либо если игрок покрывает недостающие ячейки монетами.
     *
     * <p><b>Правило (дизайнер, 2026-08-12).</b> Компенсация энергии монетами
     * работает ТОЛЬКО в приказе Разработка — то есть ровно в двух действиях,
     * где вообще проверяется запитанность. Оплата <b>одноразовая</b>: она
     * действует на ЭТО одно действие, кубик в ячейку не кладётся, и в следующую
     * Сборку (или Добычу) за то же здание придётся платить снова. Поэтому
     * дешевле один раз довезти энергию и больше не платить никогда.
     *
     * <p>Цена ячейки — {@code actions.empty_energy_slot_coin_cost} из ruleset.
     * Решение платить или нет принимает игрок (выбор {@code pay_power}).
     */
    public static boolean usableForAction(GameState state, PlayerState player,
                                          BuildingToken b, Agent agent) {
        return usableForAction(state, player, b, agent, null);
    }

    /**
     * То же, но с накопителем: {@code paid[0]} += сколько монет ушло на
     * компенсацию, {@code paid[1]} += сколько раз компенсация предлагалась.
     * Нужен телеметрии — иначе не видно, пользуются боты правилом или нет.
     */
    public static boolean usableForAction(GameState state, PlayerState player,
                                          BuildingToken b, Agent agent, int[] paid) {
        // ТОЧКА ПРАВИЛ: сколько энергии этому зданию нужно для действия. Карта
        // арсенала может снизить требование («авиабаза собирает на одну энергию
        // дешевле»), и тогда наполовину запитанное здание уже работает.
        int need = (int) Math.round(kelium.engine.ability.RuleQuery
            .of(state, player.seat, kelium.engine.ability.Hook.ASSEMBLY_ENERGY_NEEDED)
            .about(b).base(b.energySlots).ask());
        if (b.energyPlaced >= need) {
            return true;
        }
        int rate = Ctx.rules(state)
            .getInt("actions.empty_energy_slot_coin_cost", 1);
        int empty = need - b.energyPlaced;
        int cost = empty * rate;
        if (cost <= 0 || !player.resources.canPay(Resource.COIN, cost)) {
            return false;
        }
        if (paid != null) {
            paid[1] += 1;
        }
        List<Choice> opts = List.of(
            new Choice("pay_power", Boolean.TRUE,
                "запитать " + b.type.code + " монетами (" + cost + " МОН на это действие)"),
            new Choice("pay_power", Boolean.FALSE, "не платить"));
        Choice ch = agent.choose(state, opts, Map.of("kind", "pay_power",
            "cost", cost, "building", b.uid, "type", b.type.code));
        if (Boolean.TRUE.equals(ch.payload())) {
            player.resources.pay(Resource.COIN, cost);
            if (paid != null) {
                paid[0] += cost;
            }
            return true;
        }
        return false;
    }
}
