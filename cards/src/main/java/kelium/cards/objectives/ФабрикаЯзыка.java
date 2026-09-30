package kelium.cards.objectives;

import java.util.Map;

import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;

/**
 * ФАБРИКА КАРТ ЯЗЫКА (Карты 2.0): задание, у записи которого есть блок
 * {@code язык}, строится {@link ЗаданиеИзЯзыка} — без класса под карту.
 * Подключается движку через {@code ServiceLoader}.
 */
public final class ФабрикаЯзыка implements CardRegistry.DataCardFactory {

    @Override
    @SuppressWarnings("unchecked")
    public Card изДанных(String family, Map<String, Object> entry) {
        if (!"objectives".equals(family) || !(entry.get("язык") instanceof Map<?, ?> язык)) {
            return null;
        }
        return new ЗаданиеИзЯзыка(String.valueOf(entry.get("id")), (Map<String, Object>) язык);
    }
}
