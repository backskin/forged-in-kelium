package kelium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;

/**
 * Верификатор РАСКЛАДОК поля (сценарии 2p/3p/4p — транскрипция txt дизайнера 1:1).
 *
 * <p>ЖЁСТКИЕ проверки (целостность переноса):
 * <ul>
 *   <li>каждый вариант грузится как реальный сценарий (не кольцо-заглушка) —
 *       т.е. форма корректна и у всех мест есть старты;</li>
 *   <li>обычные грядки присутствуют (~1 на игрока/двоих, сдвоенная за две).</li>
 * </ul>
 *
 * <p>ПРЕДУПРЕЖДЕНИЯ (старые пожелания дизайнера; его авторские раскладки местами
 * их нарушают, поэтому НЕ роняют тест — только печатаются для обзора):
 * у старта &ge;3 соседей; стартовая грядка соседняя со стартом; дистанция между
 * стартами &ge;3.
 */
class FieldLayoutTest {

    private Integer bfs(GameState s, String from, String to) {
        Map<String, Integer> seen = new HashMap<>();
        seen.put(from, 0);
        Deque<String> q = new ArrayDeque<>();
        q.add(from);
        while (!q.isEmpty()) {
            String x = q.poll();
            if (x.equals(to)) {
                return seen.get(x);
            }
            for (String nb : s.field.neighbors(x)) {
                if (!seen.containsKey(nb)) {
                    seen.put(nb, seen.get(x) + 1);
                    q.add(nb);
                }
            }
        }
        return null;
    }

    /** Стартовый гекс каждого места = гекс, где стоит его ЦУ (Setup ставит ЦУ на старт). */
    private Map<Integer, String> startHexes(GameState s) {
        Map<Integer, String> out = new HashMap<>();
        for (var p : s.players) {
            for (var b : p.buildingsOnField()) {
                if (b.type == kelium.core.BuildingType.COMMAND_CENTER) {
                    out.put(p.seat, b.hexId);
                }
            }
        }
        return out;
    }

    private void checkLayout(int players) {
        // прогоняем несколько сидов, чтобы покрыть ВСЕ варианты раскладок
        for (long seed = 0; seed < 8; seed++) {
            checkOne(players, seed);
        }
    }

    private void checkOne(int players, long seed) {
        GameState s = Setup.buildGame(GameConfig.build(players, seed));
        // если поле — кольцо-заглушка (нет осевых id), сценарий не загрузился
        boolean scenario = s.field.hexes.keySet().stream().anyMatch(id -> id.startsWith("h") && id.contains("_"));
        System.out.printf("=== %dp seed=%d: гексов=%d, сценарий=%s ===%n",
            players, seed, s.field.hexes.size(), scenario ? "да" : "НЕТ(заглушка)");
        assertTrue(scenario, players + "p: должен грузиться реальный сценарий, а не кольцо-заглушка");

        Map<Integer, String> starts = startHexes(s);

        // стартовые и обычные грядки
        List<String> startSpawns = new ArrayList<>();
        int normalSpawns = 0;
        int doubled = 0;
        for (Hex h : s.field.hexes.values()) {
            if (h.spawnTile != null) {
                if (h.spawnTile.isStart) {
                    startSpawns.add(h.id);
                } else {
                    normalSpawns++;
                    if (h.spawnTile.stack >= 2) {
                        doubled++;
                    }
                }
            }
        }
        System.out.println("  старты(ЦУ): " + starts);
        System.out.println("  стартовых грядок: " + startSpawns.size()
            + ", обычных грядок: " + normalSpawns + " (сдвоенных: " + doubled + ")");

        // ПРЕДУПРЕЖДЕНИЯ: правила дизайнера к раскладкам (печать, не падение)
        List<String> warnings = new ArrayList<>();
        // (а) гексов на игрока 7-10
        int playable = 0;
        for (Hex h : s.field.hexes.values()) {
            if (h.kind != HexKind.FORBIDDEN) {
                playable++;
            }
        }
        double perPlayer = (double) playable / players;
        System.out.printf("  играбельных гексов=%d (%.1f на игрока)%n", playable, perPlayer);
        if (perPlayer < 7 || perPlayer > 10) {
            warnings.add(String.format(Locale.ROOT,
                "гексов на игрока %.1f (норма 7-10)", perPlayer));
        }
        // (б) грядок каждого типа — по числу игроков (x2-стопка = две)
        if (startSpawns.size() != players) {
            warnings.add("стартовых грядок " + startSpawns.size() + " (норма = игрокам: " + players + ")");
        }
        int normKelium = normalSpawns + doubled;
        if (normKelium != players) {
            warnings.add("обычных грядок " + normKelium + " с учётом x2 (норма = игрокам: " + players + ")");
        }
        // (в) старты: >=3 соседей, из них >=2 свободных (движение/стройка),
        //     плюс соседняя стартовая грядка
        for (var e : starts.entrySet()) {
            String start = e.getValue();
            List<String> nbs = s.field.neighbors(start);
            int free = 0;
            boolean adjStartSpawn = false;
            for (String nb : nbs) {
                Hex h = s.field.get(nb);
                // §12.3: блокировка по-сторонняя — гекс с нейтралом СВОБОДЕН,
                // если стенки нейтрала оставляют хотя бы одну наземную ячейку
                // и ребро к старту не закрыто стенкой.
                if (h.kind == HexKind.NORMAL && h.freeSectors() >= 1) {
                    boolean edgeOpen = false;
                    for (int j : h.sidesFacing(start)) {
                        if (h.sideOwner[j] == null) {
                            edgeOpen = true;
                        }
                    }
                    if (edgeOpen) {
                        free++;
                    }
                }
                if (h.spawnTile != null && h.spawnTile.isStart) {
                    adjStartSpawn = true;
                }
            }
            System.out.printf("  игрок%d старт=%s соседей=%d свободных=%d%n",
                e.getKey(), start, nbs.size(), free);
            if (nbs.size() < 3) {
                warnings.add("игрок" + e.getKey() + ": у старта " + start
                    + " только " + nbs.size() + " соседа(ей), норма >=3");
            }
            if (free < 2) {
                warnings.add("игрок" + e.getKey() + ": у старта " + start
                    + " только " + free + " свободных соседа(ей), норма >=2");
            }
            if (!adjStartSpawn) {
                warnings.add("игрок" + e.getKey() + ": стартовая грядка НЕ граничит со стартом " + start);
            }
        }

        List<String> sh = new ArrayList<>(starts.values());
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < sh.size(); i++) {
            for (int k = i + 1; k < sh.size(); k++) {
                Integer d = bfs(s, sh.get(i), sh.get(k));
                if (d != null) {
                    minDist = Math.min(minDist, d);
                }
            }
        }
        System.out.println("  мин. дистанция между стартами: " + minDist);
        if (minDist < 3) {
            warnings.add("дистанция между стартами " + minDist + " < 3");
        }
        for (String w : warnings) {
            System.out.println("  [!] " + w);
        }

        // ЖЁСТКО: обычные грядки есть (~1 на игрока/двоих, сдвоенная считается за две)
        assertTrue(normalSpawns + doubled >= players / 2,
            players + "p: обычных грядок мало (" + normalSpawns + "), нужно ~по 1 на игрока/двоих");
    }

    @Test
    void layout2p() {
        checkLayout(2);
    }

    @Test
    void layout3p() {
        checkLayout(3);
    }

    @Test
    void layout4p() {
        checkLayout(4);
    }
}
