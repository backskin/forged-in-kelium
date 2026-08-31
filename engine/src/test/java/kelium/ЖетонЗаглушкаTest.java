package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.Modules;
import kelium.engine.Setup;

/**
 * ЖЕТОН-ЗАГЛУШКА (правило дизайнера 27.08.2026).
 *
 * <p>Четыре красных жетона, по одному на род войск. В подготовку их тянут
 * наугад и кладут в ячейку нарисованного рода — но НАРИСОВАННЫЙ РОД ЗНАЧИТ
 * ТОЛЬКО СТАРТОВОЕ ПОЛОЖЕНИЕ. Дальше это просто заглушка: она занимает ячейку
 * красного модуля, закрывает собой ту спец-атаку, на которой лежит, и
 * перекладывается с ячейки на ячейку по обычным правилам красных жетонов.
 *
 * <p>Два этих свойства и стерегутся здесь: раньше движок заглушку двигать
 * запрещал, а ячейку она занимала не по-настоящему — рабочий модуль на тот же
 * род положить было можно, он просто не срабатывал.
 */
class ЖетонЗаглушкаTest {

    /** Свод, в котором правило включено. */
    private static final String СВОД = "1.24.0-пломба";

    /**
     * Стол уже ПОСЛЕ подготовки: заглушка выдана и лежит в ячейке своего рода.
     *
     * <p>Саму раздачу делает {@code GameEngine} при старте партии, и она была
     * верной и раньше — здесь стерегутся два правила, которые чинились:
     * заглушку можно переложить, и ячейку под ней занимать нельзя.
     */
    private GameState стол() {
        GameConfig cfg = GameConfig.buildCached(СВОД, 2, 4242L, null, null);
        GameState s = Setup.buildGame(cfg);
        for (PlayerState p : s.players) {
            положитьЗаглушку(p, первыйРодСМестом(p));
        }
        return s;
    }

    /**
     * Положить глухой жетон на ячейку рода — ровно так, как это делает движок
     * (см. GameEngine.offerSealChoice): запись в redPlacements с флагом blocks.
     * Отдельного поля под заглушку в состоянии нет и быть не должно: жетон
     * физически такой же красный, только ничего не открывает.
     */
    private static void положитьЗаглушку(PlayerState p, UnitType род) {
        java.util.Map<String, Object> жетон = new java.util.HashMap<>();
        жетон.put("id", PlayerState.CU_MODULE);
        жетон.put("blocks", true);
        p.redPlacements.put(род, жетон);
    }

    /** На каком роде сейчас лежит глухой жетон, или null. */
    private static UnitType заглушкаНа(PlayerState p) {
        for (var e : p.redPlacements.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue().get("blocks"))) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Первый род войск, у которого на планшете есть место под красный жетон. */
    private static UnitType первыйРодСМестом(PlayerState p) {
        for (UnitType t : UnitType.values()) {
            if (Modules.redSlotsFor(p, t) > 0) {
                return t;
            }
        }
        throw new IllegalStateException("на планшете нет ни одного места под модуль");
    }

    /** Агент, который на каждой точке решения берёт первый подходящий вариант. */
    private static final class Первый extends Agent {
        private final String хочу;
        private final Object payload;

        Первый(String хочу, Object payload) {
            super(0, "тест");
            this.хочу = хочу;
            this.payload = payload;
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            if (хочу.equals(ctx.get("kind"))) {
                for (Choice c : options) {
                    if (payload == null || payload.equals(c.payload())) {
                        return c;
                    }
                }
            }
            return options.get(options.size() - 1);   // «оставить в запасе»
        }
    }

    @Test
    void заглушкаЗанимаетСвоюЯчейкуПокаЖетонУВладельца() {
        GameState s = стол();
        PlayerState p = s.player(0);
        assertNotNull(заглушкаНа(p), "заглушка должна лежать в ячейке");
        assertTrue(p.ownCuTokenAvailable, "жетон ещё у владельца");
        assertTrue(Modules.sealActive(s, p), "правило включено — заглушка в игре");
        assertTrue(Modules.sealSits(s, p, заглушкаНа(p)),
            "заглушка занимает ту ячейку, на которой лежит");
    }

    @Test
    void заглушкуМожноПереложитьНаДругойРод() {
        GameState s = стол();
        PlayerState p = s.player(0);
        UnitType было = заглушкаНа(p);

        UnitType куда = null;
        for (UnitType t : UnitType.values()) {
            if (t != было && Modules.redSlotsFor(p, t) > 0) {
                куда = t;
                break;
            }
        }
        assertNotNull(куда, "на планшете должен быть второй род с местом под модуль");

        Modules.moduleSwap(s, 0, new Первый("seal_move", куда), ev -> { });
        assertEquals(куда, заглушкаНа(p),
            "нарисованный род значит только стартовое положение — заглушка переносится");
        assertTrue(Modules.sealSits(s, p, куда), "теперь занята новая ячейка");
        assertFalse(Modules.sealSits(s, p, было), "прежняя ячейка освободилась");
    }

    @Test
    void подЗаглушкойЯчейкаЗанятаИРабочийМодульТудаНеЛожится() {
        GameState s = стол();
        PlayerState p = s.player(0);
        p.redModules = 4;                      // жетонов вдоволь — дело не в них

        Modules.moduleSwap(s, 0, new Agent(0, "жадный") {
            @Override
            public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                // Заглушку не двигаем, а модули раскладываем куда дают.
                if ("seal_move".equals(ctx.get("kind"))) {
                    for (Choice c : options) {
                        if (заглушкаНа(p).equals(c.payload())) {
                            return c;
                        }
                    }
                }
                return options.get(0);
            }
        }, ev -> { });

        // ЧТО ИМЕННО ПРОВЕРЯЕТСЯ. Заглушка живёт в той же redPlacements, что и
        // рабочие модули, поэтому «записи на этом роде нет» — не тот признак:
        // запись есть всегда, это сама заглушка. Признак правила в том, что
        // запись ОСТАЛАСЬ ГЛУХОЙ: рабочий модуль ячейку не занял.
        UnitType подЗаглушкой = заглушкаНа(p);
        assertNotNull(подЗаглушкой, "заглушка никуда не делась");
        assertTrue(Boolean.TRUE.equals(p.redPlacements.get(подЗаглушкой).get("blocks")),
            "на ячейку под заглушкой рабочий красный модуль класть нельзя");
    }

    @Test
    void сносЦУУноситЗаглушкуИОсвобождаетЯчейку() {
        GameState s = стол();
        PlayerState p = s.player(0);
        UnitType была = заглушкаНа(p);

        // Жетон уехал к захватчику — ровно это и значит ownCuTokenAvailable.
        p.ownCuTokenAvailable = false;

        assertFalse(Modules.sealActive(s, p), "заглушки у игрока больше нет");
        assertFalse(Modules.sealSits(s, p, была),
            "ячейка освободилась под ещё один красный жетон");
    }
}
