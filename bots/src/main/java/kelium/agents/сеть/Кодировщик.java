package kelium.agents.сеть;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Scoring;

/**
 * КОДИРОВЩИК СТОЛА (23.09.2026): позиция глазами одного игрока — набор чисел
 * для нейросети оценки.
 *
 * <p>Это ОПИСАНИЕ стола, а не оценка: здесь нет ни одного «это хорошо». Что
 * из этого важно и насколько, сеть узнаёт сама — из итогов партий.
 *
 * <p>Порядок: сначала сам игрок, затем соперники по ходу часовой стрелки от
 * него — так сеть видит «меня» и «следующего за мной» всегда в одних и тех же
 * клетках, какое бы место я ни занимал. Видно только открытое: у соперников —
 * СКОЛЬКО карт на руке, но не какие.
 */
public final class Кодировщик {

    private Кодировщик() {
    }

    private static final String[] ЦВЕТА = {"blue", "red", "green", "yellow"};
    private static final BuildingType[] ВОЕННЫЕ = {
        BuildingType.BARRACKS, BuildingType.FACTORY, BuildingType.AIRBASE};

    /** Чисел на одного игрока. */
    public static final int НА_ИГРОКА = 42;
    /** Общих чисел стола. */
    public static final int ОБЩИХ = 4;

    /** Длина вектора для {@code мест} игроков. */
    public static int длина(int мест) {
        return ОБЩИХ + НА_ИГРОКА * мест;
    }

    /** Закодировать стол глазами места {@code seat}. */
    public static float[] закодировать(GameState s, int seat) {
        int мест = s.numPlayers();
        float[] v = new float[длина(мест)];
        int i = 0;
        int кругов = 4;
        v[i++] = s.round / 10f;
        v[i++] = s.circle / (float) кругов;
        int келемийНаПоле = 0;
        int источников = 0;
        for (Hex h : s.field.hexes.values()) {
            if (h.spawnTile != null) {
                келемийНаПоле += h.spawnTile.kelium;
                if (h.spawnTile.kelium > 0) {
                    источников++;
                }
            }
        }
        v[i++] = келемийНаПоле / 20f;
        v[i++] = источников / 6f;
        for (int k = 0; k < мест; k++) {
            PlayerState p = s.player((seat + k) % мест);
            i = игрок(s, p, v, i);
        }
        return v;
    }

    // ======================================================================
    //  ВАРИАНТ РЕШЕНИЯ — для сети ходов
    // ======================================================================

    private static final int КОРЗИН_ВИДА = 32;
    private static final int КОРЗИН_ТИПА = 16;
    private static final int КОРЗИН_СЛОВ = 128;
    private static final int ПРО_ГЕКС = 9;
    /** Длина описания варианта. */
    public static final int ВАРИАНТ = КОРЗИН_ВИДА + КОРЗИН_ТИПА + КОРЗИН_СЛОВ + ПРО_ГЕКС;

    private static final java.util.regex.Pattern ГЕКС =
        java.util.regex.Pattern.compile("h-?\\d+_-?\\d+");

    /** Вход сети ходов: стол глазами решающего + описание варианта. */
    public static float[] вход(float[] стол, float[] вариант) {
        float[] x = new float[стол.length + вариант.length];
        System.arraycopy(стол, 0, x, 0, стол.length);
        System.arraycopy(вариант, 0, x, стол.length, вариант.length);
        return x;
    }

    /**
     * Описание варианта решения: вид решения, тип варианта, слова подписи (по
     * корзинам) и — если вариант называет гекс — что на этом гексе. Названия
     * гексов в слова не идут: на другом поле те же имена значат другое, а
     * смысл гекса передают числа «что там».
     */
    public static float[] вариант(GameState s, int seat, String видРешения, kelium.core.Choice c) {
        float[] v = new float[ВАРИАНТ];
        v[корзина(видРешения, КОРЗИН_ВИДА)] = 1;
        v[КОРЗИН_ВИДА + корзина(c.kind(), КОРЗИН_ТИПА)] = 1;
        String подпись = c.label() != null ? c.label() : String.valueOf(c.payload());
        String гекс = null;
        java.util.regex.Matcher m = ГЕКС.matcher(подпись);
        if (m.find()) {
            гекс = m.group();
        }
        String слова = ГЕКС.matcher(подпись).replaceAll(" ");
        for (String слово : слова.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
            if (!слово.isEmpty()) {
                v[КОРЗИН_ВИДА + КОРЗИН_ТИПА + корзина(слово, КОРЗИН_СЛОВ)] += 1;
            }
        }
        if (гекс == null && c.payload() instanceof String p && s.field.get(p) != null) {
            гекс = p;
        }
        if (гекс != null && s.field.get(гекс) != null) {
            проГекс(s, seat, s.field.get(гекс), v, КОРЗИН_ВИДА + КОРЗИН_ТИПА + КОРЗИН_СЛОВ);
        }
        return v;
    }

    private static int корзина(String слово, int корзин) {
        return Math.floorMod(слово.hashCode() * 0x9E3779B1, корзин);
    }

    /** Что на гексе глазами игрока: 9 чисел. */
    private static void проГекс(GameState s, int seat, Hex h, float[] v, int i) {
        int моиЗд = 0, чужиеЗд = 0, моиВойска = 0, чужиеВойска = 0;
        for (PlayerState p : s.players) {
            for (BuildingToken b : p.buildingsOnField()) {
                if (h.id.equals(b.hexId)) {
                    if (p.seat == seat) {
                        моиЗд++;
                    } else {
                        чужиеЗд++;
                    }
                }
            }
            for (UnitToken u : p.unitsOnField()) {
                if (h.id.equals(u.hexId)) {
                    if (p.seat == seat) {
                        моиВойска++;
                    } else {
                        чужиеВойска++;
                    }
                }
            }
        }
        v[i++] = 1;   // вариант называет гекс
        v[i++] = моиЗд;
        v[i++] = чужиеЗд;
        v[i++] = моиВойска / 2f;
        v[i++] = чужиеВойска / 2f;
        v[i++] = h.spawnTile == null ? 0 : h.spawnTile.kelium / 3f;
        v[i++] = kelium.engine.PrintedContainers.visibleContainer(s, h) ? 1 : 0;
        v[i++] = расстояние(s, h.id, seat, true) / 6f;
        v[i++] = расстояние(s, h.id, seat, false) / 6f;
    }

    /** Шагов до моего ЦУ ({@code моё}) или до ближайшего чужого здания; 6 — далеко. */
    private static int расстояние(GameState s, String от, int seat, boolean моё) {
        java.util.Set<String> цели = new java.util.HashSet<>();
        for (PlayerState p : s.players) {
            if ((p.seat == seat) != моё) {
                continue;
            }
            for (BuildingToken b : p.buildingsOnField()) {
                if (!моё || b.type == BuildingType.COMMAND_CENTER) {
                    цели.add(b.hexId);
                }
            }
        }
        if (цели.isEmpty()) {
            return 6;
        }
        java.util.ArrayDeque<String> очередь = new java.util.ArrayDeque<>();
        java.util.Map<String, Integer> шаг = new java.util.HashMap<>();
        очередь.add(от);
        шаг.put(от, 0);
        while (!очередь.isEmpty()) {
            String x = очередь.poll();
            int d = шаг.get(x);
            if (цели.contains(x)) {
                return d;
            }
            if (d >= 6) {
                continue;
            }
            for (String nb : s.field.neighbors(x)) {
                if (!шаг.containsKey(nb)) {
                    шаг.put(nb, d + 1);
                    очередь.add(nb);
                }
            }
        }
        return 6;
    }

    private static int игрок(GameState s, PlayerState p, float[] v, int i) {
        int начало = i;
        v[i++] = Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0) / 10f;
        v[i++] = p.resources.coin() / 10f;
        v[i++] = p.resources.kelium() / 5f;
        v[i++] = p.resources.ammo() / 5f;
        v[i++] = p.resources.trophy() / 5f;
        // здания: военные по типам (стоит / запитано), добытчики и станции по уровням
        for (BuildingType t : ВОЕННЫЕ) {
            int стоит = 0;
            int запитано = 0;
            for (BuildingToken b : p.buildingsOnField()) {
                if (b.type == t) {
                    стоит++;
                    if (b.powered()) {
                        запитано++;
                    }
                }
            }
            v[i++] = стоит;
            v[i++] = запитано;
        }
        float[] шахты = new float[4];
        float[] станции = new float[4];
        int шахтЗапитано = 0;
        boolean цу = false;
        for (BuildingToken b : p.buildingsOnField()) {
            int ур = b.level == null ? 1 : Math.max(1, Math.min(4, b.level));
            if (b.type == BuildingType.MINER) {
                шахты[ур - 1]++;
                if (b.powered()) {
                    шахтЗапитано++;
                }
            } else if (b.type == BuildingType.POWER_PLANT) {
                станции[ур - 1]++;
            } else if (b.type == BuildingType.COMMAND_CENTER) {
                цу = true;
            }
        }
        for (float x : шахты) {
            v[i++] = x;
        }
        for (float x : станции) {
            v[i++] = x;
        }
        v[i++] = шахтЗапитано;
        v[i++] = цу ? 1 : 0;
        // войска на поле по родам
        for (UnitType t : new UnitType[]{UnitType.INFANTRY, UnitType.VEHICLE,
                UnitType.AIRCRAFT, UnitType.TOWER}) {
            int n = 0;
            for (UnitToken u : p.unitsOnField()) {
                if (u.type == t) {
                    n++;
                }
            }
            v[i++] = n / 2f;
        }
        // треки науки
        for (String трек : s.tech.tracks) {
            v[i++] = p.techSteps.getOrDefault(трек, 0) / 4f;
        }
        for (int k = s.tech.tracks.size(); k < 3; k++) {
            v[i++] = 0;
        }
        v[i++] = p.redModules / 2f;
        v[i++] = p.blueModules / 2f;
        v[i++] = p.goldModules / 2f;
        v[i++] = p.objectiveHand.size() / 3f;
        v[i++] = p.objectivesCompleted / 5f;
        v[i++] = p.arsenalInstalled.size() / 3f;
        v[i++] = p.arsenalHand.size() / 3f;
        v[i++] = p.destroyedValue() / 5f;
        v[i++] = p.cuDestructionTokens;
        v[i++] = p.containers / 3f;
        // цвет фракции
        String сторона = p.board.troop.side;
        for (String ц : ЦВЕТА) {
            v[i++] = ц.equals(сторона) ? 1 : 0;
        }
        if (i - начало != НА_ИГРОКА) {
            throw new IllegalStateException("кодировщик: " + (i - начало) + " чисел вместо "
                + НА_ИГРОКА);
        }
        return i;
    }
}
