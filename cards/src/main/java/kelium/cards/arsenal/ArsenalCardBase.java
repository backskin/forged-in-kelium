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
        return kelium.engine.cards.TopValue.of(ctx,
            data().get("top") instanceof Map<?, ?> top ? top : null);
    }

    /**
     * НАСКОЛЬКО ЖМЁТ ЭТО УЗКОЕ МЕСТО — от 0.2 (не жмёт) до 1.0 (нечем играть).
     *
     * <p>Ровно та связь, которой у бота не было: он считал карту хорошей или
     * плохой вообще, а не хорошей ДЛЯ ЕГО ПОЛОЖЕНИЯ. Карта, дающая боеприпасы,
     * бесценна при пустом складе и почти бесполезна при полном.
     */
    protected double pressureOn(CardContext ctx, Hint.Bottleneck what) {
        return kelium.engine.cards.TopValue.давление(ctx, what);
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
