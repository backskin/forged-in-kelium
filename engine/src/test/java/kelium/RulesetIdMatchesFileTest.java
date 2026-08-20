package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;

/**
 * ИМЯ ФАЙЛА СВОДА И ЕГО ВНУТРЕННИЙ id ОБЯЗАНЫ СОВПАДАТЬ.
 *
 * <p>ЗАЧЕМ ЭТОТ СТОРОЖ. Новый свод делают КОПИЕЙ предыдущего — это принято и
 * правильно, версии неизменяемы. Но в копии легко забыть поправить {@code
 * meta.id}, и тогда файл называется 1.18.0, а движок сообщает всем, что играет
 * по 1.17.0.
 *
 * <p>Ошибка тихая и очень дорогая: именно этот id уходит в ЗАПИСЬ ПАРТИИ, а по
 * записи потом разбирают баги. Найдено 20.08.2026 — свод 1.18.0 объявлял себя
 * 1.17.0, и полный лог партии врал бы о версии правил, по которой она сыграна.
 * Замер «до и после» на разных сводах при этом сравнивал бы одно и то же.
 */
class RulesetIdMatchesFileTest {

    private static Path rulesetsDir() {
        return GameConfig.resolveDataRoot(null).resolve("rulesets");
    }

    @Test
    void каждыйСводНазываетСебяСвоимИменем() throws IOException {
        Path dir = rulesetsDir();
        assertTrue(Files.isDirectory(dir), "нет каталога сводов: " + dir);
        List<String> беда = new ArrayList<>();
        int проверено = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".yaml"))
                    .sorted().toList()) {
                String stem = f.getFileName().toString().replaceAll("\\.yaml$", "");
                String id = metaId(f);
                проверено++;
                if (id == null) {
                    continue;   // id не задан — движок возьмёт имя файла, это законно
                }
                if (!id.equals(stem)) {
                    беда.add(stem + ".yaml объявляет себя " + id);
                }
            }
        }
        assertTrue(проверено > 0, "не нашлось ни одного свода");
        assertEquals(List.of(), беда,
            "свод называет себя не своим именем — это уйдёт в запись партии:\n"
                + String.join("\n", беда));
    }

    /** Значение {@code meta.id} без разбора всего YAML: нужна одна строка. */
    private static String metaId(Path file) throws IOException {
        boolean inMeta = false;
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("meta:")) {
                inMeta = true;
                continue;
            }
            if (inMeta) {
                if (!line.startsWith(" ") && !line.isBlank()) {
                    return null;            // раздел meta кончился, id не встретился
                }
                String t = line.trim();
                if (t.startsWith("id:")) {
                    return t.substring(3).trim().replace("\"", "");
                }
            }
        }
        return null;
    }
}
