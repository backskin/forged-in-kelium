package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.dataio.ContentLibrary;
import kelium.dataio.GameConfig;
import kelium.engine.cards.CardRegistry;
import kelium.rules.Ruleset;

/**
 * СПРАВОЧНИК ДОЛЖЕН ВИДЕТЬ ТЕКСТ ИЗ КЛАССА КАРТЫ, А НЕ ИЗ YAML (заказ дизайнера
 * 18.08.2026: «и внешний справочник, и справочник внутри реплэя должны брать
 * описания сразу с классов»).
 *
 * <p>НАЙДЕНО ПРИ ЭТОЙ ПРАВКЕ. {@code CardRegistry.bindAll} вызывался только из
 * {@code Setup.buildGame} — то есть только когда партия реально поднимается.
 * Внешний справочник ({@code HelpApp}) и внутренний справочник без открытой
 * партии строят {@code ContentLibrary} через {@code ContentLibrary.forRuleset}
 * напрямую, минуя {@code Setup.buildGame} целиком, — и потому читали каталог
 * как ДО переезда карт в код: сырой YAML. Пока текст в коде совпадал с
 * YAML дословно (сразу после переезда), это не было заметно; исправь кто-нибудь
 * описание в классе, не трогая файл, — и оба справочника молча показали бы
 * устаревший текст. Тот же класс ошибки, что чинился всю сессию с картами.
 *
 * <p>Починка: {@code bindAll} теперь вызывается внутри самого
 * {@code ContentLibrary.forRuleset} — единственной точки, откуда контент
 * загружается для чего угодно (партия, оба справочника). Тест гоняет РОВНО ТОТ
 * путь, которым идёт справочник ({@code ContentLibrary.forRuleset} напрямую, БЕЗ
 * {@code Setup.buildGame}), и требует, чтобы запись каталога для карты, живущей
 * в коде, совпала один в один с {@code Card.describe()}.
 */
class CardDescriptionsSingleSourceTest {

    @Test
    void справочникБезПартииВидитОписаниеИзКласса() {
        var root = GameConfig.resolveDataRoot(null);
        Ruleset rs = Ruleset.loadById(GameConfig.DEFAULT_RULESET, root.resolve("rulesets"));
        // ТЕМ ЖЕ ПУТЁМ, ЧТО HelpBook.of() — БЕЗ Setup.buildGame. Если бы bindAll
        // был вызван только там, эта запись осталась бы сырым YAML.
        ContentLibrary content = ContentLibrary.forRuleset(rs, root);

        String id = "n1";
        Map<String, Object> запись = content.get("objectives").byId(id);
        var карта = CardRegistry.objective(id);
        assertNotNull(карта, "карта " + id + " обязана быть кодом");

        assertEquals(карта.describe(), запись.get("описание"),
            "справочник без партии видит другое описание, чем класс карты — "
            + "значит bindAll в ContentLibrary.forRuleset не сработал");
        assertEquals(карта.name(), запись.get("name"),
            "справочник без партии видит другое имя, чем класс карты");
    }

    /** То же для карты арсенала — другая семья, другой choke point, тот же путь. */
    @Test
    void справочникБезПартииВидитАрсеналИзКласса() {
        var root = GameConfig.resolveDataRoot(null);
        Ruleset rs = Ruleset.loadById(GameConfig.DEFAULT_RULESET, root.resolve("rulesets"));
        ContentLibrary content = ContentLibrary.forRuleset(rs, root);

        // НОМЕР БЕРЁТСЯ ИЗ КОЛОДЫ, А НЕ ВПИСАН РУКАМИ. Раньше здесь стоял
        // «b01», и сторож падал, как только пересборка колоды вывела эту карту
        // из набора: он проверяет ПУТЬ (описание приходит из класса, а не из
        // YAML), а не конкретную карту.
        String id = String.valueOf(content.get("arsenal").entries.stream()
            .filter(e -> CardRegistry.arsenal(String.valueOf(e.get("id"))) != null)
            .findFirst().orElseThrow().get("id"));
        Map<String, Object> запись = content.get("arsenal").byId(id);
        var карта = CardRegistry.arsenal(id);
        assertNotNull(карта, "карта " + id + " обязана быть кодом");

        assertEquals(карта.describe(), запись.get("описание"),
            "справочник без партии видит другое описание карты арсенала, чем класс");
    }
}
