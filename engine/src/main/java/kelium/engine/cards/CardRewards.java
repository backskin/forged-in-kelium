package kelium.engine.cards;

import java.util.Map;

import kelium.core.Resource;

/**
 * РАЗДАЧА НАГРАД ПО ЗАПИСИ КАРТЫ.
 *
 * <p>Награда — это числа, а числа правит дизайнер, поэтому они остаются в YAML и
 * НЕ переезжают в код. Здесь общий разбор записи, чтобы каждой карте не
 * переписывать одно и то же:
 *
 * <pre>
 *   reward:          {coin: 2, ammo: 1, vp: 1, trophy: 2, kelium: 1, debris: 1}
 *   reward_enhanced: {coin: 4, vp: 2}
 * </pre>
 *
 * <p>Карта переопределяет {@link ObjectiveCard#reward} только если её награда
 * не выражается числами — например, «поставь войско из запаса».
 */
public final class CardRewards {

    private CardRewards() {
    }

    /** Выдать награду карты из её записи в данных. */
    public static void grantFromData(CardContext ctx, Card card, boolean enhanced) {
        Object node = card.data().get(enhanced ? "reward_enhanced" : "reward");
        // УСИЛЕННОЙ НАГРАДЫ МОЖЕТ НЕ БЫТЬ — тогда выдаём обычную, а не ничего.
        // Обратное («нет обычной») — ошибка данных, но падать из-за неё посреди
        // партии нельзя: карта просто не даст ничего, и это будет видно в отчёте.
        if (node == null && enhanced) {
            node = card.data().get("reward");
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

    private static void give(CardContext ctx, Map<?, ?> reward, String key, Resource r) {
        if (reward.get(key) instanceof Number n && n.intValue() != 0) {
            ctx.gain(r, n.intValue());
        }
    }
}
