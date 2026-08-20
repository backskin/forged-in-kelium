package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Effects;
import kelium.engine.Setup;
import kelium.core.Agent;

/**
 * ДЕСАНТ ВЫСАЖИВАЕТ ТОЛЬКО В СВОЮ ЗОНУ СТРОЙКИ (решение дизайнера 20.08.2026).
 *
 * <p>ЗАЧЕМ ЭТОТ ТЕСТ. Эффект перебирал ВСЕ гексы поля, то есть ставил два жетона
 * куда угодно — включая гекс рядом с чужим ЦУ, — а на карте было написано просто
 * «размести», без места. Дыру нашёл дизайнер, прочитав текст карты: «размести
 * ГДЕ?». Поймать её иначе было трудно — снаружи эффект выглядел безобидно.
 *
 * <p>Тест сторожит именно границу выбора, а не число высаженных: вернуть перебор
 * по всему полю — правка одной строки, и без сторожа она пройдёт незамеченной.
 */
class LandingZoneTest {

    private static GameState game() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 4242L, null, null));
    }

    /** Агент, который запоминает предложенные варианты и всегда отказывается. */
    private static final class Spy extends Agent {
        final List<String> offeredHexes = new ArrayList<>();

        Spy(int seat) {
            super(seat, "шпион");
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            for (Choice o : options) {
                if ("landing".equals(o.kind()) && o.payload() instanceof Map<?, ?> m) {
                    offeredHexes.add(String.valueOf(m.get("hex")));
                }
            }
            // Отказываемся: нам нужен сам СПИСОК вариантов, а не результат.
            for (Choice o : options) {
                if (o.payload() == null) {
                    return o;
                }
            }
            return options.get(0);
        }
    }

    @Test
    void десантПредлагаетТолькоГексыСвоейЗоныСтройки() {
        GameState s = game();
        int seat = 0;
        Spy spy = new Spy(seat);
        s.agents = List.of(spy, new Spy(1), new Spy(2), new Spy(3));

        Effects.apply("landing", s, seat, Map.of("count", 2));

        assertFalse(spy.offeredHexes.isEmpty(),
            "десант не предложил ни одного гекса — тест ничего не проверил");
        List<String> zone = Actions.buildableHexes(s, seat);
        for (String hex : spy.offeredHexes) {
            assertTrue(zone.contains(hex),
                "десант предложил гекс " + hex + " вне своей зоны стройки; "
                + "зона: " + zone);
        }
    }

    @Test
    void десантНеПредлагаетГексыЧужихБаз() {
        GameState s = game();
        int seat = 0;
        Spy spy = new Spy(seat);
        s.agents = List.of(spy, new Spy(1), new Spy(2), new Spy(3));

        Effects.apply("landing", s, seat, Map.of("count", 2));

        // Гексы чужих ЦУ — самая опасная часть прежнего поведения: именно туда
        // десант мог поставить войско без единого приказа Движения.
        List<String> чужиеБазы = new ArrayList<>();
        for (var p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            for (var b : p.buildingsOnField()) {
                чужиеБазы.add(b.hexId);
            }
        }
        for (String hex : spy.offeredHexes) {
            assertFalse(чужиеБазы.contains(hex),
                "десант предложил высадку на гекс чужого здания: " + hex);
        }
    }
}
