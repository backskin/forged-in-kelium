package kelium.engine.ability;

import java.util.List;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;

/**
 * НАБОР ЛЕГАЛЬНЫХ ОПЕРАЦИЙ, из которых способности собирают свои эффекты —
 * четвёртая опора системы (замечание дизайнера 13.08.2026: карты должны уметь
 * «всякие самые разные хаки», вплоть до переноса карт между зонами).
 *
 * <p>Смысл в том, чтобы способность НЕ ЛАЗИЛА руками в поля состояния. Она
 * складывает эффект из перечисленных здесь операций, каждая из которых знает свои
 * правила (пределы склада, возврат жетона в запас, освобождение ячеек гекса) и
 * сообщает движку о произошедшем. Тогда новая карта — это композиция готовых
 * операций, а не новая дыра в инвариантах.
 *
 * <p>Список открыт: под новую карту сюда добавляется ОДНА операция, если её
 * действительно нет. Правило то же, что и с точками правил, — расширяем осознанно.
 */
public final class AbilityEffects {

    private AbilityEffects() {
    }

    // ---- ресурсы ----

    /** Выдать ресурс в пределах вместимости склада; вернуть фактически выданное. */
    public static int gain(GameState s, PlayerState p, Resource what, int amount) {
        return switch (what) {
            // С ПАРТИЕЙ В РУКАХ: без неё предел считается по одному планшету и
            // не видит ни ячеек от способностей арсенала, ни того, что здание
            // лежит трофеем у другого игрока и своих ячеек пока не накрывает.
            case KELIUM -> kelium.engine.Storage.addKeliumCapped(s, p, amount);
            case AMMO -> kelium.engine.Storage.addAmmoCapped(s, p, amount);
            default -> {
                p.resources.add(what, amount);
                yield amount;
            }
        };
    }

    /** Забрать ресурс у ЧУЖОГО игрока себе (в пределах своего склада). */
    public static int steal(GameState s, PlayerState from, PlayerState to,
                           Resource what, int amount) {
        int have = Math.min(amount, from.resources.get(what));
        if (have <= 0) {
            return 0;
        }
        from.resources.pay(what, have);
        return gain(s, to, what, have);
    }

    // ---- жетоны ----

    /** Вернуть жетон владельцу в запас: снять с поля, освободить ячейки, снять урон. */
    public static boolean toReserve(GameState s, Token t) {
        if (t == null || t.hexId() == null) {
            return false;
        }
        kelium.core.Hex h = s.field.get(t.hexId());
        if (h != null) {
            h.freeSidesByToken(t.uid());
        }
        t.setHexId(null);
        t.resetDamage();
        return true;
    }

    /** Снять один кубик урона с жетона (движковый метод жетона). */
    public static boolean healOne(Token t) {
        if (t == null) {
            return false;
        }
        t.healOneDamage();
        return true;
    }

    // ---- карты ----

    /** Перенести карту между зонами игрока: рука ↔ установленные ↔ сброс. */
    public static boolean moveCard(GameState s, PlayerState p, String deck, String cardId,
                                   Zone from, Zone to) {
        List<String> src = zone(p, from);
        if (src == null || !src.remove(cardId)) {
            return false;
        }
        if (to == Zone.DISCARD) {
            s.decks.get(deck).discard(cardId);
            return true;
        }
        List<String> dst = zone(p, to);
        if (dst == null) {
            return false;
        }
        dst.add(cardId);
        return true;
    }

    /** Забрать карту из сброса колоды в руку (например разыгранный приказ). */
    public static boolean fromDiscard(GameState s, PlayerState p, String deck, Zone to) {
        var d = s.decks.get(deck);
        if (d == null || d.discardPile.isEmpty()) {
            return false;
        }
        String cid = d.discardPile.remove(d.discardPile.size() - 1);
        List<String> dst = zone(p, to);
        if (dst == null) {
            return false;
        }
        dst.add(cid);
        return true;
    }

    /** Зоны карт игрока, между которыми способности имеют право переносить. */
    public enum Zone { HAND, INSTALLED, ORDER_HAND, ORDER_PLAYED, OBJECTIVES, DISCARD }

    private static List<String> zone(PlayerState p, Zone z) {
        return switch (z) {
            case HAND -> p.arsenalHand;
            case INSTALLED -> p.arsenalInstalled;
            case ORDER_HAND -> p.orderHand;
            case ORDER_PLAYED -> p.orderPlayed;
            case OBJECTIVES -> p.objectiveHand;
            case DISCARD -> null;      // сброс живёт в колоде, а не у игрока
        };
    }

    // ---- поле ----

    /** Уничтожить чужой жетон: он уходит в трофейное место разрушителя. */
    public static boolean destroy(GameState s, int bySeat, Token victim) {
        if (victim == null || victim.hexId() == null) {
            return false;
        }
        kelium.core.Hex h = s.field.get(victim.hexId());
        if (h != null) {
            h.freeSidesByToken(victim.uid());
        }
        victim.setHexId(null);
        victim.setCapturedBy(bySeat);
        s.player(bySeat).trophySpace.add(victim);
        return true;
    }

    /** Жетоны игрока на поле — удобная выборка для условий способностей. */
    public static List<Token> onField(PlayerState p) {
        List<Token> out = new java.util.ArrayList<>();
        for (UnitToken u : p.unitsOnField()) {
            out.add(u);
        }
        for (BuildingToken b : p.buildingsOnField()) {
            out.add(b);
        }
        return out;
    }
}
