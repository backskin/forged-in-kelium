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

    /** Ценность утиля: разовая выдача против текущей нехватки. */
    protected double burnValue(CardContext ctx) {
        if (!(data().get("top") instanceof Map<?, ?> top)) {
            return 0.0;
        }
        String effect = String.valueOf(top.get("effect"));
        Map<?, ?> params = top.get("params") instanceof Map<?, ?> p ? p : Map.of();
        // Бесплатное действие — это ход, а ход в этой игре самый дорогой ресурс:
        // их всего около двадцати четырёх на партию.
        if ("free_action".equals(effect)) {
            return 0.7;
        }
        if (!"gain".equals(effect)) {
            return 0.5;                  // лечение, перемещение и прочее — середина
        }
        double value = 0;
        value += need(ctx, Resource.COIN) * num(params, "coin") * 0.10;
        value += need(ctx, Resource.AMMO) * num(params, "ammo") * 0.15;
        value += need(ctx, Resource.KELIUM) * num(params, "kelium") * 0.10;
        value += need(ctx, Resource.DEBRIS) * num(params, "debris") * 0.20;
        value += num(params, "objective_cards") * 0.20;
        return clamp(value);
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
