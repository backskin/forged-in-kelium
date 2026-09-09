package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.dataio.GameConfig;

/**
 * ВЫГРУЗКА ЗАДАНИЙ ЦЕЛИКОМ — снимок того, чем движок играет ПРЯМО СЕЙЧАС.
 *
 * <p>Чем отличается от {@link ВыгрузкаКаталога}: та сливает в файл набора
 * несколько полей (награды, имя, описание) и потому годится, чтобы файл не врал
 * дизайнеру. Здесь берётся ВСЯ запись каждой карты, включая верх (утиль) и его
 * параметры, и пишется отдельным файлом для таблиц. Набор в {@code data/cards}
 * при этом не трогается: он неизменяем.
 *
 * <p>ЗАЧЕМ ЭТО НУЖНО. Карты заданий живут в коде, а запись каталога —
 * производная от класса. Таблица, собранная по файлу набора, показывает ту
 * версию, которую в последний раз выгрузили; таблица, собранная по этому
 * снимку, показывает игру. Разница между ними и есть предмет разговора.
 *
 * <p>Запуск: {@code kelium.ВыгрузкаЗаданий <куда.json>}
 */
public final class ВыгрузкаЗаданий {

    private ВыгрузкаЗаданий() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        Path куда = Path.of(args.length > 0 ? args[0] : "задания-из-кода.json");

        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ruleset", GameConfig.DEFAULT_RULESET);
        Map<String, Object> версии = new LinkedHashMap<>();
        for (String семья : List.of("objectives", "super_objectives")) {
            var набор = cfg.content.sets.get(семья);
            if (набор == null) {
                continue;
            }
            версии.put(семья, набор.version);
            List<Map<String, Object>> карты = new ArrayList<>();
            for (Map<String, Object> e : набор.entries) {
                карты.add(new LinkedHashMap<>(e));
            }
            out.put(семья, карты);
        }
        out.put("versions", версии);
        // ВСЕ КЛАССЫ КОЛОДЫ, а не только те, что попали в действующий набор.
        // Пересборка колоды начинается с вопроса «что у нас уже написано»: в
        // пачке есть карты, которых в наборе нет, и переписывать их заново было
        // бы работой на пустом месте.
        List<Map<String, Object>> всеКлассы = new ArrayList<>();
        for (kelium.engine.cards.Card c : new kelium.cards.objectives.ObjectivePack().cards()) {
            всеКлассы.add(new LinkedHashMap<>(c.data()));
        }
        out.put("classes", всеКлассы);
        Files.createDirectories(куда.toAbsolutePath().getParent());
        Files.writeString(куда, kelium.report.Json.write(out), StandardCharsets.UTF_8);
        System.out.println("выгружено: " + куда.toAbsolutePath());
        for (var e : версии.entrySet()) {
            System.out.println("  " + e.getKey() + " " + e.getValue() + " — карт "
                + ((List<?>) out.get(e.getKey())).size());
        }
    }
}
