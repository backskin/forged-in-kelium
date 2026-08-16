package kelium.cards.objectives;

import java.util.Map;

import kelium.cards.BaseCard;
import kelium.cards.CardTop;
import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.TurnJournal;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.cards.CardContext;
import kelium.engine.cards.ObjectiveCard;

/**
 * ЗАДАНИЕ — написано заново, с нуля (заказ дизайнера 15.08.2026).
 *
 * <p>ПОЧЕМУ С НУЛЯ. Прежняя реализация разносила одну карту по трём чужим друг
 * другу местам: условие — строковый ключ в реестре предикатов, награда — разбор
 * записи в движке, верхний эффект — третий реестр. Карты как целого не
 * существовало, проверить её было нечем, а бот видел награду и не видел пути к
 * ней. Замер: из 6.5 полученных карт выполняется 1.18, сжигается 8.25.
 *
 * <p>ЧТО ЗДЕСЬ УСТРОЕНО ИНАЧЕ.
 *
 * <ol>
 *   <li><b>Карта проверяет себя сама.</b> Никаких строковых ключей: условие —
 *       это код в классе карты, который смотрит на состояние партии и на журнал
 *       хода напрямую. Ошибиться именем предиката больше нельзя.</li>
 *   <li><b>Есть прогресс, а не только «да/нет».</b> Бот должен отличать задание,
 *       до которого один шаг, от невыполнимого. Раньше оба выглядели как «нет» —
 *       отсюда и привычка жечь карты вместо выполнения.</li>
 *   <li><b>Есть подсказка, ЧТО сделать.</b> {@link #needed} возвращает, чего
 *       не хватает, — это то, чего у бота не было вовсе: он не мог строить план
 *       под задание, потому что не знал, из чего оно состоит.</li>
 *   <li><b>Три вида требований разделены явно.</b> Событие за ход, состояние
 *       поля и жертва — проверяются по-разному, и путать их нельзя: событие
 *       живёт в журнале хода и исчезает в конце хода, состояние поля видно
 *       всегда.</li>
 * </ol>
 *
 * <p>ЧИСЛА ОСТАЮТСЯ В ДАННЫХ. Пороги и награды правит дизайнер, и пересборка
 * ради смены цифры недопустима. В коде — только поведение.
 */
public abstract class Objective extends BaseCard implements ObjectiveCard {

    /** Что за требование у карты — от этого зависит, где его искать. */
    public enum Kind {
        /** СОБЫТИЕ ЗА ХОД: сделал что-то в этот ход. Живёт в журнале хода. */
        СОБЫТИЕ,
        /** СОСТОЯНИЕ ПОЛЯ: на столе сейчас так-то. Видно всегда. */
        СОСТОЯНИЕ,
        /** ЖЕРТВА: заплати столько-то — тогда выполнено. */
        ЖЕРТВА
    }

    protected Objective(String id) {
        super(id);
    }

    /** Вид требования этой карты. */
    public Kind kind() {
        return switch (String.valueOf(data().getOrDefault("type", "state"))) {
            case "incident" -> Kind.СОБЫТИЕ;
            case "sacrifice" -> Kind.ЖЕРТВА;
            default -> Kind.СОСТОЯНИЕ;
        };
    }

    // ==================================================================
    //  ЧТО КАРТА СПРАШИВАЕТ У ПАРТИИ — общий словарь для всех заданий
    // ==================================================================

    /** Журнал ТЕКУЩЕГО ХОДА: всё, что игрок успел сделать до этой секунды. */
    protected static TurnJournal.TurnFacts ход(CardContext ctx) {
        return ctx.state().journal.of(ctx.seat());
    }

    /** Мой планшет и запасы. */
    protected static PlayerState я(CardContext ctx) {
        return ctx.me();
    }

    /** Мои живые войска на поле. */
    protected static java.util.List<UnitToken> моиВойска(CardContext ctx) {
        return я(ctx).unitsOnField();
    }

    /** Мои здания на поле. */
    protected static java.util.List<BuildingToken> моиЗдания(CardContext ctx) {
        return я(ctx).buildingsOnField();
    }

    /** Сколько моих войск данного рода на поле. */
    protected static int войскРода(CardContext ctx, UnitType type) {
        int n = 0;
        for (UnitToken u : моиВойска(ctx)) {
            if (u.type == type) {
                n++;
            }
        }
        return n;
    }

    /** Сколько РАЗНЫХ родов войск у меня на поле. */
    protected static int родовНаПоле(CardContext ctx) {
        java.util.Set<UnitType> kinds = java.util.EnumSet.noneOf(UnitType.class);
        for (UnitToken u : моиВойска(ctx)) {
            kinds.add(u.type);
        }
        return kinds.size();
    }

    /** Сумма моих шагов по всем трекам науки. */
    protected static int шаговНауки(CardContext ctx) {
        int n = 0;
        for (String track : ctx.state().tech.tracks) {
            n += я(ctx).techSteps.getOrDefault(track, 0);
        }
        return n;
    }

    /** Стоит ли на этом гексе хоть один ЧУЖОЙ жетон. */
    protected static boolean тамВраг(CardContext ctx, String hexId) {
        GameState s = ctx.state();
        for (PlayerState other : s.players) {
            if (other.seat == ctx.seat()) {
                continue;
            }
            for (UnitToken u : other.unitsOnField()) {
                if (hexId.equals(u.hexId)) {
                    return true;
                }
            }
            for (BuildingToken b : other.buildingsOnField()) {
                if (hexId.equals(b.hexId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Граничит ли гекс с чужим жетоном. */
    protected static boolean граничитСВрагом(CardContext ctx, String hexId) {
        for (String nb : ctx.state().field.neighbors(hexId)) {
            if (тамВраг(ctx, nb)) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================
    //  ЧТО КАРТА ОТВЕЧАЕТ
    // ==================================================================

    /**
     * ЧЕГО НЕ ХВАТАЕТ ДО ВЫПОЛНЕНИЯ — человеческим языком и в числах.
     *
     * <p>Это главное, чего не было у бота: он видел награду, но не видел, из чего
     * задание состоит, и потому не мог поставить его себе целью. Строка идёт и в
     * подсказку игроку в проигрывателе.
     */
    public abstract String needed(CardContext ctx);

    /** Порог из параметров требования (обычная ветка). */
    protected int порог(String key, int fallback) {
        return param(key, fallback);
    }

    /** Порог из параметров усиленной ветки. */
    protected int порогУсил(String key, int fallback) {
        return paramEnhanced(key, fallback);
    }

    @Override
    public boolean burn(CardContext ctx) {
        return CardTop.burn(ctx, this);
    }

    /**
     * Награда. Числа берутся из данных, но КЛЮЧИ у заданий свои исторические:
     * базовая лежит в {@code base_reward}, усиленная — в {@code special_reward},
     * причём усиленная выдаётся ДОПОЛНИТЕЛЬНО к базовой, а не вместо неё.
     */
    @Override
    public void reward(CardContext ctx, boolean enhanced) {
        grant(ctx, data().get("base_reward"));
        if (enhanced) {
            grant(ctx, data().get("special_reward"));
        }
    }

    private void grant(CardContext ctx, Object node) {
        if (!(node instanceof Map<?, ?> r)) {
            return;
        }
        give(ctx, r, "coin", Resource.COIN);
        give(ctx, r, "kelium", Resource.KELIUM);
        give(ctx, r, "ammo", Resource.AMMO);
        give(ctx, r, "debris", Resource.DEBRIS);
        if (r.get("vp") instanceof Number vp && vp.intValue() != 0) {
            ctx.grantVp(vp.intValue(), "objective:" + id());
        }
        // Контейнеры, модули, карты заданий и жетоны хранилища выдаёт движок:
        // это не ресурсы, а объекты со своими правилами размещения.
        ctx.log("objective_reward", Map.of("card", id(), "reward", r));
    }

    private void give(CardContext ctx, Map<?, ?> r, String key, Resource res) {
        if (r.get(key) instanceof Number n && n.intValue() != 0) {
            ctx.gain(res, n.intValue());
        }
    }
}
