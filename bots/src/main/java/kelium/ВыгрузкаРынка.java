package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.cards.market.СделкаВКоде;
import kelium.cards.market.СделкиНаРынке3;
import kelium.cards.market.СделкиНаРынке4;

/**
 * ВЫГРУЗКА КОЛОДЫ РЫНКА ИЗ КОДА В YAML.
 *
 * <p>ПОЧЕМУ ОТДЕЛЬНО ОТ {@link ВыгрузкаКаталога}. Общая выгрузка идёт по
 * записям УЖЕ ПОДКЛЮЧЁННОГО набора и накрывает их классами: новых карт, которых
 * в подключённом наборе ещё нет, она попросту не видит. Новый набор рынка
 * рождается целиком в коде, поэтому пишется прямо из списка карт.
 *
 * <p>Запуск: {@code kelium.ВыгрузкаРынка 3.0.0 "почему выгружено"}
 */
public final class ВыгрузкаРынка {

    private ВыгрузкаРынка() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        String версия = args.length > 0 ? args[0] : "3.0.0";
        String зачем = args.length > 1 ? args[1] : "выгружено из кода";

        List<Map<String, Object>> записи = new ArrayList<>();
        List<СделкаВКоде> набор = версия.startsWith("4.") ? СделкиНаРынке4.все()
            : СделкиНаРынке3.все();
        for (СделкаВКоде c : набор) {
            записи.add(new LinkedHashMap<>(c.data()));
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", версия);
        meta.put("type", "market");
        meta.put("выгружено", зачем);
        Map<String, Object> корень = new LinkedHashMap<>();
        корень.put("meta", meta);
        корень.put("market", записи);

        var опции = new org.yaml.snakeyaml.DumperOptions();
        опции.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        опции.setAllowUnicode(true);
        опции.setWidth(100);
        опции.setIndent(2);
        String yaml = new org.yaml.snakeyaml.Yaml(опции).dump(корень);

        String шапка = "# CONTENT: market  version " + версия + "\n"
            + "# ============================================================================\n"
            + "#  ВЫГРУЖЕНО ИЗ КОДА — файл является ЗЕРКАЛОМ классов карт.\n"
            + "#  " + зачем + "\n"
            + "#\n"
            + "#  Править этот файл руками бессмысленно: движок накрывает запись каталога\n"
            + "#  выгрузкой класса. Менять надо класс kelium.cards.market.СделкиНаРынке"
            + (версия.startsWith("4.") ? "4" : "3") + ",\n"
            + "#  а затем выгружать заново (kelium.ВыгрузкаРынка).\n"
            + "# ============================================================================\n";

        Path out = Path.of("data", "cards", "market." + версия + ".yaml");
        Files.writeString(out, шапка + yaml, StandardCharsets.UTF_8);
        System.out.println("записано: " + out + " (" + записи.size() + " карт)");
    }
}
