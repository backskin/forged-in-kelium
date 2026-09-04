package kelium.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Колода карт: стопка добора и стопка сброса, идентификаторы карт — строки.
 *
 * <p>Незыблемое ядро: колода умеет тянуть карту, сбрасывать и перетасовывать
 * сброс. Детерминизм: вся тасовка использует переданный извне {@link Random},
 * поэтому одно зерно (seed) воспроизводит всю партию целиком. Полная бит-в-бит
 * совместимость с Python {@code random} не требуется — важна детерминированность
 * самой Java-версии.
 */
public final class Deck {

    public final String name;
    public List<String> drawPile;
    public List<String> discardPile = new ArrayList<>();

    public Deck(String name, List<String> drawPile) {
        this.name = name;
        this.drawPile = drawPile;
    }

    /** Точная копия колоды (для копии состояния при просчёте вперёд). */
    public Deck copy() {
        Deck d = new Deck(name, new ArrayList<>(drawPile));
        d.discardPile = new ArrayList<>(discardPile);
        return d;
    }

    /** Создать колоду из списка id карт, сразу перетасовав стопку добора. */
    public static Deck fromIds(String name, List<String> ids, Random rng) {
        List<String> pile = new ArrayList<>(ids);
        Collections.shuffle(pile, rng);
        return new Deck(name, pile);
    }

    /** Вытянуть одну карту. При пустом доборе перетасовать сброс; null если карт нет. */
    public String draw(Random rng) {
        if (drawPile.isEmpty()) {
            if (discardPile.isEmpty()) {
                return null;
            }
            drawPile = discardPile;
            discardPile = new ArrayList<>();
            Collections.shuffle(drawPile, rng);
        }
        if (drawPile.isEmpty()) {
            return null;
        }
        return drawPile.remove(drawPile.size() - 1);
    }

    /** Вытянуть до n карт; вернуть меньше, если колода и сброс исчерпаны. */
    public List<String> drawN(int n, Random rng) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String c = draw(rng);
            if (c == null) {
                break;
            }
            out.add(c);
        }
        return out;
    }

    /** Положить карту в стопку сброса. */
    public void discard(String cardId) {
        discardPile.add(cardId);
    }

    /** Убрать карту из стопки добора (например, стартовые карты уходят игрокам). */
    /**
     * ВЗЯТЬ КАРТУ ИЗ СБРОСА (эффект «верни на рынок сброшенную карту сделок»).
     * Возвращает null, если сброс пуст. Карта уходит из сброса насовсем — она
     * снова в игре, и второй раз ту же вернуть нельзя.
     */
    public String takeFromDiscard(Random rng) {
        if (discardPile.isEmpty()) {
            return null;
        }
        return discardPile.remove(discardPile.size() - 1);
    }

    public void removeCard(String cardId) {
        drawPile.remove(cardId);
    }

    /** Число карт в стопке добора (без учёта сброса). */
    public int size() {
        return drawPile.size();
    }

    /**
     * Применить отметки отбраковки по числу игроков, вернуть id оставленных карт.
     *
     * <p>Соглашение: у записи может быть {@code cull: "[4]"} (убрать при 4 игроках)
     * или {@code cull: "[3+]"} (убрать при 3 и 4 игроках). Отметки нет — карта
     * остаётся всегда.
     */
    public static List<String> cullForPlayers(List<Map<String, Object>> entries, int numPlayers) {
        List<String> kept = new ArrayList<>();
        for (Map<String, Object> e : entries) {
            Object mark = e.get("cull");
            if ("[4]".equals(mark) && numPlayers == 4) {
                continue;
            }
            if ("[3+]".equals(mark) && numPlayers >= 3) {
                continue;
            }
            // КОПИИ (заказ дизайнера 28.08.2026): востребованная карта арсенала
            // кладётся в колоду НЕСКОЛЬКО раз, чтобы преимущество не было
            // монополией одного игрока. Поле copies в данных карты; нет поля —
            // одна копия, как раньше.
            int copies = e.get("copies") instanceof Number n ? n.intValue() : 1;
            for (int i = 0; i < copies; i++) {
                kept.add((String) e.get("id"));
            }
        }
        return kept;
    }
}
