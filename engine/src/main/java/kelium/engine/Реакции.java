package kelium.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.Ctx;

/**
 * РЕАКЦИИ — верхи карт заданий, которые играются В ЧУЖОЙ ХОД.
 *
 * <p>Решение дизайнера 04.09.2026. Прежде верх карты задания был всегда «сделай
 * что-нибудь в свой ход даром», и выбор «выполнить или сжечь» стоял целиком
 * внутри собственного хода. Реакция ставит его иначе: карта нужна ИМЕННО СЕЙЧАС,
 * когда бьют тебя, — и потратив её, ты уже не выполнишь задание.
 *
 * <p>ГДЕ ОКНА. Их открывает бой ({@link CombatResolver}), потому что только он
 * знает, кто по кому бьёт и чем это кончилось. Карта объявляет ЛИШЬ ВИД
 * реакции; само действие исполняет тот, у кого на руках подробности момента.
 * Это единственное место, где верх карты играет не сама карта, и причина
 * названа прямо: у карты нет доступа к жертве, убийце и очереди атак.
 *
 * <p>ЦЕПОЧЕК НЕТ. Предсмертный хрип бьёт только по жетону-убийце; если хрип
 * добивает убийцу, отвечать тому уже некому — его жертва лежит на месте
 * уничтоженных жетонов. Поэтому окно внутри окна не открывается: реакция,
 * сыгранная в реакции, запрещена флагом {@link #внутриОкна}.
 */
public final class Реакции {

    private Реакции() {
    }

    /** Виды реакций: код — то, что написано в {@code top.params.kind}. */
    public enum Вид {
        /** Твоё ВОЙСКО уничтожают — оно наносит 1 урон убийце. */
        ОТВЕТНЫЙ_ОГОНЬ("return_fire"),
        /** Твоё ЗДАНИЕ уничтожают — оно наносит 1 урон убийце. */
        ЗАГРАДИТЕЛЬНЫЙ_ОГОНЬ("barrage"),
        /** Уведи один свой жетон из атакуемого гекса на один гекс. */
        ОТХОД("withdraw"),
        /** Атака переходит на любой другой жетон в том же гексе. */
        РИКОШЕТ("ricochet"),
        /** Твой уничтоженный жетон уходит в ТВОЙ запас, а не к атакующему. */
        ЭВАКУАЦИЯ_ТРОФЕЕВ("evacuate_trophy"),
        /** Разыграй свой Бой ПЕРЕД чужим. */
        БОЙ_ПЕРЕД_БОЕМ("battle_first");

        public final String код;

        Вид(String код) {
            this.код = код;
        }

        public static Вид поКоду(String код) {
            for (Вид в : values()) {
                if (в.код.equals(код)) {
                    return в;
                }
            }
            return null;
        }
    }

    /** Идентификатор эффекта верха-реакции в записи каталога. */
    public static final String ЭФФЕКТ = "reaction";

    /**
     * ОКНО ЗАКРЫТО, ПОКА ИГРАЕТСЯ РЕАКЦИЯ.
     *
     * <p>Флаг НА ПОТОК, а не на приложение: обучение гоняет тысячи партий
     * параллельно, и общее статическое поле связало бы чужие партии между собой
     * — реакция в одной закрывала бы окно в другой. Партия целиком играется в
     * одном потоке, поэтому поток и есть её граница.
     */
    private static final ThreadLocal<Boolean> ОКНО = ThreadLocal.withInitial(() -> false);

    /** Вид реакции, которую даёт верх этой карты; {@code null} — верх обычный. */
    public static Вид видКарты(GameState s, String cid) {
        Map<String, Object> card;
        try {
            card = Ctx.cards(s, "objectives").byId(cid);
        } catch (RuntimeException e) {
            return null;
        }
        if (!(card.get("top") instanceof Map<?, ?> t)) {
            return null;
        }
        if (!ЭФФЕКТ.equals(String.valueOf(t.get("effect")))) {
            return null;
        }
        return t.get("params") instanceof Map<?, ?> p
            ? Вид.поКоду(String.valueOf(p.get("kind"))) : null;
    }

    /** Есть ли у игрока на руках карта с такой реакцией. */
    public static boolean естьЧем(GameState s, int seat, Вид вид) {
        return !карты(s, seat, вид).isEmpty();
    }

    private static List<String> карты(GameState s, int seat, Вид вид) {
        List<String> out = new ArrayList<>();
        for (String cid : s.player(seat).objectiveHand) {
            if (видКарты(s, cid) == вид) {
                out.add(cid);
            }
        }
        return out;
    }

    /**
     * ПРЕДЛОЖИТЬ СЫГРАТЬ РЕАКЦИЮ. Игрок либо сжигает одну свою карту, либо
     * отказывается; карта уходит в сброс, как при обычном сжигании верха.
     *
     * @param повод что происходит — уходит в подсказку игроку и в событие
     * @return id сожжённой карты либо {@code null}, если реакции не было
     */
    public static String предложить(GameState s, int seat, Вид вид, Agent agent,
                                    String повод, Map<String, Object> подробности,
                                    Consumer<Map<String, Object>> emit) {
        if (agent == null || ОКНО.get()) {
            return null;
        }
        List<String> есть = карты(s, seat, вид);
        if (есть.isEmpty()) {
            return null;
        }
        List<Choice> opts = new ArrayList<>();
        for (String cid : есть) {
            opts.add(new Choice("reaction_burn", cid, "сжечь «" + имя(s, cid) + "»: " + повод));
        }
        opts.add(new Choice("pass", null, "не отвечать"));
        Map<String, Object> ctx = new LinkedHashMap<>(подробности == null
            ? Map.of() : подробности);
        ctx.put("kind", "reaction");
        ctx.put("reaction", вид.код);
        Choice ch;
        ОКНО.set(true);
        try {
            ch = agent.choose(s, opts, ctx);
        } finally {
            ОКНО.set(false);
        }
        if (ch == null || ch.payload() == null) {
            return null;
        }
        String cid = String.valueOf(ch.payload());
        PlayerState p = s.player(seat);
        p.objectiveHand.remove(cid);
        var колода = s.decks.get("objectives");
        if (колода != null) {
            колода.discard(cid);
        }
        if (emit != null) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("type", "objective_burn");
            ev.put("seat", seat);
            ev.put("card", cid);
            ev.put("round", s.round);
            ev.put("effect", ЭФФЕКТ);
            ev.put("label", вид.name());
            ev.put("reaction", вид.код);
            emit.accept(ev);
        }
        return cid;
    }

    private static String имя(GameState s, String cid) {
        try {
            return String.valueOf(Ctx.cards(s, "objectives").byId(cid).get("name"));
        } catch (RuntimeException e) {
            return cid;
        }
    }

    /** Играется ли сейчас реакция (окно внутри окна запрещено). */
    public static boolean внутриОкна() {
        return ОКНО.get();
    }
}
