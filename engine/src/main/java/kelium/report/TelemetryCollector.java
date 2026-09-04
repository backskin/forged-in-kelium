package kelium.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сборщик телеметрии — превращает поток событий движка в отчёт по партии.
 *
 * <p>Порт из forge/report/telemetry.py. Движок эмитит события (game_start,
 * refresh, reveal, action, turn_end, return, game_end). Этот сборщик накапливает
 * их в структурированный отчёт по партии: победитель + условие + отрыв, разбивка
 * трофеев по источникам для каждого игрока, потоки ресурсов, частота
 * использования действий (в т.ч. неуспешных), число раундов.
 *
 * <p>Подключается как один из получателей потока событий (см. RunBatch): метод
 * {@link #record(Map)} обрабатывает по одному событию.
 */
public final class TelemetryCollector {

    /** Структурированный отчёт по одной партии (счёт, действия, потоки ресурсов). */
    public static final class GameReport {
        public String rulesetId = "";
        public int numPlayers = 0;
        public int rounds = 0;
        public Integer winner = null;
        public String condition = null;
        // scores[seat] = {источник: очки, ..., "total": сумма}
        public Map<Integer, Map<String, Integer>> scores = new HashMap<>();

        public final Map<String, Integer> actionCounts = new HashMap<>();
        public final Map<Integer, Map<String, Integer>> actionCountsBySeat = new HashMap<>();
        public final Map<Integer, Map<String, Integer>> resourceFlows = new HashMap<>();
        public final Map<String, Integer> failedActions = new HashMap<>();

        /** Отрыв победителя от второго места по трофейным очкам. */
        public int margin() {
            if (scores.isEmpty()) {
                return 0;
            }
            List<Integer> totals = new ArrayList<>();
            for (Map<String, Integer> bd : scores.values()) {
                totals.add(bd.getOrDefault("total", 0));
            }
            totals.sort((a, b) -> Integer.compare(b, a));
            return totals.size() > 1 ? totals.get(0) - totals.get(1) : totals.get(0);
        }
    }

    private final GameReport report = new GameReport();

    /** Накопленный отчёт по партии. */
    public GameReport report() {
        return report;
    }

    /** Обработать одно событие движка и обновить накопленный отчёт. */
    @SuppressWarnings("unchecked")
    public void record(Map<String, Object> event) {
        GameReport r = report;
        Object t = event.get("type");
        if ("game_start".equals(t)) {
            r.numPlayers = ((Number) event.get("players")).intValue();
            r.rulesetId = String.valueOf(event.get("ruleset"));
            for (int seat = 0; seat < r.numPlayers; seat++) {
                r.actionCountsBySeat.put(seat, new HashMap<>());
                r.resourceFlows.put(seat, new HashMap<>());
            }
        } else if ("action".equals(t)) {
            String name = (String) event.get("action");
            int seat = ((Number) event.get("seat")).intValue();
            if (Boolean.TRUE.equals(event.get("ok"))) {
                r.actionCounts.merge(name, 1, Integer::sum);
                r.actionCountsBySeat.computeIfAbsent(seat, k -> new HashMap<>()).merge(name, 1, Integer::sum);
                Object telObj = event.get("telemetry");
                if (telObj instanceof Map<?, ?> tel) {
                    Map<String, Integer> flows = r.resourceFlows.computeIfAbsent(seat, k -> new HashMap<>());
                    for (var e : ((Map<String, Object>) tel).entrySet()) {
                        if (e.getValue() instanceof Number n) {
                            flows.merge(e.getKey(), n.intValue(), Integer::sum);
                        }
                    }
                }
            } else {
                r.failedActions.merge(name, 1, Integer::sum);
            }
        } else if ("game_end".equals(t)) {
            r.winner = event.get("winner") == null ? null : ((Number) event.get("winner")).intValue();
            r.condition = (String) event.get("condition");
            Map<Integer, Map<String, Integer>> sc = (Map<Integer, Map<String, Integer>>) event.get("scores");
            r.scores = new HashMap<>();
            for (var e : sc.entrySet()) {
                r.scores.put(e.getKey(), new HashMap<>(e.getValue()));
            }
        } else if ("return".equals(t)) {
            Object rnd = event.get("round");
            if (rnd instanceof Number n) {
                r.rounds = Math.max(r.rounds, n.intValue());
            }
        }
    }
}
