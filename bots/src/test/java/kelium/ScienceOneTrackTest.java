package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;

/**
 * ОДНО ДЕЙСТВИЕ НАУКИ — ОДИН ТРЕК (решение дизайнера 21.08.2026).
 *
 * <p>ЗАЧЕМ ПРАВИЛО. Прежде за одно действие можно было шагнуть по разу на
 * КАЖДОМ треке, и накопленные трофеи разом ложились во все три ветки: чем
 * дольше игрок копил, тем выгоднее было одно действие, а выбор ветки не был
 * выбором. Теперь ветки приходится брать разными действиями.
 *
 * <p>КАК ПРОВЕРЯЕТСЯ. Игроку выдаётся заведомо избыточный запас трофеев — так,
 * чтобы денег хватило на шаги по всем трём трекам, — и выполняется ОДНО
 * действие Науки жадным ботом, который берёт всё, что дают. Сколько треков
 * сдвинулось, столько правило и разрешило. Проверяются оба свода: 1.19.0
 * (правила ещё нет — ключ не задан) и 1.20.0. Так видно и что новое правило
 * работает, и что старые своды читаются по-прежнему.
 */
class ScienceOneTrackTest {

    /** Сколько РАЗНЫХ треков сдвинул игрок за одно действие Науки. */
    private static int tracksMovedInOneAction(String ruleset) {
        GameConfig cfg = GameConfig.buildCached(ruleset, 4, 4242L, null, null);
        GameState s = Setup.buildGame(cfg);
        PlayerState p = s.player(0);
        // ЗАПАС ЗАВЕДОМО ИЗБЫТОЧНЫЙ: платить надо трофеями или трофеями, и
        // если денег не хватит, тест померит бедность, а не правило.
        p.resources.add(Resource.TROPHY, 30);

        Agent greedy = Bots.create("hawk", 0, new Random(7), 4);
        // Партия собрана без агентов (тест не играет её целиком), а действие
        // спрашивает решения именно у них.
        s.agents = new java.util.ArrayList<>(java.util.List.of(greedy, greedy, greedy, greedy));
        var before = new java.util.HashMap<>(p.techSteps);
        kelium.engine.Actions.create("science", s)
            .perform(p, new kelium.engine.TurnContext(0, 1), greedy);

        int moved = 0;
        for (String track : s.tech.tracks) {
            if (p.techSteps.getOrDefault(track, 0) > before.getOrDefault(track, 0)) {
                moved++;
            }
        }
        return moved;
    }

    @Test
    void заОдноДействиеНаукиШагаетТолькоОдинТрек() {
        assertEquals(1, tracksMovedInOneAction("1.20.0"),
            "свод 1.20.0 разрешает один трек за действие");
    }

    @Test
    void староеПравилоЧитаетсяКакРаньше() {
        // Без ключа tech.tracks_per_action свод работает по-прежнему: по одному
        // шагу на КАЖДОМ треке. Если бы правило прописали в самом движке, старые
        // записи и замеры перестали бы воспроизводиться.
        int moved = tracksMovedInOneAction("1.19.0");
        assertTrue(moved > 1,
            "в своде 1.19.0 за одно действие берут больше одного трека, а взяли " + moved);
        assertTrue(moved <= List.of("left", "middle", "right").size(),
            "треков не может быть больше, чем их есть");
    }
}
