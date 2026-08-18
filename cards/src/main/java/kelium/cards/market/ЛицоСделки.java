package kelium.cards.market;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ВСЁ, ЧТО НА КАРТЕ СДЕЛКИ НАПЕЧАТАНО, ОБЪЯВЛЕННОЕ В КОДЕ.
 *
 * @param имя      печатное название карты
 * @param левое    предложение левой ячейки: {@code {name, effect, params, label}}
 * @param правое   предложение правой ячейки
 * @param описание литературный текст карты целиком
 */
public record ЛицоСделки(String имя, Map<String, Object> левое, Map<String, Object> правое,
                         String описание) {

    public Map<String, Object> выгрузить(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", имя);
        out.put("left", левое);
        out.put("right", правое);
        out.put("описание", описание);
        return out;
    }
}
