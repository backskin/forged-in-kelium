package kelium.cards.containers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ВСЁ, ЧТО НА КАРТЕ КОНТЕЙНЕРА НАПЕЧАТАНО, ОБЪЯВЛЕННОЕ В КОДЕ.
 *
 * @param имя        печатное название
 * @param уровень    common/good/rare — как в данных, для калибровки и подписи
 * @param spornaя    id спорного правила (contested_cards.<id>_enabled), которое
 *                   должно быть включено, чтобы карта играла; {@code null} —
 *                   карта играет всегда
 * @param a          эффект стороны А: {@code {effect, params, label}}
 * @param b          эффект стороны Б; {@code null} — карты без выбора
 * @param описание   литературный текст карты целиком
 */
public record ЛицоКонтейнера(String имя, String уровень, String spornaя,
                             Map<String, Object> a, Map<String, Object> b, String описание) {

    public Map<String, Object> выгрузить(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", имя);
        out.put("tier", уровень);
        if (spornaя != null) {
            out.put("contested", spornaя);
        }
        out.put("a", a);
        if (b != null) {
            out.put("b", b);
        }
        out.put("описание", описание);
        return out;
    }
}
