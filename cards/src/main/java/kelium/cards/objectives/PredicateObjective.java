package kelium.cards.objectives;

import java.util.Map;

import kelium.engine.Predicates;
import kelium.engine.cards.CardContext;

/**
 * ЗАДАНИЕ, ЧЬЁ УСЛОВИЕ ЦЕЛИКОМ ОПИСАНО В ДАННЫХ — предикат и его параметры.
 *
 * <p>ЗАЧЕМ ЭТОТ КЛАСС. Карт с собственным поведением в каталоге меньшинство:
 * большинству нужно ровно то, что уже умеет предикат, и отдельный класс на такую
 * карту — это шесть строк, которые ничего не добавляют, но обязаны совпадать с
 * данными. Расходятся они на первой же правке каталога, причём молча: класс
 * продолжает считать по старому порогу, а карта печатается по новому.
 *
 * <p>Здесь один класс читает id предиката и параметры прямо из карты — значит
 * разойтись с данными он не может по устройству. Карта, поведение которой не
 * сводится к предикату, по-прежнему получает свой класс (см. {@link
 * GroupNewWar}) и помечается в данных как {@code checked_by: card}.
 *
 * <p>Прогресс здесь двоичный, и намеренно: предикат отвечает «да/нет», а
 * выдумывать долю пути по чужому ответу — врать боту. Счётный прогресс и
 * человеческая подсказка «что сделать» живут в {@link
 * kelium.engine.ObjectiveHints}: там движок разбирает то же требование и умеет
 * сказать, какими действиями его закрыть.
 */
public final class PredicateObjective extends Objective {

    private final String подсказка;

    /**
     * @param id       номер карты в данных
     * @param подсказка что делать — человеческой строкой, для игрока и для бота
     */
    public PredicateObjective(String id, String подсказка) {
        super(id);
        this.подсказка = подсказка;
    }

    @SuppressWarnings("unchecked")
    private boolean check(CardContext ctx, String branch) {
        if (!(data().get(branch) instanceof Map<?, ?> req)) {
            return false;
        }
        Object pid = req.get("predicate");
        if (pid == null || !Predicates.isRegistered(pid.toString())) {
            return false;
        }
        Map<String, Object> params = req.get("params") instanceof Map<?, ?> p
            ? (Map<String, Object>) p : Map.of();
        try {
            return Predicates.check(pid.toString(), ctx.state(), ctx.seat(),
                ctx.state().journal, params);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean satisfied(CardContext ctx) {
        // У КАРТЫ-ЖЕРТВЫ предикат требования — sacrifice_paid, и он истинен
        // всегда: плата вносится в момент розыгрыша, а не проверяется заранее.
        // Настоящее условие такой карты — «есть чем заплатить», и спрашивать об
        // этом надо движок, иначе карта считается выполненной на пустом столе.
        if (data().containsKey("sacrifice")) {
            return kelium.engine.Objectives.canPaySacrifice(ctx.state(), ctx.seat(), data());
        }
        return check(ctx, "requirement");
    }

    @Override
    public boolean satisfiedEnhanced(CardContext ctx) {
        return satisfied(ctx) && check(ctx, "enhanced");
    }

    @Override
    public String needed(CardContext ctx) {
        return satisfied(ctx) ? "готово" : подсказка;
    }
}
