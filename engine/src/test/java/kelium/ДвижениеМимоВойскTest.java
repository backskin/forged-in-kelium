package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Actions;
import kelium.support.Fix;

/**
 * ДВИЖЕНИЕ МИМО ЧУЖИХ ВОЙСК (решение дизайнера 23.09.2026): наземные не заходят
 * на гекс с чужими наземными войсками; чужая авиация наземным не мешает.
 */
class ДвижениеМимоВойскTest {

    @Test
    void чужаяАвиацияНеЗапираетЧужаяПехотаЗапирает() {
        GameState s = Fix.game();
        String свой = s.player(0).startHex;
        String сосед = Fix.freeNeighbour(s, свой);
        UnitToken пехота = Fix.unit(s, 0, UnitType.INFANTRY, свой);
        UnitToken самолёт = Fix.unit(s, 1, UnitType.AIRCRAFT, сосед);
        s.field.get(сосед).airToken = самолёт.uid;
        assertTrue(Actions.canEnterHex(s, пехота, сосед, 0),
            "над чужой авиацией пехота проходит");
        Fix.unit(s, 1, UnitType.INFANTRY, сосед);
        assertFalse(Actions.canEnterHex(s, пехота, сосед, 0),
            "на гекс с чужой пехотой не зайти");
    }
}
