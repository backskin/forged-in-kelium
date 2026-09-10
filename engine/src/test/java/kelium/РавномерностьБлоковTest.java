package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.engine.BlockStamp;

/**
 * РАВНОМЕРНОСТЬ НАБОРОВ БЛОКОВ 5.* — то, чего не хватало семейству 4.*.
 *
 * <p>ЗАЧЕМ. Наборы 4.* выполняли все жёсткие правила и всё равно оказались
 * браком: дизайнер увидел глазом, что метки садятся неровно. Замер подтвердил —
 * сектор энергии у малых блоков занимался от 4 до 8 раз при идеале 6.7, а у
 * больших блоков ПЕРВЫЙ гекс не бывал «только контейнером» ни разу, тогда как
 * остальные — по два раза. Жёсткие правила это пропускали, потому что про
 * равномерность в них не было ни слова.
 *
 * <p>ЧТО ПРОВЕРЯЕТСЯ. Три гистограммы, каждая — отдельная жалоба дизайнера:
 * <ol>
 *   <li><b>секторы в целом</b>: как часто каждый из шести боковых секторов занят
 *       энергией и как часто контейнером;</li>
 *   <li><b>секторы по МЕСТУ гекса</b>: то же самое, но отдельно для каждого по
 *       счёту гекса стороны — здесь и жил самый заметный перекос;</li>
 *   <li><b>что на гексе</b>: как по местам разложены «только контейнер»,
 *       «только энергия» и «и то и то».</li>
 * </ol>
 *
 * <p>МЕРА — РАЗМАХ, наибольшее минус наименьшее. Десять сторон не делятся ни на
 * шесть, ни на семь, поэтому идеал недостижим точно, и требовать нуля нельзя:
 * порог — ЕДИНИЦА. Это ровно тот предел, ниже которого арифметика не пускает, и
 * ровно то, чего не было у 4.* (там доходило до четырёх).
 */
class РавномерностьБлоковTest {

    private static final int ПОРОГ_РАЗМАХА = 1;
    private static final int СЕКТОРОВ = 6;          // боковые 0..5
    private static final String ЭТАЛОН = "5.0.0";

    private static List<String> сетка() {
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
        // Набор в игре ОДИН: варианты, из которых его выбирали, удалены вместе
        // с прочим старьём. Проверяется тот, что лежит в данных.
        assertTrue(!out.isEmpty(), "набора семейства 5.* нет в данных вовсе");
        return out;
    }

    private static List<BlockStamp.Face> стороны(String версия, String размер) {
        BlockStamp.resetCache();
        List<BlockStamp.Face> все =
            BlockStamp.faces(GameConfig.resolveDataRoot(null), версия);
        List<BlockStamp.Face> свои = new ArrayList<>();
        for (BlockStamp.Face f : все) {
            if (размер.equals(f.kind())) {
                свои.add(f);
            }
        }
        return свои;
    }

    private static int размах(int[] счёт) {
        int мин = Integer.MAX_VALUE;
        int макс = Integer.MIN_VALUE;
        for (int v : счёт) {
            мин = Math.min(мин, v);
            макс = Math.max(макс, v);
        }
        return макс - мин;
    }

    /** Каждый боковой сектор занят примерно поровну — по набору в целом. */
    @Test
    void секторыВЦеломРовные() {
        List<String> жалобы = new ArrayList<>();
        for (String версия : сетка()) {
            for (String размер : List.of("small", "big")) {
                int[] эн = new int[СЕКТОРОВ];
                int[] кон = new int[СЕКТОРОВ];
                for (BlockStamp.Face f : стороны(версия, размер)) {
                    for (BlockStamp.Cell c : f.cells()) {
                        if (c.energy() >= 0 && c.energy() < СЕКТОРОВ) {
                            эн[c.energy()]++;
                        }
                        if (c.container() >= 0 && c.container() < СЕКТОРОВ) {
                            кон[c.container()]++;
                        }
                    }
                }
                if (размах(эн) > ПОРОГ_РАЗМАХА) {
                    жалобы.add(версия + " " + размер + ": энергия по секторам "
                        + Arrays.toString(эн) + ", размах " + размах(эн));
                }
                if (размах(кон) > ПОРОГ_РАЗМАХА) {
                    жалобы.add(версия + " " + размер + ": контейнеры по секторам "
                        + Arrays.toString(кон) + ", размах " + размах(кон));
                }
            }
        }
        assertTrue(жалобы.isEmpty(), "перекосов " + жалобы.size() + ": " + жалобы);
    }

    /**
     * НА КАЖДОМ ПО СЧЁТУ ГЕКСЕ секторы тоже расходятся ровно.
     *
     * <p>Именно здесь у 4.* был самый заметный перекос: на одном месте один
     * сектор занимался три раза из девяти, а четыре сектора — ни разу.
     */
    @Test
    void секторыПоМестуГексаРовные() {
        List<String> жалобы = new ArrayList<>();
        for (String версия : сетка()) {
            for (String размер : List.of("small", "big")) {
                List<BlockStamp.Face> свои = стороны(версия, размер);
                if (свои.isEmpty()) {
                    continue;
                }
                int мест = свои.get(0).cells().size();
                for (int место = 0; место < мест; место++) {
                    int[] эн = new int[СЕКТОРОВ];
                    int[] кон = new int[СЕКТОРОВ];
                    for (BlockStamp.Face f : свои) {
                        BlockStamp.Cell c = f.cells().get(место);
                        if (c.energy() >= 0 && c.energy() < СЕКТОРОВ) {
                            эн[c.energy()]++;
                        }
                        if (c.container() >= 0 && c.container() < СЕКТОРОВ) {
                            кон[c.container()]++;
                        }
                    }
                    if (размах(эн) > ПОРОГ_РАЗМАХА) {
                        жалобы.add(версия + " " + размер + " место " + (место + 1)
                            + ": энергия " + Arrays.toString(эн));
                    }
                    if (размах(кон) > ПОРОГ_РАЗМАХА) {
                        жалобы.add(версия + " " + размер + " место " + (место + 1)
                            + ": контейнеры " + Arrays.toString(кон));
                    }
                }
            }
        }
        assertTrue(жалобы.isEmpty(), "перекосов " + жалобы.size() + ": " + жалобы);
    }

    /**
     * «ТОЛЬКО КОНТЕЙНЕР», «ТОЛЬКО ЭНЕРГИЯ» и «И ТО И ТО» разложены по местам
     * поровну — жалоба дизайнера «какой-то гекс сильно чаще оказывается всего
     * лишь с одним элементом».
     */
    @Test
    void чтоНаГексеРовноПоМестам() {
        List<String> жалобы = new ArrayList<>();
        String[] имена = {"только контейнер", "только энергия", "и то и то"};
        for (String версия : сетка()) {
            for (String размер : List.of("small", "big")) {
                List<BlockStamp.Face> свои = стороны(версия, размер);
                if (свои.isEmpty()) {
                    continue;
                }
                int мест = свои.get(0).cells().size();
                int[][] счёт = new int[3][мест];
                for (BlockStamp.Face f : свои) {
                    for (int место = 0; место < мест; место++) {
                        BlockStamp.Cell c = f.cells().get(место);
                        int вид = c.energy() < 0 ? 0 : c.container() < 0 ? 1 : 2;
                        счёт[вид][место]++;
                    }
                }
                for (int вид = 0; вид < 3; вид++) {
                    if (размах(счёт[вид]) > ПОРОГ_РАЗМАХА) {
                        жалобы.add(версия + " " + размер + ": «" + имена[вид]
                            + "» по местам " + Arrays.toString(счёт[вид])
                            + ", размах " + размах(счёт[вид]));
                    }
                }
            }
        }
        assertTrue(жалобы.isEmpty(), "перекосов " + жалобы.size() + ": " + жалобы);
    }

    /** Ячейка авиации по местам тоже не должна липнуть к одному гексу. */
    @Test
    void ячейкаАвиацииРовноПоМестам() {
        List<String> жалобы = new ArrayList<>();
        for (String версия : сетка()) {
            for (String размер : List.of("small", "big")) {
                List<BlockStamp.Face> свои = стороны(версия, размер);
                if (свои.isEmpty()) {
                    continue;
                }
                int мест = свои.get(0).cells().size();
                int[] счёт = new int[мест];
                for (BlockStamp.Face f : свои) {
                    for (int место = 0; место < мест; место++) {
                        if (f.cells().get(место).container() == BlockStamp.AIR) {
                            счёт[место]++;
                        }
                    }
                }
                if (размах(счёт) > ПОРОГ_РАЗМАХА) {
                    жалобы.add(версия + " " + размер + ": ячейка авиации по местам "
                        + Arrays.toString(счёт) + ", размах " + размах(счёт));
                }
            }
        }
        assertTrue(жалобы.isEmpty(), "перекосов " + жалобы.size() + ": " + жалобы);
    }

    /** Действующий свод играет эталон нового семейства. */
    @Test
    void сводИграетЭталонПятогоСемейства() {
        var rules = GameConfig.build(GameConfig.DEFAULT_RULESET, 4, 1L, null, null).ruleset;
        assertEquals(ЭТАЛОН,
            String.valueOf(rules.get("content_versions.blocks", "5.0.0")),
            "действующий свод обязан играть эталон " + ЭТАЛОН);
    }
}
