package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.dataio.GameConfig;
import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;

/**
 * ВЫГРУЗКА КАТАЛОГА КАРТ ИЗ КОДА В YAML — файл как ЗЕРКАЛО классов.
 *
 * <p>ЗАЧЕМ. У карт, живущих в коде, запись каталога — производная: движок всё
 * равно накрывает её выгрузкой класса ({@code CardRegistry.bindAll}). Но файл
 * читают ЛЮДИ: дизайнер смотрит награды и тексты именно в нём. Стоит поправить
 * награду в классе, не тронув файл, — и файл начинает врать, причём молча. Это
 * ровно тот сорт расхождения, из-за которого карты и переехали в код.
 *
 * <p>ПОЧЕМУ НОВАЯ ВЕРСИЯ, А НЕ ПРАВКА СТАРОЙ. Наборы неизменяемы: прошлый файл
 * остаётся снимком того, что было, и по нему воспроизводятся прошлые партии и
 * замеры. Новый файл — снимок того, что стало.
 *
 * <p>Запуск: {@code kelium.ВыгрузкаКаталога objectives 1.10.0 "почему выгружено"}
 */
public final class ВыгрузкаКаталога {

    private ВыгрузкаКаталога() {
    }

    /**
     * ЧТО БЕРЁТСЯ ИЗ КОДА при слиянии — и ничего кроме.
     *
     * <p>Награды и печатный текст: именно они меняются, когда правят карту, и
     * именно их читает глазами дизайнер. Запись условия НЕ трогается: у карты в
     * коде там человеческий текст, а прежняя запись с предикатом кому-то ещё
     * нужна (см. комментарий у слияния).
     */
    private static final List<String> ПОЛЯ_ИЗ_КОДА =
        List.of("base_reward", "special_reward", "описание", "name");

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        String семейство = args.length > 0 ? args[0] : "objectives";
        String версия = args.length > 1 ? args[1] : "1.10.0";
        String зачем = args.length > 2 ? args[2] : "выгружено из кода";

        // Каталог грузится обычным путём: так карты получают свои записи и
        // накрывают их собой — ровно то, что мы и выгружаем.
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null);
        List<Map<String, Object>> записи = new ArrayList<>();
        List<String> порядок = new ArrayList<>();
        for (Map<String, Object> e : cfg.content.get(семейство).entries) {
            порядок.add(String.valueOf(e.get("id")));
        }
        // ПРЕЖНИЙ ФАЙЛ ЧИТАЕТСЯ СЫРЫМ И БЕРЁТСЯ ЗА ОСНОВУ.
        //
        // Выгрузка «с нуля» из классов теряет всё, чего класс не пишет: старую
        // запись условия предикатом, пометки отсева, признаки дополнений. На вид
        // ничего не менялось (движок всё равно накрывает запись классом), а
        // семь тестов индикаторов заданий сломались — потому что читают файл
        // напрямую, минуя связывание. Поэтому здесь СЛИЯНИЕ: берём прежнюю
        // запись и накрываем ровно тем, что изменилось в коде.
        String откуда = args.length > 3 ? args[3] : null;
        Map<String, Map<String, Object>> прежние = new LinkedHashMap<>();
        if (откуда != null) {
            var прежний = kelium.dataio.ContentSet.load(семейство, откуда,
                GameConfig.resolveDataRoot(null));
            for (Map<String, Object> e : прежний.entries) {
                прежние.put(String.valueOf(e.get("id")), e);
            }
        }
        for (String id : порядок) {
            Card c = "objectives".equals(семейство)
                ? CardRegistry.objective(id) : CardRegistry.arsenal(id);
            Map<String, Object> запись = new LinkedHashMap<>(
                прежние.getOrDefault(id, Map.of()));
            if (c == null) {
                // Карта без кода — берём запись как есть, чтобы не потерять её.
                if (запись.isEmpty()) {
                    запись.putAll(cfg.content.get(семейство).byId(id));
                }
                записи.add(запись);
                continue;
            }
            // НАКРЫВАЕМ ТОЛЬКО ТЕМ, ЧТО МЕНЯЛОСЬ, а не всей выгрузкой класса.
            //
            // Полная выгрузка заменяет и ЗАПИСЬ УСЛОВИЯ: у карты в коде там
            // человеческий текст вместо предиката с параметрами. Движку это
            // безразлично (он всё равно спрашивает карту), а семь тестов
            // индикаторов заданий на таком файле падают — значит форму записи
            // читает кто-то ещё, и ломать её ради косметики нельзя. Правим
            // ровно то, что и правили: награды и печатный текст.
            Map<String, Object> изКода = c.data();
            if (откуда == null) {
                // ПРЕЖНЕЙ ВЕРСИИ НЕ ЗАДАНО — пишем карту целиком, как её видит
                // код. Нужно для документов: там условие должно стоять
                // человеческим текстом, а не предикатом с параметрами.
                записи.add(new LinkedHashMap<>(изКода));
                continue;
            }
            for (String ключ : ПОЛЯ_ИЗ_КОДА) {
                if (изКода.containsKey(ключ)) {
                    запись.put(ключ, изКода.get(ключ));
                } else {
                    запись.remove(ключ);
                }
            }
            записи.add(запись);
        }

        Map<String, Object> корень = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", версия);
        meta.put("type", семейство);
        meta.put("выгружено", зачем);
        корень.put("meta", meta);
        корень.put(семейство, записи);

        var опции = new org.yaml.snakeyaml.DumperOptions();
        опции.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        опции.setAllowUnicode(true);          // русские тексты пишем как есть
        опции.setWidth(100);
        опции.setIndent(2);
        String yaml = new org.yaml.snakeyaml.Yaml(опции).dump(корень);

        String шапка = "# CONTENT: " + семейство + "  version " + версия + "\n"
            + "# ============================================================================\n"
            + "#  ВЫГРУЖЕНО ИЗ КОДА — файл является ЗЕРКАЛОМ классов карт.\n"
            + "#  " + зачем + "\n"
            + "#\n"
            + "#  Править этот файл руками бессмысленно: у карт, живущих в коде, движок\n"
            + "#  накрывает запись каталога выгрузкой класса. Менять надо класс, а затем\n"
            + "#  выгружать заново (kelium.ВыгрузкаКаталога).\n"
            + "# ============================================================================\n";
        Path out = Path.of("data", "cards", семейство + "." + версия + ".yaml");
        Files.writeString(out, шапка + yaml, StandardCharsets.UTF_8);
        System.out.println("записей: " + записи.size() + " -> " + out.toAbsolutePath());
    }
}
