package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * СТРОКА СОСТОЯНИЯ КОНСТРУКТОРА — регрессия 12.08.2026.
 *
 * <p>Конструктор перестал открываться: при чистке эмодзи из подписи инструмента
 * убрали аргумент, а {@code %s} в шаблоне остался, и {@code String.format} падал
 * MissingFormatArgumentException при первом обновлении статуса — то есть ещё до
 * показа окна. Ошибку не поймал ни один тест, потому что подпись собиралась
 * внутри Swing-класса. Теперь это чистая функция, и она проверяется.
 */
class ConstructorStatusTest {

    @Test
    void statusLineFormatsForEveryTool() {
        for (String label : new String[]{
            "Гекс: добавить / убрать", "Очистить гекс", "Старт игрока",
            "Малое зарождение", "Большое зарождение", "Двойной тайл (стопка ×2)",
            "Правка келемия ±", "Запретный гекс", "Нейтрал малый", "Нейтрал большой"}) {
            String s = LayoutEditor.statusText(label, 42, 3, 4, 7);
            assertTrue(s.contains(label), "в строке есть название инструмента: " + s);
            assertTrue(s.contains("гексов: 42"), "число гексов: " + s);
            // Знаменатель — ПРЕДЕЛ МЕСТ (4), а не текущий состав: состав и есть
            // число стартов, и дробь «N/N» ничего не сообщала.
            assertTrue(s.contains("стартов: 3 из " + LayoutEditor.MAX_SEATS),
                "старты и предел мест: " + s);
            assertTrue(s.contains("зарождений: 7"), "зарождения: " + s);
            assertTrue(!s.contains("%"), "в готовой строке не осталось шаблонов: " + s);
        }
    }
}
