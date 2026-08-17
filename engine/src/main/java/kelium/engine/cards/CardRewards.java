package kelium.engine.cards;

import java.util.Map;

import kelium.core.Resource;

/**
 * РАЗДАЧА НАГРАД ПО ЗАПИСИ КАРТЫ.
 *
 * <p>НАЙДЕНО 17.08.2026: здесь была ВТОРАЯ, РАСХОДЯЩАЯСЯ система награды. Этот
 * разбор читал ключи {@code reward} и {@code reward_enhanced}, а каталог и сам
 * движок ({@code Objectives.grantBase}) писали и читали {@code base_reward} и
 * {@code special_reward}. Ключей {@code reward} в каталоге не было ни у одной
 * карты, поэтому всякая карта, полагавшаяся на этот общий разбор, выдавала РОВНО
 * НИЧЕГО.
 *
 * <p>Почему это не всплыло раньше. В живой партии награду раздаёт движок своим
 * путём, а этот метод зовёт только договорный тест карт — и тот молчал, потому
 * что в модуле карт партия не поднимается, записи к картам не подключались и
 * проверять было нечего. Два пустых механизма, подтверждавших друг друга.
 * Вскрылось в тот момент, когда карты стали описывать себя сами и запись у них
 * появилась всегда.
 *
 * <p>Теперь читаются ТЕ ЖЕ ключи, которыми награду раздаёт движок:
 *
 * <pre>
 *   base_reward:    {coin: 2, ammo: 1, debris: 1, kelium: 1, vp: 1}
 *   special_reward: {coin: 4, vp: 2}
 * </pre>
 *
 * <p>Старые имена {@code reward} / {@code reward_enhanced} остаются запасным
 * чтением: наборы карт неизменяемы, и старая версия каталога обязана работать
 * без правки данных.
 *
 * <p>Карта переопределяет {@link ObjectiveCard#reward} только если её награда не
 * выражается числами — например, «поставь войско из запаса».
 */
public final class CardRewards {

    private CardRewards() {
    }

    /** Выдать награду карты из её записи в данных. */
    public static void grantFromData(CardContext ctx, Card card, boolean enhanced) {
        Object node = узел(card, enhanced);
        // УСИЛЕННОЙ НАГРАДЫ МОЖЕТ НЕ БЫТЬ — тогда выдаём обычную, а не ничего.
        // Обратное («нет обычной») — ошибка данных, но падать из-за неё посреди
        // партии нельзя: карта просто не даст ничего, и это будет видно в отчёте.
        if (node == null && enhanced) {
            node = узел(card, false);
        }
        if (!(node instanceof Map<?, ?> reward)) {
            return;
        }
        give(ctx, reward, "coin", Resource.COIN);
        give(ctx, reward, "kelium", Resource.KELIUM);
        give(ctx, reward, "ammo", Resource.AMMO);
        give(ctx, reward, "trophy", Resource.DEBRIS);
        give(ctx, reward, "debris", Resource.DEBRIS);
        if (reward.get("vp") instanceof Number vp && vp.intValue() != 0) {
            ctx.grantVp(vp.intValue(), "objective:" + card.id());
        }
    }

    /** Запись награды: сперва имена каталога, затем старые — для прежних версий. */
    private static Object узел(Card card, boolean enhanced) {
        Map<String, Object> d = card.data();
        Object node = d.get(enhanced ? "special_reward" : "base_reward");
        return node != null ? node : d.get(enhanced ? "reward_enhanced" : "reward");
    }

    private static void give(CardContext ctx, Map<?, ?> reward, String key, Resource r) {
        if (reward.get(key) instanceof Number n && n.intValue() != 0) {
            ctx.gain(r, n.intValue());
        }
    }
}
