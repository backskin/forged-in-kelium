package kelium.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * DeployPattern — проверка РИСУНКА второй части супер задания.
 *
 * <p>Правило дизайнера 12.08.2026: рисунок задаёт КОНКРЕТНЫЕ объекты (какое
 * здание, какой род войск, где чужой жетон) и требует их НЕПРЕРЫВНОГО
 * соединения. Непрерывность считается по ЯЧЕЙКАМ, а не по гексам — см.
 * {@link CellGraph}: жетоны напротив друг друга на одном гексе не связаны, зато
 * авиация в центре гекса связывает всю его наземку.
 *
 * <p>Формат в данных ({@code super_objectives.2.0.0.yaml}):
 * <pre>
 *   deploy:
 *     relation: connected        # непрерывное соединение всех объектов рисунка
 *     objects:
 *       - {what: "building:power_plant", count: 1}
 *       - {what: "unit:vehicle", count: 1}
 *       - {what: "enemy:any", count: 1}
 * </pre>
 *
 * <p>Проверка честная: перебираются наборы подходящих жетонов и ищется хотя бы
 * один связный. Наборы небольшие (3–4 объекта), поэтому полный перебор дешевле
 * любой эвристики и не может «почти найти» решение.
 */
public final class DeployPattern {

    private DeployPattern() {
    }

    /** Максимум объектов в рисунке, который перебираем полностью. */
    private static final int MAX_OBJECTS = 6;

    /**
     * Выполнен ли рисунок развёртывания у игрока {@code seat}.
     *
     * @param deploy блок {@code deploy} карты супер задания; null — рисунка нет,
     *               значит проверять нечего и условие считается невыполненным
     *               (карта без рисунка — ошибка данных, а не «победа даром»)
     */
    @SuppressWarnings("unchecked")
    public static boolean satisfied(GameState s, int seat, Map<String, Object> deploy) {
        if (deploy == null || !(deploy.get("objects") instanceof List<?> objs) || objs.isEmpty()) {
            return false;
        }
        // Разворачиваем требования в плоский список «слотов»: {what} × count.
        List<String> slots = new ArrayList<>();
        for (Object o : objs) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            String what = String.valueOf(m.get("what"));
            int count = m.get("count") instanceof Number n ? n.intValue() : 1;
            for (int i = 0; i < count; i++) {
                slots.add(what);
            }
        }
        if (slots.isEmpty() || slots.size() > MAX_OBJECTS) {
            return false;
        }
        // Кандидаты на каждый слот.
        List<List<Token>> candidates = new ArrayList<>();
        for (String what : slots) {
            List<Token> c = matching(s, seat, what);
            if (c.isEmpty()) {
                return false;               // такого объекта на поле нет вовсе
            }
            candidates.add(c);
        }
        String relation = String.valueOf(deploy.getOrDefault("relation", "connected"));
        return search(s, candidates, new ArrayList<>(), relation, deploy);
    }

    /** Перебор наборов: по одному жетону на слот, без повторов жетонов. */
    private static boolean search(GameState s, List<List<Token>> candidates,
                                  List<Token> chosen, String relation,
                                  Map<String, Object> deploy) {
        int depth = chosen.size();
        if (depth == candidates.size()) {
            return holds(s, chosen, relation, deploy);
        }
        for (Token t : candidates.get(depth)) {
            boolean used = false;
            for (Token c : chosen) {
                if (c.uid() == t.uid()) {
                    used = true;
                    break;
                }
            }
            if (used) {
                continue;
            }
            chosen.add(t);
            if (search(s, candidates, chosen, relation, deploy)) {
                return true;
            }
            chosen.remove(chosen.size() - 1);
        }
        return false;
    }

    /** Держится ли требуемое отношение на выбранном наборе жетонов. */
    private static boolean holds(GameState s, List<Token> chosen, String relation,
                                 Map<String, Object> deploy) {
        boolean connected = CellGraph.connected(s, chosen);
        return switch (relation) {
            case "connected" -> connected;
            // Дополнительное условие «середина рисунка примыкает к чужому»:
            // хотя бы у одного своего жетона набора есть чужой сосед по ячейке.
            case "connected_touching_enemy" -> connected && touchesEnemy(s, chosen);
            default -> connected;           // неизвестное отношение = связность
        };
    }

    /** Есть ли у набора чужой жетон, связанный по ячейке хоть с одним своим. */
    private static boolean touchesEnemy(GameState s, List<Token> chosen) {
        for (Token mine : chosen) {
            int owner = ownerOf(s, mine);
            for (PlayerState p : s.players) {
                if (p.seat == owner) {
                    continue;
                }
                for (Token foe : CellGraph.ownTokens(s, p.seat)) {
                    if (CellGraph.linked(s, mine, foe)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int ownerOf(GameState s, Token t) {
        for (PlayerState p : s.players) {
            for (Token x : CellGraph.ownTokens(s, p.seat)) {
                if (x.uid() == t.uid()) {
                    return p.seat;
                }
            }
        }
        return -1;
    }

    /** Жетоны, подходящие под требование вида {@code building:miner}. */
    private static List<Token> matching(GameState s, int seat, String what) {
        List<Token> out = new ArrayList<>();
        String[] parts = what.split(":", 2);
        String kind = parts[0];
        String sub = parts.length > 1 ? parts[1] : "any";
        switch (kind) {
            case "building" -> {
                for (BuildingToken b : s.player(seat).buildingsOnField()) {
                    if (buildingMatches(b, sub)) {
                        out.add(b);
                    }
                }
            }
            case "unit" -> {
                for (UnitToken u : s.player(seat).unitsOnField()) {
                    if (unitMatches(u, sub)) {
                        out.add(u);
                    }
                }
            }
            case "enemy" -> {
                for (PlayerState p : s.players) {
                    if (p.seat == seat) {
                        continue;
                    }
                    for (BuildingToken b : p.buildingsOnField()) {
                        if ("any".equals(sub) || "building".equals(sub)) {
                            out.add(b);
                        }
                    }
                    for (UnitToken u : p.unitsOnField()) {
                        if ("any".equals(sub) || "unit".equals(sub)) {
                            out.add(u);
                        }
                    }
                }
            }
            default -> { }
        }
        return out;
    }

    private static boolean buildingMatches(BuildingToken b, String sub) {
        if ("any".equals(sub)) {
            return true;
        }
        BuildingType want = switch (sub) {
            case "miner" -> BuildingType.MINER;
            case "power_plant" -> BuildingType.POWER_PLANT;
            case "barracks" -> BuildingType.BARRACKS;
            case "factory" -> BuildingType.FACTORY;
            case "airbase" -> BuildingType.AIRBASE;
            case "cu", "command_center" -> BuildingType.COMMAND_CENTER;
            default -> null;
        };
        return want != null && b.type == want;
    }

    private static boolean unitMatches(UnitToken u, String sub) {
        if ("any".equals(sub)) {
            return true;
        }
        UnitType want = switch (sub) {
            case "infantry" -> UnitType.INFANTRY;
            case "vehicle" -> UnitType.VEHICLE;
            case "aircraft" -> UnitType.AIRCRAFT;
            case "tower" -> UnitType.TOWER;
            default -> null;
        };
        return want != null && u.type == want;
    }

    /** Человеческое описание рисунка — для логов и проигрывателя. */
    @SuppressWarnings("unchecked")
    public static String describe(Map<String, Object> deploy) {
        if (deploy == null || !(deploy.get("objects") instanceof List<?> objs)) {
            return "рисунок не задан";
        }
        StringBuilder sb = new StringBuilder("непрерывная связка: ");
        boolean first = true;
        for (Object o : objs) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            if (!first) {
                sb.append(" + ");
            }
            first = false;
            int count = m.get("count") instanceof Number n ? n.intValue() : 1;
            sb.append(ru(String.valueOf(m.get("what"))));
            if (count > 1) {
                sb.append(" ×").append(count);
            }
        }
        return sb.toString();
    }

    private static String ru(String what) {
        return switch (what) {
            case "building:miner" -> "добытчик";
            case "building:power_plant" -> "станция";
            case "building:barracks" -> "казарма";
            case "building:factory" -> "завод";
            case "building:airbase" -> "авиабаза";
            case "building:cu" -> "ЦУ";
            case "unit:infantry" -> "пехота";
            case "unit:vehicle" -> "техника";
            case "unit:aircraft" -> "авиация";
            case "unit:tower" -> "вышка";
            case "enemy:any" -> "чужой жетон";
            case "enemy:building" -> "чужое здание";
            case "enemy:unit" -> "чужое войско";
            default -> what;
        };
    }

    /** Свободна ли воздушная ячейка гекса (подсказка боту: чем связать). */
    public static boolean airFree(GameState s, String hexId) {
        Hex h = s.field.get(hexId);
        return h != null && !CellGraph.bridgesAir(s, hexId);
    }
}
