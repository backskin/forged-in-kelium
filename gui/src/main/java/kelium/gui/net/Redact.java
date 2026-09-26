package kelium.gui.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.report.ReplayRecord;

/**
 * ВЫРЕЗАНИЕ СКРЫТОГО — ЕДИНСТВЕННАЯ ТОЧКА, через которую стол уходит к
 * клиенту. Всё, что клиент видит, проходит здесь; второго пути быть не должно.
 *
 * <p>Кадры записи партии ({@link ReplayRecord}) пишутся для ВСЕВИДЯЩЕГО
 * журнала: в каждом снимке руки всех мест, колоды по порядку, мысли ботов.
 * Отсюда к месту {@code seat} уходит копия, в которой:
 * <ul>
 *   <li>руки заданий, приказов и арсенала ЧУЖИХ мест — рубашками «?» (число
 *       карт за столом видно всем, какие — нет);</li>
 *   <li>отложенный приказ, карта под «Мандатом совета», закрытые подложенные
 *       карты чужих — рубашкой;</li>
 *   <li>колоды — рубашками той же толщины; у сброса открыта только верхняя
 *       карта;</li>
 *   <li>мысли ботов — выброшены (в них бот проговаривает свою руку);</li>
 *   <li>строки ленты о закрытом («получил задание «…»», «отложил «…»») —
 *       переписаны без названия карты;</li>
 *   <li>сид партии — ноль: сид и свод дают порядок всех колод.</li>
 * </ul>
 * Правила, что открыто, те же, что у {@code PublicView}. Новое событие с
 * названием закрытой карты в тексте надо внести в {@link #PRIVATE_LOGS} —
 * иначе протечёт; сторожит {@code NetLoopbackTest}.
 */
public final class Redact {

    private Redact() {
    }

    /** Рубашка: так лежит карта, которую место не видит. */
    public static final String BACK = "?";

    /** Кадры, строка ленты которых называет закрытую карту. */
    static final Set<String> PRIVATE_LOGS = Set.of("objective_drawn", "blind_discard");

    /**
     * Кусок записи для места {@code seat}: кадры {@code frames} (подряд, как в
     * записи), разыгранные приказы и — если {@code header} — шапка партии
     * (поле, места, цвета, имена карт).
     */
    public static Map<String, Object> chunk(ReplayRecord head, List<ReplayRecord.Frame> frames,
                                            List<ReplayRecord.OrderPlay> plays, int seat,
                                            boolean header) {
        ReplayRecord tmp = new ReplayRecord();
        tmp.ruleset = head.ruleset;
        tmp.players = head.players;
        tmp.seed = 0L;
        if (header) {
            tmp.seatIds.addAll(head.seatIds);
            tmp.expansions.putAll(head.expansions);
            tmp.seatLabels.addAll(head.seatLabels);
            tmp.sides.addAll(head.sides);
            tmp.scenarioId = head.scenarioId;
            tmp.cuFacing.addAll(head.cuFacing);
            tmp.seatColors.addAll(head.seatColors);
            tmp.unitStock.putAll(head.unitStock);
            tmp.cardNames.putAll(head.cardNames);
            tmp.hexes.addAll(head.hexes);
        }
        tmp.spawnLeft = head.spawnLeft;
        tmp.spawnThreshold = head.spawnThreshold;
        tmp.orderPlays.addAll(plays);
        tmp.frames.addAll(frames);

        Map<String, Object> m = tmp.toMap();
        m.put("seed", 0);
        m.remove("scenarioFile");
        m.put("orderPlays", orderPlays(m.get("orderPlays"), seat));
        List<Object> fs = new ArrayList<>();
        if (m.get("frames") instanceof List<?> raw) {
            for (Object o : raw) {
                fs.add(frame(asMap(o), seat, head.seed));
            }
        }
        m.put("frames", fs);
        return m;
    }

    private static Map<String, Object> frame(Map<String, Object> src, int seat, long seed) {
        Map<String, Object> f = new LinkedHashMap<>(src);
        f.put("thoughts", List.of());
        String type = String.valueOf(f.get("type"));
        Integer who = f.get("seat") instanceof Number n ? n.intValue() : null;
        String log = f.get("log") == null ? "" : String.valueOf(f.get("log"));
        if (PRIVATE_LOGS.contains(type)) {
            log = privateLog(type, who, seat, log);
        }
        // Сид называет строка начала партии («…, сид 123456») — вырезаем
        // его из любой строки, а не только из той, где он сейчас встречается.
        String sd = String.valueOf(seed);
        if (seed != 0 && log.contains(sd)) {
            log = log.replace(sd, "скрыт до конца партии");
        }
        f.put("log", log);
        if (f.get("snap") instanceof Map<?, ?> snap) {
            f.put("snap", snapshot(asMap(snap), seat));
        }
        return f;
    }

    private static String privateLog(String type, Integer who, int seat, String log) {
        String name = who == null ? "Игрок" : "Игрок " + (who + 1);
        return switch (type) {
            case "objective_drawn" -> who != null && who == seat ? log
                : name + " получил задание (в закрытую)";
            case "blind_discard" -> "Отложенные приказы — рубашкой вверх";
            default -> name + ": закрытое действие";
        };
    }

    private static Map<String, Object> snapshot(Map<String, Object> src, int seat) {
        Map<String, Object> s = new LinkedHashMap<>(src);
        if (s.get("decks") instanceof Map<?, ?> decks) {
            Map<String, Object> dk = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : decks.entrySet()) {
                Map<String, Object> one = new LinkedHashMap<>(asMap(e.getValue()));
                one.put("d", backs(size(one.get("d"))));
                List<Object> discard = new ArrayList<>();
                if (one.get("s") instanceof List<?> sl) {
                    for (int i = 0; i < sl.size(); i++) {
                        discard.add(i == 0 ? sl.get(0) : BACK);
                    }
                }
                one.put("s", discard);
                dk.put(String.valueOf(e.getKey()), one);
            }
            s.put("decks", dk);
        }
        if (s.get("players") instanceof List<?> ps) {
            List<Object> out = new ArrayList<>();
            for (Object o : ps) {
                Map<String, Object> p = asMap(o);
                int ps0 = p.get("seat") instanceof Number n ? n.intValue() : -1;
                out.add(ps0 == seat ? p : otherPlayer(p));
            }
            s.put("players", out);
        }
        return s;
    }

    private static Map<String, Object> otherPlayer(Map<String, Object> src) {
        Map<String, Object> p = new LinkedHashMap<>(src);
        for (String hand : List.of("arsHand", "objHand", "ordHand")) {
            p.put(hand, backs(size(p.get(hand))));
        }
        if (p.get("ordAside") != null) {
            p.put("ordAside", BACK);
        }
        if (p.get("mandCard") != null) {
            p.put("mandCard", BACK);
        }
        if (p.get("tucked") instanceof List<?> tu) {
            List<Object> out = new ArrayList<>();
            for (Object o : tu) {
                Map<String, Object> t = new LinkedHashMap<>(asMap(o));
                if (!Boolean.TRUE.equals(t.get("rev"))) {
                    t.put("card", BACK);
                }
                out.add(t);
            }
            p.put("tucked", out);
        }
        return p;
    }

    /**
     * Разыгранные приказы: вскрытая карта открыта всем; закрытый низ чужого
     * приказа (не открыт — {@code bottomOpen == 0}) — без кода.
     */
    private static List<Object> orderPlays(Object raw, int seat) {
        List<Object> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            Map<String, Object> op = new LinkedHashMap<>(asMap(o));
            int s = op.get("seat") instanceof Number n ? n.intValue() : -1;
            boolean open = op.get("bottomOpen") instanceof Number b && b.intValue() != 0;
            if (s != seat && !open) {
                op.put("bottom", null);
                op.put("bottomActions", List.of());
            }
            out.add(op);
        }
        return out;
    }

    private static List<Object> backs(int n) {
        List<Object> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(BACK);
        }
        return out;
    }

    private static int size(Object list) {
        return list instanceof List<?> l ? l.size() : 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }
}
