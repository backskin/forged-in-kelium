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
 * @param печать     БУКВА ЛИЦА на печати ({@code A}…{@code N}); карт с одним
 *                   лицом несколько, а картинка у них одна, и показу нужна
 *                   именно она. {@code null} — печати у карты нет
 */
public record ЛицоКонтейнера(String имя, String уровень, String spornaя,
                             Map<String, Object> a, Map<String, Object> b,
                             String описание, String печать) {

    /** Прежняя форма — без буквы лица. */
    public ЛицоКонтейнера(String имя, String уровень, String spornaя,
                          Map<String, Object> a, Map<String, Object> b, String описание) {
        this(имя, уровень, spornaя, a, b, описание, null);
    }

    public Map<String, Object> выгрузить(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", имя);
        if (уровень != null) {
            out.put("tier", уровень);
        }
        if (печать != null) {
            out.put("face", печать);
        }
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
