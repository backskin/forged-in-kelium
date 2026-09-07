package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.engine.BlockStamp;

/**
 * ЖЁСТКИЕ ПРАВИЛА НАБОРОВ БЛОКОВ 5.* — спецификация дизайнера 07.09.2026.
 *
 * <p>ЗАЧЕМ ОТДЕЛЬНЫЙ СТОРОЖ, если генератор проверяет себя сам. Потому что
 * проверять себя тем же кодом, которым генерировал, — не проверка: у прошлого
 * набора (3.*.*) собственные проверки молчали, а в нём было 480 пустых гексов и
 * все большие стороны носили один и тот же набор секторов энергии. Здесь файл
 * читается заново, через движок, и правила пересчитываются с нуля.
 *
 * <p>ЧТО ТРЕБУЕТ СПЕЦИФИКАЦИЯ:
 * <ol>
 *   <li>ни на одном гексе не пусто: нет энергии — обязан быть контейнер;</li>
 *   <li>энергия на 4 гексах из 5 (малый блок) и на 5 из 6 (большой);</li>
 *   <li>контейнеры на 3 из 5 и на 4 из 6, и ОДИН из них — в ячейке авиации;</li>
 *   <li>расположение энергии и контейнеров у любой пары сторон одного размера
 *       совпадает не более чем на ОДНОМ гексе.</li>
 * </ol>
 */
class ЭталонныйНаборБлоковTest {

    private static final String ЭТАЛОН = "5.0.0";

    private static List<BlockStamp.Face> стороны() {
        BlockStamp.resetCache();
        List<BlockStamp.Face> все =
            BlockStamp.faces(GameConfig.resolveDataRoot(null), ЭТАЛОН);
        assertTrue(!все.isEmpty(), "эталонный набор блоков " + ЭТАЛОН + " не прочитался");
        return все;
    }

    private static int гексов(BlockStamp.Face f) {
        return "small".equals(f.kind()) ? 5 : 6;
    }

    @Test
    void двадцатьСторонИтаЖеГеометрия() {
        List<BlockStamp.Face> все = стороны();
        assertEquals(20, все.size(), "20 сторон: по две у каждого из десяти блоков");
        int малых = 0;
        for (BlockStamp.Face f : все) {
            assertEquals(гексов(f), f.size(),
                "сторона " + f.blockId() + f.side() + ": не то число гексов");
            if ("small".equals(f.kind())) {
                малых++;
            }
        }
        assertEquals(10, малых, "малых сторон должно быть десять");
    }

    /** ГЛАВНОЕ ТРЕБОВАНИЕ: пустых гексов нет ни одного. Точка. */
    @Test
    void пустыхГексовНетНиОдного() {
        List<String> пустые = new ArrayList<>();
        for (BlockStamp.Face f : стороны()) {
            for (int i = 0; i < f.cells().size(); i++) {
                BlockStamp.Cell c = f.cells().get(i);
                if (c.container() < 0 && c.energy() < 0) {
                    пустые.add(f.blockId() + f.side() + " гекс " + (i + 1));
                }
            }
        }
        assertTrue(пустые.isEmpty(),
            "гекс без энергии обязан несести контейнер, а пустых нашлось "
                + пустые.size() + ": " + пустые);
    }

    @Test
    void числоМетокПоСпецификации() {
        for (BlockStamp.Face f : стороны()) {
            boolean малый = "small".equals(f.kind());
            int энергий = 0;
            int контейнеров = 0;
            int вНебе = 0;
            for (BlockStamp.Cell c : f.cells()) {
                if (c.energy() >= 0) {
                    энергий++;
                }
                if (c.container() >= 0) {
                    контейнеров++;
                }
                if (c.container() == BlockStamp.AIR) {
                    вНебе++;
                }
                assertTrue(c.energy() != BlockStamp.AIR,
                    f.blockId() + f.side() + ": энергия попала в ячейку авиации");
                assertTrue(c.container() < 0 || c.container() != c.energy(),
                    f.blockId() + f.side() + ": контейнер и энергия в одной ячейке");
            }
            assertEquals(малый ? 4 : 5, энергий,
                f.blockId() + f.side() + ": ячеек энергии должно быть "
                    + (малый ? "4 из 5" : "5 из 6"));
            assertEquals(малый ? 3 : 4, контейнеров,
                f.blockId() + f.side() + ": контейнеров должно быть "
                    + (малый ? "3 из 5" : "4 из 6"));
            assertEquals(1, вНебе,
                f.blockId() + f.side() + ": в ячейке авиации обязан быть ровно "
                    + "один контейнер");
        }
    }

    @Test
    void секторыВнутриСтороныНеПовторяются() {
        for (BlockStamp.Face f : стороны()) {
            Set<Integer> к = new HashSet<>();
            Set<Integer> э = new HashSet<>();
            for (BlockStamp.Cell c : f.cells()) {
                if (c.container() >= 0) {
                    assertTrue(к.add(c.container()),
                        f.blockId() + f.side() + ": сектор контейнера "
                            + c.container() + " повторился");
                }
                if (c.energy() >= 0) {
                    assertTrue(э.add(c.energy()),
                        f.blockId() + f.side() + ": сектор энергии "
                            + c.energy() + " повторился");
                }
            }
        }
    }

    /**
     * ПАРНОЕ ТРЕБОВАНИЕ: у любых двух сторон одного размера расположение
     * совпадает не больше чем на одном гексе — отдельно по контейнерам и
     * отдельно по энергии.
     *
     * <p>Совпадением считается один и тот же сектор на одном и том же по счёту
     * гексе, И ОТСУТСТВИЕ метки тоже: «здесь ни у той, ни у этой ничего нет» —
     * это ровно такое же одинаковое расположение.
     */
    @Test
    void любаяПараСторонРасходитсяВсемиГексамиКромеОдного() {
        List<BlockStamp.Face> все = стороны();
        List<String> жалобы = new ArrayList<>();
        for (int i = 0; i < все.size(); i++) {
            for (int j = i + 1; j < все.size(); j++) {
                BlockStamp.Face a = все.get(i);
                BlockStamp.Face b = все.get(j);
                if (!a.kind().equals(b.kind())) {
                    continue;         // разный размер — сравнивать нечего
                }
                int кон = 0;
                int эн = 0;
                for (int k = 0; k < a.cells().size(); k++) {
                    if (a.cells().get(k).container() == b.cells().get(k).container()) {
                        кон++;
                    }
                    if (a.cells().get(k).energy() == b.cells().get(k).energy()) {
                        эн++;
                    }
                }
                String пара = a.blockId() + a.side() + " и " + b.blockId() + b.side();
                if (кон > 1) {
                    жалобы.add(пара + ": контейнеры совпали на " + кон + " гексах");
                }
                if (эн > 1) {
                    жалобы.add(пара + ": энергия совпала на " + эн + " гексах");
                }
            }
        }
        assertTrue(жалобы.isEmpty(), "пар с лишним совпадением " + жалобы.size()
            + ": " + жалобы);
    }

    /**
     * СВОД ИГРАЕТ ЭТАЛОН, а не авторский набор. Ключ
     * {@code content_versions.blocks} — как у всякого прочего содержимого;
     * своды без него читают 1.4.0 и играются в точности как играли.
     */
    @Test
    void действующийСводИграетЭталон() {
        var rules = GameConfig.build(GameConfig.DEFAULT_RULESET, 4, 1L, null, null).ruleset;
        assertEquals(ЭТАЛОН,
            String.valueOf(rules.get("content_versions.blocks", "1.4.0")),
            "действующий свод обязан играть эталонный набор блоков");
    }

    // ======================================================================
    //  ВСЯ СЕТКА 4.* — правила одни на все наборы, числа у каждого свои
    // ======================================================================

    /** Все наборы семейства 4.*, что лежат в данных. */
    private static List<String> сеткаНаДиске() {
        java.nio.file.Path папка = GameConfig.resolveDataRoot(null).resolve("blocks");
        List<String> out = new ArrayList<>();
        try (var поток = java.nio.file.Files.list(папка)) {
            поток.map(p -> p.getFileName().toString())
                .filter(n -> n.startsWith("blocks.5.") && n.endsWith(".yaml"))
                .map(n -> n.substring("blocks.".length(), n.length() - ".yaml".length()))
                .sorted()
                .forEach(out::add);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("не прочитать папку блоков", e);
        }
        assertTrue(!out.isEmpty(), "наборов семейства 5.* нет ни одного");
        return out;
    }

    /**
     * ПРАВИЛА ОДНИ НА ВСЮ СЕТКУ. Числа меток у сочетаний разные — их и берём из
     * самого набора, по первой стороне каждого размера, — а вот всё остальное
     * обязано держаться у каждого: пустых гексов нет, ровно один контейнер в
     * ячейке авиации, секторы внутри стороны не повторяются, и любая пара
     * сторон одного размера расходится всеми гексами кроме одного.
     */
    @Test
    void каждыйНаборСеткиДержитВсеПравила() {
        List<String> жалобы = new ArrayList<>();
        for (String версия : сеткаНаДиске()) {
            BlockStamp.resetCache();
            List<BlockStamp.Face> все =
                BlockStamp.faces(GameConfig.resolveDataRoot(null), версия);
            if (все.size() != 20) {
                жалобы.add(версия + ": сторон " + все.size() + ", а надо 20");
                continue;
            }
            java.util.Map<String, Integer> эталонЭнергий = new java.util.HashMap<>();
            java.util.Map<String, Integer> эталонКонтейнеров = new java.util.HashMap<>();
            for (BlockStamp.Face f : все) {
                int эн = 0;
                int кон = 0;
                int небо = 0;
                Set<Integer> секторыК = new HashSet<>();
                Set<Integer> секторыЭ = new HashSet<>();
                for (BlockStamp.Cell c : f.cells()) {
                    if (c.container() < 0 && c.energy() < 0) {
                        жалобы.add(версия + " " + f.blockId() + f.side() + ": ПУСТОЙ ГЕКС");
                    }
                    if (c.energy() >= 0) {
                        эн++;
                        if (!секторыЭ.add(c.energy())) {
                            жалобы.add(версия + " " + f.blockId() + f.side()
                                + ": сектор энергии повторился");
                        }
                    }
                    if (c.container() >= 0) {
                        кон++;
                        if (!секторыК.add(c.container())) {
                            жалобы.add(версия + " " + f.blockId() + f.side()
                                + ": сектор контейнера повторился");
                        }
                    }
                    if (c.container() == BlockStamp.AIR) {
                        небо++;
                    }
                    if (c.energy() == BlockStamp.AIR) {
                        жалобы.add(версия + " " + f.blockId() + f.side()
                            + ": энергия в ячейке авиации");
                    }
                    if (c.container() >= 0 && c.container() == c.energy()) {
                        жалобы.add(версия + " " + f.blockId() + f.side()
                            + ": контейнер и энергия в одной ячейке");
                    }
                }
                if (небо != 1) {
                    жалобы.add(версия + " " + f.blockId() + f.side()
                        + ": контейнеров в ячейке авиации " + небо + ", а надо один");
                }
                // Числа меток одинаковы у всех сторон одного размера — это и есть
                // «сочетание» набора; какое именно, набор объявляет сам.
                Integer былоЭ = эталонЭнергий.putIfAbsent(f.kind(), эн);
                if (былоЭ != null && былоЭ != эн) {
                    жалобы.add(версия + " " + f.blockId() + f.side() + ": ячеек энергии "
                        + эн + ", а у прочих сторон этого размера " + былоЭ);
                }
                Integer былоК = эталонКонтейнеров.putIfAbsent(f.kind(), кон);
                if (былоК != null && былоК != кон) {
                    жалобы.add(версия + " " + f.blockId() + f.side() + ": контейнеров "
                        + кон + ", а у прочих сторон этого размера " + былоК);
                }
            }
            for (int i = 0; i < все.size(); i++) {
                for (int j = i + 1; j < все.size(); j++) {
                    BlockStamp.Face a = все.get(i);
                    BlockStamp.Face b = все.get(j);
                    if (!a.kind().equals(b.kind())) {
                        continue;
                    }
                    int кон = 0;
                    int эн = 0;
                    for (int k = 0; k < a.cells().size(); k++) {
                        if (a.cells().get(k).container() == b.cells().get(k).container()) {
                            кон++;
                        }
                        if (a.cells().get(k).energy() == b.cells().get(k).energy()) {
                            эн++;
                        }
                    }
                    if (кон > 1 || эн > 1) {
                        жалобы.add(версия + " " + a.blockId() + a.side() + " и "
                            + b.blockId() + b.side() + ": совпало контейнеров " + кон
                            + ", энергии " + эн);
                    }
                }
            }
        }
        assertTrue(жалобы.isEmpty(), "нарушений " + жалобы.size() + ": " + жалобы);
    }
}
