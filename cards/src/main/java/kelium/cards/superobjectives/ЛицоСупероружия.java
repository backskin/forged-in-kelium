package kelium.cards.superobjectives;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ВСЁ, ЧТО НА КАРТЕ СУПЕР-ЗАДАНИЯ НАПЕЧАТАНО, ОБЪЯВЛЕННОЕ В КОДЕ.
 *
 * @param имя           печатное название
 * @param подзаголовок  вторая строка названия
 * @param родВойска     род супероружия: infantry/vehicle/aircraft/tower
 * @param символ        символ, нужный для вскрытия: circle/square/triangle/hourglass
 * @param очкиЗаВскрытие победные очки за вскрытие (у всех восьми — 5)
 * @param ячейки        ровно четыре ячейки: {@code {kind, amount}}
 * @param текстКарты    полный игровой текст карты (правило, а не Javadoc)
 * @param описание      литературный текст-подпись
 */
public record ЛицоСупероружия(String имя, String подзаголовок, String родВойска, String символ,
                              int очкиЗаВскрытие, List<Map<String, Object>> ячейки,
                              String текстКарты, String описание) {

    public Map<String, Object> выгрузить(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", имя);
        out.put("subtitle", подзаголовок);
        out.put("weapon_unit", родВойска);
        out.put("requires_symbol", символ);
        out.put("vp_on_reveal", очкиЗаВскрытие);
        out.put("cells", ячейки);
        out.put("текст_карты", текстКарты);
        out.put("описание", описание);
        return out;
    }
}
