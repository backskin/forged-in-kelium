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
     * СКОЛЬКО КУБИКОВ ЭНЕРГИИ ДАЁТ ЭТОТ ИСТОЧНИК. Единственное место, где это
     * считается: и стройка (кубики приходят на здание), и Смена энергии (пул
     * снимается и раскладывается заново) обязаны получать одно и то же число.
     *
     * <p><b>Правило жёлтой ячейки (дизайнер, 16.08.2026).</b> На каждом гексе
     * картонного блока напечатана одна наземная ячейка жёлтым цветом — отдельная
     * от ячейки с контейнером. Энергостанция выдаёт свой номинал ТОЛЬКО стоя на
     * ней. Построенная на любой другой ячейке, она даёт ровно 1 кубик энергии,
     * какого бы уровня ни была. Поэтому место под станцию — решение, а не
     * «куда влезло»: жёлтых ячеек на гексе одна из шести, и станцию №4 (номинал
     * 3) на чужой ячейке строить бессмысленно.
     *
     * <p>ЦУ правилу не подчиняется: оно сам себе источник и потребитель и стоит
     * там, где стоит.
     *
     * <p>Пассив «+1 станциям» (карта арсенала) складывается СВЕРХ полученного
     * числа: правило жёлтой ячейки обнуляет вклад уровня, а не карт.
     */
    public static int plantOutput(GameState state, BuildingToken b) {
        if (b.type != kelium.core.BuildingType.POWER_PLANT) {
            return state.tokenStats.buildingEnergyGives(b.type);
        }
        int nominal = state.tokenStats.plantEnergyGives(b.level);
        if (!onEnergyCell(state, b)) {
            int off = Ctx.rules(state).getInt("energy.plant_off_cell_gives", 1);
            return Math.min(nominal, off);
        }
        return nominal;
    }

    /**
     * Стоит ли здание на ЖЁЛТОЙ ЯЧЕЙКЕ своего гекса. След энергостанции — одна
     * ячейка, так что вопрос сводится к «эта ли ячейка занята нашим жетоном».
     *
     * <p>Если гекс не размечен ({@code energyCell < 0} — ручные сцены тестов и
     * поля без набора блоков), правило не применяется и станция считается
     * стоящей верно: молча резать выработку там, где жёлтой ячейки вообще нет,
     * значило бы менять правило полем данных.
     */
    public static boolean onEnergyCell(GameState state, BuildingToken b) {
        if (b.hexId == null) {
            return false;
        }
        kelium.core.Hex h = state.field.get(b.hexId);
        if (h == null || h.energyCell < 0) {
            return true;
        }
        Integer owner = h.sideOwner[h.energyCell];
        return owner != null && owner == b.uid;
    }

    /**
     * Здание пригодно для действия РАЗРАБОТКИ (Сборка/Добыча), только если оно
     * ЗАПИТАНО.
     *
     * <p><b>ДОПЛАТЫ МОНЕТАМИ ЗА НЕДОСТАЮЩУЮ ЭНЕРГИЮ В ИГРЕ БОЛЬШЕ НЕТ</b>
     * (правило дизайнера 17.08.2026). Прежде пустую ячейку ЭНР можно было
     * закрыть монетой на одно действие, и это было общим правилом; теперь
     * такого правила нет. Энергия — главный дефицит игры, и возможность
     * покупать её за деньги обесценивала и Смену энергии, и планировку базы.
     *
     * <p>Право на доплату даёт ТОЛЬКО карта арсенала («Аварийное питание»)
     * через точку правил {@link Hook#ASSEMBLY_PAY_ENERGY_WITH_COIN}: база 0 —
     * нельзя, карта поднимает до 1. Цена ячейки при этом берётся всё из того же
     * ключа {@code actions.empty_energy_slot_coin_cost}: карта возвращает
     * прежнее правило, а не заводит своё.
     *
     * <p>Оплата остаётся <b>одноразовой</b>: она действует на ЭТО одно
     * действие, кубик в ячейку не кладётся.
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
        // ТОЧКА ПРАВИЛ: разрешена ли доплата монетами вообще. База 0 — общего
        // правила «закрой ячейку монетой» в игре нет; единица приходит только с
        // карты арсенала «Аварийное питание».
        boolean mayPay = kelium.engine.ability.RuleQuery
            .of(state, player.seat, kelium.engine.ability.Hook.ASSEMBLY_PAY_ENERGY_WITH_COIN)
            .about(b).base(0).ask() >= 1;
        if (!mayPay) {
            return false;
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
