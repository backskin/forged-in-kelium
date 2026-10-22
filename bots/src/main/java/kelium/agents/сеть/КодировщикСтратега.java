package kelium.agents.сеть;

import java.util.List;
import java.util.Map;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.engine.ability.Abilities;
import kelium.engine.ability.Ability;
import kelium.engine.ability.Hint;

/**
 * СТОЛ ГЛАЗАМИ СТРАТЕГА (30.09.2026) — вход сети оценки позиции.
 *
 * <p>Основа — {@link Кодировщик}: игроки, здания, войска, ресурсы, треки,
 * задания на руке (через саму карту: выполнено ли, близость, цена награды,
 * каким действием закрывается). Сверху — то, чего там не было.
 *
 * <p><b>НЕЗАВИСИМОСТЬ ОТ КОЛОДЫ</b> (заказ дизайнера 30.09.2026: «чтобы боты
 * были так же эффективны, если мы поменяем карты»). Ни одного номера карты:
 * карта видна через своё САМООПИСАНИЕ. Установленный арсенал каждого игрока и
 * арсенал на своей руке — сумма силы по узким местам ({@link Hint}: энергия,
 * боеприпасы, монеты, келемий, войска, досягаемость, трофеи, действия, очки,
 * оборона). Новая колода, описавшая себя так же, читается сразу; сеть не надо
 * учить с нуля, достаточно дообучить.
 */
public final class КодировщикСтратега {

    private КодировщикСтратега() {
    }

    private static final Hint.Bottleneck[] МЕСТА = Hint.Bottleneck.values();

    /** Чисел сверх основы на одного игрока: установленный арсенал по узким местам. */
    private static final int НА_ИГРОКА = МЕСТА.length + 1;
    /** Общих чисел сверх основы. */
    private static final int ОБЩИХ = 3 + МЕСТА.length;

    /** Длина вектора для {@code мест} игроков. */
    public static int длина(int мест) {
        return Кодировщик.длина(мест) + НА_ИГРОКА * мест + ОБЩИХ;
    }

    /** Закодировать стол глазами места {@code seat}. */
    public static float[] закодировать(GameState s, int seat) {
        int мест = s.numPlayers();
        float[] основа = Кодировщик.закодировать(s, seat);
        float[] v = new float[длина(мест)];
        System.arraycopy(основа, 0, v, 0, основа.length);
        int i = основа.length;
        for (int k = 0; k < мест; k++) {
            PlayerState p = s.player((seat + k) % мест);
            float[] силы = new float[МЕСТА.length];
            for (String cid : p.allInstalledArsenal()) {
                добавить(s, "arsenal", cid, силы);
            }
            for (String cid : p.superArsenalCards) {
                добавить(s, "super_arsenal", cid, силы);
            }
            for (float x : силы) {
                v[i++] = x / 3f;
            }
            v[i++] = вершин(s, p) / 3f;
        }
        // своя рука арсенала: что ещё можно поставить
        float[] рука = new float[МЕСТА.length];
        for (String cid : s.player(seat).arsenalHand) {
            добавить(s, "arsenal", cid, рука);
        }
        for (float x : рука) {
            v[i++] = x / 3f;
        }
        int осталось;
        try {
            осталось = new kelium.engine.cards.EngineCardContext(s, seat).roundsLeft();
        } catch (RuntimeException e) {
            осталось = 5;
        }
        v[i++] = Math.max(0, Math.min(12, осталось)) / 10f;
        v[i++] = s.finished ? 1 : 0;
        v[i++] = мест / 4f;
        return v;
    }

    /** Сколько вершин треков Науки занято игроком (три — мгновенная победа). */
    private static int вершин(GameState s, PlayerState p) {
        int n = 0;
        for (String трек : s.tech.tracks) {
            if (p.techSteps.getOrDefault(трек, 0) >= 4) {
                n++;
            }
        }
        return n;
    }

    /** Прибавить самоописание карты к силам по узким местам. */
    @SuppressWarnings("unchecked")
    private static void добавить(GameState s, String набор, String cid, float[] силы) {
        Map<String, Object> card;
        try {
            card = kelium.dataio.Ctx.cards(s, набор).find(cid);
        } catch (RuntimeException e) {
            return;
        }
        if (card == null) {
            return;
        }
        Object id = card.get("passive");
        if (id == null && card.get("bottom") instanceof Map<?, ?> bm) {
            id = ((Map<String, Object>) bm).get("passive");
            // СРАБАТЫВАНИЕ ДАННЫМИ (Карты 2.0): кода способности нет — узкое
            // место берётся прямо из эффекта низа, сила — из предела за ход.
            if (id == null && bm.get("эффект") instanceof Map<?, ?> эф) {
                int предел = bm.get("предел") instanceof Number n ? n.intValue() : 1;
                Map<?, ?> п = эф.get("params") instanceof Map<?, ?> m ? m : Map.of();
                switch (String.valueOf(эф.get("effect"))) {
                    case "спец", "free_action" -> силы[Hint.Bottleneck.ACTIONS.ordinal()] += предел;
                    case "gain" -> {
                        силы[Hint.Bottleneck.COINS.ordinal()] += предел * число(п, "coin");
                        силы[Hint.Bottleneck.AMMO.ordinal()] += предел * число(п, "ammo");
                        силы[Hint.Bottleneck.KELIUM.ordinal()] += предел * число(п, "kelium");
                        силы[Hint.Bottleneck.TROPHY.ordinal()] += предел * число(п, "trophy");
                        силы[Hint.Bottleneck.ACTIONS.ordinal()] += предел * число(п, "objective_cards");
                    }
                    case "heal_one" -> силы[Hint.Bottleneck.DEFENCE.ordinal()] += предел;
                    default -> { }
                }
                return;
            }
        }
        Ability a = Abilities.byId(id == null ? null : id.toString());
        if (a == null) {
            return;
        }
        Hint h;
        try {
            h = a.hint();
        } catch (RuntimeException e) {
            return;
        }
        if (h == null || h.relieves() == null) {
            return;
        }
        силы[h.relieves().ordinal()] += (float) Math.max(0, Math.min(5, h.strength()));
    }

    private static int число(Map<?, ?> m, String ключ) {
        return m.get(ключ) instanceof Number n ? n.intValue() : 0;
    }

    /** Все узкие места по порядку — для подписей в отчётах. */
    public static List<Hint.Bottleneck> места() {
        return List.of(МЕСТА);
    }
}
