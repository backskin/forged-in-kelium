package kelium.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.Placement;

/**
 * Fix — сборка состояния партии для тестов МЕХАНИК, а не целых партий.
 *
 * <p>До неё каждый тест выписывал одни и те же десять строк: собрать конфиг,
 * подготовить партию, руками привязать журнал, бой и агентов, вручную создать
 * жетон с правильным uid и посадить его на нужные стороны гекса. Половина
 * тестов эту привязку забывала и потому прогоняла целую партию там, где хватило
 * бы одной сцены.
 *
 * <p>Правила игры фикстура НЕ подменяет: жетоны создаются теми же
 * характеристиками из данных и сажаются тем же способом, что в движке.
 */
public final class Fix {

    private Fix() {
    }

    /**
     * Агент для сцен: всегда ДЕЙСТВУЕТ, если есть чем. Берёт первый вариант,
     * который не «пас» — иначе половина проверок молча превращалась бы в
     * «ничего не произошло».
     */
    public static final class FirstChoiceAgent extends Agent {
        public FirstChoiceAgent(int seat) {
            super(seat, "fix#" + seat);
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            for (Choice c : options) {
                if (!"pass".equals(c.kind()) && c.payload() != null) {
                    return c;
                }
            }
            return options.get(0);
        }
    }

    /**
     * Агент, который ЦЕЛИТСЯ В ЗАДАННЫЙ ГЕКС: среди вариантов предпочитает тот,
     * чья начинка равна {@code hexId}, иначе ведёт себя как обычный.
     *
     * <p>Нужен потому, что «первый подходящий вариант» — плохой выбор для
     * сцены: соседний гекс с нейтральной постройкой тоже законная цель, и удар
     * уходил не туда, куда ставили противника.
     */
    public static final class AimingAgent extends Agent {
        private final String hexId;

        public AimingAgent(int seat, String hexId) {
            super(seat, "aim#" + seat);
            this.hexId = hexId;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            for (Choice c : options) {
                if (hexId.equals(c.payload())) {
                    return c;
                }
            }
            for (Choice c : options) {
                if (!"pass".equals(c.kind()) && c.payload() != null) {
                    return c;
                }
            }
            return options.get(0);
        }
    }

    /**
     * Подготовленная партия с привязанным журналом, боем и агентами: можно сразу
     * вызывать действия и бой.
     */
    public static GameState game(int players, long seed) {
        GameState s = Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players, seed, null, null));
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            agents.add(new FirstChoiceAgent(seat));
        }
        GameEngine.bind(s, agents);
        s.round = 1;
        s.circle = 1;
        return s;
    }

    /** То же на четверых с сидом по умолчанию. */
    public static GameState game() {
        return game(4, 7L);
    }

    /** Поставить игроку здание на гекс (занимает столько сторон, сколько положено). */
    public static BuildingToken building(GameState s, int seat, BuildingType type,
                                         String hexId, Integer level) {
        PlayerState p = s.player(seat);
        BuildingToken b = s.tokenStats.makeBuilding(type, seat, nextUid(s), level);
        b.hexId = hexId;
        Hex h = s.field.get(hexId);
        List<Integer> sides = h.chooseFootprint(Placement.footprint(type), 0, 0);
        if (sides == null) {
            sides = h.firstFreeFootprint(Placement.footprint(type));
        }
        if (sides != null) {
            h.occupySides(b.uid, sides);
        }
        p.buildings.add(b);
        return b;
    }

    /** Поставить игроку войско на гекс. */
    public static UnitToken unit(GameState s, int seat, UnitType type, String hexId) {
        UnitToken u = s.tokenStats.makeUnit(type, seat, nextUid(s));
        u.hexId = hexId;
        s.player(seat).units.add(u);
        return u;
    }

    /** Запитать здание (столько кубиков, сколько ему нужно). */
    public static void power(BuildingToken b) {
        if (b.energySlots > b.energyPlaced) {
            b.addEnergyFrom(b.uid, b.energySlots - b.energyPlaced);
        }
    }

    /** Соседний гекс, на который можно ставить (не запретный, без тайла). */
    public static String freeNeighbour(GameState s, String hexId) {
        for (String nb : s.field.neighbors(hexId)) {
            Hex h = s.field.get(nb);
            if (h.kind == kelium.core.HexKind.NORMAL && h.spawnTile == null
                    && !h.hasNeutral()) {
                return nb;
            }
        }
        return null;
    }

    /** Начать ход места: журнал получает чистые факты этого хода. */
    public static void turn(GameState s, int seat) {
        s.journal.startTurn(seat);
    }

    private static int nextUid(GameState s) {
        int max = 0;
        for (PlayerState p : s.players) {
            for (BuildingToken b : p.buildings) {
                max = Math.max(max, b.uid);
            }
            for (UnitToken u : p.units) {
                max = Math.max(max, u.uid);
            }
        }
        return max + 1;
    }
}
