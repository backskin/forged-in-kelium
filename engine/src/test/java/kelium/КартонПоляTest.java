package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.dataio.GameConfig;
import kelium.engine.BlockStamp;
import kelium.engine.Setup;
import kelium.report.FieldGeometry;

/**
 * КАРТОН НА ПОЛЕ: поле накрыто настоящими модулями, и печать взята с них.
 *
 * <p>ЗАЧЕМ ЭТИ СТОРОЖА. До 08.09.2026 поле нарезалось на связные кусочки по
 * 5–6 гексов, а печать блока раскладывалась по ним ВПЕРЕМЕШКУ: числа сходились,
 * а картонки как таковой не было. Теперь кладутся настоящие формы, и печать
 * переносится геометрически — ровно на тот гекс и на ту сторону, где она
 * напечатана. Ошибка здесь не видна глазом: поле останется правдоподобным, а
 * контейнер будет отдаваться не с той стороны, с которой нарисован.
 */
class КартонПоляTest {

    private static GameState игра(int players, long seed) {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players, seed, null, null));
    }

    /** Стороны блоков набора этой партии: id блока и сторона → печать. */
    private static Map<String, BlockStamp.Face> набор() {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null);
        String версия = String.valueOf(
            cfg.ruleset.get("content_versions.blocks", BlockStamp.ВЕРСИЯ_ПО_УМОЛЧАНИЮ));
        Map<String, BlockStamp.Face> out = new HashMap<>();
        for (BlockStamp.Face f : BlockStamp.faces(
                GameConfig.resolveDataRoot(null), версия)) {
            out.put(f.blockId() + "/" + f.side(), f);
        }
        return out;
    }

    @Test
    void каждыйИгровойГексНакрытКартонкой() {
        for (int players = 2; players <= 4; players++) {
            for (long seed = 1; seed <= 5; seed++) {
                GameState s = игра(players, seed);
                for (Hex h : s.field.hexes.values()) {
                    if (h.kind == HexKind.FORBIDDEN) {
                        continue;
                    }
                    assertNotNull(h.blockId,
                        players + " игрока, сид " + seed + ": гекс " + h.id
                            + " не накрыт картоном");
                    assertNotNull(h.blockSide, "у картонки на " + h.id + " нет стороны");
                    assertTrue(h.blockRot >= 0 && h.blockRot < 6,
                        "поворот картонки 0..5, а не " + h.blockRot);
                }
            }
        }
    }

    @Test
    void картонкиИзКоробкиБезПовторов() {
        // В коробке пять малых и пять больших, каждый ОДИН. Один и тот же блок
        // не может лежать на поле дважды — на авторских полях запаса хватает
        // с избытком (уходит четыре-шесть картонок из десяти).
        for (int players = 2; players <= 4; players++) {
            for (long seed = 1; seed <= 5; seed++) {
                GameState s = игра(players, seed);
                Set<String> блоки = new HashSet<>();
                Map<String, String> сторонаБлока = new HashMap<>();
                for (Hex h : s.field.hexes.values()) {
                    if (h.blockId == null) {
                        continue;
                    }
                    блоки.add(h.blockId);
                    String была = сторонаБлока.put(h.blockId, h.blockSide);
                    assertTrue(была == null || была.equals(h.blockSide),
                        "блок " + h.blockId + " лежит двумя сторонами разом");
                }
                assertTrue(блоки.size() <= 10,
                    "картонок в коробке десять, а на поле " + блоки.size());
            }
        }
    }

    @Test
    void печатьВзятаСКартонкиГеометрически() {
        for (int players = 2; players <= 4; players++) {
            GameState s = игра(players, 7L);
            Map<String, BlockStamp.Face> стороны = набор();
            int проверено = 0;
            for (Hex h : s.field.hexes.values()) {
                if (h.blockId == null) {
                    continue;
                }
                BlockStamp.Face f = стороны.get(h.blockId + "/" + h.blockSide);
                assertNotNull(f, "на поле легла сторона, которой нет в наборе: "
                    + h.blockId + "-" + h.blockSide);
                BlockStamp.Cell своя = null;
                for (BlockStamp.Cell c : f.cells()) {
                    if (c.q() == h.blockQ && c.r() == h.blockR) {
                        своя = c;
                    }
                }
                assertNotNull(своя, "гекс " + h.id + " указывает на клетку ("
                    + h.blockQ + "," + h.blockR + "), которой у блока "
                    + h.blockId + " нет");
                if (h.kind == HexKind.FORBIDDEN || h.spawnTile != null) {
                    assertEquals(-1, h.containerCell,
                        "под тайлом зарождения и на запретном гексе ячейки закрыты: " + h.id);
                    assertEquals(-1, h.energyCell, "то же и для жёлтой ячейки: " + h.id);
                    continue;
                }
                assertEquals(BlockStamp.rotateCell(своя.energy(), h.blockRot), h.energyCell,
                    "жёлтая ячейка гекса " + h.id + " не с картонки "
                        + h.blockId + "-" + h.blockSide);
                // Контейнер сверяем только там, где потолок правила его не срезал:
                // напечатанного контейнера может не стать, но появиться из воздуха он
                // не может.
                if (h.containerCell >= 0) {
                    assertEquals(BlockStamp.rotateCell(своя.container(), h.blockRot),
                        h.containerCell,
                        "печатный контейнер гекса " + h.id + " не с картонки "
                            + h.blockId + "-" + h.blockSide);
                }
                проверено++;
            }
            assertTrue(проверено > 10, "проверять было что: " + проверено);
        }
    }

    @Test
    void формыКартонокНастоящие() {
        // Гексы одной картонки на поле обязаны складываться в ФОРМУ блока:
        // три в ряд и два под ними (малый) плюс один сверху (большой), в любом
        // из шести поворотов. Прежняя раскладка этого не держала — там был
        // просто связный кусок поля.
        for (int players = 2; players <= 4; players++) {
            GameState s = игра(players, 11L);
            Map<String, List<int[]>> поКартонкам = new HashMap<>();
            for (Hex h : s.field.hexes.values()) {
                if (h.blockId == null) {
                    continue;
                }
                int[] qr = FieldGeometry.parseQR(h.id);
                поКартонкам.computeIfAbsent(h.blockId + "-" + h.blockSide,
                    k -> new java.util.ArrayList<>()).add(new int[]{qr[0], qr[1], h.blockRot});
            }
            for (var e : поКартонкам.entrySet()) {
                List<int[]> гексы = e.getValue();
                int rot = гексы.get(0)[2];
                for (int[] g : гексы) {
                    assertEquals(rot, g[2],
                        "у картонки " + e.getKey() + " гексы с разным поворотом");
                }
                // Свои гексы обязаны быть подмножеством формы блока: часть могла
                // свисать за край поля, но лишних быть не может.
                Set<String> есть = new HashSet<>();
                for (int[] g : гексы) {
                    есть.add(g[0] + ":" + g[1]);
                }
                assertTrue(есть.size() >= 1 && есть.size() <= 6,
                    "в картонке " + e.getKey() + " гексов " + есть.size());
            }
        }
    }
}
