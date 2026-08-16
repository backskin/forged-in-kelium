package kelium.cards;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import kelium.engine.Effects;
import kelium.engine.cards.Card;
import kelium.engine.cards.CardContext;

/**
 * ВЕРХНИЙ ЭФФЕКТ КАРТЫ — то, ради чего её сжигают, не выполняя.
 *
 * <p>Верхи описаны в данных однообразно ({@code top: {effect, params}}) и почти
 * всегда сводятся к «выдать ресурс» или «дать бесплатное действие». Поэтому
 * общий разбор один на все карты, а своё поведение переопределяют только те
 * немногие, чей верх делает что-то особенное.
 */
public final class CardTop {

    private CardTop() {
    }

    private static final Set<String> WARNED = new LinkedHashSet<>();

    /**
     * Сказать о неполадке карты ОДИН РАЗ на запуск.
     *
     * <p>Зачем не каждый раз: в батче на десятки тысяч партий одна такая строка
     * превращается в сотни тысяч и топит весь лог — так уже было, 92% вывода
     * прогона составляли повторы одного сообщения. Но и молчать нельзя: молчание
     * ровно тем и опасно, что мёртвая карта выглядит рабочей.
     */
    public static void warnOnce(String what, String cardId) {
        if (WARNED.add(cardId + "|" + what)) {
            System.err.println("[КАРТЫ] " + cardId + ": " + what);
        }
    }

    /** Что уже сказано про карты за этот запуск — для отчётов. */
    public static Set<String> warnings() {
        return Set.copyOf(WARNED);
    }

    /**
     * Сыграть верхний эффект карты по её записи в данных.
     *
     * @return {@code false}, если верха нет или эффект не реализован
     */
    public static boolean burn(CardContext ctx, Card card) {
        if (!(card.data().get("top") instanceof Map<?, ?> top)) {
            return false;
        }
        String effect = String.valueOf(top.get("effect"));
        if (!Effects.isImplemented(effect)) {
            warnOnce("верхний эффект " + effect + " не реализован", card.id());
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> params = top.get("params") instanceof Map<?, ?> p
            ? (Map<String, Object>) p : Map.of();
        Effects.apply(effect, ctx.state(), ctx.seat(), params);
        ctx.log("card_top", Map.of("card", card.id(), "effect", effect));
        return true;
    }
}
