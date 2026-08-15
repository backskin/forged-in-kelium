package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Figures;
import kelium.support.Fix;

/**
 * РИСУНОК ИЗ ЖЕТОНОВ — фигуру можно ПОВОРАЧИВАТЬ и НЕЛЬЗЯ ОТРАЖАТЬ
 * (правило дизайнера 13.08.2026).
 *
 * <p>Различие не формальное: на гексовой сетке зеркальный «уголок» — другая фигура.
 * За столом игрок карту крутит, но лицом вниз не переворачивает, поэтому движок
 * обязан принимать шесть поворотов и отвергать отражение.
 */
class FiguresTest {

    /** Поставить свои жетоны на перечисленные гексы. */
    private static void put(GameState s, int seat, List<String> hexes) {
        int uid = 7000;
        for (String hx : hexes) {
            UnitToken u = new UnitToken(UnitType.INFANTRY, seat, 1, uid++);
            u.setHexId(hx);
            s.player(seat).units.add(u);
        }
    }

    /** Найти на поле цепочку из трёх гексов подряд в одну сторону. */
    private static List<String> lineOfThree(GameState s, int side) {
        for (Hex h : s.field.hexes.values()) {
            String b = h.neighborBySide[side];
            if (b == null) {
                continue;
            }
            String c = s.field.get(b).neighborBySide[side];
            if (c != null) {
                return List.of(h.id, b, c);
            }
        }
        return List.of();
    }

    @Test
    void turningIsSixPositionsAndKeepsPathLength() {
        List<int[]> cells = Figures.cellsOf(Map.of("cells",
            List.of(List.of(), List.of(0), List.of(0, 1))));
        assertEquals(3, cells.size(), "три гекса в фигуре");
        // Поворот сдвигает номера сторон, длину путей не меняет.
        List<int[]> t = Figures.turned(cells, 2);
        assertEquals(2, t.get(1)[0], "сторона 0 после двух поворотов — сторона 2");
        assertEquals(3, t.get(2)[1], "сторона 1 после двух поворотов — сторона 3");
        // Шесть поворотов возвращают исходное.
        assertEquals(0, Figures.turned(cells, 6).get(1)[0], "шесть поворотов — круг");
    }

    @Test
    void lineOfThreeIsFoundInAnyRotation() {
        for (int side = 0; side < 6; side++) {
            GameState s = Fix.game(4, 4242L);
            List<String> line = lineOfThree(s, side);
            if (line.isEmpty()) {
                continue;
            }
            put(s, 0, line);
            Map<String, Object> figure = Map.of(
                "cells", List.of(List.of(), List.of(0), List.of(0, 0)),
                "what", "unit:any", "name", "линия из трёх");
            assertTrue(Figures.satisfied(s, 0, figure),
                "линия по стороне " + side + " обязана найтись поворотом фигуры");
        }
    }

    @Test
    void figureIsNotSatisfiedByScatteredTokens() {
        GameState s = Fix.game(4, 4242L);
        // Три жетона на гексы, которые НЕ образуют линию: берём три первых гекса
        // поля, между которыми нет цепочки в одну сторону.
        List<String> scattered = new ArrayList<>();
        for (Hex h : s.field.hexes.values()) {
            if (scattered.size() >= 3) {
                break;
            }
            boolean lonely = true;
            for (String near : scattered) {
                if (s.field.neighbors(h.id).contains(near)) {
                    lonely = false;
                    break;
                }
            }
            if (lonely) {
                scattered.add(h.id);
            }
        }
        put(s, 0, scattered);
        Map<String, Object> figure = Map.of(
            "cells", List.of(List.of(), List.of(0), List.of(0, 0)),
            "what", "unit:any");
        assertFalse(Figures.satisfied(s, 0, figure),
            "разбросанные жетоны фигуру не образуют");
    }

    /**
     * ОТРАЖЕНИЕ НЕ ПРИНИМАЕТСЯ. «Уголок» с поворотом влево и «уголок» с поворотом
     * вправо — разные фигуры; поставив зеркальный, задание выполнить нельзя.
     */
    @Test
    void mirroredFigureIsRejected() {
        GameState s = Fix.game(4, 4242L);
        // Строим ЗЕРКАЛЬНЫЙ уголок: опора, шаг в сторону 0, затем шаг в сторону 5
        // (карта требует 0 затем 1 — это поворот в другую сторону).
        String a = null;
        String b = null;
        String c = null;
        for (Hex h : s.field.hexes.values()) {
            String n0 = h.neighborBySide[0];
            if (n0 == null) {
                continue;
            }
            String n5 = s.field.get(n0).neighborBySide[5];
            if (n5 != null) {
                a = h.id;
                b = n0;
                c = n5;
                break;
            }
        }
        if (a == null) {
            return;   // на этом поле такого места нет — проверять нечего
        }
        put(s, 0, List.of(a, b, c));
        Map<String, Object> corner = Map.of(
            "cells", List.of(List.of(), List.of(0), List.of(0, 1)),
            "what", "unit:any", "name", "уголок");
        // ВАЖНАЯ ОГОВОРКА, найденная замером: уголок ИЗ ТРЁХ гексов на гексовой
        // сетке СИММЕТРИЧЕН — его зеркало совпадает с ним же при повороте (достаточно
        // взять опорой другой жетон). Значит запрет отражений имеет силу только для
        // фигур из ЧЕТЫРЁХ и более гексов, а трёхгексовые уголки зеркалить попросту
        // нечем. Поэтому здесь проверяется именно это, а не выдуманный запрет.
        assertTrue(Figures.satisfied(s, 0, corner),
            "уголок из трёх гексов симметричен: его «зеркало» — это он же, повёрнутый");

        // А вот фигура из ЧЕТЫРЁХ гексов с изломом уже киральна: её зеркало не
        // получается никаким поворотом, и движок обязан его отвергнуть.
        String d = s.field.get(c).neighborBySide[5];
        if (d == null) {
            return;
        }
        put(s, 0, List.of(d));
        Map<String, Object> chiral = Map.of(
            "cells", List.of(List.of(), List.of(0), List.of(0, 1), List.of(0, 1, 1)),
            "what", "unit:any", "name", "крюк из четырёх");
        assertFalse(Figures.satisfied(s, 0, chiral),
            "зеркальный крюк из четырёх гексов не считается выполненным");
    }
}
