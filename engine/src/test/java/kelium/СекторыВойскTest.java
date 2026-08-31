package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Shapes;
import kelium.engine.СекторыВойск;
import kelium.support.Fix;

/**
 * ВОЙСКА СТОЯТ НА СЕКТОРАХ ГЕКСА — и цепочка их видит.
 *
 * <p>СВОД: «Жетон занимает секторы: пехота один, техника два смежных; авиация
 * встаёт только в небо на гексе». До 25.08.2026 движок наземным войскам секторов
 * не выдавал, и непрерывное соседство ({@link Shapes}) их не видело: пять карт
 * заданий (o50, o54, o56, o58, o62) были невыполнимы — 451 раздача за 200
 * партий, ноль выполнений.
 *
 * <p>Сторож проверяет ровно то, что тогда молчало: у пехоты один сектор, у
 * техники два СМЕЖНЫХ, у авиации ни одного, и собранная руками цепочка из войск
 * связывает два гекса.
 */
class СекторыВойскTest {

    @Test
    void пехотаЗанимаетОдинСектор() {
        GameState s = Fix.game();
        UnitToken u = Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        List<Integer> секторы = СекторыВойск.секторыЖетона(s, u);
        assertNotNull(секторы, "пехоте обязан достаться сектор");
        assertEquals(1, секторы.size(), "пехота занимает ровно один сектор");
    }

    @Test
    void техникаЗанимаетДваСмежныхСектора() {
        GameState s = Fix.game();
        UnitToken u = Fix.unit(s, 0, UnitType.VEHICLE, s.player(0).startHex);
        List<Integer> секторы = СекторыВойск.секторыЖетона(s, u);
        assertNotNull(секторы, "технике обязана достаться пара секторов");
        assertEquals(2, секторы.size(), "техника занимает два сектора");
        int a = секторы.get(0);
        int b = секторы.get(1);
        assertTrue((a + 1) % 6 == b || (b + 1) % 6 == a,
            "секторы техники обязаны быть СМЕЖНЫМИ, а вышло: " + секторы);
    }

    @Test
    void авиацияЗемлиНеЗанимает() {
        GameState s = Fix.game();
        UnitToken u = Fix.unit(s, 0, UnitType.AIRCRAFT, s.player(0).startHex);
        assertEquals(0, СекторыВойск.секторов(UnitType.AIRCRAFT),
            "авиация встаёт в небо на гексе");
        assertTrue(СекторыВойск.секторыЖетона(s, u) == null,
            "у авиации наземных секторов быть не должно");
    }

    /** Два жетона на одном гексе не делят один сектор. */
    @Test
    void двоеНаГексеСтоятВРазныхСекторах() {
        GameState s = Fix.game();
        String hex = s.player(0).startHex;
        UnitToken a = Fix.unit(s, 0, UnitType.INFANTRY, hex);
        UnitToken b = Fix.unit(s, 0, UnitType.INFANTRY, hex);
        Map<Integer, List<Integer>> раскладка = СекторыВойск.разложить(s, hex);
        List<Integer> ша = раскладка.get(a.uid);
        List<Integer> шб = раскладка.get(b.uid);
        assertNotNull(ша);
        assertNotNull(шб);
        assertFalse(ша.get(0).equals(шб.get(0)),
            "два жетона не могут стоять в одном секторе: " + раскладка);
    }

    /**
     * ЦЕПОЧКА ИЗ ВОЙСК СВЯЗЫВАЕТ ДВА ГЕКСА. Это то самое, чего не могло
     * случиться до правки: узлов у наземных войск не было вовсе.
     */
    @Test
    void цепочкаИзВойскСвязываетДваГекса() {
        GameState s = Fix.game();
        String середина = s.player(0).startHex;
        List<String> соседи = new ArrayList<>(s.field.neighbors(середина));
        assumeTrue(соседи.size() >= 2, "у стартового гекса меньше двух соседей");

        String левый = соседи.get(0);
        String правый = соседи.get(1);
        Fix.unit(s, 0, UnitType.INFANTRY, левый);
        Fix.unit(s, 0, UnitType.INFANTRY, середина);
        Fix.unit(s, 0, UnitType.INFANTRY, правый);

        var узлы = Shapes.ownNodes(s, 0);
        boolean естьУзелВСередине = узлы.stream()
            .anyMatch(n -> n.hexId().equals(середина) && n.cell() >= 0);
        assertTrue(естьУзелВСередине,
            "войско в середине обязано дать наземный узел, а узлов: " + узлы);
    }
}
