package kelium.cards.arsenal;

import kelium.engine.cards.CardContext;

/**
 * КАРТА-ЦЕЛЬ АРСЕНАЛА, ЖИВУЩАЯ ЦЕЛИКОМ В КОДЕ — общая часть шести карт (v01-v06).
 *
 * <p>Низ карты-цели — не способность, а условие на очки в конце партии;
 * реальный подсчёт этих очков делает {@code kelium.engine.Scoring} по записи
 * {@code bottom.scoring}, и эта часть кода не трогается: она работает верно.
 *
 * <p>НАЙДЕНО ПРИ ПЕРЕЕЗДЕ 18.08.2026: у прежнего {@link GoalCard} эвристика
 * «стоит ли ставить карту сейчас» ({@code installValue}) считала близость к
 * условию через строковый диспетчер {@code count(ctx, what)} — и для всех
 * ШЕСТИ карт нового каталога 2.3.0 (aircraft_on_enemy_hex,
 * vehicles_alone_on_hex, lone_tower_hexes, miner_plant_level_pairs,
 * kelium_ammo_pairs, cu_tokens-комбо) в этом диспетчере не было ветки — они
 * молча считались как «условие никогда не близко» (default -> 0). Это НЕ
 * баг подсчёта очков (в {@code Scoring} все шесть условий разобраны верно),
 * а недооценка бота: карта, до которой оставался один шаг, выглядела для
 * бота такой же далёкой, как совершенно недостижимая, и он охотнее жёг её
 * ради утиля, чем ставил.
 *
 * <p>Здесь каждая карта считает свою близость САМА, прямыми типизированными
 * вопросами к {@link CardContext} — тем же способом, каким карты заданий
 * считают своё условие, а не через реестр строк.
 */
public abstract class КартаЦелиВКоде extends КартаАрсеналаВКоде {

    protected КартаЦелиВКоде(String id) {
        super(id);
    }

    /** У карты-цели нет способности: низ считает очки, а не меняет правила. */
    @Override
    public final String passiveId() {
        return null;
    }

    /**
     * Насколько близко условие к выполнению прямо сейчас: 0.0 (даже не
     * начато) … 1.0 (выполнено полностью, дальнейший рост очков не считается).
     */
    protected abstract double близость(CardContext ctx);

    @Override
    protected final double installValue(CardContext ctx) {
        double left = Math.min(4, Math.max(0, ctx.roundsLeft())) / 4.0;
        double closeness = clamp01(близость(ctx));
        // Даже далёкая цель чего-то стоит, если впереди вся партия; ближе к
        // концу партии цена определяется почти целиком тем, сколько уже набрано.
        return clamp(0.1 + 0.9 * (0.4 * left + 0.6 * closeness * left));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
