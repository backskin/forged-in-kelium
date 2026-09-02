package kelium.cards.arsenal;

import java.util.Map;

import kelium.cards.BaseCard;
import kelium.cards.CardTop;
import kelium.core.Resource;
import kelium.engine.ability.Abilities;
import kelium.engine.ability.Ability;
import kelium.engine.ability.Hint;
import kelium.engine.cards.ArsenalCard;
import kelium.engine.cards.CardContext;

/**
 * КАРТА АРСЕНАЛА КАК ОБЪЕКТ.
 *
 * <p>До этого класса карта арсенала была записью в YAML плюс двумя реестрами:
 * низ — в реестре способностей, верх — в реестре эффектов. Само по себе это
 * устройство работало (все 24 способности набора 2.0 живые), но у карты не было
 * ГЛАВНОГО — умения сказать боту, стоит ли она того ПРЯМО СЕЙЧАС.
 *
 * <p>Отсюда замер 15.08.2026: за партию игрок устанавливает 0.73 карты арсенала и
 * сжигает 1.09. То есть карту чаще выбрасывают ради разовой прибавки, чем
 * играют, — и не потому, что низ слабее, а потому что сжечь даёт понятную выгоду
 * сейчас, а установка — непонятную потом.
 *
 * <p>ЧТО ЗДЕСЬ РЕШЕНО. Способности уже умеют рассказывать о себе: у каждой есть
 * {@link Hint} — какое узкое место она расшивает, насколько сильно и на каком
 * горизонте. Карта берёт это самоописание и превращает в ответ на вопрос
 * «поставить или сжечь», сверяя обещание способности с тем, чего игроку сейчас
 * НЕ ХВАТАЕТ. Рукописных чисел на каждую карту не нужно: незнакомая карта
 * оценивается сама.
 */
public class ArsenalCardBase extends BaseCard implements ArsenalCard {

    public ArsenalCardBase(String id) {
        super(id);
    }

    @Override
    public String passiveId() {
        if (data().get("bottom") instanceof Map<?, ?> b) {
            Object p = b.get("passive");
            return p == null ? null : String.valueOf(p);
        }
        return null;
    }

    /** Низ карты — СПЕЦ-действие (а не постоянная способность). */
    public boolean spec() {
        return data().get("bottom") instanceof Map<?, ?> b
            && "SPEC".equals(String.valueOf(b.get("kind")));
    }

    @Override
    public boolean burn(CardContext ctx) {
        return CardTop.burn(ctx, this);
    }

    // ==================================================================
    //  ГЛАВНОЕ: СТОИТ ЛИ ОНА СЕЙЧАС
    // ==================================================================

    /**
     * Насколько карта полезна прямо сейчас — от 0.0 до 1.0.
     *
     * <p>Для УСТАНОВКИ считается по самоописанию способности: сила обещания,
     * умноженная на то, насколько это узкое место сейчас жмёт, и на то, сколько
     * раундов осталось (постоянная способность в последнем раунде почти ничего
     * не успеет дать — а бот раньше этого не понимал вовсе).
     *
     * <p>Для УТИЛЯ — по тому, что верх выдаёт: разовая прибавка ценна ровно
     * настолько, насколько её хватает закрыть текущую нехватку.
     */
    @Override
    public double usefulness(CardContext ctx, boolean install) {
        return install ? installValue(ctx) : burnValue(ctx);
    }

    /** Ценность установки: обещание способности против текущей нужды. */
    protected double installValue(CardContext ctx) {
        String pid = passiveId();
        Ability a = pid == null ? null : Abilities.byId(pid);
        if (a == null || a.hint() == null) {
            return 0.3;                  // способность без самоописания — среднее
        }
        Hint h = a.hint();
        // УСЛОВИЕ СПОСОБНОСТИ. Некоторые работают, только если на поле есть, к
        // примеру, авиация. Обещание, которое сейчас неисполнимо, не стоит ничего.
        if (h.needs() != null && !h.needs().test(ctx.state(), ctx.seat())) {
            return 0.05;
        }
        double pressure = pressureOn(ctx, h.relieves());
        double left = Math.min(4, Math.max(0, ctx.roundsLeft())) / 4.0;
        double horizon = switch (h.horizon()) {
            case NOW -> 1.0;             // польза сразу — остаток партии не важен
            case THIS_ROUND -> 0.5 + 0.5 * left;
            case REST_OF_GAME -> left;   // постоянная польза стоит столько, сколько раундов
        };
        return clamp(0.15 + 0.85 * norm(h.strength()) * pressure * horizon);
    }

    /**
     * ЦЕНОСТЬ УТИЛЯ: что верх выдаст ЗДЕСЬ И СЕЙЧАС.
     *
     * <p>ПОЧЕМУ ЭТО ПЕРЕПИСАНО (замер 02.09.2026). Раньше сторона утиля была
     * КОНСТАНТОЙ: любое бесплатное действие стоило 0.70, любой прочий эффект —
     * 0.50, и карта свой верх фактически не оценивала. Установка при этом
     * считалась по-настоящему — сила способности на давление узкого места на
     * остаток партии. Сравнение шло «живое число против плоских 0.7», и всякий
     * раз, когда установка слабее, карта уходила в костёр независимо от того,
     * что она делает. Отсюда замер: жгут в 3.4 раза чаще, чем ставят, и не
     * меняется от пересборки колоды.
     *
     * <p>Теперь верх спрашивают то же, что и низ: пригодится ли ты сейчас.
     * Бесплатный Бой без цели и без боеприпасов не стоит ничего; кража
     * установленной карты у того, у кого её нет, не стоит ничего; десант при
     * пустом запасе войск — тоже. Пустой утиль обязан быть дешевле установки,
     * иначе бот сжигает карту «просто потому что можно».
     */
    protected double burnValue(CardContext ctx) {
        if (!(data().get("top") instanceof Map<?, ?> top)) {
            return 0.0;
        }
        String effect = String.valueOf(top.get("effect"));
        Map<?, ?> params = top.get("params") instanceof Map<?, ?> p ? p : Map.of();
        return switch (effect) {
            case "gain" -> ценаВыдачи(ctx, params);
            case "free_action" -> ценаДействия(ctx, String.valueOf(params.get("action")))
                + ценаВыдачи(ctx, params) * 0.5;
            // КРАЖИ И СНОС: цена ровно в том, есть ли у кого брать.
            case "steal_arsenal_card" -> есть(ctx, чужиеКартыАрсенала(ctx)) ? 0.65 : 0.05;
            case "discard_enemy_arsenal" -> есть(ctx, чужиеУстановленные(ctx)) ? 0.7 : 0.05;
            case "steal_objective_cards" -> есть(ctx, чужиеЗадания(ctx)) ? 0.6 : 0.05;
            case "steal_resource" -> 0.45;
            // Позолота без разложенных жетонов модуля невозможна.
            case "gild_module" -> ctx.me().redPlacements.size()
                + ctx.me().bluePlacements.size() > ctx.me().goldModules ? 0.75 : 0.05;
            // Десант ставит войска из ЗАПАСА: запас пуст — ставить нечего.
            case "landing", "deploy_units" -> запасВойск(ctx) > 0
                ? 0.35 + 0.5 * pressureOn(ctx, Hint.Bottleneck.UNITS) : 0.05;
            case "grab_first_player" -> ctx.state().firstPlayer == ctx.seat() ? 0.1 : 0.6;
            case "market_card_from_discard" -> ctx.have(Resource.KELIUM) > 0 ? 0.5 : 0.05;
            case "unlimited_spec" -> 0.3 + 0.5 * pressureOn(ctx, Hint.Bottleneck.ACTIONS);
            case "swap_order_card" -> 0.45;
            case "heal_one", "heal_all_own", "heal_hex" -> ранены(ctx) ? 0.6 : 0.05;
            case "move_unit" -> ctx.me().unitsOnField().isEmpty() ? 0.05 : 0.45;
            default -> 0.5;              // незнакомый эффект — прежняя середина
        };
    }

    /** Цена разовой выдачи ресурсов: сколько дают и насколько это нужно. */
    private double ценаВыдачи(CardContext ctx, Map<?, ?> params) {
        double value = 0;
        value += need(ctx, Resource.COIN) * num(params, "coin") * 0.10;
        value += need(ctx, Resource.AMMO) * num(params, "ammo") * 0.15;
        value += need(ctx, Resource.KELIUM) * num(params, "kelium") * 0.10;
        value += need(ctx, Resource.DEBRIS) * num(params, "debris") * 0.20;
        value += num(params, "objective_cards") * 0.20;
        value += num(params, "containers") * 0.15;
        return clamp(value);
    }

    /**
     * ЦЕНА БЕСПЛАТНОГО ДЕЙСТВИЯ. Ход — самый дорогой ресурс игры (их около
     * двадцати четырёх на партию), поэтому даровое действие дорого САМО ПО СЕБЕ.
     * Но только то, которое можно сыграть с толком: Бой без цели, Добыча без
     * досягаемого келемия и Рынок без келемия не стоят ничего.
     */
    private double ценаДействия(CardContext ctx, String action) {
        return switch (action == null ? "" : action) {
            case "combat" -> ctx.enemyTokensOnField().isEmpty() ? 0.05
                : 0.35 + 0.45 * pressureOn(ctx, Hint.Bottleneck.AMMO);
            case "mining" -> 0.3 + 0.5 * pressureOn(ctx, Hint.Bottleneck.KELIUM);
            case "assembly" -> запитанныеВоенные(ctx) > 0
                ? 0.3 + 0.45 * pressureOn(ctx, Hint.Bottleneck.AMMO) : 0.1;
            case "build" -> ctx.have(Resource.COIN) > 0 ? 0.6 : 0.15;
            case "movement" -> ctx.me().unitsOnField().isEmpty() ? 0.05 : 0.55;
            case "market" -> ctx.have(Resource.KELIUM) > 0 ? 0.6 : 0.05;
            case "science" -> ctx.have(Resource.DEBRIS) > 0
                || ctx.have(Resource.KELIUM) > 0 ? 0.6 : 0.1;
            case "energy_swap" -> голодныеЗдания(ctx) > 0 ? 0.55 : 0.15;
            default -> 0.5;
        };
    }

    private static boolean есть(CardContext ctx, int сколько) {
        return сколько > 0;
    }

    private static int чужиеКартыАрсенала(CardContext ctx) {
        int n = 0;
        for (var p : ctx.state().players) {
            if (p.seat != ctx.seat()) {
                n += p.arsenalHand.size();
            }
        }
        return n;
    }

    private static int чужиеУстановленные(CardContext ctx) {
        int n = 0;
        for (var p : ctx.state().players) {
            if (p.seat != ctx.seat()) {
                n += p.arsenalInstalled.size();
            }
        }
        return n;
    }

    private static int чужиеЗадания(CardContext ctx) {
        int n = 0;
        for (var p : ctx.state().players) {
            if (p.seat != ctx.seat()) {
                n += p.objectiveHand.size();
            }
        }
        return n;
    }

    private static int запасВойск(CardContext ctx) {
        int n = 0;
        for (var u : ctx.me().units) {
            if (u.hexId == null && u.alive()) {
                n++;
            }
        }
        return n;
    }

    private static int запитанныеВоенные(CardContext ctx) {
        int n = 0;
        for (var b : ctx.me().buildingsOnField()) {
            boolean военное = b.type == kelium.core.BuildingType.BARRACKS
                || b.type == kelium.core.BuildingType.FACTORY
                || b.type == kelium.core.BuildingType.AIRBASE
                || b.type == kelium.core.BuildingType.COMMAND_CENTER;
            if (военное && b.powered()) {
                n++;
            }
        }
        return n;
    }

    private static int голодныеЗдания(CardContext ctx) {
        int n = 0;
        for (var b : ctx.me().buildingsOnField()) {
            if (b.energySlots > b.energyPlaced) {
                n++;
            }
        }
        return n;
    }

    private static boolean ранены(CardContext ctx) {
        for (var t : ctx.myTokensOnField()) {
            if (t instanceof kelium.core.UnitToken u && u.damage > 0) {
                return true;
            }
            if (t instanceof kelium.core.BuildingToken b && b.damage > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * НАСКОЛЬКО ЖМЁТ ЭТО УЗКОЕ МЕСТО — от 0.2 (не жмёт) до 1.0 (нечем играть).
     *
     * <p>Ровно та связь, которой у бота не было: он считал карту хорошей или
     * плохой вообще, а не хорошей ДЛЯ ЕГО ПОЛОЖЕНИЯ. Карта, дающая боеприпасы,
     * бесценна при пустом складе и почти бесполезна при полном.
     */
    protected double pressureOn(CardContext ctx, Hint.Bottleneck what) {
        return switch (what) {
            case AMMO -> scarcity(ctx.have(Resource.AMMO), 4);
            case COINS -> scarcity(ctx.have(Resource.COIN), 6);
            case KELIUM -> scarcity(ctx.have(Resource.KELIUM), 4);
            case UNITS -> scarcity(ctx.me().unitsOnField().size(), 4);
            case ENERGY -> {
                int idle = 0;
                for (var b : ctx.me().buildingsOnField()) {
                    idle += b.energyIdle;
                }
                yield scarcity(idle, 3);
            }
            case REACH, DEFENCE -> ctx.me().unitsOnField().isEmpty() ? 0.2 : 0.8;
            case TROPHY -> scarcity(ctx.have(Resource.DEBRIS), 4);
            case ACTIONS, VP -> 0.9;     // действий и очков не хватает всегда
        };
    }

    /** Чем меньше есть от нормы, тем сильнее жмёт. */
    private static double scarcity(int have, int norm) {
        if (norm <= 0) {
            return 0.5;
        }
        return clamp(1.0 - Math.min(1.0, have / (double) norm) * 0.8);
    }

    /** Насколько игроку нужен этот ресурс: 0.2 (завались) … 1.0 (пусто). */
    private static double need(CardContext ctx, Resource r) {
        return scarcity(ctx.have(r), r == Resource.COIN ? 6 : 4);
    }

    private static double num(Map<?, ?> params, String key) {
        return params.get(key) instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** Сила способности из самоописания приводится к 0..1 (за раунд). */
    private static double norm(double strength) {
        return clamp(strength / 3.0);
    }

    protected static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    @Override
    public String describe() {
        Object d = data().get("описание");
        return d == null ? name() : String.valueOf(d);
    }
}
