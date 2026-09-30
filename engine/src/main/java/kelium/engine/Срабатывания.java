package kelium.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kelium.core.GameState;
import kelium.core.PlayerState;

/**
 * СРАБАТЫВАНИЯ УСТАНОВЛЕННЫХ КАРТ — «каждый раз, когда …» (Карты 2.0,
 * 30.09.2026).
 *
 * <p>Прежде «каждый раз, когда» у карт жило пометками: движок в одном
 * названном месте спрашивал «есть ли у игрока такая карта». Каждая новая карта
 * требовала правки движка, и карты не могли собираться программой. Здесь
 * срабатывание — ДАННЫЕ низа карты:
 *
 * <pre>
 *   bottom:
 *     когда: {событие: ветка, ветка: mining}
 *     эффект: {effect: gain, params: {coin: 1}}
 *     предел: 1            # не больше раз за ход (печатается на карте)
 * </pre>
 *
 * <p>События берутся из общего потока событий движка ({@code GameEngine.emit}):
 * карта ничего не встраивает в правила, она слушает то, что движок и так
 * публикует для записи партии.
 *
 * <p>ЦЕПОЧКИ. Эффект срабатывания может сам вызвать событие (ветка с карты —
 * событие «ветка»), а оно — другое срабатывание. Это и есть комбинация за ход.
 * Бесконечную цепь режут два предела: печатный «не больше N раз за ход» у
 * каждой карты и глубина вложенности {@link #ГЛУБИНА}.
 */
public final class Срабатывания {

    private Срабатывания() {
    }

    /** Наибольшая вложенность «срабатывание вызвало срабатывание». */
    private static final int ГЛУБИНА = 4;

    private static final ThreadLocal<int[]> глубина = ThreadLocal.withInitial(() -> new int[1]);

    /** Эффекты, которые можно исполнить посреди чужого действия (бой, вскрытие). */
    private static final java.util.Set<String> БЕЗОПАСНЫЕ = java.util.Set.of(
        "gain", "спец", "heal_one", "heal_all_own");

    /** События, после которых действие уже закончено и можно играть ветку. */
    private static final java.util.Set<String> ПОСЛЕ_ДЕЙСТВИЯ = java.util.Set.of(
        "action", "objective", "objective_burn", "arsenal", "container");

    /**
     * Раздать событие картам всех игроков. Зовётся из {@code GameEngine.emit}
     * для каждого события партии.
     */
    public static void раздать(GameState s, Map<String, Object> событие) {
        if (s.journal == null || событие == null) {
            return;
        }
        int[] г = глубина.get();
        if (г[0] >= ГЛУБИНА) {
            return;
        }
        г[0]++;
        try {
            for (PlayerState p : s.players) {
                for (String cid : List.copyOf(p.allInstalledArsenal())) {
                    Map<String, Object> низ = низ(s, cid);
                    if (низ == null || !(низ.get("когда") instanceof Map<?, ?> когда)) {
                        continue;
                    }
                    if (!подходит(s, p.seat, когда, событие)) {
                        continue;
                    }
                    int предел = низ.get("предел") instanceof Number n ? n.intValue() : 1;
                    if (s.journal.срабатываний(p.seat, cid) >= предел) {
                        continue;
                    }
                    if (!(низ.get("эффект") instanceof Map<?, ?> эф)) {
                        continue;
                    }
                    String имя = String.valueOf(эф.get("effect"));
                    if (!ПОСЛЕ_ДЕЙСТВИЯ.contains(String.valueOf(событие.get("type")))
                            && !БЕЗОПАСНЫЕ.contains(имя)) {
                        continue;   // ветку посреди боя не играют — карта так не собирается
                    }
                    s.journal.отметитьСрабатывание(p.seat, cid);
                    Map<String, Object> итог = исполнить(s, p.seat, имя, параметры(эф));
                    if (s.публикатор != null) {
                        Map<String, Object> ev = new HashMap<>();
                        ev.put("type", "card_trigger");
                        ev.put("seat", p.seat);
                        ev.put("card", cid);
                        ev.put("on", событие.get("type"));
                        ev.put("effect", имя);
                        ev.put("got", итог);
                        s.публикатор.accept(ev);
                    }
                }
            }
        } finally {
            г[0]--;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> параметры(Map<?, ?> эф) {
        return эф.get("params") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> низ(GameState s, String cid) {
        Map<String, Object> card;
        try {
            card = kelium.dataio.Ctx.cards(s, "arsenal").find(cid);
        } catch (RuntimeException e) {
            return null;
        }
        return card != null && card.get("bottom") instanceof Map<?, ?> b
            ? (Map<String, Object>) b : null;
    }

    /** Исполнить эффект срабатывания. «спец» — ещё спец-действие в этот ход. */
    static Map<String, Object> исполнить(GameState s, int seat, String имя,
                                         Map<String, Object> п) {
        if ("спец".equals(имя)) {
            int n = п.get("n") instanceof Number k ? k.intValue() : 1;
            s.journal.of(seat).specBonus += n;
            return Map.of("spec_actions", n);
        }
        try {
            return Effects.apply(имя, s, seat, п);
        } catch (RuntimeException e) {
            return Map.of("failed", имя);
        }
    }

    /** Ветка события словами кода: «build» развилки Добычи — это build_miner. */
    public static String ветка(Map<String, Object> событие) {
        String действие = String.valueOf(событие.get("action"));
        if ("build".equals(действие) && событие.get("fork") != null) {
            String вид = Actions.FORK_BUILD.get(String.valueOf(событие.get("fork")));
            return вид == null ? действие : "build_" + вид;
        }
        return действие;
    }

    /** Развилка, которой принадлежит ветка. */
    public static String развилка(String ветка) {
        if (ветка == null) {
            return null;
        }
        if (ветка.startsWith("build_")) {
            String вид = ветка.substring("build_".length());
            for (var e : Actions.FORK_BUILD.entrySet()) {
                if (e.getValue().equals(вид)) {
                    return e.getKey();
                }
            }
        }
        for (var e : Actions.FORKS.entrySet()) {
            if (e.getValue().contains(ветка)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Подходит ли событие под «когда» карты игрока {@code владелец}.
     * Все события — СВОИ, кроме «потерял» (твой жетон уничтожен в чужой ход).
     */
    static boolean подходит(GameState s, int владелец, Map<?, ?> когда,
                            Map<String, Object> e) {
        String тип = String.valueOf(e.get("type"));
        Object место = e.get("seat");
        boolean своё = место instanceof Integer m && m == владелец;
        String что = String.valueOf(когда.get("событие"));
        return switch (что) {
            case "ветка" -> своё && "action".equals(тип) && Boolean.TRUE.equals(e.get("ok"))
                && String.valueOf(когда.get("ветка")).equals(ветка(e));
            case "развилка" -> своё && "action".equals(тип) && Boolean.TRUE.equals(e.get("ok"))
                && String.valueOf(когда.get("развилка")).equals(развилка(ветка(e)));
            case "задание" -> своё && "objective".equals(тип);
            case "сжёг" -> своё && ("objective_burn".equals(тип)
                || "arsenal".equals(тип) && "burn".equals(e.get("mode")));
            case "установил" -> своё && "arsenal".equals(тип) && "install".equals(e.get("mode"));
            case "контейнер" -> своё && "container".equals(тип);
            case "уничтожил" -> своё && "combat_hit".equals(тип)
                && Boolean.TRUE.equals(e.get("destroyed"))
                && жертва(когда, e) && кем(когда, e);
            case "потерял" -> "combat_hit".equals(тип) && Boolean.TRUE.equals(e.get("destroyed"))
                && e.get("victim_owner") instanceof Integer vo && vo == владелец;
            case "совпадение" -> своё && "turn_orders".equals(тип)
                && Boolean.TRUE.equals(e.get("coincided"));
            case "низ" -> своё && "turn_orders".equals(тип)
                && Boolean.TRUE.equals(e.get("bottom_open"));
            default -> false;
        };
    }

    /** Фильтр жертвы: building / unit / род войска / род здания. */
    private static boolean жертва(Map<?, ?> когда, Map<String, Object> e) {
        Object нужно = когда.get("жертва");
        if (нужно == null) {
            return true;
        }
        String v = String.valueOf(e.get("victim"));
        String n = String.valueOf(нужно);
        boolean здание = !v.equals("infantry") && !v.equals("vehicle")
            && !v.equals("aircraft") && !v.equals("tower");
        return switch (n) {
            case "building" -> здание;
            case "unit" -> !здание;
            default -> v.startsWith(n);
        };
    }

    /** Фильтр атакующего: род войска, которым уничтожено. */
    private static boolean кем(Map<?, ?> когда, Map<String, Object> e) {
        Object нужно = когда.get("кем");
        if (нужно == null) {
            return true;
        }
        return String.valueOf(e.get("attacker")).startsWith(String.valueOf(нужно) + ".");
    }
}
